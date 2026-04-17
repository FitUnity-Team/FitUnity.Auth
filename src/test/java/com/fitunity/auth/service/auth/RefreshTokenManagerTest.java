package com.fitunity.auth.service.auth;

import com.fitunity.auth.domain.RefreshTokenRecord;
import com.fitunity.auth.repository.RefreshTokenRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenManagerTest {

    @Mock
    private RefreshTokenRecordRepository refreshTokenRecordRepository;

    private RefreshTokenManager manager;

    @BeforeEach
    void setUp() {
        manager = new RefreshTokenManager(refreshTokenRecordRepository, new RefreshTokenRecordFactory());
    }

    @Test
    void hashShouldBeDeterministicAndNotEqualToRaw() {
        String raw = "abc";

        String h1 = manager.hash(raw);
        String h2 = manager.hash(raw);

        assertEquals(h1, h2);
        assertNotEquals(raw, h1);
    }

    @Test
    void secureEqualsShouldOnlyMatchEqualValues() {
        assertTrue(manager.secureEquals("same", "same"));
        assertFalse(manager.secureEquals("left", "right"));
    }

    @Test
    void rotateShouldRevokeCurrentAndPersistReplacement() {
        RefreshTokenRecord current = new RefreshTokenRecord();
        current.setId(UUID.randomUUID());
        current.setUserId("user-1");
        current.setTokenFamilyId("family-1");
        current.setRefreshTokenHash("old-hash");
        current.setExpiresAt(LocalDateTime.now().plusDays(10));
        current.setRevoked(false);

        when(refreshTokenRecordRepository.save(any(RefreshTokenRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        manager.rotate(current, "new-hash");

        assertTrue(current.isRevoked());

        ArgumentCaptor<RefreshTokenRecord> saveCaptor = ArgumentCaptor.forClass(RefreshTokenRecord.class);
        verify(refreshTokenRecordRepository, times(2)).save(saveCaptor.capture());

        List<RefreshTokenRecord> saved = saveCaptor.getAllValues();
        RefreshTokenRecord replacement = saved.get(1);
        assertEquals("user-1", replacement.getUserId());
        assertEquals("family-1", replacement.getTokenFamilyId());
        assertEquals("new-hash", replacement.getRefreshTokenHash());
        assertEquals(current.getExpiresAt(), replacement.getExpiresAt());
        assertFalse(replacement.isRevoked());
    }

    @Test
    void revokeFamilyByRefreshHashShouldRevokeEntireFamily() {
        RefreshTokenRecord seed = new RefreshTokenRecord();
        seed.setTokenFamilyId("family-1");

        RefreshTokenRecord first = new RefreshTokenRecord();
        first.setRevoked(false);
        RefreshTokenRecord second = new RefreshTokenRecord();
        second.setRevoked(false);

        when(refreshTokenRecordRepository.findByRefreshTokenHash("incoming-hash")).thenReturn(Optional.of(seed));
        when(refreshTokenRecordRepository.findByTokenFamilyId("family-1")).thenReturn(List.of(first, second));

        manager.revokeFamilyByRefreshHash("incoming-hash");

        assertTrue(first.isRevoked());
        assertTrue(second.isRevoked());
        verify(refreshTokenRecordRepository).saveAll(List.of(first, second));
    }
}
