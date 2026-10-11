package com.example.phoneagent.tools.notify

internal interface Notifier {
    fun notify(
        title: String,
        message: String,
    )
}
