package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "barcodes")
data class BarcodeEntity(
    @PrimaryKey val code: String,
    val title: String,
    val format: String,           // e.g., "QR_CODE", "CODE_128"
    val amount: String,           // e.g., "50.00" or "$100"
    val isUsed: Boolean = false,
    val category: String = "Other",
    val notes: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncGroupId: String = ""  // Linked room code, empty if local-only
)
