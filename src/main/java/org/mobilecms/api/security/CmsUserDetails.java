package org.mobilecms.api.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class CmsUserDetails implements UserDetails {

    private final String email;
    private final String role;
    private final String salt;

    public CmsUserDetails(String email, String role, String salt) {
        this.email = email;
        this.role = role;
        this.salt = salt;
    }

    public String getRole() {
        return role;
    }

    public String getSalt() {
        return salt;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return email;
    }
}
