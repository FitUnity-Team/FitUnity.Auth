package com.fitunity.auth.service.auth;

import com.fitunity.auth.domain.RefreshTokenRecord;
import com.fitunity.auth.repository.RefreshTokenRecordRepository;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Component
public class RefreshTokenManager {

    private final RefreshTokenRecordRepository refreshTokenRecordRepository;
    private final RefreshTokenRecordFactory refreshTokenRecordFactory;

    public RefreshTokenManager(
            RefreshTokenRecordRepository refreshTokenRecordRepository,
            RefreshTokenRecordFactory refreshTokenRecordFactory
    ) {
        this.refreshTokenRecordRepository = refreshTokenRecordRepository;
        this.refreshTokenRecordFactory = refreshTokenRecordFactory;
    }

    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to hash refresh token", e);
        }
    }

    public boolean secureEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }

    public Optional<RefreshTokenRecord> findByHash(String refreshTokenHash) {
        return refreshTokenRecordRepository.findByRefreshTokenHash(refreshTokenHash);
    }

    public void persist(String userId, String tokenFamilyId, String refreshHash, LocalDateTime expiresAt) {
        RefreshTokenRecord record = refreshTokenRecordFactory.newRecord(userId, tokenFamilyId, refreshHash, expiresAt);
        refreshTokenRecordRepository.save(record);
    }

    public void rotate(RefreshTokenRecord currentRecord, String newRefreshHash) {
        currentRecord.setRevoked(true);
        refreshTokenRecordRepository.save(currentRecord);
        persist(currentRecord.getUserId(), currentRecord.getTokenFamilyId(), newRefreshHash, currentRecord.getExpiresAt());
    }

    public void revokeFamilyByRefreshHash(String refreshHash) {
        findByHash(refreshHash).ifPresent(record -> revokeFamily(record.getTokenFamilyId()));
    }

    public void revokeFamily(String tokenFamilyId) {
        List<RefreshTokenRecord> familyTokens = refreshTokenRecordRepository.findByTokenFamilyId(tokenFamilyId);
        for (RefreshTokenRecord tokenRecord : familyTokens) {
            tokenRecord.setRevoked(true);
        }
        refreshTokenRecordRepository.saveAll(familyTokens);
    }
}
