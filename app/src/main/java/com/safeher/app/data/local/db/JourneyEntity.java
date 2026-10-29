package com.safeher.app.data.local.db;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room Entity representing a safety journey stored locally on device.
 * Scoped to an authenticated account (userId) to enforce strict account isolation.
 * Enforces local-only storage, privacy, and terminal lifecycle guarantees.
 */
@Entity(
    tableName = "journeys",
    indices = {
        @Index(value = {"status"}),
        @Index(value = {"startTimestamp"}),
        @Index(value = {"userId"}),
        @Index(value = {"userId", "status"})
    }
)
public class JourneyEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String userId;
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

    public boolean isMonitoringActive;
    public Double lastLatitude;
    public Double lastLongitude;
    public long lastLocationTimestamp;
    public long overdueSafetyCheckTimestamp;
    public boolean safetyCheckAcknowledged;
    public long overdueEscalationDeadline;
    public boolean overdueEscalationAttempted;

    public JourneyEntity(
        long id,
        String userId,
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
        long updatedAt,
        boolean isMonitoringActive,
        Double lastLatitude,
        Double lastLongitude,
        long lastLocationTimestamp,
        long overdueSafetyCheckTimestamp,
        boolean safetyCheckAcknowledged,
        long overdueEscalationDeadline,
        boolean overdueEscalationAttempted
    ) {
        this.id = id;
        this.userId = userId;
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
        this.isMonitoringActive = isMonitoringActive;
        this.lastLatitude = lastLatitude;
        this.lastLongitude = lastLongitude;
        this.lastLocationTimestamp = lastLocationTimestamp;
        this.overdueSafetyCheckTimestamp = overdueSafetyCheckTimestamp;
        this.safetyCheckAcknowledged = safetyCheckAcknowledged;
        this.overdueEscalationDeadline = overdueEscalationDeadline;
        this.overdueEscalationAttempted = overdueEscalationAttempted;
    }

    @Ignore
    public JourneyEntity(
        long id,
        String userId,
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
        this(
            id,
            userId,
            title,
            startLocation,
            destination,
            startLatitude,
            startLongitude,
            destinationLatitude,
            destinationLongitude,
            startTimestamp,
            expectedArrivalTimestamp,
            completedTimestamp,
            status,
            lastCheckInTimestamp,
            checkInCount,
            checkInHistory,
            createdAt,
            updatedAt,
            false,
            null,
            null,
            0L,
            0L,
            false,
            0L,
            false
        );
    }

    @Ignore
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
        this(
            id,
            null,
            title,
            startLocation,
            destination,
            startLatitude,
            startLongitude,
            destinationLatitude,
            destinationLongitude,
            startTimestamp,
            expectedArrivalTimestamp,
            completedTimestamp,
            status,
            lastCheckInTimestamp,
            checkInCount,
            checkInHistory,
            createdAt,
            updatedAt
        );
    }
}
