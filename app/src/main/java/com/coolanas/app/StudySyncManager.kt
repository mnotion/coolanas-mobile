package com.coolanas.app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.WorkManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.min

data class StudySubject(val name: String, val color: Int)

data class StudyTimerState(
    val elapsedMs: Long,
    val startedAtMs: Long?,
    val selectedSubject: String?,
    val runningSubject: String?
) {
    fun elapsedAt(now: Long = System.currentTimeMillis()): Long =
        elapsedMs + (startedAtMs?.let { max(0L, now - it) } ?: 0L)
}

data class StudySubjectsLoad(val subjects: List<StudySubject>, val editable: Boolean)

data class StudyAnalyticsRecord(
    val date: String,
    val durationMs: Long,
    val hourlyMs: Map<Int, Long>,
    val subject: String,
    val eventId: String
)

@Serializable
data class StudyAnalyticsTask(
    val id: String,
    val text: String,
    val completed: Boolean,
    val completedAt: String? = null
)

@Serializable
data class StudyAnalyticsTaskDay(
    val date: String,
    val locked: Boolean,
    val updatedAt: String?,
    val tasks: List<StudyAnalyticsTask>
)

@Serializable
data class StudyAnalyticsSnapshot(
    val daily: Map<String, Long> = emptyMap(),
    val dailySubjects: Map<String, Map<String, Long>> = emptyMap(),
    val dailyHours: Map<String, Map<Int, Long>> = emptyMap(),
    val taskDays: Map<String, StudyAnalyticsTaskDay> = emptyMap(),
    val tasks: List<StudyAnalyticsTask> = emptyList(),
    val updatedAt: String? = null,
    val fromCache: Boolean = false,
    val legacyImportPending: Boolean = false,
    val mirrorPending: Boolean = false
)

data class AccountStopwatchActionResult(
    val timer: StudyTimerState,
    val serverNowMs: Long,
    val serverClockOffsetMs: Long,
    val actionChanged: Boolean,
    val previousStartedAtMs: Long?
)

@Serializable
private data class AccountStopwatchTerminalRow(
    @SerialName("elapsed_ms") val elapsedMs: Long,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("started_by") val startedBy: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("updated_by") val updatedBy: String? = null,
    val version: Long,
    @SerialName("server_now") val serverNow: String,
    @SerialName("previous_started_at") val previousStartedAt: String? = null,
    @SerialName("action_changed") val actionChanged: Boolean
)

class StudySyncManager(context: Context) {
    private val appContext = context.applicationContext
    private val preferences: SharedPreferences =
        appContext.getSharedPreferences("coolanas_study_local_v1", Context.MODE_PRIVATE)
    private val syncMutex = globalSyncMutex

    companion object {
        private val globalSyncMutex = Mutex()
        private val accountStateLock = Any()
        const val WORK_NAME_PREFIX = "coolanas-study-sync-"
        val defaultSubjects = listOf(
            StudySubject("Maths", 0),
            StudySubject("English", 0),
            StudySubject("Biology", 0),
            StudySubject("Chemistry", 0),
            StudySubject("Physics", 0),
            StudySubject("History", 0),
            StudySubject("Geography", 0),
            StudySubject("Computer Science", 0)
        )
        val subjectPalette = listOf(
            0xFFE7E7E7.toInt(), 0xFFF6CACA.toInt(), 0xFFF6DFAA.toInt(), 0xFFD9EDBD.toInt(),
            0xFFC9E7EF.toInt(), 0xFFD7D0F2.toInt(), 0xFFF0CCE8.toInt()
        )
    }

    private fun key(userId: String, suffix: String) = "$userId:$suffix"
    private fun subjectsKey(userId: String) = key(userId, "subjects")
    private fun initializedKey(userId: String) = key(userId, "subjects_initialized")
    private fun subjectsDirtyKey(userId: String) = key(userId, "subjects_dirty")
    private fun queueKey(userId: String) = key(userId, "analytics_queue")
    private fun themeKey(userId: String) = key(userId, "account_theme")
    private fun themeDirtyKey(userId: String) = key(userId, "account_theme_dirty")
    private fun terminalElapsedKey(userId: String) = key(userId, "account_stopwatch_elapsed_ms")
    private fun terminalStartedKey(userId: String) = key(userId, "account_stopwatch_started_at_ms")
    private fun terminalClockOffsetKey(userId: String) = key(userId, "account_stopwatch_clock_offset_ms")
    private fun runningSubjectKey(userId: String) = key(userId, "account_stopwatch_running_subject")
    private fun selectedSubjectKey(userId: String) = key(userId, "selected_subject")

