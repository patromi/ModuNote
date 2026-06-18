package com.example.modunote

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.modunote.data.local.Block
import com.example.modunote.data.local.BlockType
import com.example.modunote.data.local.Note
import com.example.modunote.data.local.NoteTemplate
import com.example.modunote.data.local.NoteTemplateViewModel
import com.example.modunote.data.local.NoteViewModel
import com.example.modunote.data.local.TagViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

// ─── SERIALIZATION ─────────────────────────────────────────────────────────────

object BlockSerializer {
    private val gson = Gson()
    private val listType = object : TypeToken<List<Block>>() {}.type

    fun isBlockJson(content: String) = content.trimStart().startsWith("[")

    fun parse(content: String): List<Block> {
        if (content.isBlank()) return listOf(Block())
        if (!isBlockJson(content)) return fromPlainText(content)
        return runCatching { gson.fromJson<List<Block>>(content, listType) }
            .getOrNull()?.takeIf { it.isNotEmpty() } ?: listOf(Block())
    }

    fun toJson(blocks: List<Block>): String = gson.toJson(blocks)

    fun fromPlainText(text: String): List<Block> {
        if (text.isBlank()) return listOf(Block())
        val lines = text.lines()
        val result = mutableListOf<Block>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val block = when {
                line.startsWith("# ") -> Block(type = BlockType.H1, text = line.removePrefix("# "))
                line.startsWith("## ") -> Block(type = BlockType.H2, text = line.removePrefix("## "))
                line.startsWith("### ") -> Block(type = BlockType.H3, text = line.removePrefix("### "))
                line.startsWith("- [x] ") || line.startsWith("- [X] ") ->
                    Block(type = BlockType.TODO, text = line.removePrefix("- [x] ").removePrefix("- [X] "), checked = true)
                line.startsWith("- [ ] ") -> Block(type = BlockType.TODO, text = line.removePrefix("- [ ] "))
                line.startsWith("- ") -> Block(type = BlockType.BULLET, text = line.removePrefix("- "))
                line.matches(Regex("^\\d+\\. .*")) ->
                    Block(type = BlockType.NUMBERED, text = line.replaceFirst(Regex("^\\d+\\. "), ""))
                line.startsWith("> ") -> Block(type = BlockType.QUOTE, text = line.removePrefix("> "))
                line == "---" -> Block(type = BlockType.DIVIDER)
                line.startsWith("```") -> {
                    val lang = line.removePrefix("```").trim()
                    val code = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].startsWith("```")) { code.add(lines[i]); i++ }
                    Block(type = BlockType.CODE, text = code.joinToString("\n"), language = lang)
                }
                else -> Block(type = BlockType.PARAGRAPH, text = line)
            }
            result.add(block)
            i++
        }
        return result.ifEmpty { listOf(Block()) }
    }
}

// ─── SLASH COMMANDS ────────────────────────────────────────────────────────────

data class SlashCommand(val trigger: String, val label: String, val hint: String, val type: BlockType, val icon: @Composable () -> Unit)

