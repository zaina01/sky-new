package com.sky.job.user;

import com.sky.job.user.mapper.UserMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class UserStore {
    private final UserMapper mapper;

    public UserStore(UserMapper mapper) {
        this.mapper = mapper;
    }

    public int countUsers() {
        return mapper.countUsers();
    }

    public boolean existsByUsername(String username) {
        return mapper.countByUsername(username) > 0;
    }

    public boolean existsByEmail(String email) {
        return mapper.countByEmail(email) > 0;
    }

    public boolean existsByPhone(String phone) {
        return phone != null && !phone.isBlank() && mapper.countByPhone(phone) > 0;
    }

    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(mapper.findByUsername(username));
    }

    public Optional<User> findByUserId(String userId) {
        return Optional.ofNullable(mapper.findByUserId(userId));
    }

    public List<User> list(String username, Boolean enabled, List<Long> departmentFilterIds, com.sky.job.security.EffectiveDataScope scope, Integer page, Integer size) {
        int normalizedPage = normalizedPage(page);
        int normalizedSize = normalizedSize(size);
        return mapper.list(trim(username), like(username), enabled, departmentFilterIds, scope, normalizedSize, (normalizedPage - 1) * normalizedSize);
    }

    public long count(String username, Boolean enabled, List<Long> departmentFilterIds, com.sky.job.security.EffectiveDataScope scope) {
        return mapper.count(trim(username), like(username), enabled, departmentFilterIds, scope);
    }

    public void insert(User user) {
        mapper.insert(user);
    }

    public void updatePassword(String userId, String passwordHash) {
        mapper.updatePassword(userId, passwordHash);
    }

    public int updateProfile(String userId, String email, String phone, String avatar, Long departmentId, boolean enabled) {
        return mapper.updateProfile(userId, email, phone, avatar, departmentId, enabled);
    }

    public int updateEnabled(String userId, boolean enabled) {
        return mapper.updateEnabled(userId, enabled);
    }

    public int delete(String userId) {
        mapper.deleteUserRoles(userId);
        return mapper.delete(userId);
    }

    public void replaceRoles(String userId, Set<String> roleNames) {
        mapper.deleteUserRoles(userId);
        for (String roleName : roleNames) {
            assignRole(userId, roleName);
        }
    }

    public long countEnabledAdmins() {
        return mapper.countEnabledAdmins();
    }

    public boolean hasRole(String userId, String roleName) {
        return mapper.countRole(userId, roleName) > 0;
    }

    public boolean existsByEmailForOtherUser(String email, String userId) {
        return mapper.countByEmailForOtherUser(email, userId) > 0;
    }

    public boolean existsByPhoneForOtherUser(String phone, String userId) {
        return phone != null && !phone.isBlank() && mapper.countByPhoneForOtherUser(phone, userId) > 0;
    }

    public void assignRole(String userId, String roleName) {
        mapper.assignRole(userId, roleName);
    }

    public Set<String> roles(String userId) {
        return mapper.roles(userId).stream().collect(Collectors.toUnmodifiableSet());
    }

    public Set<String> permissions(String userId) {
        return mapper.permissions(userId).stream().collect(Collectors.toUnmodifiableSet());
    }

    public boolean hasOwnedResources(String userId) {
        return mapper.countOwnedResources(userId) > 0;
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String like(String value) {
        return value == null || value.isBlank() ? null : "%" + value.trim() + "%";
    }

    private int normalizedPage(Integer page) {
        return Math.max(page == null ? 1 : page, 1);
    }

    private int normalizedSize(Integer size) {
        return Math.min(Math.max(size == null ? 20 : size, 1), 100);
    }
}
