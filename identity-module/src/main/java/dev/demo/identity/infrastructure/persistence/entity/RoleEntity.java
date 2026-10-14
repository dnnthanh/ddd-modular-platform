package dev.demo.identity.infrastructure.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "auth_role")
@Getter
@Setter
@NoArgsConstructor
public class RoleEntity {

  @Id
  @Column(length = 64)
  private String code;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "auth_role_permission", joinColumns = @JoinColumn(name = "role_code"))
  @Column(name = "permission_code", nullable = false, length = 100)
  private Set<String> permissions = new HashSet<>();
}
