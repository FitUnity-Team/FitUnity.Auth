package com.fitunity.auth.service.oauth;

import com.fitunity.auth.domain.Role;
import com.fitunity.auth.domain.Utilisateur;
import com.fitunity.auth.repository.UtilisateurRepository;
import com.fitunity.auth.service.auth.UserFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthAccountResolverTest {

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private UserFactory userFactory;

    @InjectMocks
    private OAuthAccountResolver resolver;

    @Test
    void shouldAutoLinkExistingEmail() {
        Utilisateur existing = new Utilisateur();
        existing.setId(UUID.randomUUID());
        existing.setEmail("u@test.com");
        existing.setRole(Role.CLIENT);
        existing.setActive(true);

        when(userFactory.normalizeEmail("u@test.com")).thenReturn("u@test.com");
        when(utilisateurRepository.findByEmail("u@test.com")).thenReturn(Optional.of(existing));

        Utilisateur resolved = resolver.resolveOrCreate("u@test.com", "User Name");

        assertEquals(existing.getId(), resolved.getId());
        verify(utilisateurRepository, never()).save(org.mockito.ArgumentMatchers.any(Utilisateur.class));
    }

    @Test
    void shouldCreateActiveClientUserWhenEmailNotFound() {
        Utilisateur created = new Utilisateur();
        created.setId(UUID.randomUUID());
        created.setEmail("new@test.com");
        created.setRole(Role.CLIENT);
        created.setActive(true);

        when(userFactory.normalizeEmail("new@test.com")).thenReturn("new@test.com");
        when(utilisateurRepository.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(userFactory.newClientUser("new@test.com", "", "New User")).thenReturn(created);
        when(utilisateurRepository.save(org.mockito.ArgumentMatchers.any(Utilisateur.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Utilisateur resolved = resolver.resolveOrCreate("new@test.com", "New User");

        assertEquals("new@test.com", resolved.getEmail());
        assertEquals(Role.CLIENT, resolved.getRole());
        assertTrue(resolved.isActive());

        ArgumentCaptor<Utilisateur> captor = ArgumentCaptor.forClass(Utilisateur.class);
        verify(utilisateurRepository).save(captor.capture());
        assertEquals("new@test.com", captor.getValue().getEmail());
    }
}
