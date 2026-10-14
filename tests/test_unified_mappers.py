"""Static regression tests for one mapper per bounded context (not Java compilation)."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class UnifiedMapperConventionTests(unittest.TestCase):

    def test_one_mapper_per_bounded_context(self):
        for module, expected in (("catalog-module", "CatalogMapper.java"),
                                 ("identity-module", "IdentityMapper.java")):
            files = list((ROOT / module / "src/main/java").rglob("*Mapper.java"))
            self.assertEqual([f.name for f in files], [expected])
        self.assertEqual([], list((ROOT / "modular-monolith/src/main/java").rglob("*Mapper.java")))

    def test_no_old_mapper_classes_or_old_dto_packages(self):
        names = ("CatalogApiMapper", "CatalogPersistenceMapper", "CatalogModelMapper",
                 "CatalogRequestMapper", "UserAccountModelMapper",
                 "UserAccountPersistenceMapper", "IdentityRequestMapper",
                 "dev.demo.modular.api.dto")
        for module in ("catalog-module", "identity-module", "modular-monolith"):
            for source in (ROOT / module / "src").rglob("*.java"):
                content = source.read_text()
                for old_name in names:
                    self.assertNotIn(old_name, content, (source, old_name))

    def test_module_owns_transport_dto_to_avoid_maven_dependency_cycle(self):
        cat = ROOT / "catalog-module/src/main/java/dev/demo/catalog/interfaces/dto"
        identity = ROOT / "identity-module/src/main/java/dev/demo/identity/interfaces/dto"
        self.assertTrue((cat / "response/CatalogItemResponse.java").exists())
        self.assertTrue((cat / "request/CreateCatalogItemRequest.java").exists())
        self.assertTrue((identity / "request/LoginRequest.java").exists())
        controller_dir = ROOT / "modular-monolith/src/main/java/dev/demo/modular/api"
        self.assertEqual([], list((controller_dir / "facade").glob("*.java")))
        self.assertIn("private final CatalogMapper catalogMapper;",
                      (controller_dir / "CatalogController.java").read_text())
        self.assertIn("private final IdentityMapper identityMapper;",
                      (controller_dir / "AuthController.java").read_text())


if __name__ == "__main__":
    unittest.main()
