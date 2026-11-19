package com.phincall.webrtc.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.phincall.webrtc.config.CallMediaMode
import com.phincall.webrtc.ui.theme.CrimsonAccent
import com.phincall.webrtc.ui.theme.StatusGoodLight
import com.phincall.webrtc.webrtc.CallStateSnapshot
import java.util.Locale

private val BannerMutedEmerald = Color(0xFF0E5333)
private val BannerSubtitleTint = Color(0xFFE8F5EE)

internal fun shouldShowActiveCallStickyBanner(
    snapshot: CallStateSnapshot,
    isSplashRoute: Boolean
): Boolean = !isSplashRoute && snapshot.isLocked && !snapshot.isDiscardCompleted

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
 * Persistent header bar shown across non-call screens while an audio or video interaction stays live.
 */
@Composable
fun ActiveCallStickyBanner(
    snapshot: CallStateSnapshot,
    destination: String,
    onReturnToCall: () -> Unit,
    onToggleAudioMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val callLabel = if (snapshot.mediaMode == CallMediaMode.VIDEO) {
        "Active Video Call"
    } else {
        "Active Voice Call"
    }
    val titleText = if (destination.isNotBlank()) {
        "$callLabel | $destination"
    } else {
        callLabel
    }
    val holdSuffix = if (snapshot.isHeldLocally || snapshot.isHeldRemotely) " | HELD" else ""
    val subtitleText = buildString {
        append(formatActiveCallDuration(snapshot.elapsedMillis))
        append(" | ")
        append(snapshot.interactionState.name)
        append(" | ")
        append(snapshot.platformType.name)
        append(holdSuffix)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = "Return to active call",
                onClick = onReturnToCall
            ),
        color = StatusGoodLight,
        contentColor = Color.White,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                )
                .defaultMinSize(minHeight = 52.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.16f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (snapshot.mediaMode == CallMediaMode.VIDEO) {
                            Icons.Rounded.Videocam
                        } else {
                            Icons.Rounded.Call
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = BannerSubtitleTint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(
                            role = Role.Switch,
                            onClickLabel = if (snapshot.isAudioMuted) "Unmute microphone" else "Mute microphone",
                            onClick = onToggleAudioMute
                        ),
                    shape = CircleShape,
                    color = if (snapshot.isAudioMuted) CrimsonAccent else BannerMutedEmerald
                ) {
                    Box(contentAlignment = Alignment.Center) {
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
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White
                ) {
                    Text(
                        text = "Return",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = BannerMutedEmerald,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}
