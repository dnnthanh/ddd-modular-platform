from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


class SpotlessAndGlobalTrimTests(unittest.TestCase):
    def test_formatter_is_maven_spotless_not_idea_process(self):
        script = (ROOT / 'scripts/format.sh').read_text()
        self.assertIn('spotless:apply', script)
        self.assertIn('spotless:check', script)
        self.assertNotIn('Contents/MacOS/idea', script)
        self.assertNotIn('IDEA_CLI', script)

    def test_all_modules_inherit_spotless_plugin(self):
        root = ET.parse(ROOT / 'pom.xml').getroot()
        ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
        artifacts = [x.text for x in root.findall('.//m:build/m:plugins/m:plugin/m:artifactId', ns)]
        self.assertIn('spotless-maven-plugin', artifacts)

    def test_json_normalization_is_configured_once(self):
        cfg = (ROOT / 'platform-core/src/main/java/dev/demo/core/json/CoreJsonConfiguration.java').read_text()
        self.assertIn('module.addDeserializer(String.class', cfg)
        self.assertIn('JsonTrimProperties', cfg)
        req = (ROOT / 'catalog-module/src/main/java/dev/demo/catalog/interfaces/dto/request/CreateCatalogItemRequest.java').read_text()
        self.assertNotIn('@JsonDeserialize', req)
        props = (ROOT / 'platform-core/src/main/java/dev/demo/core/json/JsonTrimProperties.java').read_text()
        self.assertIn('"password"', props)
        self.assertIn('"accessToken"', props)

    def test_production_source_has_no_raw_null_comparisons(self):
        for f in ROOT.glob('*/src/main/**/*.java'):
            s = f.read_text()
            with self.subTest(file=str(f.relative_to(ROOT))):
                self.assertNotIn('== null', s)
                self.assertNotIn('!= null', s)


if __name__ == '__main__':
    unittest.main()
