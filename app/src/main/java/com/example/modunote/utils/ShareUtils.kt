package com.example.modunote.utils

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TimePicker
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavController
import androidx.navigation.NavType
import com.example.modunote.*
import com.example.modunote.data.local.*
import com.example.modunote.data.local.Block
import com.example.modunote.data.local.BlockType
import com.example.modunote.data.local.FlattenedNote
import com.example.modunote.data.local.Note
import com.example.modunote.data.local.NoteTemplate
import com.example.modunote.data.local.NoteTemplateViewModel
import com.example.modunote.data.local.NoteViewModel
import com.example.modunote.data.local.TagViewModel
import com.example.modunote.model.ui.*
import com.example.modunote.network.ChatMessage
import com.example.modunote.network.ChatRequest
import com.example.modunote.network.OpenRouterClient
import com.example.modunote.ui.blocks.*
import com.example.modunote.ui.components.*
import com.example.modunote.ui.screens.*
import com.example.modunote.ui.screens.BlockEditorScreen
import com.example.modunote.ui.theme.*
import com.example.modunote.ui.theme.LocalEnergySavingActive
import com.example.modunote.ui.theme.ModuNoteTheme
import com.example.modunote.utils.*
import com.example.modunote.utils.BlockSerializer
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker









fun shareNote(context: Context, noteTitle: String, blocks: List<Block>) {
    val text = StringBuilder()
    text.append("# ").append(noteTitle).append("\n\n")
    var bulletIdx = 1
    blocks.forEachIndexed { idx, block ->
        when (block.type) {
            BlockType.H1 -> text.append("# ").append(block.text).append("\n")
            BlockType.H2 -> text.append("## ").append(block.text).append("\n")
            BlockType.H3 -> text.append("### ").append(block.text).append("\n")
            BlockType.BULLET -> text.append("Ă˘â‚¬Ë ").append(block.text).append("\n")
            BlockType.NUMBERED -> {
                text.append("${bulletIdx}. ").append(block.text).append("\n")
                val nextIsNumbered = blocks.getOrNull(idx + 1)?.type == BlockType.NUMBERED
                if (nextIsNumbered) {
                    bulletIdx++
                } else {
                    bulletIdx = 1
                }
            }
            BlockType.TODO -> {
                val check = if (block.checked) "[x]" else "[ ]"
                text.append(check).append(" ").append(block.text).append("\n")
            }
            BlockType.QUOTE -> text.append("> ").append(block.text).append("\n")
            BlockType.CODE -> text.append("```\n").append(block.text).append("\n```\n")
            BlockType.DIVIDER -> text.append("---\n")
            BlockType.MAP -> {
                val addr = block.address ?: "${block.latitude}, ${block.longitude}"
                text.append("Ä‘Ĺşâ€śĹ¤ Mapa: ").append(addr).append("\n")
            }
            BlockType.LINK -> {
                val label = block.text.ifBlank { block.url }
                text.append("Ä‘Ĺşâ€ťâ€” ").append(label).append(" (").append(block.url).append(")\n")
            }
            else -> text.append(block.text).append("\n")
        }
    }

    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, noteTitle)
        putExtra(android.content.Intent.EXTRA_TEXT, text.toString())
    }
    val chooser = android.content.Intent.createChooser(intent, "UdostÄ™pnij notatkÄ™ za pomocÄ…")
    context.startActivity(chooser)
}




