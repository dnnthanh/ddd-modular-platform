"""Prevent regression to google-java-format versions predating JDK 25."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


class SpotlessJdk25Test(unittest.TestCase):

    def test_compatible_formatter_version_is_declared(self):
        root = ET.parse(ROOT / "pom.xml").getroot()
        plugin = next(p for p in root.findall(".//m:plugin", NS)
                      if p.findtext("m:artifactId", namespaces=NS) == "spotless-maven-plugin")
        self.assertEqual("2.46.0", plugin.findtext("m:version", namespaces=NS))
        formatter = plugin.find("m:configuration/m:java/m:googleJavaFormat", NS)
        self.assertIsNotNone(formatter)
        self.assertEqual("1.32.0", formatter.findtext("m:version", namespaces=NS))
        self.assertEqual("GOOGLE", formatter.findtext("m:style", namespaces=NS))

if __name__ == "__main__":
    unittest.main()
