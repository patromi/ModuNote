package com.example.modunote.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Note::class, NoteTemplate::class, NoteTag::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun noteTemplateDao(): NoteTemplateDao
    abstract fun noteTagDao(): NoteTagDao

    companion object {
        @Volatile
        private var Instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "modunote_db")
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { Instance = it }
            }
        }
    }
}
