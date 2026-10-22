package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.CategoryEntity
import com.example.data.model.InspectionEntity
import com.example.data.model.PhotoEntity

@Database(
    entities = [
        InspectionEntity::class,
        CategoryEntity::class,
        PhotoEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class InspectCamDatabase : RoomDatabase() {
    abstract fun inspectionDao(): InspectionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun photoDao(): PhotoDao

    companion object {
        @Volatile
        private var INSTANCE: InspectCamDatabase? = null

        val DEFAULT_CATEGORIES = listOf(
            "Front View",
            "Back View",
            "Inside View",
            "Inside Back View",
            "Main Label",
            "Care Label",
            "Care Label Barcode",
            "Trims",
            "Shading",
            "Sample vs RFC"
        )

        fun getInstance(context: Context): InspectCamDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    InspectCamDatabase::class.java,
                    "inspect_cam_database.db"
                ).fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
