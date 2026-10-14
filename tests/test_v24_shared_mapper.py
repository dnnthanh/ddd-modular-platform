"""v24: a single named Jackson mapper, injected into REST and typed Redis."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


def source(path: str) -> str:
    return (ROOT / path).read_text()


class V24SharedMapperTests(unittest.TestCase):
    def test_explicit_bean_is_primary_and_reuses_modules(self):
        text = source('platform-core/src/main/java/dev/demo/core/json/CoreJsonConfiguration.java')
        self.assertIn('@Bean\n  @Primary\n  JsonMapper objectMapper(List<SimpleModule> modules)', text)
        self.assertIn('JsonMapper.builder()', text)
        self.assertIn('JsonInclude.Include.NON_EMPTY', text)
        self.assertIn('modules.forEach(builder::addModule)', text)
        self.assertIn('SimpleModule stringTrimModule', text)

    def test_redis_uses_shared_mapper(self):
        text = source('platform-core/src/main/java/dev/demo/core/cache/CoreCacheConfiguration.java')
        self.assertIn('ObjectMapper mapper', text)
        self.assertIn('new TypedJacksonRedisSerializer<>(mapper, definition.type())', text)

    def test_spring_yaml_does_not_duplicate_mapper_policy(self):
        text = source('modular-monolith/src/main/resources/application.yml')
        self.assertNotIn('default-property-inclusion:', text)

    def test_runtime_java_test_exists(self):
        java = source('platform-core/src/test/java/dev/demo/core/json/SharedObjectMapperTest.java')
        self.assertIn('new TypedJacksonRedisSerializer<>(mapper', java)
        self.assertIn('JsonInclude.Include.NON_EMPTY', source('platform-core/src/main/java/dev/demo/core/json/CoreJsonConfiguration.java'))


if __name__ == '__main__':
    unittest.main()
