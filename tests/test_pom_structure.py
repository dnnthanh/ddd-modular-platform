"""Regression checks for the multi-module Maven reactor and readable POM XML.

Runs without Maven/JDK and is not a replacement for `mvn clean verify`.
"""
import re
import unittest
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
NS = "http://maven.apache.org/POM/4.0.0"
N = f"{{{NS}}}"


def txt(node, child):
    val = node.findtext(f"{N}{child}")
    return val.strip() if val else None


class MavenPomStructureTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = ET.parse(ROOT / "pom.xml").getroot()
        cls.modules = [element.text.strip() for element in cls.root.findall(f"{N}modules/{N}module")]
        cls.artifact = txt(cls.root, "artifactId")
        cls.version = txt(cls.root, "version")

    def test_all_modules_exist_and_have_expected_parent(self):
        self.assertEqual(len(self.modules), len(set(self.modules)))
        self.assertEqual(
            set(self.modules),
            {p.parent.name for p in ROOT.glob("*/pom.xml")},
        )
        for module in self.modules:
            pom = ET.parse(ROOT / module / "pom.xml").getroot()
            self.assertEqual(txt(pom, "artifactId"), module)
            parent = pom.find(f"{N}parent")
            self.assertIsNotNone(parent)
            self.assertEqual(txt(parent, "artifactId"), self.artifact)
            self.assertEqual(txt(parent, "version"), self.version)

    def test_unprefixed_default_maven_namespace_in_every_pom(self):
        for pom in [ROOT / "pom.xml", *ROOT.glob("*/pom.xml")]:
            with self.subTest(path=pom.name, module=pom.parent.name):
                raw = pom.read_text(encoding="utf-8")
                self.assertIn('<project xmlns="http://maven.apache.org/POM/4.0.0"', raw)
                self.assertNotIn("ns0:", raw)
                node = ET.fromstring(raw)
                self.assertEqual(node.tag, f"{N}project")
                self.assertEqual(txt(node, "modelVersion"), "4.0.0")

    def test_internal_dependencies_get_versions_from_parent_management(self):
        managed = {
            (txt(dep, "groupId"), txt(dep, "artifactId")): txt(dep, "version")
            for dep in self.root.findall(f"{N}dependencyManagement/{N}dependencies/{N}dependency")
        }
        for module in self.modules:
            for dep in ET.parse(ROOT / module / "pom.xml").getroot().findall(f"{N}dependencies/{N}dependency"):
                if txt(dep, "groupId") == "dev.demo":
                    key = ("dev.demo", txt(dep, "artifactId"))
                    self.assertIn(key, managed, f"Missing dependencyManagement for {key}")
                    self.assertEqual(managed[key], "${project.version}")

    def test_java_25_pinned(self):
        self.assertEqual(self.root.findtext(f"{N}properties/{N}java.version"), "25")
        self.assertEqual(self.root.findtext(f"{N}properties/{N}maven.compiler.release"), "25")


if __name__ == "__main__":
    unittest.main()
