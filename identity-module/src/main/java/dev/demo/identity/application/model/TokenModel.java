package dev.demo.identity.application.model;

/** Bearer access-token representation returned by authentication endpoints. */
public record TokenModel(String accessToken, String tokenType, long expiresIn) {}
