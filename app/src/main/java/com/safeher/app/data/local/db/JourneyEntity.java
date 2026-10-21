package com.safeher.app.data.local.db;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room Entity representing a safety journey stored locally on device.
 * Enforces local-only storage, privacy, and terminal lifecycle guarantees.
 */
@Entity(
    tableName = "journeys",
    indices = {
        @Index(value = {"status"}),
        @Index(value = {"startTimestamp"})
    }
)
public class JourneyEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String title;
    public String startLocation;
    public String destination;
    public Double startLatitude;
    public Double startLongitude;
    public Double destinationLatitude;
    public Double destinationLongitude;
    public long startTimestamp;
    public long expectedArrivalTimestamp;
    public long completedTimestamp;
    public String status;
    public long lastCheckInTimestamp;
    public int checkInCount;
    public String checkInHistory;
    public long createdAt;
    public long updatedAt;

    public JourneyEntity(
        long id,
        String title,
        String startLocation,
        String destination,
        Double startLatitude,
        Double startLongitude,
        Double destinationLatitude,
        Double destinationLongitude,
        long startTimestamp,
        long expectedArrivalTimestamp,
        long completedTimestamp,
        String status,
        long lastCheckInTimestamp,
        int checkInCount,
        String checkInHistory,
        long createdAt,
        long updatedAt
    ) {
        this.id = id;
        this.title = title;
        this.startLocation = startLocation;
        this.destination = destination;
        this.startLatitude = startLatitude;
        this.startLongitude = startLongitude;
        this.destinationLatitude = destinationLatitude;
        this.destinationLongitude = destinationLongitude;
        this.startTimestamp = startTimestamp;
        this.expectedArrivalTimestamp = expectedArrivalTimestamp;
        this.completedTimestamp = completedTimestamp;
        this.status = status;
        this.lastCheckInTimestamp = lastCheckInTimestamp;
        this.checkInCount = checkInCount;
        this.checkInHistory = checkInHistory;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
