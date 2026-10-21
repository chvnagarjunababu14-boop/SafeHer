package com.safeher.app.data.local.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room Entity representing a persistent local lifecycle or safety event
 * associated with a specific safety journey.
 *
 * Privacy Guarantees:
 * - Stored exclusively in local SQLite on device.
 * - Never includes destination names, coordinates, or sensitive personal logs.
 */
@Entity(
    tableName = "journey_events",
    foreignKeys = @ForeignKey(
        entity = JourneyEntity.class,
        parentColumns = "id",
        childColumns = "journeyId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {
        @Index(value = {"journeyId"}),
        @Index(value = {"timestamp"})
    }
)
public class JourneyEventEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long journeyId;
    @NonNull
    public String eventType;
    public long timestamp;
    public String metadata;

    public JourneyEventEntity(
        long id,
        long journeyId,
        @NonNull String eventType,
        long timestamp,
        String metadata
    ) {
        this.id = id;
        this.journeyId = journeyId;
        this.eventType = eventType;
        this.timestamp = timestamp;
        this.metadata = metadata;
    }
}
