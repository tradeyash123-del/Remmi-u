package com.example.jarvis

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Main Room Database for the Jarvis App.
 */
@Database(entities = [Workflow::class], version = 1, exportSchema = false)
abstract class JarvisDatabase : RoomDatabase() {

    abstract fun workflowDao(): WorkflowDao

    companion object {
        @Volatile
        private var INSTANCE: JarvisDatabase? = null

        fun getDatabase(context: Context): JarvisDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
