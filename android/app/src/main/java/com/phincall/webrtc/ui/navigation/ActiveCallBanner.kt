package com.phincall.webrtc.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avaya.ocs.Services.Work.Enums.InteractionState
import com.phincall.webrtc.config.CallMediaMode
import com.phincall.webrtc.ui.theme.CrimsonAccent
import com.phincall.webrtc.webrtc.CallStateSnapshot
import java.util.Locale

private val WhatsAppCallGreen = Color(0xFF0E7142)
private val WhatsAppMuteIdleBg = Color(0x38000000)

internal fun shouldShowActiveCallStickyBanner(
    snapshot: CallStateSnapshot,
    isSplashRoute: Boolean
): Boolean = !isSplashRoute &&
    snapshot.isLocked &&
    !snapshot.isDiscardCompleted &&
    snapshot.isMinimizedInApp

internal fun formatActiveCallDuration(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/**
 * Slim WhatsApp-style top bar pinned above non-call screens after the user minimizes an ongoing call.
 */
@Composable
fun ActiveCallStickyBanner(
    snapshot: CallStateSnapshot,
    destination: String,
    onReturnToCall: () -> Unit,
    onToggleAudioMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val labelText = if (destination.isNotBlank()) {
        "Tap to return to call | $destination"
    } else {
        "Tap to return to call"
    }
    val statusText = when {
        snapshot.isHeldLocally || snapshot.isHeldRemotely -> "On hold"
        snapshot.interactionState == InteractionState.ESTABLISHED || snapshot.elapsedMillis > 0L ->
            formatActiveCallDuration(snapshot.elapsedMillis)
        else -> "Connecting"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = "Return to active call",
                onClick = onReturnToCall
            ),
        color = WhatsAppCallGreen,
        contentColor = Color.White,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    )
                    .defaultMinSize(minHeight = 44.dp)
                    .padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (snapshot.mediaMode == CallMediaMode.VIDEO) {
                        Icons.Rounded.Videocam
                    } else {
                        Icons.Rounded.Call
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )

                Text(
                    text = labelText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = Color.White,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.width(2.dp))

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(
                            role = Role.Switch,
                            onClickLabel = if (snapshot.isAudioMuted) "Unmute microphone" else "Mute microphone",
                            onClick = onToggleAudioMute
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (snapshot.isAudioMuted) CrimsonAccent else WhatsAppMuteIdleBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (snapshot.isAudioMuted) {
                                Icons.Rounded.MicOff
                            } else {
                                Icons.Rounded.Mic
                            },
                            contentDescription = if (snapshot.isAudioMuted) {
                                "Unmute microphone"
                            } else {
                                "Mute microphone"
                            },
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Color.White.copy(alpha = 0.16f))
            )
        }
    }
}
