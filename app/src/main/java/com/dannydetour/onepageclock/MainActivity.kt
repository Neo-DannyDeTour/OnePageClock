package com.dannydetour.onepageclock

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

// --- MODERN THEME PALETTE ---
val BgDark = Color(0xFF090A0F)
val CardSurface = Color(0xFF14161F)
val CardElevated = Color(0xFF1E2230)
val BorderSubtle = Color(0xFF282D3E)
val AccentCyan = Color(0xFF00E5FF)
val AccentMint = Color(0xFF64FFDA)
val AccentGold = Color(0xFFFFD54F)
val AccentCoral = Color(0xFFFF5252)
val TextPrimary = Color(0xFFF5F7FA)
val TextSecondary = Color(0xFF8F9BB3)

// --- GLOBAL STATE FOR SERVICE SYNC ---
object GlobalState {
    var totalTimeSeconds by mutableLongStateOf(0L)
    var timeRemainingSeconds by mutableLongStateOf(0L)
    var isRunning by mutableStateOf(false)
    var isPaused by mutableStateOf(false)
    var isRinging by mutableStateOf(false)

    var nextAlarmSummary by mutableStateOf("")
}

// --- DATA MODELS ---
data class PastTimer(val totalSeconds: Long, val isFavorite: Boolean)

data class WorldClockData(
    val city: String,
    val zoneId: String,
    val id: String = UUID.randomUUID().toString()
)

data class AlarmItem(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "Alarm",
    val hour: Int = 7,
    val minute: Int = 30,
    val amPm: String = "AM",
    val repeat: String = "Once",
    val isEnabled: Boolean = false
)

data class CityCoord(
    val city: String,
    val zoneId: String,
    val lat: Double,
    val lon: Double
)

val KNOWN_CITY_COORDS = listOf(
    // UTC-11 to UTC-8
    CityCoord("Pago Pago", "Pacific/Pago_Pago", -14.2756, -170.7020),
    CityCoord("Honolulu", "Pacific/Honolulu", 21.3069, -157.8583),
    CityCoord("Anchorage", "America/Anchorage", 61.2181, -149.9003),
    CityCoord("Vancouver", "America/Vancouver", 49.2827, -123.1207),
    CityCoord("Seattle", "America/Los_Angeles", 47.6062, -122.3321),
    CityCoord("San Francisco", "America/Los_Angeles", 37.7749, -122.4194),
    CityCoord("Los Angeles", "America/Los_Angeles", 34.0522, -118.2437),

    // UTC-7 to UTC-6
    CityCoord("Phoenix", "America/Phoenix", 33.4484, -112.0740),
    CityCoord("Denver", "America/Denver", 39.7392, -104.9903),
    CityCoord("Mexico City", "America/Mexico_City", 19.4326, -99.1332),
    CityCoord("Chicago", "America/Chicago", 41.8781, -87.6298),
    CityCoord("Dallas", "America/Chicago", 32.7767, -96.7970),
    CityCoord("Guatemala City", "America/Guatemala", 14.6349, -90.5069),
    CityCoord("San Jose", "America/Costa_Rica", 9.9281, -84.0907),

    // UTC-5
    CityCoord("Toronto", "America/Toronto", 43.6532, -79.3832),
    CityCoord("Montreal", "America/Toronto", 45.5017, -73.5673),
    CityCoord("Ottawa", "America/Toronto", 45.4215, -75.6972),
    CityCoord("New York", "America/New_York", 40.7128, -74.0060),
    CityCoord("Washington D.C.", "America/New_York", 38.9072, -77.0369),
    CityCoord("Miami", "America/New_York", 25.7617, -80.1918),
    CityCoord("Havana", "America/Havana", 23.1136, -82.3666),
    CityCoord("Panama City", "America/Panama", 8.9824, -79.5199),
    CityCoord("Bogota", "America/Bogota", 4.7110, -74.0721),
    CityCoord("Lima", "America/Lima", -12.0464, -77.0428),
    CityCoord("Quito", "America/Guayaquil", -0.1807, -78.4678),

    // UTC-4 to UTC-3
    CityCoord("Caracas", "America/Caracas", 10.4806, -66.9036),
    CityCoord("La Paz", "America/La_Paz", -16.4897, -68.1193),
    CityCoord("Santiago", "America/Santiago", -33.4489, -70.6693),
    CityCoord("Buenos Aires", "America/Argentina/Buenos_Aires", -34.6037, -58.3816),
    CityCoord("Montevideo", "America/Montevideo", -34.9011, -56.1645),
    CityCoord("Asuncion", "America/Asuncion", -25.2637, -57.5759),
    CityCoord("Sao Paulo", "America/Sao_Paulo", -23.5505, -46.6333),
    CityCoord("Rio de Janeiro", "America/Sao_Paulo", -22.9068, -43.1729),
    CityCoord("Brasilia", "America/Sao_Paulo", -15.7975, -47.8919),
    CityCoord("Nuuk", "America/Nuuk", 64.1814, -51.6941),

    // UTC-1 to UTC+0
    CityCoord("Reykjavik", "Atlantic/Reykjavik", 64.1466, -21.9426),
    CityCoord("Dakar", "Africa/Dakar", 14.7167, -17.4677),
    CityCoord("Accra", "Africa/Accra", 5.6037, -0.1870),
    CityCoord("Abidjan", "Africa/Abidjan", 5.3600, -4.0083),
    CityCoord("Dublin", "Europe/Dublin", 53.3498, -6.2603),
    CityCoord("Lisbon", "Europe/Lisbon", 38.7223, -9.1393),
    CityCoord("London", "Europe/London", 51.5074, -0.1278),
    CityCoord("Casablanca", "Africa/Casablanca", 33.5731, -7.5898),

    // UTC+1
    CityCoord("Madrid", "Europe/Madrid", 40.4168, -3.7038),
    CityCoord("Paris", "Europe/Paris", 48.8566, 2.3522),
    CityCoord("Brussels", "Europe/Brussels", 50.8503, 4.3517),
    CityCoord("Amsterdam", "Europe/Amsterdam", 52.3676, 4.9041),
    CityCoord("Berlin", "Europe/Berlin", 52.5200, 13.4050),
    CityCoord("Bern", "Europe/Zurich", 46.9480, 7.4474),
    CityCoord("Zurich", "Europe/Zurich", 47.3769, 8.5417),
    CityCoord("Rome", "Europe/Rome", 41.9028, 12.4964),
    CityCoord("Vienna", "Europe/Vienna", 48.2082, 16.3738),
    CityCoord("Prague", "Europe/Prague", 50.0755, 14.4378),
    CityCoord("Warsaw", "Europe/Warsaw", 52.2297, 21.0122),
    CityCoord("Copenhagen", "Europe/Copenhagen", 55.6761, 12.5683),
    CityCoord("Oslo", "Europe/Oslo", 59.9139, 10.7522),
    CityCoord("Stockholm", "Europe/Stockholm", 59.3293, 18.0686),
    CityCoord("Budapest", "Europe/Budapest", 47.4979, 19.0402),
    CityCoord("Belgrade", "Europe/Belgrade", 44.7866, 20.4489),
    CityCoord("Algiers", "Africa/Algiers", 36.7538, 3.0588),
    CityCoord("Tunis", "Africa/Tunis", 36.8065, 10.1815),
    CityCoord("Lagos", "Africa/Lagos", 6.5244, 3.3792),
    CityCoord("Kinshasa", "Africa/Kinshasa", -4.4419, 15.2663),

    // UTC+2
    CityCoord("Helsinki", "Europe/Helsinki", 60.1699, 24.9384),
    CityCoord("Tallinn", "Europe/Tallinn", 59.4370, 24.7535),
    CityCoord("Riga", "Europe/Riga", 56.9496, 24.1052),
    CityCoord("Vilnius", "Europe/Vilnius", 54.6872, 25.2797),
    CityCoord("Bucharest", "Europe/Bucharest", 44.4268, 26.1025),
    CityCoord("Sofia", "Europe/Sofia", 42.6977, 23.3219),
    CityCoord("Athens", "Europe/Athens", 37.9838, 23.7275),
    CityCoord("Kyiv", "Europe/Kyiv", 50.4501, 30.5234),
    CityCoord("Cairo", "Africa/Cairo", 30.0444, 31.2357),
    CityCoord("Jerusalem", "Asia/Jerusalem", 31.7683, 35.2137),
    CityCoord("Beirut", "Asia/Beirut", 33.8938, 35.5018),
    CityCoord("Johannesburg", "Africa/Johannesburg", -26.2041, 28.0473),
    CityCoord("Cape Town", "Africa/Johannesburg", -33.9249, 18.4241),

    // UTC+3
    CityCoord("Istanbul", "Europe/Istanbul", 41.0082, 28.9784),
    CityCoord("Moscow", "Europe/Moscow", 55.7558, 37.6173),
    CityCoord("Baghdad", "Asia/Baghdad", 33.3152, 44.3661),
    CityCoord("Riyadh", "Asia/Riyadh", 24.7136, 46.6753),
    CityCoord("Doha", "Asia/Qatar", 25.2854, 51.5310),
    CityCoord("Kuwait City", "Asia/Kuwait", 29.3759, 47.9774),
    CityCoord("Nairobi", "Africa/Nairobi", -1.2921, 36.8219),
    CityCoord("Addis Ababa", "Africa/Addis_Ababa", 9.0320, 38.7469),

    // UTC+3:30 to UTC+4
    CityCoord("Tehran", "Asia/Tehran", 35.6892, 51.3890),
    CityCoord("Dubai", "Asia/Dubai", 25.2048, 55.2708),
    CityCoord("Muscat", "Asia/Muscat", 23.5859, 58.4059),
    CityCoord("Baku", "Asia/Baku", 40.4093, 49.8671),
    CityCoord("Tbilisi", "Asia/Tbilisi", 41.7151, 44.8271),
    CityCoord("Yerevan", "Asia/Yerevan", 40.1792, 44.4991),

    // UTC+4:30 to UTC+5
    CityCoord("Kabul", "Asia/Kabul", 34.5553, 69.2075),
    CityCoord("Karachi", "Asia/Karachi", 24.8607, 67.0011),
    CityCoord("Islamabad", "Asia/Karachi", 33.6844, 73.0479),
    CityCoord("Tashkent", "Asia/Tashkent", 41.2995, 69.2401),
    CityCoord("Ashgabat", "Asia/Ashgabat", 37.9601, 58.3261),
    CityCoord("Almaty", "Asia/Almaty", 43.2220, 76.8512),

    // UTC+5:30 to UTC+6
    CityCoord("New Delhi", "Asia/Kolkata", 28.6139, 77.2090),
    CityCoord("Mumbai", "Asia/Kolkata", 19.0760, 72.8777),
    CityCoord("Colombo", "Asia/Colombo", 6.9271, 79.8612),
    CityCoord("Kathmandu", "Asia/Kathmandu", 27.7172, 85.3240),
    CityCoord("Dhaka", "Asia/Dhaka", 23.8103, 90.4125),

    // UTC+6:30 to UTC+7
    CityCoord("Yangon", "Asia/Yangon", 16.8661, 96.1951),
    CityCoord("Bangkok", "Asia/Bangkok", 13.7563, 100.5018),
    CityCoord("Hanoi", "Asia/Ho_Chi_Minh", 21.0285, 105.8542),
    CityCoord("Jakarta", "Asia/Jakarta", -6.2088, 106.8456),

    // UTC+8
    CityCoord("Singapore", "Asia/Singapore", 1.3521, 103.8198),
    CityCoord("Kuala Lumpur", "Asia/Kuala_Lumpur", 3.1390, 101.6869),
    CityCoord("Manila", "Asia/Manila", 14.5995, 120.9842),
    CityCoord("Beijing", "Asia/Shanghai", 39.9042, 116.4074),
    CityCoord("Shanghai", "Asia/Shanghai", 31.2304, 121.4737),
    CityCoord("Hong Kong", "Asia/Hong_Kong", 22.3193, 114.1694),
    CityCoord("Taipei", "Asia/Taipei", 25.0330, 121.5654),
    CityCoord("Perth", "Australia/Perth", -31.9505, 115.8605),

    // UTC+9 to UTC+9:30
    CityCoord("Seoul", "Asia/Seoul", 37.5665, 126.9780),
    CityCoord("Tokyo", "Asia/Tokyo", 35.6762, 139.6503),
    CityCoord("Darwin", "Australia/Darwin", -12.4634, 130.8456),
    CityCoord("Adelaide", "Australia/Adelaide", -34.9285, 138.6007),

    // UTC+10 to UTC+14
    CityCoord("Brisbane", "Australia/Brisbane", -27.4698, 153.0251),
    CityCoord("Sydney", "Australia/Sydney", -33.8688, 151.2093),
    CityCoord("Melbourne", "Australia/Melbourne", -37.8136, 144.9631),
    CityCoord("Auckland", "Pacific/Auckland", -36.8485, 174.7633),
    CityCoord("Wellington", "Pacific/Auckland", -41.2865, 174.7762),
    CityCoord("Suva", "Pacific/Fiji", -18.1416, 178.4419),
    CityCoord("Kiritimati", "Pacific/Kiritimati", 1.8709, -157.3630)
)

