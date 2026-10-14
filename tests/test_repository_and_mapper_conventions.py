"""Static checks for DDD port/adapter and MapStruct API conventions (not compilation)."""
from pathlib import Path
import unittest
import re

ROOT = Path(__file__).resolve().parents[1]
IDENTITY = ROOT / "identity-module/src/main/java/dev/demo/identity"
CATALOG = ROOT / "catalog-module/src/main/java/dev/demo/catalog"
API = ROOT / "modular-monolith/src/main/java/dev/demo/modular/api"


class RepositoryAndMapperConventionsTest(unittest.TestCase):
    def test_application_ports_are_distinct_from_jpa_repositories(self):
        for root, base in ((IDENTITY, 'UserAccount'), (CATALOG, 'Catalog')):
            port = root / f'application/port/{base}Port.java'
            repo = root / f'infrastructure/persistence/repository/{base}Repository.java'
            entity = root / f'infrastructure/persistence/entity/{base}Entity.java'
            adapter = root / f'infrastructure/persistence/{base}PersistenceAdapter.java'
            self.assertTrue(port.is_file())
            self.assertTrue(repo.is_file())
            self.assertTrue(entity.is_file())
            self.assertIn(f'interface {base}Repository extends JpaRepository<{base}Entity, UUID>', repo.read_text())
            self.assertIn(f'implements {base}Port', adapter.read_text())
            self.assertIn(f'private final {base}Repository {base[0].lower() + base[1:]}Repository;', adapter.read_text())
            self.assertFalse((root / f'application/repository/{base}Repository.java').exists())

    def test_mapping_collections_is_generated_and_pages_are_preserved(self):
        identity = (IDENTITY / 'mapper/IdentityMapper.java').read_text()
        catalog = (CATALOG / 'mapper/CatalogMapper.java').read_text()
        generic = (ROOT / 'platform-core/src/main/java/dev/demo/core/mapper/EntityMapper.java').read_text()
        self.assertIn('toDomains(List<', generic)
        self.assertIn('toEntities(List<', generic)
        self.assertIn('extends EntityMapper<UserAccount, UserAccountEntity, UserModel>', identity)
        self.assertIn('extends EntityMapper<CatalogItem, CatalogEntity, CatalogItemModel>', catalog)
        self.assertIn('toModelPage(Page<', catalog)
        self.assertIn('toResponsePage(Page<', catalog)
        self.assertIn('catalogMapper.toDomainPage(',
                      (CATALOG / 'infrastructure/persistence/CatalogPersistenceAdapter.java').read_text())
        self.assertIn('catalogMapper.toResponsePage(',
                      (API / 'CatalogController.java').read_text())
        self.assertIn('private final CatalogMapper catalogMapper;',
                      (API / 'CatalogController.java').read_text())

    def test_api_command_and_roles_conversion_use_mappers(self):
        mapper = (IDENTITY / 'mapper/IdentityMapper.java').read_text()
        controller = (API / 'AuthController.java').read_text()
        service = (IDENTITY / 'application/service/IdentityApplicationService.java').read_text()
        self.assertIn('ChangeUserRolesCommand toCommand(', mapper)
        self.assertIn('identityMapper.toCommand(userId, request)', controller)
        self.assertNotIn('RoleCode', mapper)
        self.assertIn('private final RolePort rolePort;', service)
        self.assertIn('identityMapper.toModel(user,userAccountPort.roles(', re.sub(r'\s+', '', service))


if __name__ == '__main__':
    unittest.main()
