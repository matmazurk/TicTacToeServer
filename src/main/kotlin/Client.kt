import Message.*
import com.google.protobuf.GeneratedMessageV3
import com.google.protobuf.InvalidProtocolBufferException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.Socket
import java.net.SocketException
import kotlin.properties.Delegates
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
    var nick: String by Delegates.notNull()
    var gameNumber: Int by Delegates.notNull()
    var partnerNumber: Int by Delegates.notNull()
    private val inputStream = socket.getInputStream()
    private val outputStream = socket.getOutputStream()
    var state = State.UNREGISTERED

    fun handle() {
        _connected = true
        GlobalScope.launch(Dispatchers.IO) {
            while(_connected) {
                try {
                    val incomingMessage = WrapperMessage.parseDelimitedFrom(inputStream)
                    if(incomingMessage != null)
                    {
                        handler.process(incomingMessage, this@Client)
                    } else {
                        disconnect()
                    }
                } catch (e: SocketException) {
                    disconnect()
                } catch (e: InvalidProtocolBufferException) {
                    println("Inv protocol protobuf!")
                }
            }
        }
    }

    fun disconnect(): String? {
        if(_connected) {
            if(state == State.IN_GAME) {
                handler.notifyPartnerAboutDC(partnerNumber, number)
            }
            socket.close()
            _connected = false
            handler.disconnect(number)
            println("Client $number disconnected.")
        }
        return null
    }

    private inline fun <reified Message : GeneratedMessageV3,
            reified Builder : com.google.protobuf.Message.Builder>
        sendMessage(
        messageClass: KClass<Message>,
        builderClass: KClass<Builder>,
        func: Builder.() -> Unit
    ) {
        val wrapperMessage = WrapperMessage.newBuilder()
        val messageBuilder = Message::class.members.filter { it.name.contains("newBuilder") && it.parameters.isEmpty() }[0].call() as Builder
        func(messageBuilder)
        val property = WrapperMessage.Builder::class.members.filter {
            it.name.contains("set${Message::class.simpleName}") &&
            it.parameters.find {
                !it.type.toString().contains("Builder")
            } != null
        }[0]

        property.call(wrapperMessage, messageBuilder.build())
        try {
            wrapperMessage.build().writeDelimitedTo(outputStream)
        } catch (e: SocketException) {
            println("Trying to write to disconnected client$number")
        }
    }

    fun sendRegisterResponse(correct: Boolean) =
        sendMessage(RegisterResponse::class, RegisterResponse.Builder::class) {
            success = correct
            clientNumber =
                if(correct) {
                    number
                } else {
                    -1
                }
        }

    fun sendClientsList(clients: Map<Int, String>) =
        sendMessage(ClientsList::class, ClientsList.Builder::class) {
            clients.forEach { (id, nick) ->
                val client = Message.Client.newBuilder()
                client.id = id
                client.nick = nick
                addClients(client.build())
            }
        }

    fun sendGameInvitation(from: Int) =
        sendMessage(GameInvitation::class, GameInvitation.Builder::class) {
            this.from = from
            this.to = number
        }

    fun sendGameInvitationResponse(from: Int, to: Int, result: Boolean) =
            sendMessage(GameInvitationResponse::class, GameInvitationResponse.Builder::class) {
                this.from = from
                this.to = to
                accepted = result
            }

    fun sendMoveResponse(from: Int, status: MoveResponse.Status) =
        sendMessage(MoveResponse::class, MoveResponse.Builder::class) {
            this.from = from
            this.status = status
        }

    fun sendPrivateMessage(from: Int, to: Int, message: String) =
            sendMessage(PrivateMessage::class, PrivateMessage.Builder::class) {
                this.from = from
                this.to = to
                this.message = message
            }

    fun sendPartnerDisconnected(partner: Int) =
        sendMessage(PartnerDisconnected::class, PartnerDisconnected.Builder::class) {
            partnerNumber = partner
            client = number
        }

    fun sendGameStarted(noughts: Int, crosses: Int) =
            sendMessage(GameStart::class, GameStart.Builder::class) {
                this.noughts = noughts
                this.crosses = crosses
            }

    fun sendTurn(participantsTurn: Int) =
            sendMessage(Turn::class, Turn.Builder::class) {
                turn = participantsTurn
            }

    fun sendGameResult(result: GameResult.Result) =
            sendMessage(GameResult::class, GameResult.Builder::class) {
                this.result = result
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