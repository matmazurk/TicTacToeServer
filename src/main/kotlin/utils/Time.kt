package utils

object Time {
    fun formatMillisToHHMMSS(millis: Long): String {
        val inSeconds = millis / 1000
        val seconds = inSeconds % 60
        val minutes = ((inSeconds - seconds) % 3600) / 60
        val hours = (inSeconds - minutes * 60 - seconds) / 3600
        return String.format("%d:%d:%d", hours, minutes, seconds)
    }
}