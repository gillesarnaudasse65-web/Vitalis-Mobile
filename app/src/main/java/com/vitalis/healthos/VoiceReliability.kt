package com.vitalis.healthos

import java.util.UUID

internal enum class RecognitionState {
    IDLE,
    REQUESTING_PERMISSION,
    READY,
    LISTENING,
    PROCESSING_FINAL,
    STOPPING,
    CANCELLED,
    ERROR,
    UNAVAILABLE
}

internal enum class TtsState {
    TTS_UNINITIALIZED,
    TTS_READY,
    TTS_SPEAKING,
    TTS_STOPPED,
    TTS_ERROR
}

internal enum class VoiceResultKind { PARTIAL, FINAL }

internal enum class RecognitionErrorCategory {
    RECOVERABLE,
    PERMISSION,
    NO_SPEECH,
    SERVICE_UNAVAILABLE,
    FATAL
}

internal enum class RecognitionErrorAction { RETRY, RETURN_TO_IDLE, END_SESSION }

internal data class RecognitionSessionSnapshot(
    val sessionId: String?,
    val state: RecognitionState,
    val retryCount: Int,
    val finalResultConsumed: Boolean,
    val stopReason: String?
)

internal class RecognitionSessionCoordinator(
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) {
    companion object {
        const val MAX_TRANSIENT_RETRIES = 1
    }

    private var sessionId: String? = null
    private var state = RecognitionState.IDLE
    private var retryCount = 0
    private var finalResultConsumed = false
    private var stopReason: String? = null

    fun snapshot() = RecognitionSessionSnapshot(
        sessionId = sessionId,
        state = state,
        retryCount = retryCount,
        finalResultConsumed = finalResultConsumed,
        stopReason = stopReason
    )

    fun requestStart(): RecognitionSessionSnapshot {
        sessionId = idFactory()
        state = RecognitionState.REQUESTING_PERMISSION
        retryCount = 0
        finalResultConsumed = false
        stopReason = null
        return snapshot()
    }

    fun permissionGranted(expectedSessionId: String): Boolean = updateIfCurrent(expectedSessionId) {
        state = RecognitionState.READY
    }

    fun permissionDenied(expectedSessionId: String, permanently: Boolean): Boolean =
        updateIfCurrent(expectedSessionId) {
            state = RecognitionState.ERROR
            stopReason = if (permanently) "permission_permanently_denied" else "permission_denied"
        }

    fun markUnavailable(expectedSessionId: String): Boolean = updateIfCurrent(expectedSessionId) {
        state = RecognitionState.UNAVAILABLE
        stopReason = "recognizer_unavailable"
    }

    fun markListening(expectedSessionId: String): Boolean = updateIfCurrent(expectedSessionId) {
        state = RecognitionState.LISTENING
    }

    fun markProcessing(expectedSessionId: String): Boolean = updateIfCurrent(expectedSessionId) {
        state = RecognitionState.PROCESSING_FINAL
    }

    fun acceptPartial(expectedSessionId: String, text: String): Boolean =
        isCurrent(expectedSessionId) && !finalResultConsumed && text.isNotBlank() &&
            state in setOf(RecognitionState.READY, RecognitionState.LISTENING)

    fun consumeFinal(expectedSessionId: String, text: String): Boolean {
        if (!isCurrent(expectedSessionId) || finalResultConsumed || text.isBlank()) return false
        if (state !in setOf(
                RecognitionState.READY,
                RecognitionState.LISTENING,
                RecognitionState.PROCESSING_FINAL
            )) return false
        finalResultConsumed = true
        state = RecognitionState.PROCESSING_FINAL
        return true
    }

    fun handleError(expectedSessionId: String, category: RecognitionErrorCategory): RecognitionErrorAction {
        if (!isCurrent(expectedSessionId) || finalResultConsumed) return RecognitionErrorAction.END_SESSION
        return when (category) {
            RecognitionErrorCategory.RECOVERABLE,
            RecognitionErrorCategory.SERVICE_UNAVAILABLE -> {
                if (retryCount < MAX_TRANSIENT_RETRIES) {
                    retryCount += 1
                    state = RecognitionState.READY
                    RecognitionErrorAction.RETRY
                } else {
                    state = RecognitionState.ERROR
                    stopReason = "retry_exhausted"
                    RecognitionErrorAction.END_SESSION
                }
            }
            RecognitionErrorCategory.NO_SPEECH -> {
                state = RecognitionState.IDLE
                stopReason = "no_speech"
                RecognitionErrorAction.RETURN_TO_IDLE
            }
            RecognitionErrorCategory.PERMISSION -> {
                state = RecognitionState.ERROR
                stopReason = "permission_error"
                RecognitionErrorAction.END_SESSION
            }
            RecognitionErrorCategory.FATAL -> {
                state = RecognitionState.ERROR
                stopReason = "fatal_error"
                RecognitionErrorAction.END_SESSION
            }
        }
    }

    fun stop(expectedSessionId: String?, reason: String, cancelled: Boolean): Boolean {
        if (sessionId == null || (expectedSessionId != null && !isCurrent(expectedSessionId))) return false
        state = if (cancelled) RecognitionState.CANCELLED else RecognitionState.STOPPING
        stopReason = reason
        return true
    }

    fun finish(expectedSessionId: String?): Boolean {
        if (sessionId == null || (expectedSessionId != null && !isCurrent(expectedSessionId))) return false
        state = RecognitionState.IDLE
        sessionId = null
        retryCount = 0
        finalResultConsumed = false
        return true
    }

    fun isCurrent(expectedSessionId: String?): Boolean =
        expectedSessionId != null && expectedSessionId == sessionId

    private inline fun updateIfCurrent(expectedSessionId: String, update: () -> Unit): Boolean {
        if (!isCurrent(expectedSessionId)) return false
        update()
        return true
    }
}

