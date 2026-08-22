package com.onemind.app.data.local

import androidx.room.TypeConverter
import com.onemind.app.domain.model.ContentType
import com.onemind.app.domain.model.DerivedSource
import com.onemind.app.domain.model.EntityType
import com.onemind.app.domain.model.ProcessingState
import com.onemind.app.domain.model.SourceType
import com.onemind.app.domain.processing.StageStatus

/**
 * Room TypeConverters for enum types used in entities.
 */
class Converters {

    @TypeConverter
    fun fromProcessingState(state: ProcessingState): String = state.name

    @TypeConverter
    fun toProcessingState(value: String): ProcessingState = ProcessingState.valueOf(value)

    @TypeConverter
    fun fromSourceType(type: SourceType): String = type.name

    @TypeConverter
    fun toSourceType(value: String): SourceType = SourceType.valueOf(value)

    @TypeConverter
    fun fromContentType(type: ContentType): String = type.name

    @TypeConverter
    fun toContentType(value: String): ContentType = ContentType.valueOf(value)

    @TypeConverter
    fun fromStageStatus(status: StageStatus): String = status.name

    @TypeConverter
    fun toStageStatus(value: String): StageStatus = StageStatus.valueOf(value)

    @TypeConverter
    fun fromEntityType(type: EntityType): String = type.name

    @TypeConverter
    fun toEntityType(value: String): EntityType = EntityType.valueOf(value)

    @TypeConverter
    fun fromDerivedSource(source: DerivedSource): String = source.name

    @TypeConverter
    fun toDerivedSource(value: String): DerivedSource = DerivedSource.valueOf(value)
}
