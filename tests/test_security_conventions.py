"""Static regression checks for multi-module security conventions (not Java compilation)."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT / "modular-monolith/src/main/java/dev/demo/modular"
IDENTITY = ROOT / "identity-module/src/main/java/dev/demo/identity"
CORE = ROOT / "platform-core/src/main/java/dev/demo/core"
GATEWAY = ROOT / "api-gateway/src/main/java/dev/demo/gateway"


class SecurityConventionsTest(unittest.TestCase):
    def test_delegating_validator_import_is_from_core(self):
        security = (BACKEND / "security/BackendSecurityConfiguration.java").read_text()
        self.assertIn("org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator", security)
        self.assertNotIn("oauth2.jwt.DelegatingOAuth2TokenValidator", security)

    def test_jwt_has_no_role_permission_claims(self):
        identity = (IDENTITY / "application/service/IdentityApplicationService.java").read_text()
        self.assertIn("JwtClaimsSet.builder()", identity)
        self.assertNotIn('.claim("permissions"', identity)
        self.assertNotIn('.claim("roles"', identity)
        self.assertIn("users.roles(account.id())", (BACKEND / "security/BackendSecurityConfiguration.java").read_text())
        self.assertIn("users.permissions(account.id())", (BACKEND / "security/BackendSecurityConfiguration.java").read_text())

    def test_all_controller_endpoints_use_method_security(self):
        for controller in (BACKEND / 'api/contract').glob('*Api.java'):
            content = controller.read_text()
            for item in re.finditer(r'@(PostMapping|GetMapping|PutMapping|DeleteMapping|PatchMapping)\b', content):
                following = content[item.start():item.start()+250]
                self.assertIn('@PreAuthorize(', following, str(controller))
        for target in (BACKEND / 'security/BackendSecurityConfiguration.java',
                       GATEWAY / 'GatewaySecurityConfiguration.java'):
            security = target.read_text()
            self.assertIn('/api/public/**', security)
            self.assertIn('/api/private/**', security)
            self.assertIn('denyAll()', security)

    def test_permission_guard_admin_only_privilege_exception(self):
        guard = (CORE / 'security/UserContext.java').read_text()
        self.assertIn('AppConstants.Roles.PREFIX', guard)
        self.assertIn('public static final String PREFIX = "ROLE_";', (CORE / 'constants/AppConstants.java').read_text())
        self.assertIn('hasRole(AppConstants.Roles.ADMIN) || hasRole(AppConstants.Roles.SYSTEM_ADMIN)', guard)
        self.assertIn('(isPrivileged() || authorities.contains(permission))', guard)

    def test_permissions_are_uppercase_and_authorizer_supports_spel_lists(self):
        constants = (CORE / 'constants/AppConstants.java').read_text()
        preauth = (CORE / 'security/CustomPreAuthorizer.java').read_text()
        contracts = (BACKEND / 'api/contract/CatalogApi.java').read_text()
        self.assertIn("hasAnyPermission({'CATALOG_READ'})", contracts)
        self.assertIn("hasAnyPermission({'CATALOG_WRITE'})", contracts)
        self.assertNotIn('SecurityExpressions', constants)
        self.assertIn('hasAnyPermission(Collection<String> permissions)', preauth)
        self.assertIn('@Bean("customPreAuthorizer")',
                      (CORE / 'security/UserContextConfiguration.java').read_text())
        enum_source = (IDENTITY / 'domain/enums/Permission.java').read_text()
        self.assertIn('AppConstants.Permissions.CATALOG_READ', enum_source)
        self.assertIn('@PreAuthorize(', contracts)
        self.assertIn('@RestController', (BACKEND / 'api/CatalogController.java').read_text())

    def test_configuration_properties_and_keys_not_deploy_generated(self):
        jwt_props = (CORE / 'security/jwt/JwtProperties.java').read_text()
        self.assertIn('@ConfigurationProperties(prefix = "app.security.jwt")', jwt_props)
        script = (ROOT / 'scripts/deploy.sh').read_text()
        self.assertNotIn('openssl genpkey', script)
        self.assertIn('openssl genpkey', (ROOT / 'scripts/generate-jwt-keys.sh').read_text())
        self.assertIn('secrets/', (ROOT / '.gitignore').read_text())

    def test_fresh_liquibase_seed_has_uppercase_permissions_without_004(self):
        base = ROOT / 'modular-monolith/src/main/resources/db/changelog'
        master = (base / 'master.xml').read_text()
        sql = (base / 'changes/sql/002-identity.sql').read_text()
        self.assertNotIn('004-uppercase-permissions', master)
        self.assertFalse((base / 'changes/004-uppercase-permissions.xml').exists())
        self.assertFalse((base / 'changes/sql/004-uppercase-permissions.sql').exists())
        for permission in ('CATALOG_READ', 'CATALOG_WRITE', 'USERS_READ', 'USERS_MANAGE'):
            self.assertIn(permission, sql)
        for old in ('catalog:read', 'catalog:write', 'users:read', 'users:manage'):
            self.assertNotIn("'" + old + "'", sql)

    def test_jpa_crud_stays_jpa_and_native_is_only_projection(self):
        repository = (IDENTITY / 'infrastructure/persistence/repository/UserAccountRepository.java').read_text()
        adapter = (IDENTITY / 'infrastructure/persistence/UserAccountPersistenceAdapter.java').read_text()
        self.assertEqual(repository.count('nativeQuery = true'), 1)
        self.assertIn('userAccountRepository.saveAndFlush(', adapter)
        self.assertIn('user.getRoles().addAll(', adapter)
        self.assertNotIn('userAccountRepository.assignRole(', adapter)

    def test_i18n_resolver_overloads_and_one_main_executable(self):
        resolver = (CORE / 'i18n/MessageResolver.java').read_text()
        self.assertIn('resolve(ErrorCode code, Object... args)', resolver)
        self.assertIn('resolve(String key, Object... args)', resolver)
        self.assertIn('resolve(ErrorCode code, Locale locale, Object... args)', resolver)
        self.assertIn('resolve(String key, Locale locale, Object... args)', resolver)
        self.assertTrue((ROOT / 'modular-monolith/src/main/java/dev/demo/ModularApplication.java').exists())


if __name__ == '__main__':
    unittest.main()
