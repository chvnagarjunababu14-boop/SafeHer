package com.safeher.app.data.local.db;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room Entity representing a trusted emergency contact stored locally on device.
 * Scoped to an authenticated account (userId) to enforce strict account isolation.
 * Unique constraint on (userId, phoneNumber) prevents duplicate numbers for the same account.
 */
@Entity(
    tableName = "guardians",
    indices = {
        @Index(value = {"userId", "phoneNumber"}, unique = true),
        @Index(value = {"userId"})
    }
)
public class GuardianEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String userId;
    public String name;
    public String phoneNumber;
    public String relationship;
    public boolean isVerified;
    public long createdAt;
    public long updatedAt;
    public long verifiedAt;
    public boolean isSosEnabled;
    public boolean isBatteryAlertEnabled;

    public GuardianEntity(long id, String userId, String name, String phoneNumber, String relationship, boolean isVerified, long createdAt, long updatedAt, long verifiedAt, boolean isSosEnabled, boolean isBatteryAlertEnabled) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.relationship = relationship;
        this.isVerified = isVerified;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.verifiedAt = verifiedAt;
        this.isSosEnabled = isSosEnabled;
        this.isBatteryAlertEnabled = isBatteryAlertEnabled;
    }

    @Ignore
    public GuardianEntity(long id, String userId, String name, String phoneNumber, String relationship, boolean isVerified, long createdAt, long updatedAt, long verifiedAt, boolean isSosEnabled) {
        this(id, userId, name, phoneNumber, relationship, isVerified, createdAt, updatedAt, verifiedAt, isSosEnabled, false);
    }

    @Ignore
    public GuardianEntity(long id, String userId, String name, String phoneNumber, String relationship, boolean isVerified, long createdAt, long updatedAt, long verifiedAt) {
        this(id, userId, name, phoneNumber, relationship, isVerified, createdAt, updatedAt, verifiedAt, true, false);
    }

    @Ignore
    public GuardianEntity(long id, String name, String phoneNumber, String relationship, boolean isVerified, long createdAt, long updatedAt, long verifiedAt) {
        this(id, null, name, phoneNumber, relationship, isVerified, createdAt, updatedAt, verifiedAt, true, false);
    }
}
