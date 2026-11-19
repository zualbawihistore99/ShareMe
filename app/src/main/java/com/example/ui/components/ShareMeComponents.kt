package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.transfer.FileCategory
import com.example.transfer.IncomingTransferOffer
import com.example.transfer.LocalNetworkUtils
import com.example.transfer.TransferSessionState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AvatarSwatches
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MintSuccess
import com.example.ui.theme.VioletTertiary

@Composable
fun RadarPulseCanvas(
    isActive: Boolean,
    accentColor: Color,
    deviceName: String,
    avatarColorIndex: Int,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "radar_pulse")
    val pulse1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse1"
    )
    val pulse2 by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse2"
    )

    val avatarColor = AvatarSwatches.getOrElse(avatarColorIndex) { CobaltPrimary }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = (minOf(size.width, size.height) / 2f) * 0.92f
            val baseRadius = maxRadius * 0.34f

            // Subtle static guide rings
            drawCircle(
                color = accentColor.copy(alpha = 0.14f),
                radius = maxRadius * 0.65f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(
                color = accentColor.copy(alpha = 0.08f),
                radius = maxRadius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            if (isActive) {
                listOf(pulse1, pulse2 % 1f).forEach { progress ->
                    val radius = baseRadius + (maxRadius - baseRadius) * progress
                    val alpha = (1f - progress).coerceIn(0f, 1f) * 0.45f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = alpha * 0.25f),
                                accentColor.copy(alpha = alpha)
                            ),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(avatarColor, accentColor)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhoneAndroid,
                    contentDescription = "This phone",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
            Surface(
                shape = RoundedCornerShape(99.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = 2.dp
            ) {
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun FileCategoryBadge(
    category: FileCategory,
    modifier: Modifier = Modifier
) {
    val (icon, tint) = when (category) {
        FileCategory.PHOTO -> Icons.Rounded.Image to CobaltPrimary
        FileCategory.VIDEO -> Icons.Rounded.Movie to VioletTertiary
        FileCategory.AUDIO -> Icons.Rounded.AudioFile to CyanSecondary
        FileCategory.DOCUMENT -> Icons.Rounded.Description to AmberWarning
        FileCategory.OTHER -> Icons.Rounded.InsertDriveFile to MintSuccess
    }

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = category.name,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun PermissionBannerCard(
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.WifiFind,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.permission_nearby_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.permission_nearby_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onGrantClick,
                    modifier = Modifier.testTag("grant_discovery_permission_button")
                ) {
                    Text(stringResource(R.string.permission_grant))
                }
            }
        }
    }
}

@Composable
fun TransferProgressCard(
    sessionState: TransferSessionState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onOpenFile: (String) -> Unit,
    onShareFile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (sessionState is TransferSessionState.Idle) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (sessionState) {
                is TransferSessionState.Connecting -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Connecting to ${sessionState.peerName}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = sessionState.statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = onCancel,
                            modifier = Modifier.testTag("cancel_transfer_button")
                        ) {
                            Text("Cancel")
                        }
                    }
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                    )
                }

                is TransferSessionState.WaitingApproval -> {
                    Text(
                        text = "Waiting for ${sessionState.peerName}...",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Offering ${sessionState.fileCount} file(s) (${LocalNetworkUtils.formatBytes(sessionState.totalBytes)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = CyanSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onCancel,
                            modifier = Modifier.testTag("cancel_waiting_button")
                        ) {
                            Text("Cancel")
                        }
                    }
                }

                is TransferSessionState.Transferring -> {
                    val directionLabel = if (sessionState.isSending) "Sending to" else "Receiving from"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$directionLabel ${sessionState.peerName}",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "File ${sessionState.currentFileIndex}/${sessionState.totalFiles}: ${sessionState.currentFileName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = LocalNetworkUtils.formatSpeed(sessionState.speedBytesPerSec),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { sessionState.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = if (sessionState.isSending) CobaltPrimary else CyanSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${LocalNetworkUtils.formatBytes(sessionState.bytesTransferred)} / ${LocalNetworkUtils.formatBytes(sessionState.totalBytes)}",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "ETA ${LocalNetworkUtils.formatEta(sessionState.etaSeconds)}",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.testTag("abort_transfer_button")
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop Transfer")
                        }
                    }
                }

                is TransferSessionState.Completed -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Transfer complete",
                            tint = MintSuccess,
                            modifier = Modifier.size(32.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (sessionState.isSending) "Sent Successfully!" else "Received Successfully!",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${sessionState.fileCount} file(s) • ${LocalNetworkUtils.formatBytes(sessionState.totalBytes)} at ${LocalNetworkUtils.formatSpeed(sessionState.averageSpeedBytesPerSec)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        if (!sessionState.isSending && !sessionState.lastSavedUri.isNullOrBlank()) {
                            FilledTonalButton(
                                onClick = { onOpenFile(sessionState.lastSavedUri) },
                                modifier = Modifier.testTag("open_received_file_button")
                            ) {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open")
                            }
                            FilledTonalButton(
                                onClick = { onShareFile(sessionState.lastSavedUri) },
                                modifier = Modifier.testTag("share_received_file_button")
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share")
                            }
                        }
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("transfer_done_button")
                        ) {
                            Text("Done")
                        }
                    }
                }

                is TransferSessionState.Error -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = "Transfer error",
                            tint = CoralError,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Transfer Interrupted",
                                style = MaterialTheme.typography.titleMedium,
                                color = CoralError
                            )
                            Text(
                                text = sessionState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = CoralError),
                            modifier = Modifier.testTag("dismiss_error_button")
                        ) {
                            Text("Dismiss")
                        }
                    }
                }

                TransferSessionState.Idle -> Unit
            }
        }
    }
}

@Composable
fun IncomingTransferDialog(
    offer: IncomingTransferOffer,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDecline,
        icon = {
            Icon(
                imageVector = Icons.Rounded.PhoneAndroid,
                contentDescription = null,
                tint = CyanSecondary
            )
        },
        title = {
            Text(
                text = "Incoming via ${offer.transportLabel}",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "From ${offer.senderName} • ${offer.fileCount} file(s) (${LocalNetworkUtils.formatBytes(offer.totalBytes)})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Starting with: ${offer.previewFileName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                modifier = Modifier.testTag("accept_incoming_button")
            ) {
                Text(stringResource(R.string.receive_accept))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDecline,
                modifier = Modifier.testTag("decline_incoming_button")
            ) {
                Text(stringResource(R.string.receive_decline))
            }
        }
    )
}
