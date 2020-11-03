import java.net.ServerSocket

class Application {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Server(ServerSocket(4321)).run()
        }
    }
}