// --- PERSISTENCE HELPERS ---
fun saveHistory(context: Context, history: List<PastTimer>) {
    val prefs = context.getSharedPreferences("OnePageClockPrefs", Context.MODE_PRIVATE)
    val historyString = history.joinToString("|") { "${it.totalSeconds}:${it.isFavorite}" }
    prefs.edit().putString("timer_history", historyString).apply()
}

fun loadHistory(context: Context): List<PastTimer> {
    val prefs = context.getSharedPreferences("OnePageClockPrefs", Context.MODE_PRIVATE)
    val historyString = prefs.getString("timer_history", "") ?: ""
    if (historyString.isEmpty()) return emptyList()
    return historyString.split("|").mapNotNull { item ->
        val parts = item.split(":")
        if (parts.size == 2) {
            val sec = parts[0].toLongOrNull()
            val fav = parts[1].toBoolean()
            if (sec != null) PastTimer(sec, fav) else null
        } else null
    }
}

fun saveWorldClocks(context: Context, clocks: List<WorldClockData>) {
    val prefs = context.getSharedPreferences("OnePageClockPrefs", Context.MODE_PRIVATE)
    val str = clocks.joinToString("|") { "${it.city}::${it.zoneId}::${it.id}" }
    prefs.edit().putString("world_clocks", str).apply()
}

fun loadWorldClocks(context: Context): List<WorldClockData> {
    val prefs = context.getSharedPreferences("OnePageClockPrefs", Context.MODE_PRIVATE)
    val str = prefs.getString("world_clocks", "") ?: ""
    val defaultList = listOf(
        WorldClockData("New York", "America/New_York"),
        WorldClockData("London", "Europe/London"),
        WorldClockData("Paris", "Europe/Paris"),
        WorldClockData("Tokyo", "Asia/Tokyo")
    )
    if (str.isEmpty()) {
        return defaultList
    }
    val loaded = str.split("|").mapNotNull {
        val parts = it.split("::")
        if (parts.size >= 2) {
            val id = if (parts.size >= 3) parts[2] else UUID.randomUUID().toString()
            WorldClockData(parts[0], parts[1], id)
        } else null
    }
    if (loaded.size == 3 && loaded.map { it.city } == listOf("New York", "London", "Tokyo")) {
        saveWorldClocks(context, defaultList)
        return defaultList
    }
    return loaded
}

fun saveAlarms(context: Context, alarms: List<AlarmItem>) {
    val prefs = context.getSharedPreferences("OnePageClockPrefs", Context.MODE_PRIVATE)
    val str = alarms.joinToString("|") {
        "${it.id}::${it.label}::${it.hour}::${it.minute}::${it.amPm}::${it.repeat}::${it.isEnabled}"
    }
    prefs.edit().putString("saved_alarms", str).apply()
    scheduleNextSystemAlarm(context, alarms)
}

fun loadAlarms(context: Context): List<AlarmItem> {
    val prefs = context.getSharedPreferences("OnePageClockPrefs", Context.MODE_PRIVATE)
    val str = prefs.getString("saved_alarms", "") ?: ""
    if (str.isEmpty()) {
        return listOf(
            AlarmItem(label = "Morning", hour = 7, minute = 0, amPm = "AM", repeat = "Mon-Fri", isEnabled = false),
            AlarmItem(label = "Workout", hour = 6, minute = 30, amPm = "PM", repeat = "Daily", isEnabled = false),
            AlarmItem(label = "Weekend", hour = 9, minute = 0, amPm = "AM", repeat = "Once", isEnabled = false)
        )
    }
    return str.split("|").mapNotNull {
        val p = it.split("::")
        if (p.size >= 7) {
            AlarmItem(
                id = p[0],
                label = p[1],
                hour = p[2].toIntOrNull() ?: 7,
                minute = p[3].toIntOrNull() ?: 0,
                amPm = p[4],
                repeat = p[5],
                isEnabled = p[6].toBoolean()
            )
        } else null
    }
}

fun saveAlarmUri(context: Context, uriString: String) {
    val prefs = context.getSharedPreferences("OnePageClockPrefs", Context.MODE_PRIVATE)
    prefs.edit().putString("custom_alarm_uri", uriString).apply()
}

fun loadAlarmUri(context: Context): String? {
    val prefs = context.getSharedPreferences("OnePageClockPrefs", Context.MODE_PRIVATE)
    return prefs.getString("custom_alarm_uri", null)
}

// --- EXACT SYSTEM ALARM SCHEDULER ---
fun computeNextTriggerTime(alarm: AlarmItem, now: ZonedDateTime): ZonedDateTime {
    var h24 = alarm.hour
    if (alarm.amPm == "PM" && h24 < 12) h24 += 12
    if (alarm.amPm == "AM" && h24 == 12) h24 = 0

    var target = now.withHour(h24).withMinute(alarm.minute).withSecond(0).withNano(0)
    if (!target.isAfter(now)) {
        target = target.plusDays(1)
    }
    if (alarm.repeat == "Mon-Fri") {
        while (target.dayOfWeek.value == 6 || target.dayOfWeek.value == 7) {
            target = target.plusDays(1)
        }
    }
    return target
}

