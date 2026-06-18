package com.example.modunote.ui.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.modunote.WebViewDialog
import com.example.modunote.data.local.Block
import com.example.modunote.data.local.BlockType
import com.example.modunote.model.ui.SlashCommand
import com.example.modunote.ui.theme.md_theme_light_onSurface
import com.example.modunote.ui.theme.md_theme_light_onSurfaceVariant
import com.example.modunote.ui.theme.md_theme_light_surfaceContainerHigh
import com.example.modunote.ui.theme.md_theme_link_color
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
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
    onFocus: () -> Unit,
    onTextChange: (String) -> Unit,
    onToggleCheck: () -> Unit,
    onDelete: () -> Unit,
    onSlashSelect: (SlashCommand) -> Unit,
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
            BlockType.BULLET -> Text("-", modifier = Modifier.padding(end = 8.dp, top = 4.dp), fontSize = 16.sp, color = md_theme_light_onSurface)
            BlockType.NUMBERED -> Text("$numberedIndex.", modifier = Modifier.padding(end = 6.dp, top = 4.dp), fontSize = 14.sp, color = md_theme_light_onSurfaceVariant)
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
            // URL Link block
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
                                    BlockType.PARAGRAPH -> "Wpisz '/', aby dodać blok"
                                    BlockType.H1 -> "Nagłówek 1"
                                    BlockType.H2 -> "Nagłówek 2"
                                    BlockType.H3 -> "Nagłówek 3"
                                    BlockType.CODE -> "// wpisz kod"
                                    BlockType.QUOTE -> "Cytat"
                                    BlockType.BULLET -> "Element listy"
                                    BlockType.NUMBERED -> "Element listy"
                                    BlockType.TODO -> "Zadanie"
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
