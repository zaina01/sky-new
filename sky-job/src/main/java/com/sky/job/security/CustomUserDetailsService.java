package com.sky.job.security;

import com.sky.job.user.User;
import com.sky.job.user.UserStore;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final UserStore userStore;

    public CustomUserDetailsService(UserStore userStore) {
        this.userStore = userStore;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userStore.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        Set<String> roles = userStore.roles(user.userId());
        Set<String> permissions = userStore.permissions(user.userId());
        return AuthenticatedUser.from(user, roles, permissions);
    }
}
