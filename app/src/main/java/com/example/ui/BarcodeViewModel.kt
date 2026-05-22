package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.BarcodeEntity
import com.example.data.BarcodeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface SyncState {
    object Idle : SyncState
    object Syncing : SyncState
    data class Success(val message: String) : SyncState
    data class Error(val message: String) : SyncState
}

class BarcodeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: BarcodeRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = BarcodeRepository(database.barcodeDao())
    }

    // State flows
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _currentRoomId = MutableStateFlow("")
    val currentRoomId: StateFlow<String> = _currentRoomId.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    // Holds the currently focused barcode for on-screen generation
    private val _focusedBarcode = MutableStateFlow<BarcodeEntity?>(null)
    val focusedBarcode: StateFlow<BarcodeEntity?> = _focusedBarcode.asStateFlow()

    // Combines and filters barcodes dynamically using search query, selected category, and room ID filters
    val filteredBarcodes: StateFlow<List<BarcodeEntity>> = combine(
        repository.allBarcodes,
        _searchQuery,
        _selectedCategory,
        _currentRoomId
    ) { list, query, category, roomId ->
        list.filter { item ->
            // Filter by Room ID group
            val matchesRoom = if (roomId.isEmpty()) {
                item.syncGroupId.isEmpty()
            } else {
                item.syncGroupId == roomId
            }

            // Filter by Category
            val matchesCategory = if (category == "All") {
                true
            } else if (category == "Used") {
                item.isUsed
            } else if (category == "Unused") {
                !item.isUsed
            } else {
                item.category.equals(category, ignoreCase = true)
            }

            // Filter by Search Query (match title, notes, code, amount)
            val matchesQuery = if (query.isEmpty()) {
                true
            } else {
                item.title.contains(query, ignoreCase = true) ||
                        item.code.contains(query, ignoreCase = true) ||
                        item.notes.contains(query, ignoreCase = true) ||
                        item.amount.contains(query, ignoreCase = true)
            }

            matchesRoom && matchesCategory && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setFocusedBarcode(barcode: BarcodeEntity?) {
        _focusedBarcode.value = barcode
    }

    /**
     * Enters or updates the shared cloud Room ID.
     * Generates a sync cycle instantly to load shared barcodes.
     */
    fun joinRoom(roomId: String) {
        val cleanRoom = roomId.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "")
        _currentRoomId.value = cleanRoom
        if (cleanRoom.isNotEmpty()) {
            syncNow()
        }
    }

    fun leaveRoom() {
        _currentRoomId.value = ""
    }

    /**
     * Performs a full dual cloud-local database sync.
     */
    fun syncNow() {
        val roomId = _currentRoomId.value
        if (roomId.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _syncState.value = SyncState.Syncing
            val success = repository.syncAndPushGroup(roomId)
            if (success) {
                _syncState.value = SyncState.Success("Synced with Room '$roomId'")
            } else {
                _syncState.value = SyncState.Error("Offline or Sync Failed")
            }
        }
    }

    /**
     * Resets sync banner to idle.
     */
    fun clearSyncState() {
        _syncState.value = SyncState.Idle
    }

    /**
     * Adds or overrides a barcode.
     */
    fun saveBarcode(
        code: String,
        title: String,
        format: String,
        amount: String,
        isUsed: Boolean,
        category: String,
        notes: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val barcode = BarcodeEntity(
                code = code.trim(),
                title = title.trim().ifEmpty { "Barcode Credit" },
                format = format,
                amount = amount.trim().ifEmpty { "0.00" },
                isUsed = isUsed,
                category = category,
                notes = notes,
                addedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                syncGroupId = _currentRoomId.value
            )
            repository.insertBarcode(barcode)
            
            // If focused item changed, update it too
            if (_focusedBarcode.value?.code == code) {
                _focusedBarcode.value = barcode
            }
        }
    }

    /**
     * Instantly marks a barcode as Used or Unused. This avoids tedious edit screens.
     */
    fun toggleUsedState(barcode: BarcodeEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = barcode.copy(
                isUsed = !barcode.isUsed,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateBarcode(updated)
            if (_focusedBarcode.value?.code == barcode.code) {
                _focusedBarcode.value = updated
            }
        }
    }

    /**
     * Deletes a barcode permanently.
     */
    fun deleteBarcode(barcode: BarcodeEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteBarcode(barcode.code)
            if (_focusedBarcode.value?.code == barcode.code) {
                _focusedBarcode.value = null
            }
        }
    }
}