fun scheduleNextSystemAlarm(context: Context, alarms: List<AlarmItem>) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AlarmReceiver::class.java).apply {
        action = TimerService.ACTION_ALARM_TRIGGER
    }
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        1001,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val enabledAlarms = alarms.filter { it.isEnabled }
    if (enabledAlarms.isEmpty()) {
        alarmManager.cancel(pendingIntent)
        GlobalState.nextAlarmSummary = ""
        return
    }

    val now = ZonedDateTime.now()
    val nextPair = enabledAlarms.map { it to computeNextTriggerTime(it, now) }
        .minByOrNull { it.second.toInstant().toEpochMilli() } ?: return

    val triggerMillis = nextPair.second.toInstant().toEpochMilli()
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        }
    } catch (e: SecurityException) {
        e.printStackTrace()
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val serviceIntent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_ALARM_TRIGGER
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}

// --- BACKGROUND SERVICE WITH CUTOUT ROUND LOCKSCREEN BUTTONS ---
class TimerService : Service() {
    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_ALARM_TRIGGER = "ACTION_ALARM_TRIGGER"
        const val EXTRA_TOTAL_SECONDS = "EXTRA_TOTAL_SECONDS"
    }

    private val channelId = "one_page_clock_timer_v6"
    private val notificationId = 1
    private lateinit var notificationManager: NotificationManager
    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var countdownJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OnePageClock::TimerWakeLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val total = intent.getLongExtra(EXTRA_TOTAL_SECONDS, GlobalState.timeRemainingSeconds)
                GlobalState.totalTimeSeconds = total
                GlobalState.timeRemainingSeconds = total
                GlobalState.isRunning = true
                GlobalState.isPaused = false
                GlobalState.isRinging = false

                startForeground(notificationId, buildNotification())
                acquireWakeLock()
                startTicker()
            }
            ACTION_PAUSE -> {
                GlobalState.isPaused = true
                updateNotification()
            }
            ACTION_RESUME -> {
                GlobalState.isPaused = false
                acquireWakeLock()
                startTicker()
                updateNotification()
            }
            ACTION_STOP -> {
                stopTimerService()
            }
            ACTION_ALARM_TRIGGER -> {
                GlobalState.isRinging = true
                startForeground(notificationId, buildNotification())
                acquireWakeLock()
                playAlarmSound()
            }
        }
        return START_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld != true) {
            wakeLock?.acquire(12 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun startTicker() {
        countdownJob?.cancel()
        countdownJob = serviceScope.launch {
            var lastTick = SystemClock.elapsedRealtime()
            while (isActive && GlobalState.isRunning && !GlobalState.isPaused) {
                delay(250L)
                val now = SystemClock.elapsedRealtime()
                val elapsedSecs = (now - lastTick) / 1000L
                if (elapsedSecs >= 1L) {
                    lastTick += elapsedSecs * 1000L
                    GlobalState.timeRemainingSeconds = (GlobalState.timeRemainingSeconds - elapsedSecs).coerceAtLeast(0L)
                    updateNotification()

                    if (GlobalState.timeRemainingSeconds == 0L) {
                        GlobalState.isRinging = true
                        playAlarmSound()
                        updateNotification()
                        break
                    }
                }
            }
        }
    }

    private fun playAlarmSound() {
        try {
            var targetUri: Uri? = loadAlarmUri(this)?.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }
            if (targetUri == null) targetUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (targetUri == null) targetUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            if (targetUri == null) targetUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@TimerService, targetUri!!)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopTimerService() {
        countdownJob?.cancel()
        countdownJob = null

        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        releaseWakeLock()
        GlobalState.isRunning = false
        GlobalState.isPaused = false
        GlobalState.isRinging = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Timer & Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        notificationManager.notify(notificationId, buildNotification())
    }

    private fun createRoundActionIcon(type: String): Icon {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = android.graphics.Color.WHITE
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 8f, circlePaint)

        val cutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

        when (type) {
            "PAUSE" -> {
                canvas.drawRoundRect(RectF(43f, 38f, 57f, 90f), 6f, 6f, cutoutPaint)
                canvas.drawRoundRect(RectF(71f, 38f, 85f, 90f), 6f, 6f, cutoutPaint)
            }
            "PLAY" -> {
                val path = AndroidPath().apply {
                    moveTo(48f, 36f)
                    lineTo(92f, 64f)
                    lineTo(48f, 92f)
                    close()
                }
                canvas.drawPath(path, cutoutPaint)
            }
            "STOP" -> {
                canvas.drawRoundRect(RectF(42f, 42f, 86f, 86f), 10f, 10f, cutoutPaint)
            }
        }
        return Icon.createWithBitmap(bitmap)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, TimerService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseIntent = Intent(this, TimerService::class.java).apply { action = ACTION_PAUSE }
        val pausePendingIntent = PendingIntent.getService(
            this, 2, pauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val resumeIntent = Intent(this, TimerService::class.java).apply { action = ACTION_RESUME }
        val resumePendingIntent = PendingIntent.getService(
            this, 3, resumeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val h = GlobalState.timeRemainingSeconds / 3600
        val m = (GlobalState.timeRemainingSeconds % 3600) / 60
        val s = GlobalState.timeRemainingSeconds % 60
        val timeString = if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)

        val content = when {
            GlobalState.isRinging -> "Time's up! Tap Stop to dismiss."
            GlobalState.isPaused -> "Paused • $timeString"
            else -> "Remaining • $timeString"
        }

        val subtext = if (GlobalState.nextAlarmSummary.isNotEmpty())
            " | ${GlobalState.nextAlarmSummary}" else ""

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        builder
            .setContentTitle("OnePageClock")
            .setContentText("$content$subtext")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(openAppPendingIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(GlobalState.isRunning && !GlobalState.isPaused)
            .setOnlyAlertOnce(true)
            .setColor(android.graphics.Color.parseColor("#00E5FF"))
            .setCategory(if (GlobalState.isRinging) Notification.CATEGORY_ALARM else Notification.CATEGORY_STOPWATCH)

        val mediaStyle = Notification.MediaStyle()

        if (GlobalState.isRinging) {
            val stopAction = Notification.Action.Builder(
                createRoundActionIcon("STOP"),
                "Stop",
                stopPendingIntent
            ).build()
            builder.addAction(stopAction)
            mediaStyle.setShowActionsInCompactView(0)
        } else {
            val toggleAction = if (GlobalState.isPaused) {
                Notification.Action.Builder(
                    createRoundActionIcon("PLAY"),
                    "Resume",
                    resumePendingIntent
                ).build()
            } else {
                Notification.Action.Builder(
                    createRoundActionIcon("PAUSE"),
                    "Pause",
                    pausePendingIntent
                ).build()
            }
            val stopAction = Notification.Action.Builder(
                createRoundActionIcon("STOP"),
                "Stop",
                stopPendingIntent
            ).build()
            builder.addAction(toggleAction)
            builder.addAction(stopAction)
            mediaStyle.setShowActionsInCompactView(0, 1)
        }

        builder.setStyle(mediaStyle)
        return builder.build()
    }

    override fun onDestroy() {
        stopTimerService()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = BgDark,
                    surface = CardSurface,
                    primary = AccentCyan,
                    secondary = AccentMint,
                    onBackground = TextPrimary,
                    onSurface = TextPrimary
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TimerScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimerScreen() {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            hasNotificationPermission = isGranted
        }
    LaunchedEffect(Unit) {
        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // --- MUSIC PICKER INTEGRATION ---
    var showMusicChoiceDialog by remember { mutableStateOf(false) }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            val uriStr = uri?.toString() ?: ""
            saveAlarmUri(context, uriStr)
            Toast.makeText(context, "Alarm sound updated!", Toast.LENGTH_SHORT).show()
        }
    }

    val customFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            saveAlarmUri(context, uri.toString())
            Toast.makeText(context, "Custom audio track set!", Toast.LENGTH_SHORT).show()
        }
    }

    if (showMusicChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showMusicChoiceDialog = false },
            title = { Text("Choose Alarm Sound", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            showMusicChoiceDialog = false
                            val currentUri = loadAlarmUri(context)?.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }
                                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                            val pickerIntent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Sound")
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            }
                            ringtonePickerLauncher.launch(pickerIntent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = CardElevated)
                    ) {
                        Text("System Ringtones & Alarms", color = AccentCyan)
                    }
                    FilledTonalButton(
                        onClick = {
                            showMusicChoiceDialog = false
                            customFilePickerLauncher.launch(arrayOf("audio/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = CardElevated)
                    ) {
                        Text("Pick Custom Audio File (MP3)", color = AccentMint)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMusicChoiceDialog = false }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = CardSurface
        )
    }

    // --- SYSTEM TIME TICKER ---
    var currentSystemTime by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentSystemTime = ZonedDateTime.now()
            delay(1000L)
        }
    }

    // --- TIMER STATE ---
    var history by remember { mutableStateOf(loadHistory(context)) }
    var isEditingHistory by remember { mutableStateOf(false) }

    fun updateAndSaveHistory(newList: List<PastTimer>) {
        history = newList
        saveHistory(context, newList)
    }

    var hours by remember { mutableStateOf("") }
    var savedHours by remember { mutableStateOf("") }
    var isHoursFocused by remember { mutableStateOf(false) }
    var hoursDragAmount by remember { mutableFloatStateOf(0f) }
    var hoursHorizontalDrag by remember { mutableFloatStateOf(0f) }

    var minutes by remember { mutableStateOf("") }
    var savedMinutes by remember { mutableStateOf("") }
    var isMinutesFocused by remember { mutableStateOf(false) }
    var minutesDragMode by remember { mutableIntStateOf(0) }
    var minutesDragAmount by remember { mutableFloatStateOf(0f) }
    var minutesHorizontalDrag by remember { mutableFloatStateOf(0f) }

    // --- MULTI-ALARM STATE ---
    var alarms by remember { mutableStateOf(loadAlarms(context)) }
    var selectedAlarmIndex by remember { mutableIntStateOf(0) }
    val currentAlarm = alarms.getOrNull(selectedAlarmIndex) ?: alarms.first()

    var alarmHour by remember(selectedAlarmIndex, alarms.size) { mutableStateOf(currentAlarm.hour.toString()) }
    var alarmMinute by remember(selectedAlarmIndex, alarms.size) { mutableStateOf(String.format("%02d", currentAlarm.minute)) }
    var alarmAmPm by remember(selectedAlarmIndex, alarms.size) { mutableStateOf(currentAlarm.amPm) }
    var alarmRepeat by remember(selectedAlarmIndex, alarms.size) { mutableStateOf(currentAlarm.repeat) }
    val repeatOptions = listOf("Once", "Daily", "Mon-Fri")
    val alarmPanelOffset = remember { Animatable(0f) }

    var savedAlarmHour by remember { mutableStateOf("") }
    var isAlarmHourFocused by remember { mutableStateOf(false) }
    var savedAlarmMinute by remember { mutableStateOf("") }
    var isAlarmMinuteFocused by remember { mutableStateOf(false) }
    val alarmMinuteFocusRequester = remember { FocusRequester() }

    fun syncCurrentAlarm(
        newHour: Int = alarmHour.toIntOrNull() ?: currentAlarm.hour,
        newMin: Int = alarmMinute.toIntOrNull() ?: currentAlarm.minute,
        newAmPm: String = alarmAmPm,
        newRepeat: String = alarmRepeat,
        newEnabled: Boolean = currentAlarm.isEnabled
    ) {
        val mut = alarms.toMutableList()
        if (selectedAlarmIndex in mut.indices) {
            mut[selectedAlarmIndex] = mut[selectedAlarmIndex].copy(
                hour = newHour.coerceIn(1, 12),
                minute = newMin.coerceIn(0, 59),
                amPm = newAmPm,
                repeat = newRepeat,
                isEnabled = newEnabled
            )
            alarms = mut
            saveAlarms(context, mut)
        }
    }

    // Update Alarm Countdown Summary
    LaunchedEffect(currentSystemTime, alarms) {
        val enabled = alarms.filter { it.isEnabled }
        if (enabled.isEmpty()) {
            GlobalState.nextAlarmSummary = ""
        } else {
            val nextPair = enabled.map { it to computeNextTriggerTime(it, currentSystemTime) }
                .minByOrNull { it.second.toInstant().toEpochMilli() }
            if (nextPair != null) {
                val diff = Duration.between(currentSystemTime, nextPair.second)
                val hrs = diff.toHours()
                val mins = (diff.toMinutes() % 60) + 1
                GlobalState.nextAlarmSummary = "${nextPair.first.label} in ${hrs}h ${mins}m"
            }
        }
    }

    // --- STOPWATCH STATE ---
    var swStarted by remember { mutableStateOf(false) }
    var swPaused by remember { mutableStateOf(false) }
    var swElapsedMs by remember { mutableLongStateOf(0L) }
    var swLaps by remember { mutableStateOf(listOf<Long>()) }

    LaunchedEffect(swStarted, swPaused) {
        if (swStarted && !swPaused) {
            var lastTime = SystemClock.elapsedRealtime()
            while (isActive && swStarted && !swPaused) {
                delay(16L)
                val now = SystemClock.elapsedRealtime()
                swElapsedMs += (now - lastTime)
                lastTime = now
            }
        }
    }

    // --- WORLD CLOCK STATE ---
    var worldClocks by remember { mutableStateOf(loadWorldClocks(context)) }
    var isEditingClocks by remember { mutableStateOf(false) }

    fun updateAndSaveWorldClocks(newList: List<WorldClockData>) {
        worldClocks = newList
        saveWorldClocks(context, newList)
    }

    val allAvailableZones = remember {
        ZoneId.getAvailableZoneIds()
            .mapNotNull { id ->
                if (id.contains("/")) {
                    val city = id.substringAfterLast("/").replace("_", " ")
                    WorldClockData(city, id)
                } else null
            }
            .distinctBy { it.city }
            .sortedBy { it.city }
    }
    var showCitySearchForIndex by remember { mutableStateOf<Int?>(null) }
    var isAddingNewClock by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val dragThreshold15 = with(density) { 32.dp.toPx() }
    val dragThreshold1 = with(density) { 16.dp.toPx() }
    val horizontalStepPx = with(density) { 10.dp.toPx() }
    val clockItemWidthPx = with(density) { 80.dp.toPx() }

    // --- 3-WAY CITY SEARCH DIALOG ---
    if (showCitySearchForIndex != null || isAddingNewClock) {
        CityFinderDialog(
            allAvailableZones = allAvailableZones,
            onDismiss = {
                showCitySearchForIndex = null
                isAddingNewClock = false
            },
            onCitySelected = { selectedClock ->
                val mut = worldClocks.toMutableList()
                if (isAddingNewClock && mut.size < 4) {
                    mut.add(WorldClockData(selectedClock.city, selectedClock.zoneId))
                } else if (showCitySearchForIndex != null && showCitySearchForIndex!! < mut.size) {
                    val oldId = mut[showCitySearchForIndex!!].id
                    mut[showCitySearchForIndex!!] = WorldClockData(selectedClock.city, selectedClock.zoneId, oldId)
                }
                updateAndSaveWorldClocks(mut)
                showCitySearchForIndex = null
                isAddingNewClock = false
            }
        )
    }

    // --- TIMER MATH LOGIC ---
    fun wrapBasic(value: Int, max: Int): Int =
        when {
            value < 0 -> max + value + 1
            value > max -> value - max - 1
            else -> value
        }

    fun peekSnapUp(m: Int): Int =
        when {
            m in 45..59 -> 0
            m == 0 -> 15
            else -> (m / 15) * 15 + 15
        }

    fun peekSnapDown(m: Int): Int =
        when {
            m == 0 -> 45
            m in 1..15 -> 0
            else -> {
                val rem = m % 15
                if (rem == 0) m - 15 else m - rem
            }
        }

    fun snapMinutesUp() {
        val h = hours.toIntOrNull() ?: 0
        val m = minutes.substringBefore('.').toIntOrNull() ?: 0
        if (m in 45..59) {
            minutes = "0"
            if (h == 0) hours = "1"
        } else {
            minutes = peekSnapUp(m).toString()
        }
    }

    fun snapMinutesDown() {
        val h = hours.toIntOrNull() ?: 0
        val m = minutes.substringBefore('.').toIntOrNull() ?: 0
        if (m == 0) {
            minutes = "45"
            if (h > 0) hours = (h - 1).toString()
        } else {
            minutes = peekSnapDown(m).toString()
        }
    }

    fun addSingleMinute(delta: Int) {
        val m = minutes.substringBefore('.').toIntOrNull() ?: 0
        minutes = wrapBasic(m + delta, 59).toString()
    }

    fun addHours(delta: Int) {
        val h = hours.toIntOrNull() ?: 0
        hours = wrapBasic(h + delta, 99).toString()
    }

    // Horizontal left-to-right scrubbers (0..24 for hours, 0..60 for min/sec)
    fun scrubHoursHorizontal(delta: Int) {
        val current = (if (isHoursFocused && hours.isEmpty()) savedHours else hours).toIntOrNull() ?: 0
        val next = (current + delta).coerceIn(0, 24)
        hours = next.toString()
        savedHours = hours
    }

    fun scrubMinutesHorizontal(delta: Int) {
        val raw = if (isMinutesFocused && minutes.isEmpty()) savedMinutes else minutes
        if (raw.contains(".")) {
            val parts = raw.split(".")
            val mPart = parts.getOrNull(0) ?: "0"
            val sCur = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val sNext = (sCur + delta).coerceIn(0, 60)
            minutes = "$mPart.${String.format("%02d", sNext)}"
        } else {
            val mCur = raw.toIntOrNull() ?: 0
            val mNext = (mCur + delta).coerceIn(0, 60)
            minutes = mNext.toString()
        }
        savedMinutes = minutes
    }

    fun parseCurrentTimeInput(): Long {
        val h = hours.toLongOrNull() ?: 0L
        var m = 0L
        var s = 0L
        if (minutes.contains(".")) {
            val parts = minutes.split(".")
            m = parts.getOrNull(0)?.toLongOrNull() ?: 0L
            var secStr = parts.getOrNull(1) ?: "0"
            if (secStr.length == 1) secStr += "0"
            if (secStr.length > 2) secStr = secStr.substring(0, 2)
            s = secStr.toLongOrNull() ?: 0L
        } else {
            m = minutes.toLongOrNull() ?: 0L
        }
        return (h * 3600) + (m * 60) + s
    }

    fun tryAddToHistory(seconds: Long, makeFavorite: Boolean) {
        if (seconds <= 0) return
        val existingIndex = history.indexOfFirst { it.totalSeconds == seconds }
        if (existingIndex >= 0) {
            if (makeFavorite && !history[existingIndex].isFavorite) {
                val mut = history.toMutableList()
                mut[existingIndex] = mut[existingIndex].copy(isFavorite = true)
                updateAndSaveHistory(mut)
            }
        } else {
            if (history.size < 8) {
                updateAndSaveHistory(history + PastTimer(seconds, makeFavorite))
            }
        }
    }

    fun toggleFavorite(seconds: Long) {
        if (seconds <= 0) return
        val existingIndex = history.indexOfFirst { it.totalSeconds == seconds }
        if (existingIndex >= 0) {
            val mut = history.toMutableList()
            val currentFav = mut[existingIndex].isFavorite
            mut[existingIndex] = mut[existingIndex].copy(isFavorite = !currentFav)
            updateAndSaveHistory(mut)
        } else {
            if (history.size < 8) {
                updateAndSaveHistory(history + PastTimer(seconds, true))
            }
        }
    }

    fun stepAlarmHour(delta: Int) {
        val h = alarmHour.toIntOrNull() ?: 12
        var newH = h + delta
        when {
            newH > 12 -> newH -= 12
            newH < 1 -> newH += 12
        }
        alarmHour = newH.toString()
        syncCurrentAlarm(newHour = newH)
    }

    fun stepAlarmMin(delta: Int) {
        val m = alarmMinute.toIntOrNull() ?: 0
        var newM = m + delta
        when {
            newM > 59 -> newM -= 60
            newM < 0 -> newM += 60
        }
        alarmMinute = String.format("%02d", newM)
        syncCurrentAlarm(newMin = newM)
    }

    // --- MAIN SCROLLABLE COLUMN (WITH STATUS BAR PADDING) ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        focusManager.clearFocus()
                        isEditingHistory = false
                        isEditingClocks = false
                    }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // ==========================================
        // 1. TOP: PRESET / HISTORY GRID
        // ==========================================
        if (history.isNotEmpty()) {
            val rows = history.chunked(4)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rows.forEachIndexed { rowIndex, rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (col in 0..3) {
                            val item = rowItems.getOrNull(col)
                            val actualIndex = rowIndex * 4 + col
                            if (item != null) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(CardSurface)
                                        .border(
                                            1.dp,
                                            if (item.isFavorite) AccentGold.copy(alpha = 0.4f) else BorderSubtle,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .combinedClickable(
                                            onClick = {
                                                if (isEditingHistory) {
                                                    val mut = history.toMutableList()
                                                    mut.removeAt(actualIndex)
                                                    updateAndSaveHistory(mut)
                                                    if (mut.isEmpty()) isEditingHistory = false
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                } else if (!GlobalState.isRunning) {
                                                    focusManager.clearFocus()
                                                    val intent = Intent(context, TimerService::class.java).apply {
                                                        action = TimerService.ACTION_START
                                                        putExtra(TimerService.EXTRA_TOTAL_SECONDS, item.totalSeconds)
                                                    }
                                                    ContextCompat.startForegroundService(context, intent)
                                                }
                                            },
                                            onLongClick = {
                                                isEditingHistory = true
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val h = item.totalSeconds / 3600
                                    val m = (item.totalSeconds % 3600) / 60
                                    val s = item.totalSeconds % 60
                                    val text = if (h > 0) String.format("%d:%02d:%02d", h, m, s)
                                    else String.format("%d:%02d", m, s)
                                    Text(
                                        text = text,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp
                                    )
                                    if (item.isFavorite) {
                                        Canvas(
                                            modifier = Modifier
                                                .padding(6.dp)
                                                .size(6.dp)
                                                .align(Alignment.TopStart)
                                        ) { drawCircle(AccentGold) }
                                    }
                                    if (isEditingHistory) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Delete",
                                            tint = BgDark,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .size(16.dp)
                                                .background(AccentCoral, CircleShape)
                                                .padding(2.dp)
                                        )
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // ==========================================
        // 2. TIMER INPUTS & ACTION CARD
        // ==========================================
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // HOURS COLUMN (Supports Vertical Drag + Horizontal Left-to-Right Scrubbing up to 24)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        hoursDragAmount = 0f
                                        focusManager.clearFocus()
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    hoursDragAmount += dragAmount
                                    if (hoursDragAmount <= -dragThreshold15) {
                                        addHours(1)
                                        hoursDragAmount = 0f
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    } else if (hoursDragAmount >= dragThreshold15) {
                                        addHours(-1)
                                        hoursDragAmount = 0f
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        hoursHorizontalDrag = 0f
                                        focusManager.clearFocus()
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    hoursHorizontalDrag += dragAmount
                                    if (abs(hoursHorizontalDrag) >= horizontalStepPx) {
                                        val steps = (hoursHorizontalDrag / horizontalStepPx).toInt()
                                        scrubHoursHorizontal(steps)
                                        hoursHorizontalDrag -= steps * horizontalStepPx
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            onClick = { addHours(1); focusManager.clearFocus() },
                            modifier = Modifier.size(32.dp)
                        ) { Icon(Icons.Default.KeyboardArrowUp, "Hour Up", tint = TextSecondary) }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = hours,
                                onValueChange = { if (it.length <= 2) hours = it.filter { c -> c.isDigit() } },
                                placeholder = {
                                    Text(
                                        "00",
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center,
                                        color = TextSecondary.copy(alpha = 0.4f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 24.sp
                                    )
                                },
                                label = { Text("HOURS", fontSize = 10.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { state ->
                                        if (state.isFocused && !isHoursFocused) {
                                            savedHours = hours; hours = ""; isHoursFocused = true
                                        } else if (!state.isFocused && isHoursFocused) {
                                            if (hours.isEmpty()) hours = savedHours
                                            isHoursFocused = false
                                        }
                                    },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                textStyle = LocalTextStyle.current.copy(
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 24.sp
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedBorderColor = AccentCyan,
                                    unfocusedBorderColor = BorderSubtle,
                                    focusedContainerColor = CardElevated,
                                    unfocusedContainerColor = CardElevated
                                )
                            )

                            if (hours.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 6.dp, end = 6.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(AccentCoral)
                                        .clickable {
                                            hours = ""
                                            savedHours = ""
                                            focusManager.clearFocus()
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear Hours",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { addHours(-1); focusManager.clearFocus() },
                            modifier = Modifier.size(32.dp)
                        ) { Icon(Icons.Default.KeyboardArrowDown, "Hour Down", tint = TextSecondary) }
                    }

                    // MINUTES COLUMN (Supports Vertical Drag + Horizontal Left-to-Right Scrubbing up to 60)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragStart = { offset ->
                                        minutesDragMode = if (offset.x < size.width / 2f) 1 else 2
                                        minutesDragAmount = 0f
                                        focusManager.clearFocus()
                                    },
                                    onDragEnd = { minutesDragMode = 0 },
                                    onDragCancel = { minutesDragMode = 0 }
                                ) { change, dragAmount ->
                                    if (minutesDragMode == 0) return@detectVerticalDragGestures
                                    change.consume()
                                    minutesDragAmount += dragAmount
                                    val threshold = if (minutesDragMode == 1) dragThreshold15 else dragThreshold1
                                    if (minutesDragAmount <= -threshold) {
                                        if (minutesDragMode == 1) snapMinutesUp() else addSingleMinute(1)
                                        minutesDragAmount = 0f
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    } else if (minutesDragAmount >= threshold) {
                                        if (minutesDragMode == 1) snapMinutesDown() else addSingleMinute(-1)
                                        minutesDragAmount = 0f
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        minutesHorizontalDrag = 0f
                                        focusManager.clearFocus()
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    minutesHorizontalDrag += dragAmount
                                    if (abs(minutesHorizontalDrag) >= horizontalStepPx) {
                                        val steps = (minutesHorizontalDrag / horizontalStepPx).toInt()
                                        scrubMinutesHorizontal(steps)
                                        minutesHorizontalDrag -= steps * horizontalStepPx
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(
                                onClick = { snapMinutesUp(); focusManager.clearFocus() },
                                modifier = Modifier.size(32.dp)
                            ) { Icon(Icons.Default.KeyboardArrowUp, "Up 15", tint = TextSecondary) }
                            IconButton(
                                onClick = { addSingleMinute(1); focusManager.clearFocus() },
                                modifier = Modifier.size(32.dp)
                            ) { Icon(Icons.Default.KeyboardDoubleArrowUp, "Up 1", tint = AccentCyan) }
                        }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = minutes,
                                onValueChange = { input ->
                                    val filtered = input.filter { c -> c.isDigit() || c == '.' }
                                    if (filtered.count { it == '.' } <= 1 && filtered.length <= 5) minutes = filtered
                                },
                                placeholder = {
                                    Text(
                                        "00",
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center,
                                        color = TextSecondary.copy(alpha = 0.4f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 24.sp
                                    )
                                },
                                label = { Text("MIN.SEC", fontSize = 10.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { state ->
                                        if (state.isFocused && !isMinutesFocused) {
                                            savedMinutes = minutes; minutes = ""; isMinutesFocused = true
                                        } else if (!state.isFocused && isMinutesFocused) {
                                            if (minutes.isEmpty()) minutes = savedMinutes
                                            isMinutesFocused = false
                                        }
                                    },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                textStyle = LocalTextStyle.current.copy(
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 24.sp
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedBorderColor = AccentCyan,
                                    unfocusedBorderColor = BorderSubtle,
                                    focusedContainerColor = CardElevated,
                                    unfocusedContainerColor = CardElevated
                                )
                            )

                            if (minutes.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 6.dp, end = 6.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(AccentCoral)
                                        .clickable {
                                            minutes = ""
                                            savedMinutes = ""
                                            focusManager.clearFocus()
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear Minutes/Seconds",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(
                                onClick = { snapMinutesDown(); focusManager.clearFocus() },
                                modifier = Modifier.size(32.dp)
                            ) { Icon(Icons.Default.KeyboardArrowDown, "Down 15", tint = TextSecondary) }
                            IconButton(
                                onClick = { addSingleMinute(-1); focusManager.clearFocus() },
                                modifier = Modifier.size(32.dp)
                            ) { Icon(Icons.Default.KeyboardDoubleArrowDown, "Down 1", tint = AccentCyan) }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // TIMER ACTIVE OR START BUTTON
                if (GlobalState.isRunning || GlobalState.isRinging) {
                    val totalSecs = GlobalState.totalTimeSeconds
                    val elapsedSeconds = (totalSecs - GlobalState.timeRemainingSeconds).coerceAtLeast(0L)
                    val progress = if (totalSecs > 0) elapsedSeconds.toFloat() / totalSecs.toFloat() else 0f

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .clip(RoundedCornerShape(50))
                            .background(CardElevated)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(50))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(AccentCyan.copy(alpha = 0.35f), AccentMint.copy(alpha = 0.55f))
                                    )
                                )
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val h = GlobalState.timeRemainingSeconds / 3600
                            val m = (GlobalState.timeRemainingSeconds % 3600) / 60
                            val s = GlobalState.timeRemainingSeconds % 60
                            val timeString = if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
                            else String.format("%02d:%02d", m, s)

                            Text(
                                if (GlobalState.isRinging) "TIME'S UP!" else timeString,
                                color = TextPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.weight(1f))

                            if (!GlobalState.isRinging) {
                                IconButton(
                                    onClick = {
                                        val action = if (GlobalState.isPaused) TimerService.ACTION_RESUME else TimerService.ACTION_PAUSE
                                        context.startService(Intent(context, TimerService::class.java).apply { this.action = action })
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(AccentCyan, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = if (GlobalState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                        contentDescription = "Pause/Resume",
                                        tint = BgDark
                                    )
                                }
                                // Separated Pause and Stop buttons further apart
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                            IconButton(
                                onClick = {
                                    context.startService(Intent(context, TimerService::class.java).apply {
                                        action = TimerService.ACTION_STOP
                                    })
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(AccentCoral, CircleShape)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                val totalSeconds = parseCurrentTimeInput()
                                if (totalSeconds > 0) {
                                    tryAddToHistory(totalSeconds, false)
                                    val intent = Intent(context, TimerService::class.java).apply {
                                        action = TimerService.ACTION_START
                                        putExtra(TimerService.EXTRA_TOTAL_SECONDS, totalSeconds)
                                    }
                                    ContextCompat.startForegroundService(context, intent)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentCyan,
                                contentColor = BgDark
                            )
                        ) {
                            Text("START TIMER", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                        }
                        val currentInputSeconds = parseCurrentTimeInput()
                        val isCurrentInputFavorite = currentInputSeconds > 0 && history.any { it.totalSeconds == currentInputSeconds && it.isFavorite }
                        Spacer(modifier = Modifier.width(16.dp))

                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                if (currentInputSeconds > 0) toggleFavorite(currentInputSeconds)
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(CardElevated)
                                .border(
                                    1.dp,
                                    if (isCurrentInputFavorite) AccentGold.copy(alpha = 0.6f) else BorderSubtle,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = if (isCurrentInputFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (isCurrentInputFavorite) "Favorited" else "Not Favorited",
                                tint = if (isCurrentInputFavorite) AccentGold else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ==========================================
        // 3. WORLD CLOCKS (Wider left-swipe + Top-Left Edit Pen in Edit Mode)
        // ==========================================
        var draggedClockId by remember { mutableStateOf<String?>(null) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            worldClocks.take(4).forEach { clock ->
                key(clock.id) {
                    val clockOffset = remember { Animatable(0f) }
                    var localDragOffset by remember { mutableFloatStateOf(0f) }
                    val currentIndex = worldClocks.indexOf(clock)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(92.dp)
                            .zIndex(if (draggedClockId == clock.id) 1f else 0f)
                            .offset { IntOffset(localDragOffset.roundToInt(), 0) }
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardElevated)
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        isEditingClocks = true
                                        draggedClockId = clock.id
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragEnd = {
                                        draggedClockId = null
                                        localDragOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggedClockId = null
                                        localDragOffset = 0f
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    localDragOffset += dragAmount.x
                                    val currentIdx = worldClocks.indexOf(clock)
                                    if (localDragOffset > clockItemWidthPx && currentIdx < worldClocks.size - 1) {
                                        val mut = worldClocks.toMutableList()
                                        val temp = mut[currentIdx]
                                        mut[currentIdx] = mut[currentIdx + 1]
                                        mut[currentIdx + 1] = temp
                                        updateAndSaveWorldClocks(mut)
                                        localDragOffset -= clockItemWidthPx
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    } else if (localDragOffset < -clockItemWidthPx && currentIdx > 0) {
                                        val mut = worldClocks.toMutableList()
                                        val temp = mut[currentIdx]
                                        mut[currentIdx] = mut[currentIdx - 1]
                                        mut[currentIdx - 1] = temp
                                        updateAndSaveWorldClocks(mut)
                                        localDragOffset += clockItemWidthPx
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            }
                    ) {
                        // Underlying Edit Icon revealed when swiping left
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .padding(end = 8.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            IconButton(
                                onClick = {
                                    showCitySearchForIndex = currentIndex
                                    coroutineScope.launch { clockOffset.animateTo(0f) }
                                },
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(AccentCyan.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit City",
                                    tint = AccentCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Foreground World Clock Card (Slides up to -110f px so Edit button is fully clear)
                        Column(
                            modifier = Modifier
                                .offset { IntOffset(clockOffset.value.roundToInt(), 0) }
                                .fillMaxSize()
                                .background(CardSurface, RoundedCornerShape(16.dp))
                                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                                .padding(horizontal = 6.dp, vertical = 8.dp)
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            coroutineScope.launch {
                                                clockOffset.animateTo(if (clockOffset.value < -40f) -110f else 0f)
                                            }
                                        }
                                    ) { _, dragAmount ->
                                        coroutineScope.launch {
                                            clockOffset.snapTo((clockOffset.value + dragAmount).coerceIn(-125f, 0f))
                                        }
                                    }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val localZone = ZoneId.systemDefault()
                            val targetZone = try { ZoneId.of(clock.zoneId) } catch (e: Exception) { ZoneId.of("UTC") }
                            val localNow = currentSystemTime.withZoneSameInstant(localZone)
                            val targetNow = currentSystemTime.withZoneSameInstant(targetZone)
                            val diffHours = (targetNow.offset.totalSeconds - localNow.offset.totalSeconds) / 3600
                            val diffStr = when {
                                diffHours > 0 -> "+${diffHours}h"
                                diffHours < 0 -> "${diffHours}h"
                                else -> "0h"
                            }

                            Text(
                                clock.city,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${targetNow.format(DateTimeFormatter.ofPattern("EEE"))} • $diffStr",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                targetNow.format(DateTimeFormatter.ofPattern("HH:mm")),
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 17.sp,
                                maxLines = 1
                            )
                        }

                        // Edit Mode Overlays: Top-Left Edit Pen + Top-Right Delete X
                        if (isEditingClocks) {
                            // Top-Left Pen Badge (2nd way to edit a city)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp)
                                    .size(20.dp)
                                    .background(AccentCyan, CircleShape)
                                    .clickable {
                                        val currentIdx = worldClocks.indexOf(clock)
                                        if (currentIdx != -1) {
                                            showCitySearchForIndex = currentIdx
                                            isEditingClocks = false
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit City",
                                    tint = BgDark,
                                    modifier = Modifier.size(12.dp)
                                )
                            }

                            // Top-Right Delete Badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(20.dp)
                                    .background(AccentCoral, CircleShape)
                                    .clickable {
                                        val currentIdx = worldClocks.indexOf(clock)
                                        if (currentIdx != -1) {
                                            val mut = worldClocks.toMutableList()
                                            mut.removeAt(currentIdx)
                                            updateAndSaveWorldClocks(mut)
                                            if (mut.isEmpty()) isEditingClocks = false
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Delete",
                                    tint = BgDark,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (worldClocks.size < 4) {
                Box(
                    modifier = Modifier
                        .weight((4 - worldClocks.size).toFloat())
                        .height(92.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    IconButton(
                        onClick = { isAddingNewClock = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardSurface)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add Clock",
                            tint = AccentCyan,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ==========================================
        // 4. ALARM SYSTEM
        // ==========================================
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    alarms.forEachIndexed { idx, item ->
                        val isSelected = idx == selectedAlarmIndex
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) AccentCyan.copy(alpha = 0.18f) else CardSurface,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) AccentCyan else if (item.isEnabled) AccentMint.copy(alpha = 0.5f) else BorderSubtle
                            ),
                            modifier = Modifier.combinedClickable(
                                onClick = { selectedAlarmIndex = idx },
                                onLongClick = {
                                    if (alarms.size > 1) {
                                        val mut = alarms.toMutableList()
                                        mut.removeAt(idx)
                                        if (selectedAlarmIndex >= mut.size) selectedAlarmIndex = mut.size - 1
                                        alarms = mut
                                        saveAlarms(context, mut)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Canvas(modifier = Modifier.size(6.dp)) {
                                    drawCircle(if (item.isEnabled) AccentMint else TextSecondary.copy(alpha = 0.4f))
                                }
                                Text(
                                    text = "${item.hour}:${String.format("%02d", item.minute)} ${item.amPm}",
                                    color = if (isSelected) AccentCyan else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                if (alarms.size < 6) {
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(
                        onClick = {
                            val mut = alarms.toMutableList()
                            mut.add(
                                AlarmItem(
                                    label = "Alarm ${mut.size + 1}",
                                    hour = 8,
                                    minute = 0,
                                    amPm = "AM",
                                    repeat = "Once",
                                    isEnabled = true
                                )
                            )
                            alarms = mut
                            selectedAlarmIndex = mut.size - 1
                            saveAlarms(context, mut)
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(CardSurface)
                            .border(1.dp, BorderSubtle, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add Alarm",
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardElevated)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp, top = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            val next = repeatOptions.indexOf(alarmRepeat) + 1
                            alarmRepeat = repeatOptions[if (next >= repeatOptions.size) 0 else next]
                            syncCurrentAlarm(newRepeat = alarmRepeat)
                        }
                    ) {
                        Text(alarmRepeat, color = AccentCyan, fontWeight = FontWeight.Bold)
                    }

                    IconButton(onClick = { showMusicChoiceDialog = true }) {
                        Icon(Icons.Default.LibraryMusic, contentDescription = "Choose Alarm Song", tint = AccentMint)
                    }

                    if (alarms.size > 1) {
                        IconButton(onClick = {
                            val mut = alarms.toMutableList()
                            mut.removeAt(selectedAlarmIndex)
                            if (selectedAlarmIndex >= mut.size) selectedAlarmIndex = mut.size - 1
                            alarms = mut
                            saveAlarms(context, mut)
                            coroutineScope.launch { alarmPanelOffset.animateTo(0f) }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Alarm", tint = AccentCoral)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .offset { IntOffset(alarmPanelOffset.value.roundToInt(), 0) }
                        .fillMaxWidth()
                        .background(CardSurface, RoundedCornerShape(24.dp))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(24.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    coroutineScope.launch {
                                        alarmPanelOffset.animateTo(if (alarmPanelOffset.value < -100f) -360f else 0f)
                                    }
                                }
                            ) { _, dragAmount ->
                                coroutineScope.launch {
                                    alarmPanelOffset.snapTo((alarmPanelOffset.value + dragAmount).coerceIn(-380f, 0f))
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = alarmHour,
                                onValueChange = {
                                    if (it.length <= 2) {
                                        alarmHour = it.filter { c -> c.isDigit() }
                                        alarmHour.toIntOrNull()?.let { h -> syncCurrentAlarm(newHour = h) }
                                        if (alarmHour.length == 2) alarmMinuteFocusRequester.requestFocus()
                                    }
                                },
                                modifier = Modifier
                                    .width(64.dp)
                                    .onFocusChanged { state ->
                                        if (state.isFocused && !isAlarmHourFocused) {
                                            savedAlarmHour = alarmHour; alarmHour = ""; isAlarmHourFocused = true
                                        } else if (!state.isFocused && isAlarmHourFocused) {
                                            if (alarmHour.isEmpty()) alarmHour = savedAlarmHour
                                            val coerced = (alarmHour.toIntOrNull() ?: 12).coerceIn(1, 12)
                                            alarmHour = coerced.toString()
                                            syncCurrentAlarm(newHour = coerced)
                                            isAlarmHourFocused = false
                                        }
                                    }
                                    .pointerInput(selectedAlarmIndex) {
                                        detectVerticalDragGestures { change, dragAmount ->
                                            change.consume()
                                            if (dragAmount <= -dragThreshold1) {
                                                stepAlarmHour(1)
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            } else if (dragAmount >= dragThreshold1) {
                                                stepAlarmHour(-1)
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        }
                                    },
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 26.sp,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )

                            Text(":", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = AccentCyan)

                            OutlinedTextField(
                                value = alarmMinute,
                                onValueChange = {
                                    if (it.length <= 2) {
                                        alarmMinute = it.filter { c -> c.isDigit() }
                                        alarmMinute.toIntOrNull()?.let { m -> syncCurrentAlarm(newMin = m) }
                                    }
                                },
                                modifier = Modifier
                                    .width(68.dp)
                                    .focusRequester(alarmMinuteFocusRequester)
                                    .onFocusChanged { state ->
                                        if (state.isFocused && !isAlarmMinuteFocused) {
                                            savedAlarmMinute = alarmMinute; alarmMinute = ""; isAlarmMinuteFocused = true
                                        } else if (!state.isFocused && isAlarmMinuteFocused) {
                                            if (alarmMinute.isEmpty()) alarmMinute = savedAlarmMinute
                                            val coerced = (alarmMinute.toIntOrNull() ?: 0).coerceIn(0, 59)
                                            alarmMinute = String.format("%02d", coerced)
                                            syncCurrentAlarm(newMin = coerced)
                                            isAlarmMinuteFocused = false
                                        }
                                    }
                                    .pointerInput(selectedAlarmIndex) {
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false)
                                            var isLongPress = false
                                            try {
                                                withTimeout(500) {
                                                    var slopCrossed = false
                                                    var accumulatedY = 0f
                                                    while (!slopCrossed) {
                                                        val event = awaitPointerEvent(PointerEventPass.Main)
                                                        val change = event.changes.firstOrNull() ?: break
                                                        if (!change.pressed) break
                                                        accumulatedY += change.positionChange().y
                                                        if (abs(accumulatedY) > 10f) slopCrossed = true
                                                    }
                                                }
                                            } catch (e: PointerEventTimeoutCancellationException) {
                                                isLongPress = true
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }

                                            var dragY = 0f
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Main)
                                                val change = event.changes.firstOrNull()
                                                if (change == null || !change.pressed) break
                                                change.consume()
                                                dragY += change.positionChange().y

                                                if (dragY <= -32f) {
                                                    stepAlarmMin(if (isLongPress) 1 else 10)
                                                    dragY = 0f
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                } else if (dragY >= 32f) {
                                                    stepAlarmMin(if (isLongPress) -1 else -10)
                                                    dragY = 0f
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            }
                                        }
                                    },
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 26.sp,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )

                            Text(
                                text = alarmAmPm,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CardElevated)
                                    .clickable {
                                        alarmAmPm = if (alarmAmPm == "AM") "PM" else "AM"
                                        syncCurrentAlarm(newAmPm = alarmAmPm)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                color = AccentMint,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        Switch(
                            checked = currentAlarm.isEnabled,
                            onCheckedChange = { syncCurrentAlarm(newEnabled = it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BgDark,
                                checkedTrackColor = AccentMint
                            )
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (GlobalState.nextAlarmSummary.isNotEmpty()) GlobalState.nextAlarmSummary else "No active alarms",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Swipe left for sound & repeat (${currentAlarm.repeat})",
                            color = TextSecondary.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ==========================================
        // 5. BOTTOM: STOPWATCH & LAP TRACKER
        // ==========================================
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val minutesSw = (swElapsedMs / 60000)
                    val secondsSw = (swElapsedMs % 60000) / 1000
                    val centisSw = (swElapsedMs % 1000) / 10
                    val swFormatted = String.format("%02d:%02d.%02d", minutesSw, secondsSw, centisSw)

                    Column {
                        Text(
                            text = "STOPWATCH",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = swFormatted,
                            color = TextPrimary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (!swStarted) {
                        Button(
                            onClick = {
                                swStarted = true
                                swPaused = false
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentMint,
                                contentColor = BgDark
                            ),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("START", fontWeight = FontWeight.ExtraBold)
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledIconButton(
                                onClick = {
                                    swPaused = !swPaused
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                modifier = Modifier.size(44.dp),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = AccentCyan,
                                    contentColor = BgDark
                                )
                            ) {
                                Icon(
                                    if (swPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = if (swPaused) "Resume" else "Pause"
                                )
                            }

                            FilledIconButton(
                                onClick = {
                                    swStarted = false
                                    swPaused = false
                                    swElapsedMs = 0L
                                    swLaps = emptyList()
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                modifier = Modifier.size(44.dp),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = AccentCoral,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop")
                            }

                            FilledIconButton(
                                onClick = {
                                    swLaps = listOf(swElapsedMs) + swLaps
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                modifier = Modifier.size(44.dp),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = CardElevated,
                                    contentColor = AccentGold
                                )
                            ) {
                                Icon(Icons.Default.Flag, contentDescription = "Lap")
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = swLaps.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                    ) {
                        HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(bottom = 8.dp))
                        swLaps.forEachIndexed { index, totalMs ->
                            val lapNumber = swLaps.size - index
                            val prevTotalMs = swLaps.getOrNull(index + 1) ?: 0L
                            val splitMs = (totalMs - prevTotalMs).coerceAtLeast(0L)

                            val splitStr = String.format(
                                "+%02d:%02d.%02d",
                                splitMs / 60000,
                                (splitMs % 60000) / 1000,
                                (splitMs % 1000) / 10
                            )
                            val totalStr = String.format(
                                "%02d:%02d.%02d",
                                totalMs / 60000,
                                (totalMs % 60000) / 1000,
                                (totalMs % 1000) / 10
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Lap $lapNumber",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = splitStr,
                                    color = AccentCyan,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = totalStr,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 3-MODE CITY FINDER DIALOG: TYPING NAME, COORDINATES, OR INTERACTIVE MAP
// =========================================================================
@Composable
fun CityFinderDialog(
    allAvailableZones: List<WorldClockData>,
    onDismiss: () -> Unit,
    onCitySelected: (WorldClockData) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var citySearchText by remember { mutableStateOf("") }

    var latInput by remember { mutableStateOf("51.51") }
    var lonInput by remember { mutableStateOf("-0.13") }

    var mapCrosshair by remember { mutableStateOf(Offset(0.50f, 0.21f)) }

    fun findClosestCity(lat: Double, lon: Double): CityCoord {
        return KNOWN_CITY_COORDS.minByOrNull {
            hypot(it.lat - lat, it.lon - lon)
        } ?: KNOWN_CITY_COORDS.first()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Find World City", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardElevated)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("By Name", "Coordinates", "World Map").forEachIndexed { idx, title ->
                        val active = selectedTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) AccentCyan else Color.Transparent)
                                .clickable { selectedTab = idx }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = if (active) BgDark else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        text = {
            when (selectedTab) {
                0 -> {
                    Column {
                        OutlinedTextField(
                            value = citySearchText,
                            onValueChange = { citySearchText = it },
                            label = { Text("Type city name") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = BorderSubtle
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.height(240.dp)) {
                            val filtered = allAvailableZones.filter {
                                it.city.contains(citySearchText, ignoreCase = true)
                            }
                            items(filtered.take(35)) { zone ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onCitySelected(zone) }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(zone.city, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                    Text(zone.zoneId.substringBefore("/"), color = TextSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                1 -> {
                    val latVal = latInput.toDoubleOrNull() ?: 0.0
                    val lonVal = lonInput.toDoubleOrNull() ?: 0.0
                    val closest = findClosestCity(latVal, lonVal)
                    val approxOffset = (lonVal / 15.0).roundToInt().coerceIn(-12, 14)
                    val etcZoneId = if (approxOffset >= 0) "Etc/GMT-$approxOffset" else "Etc/GMT+${abs(approxOffset)}"

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = latInput,
                                onValueChange = { latInput = it },
                                label = { Text("Latitude (-90..90)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = lonInput,
                                onValueChange = { lonInput = it },
                                label = { Text("Longitude (-180..180)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        Surface(
                            color = CardElevated,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Nearest Major City:", color = TextSecondary, fontSize = 11.sp)
                                Text(
                                    "${closest.city} (${closest.lat}°, ${closest.lon}°)",
                                    color = AccentCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { onCitySelected(WorldClockData(closest.city, closest.zoneId)) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgDark),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Use ${closest.city}", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val label = String.format("%.1f°, %.1f°", latVal, lonVal)
                                        onCitySelected(WorldClockData(label, etcZoneId))
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val utcSolarLabel = String.format("UTC%+03d:00", approxOffset)
                                    Text("Use Exact Solar Offset ($utcSolarLabel)", color = AccentMint, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                2 -> {
                    val selectedLat = ((0.5f - mapCrosshair.y) * 180.0).coerceIn(-85.0, 85.0)
                    val selectedLon = ((mapCrosshair.x - 0.5f) * 360.0).coerceIn(-180.0, 180.0)
                    val nearestOnMap = findClosestCity(selectedLat, selectedLon)

                    val cityZone = try { ZoneId.of(nearestOnMap.zoneId) } catch (e: Exception) { ZoneId.of("UTC") }
                    val cityZoned = ZonedDateTime.now(cityZone)
                    val totalOffsetSec = cityZoned.offset.totalSeconds
                    val offHours = totalOffsetSec / 3600
                    val offMins = abs((totalOffsetSec % 3600) / 60)
                    val utcString = String.format("UTC%+03d:%02d", offHours, offMins)
                    val localTimeString = cityZoned.format(DateTimeFormatter.ofPattern("hh:mm a"))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Tap or drag the crosshair across the map:",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF0B1528))
                                .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        mapCrosshair = Offset(
                                            (offset.x / size.width).coerceIn(0f, 1f),
                                            (offset.y / size.height).coerceIn(0f, 1f)
                                        )
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures { change, _ ->
                                        change.consume()
                                        mapCrosshair = Offset(
                                            (change.position.x / size.width).coerceIn(0f, 1f),
                                            (change.position.y / size.height).coerceIn(0f, 1f)
                                        )
                                    }
                                }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height

                                // 24 Standard 1-Hour Time Zone Meridians (every 15° longitude)
                                for (i in 0..24) {
                                    val mx = (i / 24f) * w
                                    val isCurrentZone = abs(mx - mapCrosshair.x * w) < (w / 48f)
                                    drawLine(
                                        color = if (isCurrentZone) AccentCyan.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.05f),
                                        start = Offset(mx, 0f),
                                        end = Offset(mx, h),
                                        strokeWidth = if (isCurrentZone) 1.5.dp.toPx() else 1.dp.toPx()
                                    )
                                }
                                for (i in 1..3) {
                                    drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, h * i / 4f), Offset(w, h * i / 4f))
                                }

                                val landColor = Color(0xFF1E3557)
                                drawRoundRect(landColor, Offset(w * 0.10f, h * 0.16f), Size(w * 0.22f, h * 0.26f), androidx.compose.ui.geometry.CornerRadius(14f))
                                drawRoundRect(landColor, Offset(w * 0.24f, h * 0.48f), Size(w * 0.11f, h * 0.34f), androidx.compose.ui.geometry.CornerRadius(14f))
                                drawRoundRect(landColor, Offset(w * 0.44f, h * 0.14f), Size(w * 0.42f, h * 0.32f), androidx.compose.ui.geometry.CornerRadius(18f))
                                drawRoundRect(landColor, Offset(w * 0.45f, h * 0.38f), Size(w * 0.16f, h * 0.34f), androidx.compose.ui.geometry.CornerRadius(16f))
                                drawRoundRect(landColor, Offset(w * 0.76f, h * 0.62f), Size(w * 0.14f, h * 0.18f), androidx.compose.ui.geometry.CornerRadius(12f))

                                KNOWN_CITY_COORDS.forEach { city ->
                                    val cx = ((city.lon + 180.0) / 360.0).toFloat() * w
                                    val cy = ((90.0 - city.lat) / 180.0).toFloat() * h
                                    val isNearest = city.city == nearestOnMap.city
                                    drawCircle(
                                        color = if (isNearest) AccentMint else Color.White.copy(alpha = 0.45f),
                                        radius = if (isNearest) 5.5.dp.toPx() else 2.dp.toPx(),
                                        center = Offset(cx, cy)
                                    )
                                }

                                val px = mapCrosshair.x * w
                                val py = mapCrosshair.y * h
                                drawCircle(AccentCyan, radius = 9.dp.toPx(), center = Offset(px, py), style = Stroke(width = 2.dp.toPx()))
                                drawLine(AccentCyan.copy(alpha = 0.5f), Offset(px, 0f), Offset(px, h))
                                drawLine(AccentCyan.copy(alpha = 0.5f), Offset(0f, py), Offset(w, py))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    nearestOnMap.city,
                                    color = AccentMint,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "$utcString • $localTimeString",
                                    color = AccentCyan,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    String.format("Lat %.1f°, Lon %.1f°", selectedLat, selectedLon),
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onCitySelected(WorldClockData(nearestOnMap.city, nearestOnMap.zoneId)) },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgDark)
                            ) {
                                Text("Select City", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = CardSurface
    )
}
