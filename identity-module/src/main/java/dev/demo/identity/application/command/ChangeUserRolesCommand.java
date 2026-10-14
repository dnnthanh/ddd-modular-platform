package dev.demo.identity.application.command;

import java.util.List;
import java.util.UUID;

public record ChangeUserRolesCommand(UUID userId, List<String> roles) {}
