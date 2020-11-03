import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.Socket

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
    private val writer = socket.getOutputStream().bufferedWriter()
    private val reader = socket.getInputStream().bufferedReader()
    var state = State.UNREGISTERED

    fun handle() {
        _connected = true
        GlobalScope.launch(Dispatchers.IO) {
            while(_connected) {
                try {
                    handler.process(reader.readLine() ?: disconnect(), number)
                } catch(e: IOException) {
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

    private fun send(message: String) {
        try {
            writer.write(message)
            writer.flush()
        } catch(e: IOException) {
            disconnect()
        }
    }

    fun sendNotOkResponse(header: String) {
        val message = "$header:notOk\n"
        send(message)
    }

    fun sendOkResponse(header: String) {
        val message = "$header:Ok\n"
        send(message)
    }

    fun sendMessageFromServer(message: String) {
        val message = "server:$message\n"
        send(message)
    }

    fun sendClientsList(list: String) {
        val message = "clients:$list\n"
        send(message)
    }

    fun sendGameInvitation(from: Int) {
        val message = "gameInv:${from}\n"
        send(message)
    }

    fun sendPartnerDisconnected(partner: Int) {
        val message = "partnerDC:${partner}\n"
        send(message)
    }

    fun sendGameStart(partner: Int, char: Char) {
        val message = "gameStart:$partner;$char\n"
        send(message)
    }

    fun sendTurn(turn: Int) {
        val message = "turn:$turn\n"
        send(message)
    }

    fun sendGameWon() {
        val message = "gameWon:\n"
        send(message)
    }

    fun sendGameLost() {
        val message = "gameLost:\n"
        send(message)
    }

    fun sendMoveResponse(participant: Int, moveResult: String) {
        val message = "moveResult:$participant;$moveResult"
    }
}