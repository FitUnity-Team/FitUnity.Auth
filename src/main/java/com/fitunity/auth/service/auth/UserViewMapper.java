package com.fitunity.auth.service.auth;

import com.fitunity.auth.domain.Utilisateur;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class UserViewMapper {

    public Map<String, Object> toUserView(Utilisateur user, String statutAbonnement) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", user.getId().toString());
        value.put("email", user.getEmail());
        value.put("nom", user.getNom());
        value.put("role", user.getRole().getValue());
        value.put("active", user.isActive());
        value.put("dateCreation", user.getCreatedAt());
        value.put("statutAbonnement", statutAbonnement);
        return value;
    }
}
