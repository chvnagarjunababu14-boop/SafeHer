package com.safeher.app.data.local.db;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room Entity representing a trusted emergency contact stored locally on device.
 * Enforces phone number uniqueness to prevent duplicate alerts.
 */
@Entity(
    tableName = "guardians",
    indices = {@Index(value = {"phoneNumber"}, unique = true)}
)
public class GuardianEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String name;
    public String phoneNumber;
    public String relationship;
    public boolean isVerified;
    public long createdAt;
    public long updatedAt;
    public long verifiedAt;

    public GuardianEntity(long id, String name, String phoneNumber, String relationship, boolean isVerified, long createdAt, long updatedAt, long verifiedAt) {
        this.id = id;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.relationship = relationship;
        this.isVerified = isVerified;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.verifiedAt = verifiedAt;
    }
}
