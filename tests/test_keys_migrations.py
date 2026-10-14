"""Static security/migration checks (no Maven, DB or registry required)."""
from pathlib import Path
from xml.etree import ElementTree as ET
import unittest

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'modular-monolith/src/main/resources/db/changelog'
NS = '{http://www.liquibase.org/xml/ns/dbchangelog}'

class SecurityAndLiquibaseTests(unittest.TestCase):
    def test_key_reader_in_core_and_no_extra_module(self):
        reader = ROOT / 'platform-core/src/main/java/dev/demo/core/security/jwt/JwtKeyResources.java'
        self.assertTrue(reader.is_file())
        self.assertIn('@UtilityClass', reader.read_text())
        self.assertFalse((ROOT / 'jwt-keys').exists())
        for pom in ROOT.glob('**/pom.xml'):
            self.assertNotIn('<artifactId>jwt-keys</artifactId>', pom.read_text())

    def test_signing_key_not_shipped_in_source_resources(self):
        # Keep private key external, even if a public-only PEM is bundled later.
        for resource in ROOT.glob('*/src/main/resources/**/*'):
            if resource.is_file():
                self.assertNotIn('-----BEGIN PRIVATE KEY-----', resource.read_text(errors='replace'))
        dockerignore = (ROOT / '.dockerignore').read_text()
        self.assertIn('.deploy', dockerignore)
        compose = (ROOT / 'compose.yaml').read_text()
        self.assertEqual(compose.count('./secrets/jwt/private.pem:/run/secrets/jwt-private.pem:ro'), 1)
        self.assertIn('user: "${APP_UID:-10001}:${APP_GID:-10001}"', compose)

    def test_xml_include_chains_to_plain_sql(self):
        master = ET.parse(BASE / 'master.xml').getroot()
        includes = master.findall(f'{NS}include')
        self.assertGreaterEqual(len(includes), 3)
        for inc in includes:
            self.assertEqual(inc.attrib.get('relativeToChangelogFile'), 'true')
            xmlpath = BASE / inc.attrib['file']
            self.assertTrue(xmlpath.exists())
            child = ET.parse(xmlpath).getroot()
            changesets = child.findall(f'{NS}changeSet')
            self.assertEqual(len(changesets), 1)
            ref = changesets[0].find(f'{NS}sqlFile')
            self.assertIsNotNone(ref)
            self.assertTrue((xmlpath.parent / ref.attrib['path']).is_file())
            self.assertTrue(ref.attrib.get('relativeToChangelogFile') == 'true')
        self.assertFalse((BASE / 'db.changelog-master.yaml').exists())

    def test_sql_idempotency_guards_and_compose_clean(self):
        catalog = (BASE / 'changes/sql/001-catalog.sql').read_text()
        identity = (BASE / 'changes/sql/002-identity.sql').read_text()
        self.assertIn('CREATE TABLE IF NOT EXISTS',catalog)
        self.assertIn('CREATE INDEX IF NOT EXISTS',catalog)
        self.assertIn('ON CONFLICT',identity)
        self.assertNotIn('--liquibase formatted sql', identity)
        self.assertNotIn('<artifactId>jwt-keys</artifactId>', (ROOT / 'README.md').read_text())

if __name__ == '__main__':
    unittest.main()
