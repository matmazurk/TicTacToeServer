import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.Socket
import Message.*
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.InputStream
import java.sql.Wrapper

class SessionTest {

    private val FIRST_CLIENT_NICK = "czopek"
    private val SECOND_CLIENT_NICK = "czop"
    private val THIRD_CLIENT_NICK = "okno"
    private val FOURTH_CLIENT_NICK = "krzys"
    private val wait get() = sleep()

    private lateinit var serverSocket: ServerSocket
    private val clients = mutableListOf<Socket>()
    private lateinit var session: Session

    @BeforeEach
    fun setup() {
        serverSocket = ServerSocket(PORT)
        session = Session(serverSocket)
        session.establish()
    }

    @AfterEach
    fun cleanup() {
        clients.forEach {
            it.close()
        }
        serverSocket.close()
    }

    @Test
    fun test_client_registration_successful() {
        val newClient = registerNewClient(FIRST_CLIENT_NICK)
        assertClientRegistered(newClient)
    }

    @Test
    fun test_client_registration_unsuccessful() {
        val firstClient = registerNewClient(FIRST_CLIENT_NICK)
        val secondClient = registerNewClient(FIRST_CLIENT_NICK)
        assertClientRegistered(firstClient)
        assertClientNotRegistered(secondClient)
    }

    @Test
    fun test_connect_four_clients() {
        val clients = mutableListOf(
                registerNewClient(FIRST_CLIENT_NICK),
                registerNewClient(SECOND_CLIENT_NICK),
                registerNewClient(THIRD_CLIENT_NICK),
                registerNewClient(FOURTH_CLIENT_NICK)
        )
        clients.forEach {
            assertClientRegistered(it)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun test_game_invitation(accepted: Boolean) {
        val firstClient = registerNewClientAndReceiveClientsList(FIRST_CLIENT_NICK)
        val secondClient = registerNewClientAndReceiveClientsList(SECOND_CLIENT_NICK)
        val first = firstClient.first as Socket
        val second = secondClient.first as Socket
        receiveClientsList(first.getInputStream())
        val from = firstClient.second
        val to = secondClient.second
        assertClientRegistered(firstClient)
        assertClientRegistered(secondClient)

        val gameInvitationMessage = prepareGameInvitationMessage(from, to)
        gameInvitationMessage.writeDelimitedTo(first.getOutputStream())
        val gameInv = WrapperMessage.parseDelimitedFrom(second.getInputStream())
        assert(gameInv.msgCase == WrapperMessage.MsgCase.GAMEINVITATION)
        assert(gameInv.gameInvitation.from == from)
        assert(gameInv.gameInvitation.to == to)

        val gameInvitationResponse = prepareGameInvitationResponseMessage(from, to, accepted)
        gameInvitationResponse.writeDelimitedTo(second.getOutputStream())
        val receivedGameInvitationResponse = WrapperMessage.parseDelimitedFrom(first.getInputStream())
        assert(receivedGameInvitationResponse.msgCase == WrapperMessage.MsgCase.GAMEINVITATIONRESPONSE)
        assert(receivedGameInvitationResponse.gameInvitationResponse.accepted == accepted)
        if(accepted) {
            receiveGameStarted(first, second)
            receiveTurns(first, second)
        }
        first.close()
        if(accepted) {
            val receivedPartnerDisconnected = WrapperMessage.parseDelimitedFrom(second.getInputStream())
            assert(receivedPartnerDisconnected.msgCase == WrapperMessage.MsgCase.PARTNERDISCONNECTED)
        }

        val receivedClientsList = WrapperMessage.parseDelimitedFrom(second.getInputStream())
        assert(receivedClientsList.msgCase == WrapperMessage.MsgCase.CLIENTSLIST)
    }

    private fun connectNewClient(): Socket {
        val newClient = Socket(serverSocket.inetAddress, PORT)
        clients.add(newClient)
        return newClient
    }

    private fun prepareRegistrationMessage(nick: String): WrapperMessage {
        val wrapperMessageBuilder = WrapperMessage.newBuilder()
        val registerMessageBuilder = Register.newBuilder()
        registerMessageBuilder.nick = nick
        wrapperMessageBuilder.register = registerMessageBuilder.build()
        return wrapperMessageBuilder.build()
    }

    private fun prepareGameInvitationMessage(from: Int, to: Int): WrapperMessage {
        val wrapperMessageBuilder = WrapperMessage.newBuilder()
        val gameInvitationMessageBuilder = GameInvitation.newBuilder()
        gameInvitationMessageBuilder.from = from
        gameInvitationMessageBuilder.to = to
        wrapperMessageBuilder.gameInvitation = gameInvitationMessageBuilder.build()
        return wrapperMessageBuilder.build()
    }

    private fun prepareGameInvitationResponseMessage(from: Int, to: Int, accepted: Boolean): WrapperMessage {
        val wrapperMessageBuilder = WrapperMessage.newBuilder()
        val gameInvitationResponseBuilder = GameInvitationResponse.newBuilder()
        gameInvitationResponseBuilder.from = from
        gameInvitationResponseBuilder.to = to
        gameInvitationResponseBuilder.accepted = accepted
        wrapperMessageBuilder.gameInvitationResponse = gameInvitationResponseBuilder.build()
        return wrapperMessageBuilder.build()
    }

    private fun registerNewClient(nick: String): Pair<Socket?, Int> {
        val newClient = connectNewClient()
        val registrationMessage = prepareRegistrationMessage(nick)
        registrationMessage.writeDelimitedTo(newClient.getOutputStream())
        val receivedMessage = WrapperMessage.parseDelimitedFrom(newClient.getInputStream())
        assert(receivedMessage.msgCase == WrapperMessage.MsgCase.REGISTERRESPONSE)
        val clientNumber = receivedMessage.registerResponse.clientNumber
        return if(receivedMessage.registerResponse.success) {
            assert(clientNumber != -1)
            newClient to clientNumber
        } else {
            assert(clientNumber == -1)
            null to clientNumber
        }
    }

    private fun registerNewClientAndReceiveClientsList(nick: String): Pair<Socket?, Int> {
        val result = registerNewClient(nick)
        wait
        receiveClientsList(result.first?.getInputStream())
        return result
    }

    private fun receiveClientsList(stream: InputStream?) {
        stream ?: return
        val receivedMessage = WrapperMessage.parseDelimitedFrom(stream)
        assert(receivedMessage.msgCase == WrapperMessage.MsgCase.CLIENTSLIST)
    }

    private fun receiveGameStarted(vararg clients: Socket) {
        clients.forEach {
            val message = WrapperMessage.parseDelimitedFrom(it.getInputStream())
            assert(message.msgCase == WrapperMessage.MsgCase.GAMESTART)
        }
    }

    private fun receiveTurns(vararg clients: Socket) {
        clients.forEach {
            val message = WrapperMessage.parseDelimitedFrom(it.getInputStream())
            assert(message.msgCase == WrapperMessage.MsgCase.TURN)
        }
    }

    private fun assertClientRegistered(client: Pair<Socket?, Int>) {
        assert(client.first != null)
        assert(client.second != -1)
    }

    private fun assertClientNotRegistered(client: Pair<Socket?, Int>) {
        assert(client.first == null)
        assert(client.second == -1)
    }

    private fun sleep() = Thread.sleep(10)
}