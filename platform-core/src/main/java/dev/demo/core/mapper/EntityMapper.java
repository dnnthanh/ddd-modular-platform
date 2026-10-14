package dev.demo.core.mapper;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Generic MapStruct contract for domain objects, persistence entities and application models. Each
 * module defines DTO/command conversions in its own mapper interface. Some models (for example
 * UserModel) also need additional projections such as roles.
 *
 * @param <D> domain type
 * @param <E> JPA entity type
 * @param <M> application model type
 */
public interface EntityMapper<D, E, M> {

  /** Converts a persistence entity into a domain object. */
  D toDomain(E entity);

  /** Converts a domain object into a persistence entity. */
  E toEntity(D domain);

  /** Converts a collection of persistence entities into domain objects. */
  List<D> toDomains(List<E> entities);

  /** Converts a collection of domain objects into persistence entities. */
  List<E> toEntities(List<D> domains);

  /** Preserves Spring Data pagination metadata while converting persistence entities. */
  default Page<D> toDomainPage(Page<E> page) {
    return page.map(this::toDomain);
  }

  /** Preserves pagination while converting to models with the module's mapping function. */
  default Page<M> mapModelPage(Page<D> page, Function<D, M> toModel) {
    return page.map(toModel);
  }
}
