interface GameHandler {
    fun turn(participant: Int, otherParticipant: Int)
    fun move(status: Message.MoveResponse.Status, participant: Int, otherParticipant: Int)
    fun start(noughts: Int, crosses: Int)
    fun end(winner: Char, participant: Int, otherParticipant: Int)
}