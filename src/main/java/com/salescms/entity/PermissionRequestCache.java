package com.salescms.entity;
import com.salescms.dto.RbacDtos;

import com.salescms.entity.User;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static com.salescms.dto.RbacDtos.CurrentUserAccessDto;

@Component
@RequestScope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class PermissionRequestCache {

    private final Map<UUID, User> usersById = new HashMap<>();
    private final Map<UUID, CurrentUserAccessDto> accessByUserId = new HashMap<>();

    public User getUser(UUID userId, Supplier<User> loader) {
        return usersById.computeIfAbsent(userId, ignored -> loader.get());
    }

    public CurrentUserAccessDto getAccess(UUID userId, Supplier<CurrentUserAccessDto> loader) {
        return accessByUserId.computeIfAbsent(userId, ignored -> loader.get());
    }
}
