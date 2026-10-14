package dev.demo.identity.application.model;

/** Access token returned to the API client with the opaque refresh secret written to its cookie. */
public record AuthenticatedSession(TokenModel accessToken, String refreshToken) {}
