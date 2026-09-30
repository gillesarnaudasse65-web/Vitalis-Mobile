package com.vitalis.healthos

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class ReliabilityStressTest {
    @Test fun twentyDateChangesKeepOnlyTheLastExplicitSelection() {
        var persisted: String? = null
        val state = SelectedDateState(
            Clock.fixed(Instant.parse("2026-09-30T00:00:00Z"), ZoneOffset.UTC),
            null
        ) { persisted = it }
        repeat(20) { index ->
            val expected = LocalDate.parse("2026-09-01").plusDays(index.toLong()).toString()
            assertTrue(state.select(expected))
            assertEquals(expected, state.selected.toString())
            assertEquals(expected, persisted)
        }
    }

    @Test fun twentyNutritionSessionsRejectEverySupersededResult() {
        val coordinator = NutritionScanCoordinator()
        var previousScan: String? = null
        repeat(20) { index ->
            val scanId = "stress-scan-$index"
            coordinator.begin(
                scanId,
                LocalDate.parse("2026-09-30"),
                NutritionImageSource.GALLERY,
                Instant.parse("2026-09-30T00:00:00Z").plusSeconds(index.toLong())
            )
            coordinator.markImageReady(scanId, metadata())
            coordinator.startAnalysis(scanId, "stress-request-$index")
            previousScan?.let { stale ->
                assertNull(coordinator.acceptAnalysis(stale, "stress-request-${index - 1}", estimate()))
            }
            previousScan = scanId
        }
        assertEquals("stress-scan-19", coordinator.active()?.scanId)
    }

    @Test fun repeatedConnectorEvidenceNeverTurnsInstallationIntoConnection() {
        val definition = requireNotNull(ConnectorCatalog.find("samsung_health"))
        repeat(20) {
            val state = ConnectorStateResolver.resolve(
                definition,
                ConnectorEvidence(
                    installed = true,
                    healthConnectAvailable = true,
                    healthConnectPermissionGranted = false,
                    providerRecordsDetected = false
                )
            )
            assertEquals(ConnectorRuntimeState.HEALTH_CONNECT_PERMISSION_REQUIRED, state)
        }
    }

    private fun metadata() = NormalizedImageMetadata(
        "image/jpeg", 100, 20, 20, "image/jpeg", 80, 20, 20, false
    )

    private fun estimate() = NutritionEstimate(
        foodItems = listOf(NutritionFoodItem("Test")),
        mealName = "Test",
        nutrients = NutritionNutrients(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0),
        confidence = 1.0,
        portionDescription = null,
        uncertaintyNotes = null
    )
}
