package dev.demo.catalog.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Persistence model only. Domain CatalogItem remains independent from JPA. */
@Entity
@Table(name = "catalog_item")
@Getter
@Setter
@NoArgsConstructor
public class CatalogEntity {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 32)
  private String sku;

  @Column(nullable = false, length = 160)
  private String name;
}
