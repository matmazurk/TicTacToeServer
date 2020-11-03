interface ClientHandler {
    fun process(message: Message.WrapperMessage, clientNumber: Int)
    fun disconnect(clientNumber: Int)
    fun notifyPartnerAboutDC(partnerNumber: Int, clientNumber: Int)
}