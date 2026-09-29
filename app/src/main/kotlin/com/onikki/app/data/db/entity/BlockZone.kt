package com.onikki.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A place (school, work, masjid...) — apps can be blocked while the phone is inside its radius. */
@Entity(tableName = "block_zones")
data class BlockZone(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = 300
)
