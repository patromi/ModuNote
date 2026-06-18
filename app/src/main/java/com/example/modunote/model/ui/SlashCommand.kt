package com.example.modunote.model.ui


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

