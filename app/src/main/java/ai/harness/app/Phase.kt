package ai.harness.app

import androidx.annotation.StringRes

enum class Phase(@StringRes val labelRes: Int, val durationMs: Long) {
    WORK(R.string.work, 25 * 60_000L),
    REST(R.string.rest, 5 * 60_000L),
    LONG_REST(R.string.long_rest, 15 * 60_000L)
}

data class TimerUiState(
    val phase: Phase = Phase.WORK,
    val remainingMs: Long = Phase.WORK.durationMs,
    val running: Boolean = false,
    val completed: Int = 0
) {
    val progress: Float
        get() = (1f - remainingMs.toFloat() / phase.durationMs.toFloat()).coerceIn(0f, 1f)
}
