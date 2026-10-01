package com.dexter.notify

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

class GoalReminderTest {
    @Test
    fun laterTodayWaitsUntilThen() {
        assertEquals(Duration.ofMinutes(150), delayUntilHour(LocalDateTime.of(2026, 10, 1, 17, 30), 20))
    }

    @Test
    fun pastHourWaitsUntilTomorrow() {
        assertEquals(Duration.ofHours(23), delayUntilHour(LocalDateTime.of(2026, 10, 1, 21, 0), 20))
        assertEquals(Duration.ofHours(24), delayUntilHour(LocalDateTime.of(2026, 10, 1, 20, 0), 20))
    }
}
