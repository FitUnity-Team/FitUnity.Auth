package com.fitunity.auth.repository;

import com.fitunity.auth.domain.RefreshTokenRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRecordRepository extends JpaRepository<RefreshTokenRecord, UUID> {

    Optional<RefreshTokenRecord> findByRefreshTokenHash(String refreshTokenHash);

    @Query("SELECT rt FROM RefreshTokenRecord rt WHERE rt.userId = :userId AND rt.isRevoked = false AND rt.expiresAt > :now")
    List<RefreshTokenRecord> findValidRefreshTokens(String userId, LocalDateTime now);

    @Query("SELECT rt FROM RefreshTokenRecord rt WHERE rt.tokenFamilyId = :familyId AND rt.isRevoked = false")
    List<RefreshTokenRecord> findActiveTokensByFamily(String familyId);

    @Query("SELECT rt FROM RefreshTokenRecord rt WHERE rt.userId = :userId AND rt.isRevoked = false")
    List<RefreshTokenRecord> findActiveByUserId(String userId);

    @Query("SELECT rt FROM RefreshTokenRecord rt WHERE rt.tokenFamilyId = :tokenFamilyId")
    List<RefreshTokenRecord> findByTokenFamilyId(String tokenFamilyId);

    void deleteByUserIdAndTokenFamilyId(String userId, String familyId);
}
