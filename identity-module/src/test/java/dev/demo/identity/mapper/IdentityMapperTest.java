package dev.demo.identity.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.demo.identity.domain.entity.RoleDefinition;
import dev.demo.identity.domain.entity.UserAccount;
import dev.demo.identity.interfaces.dto.request.LoginRequest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class IdentityMapperTest {

  private final IdentityMapper mapper = Mappers.getMapper(IdentityMapper.class);

  @Test
  void mapsEntityAndAccountProjection() {
    var user = new UserAccount(UUID.randomUUID(), "alice", "hidden", true);
    var entity = mapper.toEntity(user);
    assertEquals(user.id(), entity.getId());
    assertTrue(entity.isEnabled());
    assertEquals(user, mapper.toDomain(entity));
    assertEquals(List.of(user), mapper.toDomains(List.of(entity)));
    var page = mapper.toDomainPage(new PageImpl<>(List.of(entity), PageRequest.of(1, 2), 3));
    assertEquals(3L, page.getTotalElements());
    assertEquals(user, page.getContent().getFirst());
    assertEquals(user.id(), mapper.toEntities(List.of(user)).getFirst().getId());
    var model = mapper.toModel(user, List.of("CATALOG_VIEWER"), List.of("CATALOG_READ"));
    assertEquals(user.id(), model.id());
    assertEquals(List.of("CATALOG_READ"), model.permissions());
  }

  @Test
  void mapsDynamicRolesAndPermissions() {
    var role = new RoleDefinition("ACCOUNT_SUPPORT", Set.of("USERS_READ"));
    var entity = mapper.toRoleEntity(role);
    assertEquals(role, mapper.toRoleDefinition(entity));
    assertEquals(role.code(), mapper.toRoleModel(role).code());
    assertEquals(1, mapper.toRoleModels(List.of(role)).size());
  }

  @Test
  void mapsLoginRequestToCommand() {
    var request = new LoginRequest();
    request.setUsername("alice");
    request.setPassword("secret");
    assertEquals("alice", mapper.toCommand(request).username());
  }
}
