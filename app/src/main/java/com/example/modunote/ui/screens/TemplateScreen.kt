package com.example.modunote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.modunote.data.local.NoteTemplate
import com.example.modunote.data.local.NoteTemplateViewModel
import com.example.modunote.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateScreen(
    noteTemplateViewModel: NoteTemplateViewModel,
    navController: NavController
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = md_theme_light_background)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = md_theme_light_background) {
                NavigationBarItem(
                    selected = false,
                    onClick = {
                        navController.navigate("home") {
                            popUpTo("home")
                        }
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
                    label = { Text("Notatki", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("chat") },
                    icon = { Icon(Icons.Default.SmartToy, null) },
                    label = { Text("Chat AI", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Default.ContentCopy, null) },
                    label = { Text("Szablony", fontSize = 11.sp) }
                )
            }
        }
    ) { padding ->
        if (templates.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(64.dp), tint = md_theme_light_onSurfaceVariant.copy(alpha = 0.4f))
                    Text("Brak szablonów", color = md_theme_light_onSurfaceVariant)
                    Text(
                        "Otwórz notatkę i użyj przycisku, aby zapisać ja jako szablon.",
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
