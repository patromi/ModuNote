package com.example.modunote.data.local

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class TagViewModel(app: Application) : AndroidViewModel(app) {
    private val tagDao = AppDatabase.getDatabase(app).noteTagDao()

    val allTags: StateFlow<List<String>> = tagDao.getAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rootTags: StateFlow<List<String>> = allTags.map { tags ->
        tags.filter { !it.contains("/") }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun childTags(parent: String): List<String> =
        allTags.value.filter { it.startsWith("$parent/") && it.removePrefix("$parent/").none { c -> c == '/' } }

    fun getNoteIdsByTag(tag: String) = tagDao.getNoteIdsByTag(tag)

    suspend fun getSuggestions(prefix: String): List<String> =
        tagDao.getTagSuggestions(prefix)
}
