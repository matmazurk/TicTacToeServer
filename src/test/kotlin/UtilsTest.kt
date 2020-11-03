import org.junit.jupiter.api.Test
import utils.Time

class UtilsTest {


    @Test
    fun test_HHMMSS_formatting() {
        val millis = 20000000L
        assert(Time.formatMillisToHHMMSS(millis) == "5:33:20")
    }
}