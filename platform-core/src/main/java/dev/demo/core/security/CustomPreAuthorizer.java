package dev.demo.core.security;

import java.util.Arrays;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.util.CollectionUtils;

/** SpEL target: @customPreAuthorizer.hasAnyPermission({'PERMISSION_A', 'PERMISSION_B'}). */
@RequiredArgsConstructor
public class CustomPreAuthorizer {

  private final UserContext userContext;

  public boolean hasPermission(String permission) {
    return userContext.hasPermission(permission);
  }

  public boolean hasAnyPermission(Collection<String> permissions) {
    return !CollectionUtils.isEmpty(permissions)
        && (userContext.isPrivileged()
            || permissions.stream().anyMatch(userContext::hasPermission));
  }

  public boolean hasAnyPermission(String... permissions) {
    return ArrayUtils.isNotEmpty(permissions) && hasAnyPermission(Arrays.asList(permissions));
  }
}