    fun cachedSubjects(userId: String): List<StudySubject>? {
        if (!preferences.getBoolean(initializedKey(userId), false)) return null
        return readSubjects(userId)
    }

    fun selectedSubject(userId: String): String? =
        preferences.getString(selectedSubjectKey(userId), null)

    fun cachedServerClockOffsetMs(userId: String): Long =
        preferences.getLong(terminalClockOffsetKey(userId), 0L)

    fun timerState(userId: String): StudyTimerState = StudyTimerState(
        elapsedMs = preferences.getLong(terminalElapsedKey(userId), 0L).coerceAtLeast(0L),
        startedAtMs = preferences.getLong(terminalStartedKey(userId), 0L).takeIf { it > 0L },
        selectedSubject = selectedSubject(userId),
        runningSubject = preferences.getString(runningSubjectKey(userId), null)
    )

    fun selectSubject(userId: String, subject: String?) {
        preferences.edit().putString(selectedSubjectKey(userId), subject).apply()
    }

    fun saveSubjects(userId: String, subjects: List<StudySubject>) {
        val clean = normaliseSubjects(subjects)
        writeSubjects(userId, clean, initialized = true, dirty = true)
        StudySyncWorker.enqueue(appContext, userId)
    }

    suspend fun loadSubjects(userId: String): StudySubjectsLoad {
        val cached = cachedSubjects(userId)
        return try {
            if (preferences.getBoolean(subjectsDirtyKey(userId), false)) {
                syncSubjects(userId)
                StudySubjectsLoad(cachedSubjects(userId) ?: defaultSubjects, true)
            } else {
                val response = invokeFunction(buildJsonObject { put("action", JsonPrimitive("subjects_get")) })
                val rawSubjects = response["subjects"]
                val subjects = if (rawSubjects == null || rawSubjects == JsonNull) {
                    defaultSubjects
                } else {
                    parseSubjects(rawSubjects)
                }
                if (rawSubjects == null || rawSubjects == JsonNull) {
                    writeSubjects(userId, subjects, initialized = true, dirty = true)
                    saveSubjectsToCloud(subjects)
                    preferences.edit().putBoolean(subjectsDirtyKey(userId), false).apply()
                } else {
                    writeSubjects(userId, subjects, initialized = true, dirty = false)
                }
                StudySubjectsLoad(subjects, true)
            }
        } catch (_: Exception) {
            val latestCache = cachedSubjects(userId)
            StudySubjectsLoad(latestCache ?: defaultSubjects, true)
        }
    }

    fun cachedTheme(userId: String): String =
        preferences.getString(themeKey(userId), "light")?.takeIf { it == "dark" } ?: "light"

    fun saveTheme(userId: String, dark: Boolean) {
        synchronized(accountStateLock) {
            preferences.edit()
                .putString(themeKey(userId), if (dark) "dark" else "light")
                .putBoolean(themeDirtyKey(userId), true)
                .commit()
        }
        StudySyncWorker.enqueue(appContext, userId)
    }

    suspend fun loadTheme(userId: String): Boolean = withContext(Dispatchers.IO) {
        if (preferences.getBoolean(themeDirtyKey(userId), false)) {
            return@withContext cachedTheme(userId) == "dark"
        }
        try {
            val result = invokeFunction(buildJsonObject {
                put("action", JsonPrimitive("settings_get"))
            })
            val settings = result["settings"] as? JsonObject
            val cloudTheme = settings?.get("theme")?.jsonPrimitive?.contentOrNull
                ?.takeIf { it == "dark" || it == "light" }
            if (cloudTheme != null) {
                preferences.edit()
                    .putString(themeKey(userId), cloudTheme)
                    .putBoolean(themeDirtyKey(userId), false)
                    .apply()
                cloudTheme == "dark"
            } else {
                cachedTheme(userId) == "dark"
            }
        } catch (_: Exception) {
            cachedTheme(userId) == "dark"
        }
    }

