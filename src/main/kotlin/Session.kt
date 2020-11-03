import kotlinx.coroutines.*
import java.net.ServerSocket
import java.net.SocketException

class Session(
    private val serverSocket: ServerSocket
) : ClientHandler, GameHandler {
    private var running: Boolean = false
    private var clientsEverConnected = 0
    private var gamesEverStarted = 0

    private val clients: MutableList<Client> = mutableListOf()
    val clientsCount
        get() = clients.size
    val connectedClients: List<Client>
        get() = clients

    private val games: MutableList<Game> = mutableListOf()
    val gamesCount
        get() = games.size

    private lateinit var connSession: Job

    private val messageHandlers = mapOf<String, (String, Int) -> Unit>(
        "register" to { payload, clientNumber ->
            val clientWithNick = clients.find { it.nick == payload }
            if(clientWithNick != null) {
                getClient(clientNumber)?.sendNotOkResponse("register")
            } else {
                val client = clients.find { it.number == clientNumber }!!
                client.nick = payload
                client.state = Client.State.REGISTERED
                client.sendOkResponse("register")
                broadcastRegistered {
                    sendClientsList(prepareClientsList())
                }
            }
        },
        "message" to { payload, clientNumber ->
            println("Client$clientNumber:$payload")
        },
        "gameInv" to { payload, clientNumber ->
            val clientToInvite = getClient(payload.toInt())
            val invitingClient = getClient(clientNumber)
            if(clientToInvite == null) {
                invitingClient?.sendNotOkResponse("gameInv")
            } else {
                clientToInvite.sendGameInvitation(invitingClient!!.number)
                invitingClient?.sendOkResponse("gameInv")
            }
        },
        "gameAccept" to func@ { payload, clientNumber ->
            val firstParticipant = getClient(payload.toInt())
            val secondParticipant = getClient(clientNumber)
            if(firstParticipant == null) {
                printError("Client $payload triggered game, but is no visible anymore!")
                secondParticipant?.sendPartnerDisconnected(payload.toInt())
                return@func
            }
            if(secondParticipant == null) {
                printError("Client $clientNumber accepted game, but is no visible anymore!")
                firstParticipant.sendPartnerDisconnected(clientNumber)
                return@func
            }
            val newGame = Game(firstParticipant.number, secondParticipant.number, this, gamesEverStarted)
            firstParticipant.gameNumber = gamesEverStarted
            firstParticipant.state = Client.State.IN_GAME
            firstParticipant.partnerNumber = secondParticipant.number
            secondParticipant.gameNumber = gamesEverStarted++
            secondParticipant.state = Client.State.IN_GAME
            secondParticipant.partnerNumber = firstParticipant.number
            games.add(newGame)
            newGame.start()
        },
        "gameReject" to { payload, _ ->
            getClient(payload.toInt())?.sendNotOkResponse("gameInv")
        },
        "move" to func@ { payload, clientNumber ->
            val client = getClient(clientNumber) ?: return@func
            val moves = payload.split(";")
            if(moves.size != 2) {
                client.sendNotOkResponse("move")
                return@func
            }
            val clientNumber = client.number
            games.find { it.number == client.gameNumber }!!.move(clientNumber, moves[0].toInt(), moves[1].toInt())
        }
    )

    fun establish() {
        running = true
        connSession = GlobalScope.launch {
            while(running) {
                try {
                    val client = Client(serverSocket.accept(), clientsEverConnected++, this@Session)
                    clients.add(client)
                    GlobalScope.launch(Dispatchers.IO) { client.handle() }
                } catch(e: SocketException) {
                }
            }
        }
    }

    fun terminate() {
        running = false
        println("Disconnecting clients...")
        clients.forEach { it.disconnect() }
        serverSocket.close()
        runBlocking { connSession.join() }
    }

    fun writeToClient(message: String, clientNumber: Int) =
        getClient(clientNumber)?.sendMessageFromServer(message) ?: -1

    fun isConnected(clientNumber: Int) = getClient(clientNumber) != null

    override fun process(message: String?, clientNumber: Int) {
        if(message == null) {
            clients.removeIf { it.number == clientNumber }
        } else {
            val splitMessage = message.split(":")
            if(splitMessage.size != 2) {
                printError("Wrong message from client$clientNumber")
                println("Message:$message")
            } else {
                val header = splitMessage[0]
                val payload = splitMessage[1]
                if(header in messageHandlers.keys) {
                    messageHandlers[header]!!.invoke(payload, clientNumber)
                } else {
                    printError("Wrong message header from client$clientNumber")
                    println("Header:$header")
                }
            }
        }
    }

    override fun disconnect(clientNumber: Int) {
        clients.removeIf { it.number == clientNumber }
        broadcastRegistered {
            sendClientsList(prepareClientsList())
        }
    }

    override fun notifyPartnerAboutDC(partnerNumber: Int, clientNumber: Int) {
        getClient(partnerNumber)?.sendPartnerDisconnected(clientNumber)
    }

    private fun printError(error: String) {
        println("${ConsoleColors.RED}$error${ConsoleColors.RESET}")
    }

    private fun prepareClientsList(): String {
        val list = StringBuilder()
        clients.forEach {
            if(it.state != Client.State.UNREGISTERED) {
                list.append("${it.number}@${it.nick};")
            }
        }
        return list.toString()
    }

    private fun broadcastRegistered(function: Client.() -> Unit) {
        clients.forEach {
            if(it.state != Client.State.UNREGISTERED) {
                it.function() }
        }
    }

    private fun broadcast(function: Client.() -> Unit) {
        clients.forEach { it.function() }
    }

    override fun turn(participant: Int, other: Int) {
        getClient(participant)?.sendTurn(participant)
        getClient(other)?.sendTurn(participant)
    }

    override fun move(result: Game.Move, participant: Int, other: Int) {
        val client = getClient(participant)
        val secondClient = getClient(other)
        client?.sendMoveResponse(participant, result.toString())
        secondClient?.sendMoveResponse(other,result.toString())
    }

    override fun start(char: Char, participant: Int, partner: Int) {
        val client = getClient(participant)
        client?.sendGameStart(partner, char)
    }

    override fun win(winner: Int, loser: Int) {
        val winner = getClient(winner)
        val loser = getClient(loser)
        winner?.sendGameWon()
        loser?.sendGameLost()
    }

    private fun getClient(number: Int) =
        clients.find { it.number == number }
}


