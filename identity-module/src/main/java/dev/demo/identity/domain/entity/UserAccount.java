package dev.demo.identity.domain.entity;

import java.util.UUID;

/** Identity aggregate; roles and permissions are loaded through the repository port. */
public record UserAccount(UUID id, String username, String passwordHash, boolean enabled) {}
