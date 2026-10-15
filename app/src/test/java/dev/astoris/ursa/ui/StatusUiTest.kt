package dev.astoris.ursa.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.astoris.ursa.data.model.MonitorStatus
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusUiTest {
    @Test
    fun statusPillTextMeetsNormalTextContrast() {
        MonitorStatus.entries.forEach { status ->
            val ratio = contrastRatio(StatusUi.contentColor(status), StatusUi.color(status))
            assertTrue("$status contrast was $ratio", ratio >= 4.5f)
        }
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
