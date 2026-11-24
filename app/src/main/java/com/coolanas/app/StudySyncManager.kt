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
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

class StudySyncManager(context: Context) {
    private val appContext = context.applicationContext
    private val preferences: SharedPreferences =
        appContext.getSharedPreferences("coolanas_study_local_v1", Context.MODE_PRIVATE)
    private val syncMutex = globalSyncMutex

    companion object {
        private val globalSyncMutex = Mutex()
        private val timerCheckpointLock = Any()
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
    private fun elapsedKey(userId: String) = key(userId, "timer_elapsed_ms")
    private fun startedKey(userId: String) = key(userId, "timer_started_at_ms")
    private fun checkpointKey(userId: String) = key(userId, "timer_checkpoint_at_ms")
    private fun runningSubjectKey(userId: String) = key(userId, "timer_running_subject")
    private fun selectedSubjectKey(userId: String) = key(userId, "selected_subject")

    fun cachedSubjects(userId: String): List<StudySubject>? {
        if (!preferences.getBoolean(initializedKey(userId), false)) return null
        return readSubjects(userId)
    }

    fun selectedSubject(userId: String): String? =
        preferences.getString(selectedSubjectKey(userId), null)

    fun timerState(userId: String): StudyTimerState = StudyTimerState(
        elapsedMs = preferences.getLong(elapsedKey(userId), 0L).coerceAtLeast(0L),
        startedAtMs = preferences.getLong(startedKey(userId), 0L).takeIf { it > 0L },
        selectedSubject = selectedSubject(userId),
        runningSubject = preferences.getString(runningSubjectKey(userId), null)
    )

    fun selectSubject(userId: String, subject: String?) {
        preferences.edit().putString(selectedSubjectKey(userId), subject).apply()
    }

    fun startTimer(userId: String, subject: String): StudyTimerState = synchronized(timerCheckpointLock) {
        val now = System.currentTimeMillis()
        preferences.edit()
            .putString(selectedSubjectKey(userId), subject)
            .putLong(startedKey(userId), now)
            .putLong(checkpointKey(userId), now)
            .putString(runningSubjectKey(userId), subject)
            .apply()
        timerState(userId)
    }

    fun pauseTimer(userId: String): StudyTimerState = synchronized(timerCheckpointLock) {
        val before = timerState(userId)
        if (before.startedAtMs != null) {
            val now = System.currentTimeMillis()
            checkpointTimer(userId, now)
            preferences.edit()
                .putLong(elapsedKey(userId), before.elapsedAt(now))
                .remove(startedKey(userId))
                .remove(checkpointKey(userId))
                .remove(runningSubjectKey(userId))
                .apply()
        }
        timerState(userId)
    }

    fun resetTimer(userId: String): StudyTimerState = synchronized(timerCheckpointLock) {
        if (timerState(userId).startedAtMs != null) {
            checkpointTimer(userId, System.currentTimeMillis())
        }
        preferences.edit()
            .putLong(elapsedKey(userId), 0L)
            .remove(startedKey(userId))
            .remove(checkpointKey(userId))
            .remove(runningSubjectKey(userId))
            .apply()
        timerState(userId)
    }

    /**
     * Commits only the time since the last durable checkpoint. The pending records and
     * checkpoint timestamp are written in one SharedPreferences transaction, so a crash
     * cannot leave the timer checkpoint ahead of an unsaved analytics record.
     */
    fun checkpointTimer(userId: String, endAt: Long = System.currentTimeMillis()): Boolean =
        synchronized(timerCheckpointLock) {
            val start = preferences.getLong(startedKey(userId), 0L)
            if (start <= 0L) return@synchronized false
            val previousCheckpoint = preferences.getLong(checkpointKey(userId), start).coerceAtLeast(start)
            if (endAt <= previousCheckpoint) return@synchronized false

            val subject = preferences.getString(runningSubjectKey(userId), null) ?: "Unassigned"
            val newRecords = splitInterval(previousCheckpoint, endAt, subject)
            val queue = readAnalyticsQueue(userId).toMutableList()
            queue.addAll(newRecords)

            preferences.edit()
                .putString(queueKey(userId), encodeAnalyticsQueue(queue))
                .putLong(checkpointKey(userId), endAt)
                .commit()
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
        synchronized(timerCheckpointLock) {
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
                    synchronized(timerCheckpointLock) {
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
            val saved = synchronized(timerCheckpointLock) {
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
            repository.checkpointTimer(currentUserId)
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
