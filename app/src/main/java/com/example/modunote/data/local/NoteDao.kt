package com.example.modunote.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE parentId = :parentId ORDER BY updatedAt DESC")
    fun getNotesByParent(parentId: Int?): Flow<List<Note>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("SELECT * FROM notes WHERE id = :id")
    fun getNoteById(id: Int): Flow<Note?>

    @Query("""
        WITH RECURSIVE path(id, parentId, title, createdAt, updatedAt, content, isPinned, isLocked, reminderTime, reminderEnabled, level) AS (
            SELECT id, parentId, title, createdAt, updatedAt, content, isPinned, isLocked, reminderTime, reminderEnabled, 0
            FROM notes WHERE id = :noteId
            UNION ALL
            SELECT n.id, n.parentId, n.title, n.createdAt, n.updatedAt, n.content, n.isPinned, n.isLocked, n.reminderTime, n.reminderEnabled, p.level + 1
            FROM notes n
            JOIN path p ON n.id = p.parentId
        )
        SELECT id, parentId, title, createdAt, updatedAt, content, isPinned, isLocked, reminderTime, reminderEnabled
        FROM path ORDER BY level DESC
    """)
    fun getBreadcrumbs(noteId: Int): Flow<List<Note>>
}
