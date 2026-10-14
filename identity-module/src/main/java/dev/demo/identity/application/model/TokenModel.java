package dev.demo.identity.application.model;

public record TokenModel(String accessToken, String tokenType, long expiresIn) {}