internal data class TtsSnapshot(
    val utteranceId: String?,
    val state: TtsState,
    val fallbackLocaleUsed: Boolean
)

internal class TtsSessionCoordinator {
    private var utteranceId: String? = null
    private var state = TtsState.TTS_UNINITIALIZED
    private var fallbackLocaleUsed = false

    fun snapshot() = TtsSnapshot(utteranceId, state, fallbackLocaleUsed)

    fun initialized(success: Boolean) {
        state = if (success) TtsState.TTS_READY else TtsState.TTS_ERROR
    }

    fun begin(newUtteranceId: String, usedFallbackLocale: Boolean) {
        utteranceId = newUtteranceId
        fallbackLocaleUsed = usedFallbackLocale
        state = TtsState.TTS_SPEAKING
    }

    fun started(expectedUtteranceId: String?): Boolean =
        matches(expectedUtteranceId).also { accepted ->
            if (accepted) state = TtsState.TTS_SPEAKING
        }

    fun completed(expectedUtteranceId: String?): Boolean =
        matches(expectedUtteranceId).also { accepted ->
            if (accepted) {
                utteranceId = null
                state = TtsState.TTS_READY
            }
        }

    fun failed(expectedUtteranceId: String?): Boolean =
        matches(expectedUtteranceId).also { accepted ->
            if (accepted) {
                utteranceId = null
                state = TtsState.TTS_ERROR
            }
        }

    fun stop() {
        utteranceId = null
        state = if (state == TtsState.TTS_UNINITIALIZED) state else TtsState.TTS_STOPPED
    }

    fun readyAfterStop() {
        if (state == TtsState.TTS_STOPPED) state = TtsState.TTS_READY
    }

    fun destroy() {
        utteranceId = null
        state = TtsState.TTS_UNINITIALIZED
        fallbackLocaleUsed = false
    }

    private fun matches(expectedUtteranceId: String?): Boolean =
        expectedUtteranceId != null && expectedUtteranceId == utteranceId
}
