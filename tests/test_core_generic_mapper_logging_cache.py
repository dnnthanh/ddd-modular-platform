"""Static conventions and regression checks, not a substitute for JDK 25 Maven tests."""
from pathlib import Path
import unittest
import re

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "platform-core/src/main/java/dev/demo/core"
CATALOG = ROOT / "catalog-module/src/main/java/dev/demo/catalog"
IDENTITY = ROOT / "identity-module/src/main/java/dev/demo/identity"
BOOT = ROOT / "modular-monolith/src/main"

class CoreGenericMapperLoggingCacheTest(unittest.TestCase):
    def test_shared_mapper_generic_contract(self):
        generic = (CORE / "mapper/EntityMapper.java").read_text()
        self.assertIn("interface EntityMapper<D, E, M>", generic)
        self.assertIn("Page<D> toDomainPage(Page<E> page)", generic)
        for root, mapping in ((CATALOG, 'EntityMapper<CatalogItem, CatalogEntity, CatalogItemModel>'),
                              (IDENTITY, 'EntityMapper<UserAccount, UserAccountEntity, UserModel>')):
            self.assertIn(mapping, next(root.rglob('*Mapper.java')).read_text())
        cfg = (CORE / "mapper/CommonMapperConfig.java").read_text()
        self.assertIn("NullValuePropertyMappingStrategy.IGNORE", cfg)

    def test_exception_handler_logs_all_mvc_errors_once(self):
        code = (CORE / "web/GlobalExceptionHandler.java").read_text()
        self.assertIn('"api.exception status={} code={} method={} path={} type={}', code)
        self.assertIn("loggingException(status, exception)", code)
        self.assertIn('exception.getClass().getSimpleName()', code)
        self.assertNotIn('exception.getClass().getSimpleName(),exception)', re.sub(r'\s+', '', code))
        for marker in ('ErrorCode.VALIDATION_FAILED', 'ErrorCode.FORBIDDEN',
                       'ErrorCode.INTERNAL_ERROR', 'ErrorCode.DATA_UNAVAILABLE'):
            self.assertIn(marker, code)
        self.assertEqual(1, code.count('log.error('))

    def test_typed_cache_is_used_in_catalog_and_ttl_is_configurable(self):
        helper = (CORE / "cache/CacheHelper.java").read_text()
        for fragment in ('get(CacheDefinition<T> definition',
                         'getOrLoad(CacheDefinition<T> definition',
                         'put(CacheDefinition<T> definition',
                         'remove(CacheDefinition<?> definition',
                         'clear(CacheDefinition<?> definition'):
            self.assertIn(fragment, helper)
        service = (CATALOG / "application/service/CatalogApplicationService.java").read_text()
        self.assertIn('cacheHelper.getOrLoad(catalogItemCache', re.sub(r'\s+', '', service))
        self.assertNotIn('@Cacheable', service)
        props = (BOOT / 'java/dev/demo/modular/CatalogCacheProperties.java').read_text()
        self.assertIn('ConfigurationProperties(prefix = "app.cache.catalog")', props)
        self.assertIn('item-ttl: ${CATALOG_ITEM_CACHE_TTL:5m}',
                      (BOOT / 'resources/application.yml').read_text())

if __name__ == '__main__':
    unittest.main()
