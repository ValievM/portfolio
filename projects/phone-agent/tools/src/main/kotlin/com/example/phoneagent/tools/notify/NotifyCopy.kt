package com.example.phoneagent.tools.notify

internal object NotifyCopy {
    const val MODEL_DESCRIPTION: String = "Show a notification on the device."
    const val CARD_TITLE: String = "Показать уведомление"
    const val CHANNEL_NAME: String = "Уведомления Phone Agent"
    const val INVALID_ARGUMENTS: String = "Неверные параметры уведомления"
    const val PERMISSION_DENIED: String = "Разрешение на уведомления не выдано"
    const val NOTIFICATION_SHOWN: String = "Уведомление показано"

    fun cardSummary(args: NotifyArgs): String = "${args.title}: ${args.message}"

    fun validationError(error: NotifyValidationError): String =
        when (error) {
            NotifyValidationError.EMPTY_TITLE -> "Заголовок уведомления не должен быть пустым"
            NotifyValidationError.EMPTY_MESSAGE -> "Текст уведомления не должен быть пустым"
            NotifyValidationError.TITLE_TOO_LONG -> "Заголовок уведомления не длиннее $MAX_TITLE_LENGTH символов"
            NotifyValidationError.MESSAGE_TOO_LONG -> "Текст уведомления не длиннее $MAX_MESSAGE_LENGTH символов"
        }
}
