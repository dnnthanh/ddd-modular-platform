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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "auth_user")
@Getter
@Setter
@NoArgsConstructor
public class UserAccountEntity {

  @Id private UUID id;

  @Column(nullable = false, length = 64, unique = true)
  private String username;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(nullable = false)
  private boolean enabled;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "auth_user_role", joinColumns = @JoinColumn(name = "user_id"))
  @Column(name = "role_code", nullable = false, length = 64)
  private Set<String> roles = new HashSet<>();
}
