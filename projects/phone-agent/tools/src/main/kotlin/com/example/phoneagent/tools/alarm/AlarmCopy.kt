package com.example.phoneagent.tools.alarm

/**
 * All user-facing text for `set_alarm` in one place, kept out of the tool's logic files. Values
 * are unchanged from where they used to live inline — this is purely a relocation so a future
 * localization pass has one file to change per tool instead of a hunt through the codebase.
 */
internal object AlarmCopy {
    const val CARD_TITLE: String = "Поставить будильник"
    const val CARD_SUMMARY_INVALID_ARGS: String = "С неверными параметрами"
    const val RESULT_INVALID_ARGS: String = "Неверные параметры будильника"
    const val RESULT_SUCCESS: String = "Запрос на будильник отправлен"
    const val RESULT_NO_CLOCK_APP: String = "Не найдено приложение «Часы»"
    const val RESULT_FAILED: String = "Не удалось отправить запрос на будильник"
    const val VALIDATION_HOUR_RANGE: String = "Час должен быть от 0 до 23"
    const val VALIDATION_MINUTE_RANGE: String = "Минуты должны быть от 0 до 59"

    fun cardSummary(
        hour: Int,
        minute: Int,
        label: String?,
    ): String {
        val time = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
        val suffix = label?.let { ". Название: $it" }.orEmpty()
        return "На $time$suffix"
    }
}
