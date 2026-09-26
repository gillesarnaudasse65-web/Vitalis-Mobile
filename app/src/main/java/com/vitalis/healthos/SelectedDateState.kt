package com.vitalis.healthos

import java.time.Clock
import java.time.LocalDate

/** Native calendar-day source of truth. Persistence callback is supplied by MainActivity. */
internal class SelectedDateState(
    private val clock: Clock,
    stored: String?,
    private val persist: (String) -> Unit
) {
    var selected: LocalDate = BridgeInputPolicy.date(stored) ?: LocalDate.now(clock)
        private set

    init { persist(selected.toString()) }

    fun select(iso: String?): Boolean {
        val date = BridgeInputPolicy.date(iso) ?: return false
        selected = date
        persist(date.toString())
        return true
    }

    fun today(): LocalDate {
        selected = LocalDate.now(clock)
        persist(selected.toString())
        return selected
    }

    fun currentToday(): LocalDate = LocalDate.now(clock)
}
