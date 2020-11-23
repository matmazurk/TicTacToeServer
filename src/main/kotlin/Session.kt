import kotlinx.coroutines.*
import java.net.ServerSocket
import java.net.SocketException
import Message.WrapperMessage.MsgCase.*
import java.util.concurrent.locks.Lock

class Session(
    private val serverSocket: ServerSocket
) : ClientHandler, GameHandler {

    private var running: Boolean = false
    private var clientsEverConnected = 0
    private var gamesEverStarted = 0
    private lateinit var connSession: Job
    private val clients: MutableList<Client> = mutableListOf()
    private val games: MutableList<Game> = mutableListOf()

    val clientsCount
        get() = clients.size
    val connectedClients: List<Client>
        get() = clients
    val gamesCount
        get() = games.size


//    private val messageHandlers = mapOf<String, (String, Int) -> Unit>(
//        "register" to { payload, clientNumber ->
//            val clientWithNick = clients.find { it.nick == payload }
//            if(clientWithNick != null) {
//                getClient(clientNumber)?.sendNotOkResponse("register")
//            } else {
//                val client = clients.find { it.number == clientNumber }!!
//                client.nick = payload
//                client.state = Client.State.REGISTERED
//                client.sendOkResponse("register")
//                broadcastToRegisteredClients {
//                    sendClientsList(prepareClientsMap())
//                }
//            }
//        },
//        "message" to { payload, clientNumber ->
//            println("Client$clientNumber:$payload")
//        },
//        "gameInv" to { payload, clientNumber ->
//            val clientToInvite = getClient(payload.toInt())
//            val invitingClient = getClient(clientNumber)
//            if(clientToInvite == null) {
//                invitingClient?.sendNotOkResponse("gameInv")
//            } else {
//                clientToInvite.sendGameInvitation(invitingClient!!.number)
//                invitingClient?.sendOkResponse("gameInv")
//            }
//        },
//        "gameAccept" to func@ { payload, clientNumber ->
//            val firstParticipant = getClient(payload.toInt())
//            val secondParticipant = getClient(clientNumber)
//            if(firstParticipant == null) {
//                printError("Client $payload triggered game, but is no visible anymore!")
//                secondParticipant?.sendPartnerDisconnected(payload.toInt())
//                return@func
//            }
//            if(secondParticipant == null) {
//                printError("Client $clientNumber accepted game, but is no visible anymore!")
//                firstParticipant.sendPartnerDisconnected(clientNumber)
//                return@func
//            }
//            val newGame = Game(firstParticipant.number, secondParticipant.number, this, gamesEverStarted)
//            firstParticipant.gameNumber = gamesEverStarted
//            firstParticipant.state = Client.State.IN_GAME
//            firstParticipant.partnerNumber = secondParticipant.number
//            secondParticipant.gameNumber = gamesEverStarted++
//            secondParticipant.state = Client.State.IN_GAME
//            secondParticipant.partnerNumber = firstParticipant.number
//            games.add(newGame)
//            newGame.start()
//        },
//        "gameReject" to { payload, _ ->
//            getClient(payload.toInt())?.sendNotOkResponse("gameInv")
//        },
//        "move" to func@ { payload, clientNumber ->
//            val client = getClient(clientNumber) ?: return@func
//            val moves = payload.split(";")
//            if(moves.size != 2) {
//                client.sendNotOkResponse("move")
//                return@func
//            }
//            val clientNumber = client.number
//            games.find { it.number == client.gameNumber }!!.move(clientNumber, moves[0].toInt(), moves[1].toInt())
//        }
//    )

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

    fun writeFromServerToClient(message: String, clientNumber: Int) =
        getClient(clientNumber)?.sendPrivateMessage(-1, clientNumber, message) ?: -1

    fun isConnected(clientNumber: Int) = getClient(clientNumber) != null

    override fun process(message: Message.WrapperMessage, client: Client) {
        when(message.msgCase) {
            REGISTER -> {
                val lock = Any()
                val registerMessage = message.register
                var registered: Boolean
                synchronized(lock) {
                    registered = registerNewClient(registerMessage.nick, client)
                }
                client.sendRegisterResponse(registered)
                if(registered) {
                    performForRegisteredClients {
                        sendClientsList(prepareClientsMap())
                    }
                }
            }
            CLIENTSLIST -> {

            }
            GAMEINVITATION -> {
                val gameInvitation = message.gameInvitation
                val clientToInvite = getClient(gameInvitation.to)
                if (clientToInvite == null) {
                    printError("Invitation from ${gameInvitation.from}, ${gameInvitation.to} not connected any more.")
                    client.sendGameInvitationResponse(gameInvitation.from, gameInvitation.to, false)
                    return
                }
                clientToInvite.sendGameInvitation(gameInvitation.from)
            }
            GAMEINVITATIONRESPONSE -> {
                val response = message.gameInvitationResponse
                val invitedClient = getClient(response.to)
                val invitingClient = getClient(response.from) ?: return
                if(invitedClient == null) {
                    invitingClient.sendGameInvitationResponse(response.from, response.to, false)
                    return
                }
                invitingClient.sendGameInvitationResponse(response.from, response.to, response.accepted)
                if(!response.accepted) {
                    return
                }
                val newGame = Game(invitingClient.number, invitedClient.number, this, gamesEverStarted)
                invitingClient.gameNumber = gamesEverStarted
                invitingClient.state = Client.State.IN_GAME
                invitingClient.partnerNumber = invitedClient.number
                invitedClient.gameNumber = gamesEverStarted++
                invitedClient.state = Client.State.IN_GAME
                invitedClient.partnerNumber = invitingClient.number
                games.add(newGame)
                newGame.start()
            }
            MOVE -> {
                val move = message.move
                val game = getGame(move.from)
                game?.move(move.from, move.x, move.y)
            }
            MSG_NOT_SET -> {

            }
        }
    }

    override fun disconnect(clientNumber: Int) {
        val lock = Any()
        val disconnectedClient = getClient(clientNumber)
        synchronized(lock) {
            if(games.find { it.number == disconnectedClient?.gameNumber } != null) {
                games.removeIf { it.number == disconnectedClient?.gameNumber }
                clients.remove(disconnectedClient)
            }
        }
        performForRegisteredClients {
            sendClientsList(prepareClientsMap())
        }
    }

    override fun notifyPartnerAboutDC(partnerNumber: Int, clientNumber: Int) {
        getClient(partnerNumber)?.sendPartnerDisconnected(clientNumber)
    }

    private fun printError(error: String) {
        println("${ConsoleColors.RED}$error${ConsoleColors.RESET}")
    }

    private fun prepareClientsMap(): Map<Int, String> =
            clients.filter {
                if(it == null) {
                    return@filter false
                }
                it.state != Client.State.UNREGISTERED
            }.map {
                it.number to it.nick
            }.toMap()

    private inline fun performForRegisteredClients(function: Client.() -> Unit) {
        val iterator = clients.iterator()
        while(iterator.hasNext()) {
            val client = iterator.next()
            client.function()
        }
    }

    private inline fun performFor(vararg clients: Int, func: Client.() -> Unit) {
        clients.forEach {
            getClient(it)?.func()
        }
    }

    private inline fun broadcast(function: Client.() -> Unit) {
        clients.forEach { it.function() }
    }

    private inline fun executeInlined(func: () -> Unit) {
        func.invoke()
    }

    override fun turn(participant: Int, other: Int) {
        performFor(participant, other) {
            sendTurn(participant)
        }
    }

    override fun move(status: Message.MoveResponse.Status, participant: Int, otherParticipant: Int) {
        performFor(participant, otherParticipant) {
            sendMoveResponse(participant, status)
        }
    }

    override fun start(noughts: Int, crosses: Int) {
        performFor(noughts, crosses) {
            sendGameStarted(noughts, crosses)
        }
    }

    override fun end(winner: Char, participant: Int, otherParticipant: Int) {
        val result = when(winner) {
            'X' -> Message.GameResult.Result.CROSSES
            'O' -> Message.GameResult.Result.NOUGHTS
            ' ' -> Message.GameResult.Result.DRAW
            else -> Message.GameResult.Result.NONE
        }
        performFor(participant, otherParticipant) {
            sendGameResult(result)
        }
    }

    private fun getClient(number: Int) =
        clients.find { it.number == number }

    private fun getGame(clientNumber: Int) =
            games.find { it.number == getClient(clientNumber)?.gameNumber }

    private fun registerNewClient(nick: String, client: Client): Boolean {
        val isNickTaken = clients.any lamb@{
            if (it.state != Client.State.UNREGISTERED) {
                return@lamb it.nick == nick
            }
            false
        }
        if (isNickTaken) {
            return false
        }
        client.nick = nick
        client.state = Client.State.REGISTERED
        return true
    }
}


