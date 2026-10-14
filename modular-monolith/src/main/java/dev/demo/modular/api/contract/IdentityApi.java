package dev.demo.modular.api.contract;

import dev.demo.core.web.ApiResponse;
import dev.demo.identity.application.model.TokenModel;
import dev.demo.identity.application.model.UserModel;
import dev.demo.identity.interfaces.dto.request.CreateUserRequest;
import dev.demo.identity.interfaces.dto.request.LoginRequest;
import dev.demo.identity.interfaces.dto.request.UpdateUserRolesRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** HTTP endpoint declarations and permission expressions stay next to each other. */
public interface IdentityApi {

  /** Authenticates a caller without requiring a bearer token. */
  @PostMapping("/api/public/auth/login")
  @PreAuthorize("permitAll()")
  ApiResponse<TokenModel> login(
      @Valid @RequestBody LoginRequest request, HttpServletResponse response);

  /** Rotates the HttpOnly refresh cookie and returns only a new access token in JSON. */
  @PostMapping("/api/public/auth/refresh")
  @PreAuthorize("permitAll()")
  ApiResponse<TokenModel> refresh(
      @CookieValue(name = "refresh_token", required = false) String refreshToken,
      HttpServletResponse response);

  /** Invalidates the currently presented bearer token. */
  @PostMapping("/api/private/auth/logout")
  @PreAuthorize("isAuthenticated()")
  ApiResponse<Void> logout(
      @AuthenticationPrincipal Jwt jwt,
      @CookieValue(name = "refresh_token", required = false) String refreshToken,
      HttpServletResponse response);

  /** Invalidates the refresh family held by the browser cookie. */
  @PostMapping("/api/public/auth/logout")
  @PreAuthorize("permitAll()")
  ApiResponse<Void> cookieLogout(
      @AuthenticationPrincipal Jwt jwt,
      @CookieValue(name = "refresh_token", required = false) String refreshToken,
      HttpServletResponse response);

  /** Returns information about the current principal. */
  @GetMapping("/api/private/users/me")
  @PreAuthorize("isAuthenticated()")
  ApiResponse<UserModel> me();

  /** Creates a user subject to USERS_MANAGE permission. */
  @PostMapping("/api/private/users")
  @PreAuthorize("@customPreAuthorizer.hasAnyPermission({'USERS_MANAGE'})")
  ApiResponse<UserModel> create(@Valid @RequestBody CreateUserRequest request);

  /** Updates a user’s dynamic role assignments. */
  @PutMapping("/api/private/users/{userId}/roles")
  @PreAuthorize("@customPreAuthorizer.hasAnyPermission({'USERS_MANAGE'})")
  ApiResponse<UserModel> updateRoles(
      @PathVariable UUID userId, @Valid @RequestBody UpdateUserRolesRequest request);
}
