package com.example.data

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class SyncService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val bucketId = "CreditKeeper_v1_e823b1" // Unique application namespace
    private val baseUrl = "https://kvdb.io/$bucketId"

    /**
     * Fetch barcodes list representing the cloud state for the given room ID.
     */
    suspend fun fetchCloudBarcodes(roomId: String): List<BarcodeEntity>? {
        val sanitizedRoomId = roomId.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "")
        if (sanitizedRoomId.isEmpty()) return null
        
        val url = "$baseUrl/$sanitizedRoomId"
        Log.d("SyncService", "Fetching from cloud: $url")
        
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: "[]"
                    parseJsonToBarcodes(body, roomId)
                } else if (response.code == 404) {
                    Log.d("SyncService", "Room does not exist yet on cloud (404)")
                    emptyList()
                } else {
                    Log.e("SyncService", "Unsuccessful read response: ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("SyncService", "Network fetch failed: ${e.message}", e)
            null
        }
    }

    /**
     * Upload the unified barcodes list for this room ID to the cloud.
     */
    suspend fun uploadBarcodes(roomId: String, barcodes: List<BarcodeEntity>): Boolean {
        val sanitizedRoomId = roomId.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "")
        if (sanitizedRoomId.isEmpty()) return false

        val url = "$baseUrl/$sanitizedRoomId"
        Log.d("SyncService", "Uploading to cloud: $url (${barcodes.size} items)")
        
        val jsonString = serializeBarcodesToJson(barcodes)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonString.toRequestBody(mediaType)
        
        val request = Request.Builder()
            .url(url)
            .put(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d("SyncService", "Successfully updated cloud barcodes")
                    true
                } else {
                    Log.e("SyncService", "Cloud write failed with status: ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("SyncService", "Upload failed: ${e.message}", e)
            false
        }
    }

    private fun parseJsonToBarcodes(jsonStr: String, roomId: String): List<BarcodeEntity> {
        val list = mutableListOf<BarcodeEntity>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    BarcodeEntity(
                        code = obj.getString("code"),
                        title = obj.getString("title"),
                        format = obj.optString("format", "QR_CODE"),
                        amount = obj.optString("amount", "0.00"),
                        isUsed = obj.optBoolean("isUsed", false),
                        category = obj.optString("category", "Other"),
                        notes = obj.optString("notes", ""),
                        addedAt = obj.optLong("addedAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                        syncGroupId = roomId
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("SyncService", "JSON parsing failed for barcodes JSON: $jsonStr", e)
        }
        return list
    }

    private fun serializeBarcodesToJson(barcodes: List<BarcodeEntity>): String {
        val arr = JSONArray()
        for (item in barcodes) {
            val obj = JSONObject().apply {
                put("code", item.code)
                put("title", item.title)
                put("format", item.format)
                put("amount", item.amount)
                put("isUsed", item.isUsed)
                put("category", item.category)
                put("notes", item.notes)
                put("addedAt", item.addedAt)
                put("updatedAt", item.updatedAt)
            }
            arr.put(obj)
        }
        return arr.toString()
    }
}
