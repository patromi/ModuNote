package com.example.modunote.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "note_tags",
    indices = [Index("noteId"), Index("tag")]
)
data class NoteTag(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val noteId: Int,
    val tag: String
)
