package dev.demo.identity.application.command;

import java.util.Set;

/** Replaces the hardcoded permission codes attached to a dynamic role. */
public record UpdateRolePermissionsCommand(String code, Set<String> permissions) {}
