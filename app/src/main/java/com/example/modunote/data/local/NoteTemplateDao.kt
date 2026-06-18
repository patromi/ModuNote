package com.example.modunote.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteTemplateDao {
    @Query("SELECT * FROM note_templates ORDER BY createdAt DESC")
    fun getAllTemplates(): Flow<List<NoteTemplate>>

    @Insert
    suspend fun insert(template: NoteTemplate): Long

    @Delete
    suspend fun delete(template: NoteTemplate)
}
