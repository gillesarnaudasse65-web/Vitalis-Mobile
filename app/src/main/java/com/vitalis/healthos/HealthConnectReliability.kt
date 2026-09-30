package com.vitalis.healthos

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong

enum class HealthConnectAvailability {
    AVAILABLE,
    NOT_SUPPORTED,
    PROVIDER_NOT_INSTALLED,
    PROVIDER_UPDATE_REQUIRED
}

enum class HealthConnectStateCode {
    NOT_SUPPORTED,
    PROVIDER_NOT_INSTALLED,
    PROVIDER_UPDATE_REQUIRED,
    PERMISSION_NOT_REQUESTED,
    PERMISSION_DENIED,
    PARTIAL_PERMISSION,
    AUTHORIZED_NO_DATA,
    AUTHORIZED_WITH_DATA,
    SYNC_ERROR
}

enum class HealthConnectUiState {
    AVAILABLE,
    PERMISSION_REQUIRED,
    PARTIAL_PERMISSION,
    AUTHORIZED,
    NO_DATA,
    DATA_AVAILABLE,
    PROVIDER_UPDATE_REQUIRED,
    UNAVAILABLE,
    ERROR
}

object HealthConnectUiStateResolver {
    fun resolve(code: HealthConnectStateCode): HealthConnectUiState = when (code) {
        HealthConnectStateCode.NOT_SUPPORTED,
        HealthConnectStateCode.PROVIDER_NOT_INSTALLED -> HealthConnectUiState.UNAVAILABLE
        HealthConnectStateCode.PROVIDER_UPDATE_REQUIRED ->
            HealthConnectUiState.PROVIDER_UPDATE_REQUIRED
        HealthConnectStateCode.PERMISSION_NOT_REQUESTED,
        HealthConnectStateCode.PERMISSION_DENIED -> HealthConnectUiState.PERMISSION_REQUIRED
        HealthConnectStateCode.PARTIAL_PERMISSION -> HealthConnectUiState.PARTIAL_PERMISSION
        HealthConnectStateCode.AUTHORIZED_NO_DATA -> HealthConnectUiState.NO_DATA
        HealthConnectStateCode.AUTHORIZED_WITH_DATA -> HealthConnectUiState.DATA_AVAILABLE
        HealthConnectStateCode.SYNC_ERROR -> HealthConnectUiState.ERROR
    }
}

data class HealthConnectStateModel(
    val code: HealthConnectStateCode,
    val label: String,
    val reason: String? = null,
    val retryAction: String? = null,
    val missingPermissions: List<String> = emptyList()
)

object HealthConnectStateResolver {
    fun permissionState(
        availability: HealthConnectAvailability,
        requiredPermissions: Set<String>,
        grantedPermissions: Set<String>,
        permissionRequested: Boolean,
        previouslyAuthorized: Boolean
    ): HealthConnectStateModel {
        when (availability) {
            HealthConnectAvailability.NOT_SUPPORTED -> return HealthConnectStateModel(
                HealthConnectStateCode.NOT_SUPPORTED,
                "Health Connect non pris en charge"
            )
            HealthConnectAvailability.PROVIDER_NOT_INSTALLED -> return HealthConnectStateModel(
                HealthConnectStateCode.PROVIDER_NOT_INSTALLED,
                "Health Connect n’est pas installé",
                retryAction = "install_provider"
            )
            HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED -> return HealthConnectStateModel(
                HealthConnectStateCode.PROVIDER_UPDATE_REQUIRED,
                "Health Connect doit être mis à jour",
                retryAction = "update_provider"
            )
            HealthConnectAvailability.AVAILABLE -> Unit
        }
        val granted = requiredPermissions.intersect(grantedPermissions)
        val missing = (requiredPermissions - granted).sorted()
        if (granted.isEmpty()) {
            if (!permissionRequested && !previouslyAuthorized) {
                return HealthConnectStateModel(
                    HealthConnectStateCode.PERMISSION_NOT_REQUESTED,
                    "Autorisation Health Connect requise",
                    retryAction = "request_permissions",
                    missingPermissions = missing
                )
            }
            return HealthConnectStateModel(
                HealthConnectStateCode.PERMISSION_DENIED,
                "Autorisation Health Connect refusée",
                reason = if (previouslyAuthorized) "permission_revoked" else "permission_denied",
                retryAction = "request_permissions",
                missingPermissions = missing
            )
        }
        if (missing.isNotEmpty()) {
            return HealthConnectStateModel(
                HealthConnectStateCode.PARTIAL_PERMISSION,
                "Autorisation Health Connect partielle",
                retryAction = "request_permissions",
                missingPermissions = missing
            )
        }
        return HealthConnectStateModel(
            HealthConnectStateCode.AUTHORIZED_NO_DATA,
            "Health Connect autorisé, aucune donnée"
        )
    }
}

