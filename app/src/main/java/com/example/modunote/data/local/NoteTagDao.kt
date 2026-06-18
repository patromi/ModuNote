package com.example.modunote.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteTagDao {
    @Query("SELECT DISTINCT tag FROM note_tags ORDER BY tag ASC")
    fun getAllTags(): Flow<List<String>>

    @Query("SELECT DISTINCT noteId FROM note_tags WHERE tag = :tag OR tag LIKE :tag || '/%'")
    fun getNoteIdsByTag(tag: String): Flow<List<Int>>

    @Query("SELECT DISTINCT tag FROM note_tags WHERE tag LIKE :prefix || '%' ORDER BY tag ASC LIMIT 10")
    suspend fun getTagSuggestions(prefix: String): List<String>

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    suspend fun deleteTagsForNote(noteId: Int)

    @Insert
    suspend fun insertAll(tags: List<NoteTag>)

    @Transaction
    suspend fun replaceTagsForNote(noteId: Int, tags: List<String>) {
        deleteTagsForNote(noteId)
        if (tags.isNotEmpty()) insertAll(tags.map { NoteTag(noteId = noteId, tag = it) })
    }
}
