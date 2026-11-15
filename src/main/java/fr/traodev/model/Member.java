package fr.traodev.model;

import fr.traodev.Permission;

import java.util.List;

public record Member(User user, String nick, String avatar, List<Long> roles, String joinedAt, String premiumSince,
                     boolean deaf, boolean mute, boolean pending, String permissions,
                     String communicationDisabledUntil) {
    public Member {
        roles = roles == null ? List.of() : roles;
    }

    public boolean hasRole(long roleId) {
        return roles.contains(roleId);
    }

    public String displayName() {
        return nick != null ? nick : user != null ? user.displayName() : null;
    }

    public boolean hasPermission(Permission p) {
        return Permission.has(permissions, p);
    }
}
