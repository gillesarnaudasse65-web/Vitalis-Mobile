package com.vitalis.healthos

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HealthConnectReliabilityTest {
    private val permissions = setOf("steps", "sleep", "heart")

    @Test fun availabilityStatesAreExplicit() {
        assertEquals(
            HealthConnectStateCode.NOT_SUPPORTED,
            permission(HealthConnectAvailability.NOT_SUPPORTED).code
        )
        assertEquals(
            HealthConnectStateCode.PROVIDER_NOT_INSTALLED,
            permission(HealthConnectAvailability.PROVIDER_NOT_INSTALLED).code
        )
        assertEquals(
            HealthConnectStateCode.PROVIDER_UPDATE_REQUIRED,
            permission(HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED).code
        )
    }

    @Test fun permissionStatesDistinguishNeverDeniedPartialFullAndRevoked() {
        assertEquals(
            HealthConnectStateCode.PERMISSION_NOT_REQUESTED,
            permission(requested = false).code
        )
        assertEquals(HealthConnectStateCode.PERMISSION_DENIED, permission(requested = true).code)
        val partial = permission(granted = setOf("steps"), requested = true)
        assertEquals(HealthConnectStateCode.PARTIAL_PERMISSION, partial.code)
        assertEquals(listOf("heart", "sleep"), partial.missingPermissions)
        assertEquals(
            HealthConnectStateCode.AUTHORIZED_NO_DATA,
            permission(granted = permissions, requested = true).code
        )
        assertEquals(
            "permission_revoked",
            permission(requested = true, previouslyAuthorized = true).reason
        )
    }

    @Test fun localDayIntervalsCoverNormalLeapMonthAndYearBoundaries() {
        val zone = ZoneId.of("Africa/Abidjan")
        for (date in listOf(
            LocalDate.parse("2026-09-18"),
            LocalDate.parse("2024-02-29"),
            LocalDate.parse("2026-09-30"),
            LocalDate.parse("2025-12-31")
        )) {
            val interval = HealthDayIntervals.forDate(date, zone)
            assertEquals(date, interval.start.atZone(zone).toLocalDate())
            assertEquals(date.plusDays(1), interval.endExclusive.atZone(zone).toLocalDate())
            assertEquals(24, Duration.between(interval.start, interval.endExclusive).toHours())
        }
    }

    @Test fun localDayIntervalsRespectDstAndUtcBoundary() {
        val paris = ZoneId.of("Europe/Paris")
        val spring = HealthDayIntervals.forDate(LocalDate.parse("2026-03-29"), paris)
        val autumn = HealthDayIntervals.forDate(LocalDate.parse("2026-10-25"), paris)
        assertEquals(23, Duration.between(spring.start, spring.endExclusive).toHours())
        assertEquals(25, Duration.between(autumn.start, autumn.endExclusive).toHours())
        val abidjan = HealthDayIntervals.forDate(
            LocalDate.parse("2026-09-26"),
            ZoneId.of("Africa/Abidjan")
        )
        assertEquals("2026-09-26T00:00:00Z", abidjan.start.toString())
    }

    @Test fun pagerReadsOneTwoManyAndEmptyFinalPages() = runBlocking {
        val pages = mapOf(
            null to HealthRecordPage(listOf(FakeRecord("a", 1)), "p2"),
            "p2" to HealthRecordPage(listOf(FakeRecord("b", 2)), "p3"),
            "p3" to HealthRecordPage(emptyList(), null)
        )
        val result = pager().readAll { pages[it] ?: error("unexpected token") }
        assertEquals(listOf("a", "b"), result.map { it.id })
    }

    @Test fun pagerDeduplicatesWithinAndAcrossPagesButKeepsEqualValuesWithDifferentIds() =
        runBlocking {
            val pages = mapOf(
                null to HealthRecordPage(
                    listOf(FakeRecord("a", 10), FakeRecord("a", 10), FakeRecord("b", 10)),
                    "next"
                ),
                "next" to HealthRecordPage(
                    listOf(FakeRecord("a", 11), FakeRecord("c", 10)),
                    null
                )
            )
            val result = pager().readAll { pages[it] ?: error("unexpected token") }
            assertEquals(listOf("a", "b", "c"), result.map { it.id })
            assertEquals(11, result.first { it.id == "a" }.value)
            assertEquals(31, result.sumOf { it.value })
        }

    @Test fun repeatedPageTokenCannotLoopForever() = runBlocking {
        try {
            pager().readAll {
                if (it == null) HealthRecordPage(listOf(FakeRecord("a", 1)), "same")
                else HealthRecordPage(listOf(FakeRecord("b", 2)), "same")
            }
            fail("Expected repeated token failure")
        } catch (error: RepeatedPageTokenException) {
            assertTrue(error.message.orEmpty().contains("same"))
        }
    }

    @Test fun laterPageFailureIsPropagated() = runBlocking {
        try {
            pager().readAll {
                if (it == null) HealthRecordPage(listOf(FakeRecord("a", 1)), "later")
                else throw SecurityException("revoked")
            }
            fail("Expected API failure")
        } catch (error: SecurityException) {
            assertEquals("revoked", error.message)
        }
    }

    @Test fun metricStatusesDistinguishZeroNoDataAuthorizationUnsupportedAndError() {
        val zero = HealthMetricResult.data(0, "count", 1, 1)
        assertEquals(HealthMetricStatus.DATA, zero.status)
        assertEquals(0.0, zero.value!!, 0.0)
        assertEquals(HealthMetricStatus.NO_DATA, HealthMetricResult.noData("count").status)
        assertEquals(
            HealthMetricStatus.NOT_AUTHORIZED,
            HealthMetricResult.notAuthorized("count").status
        )
        assertEquals(HealthMetricStatus.UNSUPPORTED, HealthMetricResult.unsupported("ms").status)
        assertEquals(HealthMetricStatus.ERROR, HealthMetricResult.error("count", "read").status)
    }

    @Test fun repeatedSyncStartsFreshAndOnlyNewRecordChangesTotal() = runBlocking {
        val first = pager().readAll {
            HealthRecordPage(listOf(FakeRecord("a", 10), FakeRecord("b", 20)), null)
        }
        val second = pager().readAll {
            HealthRecordPage(listOf(FakeRecord("a", 10), FakeRecord("b", 20)), null)
        }
        val third = pager().readAll {
            HealthRecordPage(
                listOf(FakeRecord("a", 10), FakeRecord("b", 20), FakeRecord("c", 5)),
                null
            )
        }
        assertEquals(30, first.sumOf { it.value })
        assertEquals(30, second.sumOf { it.value })
        assertEquals(35, third.sumOf { it.value })
    }

    @Test fun staleResponseCannotOverwriteNewerSelectedDate() {
        val coordinator = HealthSyncCoordinator()
        val older = coordinator.begin(LocalDate.parse("2026-09-18"))
        val newer = coordinator.begin(LocalDate.parse("2026-09-19"))
        assertNotNull(coordinator.accept(newer, listOf(FakeRecord("new", 2))))
        assertNull(coordinator.accept(older, listOf(FakeRecord("old", 1))))
        assertFalse(coordinator.isCurrent(older))
        assertTrue(coordinator.isCurrent(newer))
    }

    private fun permission(
        availability: HealthConnectAvailability = HealthConnectAvailability.AVAILABLE,
        granted: Set<String> = emptySet(),
        requested: Boolean = false,
        previouslyAuthorized: Boolean = false
    ) = HealthConnectStateResolver.permissionState(
        availability,
        permissions,
        granted,
        requested,
        previouslyAuthorized
    )

    private fun pager() = HealthRecordPager<FakeRecord>(
        stableId = { it.id },
        fallbackKey = { "fallback:" + it.value }
    )

    private data class FakeRecord(val id: String, val value: Int)
}
