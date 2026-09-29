package com.onikki.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.onikki.app.data.db.entity.VocabWord
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabWordDao {
    @Query("SELECT * FROM vocab_words ORDER BY english COLLATE NOCASE")
    fun observeAll(): Flow<List<VocabWord>>

    @Query("SELECT * FROM vocab_words")
    suspend fun getAll(): List<VocabWord>

    @Insert
    suspend fun insert(word: VocabWord): Long

    @Insert
    suspend fun insertAll(words: List<VocabWord>)

    @Update
    suspend fun update(word: VocabWord)

    @Delete
    suspend fun delete(word: VocabWord)

    @Query("UPDATE vocab_words SET correctCount = correctCount + :correct, wrongCount = wrongCount + :wrong, lastAskedAt = :at WHERE id = :id")
    suspend fun recordAnswer(id: Long, correct: Int, wrong: Int, at: Long)
}
