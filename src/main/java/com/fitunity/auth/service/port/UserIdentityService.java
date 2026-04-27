package com.fitunity.auth.service.port;

import org.springframework.security.core.userdetails.UserDetails;

public interface UserIdentityService {

    UserDetails loadUserByUserId(String userId);

    default IdentityUserDetails requireIdentity(String userId) {
        UserDetails details = loadUserByUserId(userId);
        if (details instanceof IdentityUserDetails identity) {
            return identity;
        }
        return null;
    }
}
