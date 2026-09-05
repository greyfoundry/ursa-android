package dev.astoris.ursa.core.push

import java.time.DayOfWeek
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushQuietHoursTest {

    @Test
    fun disabledScheduleNeverSilencesAlerts() {
        val schedule = PushQuietHours(enabled = false, startMinute = 22 * 60, endMinute = 7 * 60)

        assertFalse(PushQuietHoursPolicy.isQuiet(schedule, DayOfWeek.MONDAY, 23 * 60))
    }

    @Test
    fun sameDayWindowUsesSelectedDayAndExcludesEndBoundary() {
        val schedule = PushQuietHours(
            enabled = true,
            startMinute = 9 * 60,
            endMinute = 17 * 60,
            daysMask = PushQuietHours.dayMask(DayOfWeek.MONDAY),
        )

        assertFalse(PushQuietHoursPolicy.isQuiet(schedule, DayOfWeek.MONDAY, 8 * 60 + 59))
        assertTrue(PushQuietHoursPolicy.isQuiet(schedule, DayOfWeek.MONDAY, 9 * 60))
        assertTrue(PushQuietHoursPolicy.isQuiet(schedule, DayOfWeek.MONDAY, 16 * 60 + 59))
        assertFalse(PushQuietHoursPolicy.isQuiet(schedule, DayOfWeek.MONDAY, 17 * 60))
        assertFalse(PushQuietHoursPolicy.isQuiet(schedule, DayOfWeek.TUESDAY, 10 * 60))
    }

    @Test
    fun overnightWindowAttributesAfterMidnightHoursToPreviousSelectedDay() {
        val schedule = PushQuietHours(
            enabled = true,
            startMinute = 22 * 60,
            endMinute = 7 * 60,
            daysMask = PushQuietHours.dayMask(DayOfWeek.MONDAY),
        )

        assertTrue(PushQuietHoursPolicy.isQuiet(schedule, DayOfWeek.MONDAY, 23 * 60))
        assertTrue(PushQuietHoursPolicy.isQuiet(schedule, DayOfWeek.TUESDAY, 6 * 60 + 59))
        assertFalse(PushQuietHoursPolicy.isQuiet(schedule, DayOfWeek.TUESDAY, 7 * 60))
        assertFalse(PushQuietHoursPolicy.isQuiet(schedule, DayOfWeek.SUNDAY, 23 * 60))
    }

    @Test
    fun equalTimesAndNoSelectedDaysAreSafelyInactive() {
        assertFalse(
            PushQuietHoursPolicy.isQuiet(
                PushQuietHours(enabled = true, startMinute = 60, endMinute = 60),
                DayOfWeek.MONDAY,
                60,
            ),
        )
        assertFalse(
            PushQuietHoursPolicy.isQuiet(
                PushQuietHours(enabled = true, startMinute = 0, endMinute = 60, daysMask = 0),
                DayOfWeek.MONDAY,
                30,
            ),
        )
    }

    @Test
    fun malformedScheduleValuesAreNormalized() {
        assertTrue(PushQuietHours(enabled = true, startMinute = -5, endMinute = 2_000, daysMask = 999).normalized().run {
            startMinute == 0 && endMinute == 1_439 && daysMask == PushQuietHours.ALL_DAYS_MASK
        })
    }
}
