package com.example.phoneagent.ui.interaction

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.phoneagent.domain.interaction.Capability
import com.example.phoneagent.domain.interaction.CapabilityResult
import com.example.phoneagent.interaction.PendingInteraction
import com.example.phoneagent.interaction.UserInteractionBridge
import com.example.phoneagent.ui.designsystem.components.ApprovalSheet
import com.example.phoneagent.ui.designsystem.components.BatchApprovalSheet

/**
 * Root-level composable that answers whatever [UserInteractionBridge] is currently waiting on —
 * see architecture/adapters.md. Placed once, above navigation, so any screen's tool call can be
 * confirmed regardless of which screen is on top.
 */
@Composable
fun InteractionHost(bridge: UserInteractionBridge) {
    val pending by bridge.pending.collectAsStateWithLifecycle()

    DisposableEffect(bridge) {
        onDispose { bridge.resolvePendingAsDeclined() }
    }

    when (val current = pending) {
        is PendingInteraction.Approval -> {
            ApprovalSheet(
                request = current.request,
                arguments = current.request.arguments,
                onApprove = { current.deferred.complete(true) },
                onReject = { current.deferred.complete(false) },
            )
        }

        is PendingInteraction.BatchApproval -> {
            BatchApprovalSheet(
                requests = current.requests,
                onApprove = { current.deferred.complete(true) },
                onReject = { current.deferred.complete(false) },
            )
        }

        is PendingInteraction.Permission -> {
            PermissionRequest(capability = current.capability, onResult = current.deferred::complete)
        }

        null -> {
            Unit
        }
    }
}

@Composable
private fun PermissionRequest(
    capability: Capability,
    onResult: (CapabilityResult) -> Unit,
) {
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            currentOnResult(if (grants.values.any { it }) CapabilityResult.Available else CapabilityResult.Denied)
        }
    DisposableEffect(capability) {
        val permissions = capability.androidPermissions()
        if (permissions.isEmpty()) {
            // No runtime permission needed for this capability on this OS version.
            currentOnResult(CapabilityResult.Available)
        } else {
            launcher.launch(permissions.toTypedArray())
        }
        onDispose {}
    }
}

private fun Capability.androidPermissions(): List<String> =
    when (this) {
        Capability.NOTIFICATIONS -> {
            listOfNotNull(Manifest.permission.POST_NOTIFICATIONS.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU })
        }

        Capability.FILES -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                buildList {
                    add(Manifest.permission.READ_MEDIA_IMAGES)
                    add(Manifest.permission.READ_MEDIA_VIDEO)
                    add(Manifest.permission.READ_MEDIA_AUDIO)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                    }
                }
            } else {
                listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }
