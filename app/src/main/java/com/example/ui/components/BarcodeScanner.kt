package com.example.ui.components

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@SuppressLint("UnrememberedMutableState")
@Composable
fun BarcodeScannerView(
    onBarcodeScanned: (code: String, format: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    var hasError by remember { mutableStateOf(false) }

    // Pulsing animation for scan line
    val infiniteTransition = rememberInfiniteTransition(label = "laser_transition")
    val laserOffset by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_anim"
    )

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (hasError) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Failed to start camera preview",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onClose) {
                    Text("Close Scanner")
                }
            }
        } else {
            // CameraX Preview integrated into Jetpack Compose
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            
                            val preview = Preview.Builder().build().apply {
                                setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val scanner = BarcodeScanning.getClient()
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            var isScanned = false

                            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                @OptIn(ExperimentalGetImage::class)
                                val mediaImage = imageProxy.image
                                if (mediaImage != null && !isScanned) {
                                    val image = InputImage.fromMediaImage(
                                        mediaImage,
                                        imageProxy.imageInfo.rotationDegrees
                                    )
                                    scanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            val firstBarcode = barcodes.firstOrNull()
                                            if (firstBarcode != null && !isScanned) {
                                                val rawValue = firstBarcode.rawValue
                                                if (rawValue != null) {
                                                    isScanned = true
                                                    val formatStr = mapMlKitFormatToZxing(firstBarcode.format)
                                                    // Post scanned barcode details to UI thread callback
                                                    previewView.post {
                                                        onBarcodeScanned(rawValue, formatStr)
                                                    }
                                                }
                                            }
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e("Scanner", "ML Kit extraction error: ${e.message}")
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }

                            // Select back camera
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            Log.e("Scanner", "Fatal camera exception: ${e.message}", e)
                            hasError = true
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
            )

            // Scanning Viewfinder Box Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                // Dimmed background helper layout can be drawn, but simple HUD outline is clean
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .size(280.dp)
                            .border(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    ) {
                        // Drawing scanning motion line (laser)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val y = size.height * laserOffset
                            drawLine(
                                color = Color.Red,
                                start = Offset(12f, y),
                                end = Offset(size.width - 12f, y),
                                strokeWidth = 4f
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                     
                    Text(
                        text = "Position barcode inside the square",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.background(
                            Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp)
                        ).padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Close button overlay on top corner
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.TopStart
            ) {
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black.copy(alpha = 0.7f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("close_scanner_button")
                ) {
                    Text("← Cancel")
                }
            }
        }
    }
}

/**
 * Normalizes ML Kit barcode formats to match ZXing Format enum naming keys.
 */
private fun mapMlKitFormatToZxing(mlKitFormat: Int): String {
    return when (mlKitFormat) {
        Barcode.FORMAT_QR_CODE -> "QR_CODE"
        Barcode.FORMAT_CODE_128 -> "CODE_128"
        Barcode.FORMAT_CODE_39 -> "CODE_39"
        Barcode.FORMAT_EAN_13 -> "EAN_13"
        Barcode.FORMAT_EAN_8 -> "EAN_8"
        Barcode.FORMAT_UPC_A -> "UPC_A"
        Barcode.FORMAT_UPC_E -> "UPC_E"
        Barcode.FORMAT_ITF -> "ITF"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_AZTEC -> "AZTEC"
        Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
        else -> "QR_CODE"
    }
}
