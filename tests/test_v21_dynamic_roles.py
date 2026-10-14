from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
IDENTITY = ROOT / 'identity-module/src/main/java/dev/demo/identity'
CORE = ROOT / 'platform-core/src/main/java/dev/demo/core'
BACKEND = ROOT / 'modular-monolith/src/main/java/dev/demo/modular'

class DynamicRolesAndContextTests(unittest.TestCase):
    def test_roles_are_data_not_enum(self):
        self.assertFalse((IDENTITY / 'domain/enums/RoleCode.java').exists())
        self.assertTrue((IDENTITY / 'application/port/RolePort.java').exists())
        self.assertTrue((IDENTITY / 'infrastructure/persistence/repository/RoleRepository.java').exists())
        self.assertTrue((BACKEND / 'api/contract/RoleApi.java').exists())
        security=(BACKEND/'security/BackendSecurityConfiguration.java').read_text()
        self.assertNotIn('RoleCode.valueOf',security)

    def test_prevent_privilege_grants_and_perms_are_hardcoded(self):
        roles=(IDENTITY/'application/service/RoleApplicationService.java').read_text()
        accounts=(IDENTITY/'application/service/IdentityApplicationService.java').read_text()
        self.assertIn('Permission.fromCode(permission)',roles)
        self.assertIn('userContext.hasPermission(permission)',roles)
        self.assertIn('AppConstants.Roles.SYSTEM_ADMIN',roles)
        self.assertIn('rolePort.existingCodes(roles)',accounts)
        self.assertTrue((IDENTITY/'domain/enums/Permission.java').exists())

    def test_optional_context_and_field_validation(self):
        utils=(CORE/'security/SecurityUtils.java').read_text()
        self.assertIn('Optional<UUID> userIdOptional()',utils)
        config=(CORE/'security/UserContextConfiguration.java').read_text()
        self.assertIn('SecurityUtils.authentication()',config)
        handler=(CORE/'web/GlobalExceptionHandler.java').read_text()
        self.assertIn('getConstraintViolations()',handler)
        self.assertIn('getParameterValidationResults()',handler)
        self.assertIn('getFieldErrors()',handler)

    def test_3_parameter_mapper_and_organized_commands(self):
        mapper=(CORE/'mapper/EntityMapper.java').read_text()
        self.assertIn('EntityMapper<D, E, M>',mapper)
        self.assertTrue((IDENTITY/'application/command/CreateUserCommand.java').exists())
        self.assertFalse((IDENTITY/'application/usecase/CreateUserCommand.java').exists())

if __name__ == '__main__':
    unittest.main()
