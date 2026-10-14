package dev.demo.modular.api;

import dev.demo.core.web.ApiResponse;
import dev.demo.identity.application.RefreshTokenProperties;
import dev.demo.identity.application.model.TokenModel;
import dev.demo.identity.application.model.UserModel;
import dev.demo.identity.application.usecase.IdentityUseCase;
import dev.demo.identity.interfaces.dto.request.CreateUserRequest;
import dev.demo.identity.interfaces.dto.request.LoginRequest;
import dev.demo.identity.interfaces.dto.request.UpdateUserRolesRequest;
import dev.demo.identity.mapper.IdentityMapper;
import dev.demo.modular.api.contract.IdentityApi;
import dev.demo.modular.security.AuthCookieWriter;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController implements IdentityApi {

  private final IdentityUseCase identityUseCase;
  private final IdentityMapper identityMapper;
  private final AuthCookieWriter cookies;
  private final RefreshTokenProperties refreshProperties;

  @Override
  public ApiResponse<TokenModel> login(LoginRequest request, HttpServletResponse response) {
    var session = identityUseCase.loginWithRefresh(identityMapper.toCommand(request));
    cookies.writeRefreshToken(
        response, session.refreshToken(), refreshProperties.ttl().toSeconds());
    return ApiResponse.ok(session.accessToken());
  }

  @Override
  public ApiResponse<TokenModel> refresh(String refreshToken, HttpServletResponse response) {
    var session = identityUseCase.refresh(refreshToken);
    cookies.writeRefreshToken(
        response, session.refreshToken(), refreshProperties.ttl().toSeconds());
    return ApiResponse.ok(session.accessToken());
  }

  @Override
  public ApiResponse<Void> logout(Jwt jwt, String refreshToken, HttpServletResponse response) {
    identityUseCase.logout(jwt.getId(), jwt.getExpiresAt(), refreshToken);
    cookies.clearRefreshToken(response);
    return ApiResponse.ok(null);
  }

  @Override
  public ApiResponse<Void> cookieLogout(
      Jwt jwt, String refreshToken, HttpServletResponse response) {
    identityUseCase.logout(
        Objects.isNull(jwt) ? null : jwt.getId(),
        Objects.isNull(jwt) ? null : jwt.getExpiresAt(),
        refreshToken);
    cookies.clearRefreshToken(response);
    return ApiResponse.ok(null);
  }

  @Override
  public ApiResponse<UserModel> me() {
    return ApiResponse.ok(identityUseCase.profileCurrent());
  }

  @Override
  public ApiResponse<UserModel> create(CreateUserRequest request) {
    return ApiResponse.ok(identityUseCase.create(identityMapper.toCommand(request)));
  }

  @Override
  public ApiResponse<UserModel> updateRoles(UUID userId, UpdateUserRolesRequest request) {
    return ApiResponse.ok(identityUseCase.updateRoles(identityMapper.toCommand(userId, request)));
  }
}
