package dev.demo.identity.application.service;

import dev.demo.core.cache.CacheDefinition;
import dev.demo.core.cache.CacheHelper;
import dev.demo.core.constants.AppConstants;
import dev.demo.core.exception.ErrorCode;
import dev.demo.core.exception.type.BadRequestException;
import dev.demo.core.exception.type.ConflictException;
import dev.demo.core.exception.type.ForbiddenException;
import dev.demo.core.exception.type.NotFoundException;
import dev.demo.core.exception.type.UnauthorizedException;
import dev.demo.core.security.UserContext;
import dev.demo.core.security.jwt.JwtProperties;
import dev.demo.core.stereotype.UseCase;
import dev.demo.identity.application.command.ChangeUserRolesCommand;
import dev.demo.identity.application.command.CreateUserCommand;
import dev.demo.identity.application.command.LoginCommand;
import dev.demo.identity.application.model.AuthenticatedSession;
import dev.demo.identity.application.model.TokenModel;
import dev.demo.identity.application.model.UserModel;
import dev.demo.identity.application.port.RolePort;
import dev.demo.identity.application.port.UserAccountPort;
import dev.demo.identity.application.usecase.IdentityUseCase;
import dev.demo.identity.domain.entity.UserAccount;
import dev.demo.identity.mapper.IdentityMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@UseCase
@RequiredArgsConstructor
public class IdentityApplicationService implements IdentityUseCase {

  private final UserAccountPort userAccountPort;
  private final RolePort rolePort;
  private final IdentityMapper identityMapper;
  private final UserContext userContext;
  private final PasswordEncoder passwordEncoder;
  private final JwtEncoder jwtEncoder;
  private final JwtProperties jwtProperties;
  private final CacheHelper cacheHelper;

  // This is the only Boolean CacheDefinition bean; generic constructor injection selects it.
  private final CacheDefinition<Boolean> revokedJwtCache;

  private final RefreshTokenApplicationService refreshTokens;

  @Transactional(readOnly = true)
  @Override
  public TokenModel login(LoginCommand command) {
    return issueAccessToken(authenticateUser(command));
  }

  /** Authenticates credentials and issues a rotation-capable, cookie-only refresh secret. */
  @Transactional
  public AuthenticatedSession loginWithRefresh(LoginCommand command) {
    UserAccount user = authenticateUser(command);
    return sessionFor(user);
  }

  public AuthenticatedSession refresh(String rawRefreshToken) {
    var rotation = refreshTokens.rotate(rawRefreshToken);
    if (!rotation.accepted()) {
      throw new UnauthorizedException(ErrorCode.UNAUTHORIZED);
    }
    UserAccount user =
        userAccountPort
            .findById(rotation.userId())
            .filter(UserAccount::enabled)
            .orElseGet(
                () -> {
                  refreshTokens.revoke(rotation.rawToken());
                  throw new UnauthorizedException(ErrorCode.UNAUTHORIZED);
                });
    return new AuthenticatedSession(issueAccessToken(user), rotation.rawToken());
  }

