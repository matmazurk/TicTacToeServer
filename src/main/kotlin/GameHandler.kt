interface GameHandler {
    fun turn(participant: Int, otherParticipant: Int)
    fun move(result: Game.Move, participant: Int, otherParticipant: Int)
    fun start(char: Char, participant: Int, partner: Int)
    fun win(winner: Int, loser: Int)
}