package com.tishan.app.data.settings

import android.content.Context
import com.tishan.app.domain.practice.ExamDurationSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: Float = 1f,
    val singleChoiceSeconds: Int = 30 * 60,
    val multipleChoiceSeconds: Int = 30 * 60,
    val trueFalseSeconds: Int = 15 * 60,
) {
    val examDuration: ExamDurationSettings
        get() = ExamDurationSettings(singleChoiceSeconds, multipleChoiceSeconds, trueFalseSeconds)
}

@Serializable
data class SettingsSnapshot(
    val themeMode: String,
    val fontScale: Float,
    val singleChoiceSeconds: Int,
    val multipleChoiceSeconds: Int,
    val trueFalseSeconds: Int,
)

/** Persists appearance and default exam settings without requiring a schema migration. */
class SettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings): AppSettings {
        val next = transform(_state.value).normalized()
        preferences.edit()
            .putString(KEY_THEME, next.themeMode.name)
            .putFloat(KEY_FONT_SCALE, next.fontScale)
            .putInt(KEY_SINGLE, next.singleChoiceSeconds)
            .putInt(KEY_MULTIPLE, next.multipleChoiceSeconds)
            .putInt(KEY_TRUE_FALSE, next.trueFalseSeconds)
            .apply()
        _state.value = next
        return next
    }

    fun defaultExamDuration(): ExamDurationSettings = _state.value.examDuration

    fun snapshot(): SettingsSnapshot = _state.value.toSnapshot()

    fun restore(snapshot: SettingsSnapshot?) {
        if (snapshot == null) {
            update { AppSettings() }
        } else {
            update {
                AppSettings(
                    themeMode = runCatching { ThemeMode.valueOf(snapshot.themeMode) }.getOrDefault(ThemeMode.SYSTEM),
                    fontScale = snapshot.fontScale,
                    singleChoiceSeconds = snapshot.singleChoiceSeconds,
                    multipleChoiceSeconds = snapshot.multipleChoiceSeconds,
                    trueFalseSeconds = snapshot.trueFalseSeconds,
                )
            }
        }
    }

    private fun read(): AppSettings = AppSettings(
        themeMode = runCatching { ThemeMode.valueOf(preferences.getString(KEY_THEME, null).orEmpty()) }
            .getOrDefault(ThemeMode.SYSTEM),
        fontScale = preferences.getFloat(KEY_FONT_SCALE, 1f),
        singleChoiceSeconds = preferences.getInt(KEY_SINGLE, 30 * 60),
        multipleChoiceSeconds = preferences.getInt(KEY_MULTIPLE, 30 * 60),
        trueFalseSeconds = preferences.getInt(KEY_TRUE_FALSE, 15 * 60),
    ).normalized()

    private fun AppSettings.toSnapshot() = SettingsSnapshot(
        themeMode = themeMode.name,
        fontScale = fontScale,
        singleChoiceSeconds = singleChoiceSeconds,
        multipleChoiceSeconds = multipleChoiceSeconds,
        trueFalseSeconds = trueFalseSeconds,
    )

    private fun AppSettings.normalized() = copy(
        fontScale = fontScale.coerceIn(0.85f, 1.3f),
        singleChoiceSeconds = singleChoiceSeconds.coerceIn(0, 24 * 60 * 60),
        multipleChoiceSeconds = multipleChoiceSeconds.coerceIn(0, 24 * 60 * 60),
        trueFalseSeconds = trueFalseSeconds.coerceIn(0, 24 * 60 * 60),
    )

    private companion object {
        const val PREFERENCES = "tishan_settings"
        const val KEY_THEME = "theme_mode"
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_SINGLE = "single_choice_seconds"
        const val KEY_MULTIPLE = "multiple_choice_seconds"
        const val KEY_TRUE_FALSE = "true_false_seconds"
    }
}
