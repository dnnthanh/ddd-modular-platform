"""v23 regression guards: Jackson output config, streamlined boot scanning and scoped context."""
from pathlib import Path
import unittest
import yaml

ROOT = Path(__file__).resolve().parents[1]


class V23JsonBootTests(unittest.TestCase):
    def test_omits_empty_and_null_values_via_shared_mapper(self):
        java = (ROOT / 'platform-core/src/main/java/dev/demo/core/json/CoreJsonConfiguration.java').read_text()
        self.assertIn('JsonInclude.Include.NON_EMPTY', java)
        config = yaml.safe_load((ROOT / 'modular-monolith/src/main/resources/application.yml').read_text())
        self.assertNotIn('jackson', config['spring'])

    def test_root_package_bootstrap_discovers_all_modules_without_manual_imports(self):
        java = (ROOT / 'modular-monolith/src/main/java/dev/demo/ModularApplication.java').read_text()
        self.assertIn('package dev.demo;', java)
        self.assertIn('@SpringBootApplication', java)
        self.assertIn('@ConfigurationPropertiesScan', java)
        for banned in ('scanBasePackages', '@EntityScan', '@EnableJpaRepositories', '@Import'):
            self.assertNotIn(banned, java)
        self.assertFalse((ROOT / 'modular-monolith/src/main/java/dev/demo/modular/ModularApplication.java').exists())

    def test_json_module_preserves_global_input_string_trim(self):
        java = (ROOT / 'platform-core/src/main/java/dev/demo/core/json/CoreJsonConfiguration.java').read_text()
        self.assertIn('SimpleModule stringTrimModule', java)
        self.assertIn('module.addDeserializer(String.class', java)
        self.assertIn('JsonMapper objectMapper(', java)

    def test_user_context_keeps_request_scope_proxy_for_singleton_injection(self):
        java = (ROOT / 'platform-core/src/main/java/dev/demo/core/security/UserContextConfiguration.java').read_text()
        self.assertIn('@RequestScope', java)
        self.assertNotIn('@RequestScope(proxyMode = ScopedProxyMode.NO)', java)
        self.assertIn('CustomPreAuthorizer customPreAuthorizer(UserContext currentUser)', java)

    def test_prior_json_java_test_was_fixed(self):
        java = (ROOT / 'platform-core/src/test/java/dev/demo/core/json/TrimmedStringDeserializerTest.java').read_text()
        self.assertIn('assertEquals("Alice", parsed.displayName())', java)
        self.assertIn('assertEquals("  preserve password  ", parsed.password())', java)


if __name__ == '__main__':
    unittest.main()
