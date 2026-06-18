package com.example.modunote.ui.blocks

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


