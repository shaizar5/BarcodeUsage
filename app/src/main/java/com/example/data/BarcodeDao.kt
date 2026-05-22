package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BarcodeDao {
    @Query("SELECT * FROM barcodes ORDER BY addedAt DESC")
    fun getAllBarcodesFlow(): Flow<List<BarcodeEntity>>

    @Query("SELECT * FROM barcodes WHERE code = :code LIMIT 1")
    suspend fun getBarcodeByCode(code: String): BarcodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBarcode(barcode: BarcodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBarcodes(barcodes: List<BarcodeEntity>)

    @Query("DELETE FROM barcodes WHERE code = :code")
    suspend fun deleteBarcodeByCode(code: String)

    @Delete
    suspend fun deleteBarcodes(barcodes: List<BarcodeEntity>)

    @Query("DELETE FROM barcodes WHERE syncGroupId = :syncGroupId")
    suspend fun deleteBarcodesByGroupId(syncGroupId: String)

    @Query("SELECT * FROM barcodes WHERE syncGroupId = :syncGroupId")
    suspend fun getBarcodesByGroupId(syncGroupId: String): List<BarcodeEntity>
}