val SLASH_COMMANDS = listOf(
    SlashCommand("tekst", "Tekst", "Zwykły akapit", BlockType.PARAGRAPH, { Icon(Icons.Default.TextFields, null, Modifier.size(18.dp)) }),
    SlashCommand("h1", "Nagłówek 1", "Duży tytuł", BlockType.H1, { Text("H1", fontWeight = FontWeight.Bold, fontSize = 14.sp) }),
    SlashCommand("h2", "Nagłówek 2", "Średni tytuł", BlockType.H2, { Text("H2", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }),
    SlashCommand("h3", "Nagłówek 3", "Mały tytuł", BlockType.H3, { Text("H3", fontWeight = FontWeight.Medium, fontSize = 12.sp) }),
    SlashCommand("lista", "Lista punktowa", "• element", BlockType.BULLET, { Text("•", fontSize = 18.sp) }),
    SlashCommand("numerowana", "Lista numerowana", "1. element", BlockType.NUMBERED, { Text("1.", fontSize = 13.sp) }),
    SlashCommand("zadanie", "Zadanie", "Checkbox do odhaczenia", BlockType.TODO, { Icon(Icons.Default.CheckBox, null, Modifier.size(18.dp)) }),
    SlashCommand("cytat", "Cytat", "Blok cytatu", BlockType.QUOTE, { Icon(Icons.Default.FormatQuote, null, Modifier.size(18.dp)) }),
    SlashCommand("podzial", "Linia", "Poziomy separator", BlockType.DIVIDER, { Icon(Icons.Default.HorizontalRule, null, Modifier.size(18.dp)) }),
    SlashCommand("kod", "Kod", "Blok kodu z monospace", BlockType.CODE, { Icon(Icons.Default.Code, null, Modifier.size(18.dp)) }),
    SlashCommand("mapa", "Mapa", "Lokalizacja na mapie", BlockType.MAP, { Icon(Icons.Default.Map, null, Modifier.size(18.dp)) }),
)

// ─── BLOCK EDITOR SCREEN ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockEditorScreen(
    noteId: Int,
    noteViewModel: NoteViewModel,
    noteTemplateViewModel: NoteTemplateViewModel,
    tagViewModel: TagViewModel,
    onBack: () -> Unit,
    onNavigateTo: (Int) -> Unit = {}
) {
    val note by noteViewModel.getNoteById(noteId).collectAsState(initial = null)
    val breadcrumbs by noteViewModel.getBreadcrumbs(noteId).collectAsState(initial = emptyList())
    val allNotes by noteViewModel.allNotes.collectAsState(initial = emptyList())
    val authenticatedNoteIds by noteViewModel.authenticatedNoteIds.collectAsState()
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val scope = rememberCoroutineScope()

    // Editor state
    var title by remember { mutableStateOf("") }
    var blocks by remember { mutableStateOf(listOf<Block>()) }
    var initialized by remember { mutableStateOf(false) }
    var isAuthenticated by remember { mutableStateOf(false) }
    var lastSavedTitle by remember { mutableStateOf("") }
    var lastSavedJson by remember { mutableStateOf("") }

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

    // Dialogs
    var showReminderDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    val timeFmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("pl")) }

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
    LaunchedEffect(title, blocksSnapshot) {
        delay(800)
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
            text = if (type == BlockType.DIVIDER || type == BlockType.MAP) "" else it.text,
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
                        IconButton(onClick = { showReminderDialog = true }) {
                            Icon(Icons.Default.Alarm, null,
                                tint = if (note?.reminderEnabled == true) md_theme_link_color else md_theme_light_onSurfaceVariant)
                        }
                        IconButton(onClick = { showTemplateDialog = true }) {
                            Icon(Icons.Default.ContentCopy, null, tint = md_theme_light_onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            note?.let { n ->
                                val json = BlockSerializer.toJson(blocks)
                                val newLocked = !n.isLocked
                                val toSave = if (newLocked) "ENC:${runCatching { CryptoManager.encrypt(json) }.getOrElse { json }}" else json
                                noteViewModel.updateNoteWithTags(n.copy(isLocked = newLocked, content = toSave, title = title))
                                if (newLocked) noteViewModel.markAuthenticated(n.id)
                                lastSavedJson = json; lastSavedTitle = title
                            }
                        }) {
                            Icon(if (note?.isLocked == true) Icons.Default.Lock else Icons.Default.LockOpen, null,
                                tint = if (note?.isLocked == true) md_theme_light_onPrimaryContainer else md_theme_light_onSurfaceVariant)
                        }
                        IconButton(onClick = { note?.let { noteViewModel.updateNoteWithTags(it.copy(isPinned = !it.isPinned)) } }) {
                            Icon(Icons.Default.PushPin, null,
                                tint = if (note?.isPinned == true) md_theme_light_onPrimaryContainer else md_theme_light_onSurfaceVariant)
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
                                text = if (pathNote.title.isBlank()) "…" else pathNote.title,
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
                            if (title.isEmpty()) Text("Tytuł strony…", style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.LightGray))
                            inner()
                        }
                    )

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
                                allNotes = allNotes,
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
                                onDismissSlash = { slashBlockId = null; slashQuery = "" },
                                onBacklinkClick = { linkedTitle ->
                                    allNotes.firstOrNull { it.title.equals(linkedTitle, ignoreCase = true) }?.let { onNavigateTo(it.id) }
                                },
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
                                onDragCancel = { draggedIndex = -1; dragOffsetY = 0f; dropIndex = -1 }
                            )
                            if (block.type == BlockType.MAP) {
                                MapBlock(
                                    block = block,
                                    isFocused = isFocused,
                                    onLocationChange = { lat, lon ->
                                        updateBlock(block.id) { it.copy(latitude = lat, longitude = lon) }
                                    }
                                )
                            }
                        }
                    }

                    // Tap empty area → add block
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

