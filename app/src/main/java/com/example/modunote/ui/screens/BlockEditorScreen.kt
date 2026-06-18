package com.example.modunote.ui.screens

import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.modunote.CryptoManager
import com.example.modunote.data.local.Block
import com.example.modunote.data.local.BlockType
import com.example.modunote.data.local.NoteTemplateViewModel
import com.example.modunote.data.local.NoteViewModel
import com.example.modunote.data.local.TagViewModel
import com.example.modunote.model.ui.SLASH_COMMANDS
import com.example.modunote.ui.blocks.BlockItem
import com.example.modunote.ui.blocks.ReminderDialogBlock
import com.example.modunote.ui.theme.LocalEnergySavingActive
import com.example.modunote.ui.theme.md_theme_light_background
import com.example.modunote.ui.theme.md_theme_light_onSurface
import com.example.modunote.ui.theme.md_theme_light_onSurfaceVariant
import com.example.modunote.ui.theme.md_theme_light_surfaceContainerHigh
import com.example.modunote.ui.theme.md_theme_link_color
import com.example.modunote.utils.BlockSerializer
import com.example.modunote.utils.printNote
import com.example.modunote.utils.shareNote
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.sqrt


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockEditorScreen(
    noteId: Int,
    noteViewModel: NoteViewModel,
    noteTemplateViewModel: NoteTemplateViewModel,
    tagViewModel: TagViewModel,
    onBack: () -> Unit,
    onNavigateTo: (Int) -> Unit = {},
    onNavigateToHome: () -> Unit = {}
) {
    val note by noteViewModel.getNoteById(noteId).collectAsState(initial = null)
    val subNotes by noteViewModel.getNotesByParent(noteId).collectAsState(initial = emptyList())
    val breadcrumbs by noteViewModel.getBreadcrumbs(noteId).collectAsState(initial = emptyList())
    val allNotes by noteViewModel.allNotes.collectAsState(initial = emptyList())
    val authenticatedNoteIds by noteViewModel.authenticatedNoteIds.collectAsState()
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val scope = rememberCoroutineScope()
    val energySaving = LocalEnergySavingActive.current

    // Editor state
    var title by remember { mutableStateOf("") }
    var blocks by remember { mutableStateOf(listOf<Block>()) }
    var initialized by remember { mutableStateOf(false) }
    var isAuthenticated by remember { mutableStateOf(false) }
    var lastSavedTitle by remember { mutableStateOf("") }
    var lastSavedJson by remember { mutableStateOf("") }
    var gesturesEnabled by remember { mutableStateOf(true) }

    // Focus tracking
    var focusedBlockId by remember { mutableStateOf<String?>(null) }
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val titleFocusRequester = remember { FocusRequester() }

    // Slash command state
    var slashBlockId by remember { mutableStateOf<String?>(null) }
    var slashQuery by remember { mutableStateOf("") }

    // Drag reorder state
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dropIndex by remember { mutableIntStateOf(-1) }

    // Dialogs & Menu
    var showReminderDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val timeFmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm") }

    // Init from note
    LaunchedEffect(note, authenticatedNoteIds) {
        note?.let { n ->
            isAuthenticated = !n.isLocked || authenticatedNoteIds.contains(noteId)
            if (!initialized && isAuthenticated) {
                initialized = true
                title = n.title
                val content = if (n.isLocked && n.content.startsWith("ENC:")) {
                    runCatching { CryptoManager.decrypt(n.content.removePrefix("ENC:")) }.getOrElse { "" }
                } else n.content
                blocks = BlockSerializer.parse(content)
                lastSavedTitle = n.title
                lastSavedJson = BlockSerializer.toJson(blocks)
                if (n.title.isBlank()) runCatching { titleFocusRequester.requestFocus() }
            }
        }
    }

    // Auto-save
    val blocksSnapshot = blocks
    LaunchedEffect(title, blocksSnapshot, energySaving) {
        val saveDelay = if (energySaving) 4000L else 800L
        delay(saveDelay)
        note?.let { n ->
            val json = BlockSerializer.toJson(blocksSnapshot)
            if (title == lastSavedTitle && json == lastSavedJson) return@let
            val toSave = if (n.isLocked) "ENC:${runCatching { CryptoManager.encrypt(json) }.getOrElse { json }}" else json
            noteViewModel.updateNoteWithTags(n.copy(title = title, content = toSave))
            lastSavedTitle = title
            lastSavedJson = json
        }
    }

    // Helpers
    fun focusBlock(id: String) = scope.launch { delay(50); focusRequesters[id]?.requestFocus() }

    fun addBlockAfter(afterId: String?, type: BlockType = BlockType.PARAGRAPH, text: String = ""): String {
        val new = Block(type = type, text = text)
        blocks = if (afterId == null) {
            blocks + new
        } else {
            val idx = blocks.indexOfFirst { it.id == afterId }.coerceAtLeast(0)
            blocks.toMutableList().also { it.add(idx + 1, new) }
        }
        return new.id
    }

    fun deleteBlock(id: String) {
        if (blocks.size <= 1) { blocks = listOf(Block()); return }
        val idx = blocks.indexOfFirst { it.id == id }
        blocks = blocks.filter { it.id != id }
        val prevId = blocks.getOrNull((idx - 1).coerceAtLeast(0))?.id
        prevId?.let { focusBlock(it) }
    }

    fun updateBlock(id: String, transform: (Block) -> Block) {
        blocks = blocks.map { if (it.id == id) transform(it) else it }
    }

    fun convertBlock(id: String, type: BlockType) {
        updateBlock(id) { it.copy(
            type = type,
            text = if (type == BlockType.DIVIDER || type == BlockType.MAP || type == BlockType.LINK) "" else it.text,
            url = "",
            latitude = if (type == BlockType.MAP) 52.2297 else null, // Default to Warsaw
            longitude = if (type == BlockType.MAP) 21.0122 else null
        ) }
        slashBlockId = null; slashQuery = ""
        if (type != BlockType.DIVIDER && type != BlockType.MAP) focusBlock(id)
    }

    fun handleBlockEnter(id: String, before: String, after: String) {
        updateBlock(id) { it.copy(text = before) }
        // For TODO/BULLET/NUMBERED: next block keeps same type if before is not empty, else converts to PARAGRAPH
        val currentType = blocks.firstOrNull { it.id == id }?.type ?: BlockType.PARAGRAPH
        val nextType = when {
            before.isEmpty() && currentType in listOf(BlockType.BULLET, BlockType.NUMBERED, BlockType.TODO) -> BlockType.PARAGRAPH
            currentType in listOf(BlockType.BULLET, BlockType.NUMBERED, BlockType.TODO) -> currentType
            else -> BlockType.PARAGRAPH
        }
        val newId = addBlockAfter(id, nextType, after)
        focusBlock(newId)
    }

    // Filtered slash commands
    val filteredSlash = remember(slashQuery) {
        if (slashQuery.isEmpty()) SLASH_COMMANDS
        else SLASH_COMMANDS.filter { cmd ->
            cmd.trigger.contains(slashQuery, ignoreCase = true) ||
            cmd.label.contains(slashQuery, ignoreCase = true)
        }
    }

    // Dialogs
    if (showReminderDialog) {
        ReminderDialogBlock(
            currentTime = note?.reminderTime,
            currentEnabled = note?.reminderEnabled ?: false,
            onDismiss = { showReminderDialog = false },
            onSave = { ms, enabled ->
                note?.let { noteViewModel.setReminder(it.id, ms, enabled, context) }
                showReminderDialog = false
            }
        )
    }

    if (showTemplateDialog) {
        var tName by remember { mutableStateOf(title) }
        AlertDialog(
            onDismissRequest = { showTemplateDialog = false },
            icon = { Icon(Icons.Default.ContentCopy, null) },
            title = { Text("Zapisz jako szablon") },
            text = {
                OutlinedTextField(tName, { tName = it }, label = { Text("Nazwa szablonu") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    note?.let { noteTemplateViewModel.saveTemplate(tName.ifBlank { title }, it.id) }
                    showTemplateDialog = false
                }, enabled = tName.isNotBlank()) { Text("Zapisz") }
            },
            dismissButton = { TextButton(onClick = { showTemplateDialog = false }) { Text("Anuluj") } }
        )
    }

    Scaffold(
        containerColor = md_theme_light_background,
        topBar = {
            Column(modifier = Modifier.background(md_theme_light_background)) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, "WiÄ™cej opcji", tint = md_theme_light_onSurfaceVariant)
                            }
                            val itemsVisible = remember(showMenu) { List(6) { mutableStateOf(false) } }
                            LaunchedEffect(showMenu) {
                                if (showMenu) {
                                    itemsVisible.forEachIndexed { i, state ->
                                        delay(i * 55L)
                                        state.value = true
                                    }
                                } else { itemsVisible.forEach { it.value = false } }
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                shape = RoundedCornerShape(20.dp),
                                tonalElevation = 4.dp,
                                shadowElevation = 12.dp
                            ) {
                                Spacer(Modifier.height(8.dp))

                                // Grupa 1: Przypomnienie + Szablon
                                AnimatedVisibility(
                                    visible = itemsVisible[0].value || itemsVisible[1].value,
                                    enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { -20 }
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        tonalElevation = 8.dp,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        Column {
                                            AnimatedVisibility(visible = itemsVisible[0].value, enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { -10 }) {
                                                DropdownMenuItem(
                                                    leadingIcon = {
                                                        Icon(Icons.Default.Alarm, null,
                                                            tint = if (note?.reminderEnabled == true) md_theme_link_color else md_theme_light_onSurfaceVariant)
                                                    },
                                                    text = { Text(if (note?.reminderEnabled == true) "Edytuj przypomnienie" else "Dodaj przypomnienie") },
                                                    onClick = { showMenu = false; showReminderDialog = true }
                                                )
                                            }
                                            AnimatedVisibility(visible = itemsVisible[1].value, enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { -10 }) {
                                                DropdownMenuItem(
                                                    leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = md_theme_light_onSurfaceVariant) },
                                                    text = { Text("Zapisz jako szablon") },
                                                    onClick = { showMenu = false; showTemplateDialog = true }
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                // â•â•â• Grupa 2: Blokada + Przypinanie â•â•â•â•â•â•â•â•â•
                                AnimatedVisibility(
                                    visible = itemsVisible[2].value || itemsVisible[3].value,
                                    enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { -20 }
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        tonalElevation = 8.dp,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        Column {
                                            AnimatedVisibility(visible = itemsVisible[2].value, enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { -10 }) {
                                                DropdownMenuItem(
                                                    leadingIcon = {
                                                        Icon(if (note?.isLocked == true) Icons.Default.Lock else Icons.Default.LockOpen, null,
                                                            tint = if (note?.isLocked == true) md_theme_link_color else md_theme_light_onSurfaceVariant)
                                                    },
                                                    text = { Text(if (note?.isLocked == true) "Odblokuj notatkę" else "Zablokuj notatkę") },
                                                    onClick = {
                                                        showMenu = false
                                                        note?.let { n ->
                                                            val json = BlockSerializer.toJson(blocks)
                                                            val newLocked = !n.isLocked
                                                            val toSave = if (newLocked) "ENC:${runCatching { CryptoManager.encrypt(json) }.getOrElse { json }}" else json
                                                            noteViewModel.updateNoteWithTags(n.copy(isLocked = newLocked, content = toSave, title = title))
                                                            if (newLocked) noteViewModel.markAuthenticated(n.id)
                                                            lastSavedJson = json; lastSavedTitle = title
                                                        }
                                                    }
                                                )
                                            }
                                            AnimatedVisibility(visible = itemsVisible[3].value, enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { -10 }) {
                                                DropdownMenuItem(
                                                    leadingIcon = {
                                                        Icon(Icons.Default.PushPin, null,
                                                            tint = if (note?.isPinned == true) md_theme_link_color else md_theme_light_onSurfaceVariant)
                                                    },
                                                    text = { Text(if (note?.isPinned == true) "Odepnij notatkę" else "Przypnij notatkę") },
                                                    onClick = {
                                                        showMenu = false
                                                        note?.let { noteViewModel.updateNoteWithTags(it.copy(isPinned = !it.isPinned)) }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                // Grupa 3: Drukowanie
                                AnimatedVisibility(
                                    visible = itemsVisible[4].value || itemsVisible[5].value,
                                    enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { -20 }
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        tonalElevation = 8.dp,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        Column {
                                            AnimatedVisibility(visible = itemsVisible[4].value, enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { -10 }) {
                                                DropdownMenuItem(
                                                    leadingIcon = { Icon(Icons.Default.Print, null, tint = md_theme_light_onSurfaceVariant) },
                                                    text = { Text("Drukuj notatkę") },
                                                    onClick = {
                                                        showMenu = false
                                                        printNote(context, title.ifBlank { "Notatka" }, blocks)
                                                    }
                                                )
                                            }
                                            AnimatedVisibility(visible = itemsVisible[5].value, enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { -10 }) {
                                                DropdownMenuItem(
                                                    leadingIcon = { Icon(Icons.Default.Share, null, tint = md_theme_light_onSurfaceVariant) },
                                                    text = { Text("Udostępnij notatkę") },
                                                    onClick = {
                                                        showMenu = false
                                                        shareNote(context, title.ifBlank { "Notatka" }, blocks)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = md_theme_light_background)
                )
                if (note?.reminderEnabled == true && note?.reminderTime != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).clickable { showReminderDialog = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Alarm, null, Modifier.size(13.dp), tint = md_theme_link_color)
                        Spacer(Modifier.width(4.dp))
                        Text("Przypomnienie: ${timeFmt.format(Date(note!!.reminderTime!!))}", fontSize = 11.sp, color = md_theme_link_color)
                    }
                }
                // Breadcrumbs
                if (breadcrumbs.size > 1) {
                    LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        itemsIndexed(breadcrumbs) { idx, pathNote ->
                            Text(
                                text = pathNote.title.ifBlank { "-" },
                                fontSize = 12.sp,
                                color = if (idx == breadcrumbs.lastIndex) md_theme_light_onSurface else md_theme_light_onSurfaceVariant,
                                modifier = if (idx < breadcrumbs.lastIndex) Modifier.clickable { onNavigateTo(pathNote.id) } else Modifier
                            )
                            if (idx < breadcrumbs.lastIndex) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(13.dp), tint = Color.LightGray)
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            note == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }

            note!!.isLocked && !isAuthenticated -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = md_theme_light_onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("Notatka chroniona", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        BiometricPrompt(activity, ContextCompat.getMainExecutor(context),
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) {
                                    noteViewModel.markAuthenticated(noteId)
                                }
                            }
                        ).authenticate(BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Odblokuj notatkę").setNegativeButtonText("Anuluj").build())
                    }) {
                        Icon(Icons.Default.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text("Odblokuj")
                    }
                }
            }

            else -> {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(scrollState)
                        .imePadding()
                        .pointerInput(noteId, gesturesEnabled) {
                            if (!gesturesEnabled) return@pointerInput
                            awaitEachGesture {
                                var accumulatedZoom = 1f
                                var gestureTriggered = false
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val activePointers = event.changes.filter { it.pressed && !it.isConsumed }
                                    if (activePointers.size >= 2) {
                                        event.changes.forEach { it.consume() }
                                        val p1 = activePointers[0]
                                        val p2 = activePointers[1]
                                        val prevDx = p1.previousPosition.x - p2.previousPosition.x
                                        val prevDy = p1.previousPosition.y - p2.previousPosition.y
                                        val prevDist = sqrt(prevDx * prevDx + prevDy * prevDy)
                                        val currDx = p1.position.x - p2.position.x
                                        val currDy = p1.position.y - p2.position.y
                                        val currDist = sqrt(currDx * currDx + currDy * currDy)
                                        if (prevDist > 0f && currDist > 0f) {
                                            val zoom = currDist / prevDist
                                            accumulatedZoom *= zoom
                                            if (accumulatedZoom < 0.7f && !gestureTriggered) {
                                                gestureTriggered = true
                                                val parentId = note?.parentId
                                                if (parentId != null) {
                                                    onNavigateTo(parentId)
                                                } else {
                                                    onNavigateToHome()
                                                }
                                            }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                ) {
                    // Page title
                    BasicTextField(
                        value = title,
                        onValueChange = { if (!it.contains('\n')) title = it },
                        textStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = md_theme_light_onSurface),
                        cursorBrush = SolidColor(md_theme_link_color),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .focusRequester(titleFocusRequester),
                        decorationBox = { inner ->
                            if (title.isEmpty()) Text("Tytuł strony", style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.LightGray))
                            inner()
                        }
                    )

                    // Sub-notes preview
                    var subNotesExpanded by remember { mutableStateOf(true) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .clickable { subNotesExpanded = !subNotesExpanded }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (subNotesExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = md_theme_light_onSurfaceVariant
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Podstrony",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = md_theme_light_onSurface
                                )
                                if (subNotes.isNotEmpty()) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(md_theme_light_surfaceContainerHigh, CircleShape)
                                            .padding(horizontal = 6.dp, vertical = 1.5.dp)
                                    ) {
                                        Text(
                                            text = subNotes.size.toString(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = md_theme_light_onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            TextButton(
                                onClick = {
                                    noteViewModel.insertNote("", "", parentId = noteId) { newId ->
                                        onNavigateTo(newId.toInt())
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("Dodaj", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        AnimatedVisibility(
                            visible = subNotesExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                if (subNotes.isEmpty()) {
                                    Text(
                                        text = "Brak podstron. Kliknij 'Dodaj', aby utworzyć nową.",
                                        fontSize = 12.sp,
                                        color = md_theme_light_onSurfaceVariant.copy(alpha = 0.6f),
                                        fontStyle = FontStyle.Italic,
                                        modifier = Modifier.padding(top = 4.dp, start = 22.dp)
                                    )
                                } else {
                                    Spacer(Modifier.height(4.dp))
                                    val chunkedSubNotes = remember(subNotes) { subNotes.chunked(2) }
                                    Column(
                                        modifier = Modifier.padding(start = 22.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        chunkedSubNotes.forEach { rowItems ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                rowItems.forEach { subNote ->
                                                    Row(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clickable { onNavigateTo(subNote.id) }
                                                            .padding(vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Description,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(14.dp),
                                                            tint = md_theme_light_onSurfaceVariant.copy(alpha = 0.7f)
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(
                                                            text = subNote.title.ifBlank { "Bez tytułu" },
                                                            fontSize = 13.sp,
                                                            color = md_theme_light_onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                if (rowItems.size < 2) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = md_theme_light_surfaceContainerHigh, modifier = Modifier.padding(horizontal = 20.dp))
                    Spacer(Modifier.height(8.dp))

                    // Blocks
                    blocks.forEachIndexed { index, block ->
                        key(block.id) {
                            val fr = remember { FocusRequester() }.also { focusRequesters[block.id] = it }
                            val isFocused = focusedBlockId == block.id
                            val isDragging = draggedIndex == index
                            val isDropTarget = dropIndex == index && draggedIndex != index

                            BlockItem(
                                block = block,
                                numberedIndex = blocks.filter { it.type == BlockType.NUMBERED }.indexOf(block) + 1,
                                isFocused = isFocused,
                                isDragging = isDragging,
                                isDropTarget = isDropTarget,
                                dragOffsetY = if (isDragging) dragOffsetY else 0f,
                                focusRequester = fr,
                                showSlashMenu = slashBlockId == block.id,
                                filteredSlash = filteredSlash,
                                onUpdateBlock = { updatedBlock -> updateBlock(block.id) { updatedBlock } },
                                onFocus = { focusedBlockId = block.id },
                                onTextChange = { newText ->
                                    when {
                                        newText.contains('\n') -> {
                                            val parts = newText.split('\n', limit = 2)
                                            handleBlockEnter(block.id, parts[0], parts.getOrElse(1) { "" })
                                        }
                                        newText == "/" -> { updateBlock(block.id) { it.copy(text = newText) }; slashBlockId = block.id; slashQuery = "" }
                                        newText.startsWith("/") && slashBlockId == block.id -> { updateBlock(block.id) { it.copy(text = newText) }; slashQuery = newText.drop(1) }
                                        // Markdown shortcuts
                                        newText == "# " -> convertBlock(block.id, BlockType.H1)
                                        newText == "## " -> convertBlock(block.id, BlockType.H2)
                                        newText == "### " -> convertBlock(block.id, BlockType.H3)
                                        newText == "- " && block.type == BlockType.PARAGRAPH -> convertBlock(block.id, BlockType.BULLET)
                                        newText == "1. " && block.type == BlockType.PARAGRAPH -> convertBlock(block.id, BlockType.NUMBERED)
                                        newText == "> " && block.type == BlockType.PARAGRAPH -> convertBlock(block.id, BlockType.QUOTE)
                                        newText == "---" && block.type == BlockType.PARAGRAPH -> convertBlock(block.id, BlockType.DIVIDER)
                                        else -> {
                                            if (slashBlockId == block.id && !newText.startsWith("/")) { slashBlockId = null; slashQuery = "" }
                                            updateBlock(block.id) { it.copy(text = newText) }
                                        }
                                    }
                                },
                                onToggleCheck = { updateBlock(block.id) { it.copy(checked = !it.checked) } },
                                onDelete = { deleteBlock(block.id) },
                                onSlashSelect = { cmd -> convertBlock(block.id, cmd.type); updateBlock(block.id) { it.copy(text = "") } },
                                onDragStart = { draggedIndex = index; dragOffsetY = 0f },
                                onDrag = { dy ->
                                    dragOffsetY += dy
                                    dropIndex = (index + (dragOffsetY / 56f).toInt()).coerceIn(0, blocks.lastIndex)
                                },
                                onDragEnd = {
                                    if (draggedIndex >= 0 && dropIndex >= 0 && draggedIndex != dropIndex) {
                                        val list = blocks.toMutableList()
                                        val item = list.removeAt(draggedIndex)
                                        list.add(dropIndex, item)
                                        blocks = list
                                    }
                                    draggedIndex = -1; dragOffsetY = 0f; dropIndex = -1
                                },
                                onDragCancel = { draggedIndex = -1; dragOffsetY = 0f; dropIndex = -1 },
                                onLocationChange = { lat, lon, addr ->
                                    updateBlock(block.id) { it.copy(latitude = lat, longitude = lon, address = addr) }
                                },
                                onMapTouch = { gesturesEnabled = !it }
                            )
                        }
                    }

                    // Tap empty area add block
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clickable {
                                val id = addBlockAfter(blocks.lastOrNull()?.id)
                                focusBlock(id)
                            }
                    )
                }
            }
        }
    }
}



