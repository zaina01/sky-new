package com.sky.job.security;

import com.sky.job.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record AuthenticatedUser(
        String userId,
        String username,
        String password,
        Long departmentId,
        boolean enabled,
        Set<String> roles,
        Set<String> permissions
) implements UserDetails {
    public static AuthenticatedUser from(User user, Set<String> roles, Set<String> permissions) {
        return new AuthenticatedUser(user.userId(), user.username(), user.passwordHash(), user.departmentId(), user.enabled() && user.departmentEnabled(), roles, permissions);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Stream.concat(
                roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)),
                permissions.stream().map(SimpleGrantedAuthority::new)
        ).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
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
        return enabled;
    }
}
