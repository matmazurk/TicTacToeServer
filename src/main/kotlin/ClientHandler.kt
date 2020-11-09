interface ClientHandler {
    fun process(message: Message.WrapperMessage, client: Client)
    fun disconnect(clientNumber: Int)
    fun notifyPartnerAboutDC(partnerNumber: Int, clientNumber: Int)
}