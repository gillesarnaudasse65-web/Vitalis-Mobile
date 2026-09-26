package com.vitalis.healthos

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlin.reflect.KClass

class AndroidHealthConnectDataSource(private val client: HealthConnectClient) {
    suspend fun <T : Record> readAll(
        recordType: KClass<T>,
        timeRangeFilter: TimeRangeFilter
    ): List<T> {
        val pager = HealthRecordPager<T>(
            stableId = { it.metadata.id },
            fallbackKey = {
                listOf(
                    recordType.qualifiedName.orEmpty(),
                    it.metadata.dataOrigin.packageName,
                    it.toString()
                ).joinToString("|")
            }
        )
        return pager.readAll { pageToken ->
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = recordType,
                    timeRangeFilter = timeRangeFilter,
                    pageToken = pageToken
                )
            )
            HealthRecordPage(response.records, response.pageToken)
        }
    }
}