    suspend fun loadProfileAvatar(userId: String, googleAvatarUrl: String?): Bitmap? =
        withContext(Dispatchers.IO) {
            val customUrl = try {
                val result = invokeFunction(buildJsonObject {
                    put("action", JsonPrimitive("profile_picture_get"))
                })
                val exists = result["exists"]?.jsonPrimitive?.booleanOrNull == true
                result["url"]?.jsonPrimitive?.contentOrNull?.takeIf { exists }
            } catch (_: Exception) {
                null
            }
            for (candidate in listOfNotNull(customUrl, googleAvatarUrl).distinct()) {
                val bitmap = downloadAvatar(candidate)
                if (bitmap != null) return@withContext bitmap
            }
            null
        }

    /**
     * Reads or mutates the single account-owned stopwatch in Supabase.
     * The database RPC is the source of truth; local preferences are only a display cache.
     */
    suspend fun accountStopwatchAction(
        userId: String,
        action: String,
        subjectForStart: String? = null
    ): AccountStopwatchActionResult = withContext(Dispatchers.IO) {
        require(action in setOf("get", "start", "pause", "reset")) {
            "Unsupported stopwatch action."
        }
        val response = SupabaseClientProvider.client.postgrest.rpc(
            function = "coolanas_account_stopwatch_action",
            parameters = buildJsonObject {
                put("p_action", JsonPrimitive(action))
            }
        )
        val row = response.decodeList<AccountStopwatchTerminalRow>().firstOrNull()
            ?: throw IllegalStateException("The account stopwatch terminal returned no state.")
        val serverNowMs = Instant.parse(row.serverNow).toEpochMilli()
        val startedAtMs = row.startedAt?.let { Instant.parse(it).toEpochMilli() }
        val previousStartedAtMs = row.previousStartedAt?.let {
            runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
        }
        val serverClockOffsetMs = serverNowMs - System.currentTimeMillis()

        synchronized(accountStateLock) {
            val storedRunningSubject = preferences.getString(runningSubjectKey(userId), null)
            val selected = preferences.getString(selectedSubjectKey(userId), null)
            val previousCachedStart = preferences.getLong(terminalStartedKey(userId), 0L).takeIf { it > 0L }
            val isNewRunningSession = startedAtMs != null && previousCachedStart != startedAtMs
            val subjectToRecord = storedRunningSubject ?: selected ?: "Unassigned"
            val completedRecords = if (
                action in setOf("pause", "reset") &&
                row.actionChanged &&
                previousStartedAtMs != null &&
                serverNowMs > previousStartedAtMs
            ) {
                splitInterval(previousStartedAtMs, serverNowMs, subjectToRecord)
            } else {
                emptyList()
            }
            val queue = readAnalyticsQueue(userId).toMutableList()
            queue.addAll(completedRecords)

            val editor = preferences.edit()
                .putLong(terminalElapsedKey(userId), row.elapsedMs.coerceAtLeast(0L))
                .putLong(terminalClockOffsetKey(userId), serverClockOffsetMs)
            if (startedAtMs != null) {
                editor.putLong(terminalStartedKey(userId), startedAtMs)
            } else {
                editor.remove(terminalStartedKey(userId))
            }
            if (action == "start" && startedAtMs != null) {
                val chosen = subjectForStart?.takeIf { it.isNotBlank() } ?: selected
                if (chosen != null) editor.putString(runningSubjectKey(userId), chosen)
                else editor.remove(runningSubjectKey(userId))
            } else if (startedAtMs == null) {
                editor.remove(runningSubjectKey(userId))
            } else if (startedAtMs != null && (storedRunningSubject == null || isNewRunningSession)) {
                // A timer started on another Coolanas client has no subject column
                // in the terminal, so capture this device's selected subject for analytics.
                editor.putString(runningSubjectKey(userId), selected ?: "Unassigned")
            }
            if (completedRecords.isNotEmpty()) {
                editor.putString(queueKey(userId), encodeAnalyticsQueue(queue))
            }
            if (!editor.commit()) {
                throw IllegalStateException("Could not persist the stopwatch cache and completed analytics.")
            }
        }

        AccountStopwatchActionResult(
            timer = timerState(userId),
            serverNowMs = serverNowMs,
            serverClockOffsetMs = serverClockOffsetMs,
            actionChanged = row.actionChanged,
            previousStartedAtMs = previousStartedAtMs
        )
    }

