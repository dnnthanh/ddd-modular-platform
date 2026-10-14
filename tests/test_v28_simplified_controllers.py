"""Regression checks for direct controller/use-case wiring and explicit injection names."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
API = ROOT / "modular-monolith/src/main/java/dev/demo/modular/api"


class SimplifiedControllerTests(unittest.TestCase):
    def test_no_facade_or_short_mapper_fields(self):
        self.assertFalse((API / "facade").exists())
        for name, use_case, mapper in [
            ("CatalogController.java", "CatalogUseCase catalogUseCase", "CatalogMapper catalogMapper"),
            ("AuthController.java", "IdentityUseCase identityUseCase", "IdentityMapper identityMapper"),
            ("RoleController.java", "RoleUseCase roleUseCase", "IdentityMapper identityMapper"),
        ]:
            source = (API / name).read_text()
            self.assertIn("@RestController", source)
            self.assertIn(use_case, source)
            self.assertIn(mapper, source)
            self.assertNotIn("Facade", source)
        for f in ROOT.rglob("*.java"):
            if "/src/main/" not in str(f):
                continue
            code = f.read_text()
            self.assertNotRegex(code, r"private final (?:CatalogMapper|IdentityMapper|CatalogRepository|UserAccountRepository|RoleRepository) (?:mapper|jpa|repository);")

    def test_enrich_uses_immutable_model_copy_and_no_mapper_expression(self):
        app = ROOT / "catalog-module/src/main/java/dev/demo/catalog"
        service = (app / "application/service/CatalogApplicationService.java").read_text()
        mapper = (app / "mapper/CatalogMapper.java").read_text()
        model = (app / "application/model/CatalogItemModel.java").read_text()
        self.assertIn("model.withDisplayLabel(", service)
        self.assertIn("catalogMapper.toModel(", service)
        self.assertNotIn("new CatalogItemModel(", service)
        self.assertNotIn('expression = "java(', mapper)
        self.assertIn("CatalogItemModel withDisplayLabel(", model)
        self.assertIn("return new CatalogItemModel(id, sku, name, displayLabel);", model)

    def test_catalog_port_has_no_duplicate_javadoc(self):
        code = (ROOT / "catalog-module/src/main/java/dev/demo/catalog/application/port/CatalogPort.java").read_text()
        self.assertNotRegex(code, r"\*/\s*/\*\*")
        self.assertIn("/** Finds an item by its identifier. */", code)

if __name__ == "__main__":
    unittest.main()
