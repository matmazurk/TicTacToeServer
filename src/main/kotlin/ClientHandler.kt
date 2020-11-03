interface ClientHandler {
    fun process(message: String?, clientNumber: Int)
    fun disconnect(clientNumber: Int)
    fun notifyPartnerAboutDC(partnerNumber: Int, clientNumber: Int)
}