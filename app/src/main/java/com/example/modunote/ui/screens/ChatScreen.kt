package com.example.modunote.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.modunote.data.local.NoteViewModel
import com.example.modunote.network.ChatMessage
import com.example.modunote.network.ChatRequest
import com.example.modunote.network.OpenRouterClient
import com.example.modunote.ui.theme.md_theme_light_background
import com.example.modunote.ui.theme.md_theme_light_onPrimaryContainer
import com.example.modunote.ui.theme.md_theme_light_onSurface
import com.example.modunote.ui.theme.md_theme_light_onSurfaceVariant
import com.example.modunote.ui.theme.md_theme_light_primaryContainer
import com.example.modunote.ui.theme.md_theme_light_surfaceContainerHigh
import com.example.modunote.ui.theme.md_theme_link_color
import com.example.modunote.utils.BlockSerializer
import com.google.gson.JsonParser
import kotlinx.coroutines.launch

private const val MODUNOTE_SYSTEM_PROMPT = """Jesteś asystentem AI w aplikacji ModuNote - notatniku opartym na blokach (jak Notion).

Gdy uzytkownik prosi o napisanie notatki, stworzenie planu, szablonu lub struktury - odpowiedz WYLACZNIE w tym formacie (nic poza tagiem):
<note>{"title":"Tytul notatki","blocks":[{"id":"1","type":"TYP","text":"Tresc bloku","checked":false,"language":""}]}</note>

DOSTEPNE TYPY BLOKOW (wartosc pola "type"):
PARAGRAPH - zwykly tekst / akapit
H1 - naglowek duzy (tytul sekcji glownej)
H2 - naglowek sredni (podsekcja)
H3 - naglowek maly (podpunkt)
BULLET - element listy punktowej
NUMBERED - element listy numerowanej
TODO - zadanie z checkboxem (pole checked: false = nieukonczone, true = ukonczone)
QUOTE - cytat lub wyrozniony tekst
CODE - blok kodu (pole language: "kotlin", "python", "javascript", "bash", "sql" itp.)
DIVIDER - pozioma linia podzialu (text zawsze "")

ZASADY:
- Kazdy blok musi miec unikalne id ("1", "2", "3"...)
- Pole checked domyslnie false, language domyslnie ""
- W odpowiedzi z notatka pisz TYLKO tag <note>...</note> - zadnego tekstu przed ani po
- Gdy uzytkownik nie prosi o notatke - rozmawiaj normalnie i odpowiadaj po polsku
- Uzyj roznorodnych blokow: H2 na sekcje, BULLET/TODO na listy, CODE na fragmenty kodu
- Tytul i text moga byc po polsku"""

private val NOTE_TAG_PATTERN = Regex("<note>(.*?)</note>", setOf(RegexOption.DOT_MATCHES_ALL))
private val CODE_JSON_PATTERN = Regex("```(?:json)?\\s*(\\{[\\s\\S]*?\\})\\s*```")

private fun extractNoteJson(text: String): String? {
    // 1. explicit <note> tag
    NOTE_TAG_PATTERN.find(text)?.groupValues?.get(1)?.trim()?.let { return it }
    // 2. ```json { "title":..., "blocks":... } ```
    CODE_JSON_PATTERN.findAll(text).forEach { m ->
        val candidate = m.groupValues[1].trim()
        runCatching {
            val obj = JsonParser.parseString(candidate).asJsonObject
            if (obj.has("title") && obj.has("blocks")) return candidate
        }
    }
    // 3. bare JSON anywhere in text
    val start = text.indexOf("{\"title\"").takeIf { it >= 0 } ?: text.indexOf("{ \"title\"").takeIf { it >= 0 } ?: return null
    var depth = 0; var end = -1
    for (i in start until text.length) { when (text[i]) { '{' -> depth++; '}' -> { depth--; if (depth == 0) { end = i; break } } } }
    if (end < 0) return null
    val candidate = text.substring(start, end + 1)
    return runCatching {
        val obj = JsonParser.parseString(candidate).asJsonObject
        if (obj.has("title") && obj.has("blocks")) candidate else null
    }.getOrNull()
}

private fun parseNoteTitleFromJson(json: String): String =
    runCatching { JsonParser.parseString(json).asJsonObject.get("title")?.asString ?: "Notatka AI" }.getOrDefault("Notatka AI")

