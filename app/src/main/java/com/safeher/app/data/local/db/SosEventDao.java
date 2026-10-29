package com.safeher.app.data.local.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

/**
 * Data Access Object for local SOS dispatch event history.
 */
@Dao
public interface SosEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(SosEventEntity event);

    @Update
    int update(SosEventEntity event);

    @Query("SELECT * FROM sos_events WHERE userId = :userId ORDER BY timestamp DESC")
    List<SosEventEntity> getEventsForUser(String userId);

    @Query("SELECT * FROM sos_events WHERE userId = :userId ORDER BY timestamp DESC LIMIT :limit")
    List<SosEventEntity> getRecentEventsForUser(String userId, int limit);

    @Query("SELECT * FROM sos_events WHERE id = :id AND userId = :userId LIMIT 1")
    SosEventEntity getEventByIdForUser(long id, String userId);

    @Query("SELECT COUNT(*) FROM sos_events WHERE userId = :userId")
    int getEventCountForUser(String userId);

    @Query("SELECT * FROM sos_events WHERE journeyId = :journeyId ORDER BY timestamp DESC")
    List<SosEventEntity> getEventsForJourney(long journeyId);

    @Query("DELETE FROM sos_events WHERE userId = :userId")
    int deleteAllForUser(String userId);
}
