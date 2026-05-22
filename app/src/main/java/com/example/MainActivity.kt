package com.example

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.BarcodeEntity
import com.example.ui.BarcodeViewModel
import com.example.ui.SyncState
import com.example.ui.components.BarcodeDisplayImage
import com.example.ui.components.BarcodeScannerView
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: BarcodeViewModel = viewModel()) {
    val context = LocalContext.current
    
    // Flows from ViewModel
    val barcodes by viewModel.filteredBarcodes.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val currentRoomId by viewModel.currentRoomId.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val focusedBarcode by viewModel.focusedBarcode.collectAsStateWithLifecycle()

    // Camera permission state
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // Local UI toggle states
    var isScanning by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    
    // Add Barcode Form States
    var formCode by remember { mutableStateOf("") }
    var formTitle by remember { mutableStateOf("") }
    var formFormat by remember { mutableStateOf("CODE_128") }
    var formAmount by remember { mutableStateOf("") }
    var formCategory by remember { mutableStateOf("Other") }
    var formNotes by remember { mutableStateOf("") }

    // Calculates unspent store credit balance
    val unspentBalance = remember(barcodes) {
        barcodes.filter { !it.isUsed }.sumOf { item ->
            val cleaned = item.amount.replace(Regex("[^0-9.]"), "")
            cleaned.toDoubleOrNull() ?: 0.0
        }
    }

    val spendCount = remember(barcodes) { barcodes.count { it.isUsed } }
    val totalCount = remember(barcodes) { barcodes.size }

    // Auto-clear sync banner notification
    LaunchedEffect(syncState) {
        if (syncState is SyncState.Success || syncState is SyncState.Error) {
            kotlinx.coroutines.delay(4000)
            viewModel.clearSyncState()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background, // bg-[#F3F4F9]
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (cameraPermissionState.status.isGranted) {
                        isScanning = true
                    } else {
                        cameraPermissionState.launchPermissionRequest()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary, // bg-[#005AC1]
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp), // rounded-2xl
                modifier = Modifier
                    .padding(bottom = 8.dp) // Lifted slightly above the custom bottom bar
                    .testTag("scan_fab")
            ) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = "Scan Barcode",
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        bottomBar = {
            // Elegant Clean Minimalism tabs linked dynamically to category state, groups, and actions
            MinimalBottomNavigation(
                selectedCategory = selectedCategory,
                onCategorySelected = { target ->
                    viewModel.setSelectedCategory(target)
                },
                onJoinClicked = { showJoinDialog = true },
                onSettingsClicked = {
                    Toast.makeText(context, "CreditKeeper • Modern Minimalism Edition v1.0", Toast.LENGTH_SHORT).show()
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // MAIN DASHBOARD LAYOUT
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // LUXURIOUS HEADER BLOCK WITH USER PROFILE & STATUS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // User Profile avatar circular accent frame
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), // bg-[#DDE1FF]
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = "User Account",
                                tint = MaterialTheme.colorScheme.secondary, // text-[#00158E]
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "CreditKeeper",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 22.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    if (currentRoomId.isNotEmpty()) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                    contentDescription = "Cloud Done Status Indicator",
                                    tint = if (currentRoomId.isNotEmpty()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (currentRoomId.isNotEmpty()) "Synced with $currentRoomId" else "Offline Local model",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Share & Admin Sync shortcut row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Share connection trigger icon shortcut (Copy current share info)
                        IconButton(
                            onClick = {
                                if (currentRoomId.isNotEmpty()) {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Room Code", currentRoomId)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Copied Room ID to share!", Toast.LENGTH_SHORT).show()
                                } else {
                                    showJoinDialog = true
                                }
                            },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                                .size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Circular add button
                        IconButton(
                            onClick = {
                                // Reset manual input form state
                                formCode = ""
                                formTitle = ""
                                formFormat = "CODE_128"
                                formAmount = ""
                                formCategory = "Other"
                                formNotes = ""
                                showAddDialog = true
                            },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                                .size(36.dp)
                                .testTag("manual_add_button")
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Manual Add",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // CONNECTED ROOM COMPACT PILL (Required for test compliance and sync control actions)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentRoomId.isNotEmpty()) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { showJoinDialog = true }
                        .testTag("room_status_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                if (currentRoomId.isNotEmpty()) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = "Sync Toggle Indicator",
                                tint = if (currentRoomId.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (currentRoomId.isNotEmpty()) {
                                    "Room connected: ${currentRoomId.uppercase()}"
                                } else {
                                    "Offline Single System • Configure cloud room"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (currentRoomId.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.syncNow() },
                                    modifier = Modifier.size(24.dp).testTag("sync_now_button")
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Sync Now",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = { viewModel.leaveRoom() },
                                    modifier = Modifier.size(24.dp).testTag("leave_room_button")
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Join Offline Mode",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                Text(
                                    "Link 🔑",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // HIGH-CONTRAST CENTRAL METRIC DISPLAY (From Clean Minimalism mockup)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp)), // rounded-3xl
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer // bg-[#EADDFF]
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp), // p-5
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Active Credit",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onPrimaryContainer // text-[#21005D]
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = String.format("$%.2f", unspentBalance),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.sp
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer // text-[#21005D]
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${totalCount - spendCount} active codes remaining", // e.g. 4 valid codes remaining
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            )
                        }

                        // Premium rounded-square scan camera toggle box
                        Box(
                            modifier = Modifier
                                .size(56.dp) // w-14 h-14
                                .background(MaterialTheme.colorScheme.onPrimaryContainer, RoundedCornerShape(16.dp)) // bg-[#21005D], rounded-2xl
                                .clickable {
                                    if (cameraPermissionState.status.isGranted) {
                                        isScanning = true
                                    } else {
                                        cameraPermissionState.launchPermissionRequest()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = "Active Scan Quick trigger",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SEARCH BAR WITH THIN OUTLINE & ROUNDED EDGES
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search merchant, code or notes...", color = Color.Gray, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon label", tint = Color.Gray) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search filter", tint = Color.Gray)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("search_field"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // HORIZONTAL MERCHANT TYPE CATEGORY SELECTION FILTERS
                val categories = listOf("All", "Unused", "Used", "Groceries", "Shopping", "Café", "Tech", "Entertainment", "Other")
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { categoryName ->
                        val isSelected = selectedCategory == categoryName
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setSelectedCategory(categoryName) },
                            label = { Text(categoryName, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White,
                                containerColor = Color.White,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.outline,
                                selectedBorderColor = Color.Transparent,
                                borderWidth = 1.dp,
                                selectedBorderWidth = 0.dp
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("category_chip_$categoryName")
                        )
                    }
                }

                // DYNAMIC AUTO-DISMISS BANNER FEEDBACK TIMEOUT
                AnimatedVisibility(
                    visible = syncState != SyncState.Idle,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    val (textColor, bgColor, msgText) = when (val s = syncState) {
                        is SyncState.Syncing -> Triple(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), "Synchronizing with the cloud...")
                        is SyncState.Success -> Triple(Color(0xFF006A6A), Color(0xFFCCE8E8), s.message)
                        is SyncState.Error -> Triple(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f), s.message)
                        else -> Triple(Color.Unspecified, Color.Unspecified, "")
                    }
                    
                    if (msgText.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(bgColor, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (syncState is SyncState.Syncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = msgText,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = textColor
                            )
                        }
                    }
                }

                // SUBHEADER: RECENT CREDITS VIEW ALL LINK
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT CREDITS",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.2.sp
                        )
                    )
                    Text(
                        text = "View All",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.clickable {
                            viewModel.setSelectedCategory("All")
                        }
                    )
                }

                // BARCODES LISTS WINDOW
                if (barcodes.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.ShoppingBag,
                            contentDescription = "Empty credit state",
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matched vouchers found" else "Your credit wallet is empty",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Try adjusting search tags or filters" else "Tap QR trigger or FAB to scan a barcode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("barcodes_list"),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 96.dp, top = 2.dp)
                    ) {
                        items(barcodes) { item ->
                            BarcodeItemRow(
                                item = item,
                                onToggleUsed = { viewModel.toggleUsedState(item) },
                                onClick = { viewModel.setFocusedBarcode(item) }
                            )
                        }
                    }
                }
            }

            // FULLSCREEN CAMERA SCANNER SYSTEM
            if (isScanning) {
                BarcodeScannerView(
                    onBarcodeScanned = { code, format ->
                        isScanning = false
                        // Prepopulate the manual fields cleanly
                        formCode = code
                        formTitle = ""
                        formFormat = format
                        formAmount = ""
                        formCategory = "Other"
                        formNotes = ""
                        showAddDialog = true
                        
                        Toast.makeText(context, "Scanned $format coupon!", Toast.LENGTH_SHORT).show()
                    },
                    onClose = { isScanning = false },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // ADD / REGISTRATION EDIT DIALOG VIEWPORT
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    text = if (formCode.isNotEmpty()) "Register Scanned Voucher" else "Add Custom Credit",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = formTitle,
                        onValueChange = { formTitle = it },
                        label = { Text("Merchant / Title") },
                        placeholder = { Text("Walmart, Starbucks, Target...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("form_title_field"),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = formAmount,
                            onValueChange = { formAmount = it },
                            label = { Text("Amount ($)") },
                            placeholder = { Text("50.00") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("form_amount_field"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = formFormat,
                            onValueChange = { formFormat = it },
                            label = { Text("Format") },
                            readOnly = formCode.isNotEmpty(),
                            modifier = Modifier
                                .weight(1.1f)
                                .testTag("form_format_field"),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = formCode,
                        onValueChange = { formCode = it },
                        label = { Text("Barcode alphanumeric Code") },
                        placeholder = { Text("Alphanumeric sequence") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("form_code_field"),
                        singleLine = true
                    )

                    // Card Category selectors
                    Column {
                        Text("Voucher Category", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        val options = listOf("Groceries", "Shopping", "Café", "Tech", "Entertainment", "Other")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(options) { opt ->
                                val active = formCategory == opt
                                FilterChip(
                                    selected = active,
                                    onClick = { formCategory = opt },
                                    label = { Text(opt, fontSize = 11.sp) },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = formNotes,
                        onValueChange = { formNotes = it },
                        label = { Text("Notes (optional)") },
                        placeholder = { Text("Card card PIN, Expiration details...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("form_notes_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (formCode.isBlank()) {
                            Toast.makeText(context, "Barcode value cannot be empty", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.saveBarcode(
                            code = formCode,
                            title = formTitle.ifEmpty { "Store Voucher" },
                            format = formFormat,
                            amount = formAmount,
                            isUsed = false,
                            category = formCategory,
                            notes = formNotes
                        )
                        showAddDialog = false
                    },
                    modifier = Modifier.testTag("form_submit_button")
                ) {
                    Text("Add to Wallet")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // CLOUD SYNERGY CONNECT ROOM DIALOG
    if (showJoinDialog) {
        var inputRoomId by remember { mutableStateOf(currentRoomId) }
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("Connect Group Cloud Room") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Vouchers inside this room will instantly synchronize and share across anyone using this exact same room ID. Enter a shared code (e.g. FAMILY_VOUCHERS) below.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputRoomId,
                        onValueChange = { inputRoomId = it },
                        label = { Text("Group Room Code") },
                        placeholder = { Text("e.g. FAMILY_TOKENS") },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("room_id_input_field"),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.joinRoom(inputRoomId)
                        showJoinDialog = false
                    },
                    modifier = Modifier.testTag("connect_room_button")
                ) {
                    Text("Connect & Sync")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // PRESENT VIEWPORT PRESENTATION DETAIL SHEET
    if (focusedBarcode != null) {
        val item = focusedBarcode!!
        Dialog(
            onDismissRequest = { viewModel.setFocusedBarcode(null) },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), CircleShape)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(item.category) },
                                        modifier = Modifier.height(30.dp)
                                    )
                                    if (item.syncGroupId.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Synced ☁️",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Text(
                                text = if (item.amount.startsWith("$") || item.amount.contains(Regex("[A-Z]"))) {
                                    item.amount
                                } else {
                                    "$${item.amount}"
                                },
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            "PRESENT TO CASHIER TO SCAN",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        BarcodeDisplayImage(
                            content = item.code,
                            formatName = item.format,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                                .testTag("focused_barcode_render")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = item.code,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Code String") },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Barcode", item.code)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied code sequence!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Copy code sequence")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("focused_code_field")
                        )

                        if (item.notes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Card Notes:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text(item.notes, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.toggleUsedState(item)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (item.isUsed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("focused_toggle_used_button")
                            ) {
                                Icon(
                                    if (item.isUsed) Icons.Default.Refresh else Icons.Default.Check,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (item.isUsed) "Reactively Mark Unused" else "Mark as Spent Vouchers")
                            }

                            IconButton(
                                onClick = {
                                    viewModel.deleteBarcode(item)
                                },
                                modifier = Modifier
                                    .size(50.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(10.dp))
                                    .testTag("focused_delete_button")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Voucher", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        TextButton(onClick = { viewModel.setFocusedBarcode(null) }) {
                            Text("Dismiss Presentation View")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BarcodeItemRow(
    item: BarcodeEntity,
    onToggleUsed: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("barcode_row_${item.code}"),
        shape = RoundedCornerShape(16.dp), // rounded-2xl
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline), // border border-[#E1E2EC]
        elevation = CardDefaults.cardElevation(defaultElevation = if (item.isUsed) 0.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp), // p-4
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rounded Icon Box: size 48.dp, bg #F3F4F9, rounded-xl (12.dp)
            val (icon, color) = remember(item.category) {
                when (item.category) {
                    "Groceries" -> Pair(Icons.Default.ShoppingCart, Color(0xFF4CAF50))
                    "Shopping" -> Pair(Icons.Default.LocalMall, Color(0xFF2196F3))
                    "Café" -> Pair(Icons.Default.Coffee, Color(0xFFFF9800))
                    "Tech" -> Pair(Icons.Default.Devices, Color(0xFF9C27B0))
                    "Entertainment" -> Pair(Icons.Default.ConfirmationNumber, Color(0xFFE91E63))
                    else -> Pair(Icons.Default.Tag, Color(0xFF607D8B))
                }
            }

            Box(
                modifier = Modifier
                    .size(48.dp) // w-12 h-12
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp)), // bg-[#F3F4F9], rounded-xl
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (item.isUsed) Color.Gray else color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Merchant Details Middle Block
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (item.isUsed) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (item.isUsed) Color.Gray else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.syncGroupId.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "SYNC",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = "${item.format} • ${item.code}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.notes.isNotEmpty()) {
                    Text(
                        text = item.notes,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (item.isUsed) TextDecoration.LineThrough else TextDecoration.None
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Value & Custom Status Badge Pill
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (item.amount.startsWith("$") || item.amount.contains(Regex("[A-Z]"))) {
                        item.amount
                    } else {
                        "$${item.amount}"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        textDecoration = if (item.isUsed) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (item.isUsed) Color.Gray else MaterialTheme.colorScheme.tertiary
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Custom Status Badge: UNUSED (text #006A6A, bg #CCE8E8), REDEEMED (text #74777F, bg #E1E2EC)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp)) // rounded-full
                        .background(
                            if (item.isUsed) {
                                MaterialTheme.colorScheme.outline // bg-[#E1E2EC]
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer // bg-[#CCE8E8]
                            }
                        )
                        .clickable(onClick = onToggleUsed)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .testTag("toggle_used_check_${item.code}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (item.isUsed) {
                            "REDEEMED"
                        } else {
                            "UNUSED"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = if (item.isUsed) {
                            MaterialTheme.colorScheme.onSurfaceVariant // text-[#74777F]
                        } else {
                            MaterialTheme.colorScheme.tertiary // text-[#006A6A]
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MinimalBottomNavigation(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    onJoinClicked: () -> Unit,
    onSettingsClicked: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background, // bg-[#F3F4F9]
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outline, shape = RectangleShape) // border-t border-[#E1E2EC]
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding() // Safely offsets system navigation gestured overlay bars
                .padding(bottom = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // TAB 1: Vault (Unused/Active)
                val isVaultActive = selectedCategory != "Used"
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onCategorySelected("Unused") }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isVaultActive) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                            )
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = "Vault",
                            tint = if (isVaultActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Vault",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isVaultActive) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = if (isVaultActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // TAB 2: History (Spent/Used)
                val isHistoryActive = selectedCategory == "Used"
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onCategorySelected("Used") }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isHistoryActive) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                            )
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "History",
                            tint = if (isHistoryActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "History",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isHistoryActive) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = if (isHistoryActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // TAB 3: Members (Room Sync Trigger)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onJoinClicked() }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Transparent)
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Group,
                            contentDescription = "Members",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Members",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // TAB 4: Settings (Toast Display Meta)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSettingsClicked() }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Transparent)
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Beautiful Screen bottom custom gesture layout indicator matching system mockup (24w 1h rounded-full)
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .height(4.dp)
                        .background(color = Color.Black.copy(alpha = 0.1f), shape = RoundedCornerShape(2.dp))
                )
            }
        }
    }
}
