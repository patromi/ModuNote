package com.example.modunote

import android.content.Context
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.*
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.style.TextOverflow
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

    fun getPreviewText(content: String): String {
        if (content.isBlank()) return ""
        if (!isBlockJson(content)) {
            return content.replace(Regex("^#+\\s*|\\*\\*|\\[\\[|]]|-\\s\\[.\\]\\s|#[\\wÀ-ɏ/]+"), "").trim()
        }
        val blocks = parse(content)
        return blocks
            .filter { it.type != BlockType.DIVIDER && it.type != BlockType.MAP && it.type != BlockType.LINK }
            .joinToString(" ") { it.text }
            .replace(Regex("\\*\\*|\\[\\[|]]|#[\\wÀ-ɏ/]+"), "")
            .trim()
    }

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
    SlashCommand("link", "Link URL", "Otwiera stronę w aplikacji", BlockType.LINK, { Icon(Icons.Default.Link, null, Modifier.size(18.dp)) }),
)

// ─── PRINT HELPERS ─────────────────────────────────────────────────────────────

fun buildHtmlForPrint(noteTitle: String, blocks: List<Block>): String {
    val body = StringBuilder()
    for (block in blocks) {
        when (block.type) {
            BlockType.H1       -> body.append("<h1>${block.text.escapeHtml()}</h1>\n")
            BlockType.H2       -> body.append("<h2>${block.text.escapeHtml()}</h2>\n")
            BlockType.H3       -> body.append("<h3>${block.text.escapeHtml()}</h3>\n")
            BlockType.BULLET   -> body.append("<ul><li>${block.text.escapeHtml()}</li></ul>\n")
            BlockType.NUMBERED -> body.append("<ol><li>${block.text.escapeHtml()}</li></ol>\n")
            BlockType.TODO     -> {
                val check = if (block.checked) "&#9746;" else "&#9744;"
                body.append("<p>$check&nbsp;${block.text.escapeHtml()}</p>\n")
            }
            BlockType.QUOTE    -> body.append("<blockquote>${block.text.escapeHtml()}</blockquote>\n")
            BlockType.CODE     -> body.append("<pre><code>${block.text.escapeHtml()}</code></pre>\n")
            BlockType.DIVIDER  -> body.append("<hr/>\n")
            BlockType.MAP      -> body.append("<p><em>[Mapa: ${block.address ?: "${block.latitude}, ${block.longitude}"}]</em></p>\n")
            BlockType.LINK     -> body.append("<p>🔗 <a href=\"${block.url.escapeHtml()}\">${block.text.ifBlank { block.url }.escapeHtml()}</a></p>\n")
            else               -> body.append("<p>${block.text.escapeHtml()}</p>\n")
        }
    }
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1"/>
            <style>
                body { font-family: 'Segoe UI', Roboto, Arial, sans-serif; margin: 32px; color: #191c20; line-height: 1.6; }
                h1 { font-size: 26px; margin-bottom: 8px; }
                h2 { font-size: 20px; margin-bottom: 6px; }
                h3 { font-size: 16px; margin-bottom: 4px; }
                blockquote { border-left: 4px solid #00639A; margin: 0; padding-left: 16px; color: #43474e; font-style: italic; }
                pre { background: #1a1a2e; color: #80cbc4; padding: 12px; border-radius: 6px; overflow-x: auto; }
                hr { border: none; border-top: 1px solid #e0e2ec; margin: 16px 0; }
                ul, ol { padding-left: 24px; }
            </style>
        </head>
        <body>
            <h1>${noteTitle.escapeHtml()}</h1>
            <hr/>
            $body
        </body>
        </html>
    """.trimIndent()
}

private fun String.escapeHtml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

fun printNote(context: Context, noteTitle: String, blocks: List<Block>) {
    val html = buildHtmlForPrint(noteTitle, blocks)
    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val jobName = "ModuNote – $noteTitle"
            val adapter = view.createPrintDocumentAdapter(jobName)
            printManager.print(jobName, adapter, null)
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
}

// ─── BLOCK EDITOR SCREEN ───────────────────────────────────────────────────────

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
                                Icon(Icons.Default.MoreVert, "Więcej opcji", tint = md_theme_light_onSurfaceVariant)
                            }
                            val itemsVisible = remember(showMenu) { List(5) { mutableStateOf(false) } }
                            LaunchedEffect(showMenu) {
                                if (showMenu) {
                                    itemsVisible.forEachIndexed { i, state ->
                                        kotlinx.coroutines.delay(i * 55L)
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

                                // ═══ Grupa 1: Przypomnienie + Szablon ═══════
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

                                // ═══ Grupa 2: Blokada + Przypinanie ═════════
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

                                // ═══ Grupa 3: Drukowanie ════════════════════
                                AnimatedVisibility(
                                    visible = itemsVisible[4].value,
                                    enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { -20 }
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        tonalElevation = 8.dp,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        DropdownMenuItem(
                                            leadingIcon = { Icon(Icons.Default.Print, null, tint = md_theme_light_onSurfaceVariant) },
                                            text = { Text("Drukuj notatkę") },
                                            onClick = {
                                                showMenu = false
                                                printNote(context, title.ifBlank { "Notatka" }, blocks)
                                            }
                                        )
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
                                        val prevDist = kotlin.math.sqrt(prevDx * prevDx + prevDy * prevDy)
                                        val currDx = p1.position.x - p2.position.x
                                        val currDy = p1.position.y - p2.position.y
                                        val currDist = kotlin.math.sqrt(currDx * currDx + currDy * currDy)
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
                            if (title.isEmpty()) Text("Tytuł strony…", style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.LightGray))
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
                                allNotes = allNotes,
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
                                onDragCancel = { draggedIndex = -1; dragOffsetY = 0f; dropIndex = -1 },
                                onLocationChange = { lat, lon, addr ->
                                    updateBlock(block.id) { it.copy(latitude = lat, longitude = lon, address = addr) }
                                },
                                onMapTouch = { gesturesEnabled = !it }
                            )
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
    onDragCancel: () -> Unit,
    onUpdateBlock: (Block) -> Unit = {},
    onLocationChange: (Double, Double, String?) -> Unit = { _, _, _ -> },
    onMapTouch: (Boolean) -> Unit = {}
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
        verticalAlignment = if (block.type == BlockType.TODO || block.type == BlockType.MAP || block.type == BlockType.LINK) Alignment.CenterVertically else Alignment.Top
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .width(28.dp)
                .padding(top = if (block.type == BlockType.H1) 10.dp else if (block.type in listOf(BlockType.H2, BlockType.H3)) 6.dp else if (block.type == BlockType.MAP || block.type == BlockType.LINK) 0.dp else 4.dp)
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

        // Text area, Map or Link
        if (block.type == BlockType.MAP) {
            MapBlock(
                block = block,
                isFocused = isFocused,
                onLocationChange = onLocationChange,
                onMapTouch = onMapTouch
            )
        } else if (block.type == BlockType.LINK) {
            // â”€â”€ URL Link block â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            var showWebView by remember { mutableStateOf(false) }
            var isEditing by remember { mutableStateOf(block.url.isBlank()) }

            if (isEditing) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Dodaj link URL",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        
                        // URL input field
                        OutlinedTextField(
                            value = block.url,
                            onValueChange = { newUrl -> 
                                onUpdateBlock(block.copy(url = newUrl))
                            },
                            label = { Text("Adres URL") },
                            placeholder = { Text("np. google.com") },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = md_theme_link_color,
                                cursorColor = md_theme_link_color
                            ),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Done,
                                keyboardType = KeyboardType.Uri
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onDone = {
                                    if (block.url.isNotBlank()) {
                                        val normalized = if (block.url.startsWith("http://") || block.url.startsWith("https://")) {
                                            block.url
                                        } else {
                                            "https://${block.url}"
                                        }
                                        onUpdateBlock(block.copy(url = normalized))
                                        isEditing = false
                                    } else {
                                        onDelete()
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onFocusChanged { if (it.isFocused) onFocus() }
                                .onKeyEvent { event ->
                                    if (event.key == Key.Backspace && event.type == KeyEventType.KeyDown && block.url.isEmpty()) {
                                        onDelete()
                                        true
                                    } else false
                                }
                        )
                        
                        Spacer(Modifier.height(8.dp))
                        
                        // Label input field
                        OutlinedTextField(
                            value = block.text,
                            onValueChange = { newLabel -> 
                                onUpdateBlock(block.copy(text = newLabel))
                            },
                            label = { Text("Nazwa linku (opcjonalnie)") },
                            placeholder = { Text("np. Wyszukiwarka Google") },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = md_theme_link_color,
                                cursorColor = md_theme_link_color
                            ),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onDone = {
                                    if (block.url.isNotBlank()) {
                                        val normalized = if (block.url.startsWith("http://") || block.url.startsWith("https://")) {
                                            block.url
                                        } else {
                                            "https://${block.url}"
                                        }
                                        onUpdateBlock(block.copy(url = normalized))
                                        isEditing = false
                                    } else {
                                        onDelete()
                                    }
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    if (block.url.isBlank()) {
                                        onDelete()
                                    } else {
                                        isEditing = false
                                    }
                                }
                            ) {
                                Text("Anuluj", color = md_theme_link_color)
                            }
                            if (block.url.isNotBlank()) {
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val normalized = if (block.url.startsWith("http://") || block.url.startsWith("https://")) {
                                            block.url
                                        } else {
                                            "https://${block.url}"
                                        }
                                        onUpdateBlock(block.copy(url = normalized))
                                        isEditing = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = md_theme_link_color)
                                ) {
                                    Text("Zapisz", color = Color.White)
                                }
                            }
                        }
                    }
                }
            } else {
                val displayUrl = block.url
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = md_theme_link_color.copy(alpha = 0.08f),
                    tonalElevation = 0.dp,
                    onClick = { if (displayUrl.isNotBlank()) showWebView = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = null,
                            tint = md_theme_link_color,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            if (block.text.isNotBlank() && block.text != block.url) {
                                Text(
                                    text = block.text,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = md_theme_light_onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = displayUrl,
                                    fontSize = 12.sp,
                                    color = md_theme_link_color,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = displayUrl,
                                    fontSize = 14.sp,
                                    color = md_theme_link_color,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        
                        IconButton(
                            onClick = { isEditing = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edytuj",
                                tint = md_theme_light_onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        
                        Spacer(Modifier.width(4.dp))
                        
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = md_theme_light_onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (showWebView && block.url.isNotBlank()) {
                WebViewDialog(
                    url = block.url,
                    onDismiss = { showWebView = false }
                )
            }
        } else {
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
}

// ─── REMINDER DIALOG (reused in block editor) ──────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
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

    var enabled by remember { mutableStateOf(currentEnabled) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.timeInMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return utcTimeMillis >= Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
        }
    )

    val timePickerState = rememberTimePickerState(
        initialHour = initial.get(Calendar.HOUR_OF_DAY),
        initialMinute = initial.get(Calendar.MINUTE),
        is24Hour = true
    )

    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Anuluj") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Anuluj") }
            },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            }
        )
    }

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
                    OutlinedCard(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CalendarToday, null, Modifier.size(20.dp), tint = md_theme_link_color)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Data", style = MaterialTheme.typography.labelSmall, color = md_theme_light_onSurfaceVariant)
                                Text(
                                    dateFormatter.format(Date(datePickerState.selectedDateMillis ?: initial.timeInMillis)),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }

                    OutlinedCard(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AccessTime, null, Modifier.size(20.dp), tint = md_theme_link_color)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Godzina", style = MaterialTheme.typography.labelSmall, color = md_theme_light_onSurfaceVariant)
                                Text(
                                    "%02d:%02d".format(timePickerState.hour, timePickerState.minute),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = datePickerState.selectedDateMillis ?: initial.timeInMillis
                    // DatePicker returns UTC midnight, we need to adjust to local and set time
                    val localCal = Calendar.getInstance().apply {
                        timeInMillis = datePickerState.selectedDateMillis ?: initial.timeInMillis
                    }
                    set(Calendar.YEAR, localCal.get(Calendar.YEAR))
                    set(Calendar.MONTH, localCal.get(Calendar.MONTH))
                    set(Calendar.DAY_OF_MONTH, localCal.get(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    set(Calendar.MINUTE, timePickerState.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onSave(cal.timeInMillis, enabled)
            }) { Text("Zapisz") }
        },
        dismissButton = {
            Row {
                if (currentEnabled) TextButton(onClick = { onSave(0L, false) }) { Text("Usuń", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Anuluj") }
            }
        }
    )
}

@Composable
fun MapBlock(
    block: Block,
    isFocused: Boolean,
    onLocationChange: (Double, Double, String?) -> Unit,
    onMapTouch: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }

    val geoPoint = remember(block.latitude, block.longitude) {
        GeoPoint(block.latitude ?: 52.2297, block.longitude ?: 21.0122)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // Address display / search
        Box(modifier = Modifier.fillMaxWidth()) {
            if (isEditing || (isFocused && block.address == null)) {
                // Initialize query with current address when starting edit
                LaunchedEffect(isEditing) {
                    if (isEditing) {
                        query = block.address ?: ""
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    placeholder = { Text("Wpisz adres...", fontSize = 13.sp) },
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
                                                val displayName = first.get("display_name").asString
                                                withContext(Dispatchers.Main) {
                                                    onLocationChange(lat, lon, displayName)
                                                    query = ""
                                                    isEditing = false
                                                }
                                            }
                                        } catch (e: Exception) { e.printStackTrace() }
                                        finally { isSearching = false }
                                    }
                                }
                            }) { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(fontSize = 14.sp)
                )
            } else if (block.address != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .pointerInput(block.id) {
                            detectTapGestures(
                                onTap = { isEditing = true },
                                onLongPress = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Address", block.address)
                                    clipboard.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(context, "Adres skopiowany!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                    color = md_theme_light_surfaceContainerHigh,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp, 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOn, null, Modifier.size(16.dp), tint = md_theme_link_color)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = block.address ?: "",
                            fontSize = 13.sp,
                            color = md_theme_light_onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Map View
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        try {
                            awaitFirstDown(requireUnconsumed = false)
                            onMapTouch(true)
                            do {
                                val event = awaitPointerEvent()
                            } while (event.changes.any { it.pressed })
                        } finally {
                            onMapTouch(false)
                        }
                    }
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        setBuiltInZoomControls(false)
                        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                        isVerticalMapRepetitionEnabled = false
                        isHorizontalMapRepetitionEnabled = false
                        controller.setZoom(15.0)
                        controller.setCenter(geoPoint)
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

