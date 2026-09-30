package com.vitalis.healthos

import org.junit.Assert.*
import org.junit.Test

class ConnectorCatalogTest {
    @Test fun baselineCatalogHasUniqueStableIdsAndPackages() {
        val entries = ConnectorCatalog.entries
        assertEquals(32, entries.size)
        assertEquals(entries.size, entries.map { it.id }.distinct().size)
        assertTrue(entries.all { it.id.isNotBlank() && it.name.isNotBlank() && it.note.isNotBlank() })
        val packages = entries.flatMap { it.packages }
        assertEquals(packages.size, packages.distinct().size)
    }

    @Test fun stableCatalogEntriesLoadAndUnknownIdsFailClosed() {
        assertEquals("Samsung Health", ConnectorCatalog.find("samsung_health")?.name)
        assertEquals("Apple Health", ConnectorCatalog.find("apple_health")?.name)
        assertNull(ConnectorCatalog.find(null))
        assertNull(ConnectorCatalog.find(""))
        assertNull(ConnectorCatalog.find("unknown_provider"))
    }

    @Test fun healthConnectProvidersUseExplicitCapability() {
        listOf("health_connect", "samsung_health", "fitbit", "withings", "myfitnesspal").forEach { id ->
            assertEquals(ConnectorCapability.HEALTH_CONNECT, ConnectorCatalog.find(id)?.capability)
        }
    }

    @Test fun installedAppWithoutPermissionIsNotConnected() {
        val state = resolve("samsung_health", installed = true, permission = false)
        assertEquals(ConnectorRuntimeState.HEALTH_CONNECT_PERMISSION_REQUIRED, state)
    }

    @Test fun permissionWithoutProviderRecordsIsCautiousNoData() {
        val state = resolve("samsung_health", installed = true, permission = true)
        assertEquals(ConnectorRuntimeState.HEALTH_CONNECT_AVAILABLE_NO_DATA, state)
    }

    @Test fun partialHealthPermissionIsNeverPresentedAsFullyAuthorized() {
        val definition = requireNotNull(ConnectorCatalog.find("samsung_health"))
        val state = ConnectorStateResolver.resolve(
            definition,
            ConnectorEvidence(
                installed = true,
                healthConnectAvailable = true,
                healthConnectPermissionGranted = true,
                providerRecordsDetected = false,
                healthConnectAllPermissionsGranted = false
            )
        )
        assertEquals(ConnectorRuntimeState.HEALTH_CONNECT_PARTIAL_PERMISSION, state)
    }

    @Test fun attributedProviderRecordsAreDataAvailable() {
        val state = resolve("samsung_health", installed = true, permission = true, records = true)
        assertEquals(ConnectorRuntimeState.HEALTH_CONNECT_DATA_AVAILABLE, state)
    }

    @Test fun healthConnectUnavailableOverridesInstallation() {
        val state = resolve("fitbit", installed = true, healthAvailable = false)
        assertEquals(ConnectorRuntimeState.UNAVAILABLE, state)
    }

    @Test fun missingProviderIsNotInstalled() {
        val state = resolve("withings", installed = false, permission = true)
        assertEquals(ConnectorRuntimeState.NOT_INSTALLED, state)
    }

    @Test fun setupOnlyProviderRequiresSetupNotConnection() {
        val state = resolve("fiton", installed = true, permission = true)
        assertEquals(ConnectorRuntimeState.SETUP_REQUIRED, state)
    }

    @Test fun absentDirectOauthIsExplicitlyUnavailable() {
        val definition = requireNotNull(ConnectorCatalog.find("strava"))
        assertEquals(ConnectorCapability.DIRECT_OAUTH, definition.capability)
        assertFalse(definition.directIntegrationImplemented)
        assertNotNull(definition.futureRequirement)
        assertEquals(
            ConnectorRuntimeState.API_UNAVAILABLE,
            ConnectorStateResolver.resolve(definition, evidence(installed = true))
        )
    }

    @Test fun appleHealthIsUnsupportedOnAndroid() {
        val definition = requireNotNull(ConnectorCatalog.find("apple_health"))
        assertEquals(ConnectorCapability.UNSUPPORTED_PLATFORM, definition.capability)
        assertEquals(
            ConnectorRuntimeState.UNSUPPORTED,
            ConnectorStateResolver.resolve(definition, evidence(installed = false))
        )
    }

    @Test fun packageAliasesRemainUnique() {
        val aliases = ConnectorCatalog.entries.flatMap { it.packages }
        assertEquals(aliases.toSet().size, aliases.size)
        assertTrue(ConnectorCatalog.find("mibro_fit")!!.packages.size > 1)
        assertTrue(ConnectorCatalog.find("zwift")!!.packages.size > 1)
    }

    private fun resolve(
        id: String,
        installed: Boolean,
        healthAvailable: Boolean = true,
        permission: Boolean = false,
        records: Boolean = false
    ) = ConnectorStateResolver.resolve(
        requireNotNull(ConnectorCatalog.find(id)),
        evidence(installed, healthAvailable, permission, records)
    )

    private fun evidence(
        installed: Boolean,
        healthAvailable: Boolean = true,
        permission: Boolean = false,
        records: Boolean = false
    ) = ConnectorEvidence(installed, healthAvailable, permission, records)
}
