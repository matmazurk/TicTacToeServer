import Message.*
import com.google.protobuf.ExtensionRegistryLite
import org.junit.jupiter.api.*
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.powermock.api.mockito.PowerMockito
import org.powermock.api.mockito.PowerMockito.*
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket


const val CLIENT_NUMBER = 1
const val CLIENT_NICK = "CZOP"
const val PORT = 43211

@RunWith(PowerMockRunner::class)
@PrepareForTest(WrapperMessage::class)
class ClientTest {

    private lateinit var serverSocket: ServerSocket
    private lateinit var clientSocket: Socket
    private lateinit var otherSocket: Socket
    private lateinit var otherInputStream: InputStream
    private lateinit var otherOutputStream: OutputStream
    private val clientHandlerMock = mock(ClientHandler::class.java)
    private lateinit var client: Client
    private val wait get() = sleep()
    private val inputStreamMock = mock(InputStream::class.java)
    private val socketMock = mock(Socket::class.java)

    @BeforeEach
    fun setup() {
//        serverSocket = ServerSocket(PORT)
//        otherSocket = Socket(serverSocket.inetAddress, PORT)
//        clientSocket = serverSocket.accept()
//        client = Client(clientSocket, CLIENT_NUMBER, clientHandlerMock)
//        otherInputStream = otherSocket.getInputStream()
//        otherOutputStream = otherSocket.getOutputStream()
//        client.handle()
    }

    @AfterEach
    fun cleanup() {
//        clientSocket.close()
//        otherSocket.close()
//        serverSocket.close()
    }

    @Test
    fun test_registration() {
        val wrapperMessageBuilder = WrapperMessage.newBuilder()
        val registerMessage = Register.newBuilder().build()
        wrapperMessageBuilder.register = registerMessage
        val wrapperMessage = wrapperMessageBuilder.build()

        `when`(socketMock.getInputStream()).thenReturn(inputStreamMock)

        PowerMockito.mockStatic(WrapperMessage::class.java)
        `when`(WrapperMessage.parseDelimitedFrom(inputStreamMock)).thenReturn(wrapperMessage)
//        mockStatic(WrapperMessage::class.java).use { theMock ->
//            theMock.`when`<Any>{ WrapperMessage.parseDelimitedFrom(inputStreamMock) }.thenReturn(wrapperMessage)
//            client = Client(socketMock, CLIENT_NUMBER, clientHandlerMock, theMock::class)
//        }
        client.handle()
        wait

        verify(clientHandlerMock).process(wrapperMessage, client)

        client.disconnect()
    }

    @Test
    fun test_registration_unsuccessful() {
        val msg = receiveRegistrationMessage()
        verify(clientHandlerMock).process(msg, client)
        client.sendRegisterResponse(false)
        val sentMsg = WrapperMessage.parseDelimitedFrom(otherInputStream)
        assert(sentMsg.msgCase == WrapperMessage.MsgCase.REGISTERRESPONSE)
        assert(!sentMsg.registerResponse.success)
        assert(sentMsg.registerResponse.clientNumber == -1)
    }

    @Test
    fun test_registration_successful() {
        val msg = receiveRegistrationMessage()
        verify(clientHandlerMock).process(msg, client)
        client.sendRegisterResponse(true)
        val sentMsg = WrapperMessage.parseDelimitedFrom(otherInputStream)
        assert(sentMsg.msgCase == WrapperMessage.MsgCase.REGISTERRESPONSE)
        assert(sentMsg.registerResponse.success)
        assert(sentMsg.registerResponse.clientNumber != -1)
    }

    @Test
    fun test_client_disconnected() {
        assert(clientSocket.isConnected)
        dropOtherSocket()
        verify(clientHandlerMock).disconnect(CLIENT_NUMBER)
        assert(clientSocket.isClosed)
    }

    @Test
    fun test_send_clients_list() {
        val clientsMap = prepareClientsListMap()
        client.sendClientsList(clientsMap)
        val sentMessage = interceptSentMessage()
        val expectedMessage = prepareClientsListMessage(clientsMap)
        assert(expectedMessage == sentMessage)
    }

