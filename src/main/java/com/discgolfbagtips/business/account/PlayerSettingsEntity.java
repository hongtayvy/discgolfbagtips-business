package com.discgolfbagtips.business.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The row behind {@link PlayerSettings}: one per owner, the document stored as {@code jsonb}. */
@Entity
@Table(name = "player_settings")
public class PlayerSettingsEntity {

    @Id
    @Column(name = "owner_key", nullable = false, length = 128)
    private String ownerKey;

    /** Explicit JDBC type for the same reason as {@code BagProfile.payload}: bind as jsonb, not varchar. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", nullable = false, columnDefinition = "jsonb")
    private String settings;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PlayerSettingsEntity() {
    }

    public PlayerSettingsEntity(String ownerKey, String settings) {
        this.ownerKey = ownerKey;
        this.settings = settings;
    }

    public String settings() {
        return settings;
    }

    public void replace(String settings) {
        this.settings = settings;
        this.updatedAt = Instant.now();
    }
}
