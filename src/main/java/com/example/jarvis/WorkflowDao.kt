package com.example.jarvis

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Workflows.
 */
@Dao
interface WorkflowDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkflow(workflow: Workflow): Long

    @Query("SELECT * FROM workflows")
    fun getAllWorkflows(): Flow<List<Workflow>>

    @Query("SELECT * FROM workflows WHERE triggerPhrase = :phrase LIMIT 1")
    suspend fun getWorkflowByTrigger(phrase: String): Workflow?

    @Query("DELETE FROM workflows WHERE id = :id")
    suspend fun deleteWorkflow(id: Long)
}
