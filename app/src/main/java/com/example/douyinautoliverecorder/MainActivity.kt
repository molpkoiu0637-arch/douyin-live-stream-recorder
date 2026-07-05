package com.example.douyinautoliverecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // No-op.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureNotificationPermission()

        val initialSettings = AppPrefs.getSettings(this)
        RuntimeStateStore.setMonitorRunning(initialSettings.monitorEnabled)

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RecorderApp()
                }
            }
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private enum class SchedulePickerTarget {
    START,
    END
}

private data class RoomSchedulePickerTarget(
    val roomId: String,
    val target: SchedulePickerTarget
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecorderApp() {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val webProbe = remember(context) { WebViewLiveProbe(context) }
    val resolver = remember(context) { DouyinLiveResolver(webProbe = webProbe) }
    DisposableEffect(webProbe) {
        onDispose { webProbe.destroy() }
    }

    var settings by remember { mutableStateOf(AppPrefs.getSettings(context)) }
    var rooms by remember { mutableStateOf(AppPrefs.getRooms(context)) }
    var roomInputValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var expandedRoomId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeTimePicker by remember { mutableStateOf<SchedulePickerTarget?>(null) }
    var activeRoomTimePicker by remember { mutableStateOf<RoomSchedulePickerTarget?>(null) }
    val roomInputFocusRequester = remember { FocusRequester() }
    val roomListState = rememberLazyListState()
    var roomInputHasFocus by remember { mutableStateOf(false) }
    var draggingRoomId by remember { mutableStateOf<String?>(null) }
    var pendingVisibleRoomId by remember { mutableStateOf<String?>(null) }

    val runtimeStates by RuntimeStateStore.roomStates.collectAsState()
    val monitorRunning by RuntimeStateStore.monitorRunning.collectAsState()

    BackHandler(enabled = showSettings) {
        showSettings = false
    }

    LaunchedEffect(runtimeStates) {
        rooms = AppPrefs.getRooms(context)
    }

    LaunchedEffect(roomInputHasFocus, roomInputValue.text) {
        if (!roomInputHasFocus || roomInputValue.text.isNotBlank()) {
            return@LaunchedEffect
        }
        delay(5_000L)
        if (roomInputHasFocus && roomInputValue.text.isBlank()) {
            focusManager.clearFocus(force = true)
        }
    }

    val treePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }

        val updated = settings.copy(
            storageMode = StorageMode.DOCUMENT_TREE,
            storageTreeUri = uri.toString()
        )
        settings = updated
        AppPrefs.setSettings(context, updated)
        Toast.makeText(context, AppText.storageFolderSelected(context), Toast.LENGTH_SHORT).show()
    }

    fun triggerImmediateMonitorIfRunning() {
        if (monitorRunning) {
            LiveMonitorService.triggerNow(context)
        }
    }

    fun updateSettings(updated: AppSettings, triggerMonitor: Boolean = false) {
        settings = updated
        AppPrefs.setSettings(context, updated)
        if (triggerMonitor) {
            triggerImmediateMonitorIfRunning()
        }
    }

    suspend fun keepDraggedRoomVisible(targetIndex: Int) {
        val layoutInfo = roomListState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) {
            return
        }

        val firstVisibleIndex = visibleItems.first().index
        val firstVisibleOffset = roomListState.firstVisibleItemScrollOffset
        val lastVisibleIndex = visibleItems.last().index
        val visibleCount = visibleItems.size.coerceAtLeast(1)
        val viewportStart = layoutInfo.viewportStartOffset
        val viewportEnd = layoutInfo.viewportEndOffset
        val targetItem = visibleItems.firstOrNull { it.index == targetIndex }
        when {
            targetIndex == 0 &&
                (roomListState.firstVisibleItemIndex > 0 || firstVisibleOffset > 0 || targetItem == null || targetItem.offset > viewportStart) -> {
                roomListState.scrollToItem(0)
            }

            targetIndex < firstVisibleIndex -> {
                roomListState.scrollToItem(targetIndex)
            }

            targetItem != null && targetItem.offset < viewportStart -> {
                roomListState.scrollToItem(targetIndex)
            }

            targetItem != null && targetItem.offset + targetItem.size > viewportEnd -> {
                val anchorIndex = (targetIndex - visibleCount + 1).coerceAtLeast(0)
                roomListState.scrollToItem(anchorIndex)
            }

            targetIndex > lastVisibleIndex -> {
                val anchorIndex = (targetIndex - visibleCount + 1).coerceAtLeast(0)
                roomListState.scrollToItem(anchorIndex)
            }
        }
    }

    LaunchedEffect(rooms, pendingVisibleRoomId) {
        val roomId = pendingVisibleRoomId ?: return@LaunchedEffect
        val targetIndex = rooms.indexOfFirst { it.id == roomId }
        if (targetIndex >= 0) {
            keepDraggedRoomVisible(targetIndex)
        }
        pendingVisibleRoomId = null
    }

    fun addRoomFromInput() {
        val result = AppPrefs.addRoom(context, roomInputValue.text)
        if (result.error != null) {
            Toast.makeText(context, result.error, Toast.LENGTH_SHORT).show()
            roomInputValue = roomInputValue.copy(selection = TextRange(roomInputValue.text.length))
            return
        }

        roomInputValue = TextFieldValue("")
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        rooms = AppPrefs.getRooms(context)
        RuntimeStateStore.removeMissing(rooms.map { it.id }.toSet())

        val newRoom = result.room
        if (newRoom != null) {
            triggerImmediateMonitorIfRunning()
            scope.launch {
                runCatching { resolver.probe(newRoom.normalizedInput) }
                    .onSuccess { probe ->
                        AppPrefs.updateRoomMetadata(
                            context = context,
                            roomId = newRoom.id,
                            displayName = probe.roomDisplayName,
                            douyinId = probe.douyinId,
                            avatarUrl = probe.avatarUrl,
                            resolvedRoomId = probe.resolvedRoomId
                        )
                    }
                rooms = AppPrefs.getRooms(context)
            }
        }
    }

    fun startMonitorNow() {
        if (settings.storageMode == StorageMode.DOCUMENT_TREE && settings.storageTreeUri.isNullOrBlank()) {
            Toast.makeText(
                context,
                AppText.selectStorageFolderFirst(context),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val enabledRooms = AppPrefs.getRooms(context).count { it.enabled }
        if (enabledRooms == 0) {
            Toast.makeText(
                context,
                AppText.noEnabledRooms(context),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val updated = settings.copy(monitorEnabled = true)
        settings = updated
        AppPrefs.setSettings(context, updated)

        val now = System.currentTimeMillis()
        AppPrefs.getRooms(context).filter { it.enabled }.forEach { room ->
            RuntimeStateStore.updateRoom(room.id) {
                it.copy(
                    status = RoomStatus.CHECKING,
                    message = AppText.startingMonitor(context),
                    lastCheckedAtMs = now,
                    saveProgressPercent = null
                )
            }
        }

        LiveMonitorService.start(context)
        RuntimeStateStore.setMonitorRunning(true)
    }

    fun toggleMonitor() {
        if (monitorRunning) {
            val updated = settings.copy(monitorEnabled = false)
            settings = updated
            AppPrefs.setSettings(context, updated)
            LiveMonitorService.stop(context)
            RuntimeStateStore.setMonitorRunning(false)
        } else {
            startMonitorNow()
        }
    }

    activeTimePicker?.let { pickerTarget ->
        val initialMinutes = when (pickerTarget) {
            SchedulePickerTarget.START -> settings.monitorWindowStartMinutes
            SchedulePickerTarget.END -> settings.monitorWindowEndMinutes
        }
        WheelTimePickerDialog(
            title = when (pickerTarget) {
                SchedulePickerTarget.START -> AppText.scheduleStartLabel(context)
                SchedulePickerTarget.END -> AppText.scheduleEndLabel(context)
            },
            initialMinutes = initialMinutes,
            onDismiss = { activeTimePicker = null },
            onConfirm = { pickedMinutes ->
                val updated = when (pickerTarget) {
                    SchedulePickerTarget.START -> settings.copy(monitorWindowStartMinutes = pickedMinutes)
                    SchedulePickerTarget.END -> settings.copy(monitorWindowEndMinutes = pickedMinutes)
                }
                updateSettings(updated, triggerMonitor = true)
                activeTimePicker = null
            }
        )
    }

    activeRoomTimePicker?.let { picker ->
        val targetRoom = rooms.firstOrNull { it.id == picker.roomId }
        if (targetRoom == null) {
            activeRoomTimePicker = null
        } else {
            val initialMinutes = when (picker.target) {
                SchedulePickerTarget.START -> targetRoom.monitorWindowStartMinutes
                SchedulePickerTarget.END -> targetRoom.monitorWindowEndMinutes
            }
            WheelTimePickerDialog(
                title = when (picker.target) {
                    SchedulePickerTarget.START -> AppText.scheduleStartLabel(context)
                    SchedulePickerTarget.END -> AppText.scheduleEndLabel(context)
                },
                initialMinutes = initialMinutes,
                onDismiss = { activeRoomTimePicker = null },
                onConfirm = { pickedMinutes ->
                    val updatedStart = if (picker.target == SchedulePickerTarget.START) {
                        pickedMinutes
                    } else {
                        targetRoom.monitorWindowStartMinutes
                    }
                    val updatedEnd = if (picker.target == SchedulePickerTarget.END) {
                        pickedMinutes
                    } else {
                        targetRoom.monitorWindowEndMinutes
                    }
                    AppPrefs.setRoomSchedule(
                        context = context,
                        roomId = targetRoom.id,
                        enabled = targetRoom.scheduleEnabled,
                        startMinutes = updatedStart,
                        endMinutes = updatedEnd
                    )
                    rooms = AppPrefs.getRooms(context)
                    triggerImmediateMonitorIfRunning()
                    activeRoomTimePicker = null
                }
            )
        }
    }

    Crossfade(targetState = showSettings, label = "screen") { inSettings ->
        if (inSettings) {
            SettingsScreen(
                settings = settings,
                onBack = { showSettings = false },
                onUpdateSettings = { updated, trigger -> updateSettings(updated, trigger) },
                onPickFolder = { treePicker.launch(settings.storageTreeUri?.let(Uri::parse)) },
                onOpenTimePicker = { target -> activeTimePicker = target }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = AppText.appTitle(context),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = AppText.settingsTab(context),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HeroCard(
                    running = monitorRunning,
                    settings = settings,
                    rooms = rooms,
                    runtimeStates = runtimeStates,
                    onToggle = { toggleMonitor() }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = roomInputValue,
                        onValueChange = { roomInputValue = it },
                        placeholder = {
                            Text(
                                text = AppText.roomHint(context),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(roomInputFocusRequester)
                            .onFocusChanged { roomInputHasFocus = it.isFocused },
                        singleLine = true,
                        shape = CircleShape,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { addRoomFromInput() }
                        )
                    )

                    FilledIconButton(
                        onClick = { addRoomFromInput() },
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = AppText.addRoomShort(context)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = AppText.roomsCount(context, rooms.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = AppText.dragSortLabel(context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = roomListState,
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = rooms,
                        key = { _, room -> room.id }
                    ) { index, room ->
                        val runRoomCheck: () -> Unit = {
                            scope.launch {
                                val now = System.currentTimeMillis()
                                RuntimeStateStore.updateRoom(room.id) {
                                    it.copy(
                                        status = RoomStatus.CHECKING,
                                        message = AppText.checking(context),
                                        lastCheckedAtMs = now,
                                        saveProgressPercent = null
                                    )
                                }

                                val probe = resolver.probe(room.normalizedInput)
                                AppPrefs.updateRoomMetadata(
                                    context = context,
                                    roomId = room.id,
                                    displayName = probe.roomDisplayName,
                                    douyinId = probe.douyinId,
                                    avatarUrl = probe.avatarUrl,
                                    resolvedRoomId = probe.resolvedRoomId
                                )
                                rooms = AppPrefs.getRooms(context)

                                val hasStream = selectPreviewStreamUrl(probe.streamUrls, settings.quality, settings.bitrate) != null
                                val showOfflineStatus = probe.shouldShowOfflineStatus()
                                val message = when {
                                    probe.blockedByVerification -> AppText.verificationRequired(context)
                                    showOfflineStatus -> AppText.offline(context)
                                    !probe.isLive && !probe.isReliable -> probe.message ?: AppText.probeUncertain(context)
                                    !probe.isLive -> AppText.offline(context)
                                    !hasStream -> AppText.liveNoStream(context)
                                    else -> AppText.roomReady(context)
                                }
                                val status = when {
                                    probe.blockedByVerification -> RoomStatus.ERROR
                                    showOfflineStatus -> RoomStatus.OFFLINE
                                    !probe.isLive && !probe.isReliable -> RoomStatus.ERROR
                                    !probe.isLive -> RoomStatus.OFFLINE
                                    !hasStream -> RoomStatus.ERROR
                                    else -> RoomStatus.LIVE
                                }

                                val checkedAt = System.currentTimeMillis()
                                RuntimeStateStore.updateRoom(room.id) { current ->
                                    val wasLive = current.status == RoomStatus.LIVE || current.status == RoomStatus.RECORDING
                                    val liveStartAt = if (probe.isLive && !wasLive) checkedAt else current.lastLiveStartAtMs
                                    val liveEndAt = if (status == RoomStatus.OFFLINE && wasLive) checkedAt else current.lastLiveEndAtMs
                                    current.copy(
                                        status = status,
                                        message = message,
                                        lastCheckedAtMs = checkedAt,
                                        saveProgressPercent = null,
                                        lastLiveStartAtMs = liveStartAt,
                                        lastLiveEndAtMs = liveEndAt
                                    )
                                }
                            }
                        }
                        RoomCard(
                            room = room,
                            runtime = runtimeStates[room.id],
                            index = index,
                            lastIndex = rooms.lastIndex,
                            expanded = expandedRoomId == room.id,
                            onExpandToggle = {
                                expandedRoomId = if (expandedRoomId == room.id) null else room.id
                            },
                            onDragStateChange = { isDragging ->
                                draggingRoomId = if (isDragging) room.id else draggingRoomId.takeUnless { it == room.id }
                            },
                            onMoveTo = { targetIndex ->
                                val moved = AppPrefs.moveRoomTo(context, room.id, targetIndex)
                                if (moved) {
                                    rooms = AppPrefs.getRooms(context)
                                    pendingVisibleRoomId = room.id
                                }
                                moved
                            },
                            onToggleEnabled = { enabled ->
                                AppPrefs.setRoomEnabled(context, room.id, enabled)
                                rooms = AppPrefs.getRooms(context)
                                triggerImmediateMonitorIfRunning()
                            },
                            onScheduleEnabledChange = { enabled ->
                                AppPrefs.setRoomSchedule(
                                    context = context,
                                    roomId = room.id,
                                    enabled = enabled,
                                    startMinutes = room.monitorWindowStartMinutes,
                                    endMinutes = room.monitorWindowEndMinutes
                                )
                                rooms = AppPrefs.getRooms(context)
                                triggerImmediateMonitorIfRunning()
                            },
                            onScheduleStartClick = {
                                activeRoomTimePicker = RoomSchedulePickerTarget(room.id, SchedulePickerTarget.START)
                            },
                            onScheduleEndClick = {
                                activeRoomTimePicker = RoomSchedulePickerTarget(room.id, SchedulePickerTarget.END)
                            },
                            onCheck = runRoomCheck,
                            onDelete = {
                                AppPrefs.removeRoom(context, room.id)
                                val latest = AppPrefs.getRooms(context)
                                RuntimeStateStore.removeMissing(latest.map { it.id }.toSet())
                                rooms = latest
                                triggerImmediateMonitorIfRunning()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    running: Boolean,
    settings: AppSettings,
    rooms: List<MonitoredRoom>,
    runtimeStates: Map<String, RoomRuntimeState>,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    val palette = statusPalette()
    val enabledRooms = rooms.filter { it.enabled }
    val recordingCount = enabledRooms.count { runtimeStates[it.id]?.status == RoomStatus.RECORDING }
    val liveCount = enabledRooms.count { runtimeStates[it.id]?.status == RoomStatus.LIVE }
    val offlineCount = (enabledRooms.size - recordingCount - liveCount).coerceAtLeast(0)
    val windowText = if (settings.scheduleEnabled) {
        MonitorWindow.formatMinutes(settings.monitorWindowStartMinutes) +
            " - " +
            MonitorWindow.formatMinutes(settings.monitorWindowEndMinutes)
    } else {
        null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            if (running) MaterialTheme.colorScheme.primary else palette.heroStoppedBg
                        )
                        .clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center
                ) {
                    if (running) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = AppText.startMonitoring(context),
                            tint = palette.heroStoppedFg,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (running) AppText.heroRunning(context) else AppText.heroStopped(context),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (running) {
                            AppText.heroRunningSub(context, settings.checkIntervalSeconds, windowText)
                        } else {
                            AppText.heroStoppedSub(context)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (running) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp, bottom = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HeroStat(
                        dotColor = MaterialTheme.colorScheme.primary,
                        text = AppText.statRecording(context, recordingCount),
                        textColor = palette.statRecording
                    )
                    HeroStat(
                        dotColor = palette.live.ring,
                        text = AppText.statLive(context, liveCount),
                        textColor = palette.statLive
                    )
                    HeroStat(
                        dotColor = palette.dotOffline,
                        text = AppText.statOffline(context, offlineCount),
                        textColor = palette.statOffline
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStat(dotColor: Color, text: String, textColor: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = textColor
        )
    }
}

@Composable
private fun StatusChip(
    text: String,
    style: StatusStyle,
    showDot: Boolean
) {
    Surface(
        shape = CircleShape,
        color = style.chipBg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(style.ring)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = style.chipFg,
                maxLines = 1
            )
        }
    }
}

private fun calculateDragTargetOffset(totalDragPx: Float, stepPx: Float, thresholdPx: Float): Int {
    if (stepPx <= 0f) {
        return 0
    }

    return when {
        totalDragPx >= thresholdPx -> ((totalDragPx - thresholdPx) / stepPx).toInt() + 1
        totalDragPx <= -thresholdPx -> -(((-totalDragPx - thresholdPx) / stepPx).toInt() + 1)
        else -> 0
    }
}

private fun constrainDraggedCardOffset(offsetPx: Float, currentIndex: Int, lastIndex: Int): Float {
    return when {
        currentIndex <= 0 -> 0f
        currentIndex >= lastIndex -> 0f
        else -> offsetPx
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoomCard(
    modifier: Modifier = Modifier,
    room: MonitoredRoom,
    runtime: RoomRuntimeState?,
    index: Int,
    lastIndex: Int,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    onDragStateChange: (Boolean) -> Unit,
    onMoveTo: (Int) -> Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onScheduleEnabledChange: (Boolean) -> Unit,
    onScheduleStartClick: () -> Unit,
    onScheduleEndClick: () -> Unit,
    onCheck: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val palette = statusPalette()
    val nowMs = rememberTickerNow()

    val roomIdFallback = RoomInputNormalizer.extractLiveRoomId(room.normalizedInput)
    val displayId = room.douyinId ?: RoomInputNormalizer.extractIdHint(room.input) ?: roomIdFallback ?: room.input
    val displayTitle = room.displayName ?: RoomInputNormalizer.extractDisplayHint(room.input) ?: displayId
    val saveProgressPercent = runtime?.saveProgressPercent
    val status = runtime?.status ?: RoomStatus.OFFLINE

    val statusStyle = when {
        !room.enabled -> palette.neutral
        saveProgressPercent != null -> palette.saving
        status == RoomStatus.RECORDING -> palette.recording
        status == RoomStatus.LIVE -> palette.live
        status == RoomStatus.ERROR -> palette.recording
        else -> palette.neutral
    }

    val chipText = when {
        !room.enabled -> AppText.disabled(context)
        saveProgressPercent != null -> AppText.savingLabel(context)
        status == RoomStatus.RECORDING && runtime?.startedAtMs != null ->
            "REC " + formatElapsedDuration(nowMs - runtime.startedAtMs)
        else -> AppText.statusLabel(context, status)
    }
    val chipShowsDot = room.enabled && saveProgressPercent == null &&
        (status == RoomStatus.RECORDING || status == RoomStatus.LIVE)

    val statusLabelText = AppText.statusLabel(context, status)
    val messageText = runtime?.message
        ?.takeIf { it.isNotBlank() }
        ?.takeUnless { msg ->
            // Avoid sublines like "Checking" under a chip that already says "Checking".
            msg.trim().trimEnd('.', '…', '。').equals(
                statusLabelText.trim().trimEnd('.', '…', '。'),
                ignoreCase = true
            )
        }
    val lastCheckText = runtime?.lastCheckedAtMs
        ?.takeIf { it > 0L }
        ?.let { AppText.lastCheck(context, DateFormat.format("HH:mm:ss", it)) }
    val subText = messageText ?: lastCheckText ?: displayId

    val roomWindowStart = MonitorWindow.formatMinutes(room.monitorWindowStartMinutes)
    val roomWindowEnd = MonitorWindow.formatMinutes(room.monitorWindowEndMinutes)

    var dragOffsetPx by remember(room.id) { mutableStateOf(0f) }
    var isDragging by remember(room.id) { mutableStateOf(false) }
    var cardHeightPx by remember(room.id) { mutableStateOf(0) }
    var totalDragPx by remember(room.id) { mutableStateOf(0f) }
    var dragStartIndex by remember(room.id) { mutableStateOf(index) }
    var currentIndex by remember(room.id) { mutableStateOf(index) }
    val itemStepPx = with(LocalDensity.current) {
        if (cardHeightPx > 0) cardHeightPx.toFloat() + 8.dp.toPx() else 84.dp.toPx()
    }
    val minimumThresholdPx = with(LocalDensity.current) { 36.dp.toPx() }
    val latestIndex = rememberUpdatedState(index)
    val latestLastIndex = rememberUpdatedState(lastIndex)
    val latestOnMoveTo = rememberUpdatedState(onMoveTo)
    val latestOnDragStateChange = rememberUpdatedState(onDragStateChange)
    val latestItemStepPx = rememberUpdatedState(itemStepPx)
    val latestThresholdPx = rememberUpdatedState(minimumThresholdPx)

    LaunchedEffect(index, isDragging) {
        if (!isDragging) {
            dragStartIndex = index
            currentIndex = index
            totalDragPx = 0f
            dragOffsetPx = 0f
        } else {
            currentIndex = index
        }
    }

    fun finishDrag() {
        totalDragPx = 0f
        dragOffsetPx = 0f
        dragStartIndex = latestIndex.value
        currentIndex = latestIndex.value
        isDragging = false
        latestOnDragStateChange.value(false)
    }

    val borderColor = when {
        room.enabled && saveProgressPercent == null && status == RoomStatus.RECORDING -> palette.recordingCardBorder
        expanded -> palette.expandedCardBorder
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        onClick = onExpandToggle,
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (room.enabled) 1f else 0.55f)
            .onSizeChanged { cardHeightPx = it.height }
            .pointerInput(room.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        totalDragPx = 0f
                        dragOffsetPx = 0f
                        dragStartIndex = latestIndex.value
                        currentIndex = latestIndex.value
                        isDragging = true
                        latestOnDragStateChange.value(true)
                    },
                    onDragEnd = {
                        finishDrag()
                    },
                    onDragCancel = {
                        finishDrag()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragPx += dragAmount.y

                        val stepPx = latestItemStepPx.value
                        val reorderThresholdPx = max(latestThresholdPx.value, stepPx * 0.28f)
                        val targetOffset = calculateDragTargetOffset(totalDragPx, stepPx, reorderThresholdPx)
                        val targetIndex = (dragStartIndex + targetOffset).coerceIn(0, latestLastIndex.value)
                        if (targetIndex != currentIndex && latestOnMoveTo.value(targetIndex)) {
                            currentIndex = targetIndex
                        }

                        val relativeOffsetPx = totalDragPx - ((currentIndex - dragStartIndex) * stepPx)
                        dragOffsetPx = constrainDraggedCardOffset(
                            offsetPx = relativeOffsetPx,
                            currentIndex = currentIndex,
                            lastIndex = latestLastIndex.value
                        )
                    }
                )
            }
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffsetPx
                scaleX = if (isDragging) 1.01f else 1f
                scaleY = if (isDragging) 1.01f else 1f
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 10.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoomAvatar(
                    avatarUrl = room.avatarUrl,
                    fallbackText = displayTitle,
                    style = statusStyle,
                    showRing = room.enabled
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                StatusChip(
                    text = chipText,
                    style = statusStyle,
                    showDot = chipShowsDot
                )
            }

            if (saveProgressPercent != null) {
                LinearProgressIndicator(
                    progress = { saveProgressPercent.coerceIn(0, 100) / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    color = palette.progressFill,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = AppText.saveProgress(context, saveProgressPercent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (!runtime?.recordingPath.isNullOrBlank()) {
                Text(
                    text = runtime?.recordingPath.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (expanded) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = AppText.roomScheduleEnabled(context),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = room.scheduleEnabled,
                        onCheckedChange = onScheduleEnabledChange
                    )
                }

                if (room.scheduleEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScheduleTimeField(
                            label = AppText.scheduleStartLabel(context),
                            value = roomWindowStart,
                            modifier = Modifier.weight(1f),
                            onClick = onScheduleStartClick
                        )
                        ScheduleTimeField(
                            label = AppText.scheduleEndLabel(context),
                            value = roomWindowEnd,
                            modifier = Modifier.weight(1f),
                            onClick = onScheduleEndClick
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = room.enabled,
                        onCheckedChange = onToggleEnabled
                    )
                    Text(
                        text = if (room.enabled) AppText.enabled(context) else AppText.disabled(context),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onCheck) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = AppText.check(context),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = AppText.delete(context),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomAvatar(
    avatarUrl: String?,
    fallbackText: String,
    style: StatusStyle,
    showRing: Boolean
) {
    val ringModifier = if (showRing) {
        Modifier.border(2.dp, style.ring, CircleShape)
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .size(42.dp)
            .then(ringModifier)
            .padding(if (showRing) 3.dp else 0.dp)
            .clip(CircleShape)
            .background(style.avatarBg),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Room avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            val char = fallbackText.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            Text(
                text = char,
                style = MaterialTheme.typography.titleMedium,
                color = style.avatarFg
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onUpdateSettings: (AppSettings, Boolean) -> Unit,
    onPickFolder: () -> Unit,
    onOpenTimePicker: (SchedulePickerTarget) -> Unit
) {
    val context = LocalContext.current
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showBitrateDialog by remember { mutableStateOf(false) }
    var showOutputDialog by remember { mutableStateOf(false) }
    var showStorageDialog by remember { mutableStateOf(false) }

    if (showIntervalDialog) {
        IntervalDialog(
            currentSeconds = settings.checkIntervalSeconds,
            onDismiss = { showIntervalDialog = false },
            onConfirm = { seconds ->
                onUpdateSettings(settings.copy(checkIntervalSeconds = seconds), false)
                showIntervalDialog = false
            }
        )
    }

    if (showQualityDialog) {
        OptionDialog(
            title = AppText.qualityLabel(context),
            options = RecordQuality.entries,
            selected = settings.quality,
            optionLabel = { AppText.qualityOption(context, it) },
            onSelect = {
                onUpdateSettings(settings.copy(quality = it), false)
                showQualityDialog = false
            },
            onDismiss = { showQualityDialog = false }
        )
    }

    if (showBitrateDialog) {
        OptionDialog(
            title = AppText.bitrateLabel(context),
            options = BitratePreset.entries,
            selected = settings.bitrate,
            optionLabel = { AppText.bitrateOption(context, it) },
            onSelect = {
                onUpdateSettings(settings.copy(bitrate = it), false)
                showBitrateDialog = false
            },
            onDismiss = { showBitrateDialog = false }
        )
    }

    if (showOutputDialog) {
        OptionDialog(
            title = AppText.outputFilesLabel(context),
            options = SaveOutputMode.entries,
            selected = settings.saveOutputMode,
            optionLabel = { mode ->
                when (mode) {
                    SaveOutputMode.RAW_ONLY -> AppText.rawMp4OnlyLabel(context)
                    SaveOutputMode.RAW_AND_DANMU -> AppText.rawAndDanmuLabel(context)
                }
            },
            onSelect = {
                onUpdateSettings(settings.copy(saveOutputMode = it), false)
                showOutputDialog = false
            },
            onDismiss = { showOutputDialog = false }
        )
    }

    if (showStorageDialog) {
        OptionDialog(
            title = AppText.storageLabel(context),
            options = StorageMode.entries,
            selected = settings.storageMode,
            optionLabel = { AppText.storageMode(context, it) },
            onSelect = {
                onUpdateSettings(settings.copy(storageMode = it), false)
                showStorageDialog = false
            },
            onDismiss = { showStorageDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = AppText.settingsTab(context),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        SectionLabel(AppText.sectionMonitor(context))
        SettingsGroup {
            SettingRow(
                label = AppText.checkIntervalLabel(context),
                value = AppText.intervalValue(context, settings.checkIntervalSeconds),
                onClick = { showIntervalDialog = true }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SwitchRow(
                label = AppText.scheduleEnabled(context),
                checked = settings.scheduleEnabled,
                onCheckedChange = { enabled ->
                    onUpdateSettings(settings.copy(scheduleEnabled = enabled), true)
                }
            )
            if (settings.scheduleEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScheduleTimeField(
                        label = AppText.scheduleStartLabel(context),
                        value = MonitorWindow.formatMinutes(settings.monitorWindowStartMinutes),
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenTimePicker(SchedulePickerTarget.START) }
                    )
                    ScheduleTimeField(
                        label = AppText.scheduleEndLabel(context),
                        value = MonitorWindow.formatMinutes(settings.monitorWindowEndMinutes),
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenTimePicker(SchedulePickerTarget.END) }
                    )
                }
            }
        }

        SectionLabel(AppText.sectionRecording(context))
        SettingsGroup {
            SettingRow(
                label = AppText.qualityLabel(context),
                value = AppText.qualityOption(context, settings.quality),
                onClick = { showQualityDialog = true }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingRow(
                label = AppText.bitrateLabel(context),
                value = AppText.bitrateOption(context, settings.bitrate),
                onClick = { showBitrateDialog = true }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingRow(
                label = AppText.outputFilesLabel(context),
                value = when (settings.saveOutputMode) {
                    SaveOutputMode.RAW_ONLY -> AppText.rawMp4OnlyLabel(context)
                    SaveOutputMode.RAW_AND_DANMU -> AppText.rawAndDanmuLabel(context)
                },
                onClick = { showOutputDialog = true }
            )
        }

        SectionLabel(AppText.sectionStorage(context))
        SettingsGroup {
            SettingRow(
                label = AppText.storageLabel(context),
                value = AppText.storageMode(context, settings.storageMode),
                onClick = { showStorageDialog = true }
            )
            if (settings.storageMode == StorageMode.DOCUMENT_TREE) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onPickFolder,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(AppText.selectFolder(context))
                    }
                    Text(
                        text = settings.storageTreeUri ?: AppText.noFolderSelected(context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        SectionLabel(AppText.sectionAlerts(context))
        SettingsGroup {
            SwitchRow(
                label = AppText.liveCueLabel(context),
                checked = settings.liveCueEnabled,
                onCheckedChange = { enabled ->
                    onUpdateSettings(settings.copy(liveCueEnabled = enabled), false)
                    if (enabled) {
                        SystemCuePlayer.playRecordingStarted(context)
                    }
                }
            )
            Text(
                text = AppText.liveCueHint(context),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun <T> OptionDialog(
    title: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(option) }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = null
                        )
                        Text(
                            text = optionLabel(option),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = AppText.cancel(context))
            }
        }
    )
}

@Composable
private fun IntervalDialog(
    currentSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf(currentSeconds.toString()) }
    val presets = listOf(15, 30, 45, 60, 90, 120, 300)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = AppText.checkIntervalLabel(context)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter(Char::isDigit).take(3) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    placeholder = { Text("45") }
                )
                Text(
                    text = AppText.intervalDialogHint(context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = input.toIntOrNull() == preset,
                            onClick = { input = preset.toString() },
                            label = { Text(AppText.intervalValue(context, preset)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = input.toIntOrNull()
                    if (parsed == null) {
                        Toast.makeText(context, AppText.invalidInterval(context), Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    onConfirm(
                        parsed.coerceIn(
                            AppPrefs.MIN_CHECK_INTERVAL_SECONDS,
                            AppPrefs.MAX_CHECK_INTERVAL_SECONDS
                        )
                    )
                }
            ) {
                Text(text = AppText.confirm(context))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = AppText.cancel(context))
            }
        }
    )
}

@Composable
private fun ScheduleTimeField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = value)
        }
    }
}

@Composable
private fun WheelTimePickerDialog(
    title: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val context = LocalContext.current
    var hour by remember(initialMinutes) { mutableStateOf((initialMinutes / 60).coerceIn(0, 23)) }
    var minute by remember(initialMinutes) { mutableStateOf((initialMinutes % 60).coerceIn(0, 59)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WheelNumberPicker(
                    value = hour,
                    range = 0..23,
                    formatter = { String.format("%02d", it) },
                    modifier = Modifier
                        .weight(1f)
                        .height(160.dp),
                    onValueChange = { hour = it }
                )
                Text(text = ":", style = MaterialTheme.typography.titleLarge)
                WheelNumberPicker(
                    value = minute,
                    range = 0..59,
                    formatter = { String.format("%02d", it) },
                    modifier = Modifier
                        .weight(1f)
                        .height(160.dp),
                    onValueChange = { minute = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hour * 60 + minute) }) {
                Text(text = context.getString(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = context.getString(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun WheelNumberPicker(
    value: Int,
    range: IntRange,
    modifier: Modifier = Modifier,
    formatter: (Int) -> String,
    onValueChange: (Int) -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            NumberPicker(context).apply {
                minValue = range.first
                maxValue = range.last
                wrapSelectorWheel = true
                descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                setFormatter { formatter(it) }
                this.value = value
                setOnValueChangedListener { _, _, newValue ->
                    onValueChange(newValue)
                }
            }
        },
        update = { picker ->
            picker.minValue = range.first
            picker.maxValue = range.last
            picker.wrapSelectorWheel = true
            picker.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
            picker.setFormatter { formatter(it) }
            if (picker.value != value) {
                picker.value = value
            }
            picker.setOnValueChangedListener { _, _, newValue ->
                onValueChange(newValue)
            }
        }
    )
}

private fun selectPreviewStreamUrl(
    streamUrls: Map<String, String>,
    quality: RecordQuality,
    bitrate: BitratePreset
): String? {
    return StreamSelector.primaryUrl(streamUrls, quality, bitrate)
}

@Composable
private fun rememberTickerNow(): Long {
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }
    return nowMs
}

private fun formatElapsedDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
