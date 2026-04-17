package com.fitunity.auth.service.auth;

import com.fitunity.auth.domain.RefreshTokenRecord;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class RefreshTokenRecordFactory {

    public RefreshTokenRecord newRecord(String userId, String familyId, String hash, LocalDateTime expiresAt) {
        RefreshTokenRecord record = new RefreshTokenRecord();
        record.setId(UUID.randomUUID());
        record.setUserId(userId);
        record.setTokenFamilyId(familyId);
        record.setRefreshTokenHash(hash);
        record.setExpiresAt(expiresAt);
        record.setCreatedAt(LocalDateTime.now());
        record.setRevoked(false);
        return record;
    }
}
