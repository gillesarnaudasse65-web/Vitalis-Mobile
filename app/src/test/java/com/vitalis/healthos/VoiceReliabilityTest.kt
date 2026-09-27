package com.vitalis.healthos

import org.junit.Assert.*
import org.junit.Test

class VoiceReliabilityTest {
    @Test fun explicitStartCreatesOnePermissionRequestSession() {
        val coordinator = coordinator()
        val state = coordinator.requestStart()
        assertEquals("voice-1", state.sessionId)
        assertEquals(RecognitionState.REQUESTING_PERMISSION, state.state)
    }

    @Test fun grantedSessionCanListen() {
        val coordinator = coordinator()
        coordinator.requestStart()
        assertTrue(coordinator.permissionGranted("voice-1"))
        assertTrue(coordinator.markListening("voice-1"))
        assertEquals(RecognitionState.LISTENING, coordinator.snapshot().state)
    }

    @Test fun deniedPermissionEndsInControlledError() {
        val coordinator = coordinator()
        coordinator.requestStart()
        assertTrue(coordinator.permissionDenied("voice-1", false))
        assertEquals("permission_denied", coordinator.snapshot().stopReason)
    }

    @Test fun permanentlyDeniedPermissionIsDistinct() {
        val coordinator = coordinator()
        coordinator.requestStart()
        coordinator.permissionDenied("voice-1", true)
        assertEquals("permission_permanently_denied", coordinator.snapshot().stopReason)
    }

    @Test fun unavailableRecognizerHasExplicitState() {
        val coordinator = coordinator()
        coordinator.requestStart()
        coordinator.markUnavailable("voice-1")
        assertEquals(RecognitionState.UNAVAILABLE, coordinator.snapshot().state)
    }

    @Test fun partialResultDoesNotConsumeFinal() {
        val coordinator = listeningCoordinator()
        assertTrue(coordinator.acceptPartial("voice-1", "bonjour"))
        assertFalse(coordinator.snapshot().finalResultConsumed)
    }

    @Test fun finalResultIsConsumedExactlyOnce() {
        val coordinator = listeningCoordinator()
        assertTrue(coordinator.consumeFinal("voice-1", "message final"))
        assertFalse(coordinator.consumeFinal("voice-1", "message final"))
    }

    @Test fun emptyFinalResultIsRejected() {
        val coordinator = listeningCoordinator()
        assertFalse(coordinator.consumeFinal("voice-1", "  "))
    }

    @Test fun finalAfterCancelIsRejected() {
        val coordinator = listeningCoordinator()
        coordinator.stop("voice-1", "user_cancel", true)
        assertFalse(coordinator.consumeFinal("voice-1", "late"))
    }

    @Test fun backgroundStopRejectsLateFinal() {
        val coordinator = listeningCoordinator()
        coordinator.stop("voice-1", "activity_background", true)
        assertFalse(coordinator.consumeFinal("voice-1", "late"))
    }

    @Test fun newerSessionSupersedesOlderSession() {
        var sequence = 0
        val coordinator = RecognitionSessionCoordinator { "voice-${++sequence}" }
        coordinator.requestStart()
        coordinator.requestStart()
        assertFalse(coordinator.consumeFinal("voice-1", "old"))
        coordinator.permissionGranted("voice-2")
        coordinator.markListening("voice-2")
        assertTrue(coordinator.consumeFinal("voice-2", "new"))
    }

    @Test fun recoverableFailureRetriesOnceOnly() {
        val coordinator = listeningCoordinator()
        assertEquals(
            RecognitionErrorAction.RETRY,
            coordinator.handleError("voice-1", RecognitionErrorCategory.RECOVERABLE)
        )
        assertEquals(
            RecognitionErrorAction.END_SESSION,
            coordinator.handleError("voice-1", RecognitionErrorCategory.RECOVERABLE)
        )
        assertEquals(1, coordinator.snapshot().retryCount)
    }

