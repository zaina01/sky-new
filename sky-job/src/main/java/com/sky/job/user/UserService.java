package com.sky.job.user;

import com.sky.job.audit.LoginLogStore;
import com.sky.job.audit.SystemLogStore;
import com.sky.job.security.AuthenticatedUser;
import com.sky.job.security.JwtTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
public class UserService {
    private final UserStore userStore;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final LoginLogStore loginLogStore;
    private final SystemLogStore systemLogStore;

    public UserService(
            UserStore userStore,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtTokenService jwtTokenService,
            LoginLogStore loginLogStore,
            SystemLogStore systemLogStore
    ) {
        this.userStore = userStore;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.loginLogStore = loginLogStore;
        this.systemLogStore = systemLogStore;
    }

    public UserResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        if (userStore.countUsers() > 0) {
            throw new IllegalStateException("目前账号只能注册一个");
        }
        if (userStore.existsByUsername(request.username())) {
            throw new IllegalStateException("用户名已存在");
        }
        if (userStore.existsByEmail(request.email())) {
            throw new IllegalStateException("邮箱已存在");
        }
        if (userStore.existsByPhone(request.phone())) {
            throw new IllegalStateException("手机号已存在");
        }
        User user = new User(
                UUID.randomUUID().toString(),
                request.username(),
                passwordEncoder.encode(request.password()),
                request.email(),
                blankToNull(request.phone()),
                null,
                1L,
                null,
                true,
                true,
                null,
                null
        );
        userStore.insert(user);
        userStore.assignRole(user.userId(), "ADMIN");
        User stored = userStore.findByUserId(user.userId()).orElseThrow();
        Set<String> roles = userStore.roles(stored.userId());
        Set<String> permissions = userStore.permissions(stored.userId());
        systemLogStore.record("USER", "REGISTER", "INFO", "SUCCESS", "USER", stored.userId(), "User registered", httpRequest);
        return UserResponse.from(stored, roles, permissions);
    }

    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));
            AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
            User user = userStore.findByUserId(principal.userId()).orElseThrow();
            loginLogStore.success(user, httpRequest);
            String token = jwtTokenService.generateToken(principal);
            return new LoginResponse(token, UserResponse.from(user, principal.roles(), principal.permissions()), jwtTokenService.expiresInSeconds(), principal.permissions());
        } catch (AuthenticationException exception) {
            loginLogStore.failure(request.username(), exception.getMessage(), httpRequest);
            throw new IllegalArgumentException("用户名或密码错误");
        }
    }

    public UserResponse currentUser(AuthenticatedUser principal) {
        User user = userStore.findByUserId(principal.userId()).orElseThrow(() -> new IllegalStateException("User not found"));
        return UserResponse.from(user, userStore.roles(user.userId()), userStore.permissions(user.userId()));
    }

    public void changePassword(AuthenticatedUser principal, ChangePasswordRequest request, HttpServletRequest httpRequest) {
        User user = userStore.findByUserId(principal.userId()).orElseThrow(() -> new IllegalStateException("User not found"));
        if (!passwordEncoder.matches(request.oldPassword(), user.passwordHash())) {
            throw new IllegalArgumentException("原密码错误");
        }
        userStore.updatePassword(user.userId(), passwordEncoder.encode(request.newPassword()));
        systemLogStore.record("USER", "CHANGE_PASSWORD", "INFO", "SUCCESS", "USER", user.userId(), "Password changed", httpRequest);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
