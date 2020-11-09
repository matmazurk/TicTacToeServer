import Message.*
import com.google.protobuf.GeneratedMessageV3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.Socket
import java.net.SocketException
import kotlin.reflect.KClass

class Client(
        private val socket: Socket,
        val number: Int,
        private val handler: ClientHandler,
) {
    enum class State {
        UNREGISTERED,
        REGISTERED,
        IN_GAME
    }

    private var _connected = false
    val connected get() = _connected
    var nick: String? = null
    var gameNumber: Int? = null
    var partnerNumber: Int? = null
    private val inputStream = socket.getInputStream()
    private val outputStream = socket.getOutputStream()
    var state = State.UNREGISTERED

    fun handle() {
        _connected = true
        GlobalScope.launch(Dispatchers.IO) {
            while(_connected) {
                try {
                    val incomingMessage = WrapperMessage.parseFrom(inputStream)
                    handler.process(incomingMessage, this@Client)
                } catch (e: SocketException) {
                    disconnect()
                }
            }
        }
    }

    fun disconnect(): String? {
        if(_connected) {
            if(state == State.IN_GAME) {
                handler.notifyPartnerAboutDC(partnerNumber!!, number)
            }
            socket.close()
            _connected = false
            handler.disconnect(number)
            println("Client $number disconnected.")
        }
        return null
    }

    private inline fun <reified Message : GeneratedMessageV3, reified Builder: com.google.protobuf.Message.Builder>
            message(messageClass: KClass<Message>,
                    builderClass: KClass<Builder>,
                    func: Builder.() -> Unit) {
        val wrapperMessage = WrapperMessage.newBuilder()
        val messageBuilder = Message::class.members.filter { it.name.contains("newBuilder") && it.parameters.isEmpty() }[0].call() as Builder
        func(messageBuilder)
        val property = WrapperMessage::class.java.fields.filter {
            it.type.name == Message::class.java.name
        }[0]
        property.set(wrapperMessage, messageBuilder.build())
        wrapperMessage.build().writeDelimitedTo(outputStream)
    }

    fun sendRegisterResponse(correct: Boolean) =
        message(RegisterResponse::class, RegisterResponse.Builder::class) {
            success = correct
        }

    fun sendClientsList(clients: Map<Int, String>) =
        message(ClientsList::class, ClientsList.Builder::class) {
            clients.forEach { (id, nick) ->
                val client = Message.Client.newBuilder()
                client.id = id
                client.nick = nick
                addClients(client.build())
            }
        }

    fun sendGameInvitation(from: Int, to: Int) =
        message(GameInvitation::class, GameInvitation.Builder::class) {
            this.from = from
            this.to = to
        }

    fun sendGameInvitationResponse(from: Int, to: Int, result: Boolean) =
            message(GameInvitationResponse::class, GameInvitationResponse.Builder::class) {
                this.from = from
                this.to = to
                accepted = result
            }

    fun sendMoveResponse(from: Int, correct: Boolean, reason: MoveResponse.MoveRejectReason = MoveResponse.MoveRejectReason.NONE) =
        message(MoveResponse::class, MoveResponse.Builder::class) {
            this.from = from
            this.correct = correct
            if(!correct) {
                this.reason = reason
            }
        }

    fun sendPrivateMessage(from: Int, to: Int, message: String) =
            message(PrivateMessage::class, PrivateMessage.Builder::class) {
                this.from = from
                this.to = to
                this.message = message
            }

//    private fun send(message: String) {
//        try {
//            writer.write(message)
//            writer.flush()
//        } catch(e: SocketException) {
//            disconnect()
//        }
//    }
//
//    fun sendNotOkResponse(header: String) {
//        val message = "$header:notOk\n"
//        send(message)
//    }
//
//    fun sendOkResponse(header: String) {
//        val message = "$header:Ok\n"
//        send(message)
//    }
//
//    fun sendMessageFromServer(message: String) {
//        val message = "server:$message\n"
//        send(message)
//    }
//
//    fun sendClientsList(list: String) {
//        val message = "clients:$list\n"
//        send(message)
//    }
//
//    fun sendGameInvitation(from: Int) {
//        val message = "gameInv:${from}\n"
//        send(message)
//    }
//
//    fun sendPartnerDisconnected(partner: Int) {
//        val message = "partnerDC:${partner}\n"
//        send(message)
//    }
//
//    fun sendGameStart(partner: Int, char: Char) {
//        val message = "gameStart:$partner;$char\n"
//        send(message)
//    }
//
//    fun sendTurn(turn: Int) {
//        val message = "turn:$turn\n"
//        send(message)
//    }
//
//    fun sendGameWon() {
//        val message = "gameWon:\n"
//        send(message)
//    }
//
//    fun sendGameLost() {
//        val message = "gameLost:\n"
//        send(message)
//    }
//
//    fun sendMoveResponse(participant: Int, moveResult: String) {
//        val message = "moveResult:$participant;$moveResult"
//    }
}