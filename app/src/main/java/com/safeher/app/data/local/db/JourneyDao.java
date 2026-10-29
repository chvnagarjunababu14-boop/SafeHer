package com.safeher.app.data.local.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

/**
 * Data Access Object for local journey storage.
 * Supports account-scoped queries to guarantee strict isolation between authenticated accounts.
 */
@Dao
public interface JourneyDao {
    // Account-scoped queries
    @Query("SELECT * FROM journeys WHERE userId = :userId ORDER BY startTimestamp DESC")
    List<JourneyEntity> getJourneysForUser(String userId);

    @Query("SELECT * FROM journeys WHERE userId = :userId AND status IN ('ACTIVE', 'OVERDUE') ORDER BY startTimestamp DESC")
    List<JourneyEntity> getActiveOrOverdueJourneysForUser(String userId);

    @Query("SELECT * FROM journeys WHERE userId = :userId AND status IN ('ACTIVE', 'OVERDUE') ORDER BY startTimestamp DESC LIMIT 1")
    JourneyEntity getLatestActiveOrOverdueJourneyForUser(String userId);

    @Query("SELECT * FROM journeys WHERE id = :id AND userId = :userId LIMIT 1")
    JourneyEntity getJourneyByIdForUser(long id, String userId);

    @Query("SELECT * FROM journeys WHERE userId = :userId AND status IN ('COMPLETED', 'CANCELLED') ORDER BY startTimestamp DESC")
    List<JourneyEntity> getCompletedOrCancelledJourneysForUser(String userId);

    @Query("DELETE FROM journeys WHERE id = :id AND userId = :userId")
    int deleteByIdForUser(long id, String userId);

    // Unassigned legacy data management & recovery
    @Query("SELECT * FROM journeys WHERE userId IS NULL ORDER BY startTimestamp DESC")
    List<JourneyEntity> getUnassignedJourneys();

    @Query("SELECT COUNT(*) FROM journeys WHERE userId IS NULL")
    int getUnassignedJourneyCount();

    @Query("UPDATE journeys SET userId = :userId WHERE userId IS NULL")
    int assignUnassignedJourneysToUser(String userId);

    // Unconstrained / backward-compatible queries
    @Query("SELECT * FROM journeys ORDER BY startTimestamp DESC")
    List<JourneyEntity> getAllJourneys();

    @Query("SELECT * FROM journeys WHERE status IN ('ACTIVE', 'OVERDUE') ORDER BY startTimestamp DESC")
    List<JourneyEntity> getActiveOrOverdueJourneys();

    @Query("SELECT * FROM journeys WHERE status IN ('ACTIVE', 'OVERDUE') ORDER BY startTimestamp DESC LIMIT 1")
    JourneyEntity getLatestActiveOrOverdueJourney();

    @Query("SELECT * FROM journeys WHERE id = :id LIMIT 1")
    JourneyEntity getJourneyById(long id);

    @Query("SELECT * FROM journeys WHERE status IN ('COMPLETED', 'CANCELLED') ORDER BY startTimestamp DESC")
    List<JourneyEntity> getCompletedOrCancelledJourneys();

    // Monitoring and Overdue Escalation queries
    @Query("SELECT * FROM journeys WHERE isMonitoringActive = 1 AND status IN ('ACTIVE', 'OVERDUE') ORDER BY startTimestamp DESC")
    List<JourneyEntity> getActiveMonitoredJourneys();

    @Query("SELECT * FROM journeys WHERE isMonitoringActive = 1 AND userId = :userId AND status IN ('ACTIVE', 'OVERDUE') ORDER BY startTimestamp DESC")
    List<JourneyEntity> getActiveMonitoredJourneysForUser(String userId);

    @Query("UPDATE journeys SET lastLatitude = :lat, lastLongitude = :lng, lastLocationTimestamp = :timestamp, updatedAt = :timestamp WHERE id = :journeyId")
    int updateLastLocation(long journeyId, Double lat, Double lng, long timestamp);

    @Query("UPDATE journeys SET isMonitoringActive = :isActive, updatedAt = :updatedAt WHERE id = :journeyId")
    int setMonitoringActive(long journeyId, boolean isActive, long updatedAt);

    @Query("UPDATE journeys SET safetyCheckAcknowledged = 1, updatedAt = :updatedAt WHERE id = :journeyId")
    int markSafetyCheckAcknowledged(long journeyId, long updatedAt);

    @Query("UPDATE journeys SET overdueEscalationAttempted = 1, updatedAt = :updatedAt WHERE id = :journeyId")
    int markOverdueEscalationAttempted(long journeyId, long updatedAt);

    @Query("UPDATE journeys SET overdueSafetyCheckTimestamp = :checkTime, overdueEscalationDeadline = :deadline, status = 'OVERDUE', updatedAt = :updatedAt WHERE id = :journeyId")
    int recordSafetyCheckNotification(long journeyId, long checkTime, long deadline, long updatedAt);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(JourneyEntity journey);

    @Update
    int update(JourneyEntity journey);

    @Delete
    int delete(JourneyEntity journey);

    @Query("DELETE FROM journeys WHERE id = :id")
    int deleteById(long id);
}
