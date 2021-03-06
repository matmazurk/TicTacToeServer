import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import Message.MoveResponse.Status.*

private const val FIRST_PARTICIPANT = 1
private const val SECOND_PARTICIPANT = 2

class GameTest {

    private val gameHandler = mock(GameHandler::class.java)
    private val game = Game(FIRST_PARTICIPANT, SECOND_PARTICIPANT, gameHandler, 0)
    private var firstTurnParticipantChar = ' '
    private var secondTurnParticipantChar = ' '
    private var firstTurnParticipant = -1
    private var secondTurnParticipant = -1

    @BeforeEach
    fun setup() {
        game.start()

        val intCaptor: ArgumentCaptor<Int> = ArgumentCaptor.forClass(Int::class.java)
        verify(gameHandler).start(intCaptor.capture(), intCaptor.capture())

        val firstTurnParticipantCaptor = ArgumentCaptor.forClass(Int::class.java)
        verify(gameHandler).turn(firstTurnParticipantCaptor.capture(), firstTurnParticipantCaptor.capture())
        firstTurnParticipant = firstTurnParticipantCaptor.allValues[0]
        secondTurnParticipant = firstTurnParticipantCaptor.allValues[1]
        assert(setOf(FIRST_PARTICIPANT, SECOND_PARTICIPANT).find { it != firstTurnParticipant }!! == secondTurnParticipant)

        val participants = listOf(FIRST_PARTICIPANT, SECOND_PARTICIPANT)
        assert(firstTurnParticipant in participants)
        assert(secondTurnParticipant in participants)

        if(firstTurnParticipant == intCaptor.allValues[0] && secondTurnParticipant == intCaptor.allValues[1]) {
            firstTurnParticipantChar = 'O'
            secondTurnParticipantChar = 'X'

        } else if(firstTurnParticipant == intCaptor.allValues[1] && secondTurnParticipant == intCaptor.allValues[0]) {
            firstTurnParticipantChar = 'X'
            secondTurnParticipantChar = 'O'
        }
        val availableChartsList = listOf('X', 'O')
        assert(firstTurnParticipantChar in availableChartsList)
        assert(secondTurnParticipantChar in availableChartsList)
    }

    @Test
    fun test_game_move_out_of_bounds() {
        game.move(firstTurnParticipant, BOARD_SIZE , -1)
        game.move(firstTurnParticipant, BOARD_SIZE + 1, 0)
        game.move(firstTurnParticipant, BOARD_SIZE + 1, -1)
        verify(gameHandler, times(3)).move(WRONG_POS, firstTurnParticipant, secondTurnParticipant)
    }

    @Test
    fun test_game_move_cell_already_set() {
        game.move(firstTurnParticipant, 0, 0)
        verify(gameHandler).move(OK, firstTurnParticipant, secondTurnParticipant)
        verify(gameHandler).turn(secondTurnParticipant, firstTurnParticipant)
        game.move(secondTurnParticipant,  0, 0)
        verify(gameHandler).move(WRONG_POS, secondTurnParticipant, firstTurnParticipant)
    }

    @Test
    fun test_game_wrong_turn() {
        game.move(secondTurnParticipant, 0, 0)
        verify(gameHandler).move(WRONG_TURN, secondTurnParticipant, firstTurnParticipant)
    }

    @Test
    fun test_for_winner_empty_board() {
        assert(-1 == game.checkForWinner())
    }

    @Test
    fun test_for_winner_won_horizontally() {
        game.move(firstTurnParticipant, 2, 0)
        game.move(secondTurnParticipant, 1, 0)
        game.move(firstTurnParticipant, 2, 1)
        game.move(secondTurnParticipant, 1, 1)
        game.move(firstTurnParticipant, 2, 2)
        assert(firstTurnParticipant == game.checkForWinner())
    }

    @Test
    fun test_for_winner_won_vertically() {
        game.move(firstTurnParticipant, 0, 0)
        game.move(secondTurnParticipant, 1, 1)
        game.move(firstTurnParticipant, 1, 0)
        game.move(secondTurnParticipant, 1, 2)
        game.move(firstTurnParticipant, 2, 0)
        assert(firstTurnParticipant == game.checkForWinner())
    }

    @Test
    fun test_for_winner_won_slant_left_to_right() {
        game.move(firstTurnParticipant, 1, 0)
        game.move(secondTurnParticipant, 0, 0)
        game.move(firstTurnParticipant, 1, 2)
        game.move(secondTurnParticipant, 1, 1)
        game.move(firstTurnParticipant, 0, 2)
        game.move(secondTurnParticipant, 2, 2)
        assert(secondTurnParticipant == game.checkForWinner())
    }

    @Test
    fun test_for_winner_won_slant_right_to_left() {
        game.move(firstTurnParticipant, 1, 0)
        game.move(secondTurnParticipant, 2, 0)
        game.move(firstTurnParticipant, 1, 2)
        game.move(secondTurnParticipant, 1, 1)
        game.move(firstTurnParticipant, 0, 1)
        game.move(secondTurnParticipant, 0, 2)
        assert(secondTurnParticipant == game.checkForWinner())
    }

    @Test
    fun test_draft_board_empty() {
        assert(!game.checkForDraft())
    }

    @Test
    fun test_draft_full_board_with_winner() {
        game.move(firstTurnParticipant, 0, 0)
        game.move(secondTurnParticipant, 0, 1)
        game.move(firstTurnParticipant, 0, 2)
        game.move(secondTurnParticipant, 1, 0)
        game.move(firstTurnParticipant, 1, 1)
        game.move(secondTurnParticipant, 1, 2)
        game.move(firstTurnParticipant, 2, 0)
        game.move(secondTurnParticipant, 2, 1)
        game.move(firstTurnParticipant, 2, 2)
        assert(!game.checkForDraft())
    }

    @Test
    fun test_draft_full_board_no_winner() {
        game.move(firstTurnParticipant, 0, 0)
        game.move(secondTurnParticipant, 0, 2)
        game.move(firstTurnParticipant, 0, 1)
        game.move(secondTurnParticipant, 1, 0)
        game.move(firstTurnParticipant, 1, 2)
        game.move(secondTurnParticipant, 1, 1)
        game.move(firstTurnParticipant, 2, 0)
        game.move(secondTurnParticipant, 2, 1)
        game.move(firstTurnParticipant, 2, 2)
        assert(game.checkForDraft())
    }
}