package com.example.modunote.data.local

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.modunote.ReminderScheduler
import com.example.modunote.TagExtractor
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FlattenedNote(
    val note: Note,
    val depth: Int,
    val hasChildren: Boolean,
    val isExpanded: Boolean
)

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val noteDao = AppDatabase.getDatabase(application).noteDao()
    private val tagDao = AppDatabase.getDatabase(application).noteTagDao()
    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()

    private val _expandedIds = MutableStateFlow<Set<Int>>(emptySet())
    val expandedIds: StateFlow<Set<Int>> = _expandedIds

    private val _authenticatedNoteIds = MutableStateFlow<Set<Int>>(emptySet())
    val authenticatedNoteIds: StateFlow<Set<Int>> = _authenticatedNoteIds

    val flattenedNotes: Flow<List<FlattenedNote>> = combine(allNotes, _expandedIds) { notes, expanded ->
        val result = mutableListOf<FlattenedNote>()
        fun flatten(parentId: Int?, depth: Int) {
            notes.filter { it.parentId == parentId }.forEach { child ->
                val isExpanded = expanded.contains(child.id)
                val hasChildren = notes.any { it.parentId == child.id }
                result.add(FlattenedNote(child, depth, hasChildren, isExpanded))
                if (isExpanded) flatten(child.id, depth + 1)
            }
        }
        flatten(null, 0)
        result
    }

    fun toggleExpanded(noteId: Int) {
        _expandedIds.value = if (_expandedIds.value.contains(noteId)) {
            _expandedIds.value - noteId
        } else {
            _expandedIds.value + noteId
        }
    }

    fun markAuthenticated(noteId: Int) {
        _authenticatedNoteIds.value = _authenticatedNoteIds.value + noteId
    }

    fun getNoteById(id: Int): Flow<Note?> = noteDao.getNoteById(id)

    fun getBreadcrumbs(noteId: Int): Flow<List<Note>> = noteDao.getBreadcrumbs(noteId)

    fun insertNote(title: String, content: String, parentId: Int? = null, onResult: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = noteDao.insertNote(Note(title = title, content = content, parentId = parentId))
            onResult(id)
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch { noteDao.updateNote(note) }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch { noteDao.deleteNote(note) }
    }

    fun togglePinned(note: Note) {
        viewModelScope.launch {
            noteDao.updateNote(note.copy(isPinned = !note.isPinned, updatedAt = System.currentTimeMillis()))
        }
    }

    fun moveNote(noteId: Int, newParentId: Int?) {
        viewModelScope.launch {
            noteDao.getNoteById(noteId).firstOrNull()?.let { note ->
                noteDao.updateNote(note.copy(parentId = newParentId, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun setReminder(noteId: Int, timeMs: Long, enabled: Boolean, context: Context) {
        viewModelScope.launch {
            noteDao.getNoteById(noteId).firstOrNull()?.let { note ->
                val updated = note.copy(reminderTime = timeMs, reminderEnabled = enabled)
                noteDao.updateNote(updated)
                if (enabled) {
                    ReminderScheduler.schedule(context, noteId, note.title, timeMs)
                } else {
                    ReminderScheduler.cancel(context, noteId)
                }
            }
        }
    }

    fun updateNoteWithTags(note: Note) {
        viewModelScope.launch {
            noteDao.updateNote(note.copy(updatedAt = System.currentTimeMillis()))
            val tags = TagExtractor.extract(note.title + " " + note.content)
            tagDao.replaceTagsForNote(note.id, tags)
        }
    }
}