    @Test fun serviceFailureUsesSameBoundedRetryBudget() {
        val coordinator = listeningCoordinator()
        assertEquals(
            RecognitionErrorAction.RETRY,
            coordinator.handleError("voice-1", RecognitionErrorCategory.SERVICE_UNAVAILABLE)
        )
        assertEquals(
            RecognitionErrorAction.END_SESSION,
            coordinator.handleError("voice-1", RecognitionErrorCategory.SERVICE_UNAVAILABLE)
        )
    }

    @Test fun noSpeechReturnsToIdleWithoutRetry() {
        val coordinator = listeningCoordinator()
        assertEquals(
            RecognitionErrorAction.RETURN_TO_IDLE,
            coordinator.handleError("voice-1", RecognitionErrorCategory.NO_SPEECH)
        )
        assertEquals(RecognitionState.IDLE, coordinator.snapshot().state)
        assertEquals(0, coordinator.snapshot().retryCount)
    }

    @Test fun permissionErrorNeverRetries() {
        val coordinator = listeningCoordinator()
        assertEquals(
            RecognitionErrorAction.END_SESSION,
            coordinator.handleError("voice-1", RecognitionErrorCategory.PERMISSION)
        )
        assertEquals(0, coordinator.snapshot().retryCount)
    }

    @Test fun finishClearsSessionIdentity() {
        val coordinator = listeningCoordinator()
        assertTrue(coordinator.finish("voice-1"))
        assertNull(coordinator.snapshot().sessionId)
        assertEquals(RecognitionState.IDLE, coordinator.snapshot().state)
    }

    @Test fun ttsInitializationSuccessBecomesReady() {
        val coordinator = TtsSessionCoordinator()
        coordinator.initialized(true)
        assertEquals(TtsState.TTS_READY, coordinator.snapshot().state)
    }

    @Test fun ttsInitializationFailureIsControlled() {
        val coordinator = TtsSessionCoordinator()
        coordinator.initialized(false)
        assertEquals(TtsState.TTS_ERROR, coordinator.snapshot().state)
    }

    @Test fun ttsSpeakTracksUtteranceIdentity() {
        val coordinator = readyTts()
        coordinator.begin("utterance-1", false)
        assertTrue(coordinator.started("utterance-1"))
        assertEquals(TtsState.TTS_SPEAKING, coordinator.snapshot().state)
    }

    @Test fun newerTtsReplacementRejectsOldCallback() {
        val coordinator = readyTts()
        coordinator.begin("utterance-1", false)
        coordinator.begin("utterance-2", false)
        assertFalse(coordinator.completed("utterance-1"))
        assertTrue(coordinator.completed("utterance-2"))
    }

    @Test fun ttsStopClearsActiveUtterance() {
        val coordinator = readyTts()
        coordinator.begin("utterance-1", false)
        coordinator.stop()
        assertNull(coordinator.snapshot().utteranceId)
        assertEquals(TtsState.TTS_STOPPED, coordinator.snapshot().state)
    }

    @Test fun ttsErrorCallbackIsBoundToCurrentUtterance() {
        val coordinator = readyTts()
        coordinator.begin("utterance-1", false)
        assertTrue(coordinator.failed("utterance-1"))
        assertEquals(TtsState.TTS_ERROR, coordinator.snapshot().state)
    }

    @Test fun ttsDestroyReleasesSession() {
        val coordinator = readyTts()
        coordinator.begin("utterance-1", true)
        coordinator.destroy()
        assertNull(coordinator.snapshot().utteranceId)
        assertEquals(TtsState.TTS_UNINITIALIZED, coordinator.snapshot().state)
    }

    private fun coordinator() = RecognitionSessionCoordinator { "voice-1" }

    private fun listeningCoordinator() = coordinator().apply {
        requestStart()
        permissionGranted("voice-1")
        markListening("voice-1")
    }

    private fun readyTts() = TtsSessionCoordinator().apply { initialized(true) }
}
