package com.example.modunote.ui.blocks

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.modunote.data.local.Block
import com.example.modunote.ui.theme.md_theme_light_onSurface
import com.example.modunote.ui.theme.md_theme_light_surfaceContainerHigh
import com.example.modunote.ui.theme.md_theme_link_color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.net.HttpURLConnection
import java.net.URL

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
                            text = block.address,
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


