package com.example.phoneagent.ui.chat

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.components.FileHit

internal fun openFileHit(
    context: Context,
    fileHit: FileHit,
) {
    if (!fileHit.hasContentUriScheme()) {
        Toast.makeText(context, R.string.file_open_failed, Toast.LENGTH_LONG).show()
        return
    }
    val viewIntent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(fileHit.uri), fileHit.mime ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    openInDefaultViewer(context, viewIntent)
}

/**
 * Opens the file in the default viewer app; only when there is none, falls back to the chooser,
 * and surfaces a readable failure when nothing can handle the intent.
 */
private fun openInDefaultViewer(
    context: Context,
    viewIntent: Intent,
) {
    try {
        context.startActivity(viewIntent)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(
                Intent.createChooser(
                    viewIntent,
                    context.getString(R.string.file_open_chooser_title),
                ),
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.file_open_failed, Toast.LENGTH_LONG).show()
        }
    } catch (_: SecurityException) {
        Toast.makeText(context, R.string.file_open_failed, Toast.LENGTH_LONG).show()
    }
}
