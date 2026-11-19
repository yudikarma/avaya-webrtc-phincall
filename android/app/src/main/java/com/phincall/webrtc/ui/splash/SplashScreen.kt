package com.phincall.webrtc.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.delay

private const val SPLASH_DISPLAY_MS = 1400L

@Composable
fun SplashRoute(
    onSplashComplete: () -> Unit
) {
    val metadata = remember { AppMetadata.current() }
    var navigated by remember { mutableStateOf(false) }

    fun finishOnce() {
        if (!navigated) {
            navigated = true
            onSplashComplete()
        }
    }

    LaunchedEffect(Unit) {
        delay(SPLASH_DISPLAY_MS)
        finishOnce()
    }

    SplashScreen(
        metadata = metadata,
        onContinueNow = ::finishOnce
    )
}

@Composable
fun SplashScreen(
    metadata: AppMetadataInfo,
    onContinueNow: () -> Unit = {}
) {
    val entrance = remember { Animatable(0f) }
    val progress = remember { Animatable(0.18f) }

    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
        )
    }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
        )
    }

    val heroAlpha = entrance.value
    val heroScale = 0.95f + (0.05f * entrance.value)

    Surface(
        color = SurfaceLight,
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onContinueNow
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1.1f))

            // Minimalist centered brand focal point
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .alpha(heroAlpha)
                    .scale(heroScale)
            ) {
                Surface(
                    color = SurfaceLight,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, BorderHairline),
                    shadowElevation = 3.dp,
                    modifier = Modifier.size(92.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_phincall_brand),
                        contentDescription = "PhinCall logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = metadata.appName,
                    color = InkPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = metadata.appTagline,
                    color = InkSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                LinearProgressIndicator(
                    progress = { progress.value },
                    color = SlateNavy,
                    trackColor = SurfaceSubtle,
                    modifier = Modifier
                        .width(72.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(99.dp))
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Quiet, legible SDK and build metadata footer
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .alpha(heroAlpha)
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = "${metadata.formattedAppVersion}  \u2022  Avaya SDK ${metadata.avayaSdkVersion}",
                    color = SlateNavy,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "CSDK ${metadata.csdkVersion}  \u2022  ${metadata.formattedDeviceOs}  \u2022  ${metadata.formattedSdkRange}",
                    color = InkSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "${metadata.supportedPlatforms}  \u2022  ${metadata.buildType}",
                    color = InkSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SplashScreenPreview() {
    PhinCallTheme {
        SplashScreen(
            metadata = AppMetadata.create(
                appVersionName = "4.0.10",
                appVersionCode = 2,
                applicationId = "com.phincall.webrtc",
                buildTypeRaw = "release",
                androidRelease = "16",
                deviceApiLevel = 36
            )
        )
    }
}
