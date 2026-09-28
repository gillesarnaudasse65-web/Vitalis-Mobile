package com.vitalis.healthos

import java.io.IOException
import java.net.SocketTimeoutException
import org.json.JSONException

enum class NutritionUiState {
    PHOTO_READY,
    AI_KEY_REQUIRED,
    AI_CONSENT_REQUIRED,
    READY_FOR_ANALYSIS,
    ANALYZING,
    ANALYSIS_SUCCESS,
    ANALYSIS_ERROR,
    API_AUTH_ERROR,
    API_QUOTA_ERROR,
    NETWORK_ERROR,
    INVALID_RESPONSE
}

object NutritionUiStateResolver {
    fun resolve(
        hasNormalizedPhoto: Boolean,
        aiConfigured: Boolean,
        consented: Boolean,
        status: NutritionScanStatus,
        errorCode: String? = null
    ): NutritionUiState {
        if (status == NutritionScanStatus.ANALYZING) return NutritionUiState.ANALYZING
        if (status == NutritionScanStatus.REVIEW || status == NutritionScanStatus.SAVED) {
            return NutritionUiState.ANALYSIS_SUCCESS
        }
        if (status == NutritionScanStatus.ERROR) return when (errorCode) {
            "api_auth_error" -> NutritionUiState.API_AUTH_ERROR
            "api_quota_error" -> NutritionUiState.API_QUOTA_ERROR
            "network_error", "timeout", "service_unavailable" -> NutritionUiState.NETWORK_ERROR
            "invalid_response" -> NutritionUiState.INVALID_RESPONSE
            else -> NutritionUiState.ANALYSIS_ERROR
        }
        if (!hasNormalizedPhoto) return NutritionUiState.PHOTO_READY
        if (!aiConfigured) return NutritionUiState.AI_KEY_REQUIRED
        if (!consented) return NutritionUiState.AI_CONSENT_REQUIRED
        return NutritionUiState.READY_FOR_ANALYSIS
    }
}

data class AiRequestFailure(val code: String, val userMessage: String)

class OpenAiHttpException(val statusCode: Int) : IOException("OpenAI HTTP $statusCode")

class InvalidAiResponseException : IllegalArgumentException("invalid_ai_response")

object AiRequestFailureClassifier {
    fun classify(error: Throwable): AiRequestFailure = when (error) {
        is OpenAiHttpException -> when (error.statusCode) {
            401, 403 -> AiRequestFailure("api_auth_error", "Clé API invalide.")
            429 -> AiRequestFailure("api_quota_error", "Quota ou limite de requêtes atteint.")
            in 500..599 -> AiRequestFailure(
                "service_unavailable",
                "Service temporairement indisponible. Réessayez plus tard."
            )
            else -> AiRequestFailure("analysis_error", "L’analyse a échoué.")
        }
        is SocketTimeoutException -> AiRequestFailure("timeout", "Délai dépassé. Réessayez.")
        is InvalidAiResponseException,
        is JSONException,
        is IllegalArgumentException -> AiRequestFailure(
            "invalid_response",
            "Réponse non exploitable. Réessayez l’analyse."
        )
        is IOException -> AiRequestFailure("network_error", "Connexion impossible. Vérifiez le réseau.")
        else -> AiRequestFailure("analysis_error", "L’analyse a échoué. Réessayez.")
    }
}
