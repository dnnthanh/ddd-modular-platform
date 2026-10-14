package dev.demo.identity.infrastructure.persistence.repository;

import dev.demo.identity.infrastructure.persistence.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for the auth_role table. */
public interface RoleRepository extends JpaRepository<RoleEntity, String> {}
