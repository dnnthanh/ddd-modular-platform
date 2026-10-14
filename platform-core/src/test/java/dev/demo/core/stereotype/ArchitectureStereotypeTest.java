package dev.demo.core.stereotype;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

class ArchitectureStereotypeTest {

  @Test
  void useCaseIsSpringService() {
    assertTrue(AnnotatedElementUtils.hasAnnotation(UseCase.class, Service.class));
  }

  @Test
  void persistenceIsSpringRepository() {
    assertTrue(AnnotatedElementUtils.hasAnnotation(Persistence.class, Repository.class));
  }

  @Test
  void adapterIsSpringComponent() {
    assertTrue(AnnotatedElementUtils.hasAnnotation(Adapter.class, Component.class));
  }
}
