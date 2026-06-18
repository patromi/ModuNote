package com.example.modunote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.modunote.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        CoroutineScope(Dispatchers.IO).launch {
            val now = System.currentTimeMillis()
            AppDatabase.getDatabase(context).noteDao().getAllNotes().first()
                .filter { it.reminderEnabled && it.reminderTime != null && it.reminderTime > now }
                .forEach { note ->
                    ReminderScheduler.schedule(context, note.id, note.title, note.reminderTime!!)
                }
        }
    }
}
