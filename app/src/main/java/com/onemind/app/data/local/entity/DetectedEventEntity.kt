package com.onemind.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.onemind.app.domain.model.EventStatus

@Entity(
    tableName = "detected_events",
    foreignKeys = [
        ForeignKey(
            entity = MemoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["memoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["memoryId"]),
        Index(value = ["status", "eventTime"])
    ]
)
data class DetectedEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memoryId: Long,
    /** Epoch millis of the event time. */
    val eventTime: Long,
    val eventTitle: String,
    val status: EventStatus = EventStatus.UPCOMING,
    /** Epoch millis when reminders were last scheduled, or null. */
    val remindersScheduledAt: Long? = null
)
