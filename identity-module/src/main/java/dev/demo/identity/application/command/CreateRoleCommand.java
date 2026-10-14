package dev.demo.identity.application.command;

import java.util.Set;

/** Request to create a database-defined non-privileged role. */
public record CreateRoleCommand(String code, Set<String> permissions) {}
