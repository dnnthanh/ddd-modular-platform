package dev.demo.identity.application.bootstrap;

import dev.demo.identity.application.port.UserAccountPort;
import dev.demo.identity.domain.entity.UserAccount;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Demo bootstrap. Must be disabled and handled by IAM/secrets in real deployments. */
@Component
@Slf4j
@RequiredArgsConstructor
public class BootstrapAdmin implements ApplicationRunner {

  private final UserAccountPort userAccountPort;
  private final PasswordEncoder passwordEncoder;

  private final BootstrapAdminProperties properties;

  @Override
  public void run(ApplicationArguments args) {
    String username = properties.username();
    String password = properties.password();
    if (StringUtils.isNoneBlank(username, password)) {
      try {
        userAccountPort.insertBootstrapAdmin(
            new UserAccount(UUID.randomUUID(), username, passwordEncoder.encode(password), true));
      } catch (DataIntegrityViolationException ex) {
        // Another replica may have completed the bootstrap first. Never reset roles.
        if (userAccountPort.findByUsername(username).isEmpty()) {
          throw ex;
        }
        log.info("Bootstrap user already exists; preserving existing credentials and roles");
      }
    }
  }
}
