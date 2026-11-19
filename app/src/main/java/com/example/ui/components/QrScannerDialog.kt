package com.example.ui.components

import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.transfer.QrPairingCodec
import com.example.transfer.QrPairingPayload
import com.example.ui.theme.CyanSecondary
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalGetImage::class)
@Composable
fun QrScannerDialog(
    onDismiss: () -> Unit,
    onQrScanned: (QrPairingPayload) -> Unit,
    onManualConnectFallback: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }
    val hasScanned = remember { AtomicBoolean(false) }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    var statusMessage by remember {
        mutableStateOf("Align the ShareMe QR code on the receiving phone inside the frame")
    }
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraProviderRef?.unbindAll() }
            runCatching { scanner.close() }
            runCatching { analysisExecutor.shutdown() }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = runCatching { providerFuture.get() }.getOrNull()
                        if (provider == null) {
                            statusMessage = "Camera preview unavailable on this device"
                            return@addListener
                        }
                        cameraProviderRef = provider

                        val preview = Preview.Builder().build().apply {
                            surfaceProvider = previewView.surfaceProvider
                        }

                        val analysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(1280, 720))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage == null || hasScanned.get()) {
                                imageProxy.close()
                                return@setAnalyzer
                            }

                            val inputImage = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )
                            scanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        val raw = barcode.rawValue ?: continue
                                        val parsed = QrPairingCodec.parse(raw)
                                        if (parsed != null) {
                                            if (hasScanned.compareAndSet(false, true)) {
                                                mainExecutor.execute {
                                                    onQrScanned(parsed)
                                                }
                                            }
                                            break
                                        } else {
                                            mainExecutor.execute {
                                                statusMessage = "QR detected, but it is not a ShareMe pairing code"
                                            }
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        }

                        runCatching {
                            provider.unbindAll()
                            camera = provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis
                            )
                        }.onFailure {
                            statusMessage = "Unable to start camera scanner"
                        }
                    }, mainExecutor)

                    previewView
                }
            )

            // Viewfinder reticle overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val boxSize = minOf(size.width, size.height) * 0.66f
                val left = (size.width - boxSize) / 2f
                val top = (size.height - boxSize) / 2f
                val right = left + boxSize
                val bottom = top + boxSize
                val cornerLen = boxSize * 0.16f
                val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.25f),
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(boxSize, boxSize),
                    cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Top-left bracket
                drawLine(CyanSecondary, Offset(left, top + cornerLen), Offset(left, top), stroke.width, StrokeCap.Round)
                drawLine(CyanSecondary, Offset(left, top), Offset(left + cornerLen, top), stroke.width, StrokeCap.Round)
                // Top-right bracket
                drawLine(CyanSecondary, Offset(right - cornerLen, top), Offset(right, top), stroke.width, StrokeCap.Round)
                drawLine(CyanSecondary, Offset(right, top), Offset(right, top + cornerLen), stroke.width, StrokeCap.Round)
                // Bottom-left bracket
                drawLine(CyanSecondary, Offset(left, bottom - cornerLen), Offset(left, bottom), stroke.width, StrokeCap.Round)
                drawLine(CyanSecondary, Offset(left, bottom), Offset(left + cornerLen, bottom), stroke.width, StrokeCap.Round)
                // Bottom-right bracket
                drawLine(CyanSecondary, Offset(right - cornerLen, bottom), Offset(right, bottom), stroke.width, StrokeCap.Round)
                drawLine(CyanSecondary, Offset(right, bottom), Offset(right, bottom - cornerLen), stroke.width, StrokeCap.Round)
            }

            // Top Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = null,
                            tint = CyanSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Scan Receiver QR",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = {
                            val next = !torchEnabled
                            torchEnabled = next
                            camera?.cameraControl?.enableTorch(next)
                        },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .testTag("qr_torch_button")
                    ) {
                        Icon(
                            imageVector = if (torchEnabled) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                            contentDescription = if (torchEnabled) "Turn off flash" else "Turn on flash",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .testTag("close_qr_scanner_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close scanner",
                            tint = Color.White
                        )
                    }
                }
            }

            // Bottom Hint & Manual Fallback
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(20.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    OutlinedButton(
                        onClick = onManualConnectFallback,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("qr_manual_fallback_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Dialpad,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enter Pairing Code / IP Manually")
                    }
                }
            }
        }
    }
}
