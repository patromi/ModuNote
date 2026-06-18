package com.example.modunote.model.ui



import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modunote.data.local.BlockType


data class SlashCommand(val trigger: String, val label: String, val hint: String, val type: BlockType, val icon: @Composable () -> Unit)

val SLASH_COMMANDS = listOf(
    SlashCommand("tekst", "Tekst", "Zwykły akapit", BlockType.PARAGRAPH, { Icon(Icons.Default.TextFields, null, Modifier.size(18.dp)) }),
    SlashCommand("h1", "Nagłówek 1", "Duży tytuł", BlockType.H1, { Text("H1", fontWeight = FontWeight.Bold, fontSize = 14.sp) }),
    SlashCommand("h2", "Nagłówek 2", "Średni tytuł", BlockType.H2, { Text("H2", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }),
    SlashCommand("h3", "Nagłówek 3", "Mały tytuł", BlockType.H3, { Text("H3", fontWeight = FontWeight.Medium, fontSize = 12.sp) }),
    SlashCommand("lista", "Lista punktowa", "- element", BlockType.BULLET, { Text("-", fontSize = 18.sp) }),
    SlashCommand("numerowana", "Lista numerowana", "1. element", BlockType.NUMBERED, { Text("1.", fontSize = 13.sp) }),
    SlashCommand("zadanie", "Zadanie", "Checkbox do odhaczenia", BlockType.TODO, { Icon(Icons.Default.CheckBox, null, Modifier.size(18.dp)) }),
    SlashCommand("cytat", "Cytat", "Blok cytatu", BlockType.QUOTE, { Icon(Icons.Default.FormatQuote, null, Modifier.size(18.dp)) }),
    SlashCommand("podzial", "Linia", "Poziomy separator", BlockType.DIVIDER, { Icon(Icons.Default.HorizontalRule, null, Modifier.size(18.dp)) }),
    SlashCommand("kod", "Kod", "Blok kodu z monospace", BlockType.CODE, { Icon(Icons.Default.Code, null, Modifier.size(18.dp)) }),
    SlashCommand("mapa", "Mapa", "Lokalizacja na mapie", BlockType.MAP, { Icon(Icons.Default.Map, null, Modifier.size(18.dp)) }),
    SlashCommand("link", "Link URL", "Otwiera stronę w aplikacji", BlockType.LINK, { Icon(Icons.Default.Link, null, Modifier.size(18.dp)) }),
)

