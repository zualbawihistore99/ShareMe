package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.TransferDirection
import com.example.data.local.TransferRecordEntity
import com.example.transfer.FileMetadataResolver
import com.example.transfer.FileSaver
import com.example.transfer.LocalNetworkUtils
import com.example.ui.components.FileCategoryBadge
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MintSuccess
import java.text.DateFormat
import java.util.Date

private enum class HistoryFilter { ALL, RECEIVED, SENT }

@Composable
fun HistoryScreen(
    transfers: List<TransferRecordEntity>,
    onDeleteRecord: (Long) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(HistoryFilter.ALL) }
    var showClearDialog by remember { mutableStateOf(false) }

    val filteredTransfers = remember(transfers, searchQuery, activeFilter) {
        val query = searchQuery.trim().lowercase()
        transfers.filter { item ->
            val matchesFilter = when (activeFilter) {
                HistoryFilter.ALL -> true
                HistoryFilter.RECEIVED -> item.direction == TransferDirection.RECEIVED.name
                HistoryFilter.SENT -> item.direction == TransferDirection.SENT.name
            }
            val matchesQuery = query.isEmpty() ||
                item.fileName.lowercase().contains(query) ||
                item.peerName.lowercase().contains(query)
            matchesFilter && matchesQuery
        }
    }

    val totalVolumeBytes = remember(transfers) { transfers.sumOf { it.fileSizeBytes } }
    val avgSpeedBytes = remember(transfers) {
        if (transfers.isEmpty()) 0L else transfers.map { it.averageSpeedBytesPerSec }.average().toLong()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.history_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    if (transfers.isNotEmpty()) {
                        TextButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.testTag("clear_history_button")
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear All")
                        }
                    }
                }
            }

            // Summary Metrics Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatMetric(label = "Files", value = transfers.size.toString())
                        StatMetric(label = "Total Data", value = LocalNetworkUtils.formatBytes(totalVolumeBytes))
                        StatMetric(label = "Avg Speed", value = LocalNetworkUtils.formatSpeed(avgSpeedBytes))
                    }
                }
            }

            // Search & Filter
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.history_search_hint)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_search_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = activeFilter == HistoryFilter.ALL,
                            onClick = { activeFilter = HistoryFilter.ALL },
                            label = { Text(stringResource(R.string.history_filter_all)) },
                            modifier = Modifier.testTag("filter_all_chip")
                        )
                        FilterChip(
                            selected = activeFilter == HistoryFilter.RECEIVED,
                            onClick = { activeFilter = HistoryFilter.RECEIVED },
                            label = { Text(stringResource(R.string.history_filter_received)) },
                            modifier = Modifier.testTag("filter_received_chip")
                        )
                        FilterChip(
                            selected = activeFilter == HistoryFilter.SENT,
                            onClick = { activeFilter = HistoryFilter.SENT },
                            label = { Text(stringResource(R.string.history_filter_sent)) },
                            modifier = Modifier.testTag("filter_sent_chip")
                        )
                    }
                }
            }

            if (filteredTransfers.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = if (transfers.isEmpty()) "No transfer logs yet" else "No matching transfers",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Files you send or receive appear here with speed and device details.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredTransfers, key = { it.id }) { record ->
                    HistoryTransferCard(
                        record = record,
                        onOpen = {
                            val uri = record.localUri
                            if (uri.isNullOrBlank() || !FileSaver.openFile(context, uri, record.mimeType)) {
                                Toast.makeText(context, "Unable to open file", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onShare = {
                            val uri = record.localUri
                            if (uri.isNullOrBlank() || !FileSaver.shareFileViaChooser(context, uri, record.mimeType)) {
                                Toast.makeText(context, "Unable to share file", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = { onDeleteRecord(record.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear transfer history?") },
            text = { Text("This removes transfer logs from ShareMe. Saved files on your phone stay intact.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        onClearAll()
                    },
                    modifier = Modifier.testTag("confirm_clear_history_button")
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun HistoryTransferCard(
    record: TransferRecordEntity,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val isSent = record.direction == TransferDirection.SENT.name
    val category = FileMetadataResolver.categorize(record.mimeType, record.fileName)
    val dateLabel = remember(record.timestampMillis) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(record.timestampMillis))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FileCategoryBadge(category = category)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${if (isSent) "Sent to" else "From"} ${record.peerName} • $dateLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = (if (isSent) CobaltPrimary else MintSuccess).copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isSent) Icons.Rounded.NorthEast else Icons.Rounded.SouthWest,
                            contentDescription = null,
                            tint = if (isSent) CobaltPrimary else MintSuccess,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = LocalNetworkUtils.formatBytes(record.fileSizeBytes),
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSent) CobaltPrimary else MintSuccess
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${record.transportMethod} • ${LocalNetworkUtils.formatSpeed(record.averageSpeedBytesPerSec)}",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row {
                    if (!record.localUri.isNullOrBlank()) {
                        IconButton(
                            onClick = onOpen,
                            modifier = Modifier.testTag("history_open_${record.id}")
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = "Open file")
                        }
                        IconButton(
                            onClick = onShare,
                            modifier = Modifier.testTag("history_share_${record.id}")
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = "Share file")
                        }
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("history_delete_${record.id}")
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete log")
                    }
                }
            }
        }
    }
}
