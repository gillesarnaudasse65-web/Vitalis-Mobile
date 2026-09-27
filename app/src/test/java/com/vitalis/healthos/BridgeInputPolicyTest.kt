package com.vitalis.healthos

import org.junit.Assert.*
import org.junit.Test

class BridgeInputPolicyTest {
    @Test fun dateValidationRejectsInvalidAndMalformedDates() {
        assertEquals("2026-09-26", BridgeInputPolicy.date(" 2026-09-26 ")?.toString())
        for (value in listOf(null, "", "2026-02-30", "2026-9-26", "tomorrow", "2026-09-26<script>"))
            assertNull(BridgeInputPolicy.date(value))
    }

    @Test fun requestIdsHaveBoundedSafeCharacters() {
        assertTrue(BridgeInputPolicy.requestId("coach-123:response"))
        for (value in listOf(null, "", " ", "<script>", "x".repeat(121)))
            assertFalse(BridgeInputPolicy.requestId(value))
    }

    @Test fun oversizeAndBlankMealPayloadsAreRejected() {
        assertTrue(BridgeInputPolicy.mealJsonSize("{\"name\":\"Lunch\"}"))
        for (value in listOf(null, "", " ", "x".repeat(32_001)))
            assertFalse(BridgeInputPolicy.mealJsonSize(value))
    }

    @Test fun fakeKeyShapeAndImagePayloadAreBounded() {
        assertTrue(BridgeInputPolicy.apiKey("sk-" + "f".repeat(32)))
        assertFalse(BridgeInputPolicy.apiKey("sk-" + "f".repeat(510)))
        assertFalse(BridgeInputPolicy.apiKey("invalid"))
        assertTrue(BridgeInputPolicy.mealImage("data:image/jpeg;base64," + "A".repeat(40)))
        assertFalse(BridgeInputPolicy.mealImage("data:text/html;base64," + "A".repeat(40)))
        assertFalse(BridgeInputPolicy.mealImage("data:image/jpeg;base64," + "A".repeat(2_100_001)))
        assertFalse(BridgeInputPolicy.mealImage("data:image/jpeg;base64,<script>"))
    }

    @Test fun onlyOrdinaryExternalHttpsCanBeOpenedByBridge() {
        assertTrue(BridgeInputPolicy.externalUrl("https://example.org/account"))
        assertFalse(BridgeInputPolicy.externalUrl("javascript:alert(1)"))
        assertFalse(BridgeInputPolicy.externalUrl("http://example.org/"))
        assertFalse(BridgeInputPolicy.externalUrl("https://vitalis-health-os.gillesarnaudasse65.chatgpt.site/"))
    }
}
