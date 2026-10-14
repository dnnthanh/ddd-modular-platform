package dev.demo.identity.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.demo.core.exception.type.BadRequestException;
import dev.demo.core.exception.type.ForbiddenException;
import dev.demo.core.security.UserContext;
import dev.demo.core.security.jwt.JwtProperties;
import dev.demo.identity.application.command.ChangeUserRolesCommand;
import dev.demo.identity.application.command.CreateUserCommand;
import dev.demo.identity.application.port.RolePort;
import dev.demo.identity.application.port.UserAccountPort;
import dev.demo.identity.domain.entity.RoleDefinition;
import dev.demo.identity.domain.entity.UserAccount;
import dev.demo.identity.mapper.IdentityMapper;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class IdentityApplicationServiceTest {
  private final UserAccountPort users = mock(UserAccountPort.class);
  private final RolePort roles = mock(RolePort.class);
  private final IdentityApplicationService service =
      new IdentityApplicationService(
          users,
          roles,
          mock(IdentityMapper.class),
          new UserContext(UUID.randomUUID(), Set.of("ROLE_ADMIN")),
          new BCryptPasswordEncoder(4),
          mock(JwtEncoder.class),
          mock(StringRedisTemplate.class),
          new JwtProperties(null, null, "test", Duration.ofMinutes(15)));

  private void allowReader() {
    when(roles.existingCodes(List.of("READER"))).thenReturn(Set.of("READER"));
    when(roles.findByCode("READER"))
        .thenReturn(Optional.of(new RoleDefinition("READER", Set.of("CATALOG_READ"))));
  }

  @Test
  void overlongAsciiPasswordIsAValidationError() {
    allowReader();
    assertThrows(
        BadRequestException.class,
        () -> service.create(new CreateUserCommand("sample", "a".repeat(80), List.of("READER"))));
    verify(users, never()).insert(any());
  }

  @Test
  void passwordByteLimitIncludesUnicode() {
    allowReader();
    assertThrows(
        BadRequestException.class,
        () -> service.create(new CreateUserCommand("sample", "界".repeat(25), List.of("READER"))));
    verify(users, never()).insert(any());
  }

  @Test
  void privilegedCallerCanChangeProtectedAccount() {
    allowReader();
    UUID target = UUID.randomUUID();
    when(users.roles(target)).thenReturn(List.of("ADMIN"));
    when(users.findById(target))
        .thenReturn(Optional.of(new UserAccount(target, "target", "hash", true)));
    assertDoesNotThrow(
        () -> service.updateRoles(new ChangeUserRolesCommand(target, List.of("READER"))));
    verify(users).setRoles(target, List.of("READER"));
  }

  @Test
  void userManagerCannotChangeProtectedAccount() {
    allowReader();
    UUID target = UUID.randomUUID();
    when(users.roles(target)).thenReturn(List.of("ADMIN"));
    var manager =
        new IdentityApplicationService(
            users,
            roles,
            mock(IdentityMapper.class),
            new UserContext(UUID.randomUUID(), Set.of("USERS_MANAGE", "CATALOG_READ")),
            new BCryptPasswordEncoder(4),
            mock(JwtEncoder.class),
            mock(StringRedisTemplate.class),
            new JwtProperties(null, null, "test", Duration.ofMinutes(15)));
    assertThrows(
        ForbiddenException.class,
        () -> manager.updateRoles(new ChangeUserRolesCommand(target, List.of("READER"))));
    verify(users, never()).setRoles(any(), any());
  }

  @Test
  void privilegedCallerCanGrantExistingAdminRole() {
    when(roles.existingCodes(List.of("ADMIN"))).thenReturn(Set.of("ADMIN"));
    when(roles.findByCode("ADMIN"))
        .thenReturn(Optional.of(new RoleDefinition("ADMIN", Set.of("USERS_MANAGE"))));
    assertDoesNotThrow(
        () ->
            service.create(
                new CreateUserCommand("target", "valid-password-123", List.of("ADMIN"))));
    verify(users).insert(any());
  }
}
