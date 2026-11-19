package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.material.icons.rounded.Wifi
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.transfer.FileCategory
import com.example.transfer.IncomingTransferOffer
import com.example.transfer.LocalNetworkUtils
import com.example.transfer.NearbyPeer
import com.example.transfer.PeerTransport
import com.example.transfer.TransferSessionState
import kotlin.math.cos
import kotlin.math.sin
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
    modifier: Modifier = Modifier,
    peers: List<NearbyPeer> = emptyList(),
    selectedPeerId: String? = null,
    onPeerClick: ((NearbyPeer) -> Unit)? = null
) {
    val transition = rememberInfiniteTransition(label = "radar_pulse")
    val pulse1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse1"
    )
    val pulse2 by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse2"
    )
    val sweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_sweep"
    )

    val avatarColor = AvatarSwatches.getOrElse(avatarColorIndex) { CobaltPrimary }
    val visiblePeers = remember(peers) { peers.take(6) }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val maxRadiusDp = (minOf(maxWidth, maxHeight) / 2f) * 0.90f

        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = (minOf(size.width, size.height) / 2f) * 0.90f
            val baseRadius = maxRadius * 0.28f

            // Clear radar field backdrop
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.14f),
                        accentColor.copy(alpha = 0.05f),
                        accentColor.copy(alpha = 0.02f)
                    ),
                    center = center,
                    radius = maxRadius
                ),
                radius = maxRadius,
                center = center
            )

            // Crosshair axis lines
            drawLine(
                color = accentColor.copy(alpha = 0.12f),
                start = Offset(center.x - maxRadius, center.y),
                end = Offset(center.x + maxRadius, center.y),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = accentColor.copy(alpha = 0.12f),
                start = Offset(center.x, center.y - maxRadius),
                end = Offset(center.x, center.y + maxRadius),
                strokeWidth = 1.dp.toPx()
            )

            // Crisp concentric range rings
            listOf(0.36f, 0.68f, 1.0f).forEachIndexed { idx, factor ->
                drawCircle(
                    color = accentColor.copy(alpha = if (idx == 2) 0.28f else 0.18f),
                    radius = maxRadius * factor,
                    center = center,
                    style = Stroke(width = if (idx == 2) 1.8.dp.toPx() else 1.2.dp.toPx())
                )
            }

            if (isActive) {
                // Rotating radar sweep beam
                rotate(degrees = sweepAngle, pivot = center) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0.0f to Color.Transparent,
                            0.75f to Color.Transparent,
                            0.94f to accentColor.copy(alpha = 0.14f),
                            1.0f to accentColor.copy(alpha = 0.34f),
                            center = center
                        ),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = true,
                        topLeft = Offset(center.x - maxRadius, center.y - maxRadius),
                        size = Size(maxRadius * 2f, maxRadius * 2f)
                    )
                }

                // Expanding pulse waves
                listOf(pulse1, pulse2 % 1f).forEach { progress ->
                    val radius = baseRadius + (maxRadius - baseRadius) * progress
                    val alpha = (1f - progress).coerceIn(0f, 1f) * 0.45f
                    drawCircle(
                        color = accentColor.copy(alpha = alpha),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        // Center self device badge
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
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
                    modifier = Modifier.size(24.dp)
                )
            }
            Surface(
                shape = RoundedCornerShape(99.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 2.dp
            ) {
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = 96.dp)
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }

        // Pop-up visible phones inside radar range
        val angles = radarAnglesForCount(visiblePeers.size)
        visiblePeers.forEachIndexed { index, peer ->
            val angleDeg = angles[index]
            val orbitFactor = if (visiblePeers.size > 3 && index % 2 == 1) 0.74f else 0.66f
            val orbitRadius = maxRadiusDp * orbitFactor
            val rad = Math.toRadians(angleDeg.toDouble())
            val offsetX = (orbitRadius.value * cos(rad)).dp
            val offsetY = (orbitRadius.value * sin(rad)).dp

            RadarPeerNode(
                peer = peer,
                selected = selectedPeerId == peer.id,
                pulseProgress = pulse1,
                onClick = { onPeerClick?.invoke(peer) },
                modifier = Modifier.offset(x = offsetX, y = offsetY)
            )
        }
    }
}

private fun radarAnglesForCount(count: Int): List<Float> = when (count) {
    0 -> emptyList()
    1 -> listOf(-48f)
    2 -> listOf(-134f, -46f)
    3 -> listOf(-145f, -35f, 16f)
    4 -> listOf(-148f, -32f, 18f, 162f)
    5 -> listOf(-152f, -90f, -28f, 20f, 160f)
    else -> listOf(-155f, -108f, -55f, -10f, 24f, 158f)
}

@Composable
private fun RadarPeerNode(
    peer: NearbyPeer,
    selected: Boolean,
    pulseProgress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val popScale = remember(peer.id) { Animatable(0.25f) }
    LaunchedEffect(peer.id) {
        popScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    val transportColor = when {
        selected -> MintSuccess
        peer.transport == PeerTransport.WIFI_DIRECT -> CobaltPrimary
        peer.transport == PeerTransport.LOCAL_WIFI -> CyanSecondary
        else -> VioletTertiary
    }
    val transportShort = when (peer.transport) {
        PeerTransport.WIFI_DIRECT -> "Direct"
        PeerTransport.LOCAL_WIFI -> "Wi-Fi"
        PeerTransport.BLUETOOTH -> "BT"
    }

    Column(
        modifier = modifier
            .scale(popScale.value)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(4.dp)
            .testTag("radar_peer_${peer.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(54.dp)
        ) {
            // Subtle pop-up beacon halo around clickable receiver phone
            Box(
                modifier = Modifier
                    .size((46 + (pulseProgress * 8f)).dp)
                    .clip(CircleShape)
                    .background(transportColor.copy(alpha = (1f - pulseProgress) * 0.28f))
            )
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = transportColor,
                shadowElevation = 6.dp,
                border = BorderStroke(2.dp, Color.White)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (selected) Icons.Rounded.Check else Icons.Rounded.PhoneAndroid,
                        contentDescription = "Select ${peer.name}",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            // Small transport indicator dot
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(18.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.5.dp, transportColor)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (peer.transport) {
                            PeerTransport.BLUETOOTH -> Icons.Rounded.Bluetooth
                            else -> Icons.Rounded.Wifi
                        },
                        contentDescription = null,
                        tint = transportColor,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
            border = BorderStroke(1.5.dp, transportColor.copy(alpha = 0.85f))
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 64.dp, max = 112.dp)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = peer.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (selected) "Selected" else "$transportShort • Tap",
                    style = MaterialTheme.typography.labelSmall,
                    color = transportColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
