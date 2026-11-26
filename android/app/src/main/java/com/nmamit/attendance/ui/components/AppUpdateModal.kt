package com.nmamit.attendance.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmamit.attendance.data.remote.AppUpdateInfo
import com.nmamit.attendance.ui.theme.*

@Composable
fun AppUpdateModal(
    updateInfo: AppUpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current

    fun openUrl(url: String) {
        val result = runCatching { uriHandler.openUri(url) }
        if (result.isFailure) {
            Toast.makeText(context, "Could not open browser. Link copied instead.", Toast.LENGTH_SHORT).show()
            clipboard.setText(AnnotatedString(url))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(horizontal = 16.dp, vertical = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        LiquidGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* consume clicks inside modal */ },
            cornerRadius = 24.dp,
            tintColor = AuthCardTint,
            glassAlpha = 0.78f,
            blurRadius = 28.dp,
            enableBlur = true,
            contentPadding = PaddingValues(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(EmeraldBg)
                            .border(1.dp, EmeraldBorder, RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = Emerald,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Column {
                        SectionTag(text = "// UPDATE AVAILABLE")
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "New Version Available",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.3).sp,
                            color = Foreground
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .bouncyClickable(onClick = onDismiss)
                        .clip(CircleShape)
                        .background(GlassSubCardBg)
                        .border(1.dp, GlassBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = ForegroundMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Version Pill Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(GlassSubCardBg)
                        .border(1.dp, GlassBorder, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "v${updateInfo.currentVersion}",
                        fontSize = 11.sp,
                        fontFamily = AppMonoFont,
                        color = ForegroundMuted
                    )
                }
                Text(
                    text = "→",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ForegroundMuted
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(EmeraldBg)
                        .border(1.dp, EmeraldBorder, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = updateInfo.tagName,
                        fontSize = 11.sp,
                        fontFamily = AppMonoFont,
                        fontWeight = FontWeight.Bold,
                        color = Emerald
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "A newer release of NMAMIT Attendance Tracker is available on GitHub. Download the latest APK to stay up to date.",
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = ForegroundMuted
            )

            if (updateInfo.releaseNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                GlassSubCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 14.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 130.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "WHAT'S NEW",
                            fontSize = 9.5.sp,
                            fontFamily = AppMonoFont,
                            fontWeight = FontWeight.Bold,
                            color = ForegroundMuted
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = updateInfo.releaseNotes,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = Foreground.copy(alpha = 0.88f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Visible GitHub Link Box
            GlassSubCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .bouncyClickable(pressedScale = 0.98f) { openUrl(updateInfo.releaseUrl) },
                cornerRadius = 14.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "GITHUB RELEASE LINK",
                            fontSize = 9.sp,
                            fontFamily = AppMonoFont,
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = updateInfo.releaseUrl,
                            fontSize = 11.sp,
                            fontFamily = AppMonoFont,
                            color = Foreground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .bouncyClickable {
                                clipboard.setText(AnnotatedString(updateInfo.releaseUrl))
                                Toast.makeText(context, "GitHub link copied!", Toast.LENGTH_SHORT).show()
                            }
                            .clip(RoundedCornerShape(8.dp))
                            .background(GlassInputBg)
                            .border(1.dp, GlassBorder, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy GitHub link",
                            tint = ForegroundMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary CTA: Download Latest APK
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .bouncyClickable { openUrl(updateInfo.apkDownloadUrl) }
                    .clip(RoundedCornerShape(14.dp))
                    .background(Foreground)
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = BgCanvas,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = "Download Latest APK (${updateInfo.tagName})",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = BgCanvas
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Secondary Row: Open GitHub Page + Later
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .bouncyClickable { openUrl(updateInfo.releaseUrl) }
                        .clip(RoundedCornerShape(13.dp))
                        .background(GlassSubCardBg)
                        .border(1.dp, GlassBorder, RoundedCornerShape(13.dp))
                        .padding(vertical = 11.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = Foreground,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Open GitHub",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Foreground
                    )
                }

                Box(
                    modifier = Modifier
                        .bouncyClickable(onClick = onDismiss)
                        .clip(RoundedCornerShape(13.dp))
                        .background(GlassSubCardBg)
                        .border(1.dp, GlassBorder, RoundedCornerShape(13.dp))
                        .padding(horizontal = 20.dp, vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Later",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ForegroundMuted
                    )
                }
            }
        }
    }
}
