package com.example.modunote

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.modunote.data.local.CalendarEvent
import com.example.modunote.data.local.CalendarEventViewModel
import com.example.modunote.data.local.FlattenedNote
import com.example.modunote.data.local.Note
import com.example.modunote.data.local.NoteTemplate
import com.example.modunote.data.local.NoteTemplateViewModel
import com.example.modunote.data.local.NoteViewModel
import com.example.modunote.data.local.TagViewModel
import com.example.modunote.network.ChatMessage
import com.example.modunote.network.ChatRequest
import com.example.modunote.network.OpenRouterClient
import com.google.gson.JsonParser
import com.example.modunote.ui.theme.ModuNoteTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

val md_theme_light_background = Color(0xFFFEF7FF)
val md_theme_light_onSurface = Color(0xFF1D1B20)
val md_theme_light_onSurfaceVariant = Color(0xFF49454F)
val md_theme_light_surfaceContainerHigh = Color(0xFFECE6F0)
val md_theme_light_primaryContainer = Color(0xFFEADDFF)
val md_theme_light_onPrimaryContainer = Color(0xFF4F378A)
val md_theme_link_color = Color(0xFF6650A4)

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
        ReminderScheduler.createChannel(this)

        setContent {
            ModuNoteTheme {
                val navController = rememberNavController()
                val noteViewModel: NoteViewModel = viewModel()
                val calendarViewModel: CalendarEventViewModel = viewModel()
                val noteTemplateViewModel: NoteTemplateViewModel = viewModel()
                val tagViewModel: TagViewModel = viewModel()
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(noteViewModel, tagViewModel, navController)
                    }
                    composable(
                        "editor/{noteId}",
                        arguments = listOf(navArgument("noteId") { type = NavType.IntType })
                    ) { backStackEntry ->
                        val noteId = backStackEntry.arguments?.getInt("noteId") ?: -1
                        BlockEditorScreen(
                            noteId = noteId,
                            noteViewModel = noteViewModel,
                            noteTemplateViewModel = noteTemplateViewModel,
                            tagViewModel = tagViewModel,
                            onBack = { navController.popBackStack() },
                            onNavigateTo = { id -> navController.navigate("editor/$id") }
                        )
                    }
                    composable("chat") {
                        ChatScreen(
                            noteViewModel = noteViewModel,
                            onNavigateTo = { id -> navController.navigate("editor/$id") },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("calendar") {
                        CalendarScreen(
                            viewModel = calendarViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("templates") {
                        TemplateScreen(
                            noteTemplateViewModel = noteTemplateViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

// ─── HOME SCREEN ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    noteViewModel: NoteViewModel,
    tagViewModel: TagViewModel,
    navController: NavController
) {
    val allNotes by noteViewModel.allNotes.collectAsState(initial = emptyList())
    val flattenedNotes by noteViewModel.flattenedNotes.collectAsState(initial = emptyList())
    val authenticatedNoteIds by noteViewModel.authenticatedNoteIds.collectAsState()
    val allTags by tagViewModel.allTags.collectAsState()
    val context = LocalContext.current
    val activity = context as FragmentActivity

    val pinnedNotes = remember(allNotes) {
        allNotes.filter { it.isPinned && !it.content.startsWith("ENC:") }
    }

    var selectedTag by remember { mutableStateOf<String?>(null) }

    val filteredNoteIds by remember(selectedTag) {
        if (selectedTag != null) tagViewModel.getNoteIdsByTag(selectedTag!!)
        else flowOf(null)
    }.collectAsState(initial = null)

    val displayedNotes = remember(flattenedNotes, selectedTag, filteredNoteIds) {
        if (selectedTag != null && filteredNoteIds != null)
            flattenedNotes.filter { it.note.id in filteredNoteIds!! }
        else flattenedNotes
    }

    var draggedItemIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dropTargetIndex by remember { mutableIntStateOf(-1) }

    fun openNote(note: Note) {
        if (note.isLocked && !authenticatedNoteIds.contains(note.id)) {
            val executor = ContextCompat.getMainExecutor(context)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    noteViewModel.markAuthenticated(note.id)
                    navController.navigate("editor/${note.id}")
                }
            }
            BiometricPrompt(activity, executor, callback).authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Notatka chroniona")
                    .setSubtitle("Uwierzytelnij się, aby otworzyć")
                    .setNegativeButtonText("Anuluj")
                    .build()
            )
        } else {
            navController.navigate("editor/${note.id}")
        }
    }

    Scaffold(
        containerColor = md_theme_light_background,
        topBar = {
            Column(modifier = Modifier.background(md_theme_light_background)) {
                TopAppBar(
                    title = {
                        Column {
                            Text("ModuNote", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            if (selectedTag != null) {
                                Text(
                                    "#$selectedTag",
                                    fontSize = 12.sp,
                                    color = md_theme_link_color,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    },
                    actions = {
                        if (selectedTag != null) {
                            IconButton(onClick = { selectedTag = null }) {
                                Icon(Icons.Default.FilterAltOff, "Wyczyść filtr", tint = md_theme_link_color)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = md_theme_light_background)
                )
                // Tag filter chips — visible when tags exist
                if (allTags.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(allTags) { tag ->
                            FilterChip(
                                selected = tag == selectedTag,
                                onClick = { selectedTag = if (selectedTag == tag) null else tag },
                                label = { Text("#$tag", fontSize = 11.sp) },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    noteViewModel.insertNote("", "") { id ->
                        navController.navigate("editor/${id.toInt()}")
                    }
                },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Nowa notatka") },
                containerColor = md_theme_link_color,
                contentColor = Color.White
            )
        },
        bottomBar = {
            NavigationBar(containerColor = md_theme_light_background) {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Default.Notes, null) },
                    label = { Text("Notatki", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("calendar") },
                    icon = { Icon(Icons.Default.CalendarMonth, null) },
                    label = { Text("Kalendarz", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("chat") },
                    icon = { Icon(Icons.Default.SmartToy, null) },
                    label = { Text("Chat AI", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("templates") },
                    icon = { Icon(Icons.Default.ContentCopy, null) },
                    label = { Text("Szablony", fontSize = 11.sp) }
                )
            }
        }
    ) { padding ->
        if (displayedNotes.isEmpty() && allNotes.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.NoteAdd,
                        null,
                        modifier = Modifier.size(72.dp),
                        tint = md_theme_light_onSurfaceVariant.copy(alpha = 0.35f)
                    )
                    Text(
                        "Brak notatek",
                        style = MaterialTheme.typography.titleMedium,
                        color = md_theme_light_onSurfaceVariant
                    )
                    Text(
                        "Nacisnij przycisk + aby zaczac",
                        style = MaterialTheme.typography.bodySmall,
                        color = md_theme_light_onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                // ── Pinned section ──────────────────────────────────────────
                if (pinnedNotes.isNotEmpty() && selectedTag == null) {
                    item(key = "pinned_header") {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PushPin,
                                null,
                                modifier = Modifier.size(14.dp),
                                tint = md_theme_light_onPrimaryContainer
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Przypięte",
                                style = MaterialTheme.typography.labelMedium,
                                color = md_theme_light_onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    item(key = "pinned_row") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(pinnedNotes, key = { "pin_${it.id}" }) { note ->
                                PinnedNoteCard(
                                    note = note,
                                    onClick = { openNote(note) }
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = md_theme_light_surfaceContainerHigh
                        )
                    }
                }

                // ── Notes header ─────────────────────────────────────────────
                item(key = "notes_header") {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Notes,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = md_theme_light_onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (selectedTag != null) "Przefiltrowane (#$selectedTag)" else "Wszystkie notatki",
                            style = MaterialTheme.typography.labelMedium,
                            color = md_theme_light_onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "(${displayedNotes.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = md_theme_light_onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }

                // ── Tree list ─────────────────────────────────────────────────
                itemsIndexed(displayedNotes, key = { _, item -> item.note.id }) { index, item ->
                    val isDragging = draggedItemIndex == index
                    val isDropTarget = dropTargetIndex == index && draggedItemIndex != index

                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            when (value) {
                                SwipeToDismissBoxValue.StartToEnd -> {
                                    noteViewModel.togglePinned(item.note); false
                                }
                                SwipeToDismissBoxValue.EndToStart -> {
                                    noteViewModel.deleteNote(item.note); true
                                }
                                else -> false
                            }
                        },
                        positionalThreshold = { it * 0.4f }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        gesturesEnabled = draggedItemIndex == -1,
                        backgroundContent = {
                            val bgColor by animateColorAsState(
                                targetValue = when (dismissState.targetValue) {
                                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFF4CAF50)
                                    SwipeToDismissBoxValue.EndToStart -> Color(0xFFF44336)
                                    else -> Color.Transparent
                                },
                                label = "swipeBg"
                            )
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp)
                                    .background(bgColor, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 20.dp)
                            ) {
                                when (dismissState.targetValue) {
                                    SwipeToDismissBoxValue.StartToEnd -> Icon(
                                        Icons.Default.PushPin, null,
                                        modifier = Modifier.align(Alignment.CenterStart),
                                        tint = Color.White
                                    )
                                    SwipeToDismissBoxValue.EndToStart -> Icon(
                                        Icons.Default.Delete, null,
                                        modifier = Modifier.align(Alignment.CenterEnd),
                                        tint = Color.White
                                    )
                                    else -> {}
                                }
                            }
                        }
                    ) {
                        NoteTreeItem(
                            item = item,
                            isDragging = isDragging,
                            isDropTarget = isDropTarget,
                            dragOffsetY = if (isDragging) dragOffsetY else 0f,
                            onExpandClick = { noteViewModel.toggleExpanded(item.note.id) },
                            onNoteClick = { openNote(item.note) },
                            onAddChildClick = {
                                noteViewModel.insertNote("", "", parentId = item.note.id) { id ->
                                    navController.navigate("editor/${id.toInt()}")
                                }
                            },
                            onTagClick = { tag -> selectedTag = tag },
                            onDragStart = { draggedItemIndex = index; dragOffsetY = 0f },
                            onDrag = { dy ->
                                dragOffsetY += dy
                                val deltaIndex = (dragOffsetY / 80f).toInt()
                                dropTargetIndex = (index + deltaIndex).coerceIn(0, displayedNotes.lastIndex)
                            },
                            onDragEnd = {
                                val draggedNote = displayedNotes.getOrNull(draggedItemIndex)?.note
                                val targetNote = displayedNotes.getOrNull(dropTargetIndex)?.note
                                if (draggedNote != null && targetNote != null && draggedItemIndex != dropTargetIndex) {
                                    noteViewModel.moveNote(draggedNote.id, targetNote.id)
                                }
                                draggedItemIndex = -1; dragOffsetY = 0f; dropTargetIndex = -1
                            },
                            onDragCancel = { draggedItemIndex = -1; dragOffsetY = 0f; dropTargetIndex = -1 }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinnedNoteCard(note: Note, onClick: () -> Unit) {
    val tags = remember(note.content, note.title) {
        TagExtractor.extract(note.title + " " + note.content).filter { !it.contains("/") }.take(3)
    }
    Card(
        modifier = Modifier
            .width(160.dp)
            .height(110.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = md_theme_light_primaryContainer),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = note.title.ifBlank { "Bez tytułu" },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = md_theme_light_onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (note.content.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = note.content
                            .replace(Regex("#[\\wÀ-ɏ/]+|\\*\\*|\\[\\[|]]|^#+\\s*"), "")
                            .trim(),
                        fontSize = 11.sp,
                        color = md_theme_light_onPrimaryContainer.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                if (tags.isNotEmpty()) {
                    Text(
                        tags.joinToString(" ") { "#$it" },
                        fontSize = 10.sp,
                        color = md_theme_link_color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else Spacer(Modifier.weight(1f))
                if (note.reminderEnabled && note.reminderTime != null && note.reminderTime > System.currentTimeMillis()) {
                    Icon(Icons.Default.Alarm, null, modifier = Modifier.size(12.dp), tint = md_theme_link_color)
                }
            }
        }
    }
}

// ─── TREE ITEM ─────────────────────────────────────────────────────────────────

@Composable
fun NoteTreeItem(
    item: FlattenedNote,
    isDragging: Boolean = false,
    isDropTarget: Boolean = false,
    dragOffsetY: Float = 0f,
    onExpandClick: () -> Unit,
    onNoteClick: () -> Unit,
    onAddChildClick: () -> Unit,
    onTagClick: (String) -> Unit = {},
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {}
) {
    val noteTags = remember(item.note.title, item.note.content) {
        TagExtractor.extract(item.note.title + " " + item.note.content)
            .filter { !it.contains("/") }
            .take(5)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(start = (item.depth * 20).dp)
            .zIndex(if (isDragging) 1f else 0f)
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .alpha(if (isDragging) 0.85f else 1f)
            .scale(if (isDragging) 1.03f else 1f)
            .pointerInput(item.note.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { _ -> onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.y)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() }
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.depth > 0) {
            Canvas(modifier = Modifier.width(16.dp).fillMaxHeight()) {
                drawLine(
                    color = Color.LightGray.copy(alpha = 0.5f),
                    start = Offset(x = size.width / 2, y = 0f),
                    end = Offset(x = size.width / 2, y = size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        IconButton(onClick = { if (item.hasChildren) onExpandClick() }, enabled = item.hasChildren) {
            if (item.hasChildren) {
                Icon(
                    imageVector = if (item.isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = md_theme_light_onSurfaceVariant
                )
            } else {
                Icon(Icons.Default.Circle, null, modifier = Modifier.size(6.dp), tint = Color.LightGray)
            }
        }

        Card(
            modifier = Modifier.weight(1f).padding(vertical = 4.dp).clickable { onNoteClick() },
            colors = CardDefaults.cardColors(
                containerColor = if (isDropTarget) md_theme_light_primaryContainer else md_theme_light_surfaceContainerHigh
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.note.isPinned) {
                            Icon(Icons.Default.PushPin, null, modifier = Modifier.size(12.dp), tint = md_theme_light_onPrimaryContainer)
                            Spacer(Modifier.width(4.dp))
                        }
                        if (item.note.isLocked) {
                            Icon(Icons.Default.Lock, null, modifier = Modifier.size(12.dp), tint = md_theme_light_onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                        }
                        if (item.note.reminderEnabled && item.note.reminderTime != null && item.note.reminderTime > System.currentTimeMillis()) {
                            Icon(Icons.Default.Alarm, null, modifier = Modifier.size(12.dp), tint = md_theme_link_color)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = if (item.note.title.isBlank()) "Bez tytułu" else item.note.title,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val preview = item.note.content
                    if (preview.isNotBlank() && !preview.startsWith("ENC:")) {
                        Text(
                            text = preview.replace(Regex("^#+\\s*|\\*\\*|\\[\\[|]]|-\\s\\[.\\]\\s|#[\\wÀ-ɏ/]+"), "").trim(),
                            fontSize = 12.sp,
                            color = md_theme_light_onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (item.note.isLocked) {
                        Text("••••••••", fontSize = 12.sp, color = md_theme_light_onSurfaceVariant)
                    }
                    if (noteTags.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            noteTags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = md_theme_light_primaryContainer,
                                    modifier = Modifier.clickable { onTagClick(tag) }
                                ) {
                                    Text(
                                        "#$tag",
                                        fontSize = 10.sp,
                                        color = md_theme_light_onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                IconButton(onClick = { onAddChildClick() }) {
                    Icon(Icons.Default.SubdirectoryArrowRight, "Dodaj podnotatkę", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ─── MARKDOWN PREVIEW ──────────────────────────────────────────────────────────

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
    val annotated = buildRichAnnotatedString(text)
    ClickableText(
        text = annotated,
        style = LocalTextStyle.current,
        onClick = { offset ->
            annotated.getStringAnnotations("BACKLINK", offset, offset)
                .firstOrNull()?.let { onBacklinkClick(it.item) }
        }
    )
}

fun buildRichAnnotatedString(text: String): AnnotatedString = buildAnnotatedString {
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
            withStyle(SpanStyle(color = md_theme_link_color, textDecoration = TextDecoration.Underline)) {
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

// ─── NOTE EDITOR (legacy — replaced by BlockEditorScreen in BlockEditor.kt) ───

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
            val wordMatch = Regex("""#([\wÀ-ɏ/]*)$""").find(beforeCursor)
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
                                            val wordMatch = Regex("""#([\wÀ-ɏ/]*)$""").find(beforeCursor)
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

// ─── REMINDER DIALOG ───────────────────────────────────────────────────────────

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

// ─── TEMPLATE SCREEN ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateScreen(
    noteTemplateViewModel: NoteTemplateViewModel,
    onBack: () -> Unit
) {
    val templates by noteTemplateViewModel.allTemplates.collectAsState()
    var applyTarget by remember { mutableStateOf<NoteTemplate?>(null) }

    if (applyTarget != null) {
        AlertDialog(
            onDismissRequest = { applyTarget = null },
            icon = { Icon(Icons.Default.ContentCopy, null) },
            title = { Text("Zastosuj szablon") },
            text = { Text("Szablon \"${applyTarget!!.name}\" zostanie wstawiony jako notatka główna (${noteTemplateViewModel.countNodes(applyTarget!!)} węzłów).") },
            confirmButton = {
                TextButton(onClick = {
                    noteTemplateViewModel.applyTemplate(applyTarget!!, null)
                    applyTarget = null
                }) { Text("Wstaw") }
            },
            dismissButton = {
                TextButton(onClick = { applyTarget = null }) { Text("Anuluj") }
            }
        )
    }

    Scaffold(
        containerColor = md_theme_light_background,
        topBar = {
            TopAppBar(
                title = { Text("Szablony", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = md_theme_light_background)
            )
        }
    ) { padding ->
        if (templates.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(64.dp), tint = md_theme_light_onSurfaceVariant.copy(alpha = 0.4f))
                    Text("Brak szablonów", color = md_theme_light_onSurfaceVariant)
                    Text(
                        "Otwórz notatkę i użyj przycisku  aby zapisać ją jako szablon.",
                        color = md_theme_light_onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(templates, key = { it.id }) { template ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = md_theme_light_surfaceContainerHigh)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(template.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${noteTemplateViewModel.countNodes(template)} notatek",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = md_theme_light_onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { applyTarget = template }) {
                                Icon(Icons.Default.PlayArrow, "Zastosuj", tint = md_theme_link_color)
                            }
                            IconButton(onClick = { noteTemplateViewModel.deleteTemplate(template) }) {
                                Icon(Icons.Default.Delete, "Usuń", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── CHAT SCREEN ───────────────────────────────────────────────────────────────

private const val MODUNOTE_SYSTEM_PROMPT = """Jestes asystentem AI w aplikacji ModuNote - notatniku opartym na blokach (jak Notion).

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
        // re-parse and re-serialize via BlockSerializer so Gson types are guaranteed correct
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
            icon = { Icon(Icons.Default.NoteAdd, null, tint = Color(0xFF6650A4)) },
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
                errorMsg = e.message ?: "Blad polaczenia"
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

            // Auto-detected structured note → prominent save card
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
                        Icon(Icons.Default.NoteAdd, null, tint = Color(0xFF6650A4), modifier = Modifier.size(22.dp))
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

            // All AI messages (no structured note) → small manual save chip
            if (!isUser && noteJson == null && onSaveNote != null && msg.content.isNotBlank()) {
                TextButton(
                    onClick = { onSaveNote("Notatka AI", aiTextToBlocksJson(msg.content)) },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.NoteAdd, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Zapisz jako notatkę", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ─── CALENDAR SCREEN ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: CalendarEventViewModel, onBack: () -> Unit) {
    val today = Calendar.getInstance()
    var displayYear by remember { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var displayMonth by remember { mutableIntStateOf(today.get(Calendar.MONTH)) }
    var selectedDay by remember { mutableIntStateOf(today.get(Calendar.DAY_OF_MONTH)) }

    val monthEvents by remember(displayYear, displayMonth) {
        viewModel.eventsForMonth(displayYear, displayMonth)
    }.collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<CalendarEvent?>(null) }

    val dayFmt = SimpleDateFormat("d MMMM yyyy", Locale("pl"))
    val timeFmt = SimpleDateFormat("HH:mm", Locale("pl"))
    val monthFmt = SimpleDateFormat("LLLL yyyy", Locale("pl"))

    fun epochForDay(day: Int, hour: Int = 8, minute: Int = 0): Long {
        return Calendar.getInstance().apply {
            set(displayYear, displayMonth, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val eventsByDay = remember(monthEvents) {
        monthEvents.groupBy {
            Calendar.getInstance().apply { timeInMillis = it.startTime }.get(Calendar.DAY_OF_MONTH)
        }
    }

    val daysInMonth = Calendar.getInstance().apply {
        set(displayYear, displayMonth, 1)
    }.getActualMaximum(Calendar.DAY_OF_MONTH)

    val firstDayOfWeek = Calendar.getInstance().apply {
        set(displayYear, displayMonth, 1)
    }.get(Calendar.DAY_OF_WEEK).let { if (it == Calendar.SUNDAY) 6 else it - 2 }

    val selectedDayEvents = eventsByDay[selectedDay] ?: emptyList()

    if (showAddDialog || editingEvent != null) {
        EventDialog(
            event = editingEvent,
            defaultStartEpoch = epochForDay(selectedDay),
            onDismiss = { showAddDialog = false; editingEvent = null },
            onSave = { title, desc, start, end ->
                if (editingEvent != null) {
                    viewModel.deleteEvent(editingEvent!!)
                }
                viewModel.addEvent(title, desc, start, end, null)
                showAddDialog = false; editingEvent = null
            },
            onDelete = editingEvent?.let { ev -> { viewModel.deleteEvent(ev); editingEvent = null } }
        )
    }

    Scaffold(
        containerColor = md_theme_light_background,
        topBar = {
            TopAppBar(
                title = { Text("Kalendarz", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = md_theme_light_background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "Dodaj wydarzenie")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (displayMonth == 0) { displayMonth = 11; displayYear-- } else displayMonth--
                    selectedDay = 1
                }) { Icon(Icons.Default.ArrowBack, "Poprzedni") }
                Text(
                    text = monthFmt.format(Calendar.getInstance().apply { set(displayYear, displayMonth, 1) }.time)
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = {
                    if (displayMonth == 11) { displayMonth = 0; displayYear++ } else displayMonth++
                    selectedDay = 1
                }) { Icon(Icons.Default.ArrowForward, "Następny") }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                listOf("Pn", "Wt", "Śr", "Cz", "Pt", "Sb", "Nd").forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = md_theme_light_onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            val cells = firstDayOfWeek + daysInMonth
            val totalCells = if (cells % 7 == 0) cells else cells + (7 - cells % 7)
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                userScrollEnabled = false
            ) {
                items(totalCells) { index ->
                    val day = index - firstDayOfWeek + 1
                    if (day < 1 || day > daysInMonth) {
                        Box(modifier = Modifier.aspectRatio(1f))
                    } else {
                        val isToday = day == today.get(Calendar.DAY_OF_MONTH) &&
                            displayMonth == today.get(Calendar.MONTH) &&
                            displayYear == today.get(Calendar.YEAR)
                        val isSelected = day == selectedDay
                        val hasEvents = eventsByDay.containsKey(day)
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .then(
                                    if (isSelected) Modifier.background(md_theme_link_color, CircleShape)
                                    else if (isToday) Modifier.border(1.5.dp, md_theme_link_color, CircleShape)
                                    else Modifier
                                )
                                .clickable { selectedDay = day },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = day.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) Color.White else md_theme_light_onSurface,
                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (hasEvents) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .background(
                                                if (isSelected) Color.White else md_theme_link_color,
                                                CircleShape
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            val selCal = Calendar.getInstance().apply { set(displayYear, displayMonth, selectedDay) }
            Text(
                text = dayFmt.format(selCal.time).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (selectedDayEvents.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Brak wydarzeń", color = md_theme_light_onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    items(selectedDayEvents) { event ->
                        EventCard(event = event, timeFmt = timeFmt, onClick = { editingEvent = event })
                    }
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: CalendarEvent, timeFmt: SimpleDateFormat, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = md_theme_light_surfaceContainerHigh)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(4.dp).height(40.dp).background(Color(event.color), RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(event.title, fontWeight = FontWeight.SemiBold)
                Text(
                    "${timeFmt.format(Date(event.startTime))} – ${timeFmt.format(Date(event.endTime))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = md_theme_light_onSurfaceVariant
                )
                if (event.description.isNotBlank()) {
                    Text(event.description, style = MaterialTheme.typography.bodySmall, color = md_theme_light_onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun EventDialog(
    event: CalendarEvent?,
    defaultStartEpoch: Long,
    onDismiss: () -> Unit,
    onSave: (String, String, Long, Long) -> Unit,
    onDelete: (() -> Unit)?
) {
    var title by remember { mutableStateOf(event?.title ?: "") }
    var description by remember { mutableStateOf(event?.description ?: "") }

    val startCal = Calendar.getInstance().apply {
        timeInMillis = event?.startTime ?: defaultStartEpoch
    }
    val endCal = Calendar.getInstance().apply {
        timeInMillis = event?.endTime ?: (defaultStartEpoch + 3600_000L)
    }
    var startHour by remember { mutableIntStateOf(startCal.get(Calendar.HOUR_OF_DAY)) }
    var startMin by remember { mutableIntStateOf(startCal.get(Calendar.MINUTE)) }
    var endHour by remember { mutableIntStateOf(endCal.get(Calendar.HOUR_OF_DAY)) }
    var endMin by remember { mutableIntStateOf(endCal.get(Calendar.MINUTE)) }

    fun buildEpoch(baseCal: Calendar, hour: Int, min: Int): Long {
        return Calendar.getInstance().apply {
            set(baseCal.get(Calendar.YEAR), baseCal.get(Calendar.MONTH), baseCal.get(Calendar.DAY_OF_MONTH), hour, min, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (event == null) "Nowe wydarzenie" else "Edytuj wydarzenie") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tytuł *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Opis") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = "%02d:%02d".format(startHour, startMin),
                        onValueChange = {
                            it.split(":").let { parts ->
                                parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23)?.let { h -> startHour = h }
                                parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59)?.let { m -> startMin = m }
                            }
                        },
                        label = { Text("Od") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = "%02d:%02d".format(endHour, endMin),
                        onValueChange = {
                            it.split(":").let { parts ->
                                parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23)?.let { h -> endHour = h }
                                parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59)?.let { m -> endMin = m }
                            }
                        },
                        label = { Text("Do") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(
                            title.trim(),
                            description.trim(),
                            buildEpoch(startCal, startHour, startMin),
                            buildEpoch(endCal, endHour, endMin)
                        )
                    }
                },
                enabled = title.isNotBlank()
            ) { Text("Zapisz") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Usuń", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Anuluj") }
            }
        }
    )
}

// ─── HELPERS ───────────────────────────────────────────────────────────────────

fun TextFieldValue.insertAtCursor(textToInsert: String): TextFieldValue {
    val newText = text.replaceRange(selection.start, selection.end, textToInsert)
    return copy(text = newText, selection = TextRange(selection.start + textToInsert.length))
}
