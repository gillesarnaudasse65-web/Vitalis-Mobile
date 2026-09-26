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
        assertTrue(entries.all { it.mode in setOf("health_connect", "bridge", "provider", "unsupported_android") })
    }

    @Test fun stableCatalogEntriesLoadAndUnknownIdsFailClosed() {
        assertEquals("Samsung Health", ConnectorCatalog.find("samsung_health")?.name)
        assertEquals("Apple Health", ConnectorCatalog.find("apple_health")?.name)
        assertNull(ConnectorCatalog.find(null))
        assertNull(ConnectorCatalog.find(""))
        assertNull(ConnectorCatalog.find("unknown_provider"))
    }
}
