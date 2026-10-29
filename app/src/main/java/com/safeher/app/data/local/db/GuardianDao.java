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
 * Supports account-scoped queries to guarantee strict isolation between authenticated accounts.
 */
@Dao
public interface GuardianDao {
    // Account-scoped queries
    @Query("SELECT * FROM guardians WHERE userId = :userId ORDER BY createdAt DESC")
    List<GuardianEntity> getGuardiansForUser(String userId);

    @Query("SELECT COUNT(*) FROM guardians WHERE userId = :userId")
    int getGuardianCountForUser(String userId);

    @Query("SELECT * FROM guardians WHERE id = :id AND userId = :userId LIMIT 1")
    GuardianEntity getGuardianByIdForUser(long id, String userId);

    @Query("SELECT * FROM guardians WHERE phoneNumber = :phoneNumber AND userId = :userId LIMIT 1")
    GuardianEntity getGuardianByPhoneNumberForUser(String phoneNumber, String userId);

    @Query("DELETE FROM guardians WHERE id = :id AND userId = :userId")
    int deleteByIdForUser(long id, String userId);

    @Query("SELECT * FROM guardians WHERE userId = :userId AND isSosEnabled = 1 ORDER BY createdAt DESC")
    List<GuardianEntity> getSosEnabledGuardiansForUser(String userId);

    @Query("UPDATE guardians SET isSosEnabled = :isSosEnabled, updatedAt = :updatedAt WHERE id = :id AND userId = :userId")
    int updateSosEnabledForUser(long id, String userId, boolean isSosEnabled, long updatedAt);

    @Query("SELECT * FROM guardians WHERE isSosEnabled = 1 ORDER BY createdAt DESC")
    List<GuardianEntity> getAllSosEnabledGuardians();

    @Query("SELECT * FROM guardians WHERE userId = :userId AND isBatteryAlertEnabled = 1 ORDER BY createdAt DESC")
    List<GuardianEntity> getBatteryAlertEnabledGuardiansForUser(String userId);

    @Query("UPDATE guardians SET isBatteryAlertEnabled = :isBatteryAlertEnabled, updatedAt = :updatedAt WHERE id = :id AND userId = :userId")
    int updateBatteryAlertEnabledForUser(long id, String userId, boolean isBatteryAlertEnabled, long updatedAt);

    @Query("SELECT * FROM guardians WHERE isBatteryAlertEnabled = 1 ORDER BY createdAt DESC")
    List<GuardianEntity> getAllBatteryAlertEnabledGuardians();

    // Unassigned legacy data management & recovery
    @Query("SELECT * FROM guardians WHERE userId IS NULL ORDER BY createdAt DESC")
    List<GuardianEntity> getUnassignedGuardians();

    @Query("SELECT COUNT(*) FROM guardians WHERE userId IS NULL")
    int getUnassignedGuardianCount();

    @Query("UPDATE guardians SET userId = :userId WHERE userId IS NULL")
    int assignUnassignedGuardiansToUser(String userId);

    // Unconstrained / backward-compatible queries
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
