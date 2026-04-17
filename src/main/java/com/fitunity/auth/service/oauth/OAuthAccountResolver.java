package com.fitunity.auth.service.oauth;

import com.fitunity.auth.domain.Utilisateur;
import com.fitunity.auth.repository.UtilisateurRepository;
import com.fitunity.auth.service.auth.UserFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthAccountResolver {

    private final UtilisateurRepository utilisateurRepository;
    private final UserFactory userFactory;

    public OAuthAccountResolver(UtilisateurRepository utilisateurRepository, UserFactory userFactory) {
        this.utilisateurRepository = utilisateurRepository;
        this.userFactory = userFactory;
    }

    @Transactional
    public Utilisateur resolveOrCreate(String email, String displayName) {
        String normalizedEmail = userFactory.normalizeEmail(email);
        return utilisateurRepository.findByEmail(normalizedEmail)
                .orElseGet(() -> utilisateurRepository.save(userFactory.newClientUser(normalizedEmail, "", displayName)));
    }
}
