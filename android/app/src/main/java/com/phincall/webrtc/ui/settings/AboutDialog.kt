package com.phincall.webrtc.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.phincall.webrtc.R
import com.phincall.webrtc.config.AppMetadata
import com.phincall.webrtc.config.AppMetadataInfo
import com.phincall.webrtc.ui.theme.BorderHairline
import com.phincall.webrtc.ui.theme.InkPrimary
import com.phincall.webrtc.ui.theme.InkSecondary
import com.phincall.webrtc.ui.theme.PhinCallTheme
import com.phincall.webrtc.ui.theme.SlateNavy
import com.phincall.webrtc.ui.theme.SurfaceLight
import com.phincall.webrtc.ui.theme.SurfaceSubtle

@Composable
fun AboutDialog(
    metadata: AppMetadataInfo,
    activeProfileLabel: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = SurfaceLight,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, BorderHairline),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderHairline),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_phincall_brand),
                            contentDescription = "PhinCall icon",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "About ${metadata.appName}",
                            color = InkPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = metadata.appTagline,
                            color = InkSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Surface(
                    color = SurfaceSubtle,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderHairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        AboutSpecRow(label = "App Version", value = metadata.formattedAppVersion)
                        AboutSpecDivider()
                        AboutSpecRow(
                            label = "Build & Package",
                            value = "${metadata.buildType} | ${metadata.applicationId}"
                        )
                        AboutSpecDivider()
                        AboutSpecRow(label = "Avaya WebRTC SDK", value = metadata.formattedAvayaSdk)
                        AboutSpecDivider()
                        AboutSpecRow(label = "Active Profile", value = activeProfileLabel)
                        AboutSpecDivider()
                        AboutSpecRow(label = "Device OS", value = metadata.formattedDeviceOs)
                        AboutSpecDivider()
                        AboutSpecRow(label = "Android SDK", value = metadata.formattedSdkRange)
                        AboutSpecDivider()
                        AboutSpecRow(label = "Security & ABI", value = metadata.securitySummary)
                    }
                }

                Text(
                    text = "Stack: ${metadata.architectureSummary}.",
                    color = InkSecondary,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    lineHeight = 17.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val summary = metadata.formatDiagnosticsSummary(activeProfileLabel)
                            clipboard?.setPrimaryClip(
                                ClipData.newPlainText("PhinCall Version Info", summary)
                            )
                            Toast.makeText(
                                context,
                                "App and SDK info copied",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text(
                            text = "Copy Info",
                            color = SlateNavy,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SlateNavy,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text(
                            text = "Close",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSpecRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = InkSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            color = InkPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
private fun AboutSpecDivider() {
    HorizontalDivider(thickness = 1.dp, color = BorderHairline)
}

@Preview(showBackground = true)
@Composable
private fun AboutDialogPreview() {
    PhinCallTheme {
        AboutDialog(
            metadata = AppMetadata.create(
                appVersionName = "4.0.10",
                appVersionCode = 2,
                applicationId = "com.phincall.webrtc",
                buildTypeRaw = "release",
                androidRelease = "16",
                deviceApiLevel = 36
            ),
            activeProfileLabel = "Avaya Oceana",
            onDismiss = {}
        )
    }
}
