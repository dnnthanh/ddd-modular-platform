package dev.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Single executable entry point for the modular monolith.
 *
 * <p>The root package lets Spring Boot discover all module components, JPA entities, and Spring
 * Data repositories using their default package scanning conventions.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ModularApplication {

  public static void main(String[] args) {
    SpringApplication.run(ModularApplication.class, args);
  }
}
