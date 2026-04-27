package com.fitunity.auth.service.port;

import com.fitunity.auth.domain.Role;
import org.springframework.security.core.userdetails.UserDetails;

public interface IdentityUserDetails extends UserDetails {

    String getUserId();

    Role getRole();
}
