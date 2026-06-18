package com.example.modunote.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendar_events")
data class CalendarEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String = "",
    val startTime: Long,
    val endTime: Long,
    val noteId: Int? = null,
    val color: Long = 0xFF6650A4L
)
