package com.agon.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.Calendar
import java.util.UUID

@Serializable
data class MusicAlarmEntry(
    val id: String = UUID.randomUUID().toString(),
    val enabled: Boolean = true,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val playlistId: String = "",     // kosong = random dari library
    val randomSong: Boolean = true,
    val nextTriggerAt: Long = -1L
)

/** Store alarm entries ke SharedPreferences */
object MusicAlarmStore {
    private const val PREFS = "agon_alarms"
    private const val KEY = "alarm_entries"
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): List<MusicAlarmEntry> = try {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        json.decodeFromString<List<MusicAlarmEntry>>(raw)
    } catch (e: Exception) { emptyList() }

    fun save(context: Context, entries: List<MusicAlarmEntry>) = try {
        prefs(context).edit().putString(KEY, json.encodeToString(entries)).apply()
    } catch (e: Exception) { Timber.e(e) }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Scheduler — daftar / batalkan alarm sistem */
object MusicAlarmScheduler {

    fun scheduleAll(context: Context, alarms: List<MusicAlarmEntry>) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        // Cancel semua dulu
        alarms.forEach { cancel(context, it.id) }

        alarms.filter { it.enabled }.forEach { alarm ->
            val triggerMs = nextTriggerMillis(alarm.hour, alarm.minute)
            val pi = pendingIntent(context, alarm)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            }
            Timber.d("Alarm scheduled: ${alarm.id} at $triggerMs")
        }

        // Simpan dengan nextTriggerAt terupdate
        val updated = alarms.map { a ->
            if (a.enabled) a.copy(nextTriggerAt = nextTriggerMillis(a.hour, a.minute)) else a
        }
        MusicAlarmStore.save(context, updated)
    }

    fun cancel(context: Context, alarmId: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pendingIntent(context, MusicAlarmEntry(id = alarmId, hour = 0, minute = 0)))
    }

    private fun pendingIntent(context: Context, alarm: MusicAlarmEntry): PendingIntent {
        val intent = Intent(context, MusicAlarmReceiver::class.java).apply {
            action = MusicAlarmReceiver.ACTION_TRIGGER
            putExtra(MusicAlarmReceiver.EXTRA_ALARM_ID, alarm.id)
            putExtra(MusicAlarmReceiver.EXTRA_PLAYLIST_ID, alarm.playlistId)
            putExtra(MusicAlarmReceiver.EXTRA_RANDOM, alarm.randomSong)
        }
        return PendingIntent.getBroadcast(
            context,
            alarm.id.hashCode() and Int.MAX_VALUE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }
}

/** BroadcastReceiver yang dipanggil saat alarm berbunyi */
class MusicAlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TRIGGER = "com.agon.app.ALARM_TRIGGER"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_PLAYLIST_ID = "playlist_id"
        const val EXTRA_RANDOM = "random_song"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRIGGER) return
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: return
        val playlistId = intent.getStringExtra(EXTRA_PLAYLIST_ID) ?: ""
        val random = intent.getBooleanExtra(EXTRA_RANDOM, true)
        Timber.d("Alarm triggered: $alarmId playlist=$playlistId random=$random")
        // TODO: start MusicService dengan playlist/random
        // Untuk saat ini kita cukup trigger - integrasi ke MusicService bisa ditambahkan
        reschedule(context, alarmId)
    }

    /** Reschedule alarm untuk besok setelah triggered */
    private fun reschedule(context: Context, alarmId: String) {
        val alarms = MusicAlarmStore.load(context)
        val alarm = alarms.find { it.id == alarmId } ?: return
        MusicAlarmScheduler.scheduleAll(context, alarms.map {
            if (it.id == alarmId) it else it
        })
    }
}

/** Re-schedule semua alarm setelah device reboot */
class AlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val alarms = MusicAlarmStore.load(context)
        if (alarms.any { it.enabled }) {
            MusicAlarmScheduler.scheduleAll(context, alarms)
            Timber.d("Alarms rescheduled after boot: ${alarms.size} entries")
        }
    }
}
