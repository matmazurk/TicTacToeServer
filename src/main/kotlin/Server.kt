import utils.Time.formatMillisToHHMMSS
import java.net.ServerSocket

class Server(
    private val serverSocket: ServerSocket
) {
    private var running: Boolean = false
    private var startTime: Long = 0
    private lateinit var session: Session

    private val commands = mapOf(
        "clients" to {
            printAllConnectedClients()
        },
        "running" to {
            formatMillisToHHMMSS(System.currentTimeMillis() - startTime).also(::println)
        },
        "exit" to {
            running = false
            session.terminate()
            println("Exiting...")
        },
        "msg" to {
            if(session.clientsCount > 0) {
                println("To which client?:")
                printAllConnectedClients()
                val number = readLine()!!.toInt()
                if(session.isConnected(number)) {
                    session.writeFromServerToClient(readLine()!!, number)
                } else {
                    println("Such client is no connected.")
                }
            } else {
                println("Currently no clients.")
            }
        },
    )

    fun run() {
        running = true
        startTime = System.currentTimeMillis()

        session = Session(serverSocket)
        session.establish()

        while(running) {
            val input = readLine()
            if(input in commands.keys) {
                commands[input]!!.invoke()
            } else {
                println("No such command. Available commands:")
                commands.keys.forEach {
                    println(it)
                }
            }
        }
    }

    private fun printAllConnectedClients() {
        if(session.clientsCount > 0) {
            session.connectedClients.forEach {
                print("${it.number}:${it.nick}, ")
            }
            println()
        } else {
            println("No clients yet!")
        }
    }
}