  private UserAccount authenticateUser(LoginCommand command) {
    String username = command.username();
    String password = command.password();
    if (StringUtils.isBlank(password) || password.getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS);
    }
    UserAccount user =
        userAccountPort
            .findByUsername(username)
            .orElseThrow(() -> new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS));
    if (!user.enabled() || !passwordEncoder.matches(password, user.passwordHash())) {
      throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS);
    }
    return user;
  }

  private AuthenticatedSession sessionFor(UserAccount user) {
    var refresh = refreshTokens.issue(user.id());
    return new AuthenticatedSession(issueAccessToken(user), refresh.rawToken());
  }

  private TokenModel issueAccessToken(UserAccount user) {
    Instant issued = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer(jwtProperties.issuer())
            .subject(user.id().toString())
            .issuedAt(issued)
            .notBefore(issued)
            .expiresAt(issued.plus(jwtProperties.ttl()))
            .id(UUID.randomUUID().toString())
            .build();
    var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
    String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new TokenModel(token, "Bearer", jwtProperties.ttl().toSeconds());
  }

  /** Denylist until token expiration. Other instances see it through shared Redis. */
  @Override
  public void logout(String jti, Instant expiresAt, String rawRefreshToken) {
    if (StringUtils.isNotBlank(jti)
        && Objects.nonNull(expiresAt)
        // JWT validators permit 60 seconds of clock skew, so exp alone cannot decide revocation.
        && expiresAt.plusSeconds(60).isAfter(Instant.now())) {
      // The fixed cache TTL is JWT TTL + validator clock skew, so this survives its remaining life.
      cacheHelper.put(revokedJwtCache, jti, Boolean.TRUE);
    }
    refreshTokens.revoke(rawRefreshToken);
  }

  @Override
  @Transactional(readOnly = true)
  public UserModel profileCurrent() {
    return profile(userContext.userId());
  }

  private UserModel profile(UUID id) {
    var user =
        userAccountPort
            .findById(id)
            .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
    return model(user);
  }

  @Transactional
  @Override
  public UserModel create(CreateUserCommand command) {
    String username = command.username();
    String password = command.password();
    List<String> roles = command.roles();
    if (StringUtils.isBlank(password)
        || password.length() < 12
        || password.getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new BadRequestException(ErrorCode.VALIDATION_FAILED);
    }
    validateRoleGrants(roles);
    var user = new UserAccount(UUID.randomUUID(), username, passwordEncoder.encode(password), true);
    try {
      userAccountPort.insert(user);
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException(ErrorCode.DUPLICATE_USERNAME);
    }
    userAccountPort.setRoles(user.id(), roles);
    return model(user);
  }

  @Transactional
  @Override
  public UserModel updateRoles(ChangeUserRolesCommand command) {
    UUID id = command.userId();
    if (!userContext.isPrivileged()
        && userAccountPort.roles(id).stream()
            .anyMatch(
                role ->
                    AppConstants.Roles.ADMIN.equals(role)
                        || AppConstants.Roles.SYSTEM_ADMIN.equals(role))) {
      throw new ForbiddenException(ErrorCode.FORBIDDEN);
    }
    List<String> roles = command.roles();
    validateRoleGrants(roles);
    userAccountPort.setRoles(id, roles);
    return profile(id);
  }

  /** Enforce database-defined roles; only privileged callers may grant privileged roles. */
  private void validateRoleGrants(List<String> roles) {
    if (CollectionUtils.isEmpty(roles)) {
      throw new BadRequestException(ErrorCode.INVALID_ROLE, "empty");
    }

    for (String role : roles) {
      if (StringUtils.isBlank(role)
          || (!userContext.isPrivileged()
              && (AppConstants.Roles.ADMIN.equals(role)
                  || AppConstants.Roles.SYSTEM_ADMIN.equals(role)))) {
        throw new ForbiddenException(ErrorCode.FORBIDDEN);
      }
    }

    var existing = rolePort.existingCodes(roles);
    if (!existing.containsAll(roles)) {
      throw new BadRequestException(ErrorCode.INVALID_ROLE, "unknown role");
    }
    // Do not allow USERS_MANAGE alone to grant a stronger custom role.
    for (String role : roles) {
      var definition =
          rolePort
              .findByCode(role)
              .orElseThrow(() -> new BadRequestException(ErrorCode.INVALID_ROLE, role));
      if (!definition.permissions().stream().allMatch(userContext::hasPermission)) {
        throw new ForbiddenException(ErrorCode.FORBIDDEN);
      }
    }
  }

  private UserModel model(UserAccount user) {
    return identityMapper.toModel(
        user, userAccountPort.roles(user.id()), userAccountPort.permissions(user.id()));
  }
}
