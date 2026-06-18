package com.example.modunote.ui.screens

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.modunote.CryptoManager
import com.example.modunote.data.local.Note
import com.example.modunote.data.local.NoteTemplateViewModel
import com.example.modunote.data.local.NoteViewModel
import com.example.modunote.data.local.TagViewModel
import com.example.modunote.ui.theme.*
import com.example.modunote.utils.insertAtCursor
import com.example.modunote.TagExtractor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
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

    var title by remember { mutableStateOf("") }
    var contentValue by remember { mutableStateOf(TextFieldValue("")) }
    var previewMode by remember { mutableStateOf(false) }
    var isAuthenticated by remember { mutableStateOf(false) }
    var contentInitialized by remember { mutableStateOf(false) }
    var lastSavedTitle by remember { mutableStateOf("") }
    var lastSavedContent by remember { mutableStateOf("") }

    var showReminderDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showTagSuggestions by remember { mutableStateOf(false) }
    var tagSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }

    val titleFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val timeFmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("pl")) }

    LaunchedEffect(note, authenticatedNoteIds) {
        note?.let { n ->
            isAuthenticated = !n.isLocked || authenticatedNoteIds.contains(noteId)
            if (!contentInitialized && isAuthenticated) {
                contentInitialized = true
                title = n.title
                val decrypted = if (n.isLocked && n.content.startsWith("ENC:")) {
                    try { CryptoManager.decrypt(n.content.removePrefix("ENC:")) } catch (e: Exception) { "" }
                } else n.content
                contentValue = TextFieldValue(decrypted, TextRange(decrypted.length))
                lastSavedTitle = n.title
                lastSavedContent = decrypted
                // auto-focus title for new empty notes
                if (n.title.isBlank() && decrypted.isBlank()) {
                    runCatching { titleFocusRequester.requestFocus() }
                }
            }
        }
    }

    LaunchedEffect(title, contentValue.text) {
        delay(800)
        note?.let { n ->
            if (title == lastSavedTitle && contentValue.text == lastSavedContent) return@let
            val toSave = if (n.isLocked) "ENC:${CryptoManager.encrypt(contentValue.text)}" else contentValue.text
            noteViewModel.updateNoteWithTags(n.copy(title = title, content = toSave))
            lastSavedTitle = title
            lastSavedContent = contentValue.text
        }
    }

    // Tag autocomplete
    LaunchedEffect(contentValue.text, contentValue.selection) {
        val text = contentValue.text
        val cursor = contentValue.selection.end
        if (cursor > 0) {
            val beforeCursor = text.substring(0, cursor)
            val wordMatch = Regex("""#([\wĄĘĆŁÓŚŻŹŃ/]*)$""").find(beforeCursor)
            if (wordMatch != null) {
                val prefix = wordMatch.groupValues[1]
                tagSuggestions = tagViewModel.getSuggestions(prefix)
                showTagSuggestions = tagSuggestions.isNotEmpty()
            } else {
                showTagSuggestions = false
                tagSuggestions = emptyList()
            }
        }
    }

    // Reminder dialog
    if (showReminderDialog) {
        ReminderDialog(
            currentTime = note?.reminderTime,
            currentEnabled = note?.reminderEnabled ?: false,
            onDismiss = { showReminderDialog = false },
            onSave = { timeMs, enabled ->
                note?.let { n ->
                    noteViewModel.setReminder(n.id, timeMs, enabled, context)
                }
                showReminderDialog = false
            }
        )
    }

    // Save as template dialog
    if (showTemplateDialog) {
        var templateName by remember { mutableStateOf(title) }
        AlertDialog(
            onDismissRequest = { showTemplateDialog = false },
            icon = { Icon(Icons.Default.ContentCopy, null) },
            title = { Text("Zapisz jako szablon") },
            text = {
                OutlinedTextField(
                    value = templateName,
                    onValueChange = { templateName = it },
                    label = { Text("Nazwa szablonu") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        note?.let { n ->
                            noteTemplateViewModel.saveTemplate(templateName.ifBlank { n.title }, n.id)
                        }
                        showTemplateDialog = false
                    },
                    enabled = templateName.isNotBlank()
                ) { Text("Zapisz") }
            },
            dismissButton = {
                TextButton(onClick = { showTemplateDialog = false }) { Text("Anuluj") }
            }
        )
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(md_theme_light_background)) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz")
                        }
                    },
                    actions = {
                        IconButton(onClick = { previewMode = !previewMode }) {
                            Icon(
                                if (previewMode) Icons.Default.Edit else Icons.Default.Visibility,
                                contentDescription = if (previewMode) "Edytuj" else "Podgląd",
                                tint = if (previewMode) md_theme_light_onPrimaryContainer else md_theme_light_onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showReminderDialog = true }) {
                            Icon(
                                Icons.Default.Alarm,
                                contentDescription = "Przypomnienie",
                                tint = if (note?.reminderEnabled == true) md_theme_link_color else md_theme_light_onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showTemplateDialog = true }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Zapisz jako szablon", tint = md_theme_light_onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            note?.let { n ->
                                val newLocked = !n.isLocked
                                val toSave = if (newLocked) {
                                    try { "ENC:${CryptoManager.encrypt(contentValue.text)}" } catch (e: Exception) { contentValue.text }
                                } else contentValue.text
                                noteViewModel.updateNoteWithTags(n.copy(isLocked = newLocked, content = toSave, title = title))
                                if (newLocked) noteViewModel.markAuthenticated(n.id)
                                lastSavedContent = contentValue.text
                                lastSavedTitle = title
                            }
                        }) {
                            Icon(
                                if (note?.isLocked == true) Icons.Default.Lock else Icons.Default.LockOpen,
                                null,
                                tint = if (note?.isLocked == true) md_theme_light_onPrimaryContainer else md_theme_light_onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            note?.let { noteViewModel.updateNoteWithTags(it.copy(isPinned = !it.isPinned)) }
                        }) {
                            Icon(
                                Icons.Default.PushPin,
                                null,
                                tint = if (note?.isPinned == true) md_theme_light_onPrimaryContainer else md_theme_light_onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = md_theme_light_background)
                )
                if (note?.reminderEnabled == true && note?.reminderTime != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .clickable { showReminderDialog = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Alarm, null, modifier = Modifier.size(14.dp), tint = md_theme_link_color)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Przypomnienie: ${timeFmt.format(Date(note!!.reminderTime!!))}",
                            fontSize = 12.sp,
                            color = md_theme_link_color
                        )
                    }
                }
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(breadcrumbs) { index, pathNote ->
                        Text(
                            text = if (pathNote.title.isBlank()) "…" else pathNote.title,
                            fontSize = 12.sp,
                            color = if (index == breadcrumbs.lastIndex) md_theme_light_onSurface else md_theme_light_onSurfaceVariant,
                            modifier = if (index < breadcrumbs.lastIndex) Modifier.clickable { onNavigateTo(pathNote.id) } else Modifier
                        )
                        if (index < breadcrumbs.lastIndex) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(14.dp), tint = Color.LightGray)
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (!previewMode) {
                Surface(tonalElevation = 2.dp, modifier = Modifier.imePadding()) {
                    Column {
                        AnimatedVisibility(visible = showTagSuggestions) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(tagSuggestions) { suggestion ->
                                    SuggestionChip(
                                        onClick = {
                                            val text = contentValue.text
                                            val cursor = contentValue.selection.end
                                            val beforeCursor = text.substring(0, cursor)
                                            val wordMatch = Regex("""#([\wĄĘĆŁÓŚŻŹŃ/]*)$""").find(beforeCursor)
                                            if (wordMatch != null) {
                                                val start = wordMatch.range.first
                                                val newText = text.replaceRange(start, cursor, "#$suggestion")
                                                val newCursor = start + suggestion.length + 1
                                                contentValue = TextFieldValue(newText, TextRange(newCursor))
                                            }
                                            showTagSuggestions = false
                                        },
                                        label = { Text("#$suggestion", fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(onClick = { contentValue = contentValue.insertAtCursor("\n# ") }) {
                                Icon(Icons.Default.FormatSize, null)
                            }
                            IconButton(onClick = { contentValue = contentValue.insertAtCursor("**tekst**") }) {
                                Icon(Icons.Default.FormatBold, null)
                            }
                            IconButton(onClick = { contentValue = contentValue.insertAtCursor("\n- [ ] ") }) {
                                Icon(Icons.Default.CheckBoxOutlineBlank, null)
                            }
                            IconButton(onClick = { contentValue = contentValue.insertAtCursor("[[ ]]") }) {
                                Icon(Icons.Default.Link, null)
                            }
                            IconButton(onClick = { contentValue = contentValue.insertAtCursor(" #") }) {
                                Icon(Icons.Default.Tag, null)
                            }
                        }
                    }
                }
            }
        },
        containerColor = md_theme_light_background
    ) { padding ->
        when {
            note == null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            note!!.isLocked && !isAuthenticated -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Lock, null, modifier = Modifier.size(64.dp), tint = md_theme_light_onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        Text("Notatka chroniona", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            val executor = ContextCompat.getMainExecutor(context)
                            val callback = object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    noteViewModel.markAuthenticated(noteId)
                                }
                            }
                            BiometricPrompt(activity, executor, callback).authenticate(
                                BiometricPrompt.PromptInfo.Builder()
                                    .setTitle("Odblokuj notatkę")
                                    .setNegativeButtonText("Anuluj")
                                    .build()
                            )
                        }) {
                            Icon(Icons.Default.Fingerprint, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Odblokuj")
                        }
                    }
                }
            }
            previewMode -> {
                MarkdownPreview(
                    text = contentValue.text,
                    onTextChange = { newText ->
                        contentValue = contentValue.copy(text = newText)
                    },
                    onBacklinkClick = { linkedTitle ->
                        allNotes.firstOrNull { it.title.equals(linkedTitle, ignoreCase = true) }?.let { target ->
                            onNavigateTo(target.id)
                        }
                    },
                    modifier = Modifier.padding(padding)
                )
            }
            else -> {
                Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Tytuł", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(titleFocusRequester),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    )
                    TextField(
                        value = contentValue,
                        onValueChange = { contentValue = it },
                        placeholder = { Text("Zacznij pisać…") },
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderDialog(
    currentTime: Long?,
    currentEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long, Boolean) -> Unit
) {
    val now = Calendar.getInstance()
    val initial = if (currentTime != null && currentTime > now.timeInMillis) {
        Calendar.getInstance().apply { timeInMillis = currentTime }
    } else {
        Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
    }

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
                    Text("Włączone")
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                if (enabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = "%04d-%02d-%02d".format(year, month, day),
                            onValueChange = {
                                val parts = it.split("-")
                                parts.getOrNull(0)?.toIntOrNull()?.let { y -> year = y }
                                parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 12)?.let { m -> month = m }
                                parts.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 31)?.let { d -> day = d }
                            },
                            label = { Text("Data (RRRR-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = "%02d".format(hour),
                            onValueChange = { it.toIntOrNull()?.coerceIn(0, 23)?.let { h -> hour = h } },
                            label = { Text("Godz.") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = "%02d".format(minute),
                            onValueChange = { it.toIntOrNull()?.coerceIn(0, 59)?.let { m -> minute = m } },
                            label = { Text("Min.") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cal = Calendar.getInstance().apply {
                    set(year, month - 1, day, hour, minute, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onSave(cal.timeInMillis, enabled)
            }) { Text("Zapisz") }
        },
        dismissButton = {
            Row {
                if (currentEnabled) {
                    TextButton(onClick = { onSave(0L, false) }) {
                        Text("Usuń", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Anuluj") }
            }
        }
    )
}

@Composable
fun MarkdownPreview(
    text: String,
    onTextChange: (String) -> Unit,
    onBacklinkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val lines = text.lines()
        lines.forEachIndexed { index, rawLine ->
            when {
                rawLine.startsWith("# ") -> {
                    Text(
                        text = rawLine.removePrefix("# "),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                rawLine.startsWith("## ") -> {
                    Text(
                        text = rawLine.removePrefix("## "),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                rawLine.startsWith("### ") -> {
                    Text(
                        text = rawLine.removePrefix("### "),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                rawLine.startsWith("- [x] ") || rawLine.startsWith("- [X] ") -> {
                    val content = rawLine.removePrefix("- [x] ").removePrefix("- [X] ")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = true,
                            onCheckedChange = {
                                val newLines = lines.toMutableList()
                                newLines[index] = "- [ ] $content"
                                onTextChange(newLines.joinToString("\n"))
                            }
                        )
                        RichInlineText(content, onBacklinkClick)
                    }
                }
                rawLine.startsWith("- [ ] ") -> {
                    val content = rawLine.removePrefix("- [ ] ")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = false,
                            onCheckedChange = {
                                val newLines = lines.toMutableList()
                                newLines[index] = "- [x] $content"
                                onTextChange(newLines.joinToString("\n"))
                            }
                        )
                        RichInlineText(content, onBacklinkClick)
                    }
                }
                rawLine.startsWith("- ") -> {
                    Row {
                        Text("• ")
                        RichInlineText(rawLine.removePrefix("- "), onBacklinkClick)
                    }
                }
                rawLine.isBlank() -> Spacer(Modifier.height(8.dp))
                else -> RichInlineText(rawLine, onBacklinkClick)
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
fun RichInlineText(text: String, onBacklinkClick: (String) -> Unit) {
    val annotated = buildRichAnnotatedString(text, md_theme_link_color)
    ClickableText(
        text = annotated,
        style = LocalTextStyle.current,
        onClick = { offset ->
            annotated.getStringAnnotations("BACKLINK", offset, offset)
                .firstOrNull()?.let { onBacklinkClick(it.item) }
        }
    )
}

fun buildRichAnnotatedString(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    val backlinkRegex = Regex("""\[\[(.+?)]]""")
    val boldRegex = Regex("""\*\*(.+?)\*\*""")
    val allMatches = (backlinkRegex.findAll(text) + boldRegex.findAll(text))
        .sortedBy { it.range.first }
    var lastIndex = 0
    for (match in allMatches) {
        if (match.range.first < lastIndex) continue
        append(text.substring(lastIndex, match.range.first))
        if (match.value.startsWith("[[")) {
            val title = match.groupValues[1]
            pushStringAnnotation("BACKLINK", title)
            withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                append(title)
            }
            pop()
        } else {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groupValues[1])
            }
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex <= text.lastIndex) append(text.substring(lastIndex))
}
