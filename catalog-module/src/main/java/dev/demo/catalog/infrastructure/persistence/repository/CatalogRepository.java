package dev.demo.catalog.infrastructure.persistence.repository;

import dev.demo.catalog.application.model.CatalogSearchCriteria;
import dev.demo.catalog.infrastructure.persistence.entity.CatalogEntity;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Query is native PostgreSQL because ILIKE and explicit stable ordering are desired. SpEL evaluates
 * properties on the application search model, never injects raw SQL. Spring Data binds the
 * evaluated values as parameters (not string concatenation).
 */
public interface CatalogRepository extends JpaRepository<CatalogEntity, UUID> {

  /** Runs parameterized PostgreSQL search with a separate count query. */
  @Query(
      value =
          """
      SELECT c.id, c.sku, c.name
      FROM catalog_item c
      WHERE (:#{#criteria.keyword()} = ''
         OR c.sku ILIKE CONCAT('%', :#{#criteria.keyword()}, '%')
         OR c.name ILIKE CONCAT('%', :#{#criteria.keyword()}, '%'))
      ORDER BY c.created_at DESC, c.id DESC
      """,
      countQuery =
          """
          SELECT COUNT(*) FROM catalog_item c
          WHERE (:#{#criteria.keyword()} = ''
             OR c.sku ILIKE CONCAT('%', :#{#criteria.keyword()}, '%')
             OR c.name ILIKE CONCAT('%', :#{#criteria.keyword()}, '%'))
          """,
      nativeQuery = true)
  Page<CatalogEntity> search(@Param("criteria") CatalogSearchCriteria criteria, Pageable pageable);
}
