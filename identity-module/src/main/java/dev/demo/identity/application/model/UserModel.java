package dev.demo.identity.application.model;

import java.util.List;
import java.util.UUID;

/** Current user profile enriched with assigned role and effective permission codes. */
public record UserModel(
    UUID id, String username, boolean enabled, List<String> roles, List<String> permissions) {}
