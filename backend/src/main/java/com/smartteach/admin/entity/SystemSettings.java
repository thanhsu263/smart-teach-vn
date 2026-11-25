package com.smartteach.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The single row of {@code system_settings}; created by the V3 migration. */
@Entity
@Table(name = "system_settings")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemSettings {

    public static final short ID = 1;

    @Id
    private Short id;

    @Column(name = "keep_alive_enabled", nullable = false)
    private boolean keepAliveEnabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** For tests; the real row comes from the migration. */
    public SystemSettings(boolean keepAliveEnabled, Instant updatedAt) {
        this.id = ID;
        this.keepAliveEnabled = keepAliveEnabled;
        this.updatedAt = updatedAt;
    }
}
