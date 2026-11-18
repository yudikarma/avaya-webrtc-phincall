package com.phincall.webrtc.config

import android.content.Context
import com.avaya.ocs.Services.Work.Enums.PlatformType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

enum class CallOutcome {
    INITIATED,
    CONNECTED,
    FAILED
}

data class RecentCallRecord(
    val id: String,
    val destinationNumber: String,
    val displayName: String,
    val platformType: PlatformType,
    val mediaMode: CallMediaMode,
    val timestampMillis: Long,
    val durationMillis: Long = 0L,
    val outcome: CallOutcome = CallOutcome.INITIATED
)

/**
 * Persists recent call metadata in app-private SharedPreferences (up to 25 entries).
 * Only destination/platform/media/duration metadata is stored; tokens and API keys are never saved.
 */
class CallHistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val seq = AtomicInteger(0)

    private val _history = MutableStateFlow(loadStored())
    val history: StateFlow<List<RecentCallRecord>> = _history.asStateFlow()

    fun recordCallStart(config: InteractionLaunchConfig, nowMillis: Long = System.currentTimeMillis()): String {
        val id = "${nowMillis}_${seq.incrementAndGet()}"
        val entry = RecentCallRecord(
            id = id,
            destinationNumber = config.callerProfile.destinationNumber.trim(),
            displayName = config.callerProfile.displayName.trim(),
            platformType = config.platformType,
            mediaMode = config.mediaMode,
            timestampMillis = nowMillis,
            durationMillis = 0L,
            outcome = CallOutcome.INITIATED
        )
        val updated = (listOf(entry) + _history.value).take(MAX_RECORDS)
        saveAndEmit(updated)
        return id
    }

    fun updateCallResult(recordId: String, durationMillis: Long, outcome: CallOutcome) {
        val current = _history.value
        val index = current.indexOfFirst { it.id == recordId }
        if (index == -1) return
        val mutable = current.toMutableList()
        mutable[index] = mutable[index].copy(
            durationMillis = durationMillis.coerceAtLeast(0L),
            outcome = outcome
        )
        saveAndEmit(mutable)
    }

    fun clearAll() {
        saveAndEmit(emptyList())
    }

    private fun loadStored(): List<RecentCallRecord> {
        val raw = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return parseRecords(raw)
    }

    private fun saveAndEmit(records: List<RecentCallRecord>) {
        val bounded = records.take(MAX_RECORDS)
        prefs.edit().putString(KEY_RECORDS, serializeRecords(bounded)).apply()
        _history.value = bounded
    }

    companion object {
        private const val PREFS_NAME = "phincall_call_history"
        private const val KEY_RECORDS = "recent_calls_json"
        const val MAX_RECORDS = 25

        fun serializeRecords(records: List<RecentCallRecord>): String {
            val array = JSONArray()
            records.take(MAX_RECORDS).forEach { rec ->
                val obj = JSONObject()
                obj.put("id", rec.id)
                obj.put("dest", rec.destinationNumber)
                obj.put("name", rec.displayName)
                obj.put("platform", rec.platformType.name)
                obj.put("media", rec.mediaMode.name)
                obj.put("ts", rec.timestampMillis)
                obj.put("dur", rec.durationMillis)
                obj.put("outcome", rec.outcome.name)
                array.put(obj)
            }
            return array.toString()
        }

        fun parseRecords(json: String): List<RecentCallRecord> {
            return runCatching {
                val array = JSONArray(json)
                buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val id = obj.optString("id")
                        if (id.isBlank()) continue
                        val platform = runCatching {
                            PlatformType.valueOf(obj.optString("platform", PlatformType.OCEANA.name))
                        }.getOrDefault(PlatformType.OCEANA)
                        val media = runCatching {
                            CallMediaMode.valueOf(obj.optString("media", CallMediaMode.AUDIO.name))
                        }.getOrDefault(CallMediaMode.AUDIO)
                        val outcome = runCatching {
                            CallOutcome.valueOf(obj.optString("outcome", CallOutcome.INITIATED.name))
                        }.getOrDefault(CallOutcome.INITIATED)
                        add(
                            RecentCallRecord(
                                id = id,
                                destinationNumber = obj.optString("dest"),
                                displayName = obj.optString("name"),
                                platformType = platform,
                                mediaMode = media,
                                timestampMillis = obj.optLong("ts", 0L),
                                durationMillis = obj.optLong("dur", 0L),
                                outcome = outcome
                            )
                        )
                    }
                }.take(MAX_RECORDS)
            }.getOrDefault(emptyList())
        }
    }
}