enum class HealthMetricStatus {
    DATA,
    NO_DATA,
    NOT_AUTHORIZED,
    UNSUPPORTED,
    ERROR
}

data class HealthMetricResult(
    val status: HealthMetricStatus,
    val value: Double? = null,
    val unit: String,
    val sampleCount: Int = 0,
    val sourceCount: Int = 0,
    val errorCode: String? = null
) {
    companion object {
        fun data(value: Number, unit: String, sampleCount: Int, sourceCount: Int) =
            HealthMetricResult(
                HealthMetricStatus.DATA,
                value.toDouble(),
                unit,
                sampleCount,
                sourceCount
            )

        fun noData(unit: String) = HealthMetricResult(HealthMetricStatus.NO_DATA, unit = unit)
        fun notAuthorized(unit: String) =
            HealthMetricResult(HealthMetricStatus.NOT_AUTHORIZED, unit = unit)
        fun unsupported(unit: String) =
            HealthMetricResult(HealthMetricStatus.UNSUPPORTED, unit = unit)
        fun error(unit: String, code: String) =
            HealthMetricResult(HealthMetricStatus.ERROR, unit = unit, errorCode = code)
    }
}

data class HealthDayInterval(
    val date: LocalDate,
    val start: Instant,
    val endExclusive: Instant
)

object HealthDayIntervals {
    fun forDate(date: LocalDate, zoneId: ZoneId): HealthDayInterval =
        HealthDayInterval(
            date,
            date.atStartOfDay(zoneId).toInstant(),
            date.plusDays(1).atStartOfDay(zoneId).toInstant()
        )
}

data class HealthRecordPage<T>(
    val records: List<T>,
    val nextPageToken: String?
)

class RepeatedPageTokenException(token: String) :
    IllegalStateException("Health Connect repeated page token: " + token)

class HealthRecordPager<T>(
    private val stableId: (T) -> String?,
    private val fallbackKey: (T) -> String
) {
    suspend fun readAll(fetch: suspend (String?) -> HealthRecordPage<T>): List<T> {
        val records = linkedMapOf<String, T>()
        val seenTokens = mutableSetOf<String>()
        var token: String? = null
        do {
            val page = fetch(token)
            page.records.forEach { record ->
                val key = stableId(record)?.takeIf { it.isNotBlank() } ?: fallbackKey(record)
                records[key] = record
            }
            val next = page.nextPageToken?.takeIf { it.isNotBlank() }
            if (next != null && !seenTokens.add(next)) throw RepeatedPageTokenException(next)
            token = next
        } while (token != null)
        return records.values.toList()
    }
}

data class HealthSyncRequest(val generation: Long, val selectedDate: LocalDate)

data class HealthSyncSnapshot<T>(
    val selectedDate: LocalDate,
    val records: List<T>
)

class HealthSyncCoordinator {
    private val generation = AtomicLong(0)

    fun begin(selectedDate: LocalDate): HealthSyncRequest =
        HealthSyncRequest(generation.incrementAndGet(), selectedDate)

    fun isCurrent(request: HealthSyncRequest): Boolean = request.generation == generation.get()

    fun <T> accept(request: HealthSyncRequest, records: List<T>): HealthSyncSnapshot<T>? =
        if (isCurrent(request)) HealthSyncSnapshot(request.selectedDate, records) else null
}
