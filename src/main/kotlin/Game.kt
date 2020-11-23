import kotlin.random.Random
import Message.MoveResponse.Status.*

const val BOARD_SIZE = 3

class Game(
    private val firstParticipant: Int,
    private val secondParticipant: Int,
    private val handler: GameHandler,
    val number: Int
) {

    private var turn = -1

    private var moves = 0

    private val participantsChar = mutableMapOf(
        firstParticipant to ' ',
        secondParticipant to ' '
    )

    private val board = MutableList(BOARD_SIZE) {
        MutableList(BOARD_SIZE) { ' ' }
    }

    fun start() {
        if(Random.nextBoolean()) {
            participantsChar[firstParticipant] = 'X'
            participantsChar[secondParticipant] = 'O'
            handler.start(secondParticipant, firstParticipant)
        } else {
            participantsChar[firstParticipant] = 'O'
            participantsChar[secondParticipant] = 'X'
            handler.start(firstParticipant, secondParticipant)
        }

        turn = if(Random.nextBoolean()) {
            firstParticipant
        } else {
            secondParticipant
        }
        handler.turn(turn, getOtherParticipant(turn))
    }

    fun move(participant: Int, row: Int, col: Int) {
        if(participant != turn) {
            handler.move(WRONG_TURN, participant, getOtherParticipant(participant))
            handler.turn(turn, getOtherParticipant(turn))
            return
        }
        if(row !in 0 until BOARD_SIZE || col !in 0 until BOARD_SIZE) {
            handler.move(WRONG_POS, participant, getOtherParticipant(participant))
            return
        }
        performMove(participant, row, col)
        if(moves > 4) {
            val winner = checkForWinner()
            if(winner != -1) {
                handler.end(participantsChar[winner]!!, winner, getOtherParticipant(winner))
            }
            if(checkForDraft()) {
                handler.end(' ', winner, getOtherParticipant(winner))
            }
        }
    }

    private fun performMove(participant: Int, row: Int, col: Int) {
        if (board[row][col] == ' ') {
            board[row][col] = participantsChar[participant]!!
            val otherParticipant = getOtherParticipant(participant)
            handler.move(OK, participant, otherParticipant)
            handler.turn(otherParticipant, participant)
            turn = otherParticipant
            moves++
        } else {
            handler.move(WRONG_POS, participant, getOtherParticipant(participant))
        }
    }

    fun checkForWinner(): Int {
        repeat(BOARD_SIZE) {
            if(board[it].all { it == participantsChar[firstParticipant] }) {
                return firstParticipant
            } else if(board[it].all { it == participantsChar[secondParticipant] }) {
                return secondParticipant
            }
        }
        for(col in 0 until BOARD_SIZE) {
            val firstElement = board[0][col]
            if(firstElement == ' ') {
                continue
            }
            if(board[1][col] == firstElement && board[2][col] == firstElement) {
                return participantsChar.filterValues { it == firstElement }.keys.first()
            }
        }
        for(i in 0..1) {
            val firstElement = board[0][i * (BOARD_SIZE - 1)]
            if(firstElement == ' ') {
                continue
            }
            if(board[1][1] == firstElement && board[2][2 * ((i + 1) % 2)] == firstElement) {
                return participantsChar.filterValues { it == firstElement }.keys.first()
            }
        }
        return -1
    }

    fun checkForDraft(): Boolean =
        board.flatten().find { it == ' ' } == null && checkForWinner() == -1

    private fun getOtherParticipant(participant: Int) =
        participantsChar.keys.first { it != participant }
}