package com.phincall.webrtc.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avaya.ocs.Services.Work.Enums.PlatformType
import com.phincall.webrtc.config.CallMediaMode
import com.phincall.webrtc.config.InputValidator
import com.phincall.webrtc.ui.theme.BorderHairline
import com.phincall.webrtc.ui.theme.CrimsonAccent
import com.phincall.webrtc.ui.theme.InkPrimary
import com.phincall.webrtc.ui.theme.InkSecondary
import com.phincall.webrtc.ui.theme.SlateNavy
import com.phincall.webrtc.ui.theme.StatusPoorLight
import com.phincall.webrtc.ui.theme.SurfaceLight
import com.phincall.webrtc.ui.theme.SurfaceSubtle
import com.phincall.webrtc.ui.theme.TabularMonospaceStyle

private data class DialKey(
    val digit: String,
    val letters: String = "",
    val longPressValue: String? = null
)

private val DIALPAD_ROWS = listOf(
    listOf(DialKey("1"), DialKey("2", "ABC"), DialKey("3", "DEF")),
    listOf(DialKey("4", "GHI"), DialKey("5", "JKL"), DialKey("6", "MNO")),
    listOf(DialKey("7", "PQRS"), DialKey("8", "TUV"), DialKey("9", "WXYZ")),
    listOf(DialKey("*"), DialKey("0", "+", longPressValue = "+"), DialKey("#"))
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DialpadBottomSheet(
    platformType: PlatformType,
    initialNumber: String,
    onTogglePlatform: () -> Unit,
    onDismiss: () -> Unit,
    onDialCall: (destinationNumber: String, mediaMode: CallMediaMode) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var dialedNumber by rememberSaveable(initialNumber) { mutableStateOf(initialNumber) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current

    fun append(value: String) {
        if (dialedNumber.length < 64) {
            dialedNumber += value
            validationError = null
        }
    }

    fun submit(mode: CallMediaMode) {
        val trimmed = dialedNumber.trim()
        if (platformType == PlatformType.ELITE && trimmed.isBlank()) {
            validationError = "Enter a destination number for Avaya Aura Elite."
            return
        }
        if (!InputValidator.isValidSipUser(trimmed)) {
            validationError = "Destination contains disallowed SIP characters."
            return
        }
        onDialCall(trimmed, mode)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceLight,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (platformType == PlatformType.ELITE) {
                        "Routing via Avaya Aura Elite"
                    } else {
                        "Routing via Avaya Oceana"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = InkSecondary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        onTogglePlatform()
                        validationError = null
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        tint = SlateNavy,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Switch", color = SlateNavy)
                }
            }

            Spacer(Modifier.height(6.dp))

            // Number display box with tap-delete and long-press clear
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceLight)
                    .border(1.dp, BorderHairline, RoundedCornerShape(12.dp))
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dialedNumber.ifEmpty { "Destination number" },
                    style = TabularMonospaceStyle.copy(fontSize = 23.sp),
                    color = if (dialedNumber.isEmpty()) InkSecondary else InkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .semantics {
                            contentDescription = "Delete last digit; hold to clear"
                        }
                        .combinedClickable(
                            role = Role.Button,
                            onClick = {
                                if (dialedNumber.isNotEmpty()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    dialedNumber = dialedNumber.dropLast(1)
                                    validationError = null
                                }
                            },
                            onLongClick = {
                                if (dialedNumber.isNotEmpty()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dialedNumber = ""
                                    validationError = null
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Backspace,
                        contentDescription = null,
                        tint = InkSecondary
                    )
                }
            }

            if (!validationError.isNullOrBlank()) {
                Text(
                    text = validationError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusPoorLight,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DIALPAD_ROWS.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { key ->
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .combinedClickable(
                                        role = Role.Button,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            append(key.digit)
                                        },
                                        onLongClick = key.longPressValue?.let { alt ->
                                            {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                append(alt)
                                            }
                                        }
                                    ),
                                color = SurfaceSubtle,
                                border = BorderStroke(1.dp, BorderHairline),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = key.digit,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = InkPrimary
                                    )
                                    if (key.letters.isNotEmpty()) {
                                        Text(
                                            text = key.letters,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = InkSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { submit(CallMediaMode.AUDIO) },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SlateNavy,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Voice Call", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { submit(CallMediaMode.VIDEO) },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonAccent,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Videocam,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Video Call", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
