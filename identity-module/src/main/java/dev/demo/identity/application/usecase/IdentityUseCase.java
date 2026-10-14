package dev.demo.identity.application.usecase;

import dev.demo.identity.application.command.ChangeUserRolesCommand;
import dev.demo.identity.application.command.CreateUserCommand;
import dev.demo.identity.application.command.LoginCommand;
import dev.demo.identity.application.model.TokenModel;
import dev.demo.identity.application.model.UserModel;
import java.time.Instant;

public interface IdentityUseCase {

  /** Authenticates credentials and creates a short-lived access token. */
  TokenModel login(LoginCommand command);

  /** Revokes a token for the remaining validity period. */
  void logout(String jti, Instant expiresAt);

  /** Returns the current authenticated profile. */
  UserModel profileCurrent();

  /** Creates a user with validated database-defined roles. */
  UserModel create(CreateUserCommand command);

  /** Replaces the user role assignments after checking role existence. */
  UserModel updateRoles(ChangeUserRolesCommand command);
}