    @Test
    fun test_send_game_invitation() {
        val inviterNumber = 3
        client.sendGameInvitation(inviterNumber)
        val sentMessage = interceptSentMessage()
        assert(sentMessage.msgCase == WrapperMessage.MsgCase.GAMEINVITATION)
        sentMessage.gameInvitation.run {
            assert(from == inviterNumber)
            assert(to == CLIENT_NUMBER)
        }
    }

    @Test
    fun test_send_game_invitation_response() {
        val inviterNumber = 2
        val result = true
        client.sendGameInvitationResponse(inviterNumber, CLIENT_NUMBER, result)
        val sentMessage = interceptSentMessage()
        assert(sentMessage.msgCase == WrapperMessage.MsgCase.GAMEINVITATIONRESPONSE)
        sentMessage.gameInvitationResponse.run {
            assert(accepted == result)
            assert(from == inviterNumber)
            assert(to == CLIENT_NUMBER)
        }
    }

    @Test
    fun test_send_move_response_correct_move() {
        val status = MoveResponse.Status.OK
        client.sendMoveResponse(CLIENT_NUMBER, status)
        val sentMessage = interceptSentMessage()
        assert(sentMessage.msgCase == WrapperMessage.MsgCase.MOVERESPONSE)
        sentMessage.moveResponse.run {
            assert(from == CLIENT_NUMBER)
            assert(this.status == status)
        }
    }

    @Test
    fun test_send_move_response_wrong_move() {
        val status = MoveResponse.Status.WRONG_POS
        client.sendMoveResponse(CLIENT_NUMBER, status)
        val sentMessage = interceptSentMessage()
        assert(sentMessage.msgCase == WrapperMessage.MsgCase.MOVERESPONSE)
        sentMessage.moveResponse.run {
            assert(from == CLIENT_NUMBER)
            assert(this.status == status)
        }
    }

    @Test
    fun test_send_private_message() {
        val payload = "oko asd"
        val to = 3
        client.sendPrivateMessage(CLIENT_NUMBER, to, payload)
        val sentMessage = interceptSentMessage()
        assert(sentMessage.msgCase == WrapperMessage.MsgCase.PRIVATEMESSAGE)
        sentMessage.privateMessage.run {
            assert(from == CLIENT_NUMBER)
            assert(this.to == to)
            assert(message == payload)
        }
    }

    private fun sleep() = Thread.sleep(10)

    private fun receiveRegistrationMessage() : WrapperMessage {
        val wrapperMessageBuilder = WrapperMessage.newBuilder()
        val registrationMessage = Register.newBuilder()
        registrationMessage.nick = CLIENT_NICK
        wrapperMessageBuilder.register = registrationMessage.build()
        val messageWrapper = wrapperMessageBuilder.build()
        messageWrapper.writeDelimitedTo(otherOutputStream)
        wait
        return messageWrapper
    }

    private fun prepareClientsListMap() : Map<Int, String> =
            mapOf(
                2 to "CZOPEK",
                3 to "OKO",
                4 to "noga",
                5 to "kolo"
            )

    private fun prepareClientsListMessage(clientsMap: Map<Int, String>): WrapperMessage {
        val wrapperMessageBuilder = WrapperMessage.newBuilder()
        val clientsListMessageBuilder = ClientsList.newBuilder()
        clientsMap.forEach { (number, nick) ->
            val clientMessageBuilder = Message.Client.newBuilder()
            clientMessageBuilder.id = number
            clientMessageBuilder.nick = nick
            clientsListMessageBuilder.addClients(clientMessageBuilder)
        }
        wrapperMessageBuilder.clientsList = clientsListMessageBuilder.build()
        return wrapperMessageBuilder.build()
    }

    private fun interceptSentMessage(): WrapperMessage {
        wait
        return WrapperMessage.parseDelimitedFrom(otherInputStream)
    }

    private fun dropOtherSocket() {
        otherSocket.close()
        wait
    }
}