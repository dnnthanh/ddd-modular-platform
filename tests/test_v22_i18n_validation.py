from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / 'platform-core/src/main'


def parse_messages(path):
    data = {}
    for line in path.read_text(encoding='utf-8').splitlines():
        if not line or line.startswith('#'):
            continue
        key, sep, value = line.partition('=')
        if sep:
            assert key not in data, f'duplicate message key {key} in {path}'
            data[key] = value
    return data


class I18nValidationConventionTest(unittest.TestCase):
    def test_validation_is_registered_with_application_message_source(self):
        cfg = (CORE / 'java/dev/demo/core/i18n/InternationalizationConfiguration.java').read_text()
        self.assertIn('LocalValidatorFactoryBean', cfg)
        self.assertIn('validator.setValidationMessageSource(messageSource)', cfg)
        self.assertIn('resolver.setDefaultLocale(DEFAULT_LOCALE)', cfg)
        self.assertIn('Locale.forLanguageTag("vi")', cfg)

    def test_bundles_share_all_keys_and_fallback_is_vietnamese(self):
        res = CORE / 'resources'
        bundles = {name: parse_messages(res / name) for name in
                   ('messages.properties', 'messages_vi.properties', 'messages_en.properties')}
        self.assertEqual(set(bundles['messages.properties']), set(bundles['messages_vi.properties']))
        self.assertEqual(set(bundles['messages.properties']), set(bundles['messages_en.properties']))
        self.assertEqual(bundles['messages.properties']['validation.required'], 'Trường này không được bỏ trống')
        self.assertEqual(bundles['messages_en.properties']['validation.required'], 'This field is required')
        self.assertEqual(bundles['messages.properties']['error.UNAUTHORIZED'], 'Cần xác thực')
        self.assertFalse((res / 'ValidationMessages.properties').exists())

    def test_custom_validation_keys_all_exist(self):
        res = parse_messages(CORE / 'resources/messages.properties')
        root = ROOT
        for module in ('identity-module','catalog-module','platform-core','modular-monolith'):
            for source in (root / module / 'src/main').rglob('*.java'):
                for key in re.findall(r'\{(validation\.[\w.]+)\}', source.read_text()):
                    self.assertIn(key,res, f'{source} refers to absent {key}')

    def test_early_security_errors_use_locale_resolver(self):
        config = (ROOT / 'modular-monolith/src/main/java/dev/demo/modular/security/BackendSecurityConfiguration.java').read_text()
        self.assertIn('localeResolver.resolveLocale(request)', config)
        self.assertNotIn('messages.resolve(code, request.getLocale())', config)

    def test_validation_translation_in_java_unit_tests(self):
        test = ROOT / 'platform-core/src/test/java/dev/demo/core/i18n/InternationalizationTest.java'
        text = test.read_text()
        self.assertIn('context.getBean(Validator.class)', text)
        self.assertIn('Locale.ENGLISH', text)
        self.assertIn('"vi"', text)
        self.assertIn('Trường này không được bỏ trống', text)

if __name__ == '__main__':
    unittest.main()
