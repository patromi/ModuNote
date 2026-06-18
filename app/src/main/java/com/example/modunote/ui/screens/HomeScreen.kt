package com.example.modunote.ui.screens

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import com.example.modunote.*
import com.example.modunote.data.local.*
import com.example.modunote.data.local.FlattenedNote
import com.example.modunote.data.local.Note
import com.example.modunote.data.local.NoteViewModel
import com.example.modunote.data.local.TagViewModel
import com.example.modunote.ui.theme.*
import com.example.modunote.utils.*
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.flowOf

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
        allNotes.filter { it.isPinned }
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
                // —— Pinned section ——
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

                // —— Notes header ——
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

                // —— Tree list ——
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
                        modifier = Modifier.animateItem(),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (note.isLocked) {
                        Icon(
                            Icons.Default.Lock,
                            null,
                            modifier = Modifier.size(12.dp),
                            tint = md_theme_light_onPrimaryContainer
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = note.title.ifBlank { "Bez tytułu" },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = md_theme_light_onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (note.content.isNotBlank() && !note.isLocked) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = com.example.modunote.utils.BlockSerializer.getPreviewText(note.content),
                        fontSize = 11.sp,
                        color = md_theme_light_onPrimaryContainer.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (note.isLocked) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "••••••••",
                        fontSize = 11.sp,
                        color = md_theme_light_onPrimaryContainer.copy(alpha = 0.7f)
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
                    val preview = if (item.note.isLocked) "" else com.example.modunote.utils.BlockSerializer.getPreviewText(item.note.content)
                    if (preview.isNotBlank()) {
                        Text(
                            text = preview,
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