// ─── BLOCK ITEM ────────────────────────────────────────────────────────────────

@Composable
fun BlockItem(
    block: Block,
    numberedIndex: Int,
    isFocused: Boolean,
    isDragging: Boolean,
    isDropTarget: Boolean,
    dragOffsetY: Float,
    focusRequester: FocusRequester,
    showSlashMenu: Boolean,
    filteredSlash: List<SlashCommand>,
    allNotes: List<Note>,
    onFocus: () -> Unit,
    onTextChange: (String) -> Unit,
    onToggleCheck: () -> Unit,
    onDelete: () -> Unit,
    onSlashSelect: (SlashCommand) -> Unit,
    onDismissSlash: () -> Unit,
    onBacklinkClick: (String) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    // Divider block
    if (block.type == BlockType.DIVIDER) {
        HorizontalDivider(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .zIndex(if (isDragging) 1f else 0f)
                .alpha(if (isDragging) 0.5f else 1f),
            color = md_theme_light_surfaceContainerHigh.copy(alpha = 2f)
        )
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .alpha(if (isDragging) 0.65f else 1f)
            .background(if (isDropTarget) md_theme_link_color.copy(alpha = 0.07f) else Color.Transparent)
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = if (block.type == BlockType.TODO) Alignment.CenterVertically else Alignment.Top
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .width(28.dp)
                .padding(top = if (block.type == BlockType.H1) 10.dp else if (block.type in listOf(BlockType.H2, BlockType.H3)) 6.dp else 4.dp)
                .pointerInput(block.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { _ -> onDragStart() },
                        onDrag = { change, amount -> change.consume(); onDrag(amount.y) },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragCancel() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (isFocused) {
                Icon(Icons.Default.DragIndicator, null, modifier = Modifier.size(16.dp), tint = Color.LightGray)
            }
        }

        // Block-type prefix
        when (block.type) {
            BlockType.BULLET -> Text("•", modifier = Modifier.padding(end = 8.dp, top = 10.dp), fontSize = 16.sp, color = md_theme_light_onSurface)
            BlockType.NUMBERED -> Text("$numberedIndex.", modifier = Modifier.padding(end = 6.dp, top = 10.dp), fontSize = 14.sp, color = md_theme_light_onSurfaceVariant)
            BlockType.TODO -> Checkbox(
                checked = block.checked,
                onCheckedChange = { onToggleCheck() },
                modifier = Modifier.size(28.dp).padding(end = 4.dp),
                colors = CheckboxDefaults.colors(checkedColor = md_theme_link_color)
            )
            BlockType.QUOTE -> Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(if (block.text.isBlank()) 24.dp else 48.dp)
                    .background(md_theme_link_color, RoundedCornerShape(2.dp))
                    .padding(end = 0.dp)
            )
            else -> {}
        }
        if (block.type == BlockType.QUOTE) Spacer(Modifier.width(10.dp))

        // Text area (with slash menu overlay)
        Box(modifier = Modifier.weight(1f)) {
            val textStyle = when (block.type) {
                BlockType.H1 -> TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = md_theme_light_onSurface, lineHeight = 32.sp)
                BlockType.H2 -> TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = md_theme_light_onSurface, lineHeight = 28.sp)
                BlockType.H3 -> TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = md_theme_light_onSurface, lineHeight = 24.sp)
                BlockType.QUOTE -> TextStyle(fontSize = 15.sp, fontStyle = FontStyle.Italic, color = md_theme_light_onSurfaceVariant, lineHeight = 22.sp)
                BlockType.CODE -> TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF80CBC4), lineHeight = 20.sp)
                BlockType.TODO -> TextStyle(
                    fontSize = 15.sp,
                    color = if (block.checked) md_theme_light_onSurfaceVariant else md_theme_light_onSurface,
                    textDecoration = if (block.checked) TextDecoration.LineThrough else TextDecoration.None,
                    lineHeight = 22.sp
                )
                else -> TextStyle(fontSize = 15.sp, color = md_theme_light_onSurface, lineHeight = 22.sp)
            }

            val vertPad = when (block.type) {
                BlockType.H1 -> 8.dp
                BlockType.H2, BlockType.H3 -> 6.dp
                else -> 4.dp
            }

            BasicTextField(
                value = block.text,
                onValueChange = onTextChange,
                textStyle = textStyle,
                cursorBrush = SolidColor(md_theme_link_color),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = vertPad, horizontal = if (block.type == BlockType.CODE) 0.dp else 4.dp)
                    .then(
                        if (block.type == BlockType.CODE)
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1A1A2E))
                                .padding(12.dp)
                        else Modifier
                    )
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused) onFocus() }
                    .onKeyEvent { event ->
                        if (event.key == Key.Backspace && event.type == KeyEventType.KeyDown && block.text.isEmpty()) {
                            onDelete(); true
                        } else false
                    },
                decorationBox = { inner ->
                    if (block.text.isEmpty() && isFocused) {
                        Text(
                            when (block.type) {
                                BlockType.PARAGRAPH -> "Wpisz '/' aby dodac blok…"
                                BlockType.H1 -> "Naglowek 1"
                                BlockType.H2 -> "Naglowek 2"
                                BlockType.H3 -> "Naglowek 3"
                                BlockType.CODE -> "// wpisz kod…"
                                BlockType.QUOTE -> "Cytat…"
                                BlockType.BULLET -> "Element listy…"
                                BlockType.NUMBERED -> "Element listy…"
                                BlockType.TODO -> "Zadanie…"
                                else -> ""
                            },
                            style = textStyle.copy(color = Color.LightGray)
                        )
                    }
                    inner()
                }
            )

            // Slash command dropdown
            if (showSlashMenu && filteredSlash.isNotEmpty()) {
                Box(modifier = Modifier.padding(top = if (block.type == BlockType.H1) 40.dp else 28.dp)) {
                    Card(
                        modifier = Modifier.width(240.dp).heightIn(max = 280.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        LazyColumn {
                            item {
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Bolt, null, Modifier.size(14.dp), tint = md_theme_link_color)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Komendy", style = MaterialTheme.typography.labelSmall, color = md_theme_light_onSurfaceVariant)
                                }
                                HorizontalDivider()
                            }
                            items(filteredSlash) { cmd ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSlashSelect(cmd) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(28.dp).background(md_theme_light_surfaceContainerHigh, RoundedCornerShape(4.dp)),
                                        contentAlignment = Alignment.Center
                                    ) { cmd.icon() }
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(cmd.label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        Text(cmd.hint, fontSize = 11.sp, color = md_theme_light_onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── REMINDER DIALOG (reused in block editor) ──────────────────────────────────

@Composable
fun ReminderDialogBlock(
    currentTime: Long?,
    currentEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long, Boolean) -> Unit
) {
    val initial = if (currentTime != null && currentTime > System.currentTimeMillis()) {
        Calendar.getInstance().apply { timeInMillis = currentTime }
    } else Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }

    var year by remember { mutableIntStateOf(initial.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(initial.get(Calendar.MONTH) + 1) }
    var day by remember { mutableIntStateOf(initial.get(Calendar.DAY_OF_MONTH)) }
    var hour by remember { mutableIntStateOf(initial.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(initial.get(Calendar.MINUTE)) }
    var enabled by remember { mutableStateOf(currentEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Alarm, null) },
        title = { Text("Ustaw przypomnienie") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Włączone"); Switch(enabled, { enabled = it })
                }
                if (enabled) {
                    OutlinedTextField(
                        value = "%04d-%02d-%02d".format(year, month, day),
                        onValueChange = {
                            val p = it.split("-")
                            p.getOrNull(0)?.toIntOrNull()?.let { v -> year = v }
                            p.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 12)?.let { v -> month = v }
                            p.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 31)?.let { v -> day = v }
                        },
                        label = { Text("Data RRRR-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField("%02d".format(hour), { it.toIntOrNull()?.coerceIn(0, 23)?.let { v -> hour = v } },
                            label = { Text("Godz.") }, singleLine = true, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField("%02d".format(minute), { it.toIntOrNull()?.coerceIn(0, 59)?.let { v -> minute = v } },
                            label = { Text("Min.") }, singleLine = true, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cal = Calendar.getInstance().apply { set(year, month - 1, day, hour, minute, 0); set(Calendar.MILLISECOND, 0) }
                onSave(cal.timeInMillis, enabled)
            }) { Text("Zapisz") }
        },
        dismissButton = {
            Row {
                if (currentEnabled) TextButton(onClick = { onSave(0L, false) }) { Text("Usun", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Anuluj") }
            }
        }
    )
}

@Composable
fun MapBlock(
    block: Block,
    isFocused: Boolean,
    onLocationChange: (Double, Double) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    val geoPoint = remember(block.latitude, block.longitude) {
        GeoPoint(block.latitude ?: 52.2297, block.longitude ?: 21.0122)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // Search bar - only visible when focused
        AnimatedVisibility(visible = isFocused) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                placeholder = { Text("Wpisz adres (np. Warszawa, ul. Nowa 1)...", fontSize = 13.sp) },
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = {
                            if (query.isNotBlank()) {
                                isSearching = true
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val url = URL("https://nominatim.openstreetmap.org/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&format=json&limit=1")
                                        val conn = url.openConnection() as HttpURLConnection
                                        conn.setRequestProperty("User-Agent", "ModuNote-App")
                                        val response = conn.inputStream.bufferedReader().readText()
                                        val json = com.google.gson.JsonParser.parseString(response).asJsonArray
                                        if (json.size() > 0) {
                                            val first = json.get(0).asJsonObject
                                            val lat = first.get("lat").asDouble
                                            val lon = first.get("lon").asDouble
                                            withContext(Dispatchers.Main) {
                                                onLocationChange(lat, lon)
                                                query = "" // Clear after search
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    } finally {
                                        isSearching = false
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                textStyle = TextStyle(fontSize = 14.sp)
            )
        }

        // Map View
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        ) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        setBuiltInZoomControls(false) // Remove +/- buttons
                        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                        
                        // Disable panning/scrolling, allow only pinch-to-zoom
                        setScrollableAreaLimitDouble(org.osmdroid.util.BoundingBox(85.0, 180.0, -85.0, -180.0)) // limit if needed
                        isVerticalMapRepetitionEnabled = false
                        isHorizontalMapRepetitionEnabled = false
                        
                        controller.setZoom(15.0)
                        controller.setCenter(geoPoint)

                        // Styling: Grayscale filter for a more "notey" look
                        val matrix = android.graphics.ColorMatrix().apply { setSaturation(0f) }
                        val filter = android.graphics.ColorMatrixColorFilter(matrix)
                        overlayManager.tilesOverlay.setColorFilter(filter)
                    }
                },
                update = { view ->
                    view.controller.setCenter(geoPoint)
                    view.overlays.removeIf { it is Marker }
                    val marker = Marker(view).apply {
                        position = geoPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)
                        icon?.setTint(android.graphics.Color.parseColor("#6650A4"))
                        // Disable marker dragging/interaction
                        setOnMarkerClickListener { _, _ -> true }
                    }
                    view.overlays.add(marker)
                    view.invalidate()
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

