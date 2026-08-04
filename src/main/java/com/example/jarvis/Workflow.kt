package com.example.jarvis

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a custom workflow stored locally using Room.
 */
@Entity(tableName = "workflows")
data class Workflow(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val triggerPhrase: String,
    val actionCommandJson: String
)
