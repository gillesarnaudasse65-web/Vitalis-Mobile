package com.vitalis.healthos

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class SelectedDateStateTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-26T00:30:00Z"), ZoneId.of("Africa/Abidjan"))
    private fun state(stored: String? = null, saves: MutableList<String> = mutableListOf()) =
        SelectedDateState(clock, stored) { saves.add(it) }

    @Test fun firstLaunchAndCorruptStorageNormalizeToLocalToday() {
        for (stored in listOf(null, "", "invalid", "2026-02-30")) {
            val saves = mutableListOf<String>()
            assertEquals("2026-09-26", state(stored, saves).selected.toString())
            assertEquals(listOf("2026-09-26"), saves)
        }
    }

    @Test fun historicalSelectionPersistsAcrossRecreationAndTodayIsExplicit() {
        val saves = mutableListOf<String>()
        val original = state(null, saves)
        assertTrue(original.select("2026-09-18"))
        assertEquals("2026-09-18", original.selected.toString())
        val recreated = state(saves.last(), saves)
        assertEquals("2026-09-18", recreated.selected.toString())
        assertEquals("2026-09-26", recreated.today().toString())
        assertEquals("2026-09-26", saves.last())
    }

    @Test fun leapYearBoundariesFutureDatesAndInvalidSelection() {
        val selected = state("2024-02-29")
        assertEquals("2024-02-29", selected.selected.toString())
        assertTrue(selected.select("2027-01-01"))
        assertFalse(selected.select("2026-02-30"))
        assertEquals("2027-01-01", selected.selected.toString())
        assertTrue(selected.select("2025-12-31"))
        assertEquals("2025-12-31", selected.selected.toString())
    }

    @Test fun clockUsesDeviceZoneRatherThanUtcCalendarDay() {
        val moment = Instant.parse("2026-09-25T23:30:00Z")
        val abidjan = SelectedDateState(Clock.fixed(moment, ZoneId.of("Africa/Abidjan")), null) {}
        val paris = SelectedDateState(Clock.fixed(moment, ZoneId.of("Europe/Paris")), null) {}
        assertEquals("2026-09-25", abidjan.selected.toString())
        assertEquals("2026-09-26", paris.selected.toString())
    }
}
