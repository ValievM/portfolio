package com.example.phoneagent.ui.chat

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.components.LinkDecision
import com.example.phoneagent.ui.designsystem.components.evaluateLink

/**
 * Opens a link the user has already confirmed. The policy is checked again here, so nothing but a
 * well-formed https link can ever reach [Intent.ACTION_VIEW], whatever calls this.
 */
internal fun openLink(
    context: Context,
    url: String,
) {
    val decision = evaluateLink(url)
    if (decision !is LinkDecision.Confirm) {
        Toast.makeText(context, R.string.link_blocked_title, Toast.LENGTH_LONG).show()
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(decision.url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.link_open_failed, Toast.LENGTH_LONG).show()
    }
}
