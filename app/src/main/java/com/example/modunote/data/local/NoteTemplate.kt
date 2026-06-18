package com.example.modunote.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "note_templates")
data class NoteTemplate(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val jsonTree: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class TemplateNode(
    val title: String = "",
    val content: String = "",
    val children: List<TemplateNode> = emptyList()
)
