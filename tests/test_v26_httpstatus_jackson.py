"""Regression checks for Spring Framework 7 statuses and Jackson 3 builder API."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

class V26HttpStatusJacksonTests(unittest.TestCase):
    def test_spring7_status_names(self):
        s = (ROOT / "platform-core/src/main/java/dev/demo/core/web/GlobalExceptionHandler.java").read_text()
        self.assertIn("HttpStatus.UNPROCESSABLE_CONTENT", s)
        self.assertIn("HttpStatus.CONTENT_TOO_LARGE", s)
        self.assertNotIn("HttpStatus.UNPROCESSABLE_ENTITY", s)
        self.assertNotIn("HttpStatus.PAYLOAD_TOO_LARGE", s)

    def test_shared_jackson3_mapper_and_safe_features(self):
        s = (ROOT / "platform-core/src/main/java/dev/demo/core/json/CoreJsonConfiguration.java").read_text()
        self.assertIn("changeDefaultPropertyInclusion(", s)
        self.assertNotIn(".defaultPropertyInclusion(", s)
        self.assertIn("FAIL_ON_UNKNOWN_PROPERTIES", s)
        self.assertIn("FAIL_ON_TRAILING_TOKENS", s)
        self.assertIn("FAIL_ON_NULL_FOR_PRIMITIVES", s)
        self.assertIn("modules.forEach(builder::addModule)", s)

    def test_runtime_tests_cover_behavior(self):
        s = (ROOT / "platform-core/src/test/java/dev/demo/core/json/SharedObjectMapperTest.java").read_text()
        for method in ("ignoresUnknownPropertiesForCompatibility", "rejectsNullForPrimitiveFields", "rejectsTrailingJsonTokens"):
            self.assertIn(method, s)

if __name__ == "__main__":
    unittest.main()
