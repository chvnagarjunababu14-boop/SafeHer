package com.safeher.app.data.local.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

/**
 * Data Access Object for local journey event timeline history.
 */
@Dao
public interface JourneyEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(JourneyEventEntity event);

    @Query("SELECT * FROM journey_events WHERE journeyId = :journeyId ORDER BY timestamp ASC, id ASC")
    List<JourneyEventEntity> getEventsForJourney(long journeyId);

    @Query("SELECT * FROM journey_events WHERE journeyId = :journeyId ORDER BY timestamp DESC, id DESC LIMIT 1")
    JourneyEventEntity getLatestEventForJourney(long journeyId);

    @Query("SELECT COUNT(*) FROM journey_events WHERE journeyId = :journeyId AND eventType = 'OVERDUE_OBSERVED'")
    int countOverdueEvents(long journeyId);

    @Query("DELETE FROM journey_events WHERE journeyId = :journeyId")
    int deleteEventsForJourney(long journeyId);
}
