package com.example.modunote.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.modunote.MainActivity
import com.example.modunote.data.local.AppDatabase
import com.example.modunote.data.local.Note
import kotlinx.coroutines.flow.first

class NoteWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val pinnedNotes = try {
            AppDatabase.getDatabase(context)
                .noteDao()
                .getAllNotes()
                .first()
                .filter { it.isPinned && !it.content.startsWith("ENC:") }
                .take(5)
        } catch (_: Exception) {
            emptyList()
        }

        provideContent {
            WidgetContent(pinnedNotes)
        }
    }
}

@Composable
private fun WidgetContent(notes: List<Note>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(12.dp)
            .background(Color(0xFFFEF7FF))
            .clickable(onClick = actionStartActivity<MainActivity>())
    ) {
        Text(
            text = "ModuNote",
            style = TextStyle(fontWeight = FontWeight.Bold)
        )
        if (notes.isEmpty()) {
            Text(
                text = "Brak przypiętych notatek",
                modifier = GlanceModifier.padding(top = 4.dp)
            )
        } else {
            notes.forEach { note ->
                Text(
                    text = "• ${note.title.ifBlank { "Bez tytułu" }}",
                    modifier = GlanceModifier.padding(top = 4.dp)
                )
            }
        }
    }
}

class NoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = NoteWidget()
}
