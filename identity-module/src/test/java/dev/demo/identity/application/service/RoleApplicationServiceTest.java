package dev.demo.identity.application.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.demo.core.exception.type.ForbiddenException;
import dev.demo.core.security.UserContext;
import dev.demo.identity.application.command.CreateRoleCommand;
import dev.demo.identity.application.port.RolePort;
import dev.demo.identity.mapper.IdentityMapper;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RoleApplicationServiceTest {

  @Test
  void protectedRolesCannotBeCreatedEvenByAdmin() {
    var context = new UserContext(UUID.randomUUID(), Set.of("ROLE_ADMIN"));
    var service =
        new RoleApplicationService(
            Mockito.mock(RolePort.class), Mockito.mock(IdentityMapper.class), context);
    assertThrows(
        ForbiddenException.class,
        () -> service.create(new CreateRoleCommand("SYSTEM_ADMIN", Set.of("USERS_READ"))));
  }

  @Test
  void nonAdminCannotAssignPermissionsTheyDoNotHold() {
    var context = new UserContext(UUID.randomUUID(), Set.of("USERS_MANAGE"));
    var service =
        new RoleApplicationService(
            Mockito.mock(RolePort.class), Mockito.mock(IdentityMapper.class), context);
    assertThrows(
        ForbiddenException.class,
        () -> service.create(new CreateRoleCommand("CATALOG_MANAGER", Set.of("CATALOG_WRITE"))));
  }
}