private fun extractBlocksJsonFromNoteJson(noteJson: String): String =
    runCatching {
        val blocksRaw = JsonParser.parseString(noteJson).asJsonObject.get("blocks")?.toString() ?: return@runCatching null
        // reparse and re-serialize via BlockSerializer so Gson types are guaranteed correct
        BlockSerializer.toJson(BlockSerializer.parse(blocksRaw))
    }.getOrNull() ?: aiTextToBlocksJson(noteJson)

private fun aiTextToBlocksJson(text: String): String {
    val cleaned = text.replace(NOTE_TAG_PATTERN, "").replace(CODE_JSON_PATTERN, "").trim()
    return BlockSerializer.toJson(BlockSerializer.fromPlainText(cleaned.ifBlank { " " }))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(noteViewModel: NoteViewModel, onNavigateTo: (Int) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("modunote_prefs", Context.MODE_PRIVATE) }
    var apiKey by remember { mutableStateOf(prefs.getString("openrouter_api_key", "") ?: "") }
    var selectedModel by remember { mutableStateOf(OpenRouterClient.models.first().first) }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showApiKeyDialog by remember { mutableStateOf(apiKey.isEmpty()) }
    var showModelMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // note save dialog: first = title, second = blocksJson
    var pendingNote by remember { mutableStateOf<Pair<String, String>?>(null) }
    var editableTitle by remember { mutableStateOf("") }

    pendingNote?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingNote = null },
            icon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, null, tint = Color(0xFF6650A4)) },
            title = { Text("Zapisać notatkę?", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tytuł notatki:", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = editableTitle,
                        onValueChange = { editableTitle = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val blocksJson = pending.second
                    pendingNote = null
                    noteViewModel.insertNote(editableTitle.trim().ifBlank { "Notatka AI" }, blocksJson) { id ->
                        onNavigateTo(id.toInt())
                    }
                }) { Text("Utwórz notatkę") }
            },
            dismissButton = {
                TextButton(onClick = { pendingNote = null }) { Text("Anuluj") }
            }
        )
    }

    fun openSaveDialog(title: String, blocksJson: String) {
        editableTitle = title
        pendingNote = title to blocksJson
    }

    fun sendMessage() {
        val text = inputText.trim()
        if (text.isEmpty() || apiKey.isEmpty()) return
        val userMsg = ChatMessage("user", text)
        val newMessages = messages + userMsg
        messages = newMessages
        inputText = ""
        isLoading = true
        errorMsg = null
        scope.launch {
            try {
                val systemMsg = ChatMessage("system", MODUNOTE_SYSTEM_PROMPT)
                val response = OpenRouterClient.api.complete(
                    authorization = "Bearer $apiKey",
                    request = ChatRequest(model = selectedModel, messages = listOf(systemMsg) + newMessages)
                )
                val reply = response.choices.firstOrNull()?.message
                    ?: ChatMessage("assistant", "(brak odpowiedzi)")
                messages = newMessages + reply
            } catch (e: Exception) {
                errorMsg = e.message ?: "Błąd połączenia"
                messages = newMessages
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    if (showApiKeyDialog) {
        var tempKey by remember { mutableStateOf(apiKey) }
        AlertDialog(
            onDismissRequest = { if (apiKey.isNotEmpty()) showApiKeyDialog = false },
            icon = { Icon(Icons.Default.Key, null) },
            title = { Text("Klucz API OpenRouter") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Wpisz swój klucz z openrouter.ai", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = { tempKey = it },
                        label = { Text("sk-or-...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    apiKey = tempKey.trim()
                    prefs.edit().putString("openrouter_api_key", apiKey).apply()
                    showApiKeyDialog = false
                }) { Text("Zapisz") }
            },
            dismissButton = {
                if (apiKey.isNotEmpty()) TextButton(onClick = { showApiKeyDialog = false }) { Text("Anuluj") }
            }
        )
    }

    Scaffold(
        containerColor = md_theme_light_background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Chatbot", fontWeight = FontWeight.SemiBold)
                        Box {
                            TextButton(
                                onClick = { showModelMenu = true },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    OpenRouterClient.models.find { it.first == selectedModel }?.second ?: selectedModel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = md_theme_light_onSurfaceVariant
                                )
                            }
                            DropdownMenu(expanded = showModelMenu, onDismissRequest = { showModelMenu = false }) {
                                OpenRouterClient.models.forEach { (id, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = { selectedModel = id; showModelMenu = false },
                                        trailingIcon = if (id == selectedModel) {
                                            { Icon(Icons.Default.Check, null, tint = md_theme_link_color) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć") }
                },
                actions = {
                    IconButton(onClick = { showApiKeyDialog = true }) {
                        Icon(Icons.Default.Key, "Klucz API")
                    }
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { messages = emptyList() }) {
                            Icon(Icons.Default.Delete, "Wyczyść")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = md_theme_light_background)
            )
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp, color = md_theme_light_background) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Napisz wiadomość…") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = { sendMessage() },
                        enabled = inputText.isNotBlank() && !isLoading && apiKey.isNotEmpty()
                    ) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        else Icon(Icons.Default.Send, "Wyślij")
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (errorMsg != null) {
                Text(
                    text = "Błąd: $errorMsg",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (messages.isEmpty() && !isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.SmartToy, null, modifier = Modifier.size(64.dp), tint = md_theme_light_onSurfaceVariant.copy(alpha = 0.4f))
                        Text("Zadaj pytanie chatbotowi", color = md_theme_light_onSurfaceVariant)
                        if (apiKey.isEmpty()) {
                            OutlinedButton(onClick = { showApiKeyDialog = true }) { Text("Ustaw klucz API") }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { msg ->
                        ChatBubble(
                            msg = msg,
                            onSaveNote = { title, blocksJson -> openSaveDialog(title, blocksJson) }
                        )
                    }
                    if (isLoading) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                                    color = md_theme_light_surfaceContainerHigh,
                                    modifier = Modifier.padding(end = 64.dp)
                                ) {
                                    Row(modifier = Modifier.padding(12.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Odpowiadam…", style = MaterialTheme.typography.bodySmall)
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

@Composable
private fun ChatBubble(
    msg: ChatMessage,
    onSaveNote: ((title: String, blocksJson: String) -> Unit)? = null
) {
    val isUser = msg.role == "user"
    val noteJson = remember(msg.content) { if (!isUser) extractNoteJson(msg.content) else null }
    val noteTitle = remember(noteJson) { noteJson?.let { parseNoteTitleFromJson(it) } }
    val displayText = remember(msg.content, noteJson) {
        if (noteJson != null)
            msg.content.replace(NOTE_TAG_PATTERN, "").replace(CODE_JSON_PATTERN, "").trim().ifBlank { null }
        else msg.content
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = if (isUser) Modifier.padding(start = 48.dp) else Modifier.padding(end = 48.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // main text bubble (skip if it's purely a note with no surrounding text)
            if (displayText != null) {
                Surface(
                    shape = if (isUser) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
                    else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                    color = if (isUser) md_theme_light_primaryContainer else md_theme_light_surfaceContainerHigh
                ) {
                    Text(
                        text = displayText,
                        modifier = Modifier.padding(12.dp, 10.dp),
                        color = if (isUser) md_theme_light_onPrimaryContainer else md_theme_light_onSurface
                    )
                }
            }

            // Auto-detected structured note -> prominent save card
            if (!isUser && noteJson != null && onSaveNote != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF6650A4).copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6650A4).copy(alpha = 0.3f)),
                    modifier = Modifier.clickable {
                        onSaveNote(noteTitle ?: "Notatka AI", extractBlocksJsonFromNoteJson(noteJson))
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NoteAdd, null, tint = Color(0xFF6650A4), modifier = Modifier.size(22.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Notatka gotowa", style = MaterialTheme.typography.labelSmall, color = Color(0xFF6650A4))
                            Text(
                                noteTitle ?: "Notatka AI",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        FilledTonalButton(
                            onClick = { onSaveNote(noteTitle ?: "Notatka AI", extractBlocksJsonFromNoteJson(noteJson)) },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) { Text("Zapisz i otwórz", style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }

            // All AI messages (no structured note) -> small manual save chip
            if (!isUser && noteJson == null && onSaveNote != null && msg.content.isNotBlank()) {
                TextButton(
                    onClick = { onSaveNote("Notatka AI", aiTextToBlocksJson(msg.content)) },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.NoteAdd, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Zapisz jako notatkę", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
