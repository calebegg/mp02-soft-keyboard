package gg.lebe.mp02keyboard.ime

import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager

class Mp02Preferences(context: Context) {
    private val prefs: SharedPreferences = run {
        val deviceCtx = context.createDeviceProtectedStorageContext()
        val um = context.getSystemService(UserManager::class.java)
        if (um?.isUserUnlocked != false) {
            runCatching { deviceCtx.moveSharedPreferencesFrom(context, PREFS_NAME) }
        }
        deviceCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var finish: Mp02Finish
        get() = runCatching {
            Mp02Finish.valueOf(prefs.getString(KEY_FINISH, Mp02Finish.ONYX.name)!!)
        }.getOrDefault(Mp02Finish.ONYX)
        set(value) = prefs.edit().putString(KEY_FINISH, value.name).apply()

    var heightScale: Float
        get() = prefs.getFloat(KEY_HEIGHT_SCALE, 1.00f).coerceIn(0.90f, 1.25f)
        set(value) = prefs.edit().putFloat(KEY_HEIGHT_SCALE, value.coerceIn(0.90f, 1.25f)).apply()

    var bottomChinMm: Float
        get() = prefs.getFloat(KEY_BOTTOM_CHIN_MM, 2.2f).coerceIn(0.8f, 6.0f)
        set(value) = prefs.edit().putFloat(KEY_BOTTOM_CHIN_MM, value.coerceIn(0.8f, 6.0f)).apply()

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    var backlightEnabled: Boolean
        get() = prefs.getBoolean(KEY_BACKLIGHT, true)
        set(value) = prefs.edit().putBoolean(KEY_BACKLIGHT, value).apply()

    var autoCapEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CAP, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CAP, value).apply()

    var doubleSpacePeriod: Boolean
        get() = prefs.getBoolean(KEY_DOUBLE_SPACE_PERIOD, true)
        set(value) = prefs.edit().putBoolean(KEY_DOUBLE_SPACE_PERIOD, value).apply()

    var spacebarTrackpad: Boolean
        get() = prefs.getBoolean(KEY_SPACEBAR_TRACKPAD, true)
        set(value) = prefs.edit().putBoolean(KEY_SPACEBAR_TRACKPAD, value).apply()

    var longPressForAlt: Boolean
        get() = prefs.getBoolean(KEY_LONG_PRESS_ALT, true)
        set(value) = prefs.edit().putBoolean(KEY_LONG_PRESS_ALT, value).apply()

    var recentEmojis: List<String>
        get() {
            val raw = prefs.getString(KEY_RECENT_EMOJIS, "") ?: ""
            return if (raw.isBlank()) DEFAULT_EMOJIS else raw.split("|").filter { it.isNotBlank() }
        }
        set(value) {
            prefs.edit().putString(KEY_RECENT_EMOJIS, value.take(24).joinToString("|")).apply()
        }

    fun recordRecentEmoji(emoji: String) {
        val current = recentEmojis.filterNot { it == emoji }
        recentEmojis = listOf(emoji) + current
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        private const val PREFS_NAME = "mp02_keyboard_prefs"
        private const val KEY_FINISH = "finish"
        private const val KEY_HEIGHT_SCALE = "height_scale"
        private const val KEY_BOTTOM_CHIN_MM = "bottom_chin_mm"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_BACKLIGHT = "backlight"
        private const val KEY_AUTO_CAP = "auto_cap"
        private const val KEY_DOUBLE_SPACE_PERIOD = "double_space_period"
        private const val KEY_SPACEBAR_TRACKPAD = "spacebar_trackpad"
        private const val KEY_LONG_PRESS_ALT = "long_press_alt"
        private const val KEY_RECENT_EMOJIS = "recent_emojis"

        private val DEFAULT_EMOJIS = listOf(
            "👍", "😂", "❤️", "🙏", "🔥", "✨", "🎉", "👀",
            "😊", "🤔", "😅", "🙌", "💯", "✅", "🌱", "☕"
        )
    }
}
