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
 */
@Dao
public interface JourneyDao {
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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(JourneyEntity journey);

    @Update
    int update(JourneyEntity journey);

    @Delete
    int delete(JourneyEntity journey);

    @Query("DELETE FROM journeys WHERE id = :id")
    int deleteById(long id);
}
