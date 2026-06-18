package com.example.modunote.data.local

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteTemplateViewModel(app: Application) : AndroidViewModel(app) {
    private val templateDao = AppDatabase.getDatabase(app).noteTemplateDao()
    private val noteDao = AppDatabase.getDatabase(app).noteDao()
    private val gson = Gson()

    val allTemplates = templateDao.getAllTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveTemplate(name: String, rootNoteId: Int, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val allNotes = noteDao.getAllNotes().first()
            val root = allNotes.find { it.id == rootNoteId } ?: return@launch
            val tree = buildNode(root, allNotes)
            templateDao.insert(NoteTemplate(name = name, jsonTree = gson.toJson(tree)))
            onDone()
        }
    }

    fun applyTemplate(template: NoteTemplate, parentNoteId: Int?, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val root = runCatching { gson.fromJson(template.jsonTree, TemplateNode::class.java) }
                .getOrNull() ?: return@launch
            insertSubtree(root, parentNoteId)
            onDone()
        }
    }

    fun deleteTemplate(template: NoteTemplate) {
        viewModelScope.launch { templateDao.delete(template) }
    }

    fun countNodes(template: NoteTemplate): Int {
        val root = runCatching { gson.fromJson(template.jsonTree, TemplateNode::class.java) }
            .getOrNull() ?: return 0
        return countNodes(root)
    }

    private fun buildNode(note: Note, all: List<Note>): TemplateNode =
        TemplateNode(
            title = note.title,
            content = note.content,
            children = all.filter { it.parentId == note.id }.map { buildNode(it, all) }
        )

    private suspend fun insertSubtree(node: TemplateNode, parentId: Int?) {
        val newId = noteDao.insertNote(
            Note(title = node.title, content = node.content, parentId = parentId)
        ).toInt()
        node.children.forEach { insertSubtree(it, newId) }
    }

    private fun countNodes(node: TemplateNode): Int =
        1 + node.children.sumOf { countNodes(it) }
}
