package com.sky.job.user.mapper;

import com.sky.job.user.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserMapper {
    int countUsers();

    int countByUsername(@Param("username") String username);

    int countByEmail(@Param("email") String email);

    int countByPhone(@Param("phone") String phone);

    int countByEmailForOtherUser(@Param("email") String email, @Param("userId") String userId);

    int countByPhoneForOtherUser(@Param("phone") String phone, @Param("userId") String userId);

    User findByUsername(@Param("username") String username);

    User findByUserId(@Param("userId") String userId);

    List<User> list(
            @Param("username") String username,
            @Param("usernameLike") String usernameLike,
            @Param("enabled") Boolean enabled,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long count(@Param("username") String username, @Param("usernameLike") String usernameLike, @Param("enabled") Boolean enabled);

    int insert(User user);

    int updatePassword(@Param("userId") String userId, @Param("passwordHash") String passwordHash);

    int updateProfile(
            @Param("userId") String userId,
            @Param("email") String email,
            @Param("phone") String phone,
            @Param("avatar") String avatar,
            @Param("enabled") boolean enabled
    );

    int updateEnabled(@Param("userId") String userId, @Param("enabled") boolean enabled);

    int deleteUserRoles(@Param("userId") String userId);

    int delete(@Param("userId") String userId);

    long countEnabledAdmins();

    int countRole(@Param("userId") String userId, @Param("roleName") String roleName);

    int assignRole(@Param("userId") String userId, @Param("roleName") String roleName);

    List<String> roles(@Param("userId") String userId);

    List<String> permissions(@Param("userId") String userId);
}
