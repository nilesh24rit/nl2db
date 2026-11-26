package com.nl2db.security;

import java.util.UUID;
import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Principal representation for API key authenticated users.
 * Contains the API key ID, organization ID, authentication type, and authorities.
 * Used by the Spring Security filter chain for API key authentication.
 */
public record ApiKeyPrincipal(
        UUID apiKeyId,
        UUID organizationId,
        String authType,
        Collection<? extends GrantedAuthority> authorities
) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return apiKeyId.toString();
    }

    public UUID getApiKeyId() {
        return apiKeyId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getAuthType() {
        return authType;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}