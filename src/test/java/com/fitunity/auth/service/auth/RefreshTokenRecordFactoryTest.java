package com.fitunity.auth.service.auth;

import com.fitunity.auth.domain.RefreshTokenRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RefreshTokenRecordFactoryTest {

    private final RefreshTokenRecordFactory factory = new RefreshTokenRecordFactory();

    @Test
    void shouldCreateRecordWithDefaults() {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(30);

        RefreshTokenRecord record = factory.newRecord("user-id", "family-id", "hash", expiresAt);

        assertNotNull(record.getId());
        assertEquals("user-id", record.getUserId());
        assertEquals("family-id", record.getTokenFamilyId());
        assertEquals("hash", record.getRefreshTokenHash());
        assertEquals(expiresAt, record.getExpiresAt());
        assertNotNull(record.getCreatedAt());
        assertFalse(record.isRevoked());
    }
}
