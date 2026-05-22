package com.example.data

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class BarcodeRepository(
    private val barcodeDao: BarcodeDao,
    private val syncService: SyncService = SyncService()
) {
    /**
     * Observable flow of all barcodes stored locally.
     */
    val allBarcodes: Flow<List<BarcodeEntity>> = barcodeDao.getAllBarcodesFlow()

    suspend fun getBarcode(code: String): BarcodeEntity? = withContext(Dispatchers.IO) {
        barcodeDao.getBarcodeByCode(code)
    }

    suspend fun insertBarcode(barcode: BarcodeEntity) = withContext(Dispatchers.IO) {
        barcodeDao.insertBarcode(barcode)
        if (barcode.syncGroupId.isNotEmpty()) {
            syncAndPushGroup(barcode.syncGroupId)
        }
    }

    suspend fun updateBarcode(barcode: BarcodeEntity) = withContext(Dispatchers.IO) {
        barcodeDao.insertBarcode(barcode)
        if (barcode.syncGroupId.isNotEmpty()) {
            syncAndPushGroup(barcode.syncGroupId)
        }
    }

    suspend fun deleteBarcode(code: String) = withContext(Dispatchers.IO) {
        val barcode = barcodeDao.getBarcodeByCode(code)
        barcodeDao.deleteBarcodeByCode(code)
        if (barcode != null && barcode.syncGroupId.isNotEmpty()) {
            syncAndPushGroup(barcode.syncGroupId)
        }
    }

    /**
     * Clears all local barcodes that are associated with a group. Useful if switching rooms.
     */
    suspend fun clearLocalGroupBarcodes(groupId: String) = withContext(Dispatchers.IO) {
        barcodeDao.deleteBarcodesByGroupId(groupId)
    }

    /**
     * Synchronizes and merges local and remote barcodes for a specific Room ID.
     * Returns true if the sync succeeds, false otherwise.
     */
    suspend fun syncAndPushGroup(groupId: String): Boolean = withContext(Dispatchers.IO) {
        val cleanGroupId = groupId.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "")
        if (cleanGroupId.isEmpty()) return@withContext false

        Log.d("Repo", "Starting sync cycle for group: $cleanGroupId")
        
        // 1. Fetch remote barcodes
        val remoteList = syncService.fetchCloudBarcodes(cleanGroupId)
        if (remoteList == null) {
            Log.e("Repo", "Sync aborted: could not load cloud barcodes")
            return@withContext false
        }

        // 2. Fetch local barcodes belonging to this group
        val localList = barcodeDao.getBarcodesByGroupId(cleanGroupId)

        // 3. Perform a symmetric merge of lists using 'code' as key and comparing 'updatedAt'
        val mergedMap = mutableMapOf<String, BarcodeEntity>()

        // Seed with all remote items
        for (remote in remoteList) {
            mergedMap[remote.code] = remote
        }

        // Update or append local items
        for (local in localList) {
            val matchingRemote = mergedMap[local.code]
            if (matchingRemote == null) {
                // Not in remote list yet, add to merged set
                mergedMap[local.code] = local
            } else {
                // Barcode exists in both, keep the newest version
                if (local.updatedAt > matchingRemote.updatedAt) {
                    mergedMap[local.code] = local
                } else {
                    // Remote is identical or newer, keep remote
                }
            }
        }

        val mergedList = mergedMap.values.toList()

        // 4. Save merged list locally to ensure modern database cache is fully updated
        barcodeDao.insertBarcodes(mergedList)

        // 5. Publish the unified list back of the cloud
        val ok = syncService.uploadBarcodes(cleanGroupId, mergedList)
        if (!ok) {
            Log.e("Repo", "Sync partially failed: merged locally but failed to push to cloud")
        }
        ok
    }
}
