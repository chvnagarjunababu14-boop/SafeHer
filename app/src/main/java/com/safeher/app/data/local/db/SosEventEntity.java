package com.safeher.app.data.local.db;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room Entity representing a recorded local Emergency SOS dispatch event.
 * Scoped to an authenticated user ID to maintain strict account isolation.
 */
@Entity(
    tableName = "sos_events",
    indices = {
        @Index(value = {"userId"}),
        @Index(value = {"timestamp"}),
        @Index(value = {"journeyId"})
    }
)
public class SosEventEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String userId;
    public long timestamp;
    public String message;
    public Double latitude;
    public Double longitude;
    public Float locationAccuracy;
    public int recipientsCount;
    public int successCount;
    public int failureCount;
    public String dispatchMode;
    public String detailsJson;
    public Long journeyId;

    public SosEventEntity(
        long id,
        String userId,
        long timestamp,
        String message,
        Double latitude,
        Double longitude,
        Float locationAccuracy,
        int recipientsCount,
        int successCount,
        int failureCount,
        String dispatchMode,
        String detailsJson,
        Long journeyId
    ) {
        this.id = id;
        this.userId = userId;
        this.timestamp = timestamp;
        this.message = message;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationAccuracy = locationAccuracy;
        this.recipientsCount = recipientsCount;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.dispatchMode = dispatchMode;
        this.detailsJson = detailsJson;
        this.journeyId = journeyId;
    }

    @androidx.room.Ignore
    public SosEventEntity(
        long id,
        String userId,
        long timestamp,
        String message,
        Double latitude,
        Double longitude,
        Float locationAccuracy,
        int recipientsCount,
        int successCount,
        int failureCount,
        String dispatchMode,
        String detailsJson
    ) {
        this(
            id,
            userId,
            timestamp,
            message,
            latitude,
            longitude,
            locationAccuracy,
            recipientsCount,
            successCount,
            failureCount,
            dispatchMode,
            detailsJson,
            null
        );
    }
}
