package com.safeher.app.data.local.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

/**
 * Data Access Object for local guardian storage.
 */
@Dao
public interface GuardianDao {
    @Query("SELECT * FROM guardians ORDER BY createdAt DESC")
    List<GuardianEntity> getAllGuardians();

    @Query("SELECT COUNT(*) FROM guardians")
    int getGuardianCount();

    @Query("SELECT * FROM guardians WHERE id = :id LIMIT 1")
    GuardianEntity getGuardianById(long id);

    @Query("SELECT * FROM guardians WHERE phoneNumber = :phoneNumber LIMIT 1")
    GuardianEntity getGuardianByPhoneNumber(String phoneNumber);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(GuardianEntity guardian);

    @Update
    int update(GuardianEntity guardian);

    @Delete
    int delete(GuardianEntity guardian);

    @Query("DELETE FROM guardians WHERE id = :id")
    int deleteById(long id);
}
