package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BarcodeDisplayImage(
    content: String,
    formatName: String,
    modifier: Modifier = Modifier
) {
    var imageBitmap by remember(content, formatName) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var isLoading by remember(content, formatName) { mutableStateOf(true) }
    var hasError by remember(content, formatName) { mutableStateOf(false) }

    LaunchedEffect(content, formatName) {
        isLoading = true
        hasError = false
        withContext(Dispatchers.Default) {
            try {
                val zxingFormat = mapFormatNameToZxing(formatName)
                val isQr = zxingFormat == BarcodeFormat.QR_CODE
                
                // 1D barcodes are wide and short, 2D barcodes (QR) are perfect squares
                val width = if (isQr) 512 else 820
                val height = if (isQr) 512 else 260
                
                val bitMatrix = MultiFormatWriter().encode(content, zxingFormat, width, height)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        bitmap.setPixel(
                            x,
                            y,
                            if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                        )
                    }
                }
                imageBitmap = bitmap.asImageBitmap()
            } catch (e: Exception) {
                Log.e("BarcodeDisplay", "Failed to encode visual barcode: ${e.message}", e)
                hasError = true
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(androidx.compose.ui.graphics.Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Generating barcode...", style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.Gray)
            }
        } else if (hasError || imageBitmap == null) {
            Text(
                text = "Could not render format\n$formatName visually",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            Image(
                bitmap = imageBitmap!!,
                contentDescription = "Barcode image for $content in format $formatName",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            )
        }
    }
}

/**
 * Maps String representations of formats back to standard ZXing's BarcodeFormat.
 */
private fun mapFormatNameToZxing(formatName: String): BarcodeFormat {
    return try {
        BarcodeFormat.valueOf(formatName)
    } catch (e: Exception) {
        when (formatName.uppercase()) {
            "QR_CODE" -> BarcodeFormat.QR_CODE
            "CODE_128" -> BarcodeFormat.CODE_128
            "CODE_39" -> BarcodeFormat.CODE_39
            "EAN_13" -> BarcodeFormat.EAN_13
            "EAN_8" -> BarcodeFormat.EAN_8
            "UPC_A" -> BarcodeFormat.UPC_A
            "UPC_E" -> BarcodeFormat.UPC_E
            "ITF" -> BarcodeFormat.ITF
            "PDF417" -> BarcodeFormat.PDF_417
            "AZTEC" -> BarcodeFormat.AZTEC
            "DATA_MATRIX" -> BarcodeFormat.DATA_MATRIX
            else -> BarcodeFormat.CODE_128 // Default to Code 128 as it is the store-run standard
        }
    }
}
