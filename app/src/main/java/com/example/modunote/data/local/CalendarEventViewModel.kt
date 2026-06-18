package com.example.modunote.data.local

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class CalendarEventViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.getDatabase(app).calendarEventDao()

    val allEvents = dao.getAllEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun eventsForMonth(year: Int, month: Int) = run {
        val cal = Calendar.getInstance().apply { set(year, month, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val end = cal.timeInMillis
        dao.getEventsInRange(start, end)
    }

    fun addEvent(title: String, description: String, startTime: Long, endTime: Long, noteId: Int?) {
        viewModelScope.launch {
            dao.insert(CalendarEvent(title = title, description = description, startTime = startTime, endTime = endTime, noteId = noteId))
        }
    }

    fun deleteEvent(event: CalendarEvent) = viewModelScope.launch { dao.delete(event) }
}
