package dev.demo.identity.application.model;

import java.util.List;
import java.util.UUID;

public record UserModel(
    UUID id, String username, boolean enabled, List<String> roles, List<String> permissions) {}