    /** Reads the account analytics document from the same endpoint used by the website. */
    suspend fun loadAnalytics(userId: String): StudyAnalyticsSnapshot = withContext(Dispatchers.IO) {
        require(userId.isNotBlank())
        val cacheKey = key(userId, "analytics_snapshot")
        val cachedSnapshot = preferences.getString(cacheKey, null)?.let { cached ->
            runCatching { Json.decodeFromString<StudyAnalyticsSnapshot>(cached) }.getOrNull()
        }
        try {
            val result = invokeFunction(buildJsonObject {
            put("action", JsonPrimitive("analytics_get"))
        })

        fun longMap(element: JsonElement?): Map<String, Long> {
            val obj = element as? JsonObject ?: return emptyMap()
            return obj.mapNotNull { (key, value) ->
                val amount = value.jsonPrimitive.longOrNull
                if (amount != null && amount >= 0L) key to amount else null
            }.toMap()
        }
        fun nestedLongMap(element: JsonElement?): Map<String, Map<String, Long>> {
            val obj = element as? JsonObject ?: return emptyMap()
            return obj.mapNotNull { (date, value) ->
                val values = longMap(value)
                if (values.isNotEmpty()) date to values else null
            }.toMap()
        }
        val hourly = nestedLongMap(result["dailyHours"]).mapValues { (_, values) ->
            values.mapNotNull { (hour, duration) ->
                hour.toIntOrNull()?.takeIf { it in 0..23 }?.let { it to duration }
            }.toMap()
        }
            val taskData = runCatching {
                invokeFunction(buildJsonObject {
                    put("action", JsonPrimitive("tasks_get"))
                })
            }.getOrNull()
        fun parseTask(element: JsonElement): StudyAnalyticsTask? {
            val obj = element as? JsonObject ?: return null
            val text = obj["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (text.isBlank()) return null
            return StudyAnalyticsTask(
                id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                text = text.take(180),
                completed = obj["completed"]?.jsonPrimitive?.booleanOrNull == true,
                completedAt = obj["completedAt"]?.jsonPrimitive?.contentOrNull
            )
        }
            val tasks = if (taskData != null) {
                (taskData["tasks"] as? JsonArray)?.mapNotNull(::parseTask).orEmpty()
            } else {
                cachedSnapshot?.tasks.orEmpty()
            }
            val taskDays = if (taskData != null) {
                (taskData["taskDays"] as? JsonArray)
                    ?.mapNotNull { element ->
                        val obj = element as? JsonObject ?: return@mapNotNull null
                        val date = obj["date"]?.jsonPrimitive?.contentOrNull
                            ?.takeIf { Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(it) }
                            ?: return@mapNotNull null
                        val dayTasks = (obj["tasks"] as? JsonArray)?.mapNotNull(::parseTask).orEmpty()
                        date to StudyAnalyticsTaskDay(
                            date = date,
                            locked = obj["locked"]?.jsonPrimitive?.booleanOrNull == true,
                            updatedAt = obj["updatedAt"]?.jsonPrimitive?.contentOrNull,
                            tasks = dayTasks
                        )
                    }?.toMap().orEmpty()
            } else {
                cachedSnapshot?.taskDays.orEmpty()
            }

            val snapshot = StudyAnalyticsSnapshot(
                daily = longMap(result["daily"]),
                dailySubjects = nestedLongMap(result["dailySubjects"]),
                dailyHours = hourly,
                taskDays = taskDays,
                tasks = tasks,
                updatedAt = result["updatedAt"]?.jsonPrimitive?.contentOrNull,
                legacyImportPending = result["legacyImportPending"]?.jsonPrimitive?.booleanOrNull == true,
                mirrorPending = result["mirrorPending"]?.jsonPrimitive?.booleanOrNull == true
            )
            preferences.edit().putString(cacheKey, Json.encodeToString(snapshot)).apply()
            snapshot
        } catch (error: Exception) {
            cachedSnapshot?.copy(fromCache = true) ?: throw error
        }
    }

    private fun downloadAvatar(imageUrl: String): Bitmap? {
        var connection: HttpURLConnection? = null
        return try {
            val parsed = URL(imageUrl)
            if (parsed.protocol != "https") return null
            connection = parsed.openConnection() as? HttpURLConnection ?: return null
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = true
            connection.connect()
            if (connection.responseCode !in 200..299) return null
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > 8 * 1024 * 1024) return null
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val sample = (max(bounds.outWidth, bounds.outHeight) / 512).coerceAtLeast(1)
            BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun syncPending(userId: String, scheduleRetry: Boolean = true): Boolean =
        syncMutex.withLock {
            if (!hasValidatedNetwork()) {
                if (scheduleRetry) StudySyncWorker.enqueue(appContext, userId)
                return@withLock false
            }
            try {
                if (preferences.getBoolean(themeDirtyKey(userId), false)) {
                    val theme = cachedTheme(userId)
                    invokeFunction(buildJsonObject {
                        put("action", JsonPrimitive("settings_update"))
                        put("settings", buildJsonObject {
                            put("theme", JsonPrimitive(theme))
                        })
                    })
                    synchronized(accountStateLock) {
                        if (cachedTheme(userId) == theme) {
                            preferences.edit().putBoolean(themeDirtyKey(userId), false).apply()
                        }
                    }
                }
                if (preferences.getBoolean(subjectsDirtyKey(userId), false)) {
                    syncSubjects(userId)
                }
                flushAnalytics(userId)
                true
            } catch (_: Exception) {
                if (scheduleRetry) StudySyncWorker.enqueue(appContext, userId)
                false
            }
        }

    private suspend fun syncSubjects(userId: String) {
        val snapshot = cachedSubjects(userId) ?: defaultSubjects
        saveSubjectsToCloud(snapshot)
        if (cachedSubjects(userId) == snapshot) {
            preferences.edit().putBoolean(subjectsDirtyKey(userId), false).apply()
        }
    }

    private suspend fun saveSubjectsToCloud(subjects: List<StudySubject>) {
        invokeFunction(buildJsonObject {
            put("action", JsonPrimitive("subjects_save"))
            put("subjects", buildJsonArray {
                subjects.forEach { subject ->
                    add(buildJsonObject {
                        put("name", JsonPrimitive(subject.name))
                        put("color", JsonPrimitive(subject.color))
                    })
                }
            })
        })
    }

    private suspend fun flushAnalytics(userId: String) {
        while (true) {
            val pending = readAnalyticsQueue(userId)
            if (pending.isEmpty()) return
            val batch = pending.take(100)
            invokeFunction(buildJsonObject {
                put("action", JsonPrimitive("analytics_sync"))
                put("records", buildJsonArray {
                    batch.forEach { record ->
                        add(buildJsonObject {
                            put("date", JsonPrimitive(record.date))
                            put("durationMs", JsonPrimitive(record.durationMs))
                            put("subject", JsonPrimitive(record.subject))
                            put("eventId", JsonPrimitive(record.eventId))
                            put("hourlyMs", buildJsonObject {
                                record.hourlyMs.forEach { (hour, duration) ->
                                    put(hour.toString(), JsonPrimitive(duration))
                                }
                            })
                        })
                    }
                })
            })
            val sentIds = batch.mapTo(mutableSetOf()) { it.eventId }
            val saved = synchronized(accountStateLock) {
                val latest = readAnalyticsQueue(userId).filterNot { it.eventId in sentIds }
                preferences.edit().putString(queueKey(userId), encodeAnalyticsQueue(latest)).commit()
            }
            if (!saved) throw IllegalStateException("Could not persist the remaining offline analytics queue.")
        }
    }

    private fun hasValidatedNetwork(): Boolean {
        val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private suspend fun invokeFunction(body: JsonObject): JsonObject {
        val response = SupabaseClientProvider.client.functions.invoke(
            function = "coolanas-files-b2",
            body = body,
            headers = Headers.build {
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
        )
        val parsed = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val error = parsed["error"]?.jsonPrimitive?.contentOrNull
        if (!error.isNullOrBlank()) throw IllegalStateException(error)
        return parsed
    }

    private fun writeSubjects(
        userId: String,
        subjects: List<StudySubject>,
        initialized: Boolean,
        dirty: Boolean
    ) {
        val clean = normaliseSubjects(subjects)
        val json = buildJsonArray {
            clean.forEach { subject ->
                add(buildJsonObject {
                    put("name", JsonPrimitive(subject.name))
                    put("color", JsonPrimitive(subject.color))
                })
            }
        }
        preferences.edit()
            .putString(subjectsKey(userId), json.toString())
            .putBoolean(initializedKey(userId), initialized)
            .putBoolean(subjectsDirtyKey(userId), dirty)
            .apply()
    }

    private fun readSubjects(userId: String): List<StudySubject> {
        return try {
            parseSubjects(Json.parseToJsonElement(preferences.getString(subjectsKey(userId), "[]") ?: "[]"))
        } catch (_: Exception) {
            defaultSubjects
        }
    }

    private fun parseSubjects(element: JsonElement): List<StudySubject> =
        normaliseSubjects(element.jsonArray.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val color = obj["color"]?.jsonPrimitive?.intOrNull ?: 0
            StudySubject(name, color)
        })

    private fun normaliseSubjects(subjects: List<StudySubject>): List<StudySubject> =
        subjects.asSequence()
            .filter { it.name.isNotBlank() }
            .map { StudySubject(it.name.trim().take(32), it.color.coerceIn(0, 6)) }
            .distinctBy { it.name.lowercase() }
            .take(40)
            .toList()

    private fun splitInterval(startAt: Long, endAt: Long, subject: String): List<StudyAnalyticsRecord> {
        val zone = ZoneId.systemDefault()
        val records = mutableListOf<StudyAnalyticsRecord>()
        var cursor = startAt
        while (cursor < endAt) {
            val firstHour = Instant.ofEpochMilli(cursor).atZone(zone)
            val dateEnd = firstHour.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = min(endAt, dateEnd)
            val hourly = linkedMapOf<Int, Long>()
            var hourCursor = cursor
            while (hourCursor < dayEnd) {
                val hourDate = Instant.ofEpochMilli(hourCursor).atZone(zone)
                val nextHour = hourDate.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
                val segmentEnd = min(dayEnd, max(hourCursor + 1L, nextHour))
                val hour = hourDate.hour
                hourly[hour] = (hourly[hour] ?: 0L) + (segmentEnd - hourCursor)
                hourCursor = segmentEnd
            }
            val duration = hourly.values.sum()
            if (duration > 0L) {
                records += StudyAnalyticsRecord(
                    date = firstHour.toLocalDate().toString(),
                    durationMs = duration,
                    hourlyMs = hourly,
                    subject = subject,
                    eventId = "mobile_" + UUID.randomUUID().toString().replace("-", "")
                )
            }
            cursor = dayEnd
        }
        return records
    }

    private fun readAnalyticsQueue(userId: String): List<StudyAnalyticsRecord> {
        return try {
            val raw = preferences.getString(queueKey(userId), "[]") ?: "[]"
            Json.parseToJsonElement(raw).jsonArray.mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                val date = obj["date"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val duration = obj["durationMs"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                val subject = obj["subject"]?.jsonPrimitive?.contentOrNull ?: "Unassigned"
                val eventId = obj["eventId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val hourly = (obj["hourlyMs"] as? JsonObject)?.mapNotNull { (hour, value) ->
                    val hourNumber = hour.toIntOrNull() ?: return@mapNotNull null
                    val milliseconds = value.jsonPrimitive.longOrNull ?: return@mapNotNull null
                    hourNumber to milliseconds
                }?.toMap() ?: emptyMap()
                if (duration <= 0L || eventId.isBlank()) null
                else StudyAnalyticsRecord(date, duration, hourly, subject, eventId)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodeAnalyticsQueue(records: List<StudyAnalyticsRecord>): String =
        JsonArray(records.map { record ->
            buildJsonObject {
                put("date", JsonPrimitive(record.date))
                put("durationMs", JsonPrimitive(record.durationMs))
                put("subject", JsonPrimitive(record.subject))
                put("eventId", JsonPrimitive(record.eventId))
                put("hourlyMs", buildJsonObject {
                    record.hourlyMs.forEach { (hour, duration) ->
                        put(hour.toString(), JsonPrimitive(duration))
                    }
                })
            }
        }).toString()
}

class StudySyncWorker(
    appContext: Context,
    params: androidx.work.WorkerParameters
) : androidx.work.CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val expectedUserId = inputData.getString("userId") ?: return Result.failure()
        return try {
            val status = kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                SupabaseClientProvider.client.auth.sessionStatus.first {
                    it !is io.github.jan.supabase.auth.status.SessionStatus.Initializing
                }
            } ?: return Result.retry()
            val authenticated = status as? io.github.jan.supabase.auth.status.SessionStatus.Authenticated
                ?: return Result.failure()
            val currentUserId = authenticated.session.user?.id ?: return Result.failure()
            if (currentUserId != expectedUserId) return Result.failure()
            val repository = StudySyncManager(applicationContext)
            val synced = repository.syncPending(currentUserId, scheduleRetry = false)
            if (synced) Result.success() else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun enqueue(context: Context, userId: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<StudySyncWorker>()
                .setConstraints(constraints)
                .setInputData(Data.Builder().putString("userId", userId).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                StudySyncManager.WORK_NAME_PREFIX + userId,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
