package dev.demo.identity.application.model;

import java.util.Set;

/** View of a configurable role and its hardcoded permission codes. */
public record RoleModel(String code, Set<String> permissions) {}
