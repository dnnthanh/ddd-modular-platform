# DDD Modular Platform — Java 25 / Spring Boot 4 (v26 Spring 7 Status & Jackson 3 Fix)
> Current observability setup: LGTM default datasources only. Alloy collects Java
> logs with the `service_name` stream label; infrastructure is metrics-only.
> Only API/Grafana and direct Java ports are published on loopback. See
> `docs/observability.md` for permission limits and cleanup details.

Starter project for **DDD Modular Monolith**, Maven multi-module, Spring Cloud Gateway, Eureka, PostgreSQL, Redis, Liquibase, RS256 JWT and **grafana/otel-lgtm**. Includes JVM/Hikari, PostgreSQL, Redis metrics and Grafana alert rules. One `modular-app` Compose service scales to N identical JVM containers. **This is a learning/reference stack, not a security-certified production distribution.**

> **Verification scope (v21):** The generation environment has JDK 21 but **no Maven, Docker or JDK 25**. Syntax and consistency checks were performed, but **`mvn clean verify`, Java 25 compilation and Docker integration have not been successfully run here**. Run them in your environment before deploying. Framework version compatibility cannot be verified against current upstream documentation while network search is unavailable.

## 1. Architecture

```text
Client ─► Nginx :8088 (localhost demo) ─► Spring Cloud Gateway × 1
                                               │ JWT / rate limit / route
                                               │ lb://modular-app
                                               ▼
                                     Eureka registry (discovery)
                                               │
                            ┌──────────────────┼─────────────────┐
                            ▼                  ▼                 ▼
                         App × 1            App × 2           App × N
                         │ Catalog module │ Identity module │ platform-core │
                            └──────────────────┼─────────────────┘
                                         PostgreSQL + Redis

Java OTel agent ─OTLP traces + metrics────► grafana/otel-lgtm (one container)
Spring Actuator :/prometheus (each replica) ──┐
PostgreSQL Exporter ─┐                         ├─► Ops metric bridge ─OTLP─► LGTM
Redis Exporter ──────┴─────────────────────────┘
Docker stats/health/events ─► Ops observer ─OTLP─► LGTM
Alloy ─Docker stdout/stderr─► Loki inside LGTM
Grafana Alerting ─► optional Telegram contact point
```

Nginx/Gateway are single-instance **SPOFs** in this tutorial. Eureka is not on the normal business request path. For this local test setup, backend/database/telemetry ports are published on loopback using `.env`; see `docs/observability.md`. Frontend edge is localhost HTTP by default; public HTTPS/TLS, managed secrets, key rotation, reliable backups, WAF/DDoS control, HA and audited RBAC are necessary before production.

## 2. Files, conventions and DDD boundaries

```text
pom.xml
platform-core/
  exception/{AppException,ErrorCode,type/}     # typed errors
  i18n/{MessageResolver,SpringMessageResolver} # enum/key/args/locale
  security/{UserContext,CustomPreAuthorizer,SecurityUtils,jwt/}
  web/{ApiResponse,ApiFailure,PageQuery,GlobalExceptionHandler}
  async/  cache/{CacheHelper,CacheDefinition,CoreCacheConfiguration}  json/  mapper/CommonMapperConfig  observability/  stereotype/
identity-module/
  domain/{entity,enums}/
  application/{usecase,service,port,model}/
  infrastructure/persistence/{entity,repository}/
  interfaces/dto/request/
  mapper/IdentityMapper.java
catalog-module/
  domain/entity/
  application/{usecase,service,port,model}/
  infrastructure/persistence/{entity,repository}/
  interfaces/dto/{request,response}/
  mapper/CatalogMapper.java
modular-monolith/
  api/{contract/,AuthController,CatalogController,RoleController}
  security/BackendSecurityConfiguration.java
  src/main/resources/db/changelog/{master.xml,changes/*.xml,changes/sql/*.sql}
api-gateway/                  # WebFlux gateway + LB + signature verification
discovery-server/             # Eureka
compose.yaml                  # 1 gateway, 1 Eureka, N identical application containers
scripts/deploy.sh             # deploy / scale / restart only
scripts/format.sh             # Maven Spotless apply/check; works with IDEA running
scripts/smoke.sh              # public login, private catalog, logout
```


**Conventions:** `domain` aggregates enforce invariants and never depend on HTTP or JPA. `application` classes annotated `@UseCase` implement business workflows, manage `@Transactional` boundaries, and depend on `application/port` **ports** (`UserAccountPort`, `CatalogPort`). `infrastructure` uses `@Persistence` on adapter implementations, while Spring Data `JpaRepository` and JPA entity classes stay in `infrastructure/persistence/{repository,entity}`. `api.contract` interfaces declare Spring MVC mapping and inline `@PreAuthorize` expressions; implementing controllers use `@RestController` (not `@Adapter`) and call use cases and shared module mappers directly. Controllers handle DTO ↔ application-command/model mapping; the DDD use-case implementations depend on the application use-case interfaces and never import HTTP request/response DTOs. MapStruct converts persistence entity ↔ domain ↔ application model ↔ API response with a common `@MapperConfig`. Application service enriches derived fields (no Java expression inside `@Mapping`). Records represent immutable business models/DTO responses, and Lombok is used on mutable JPA entities/request DTOs and injected services. `@UtilityClass` is for genuine stateless helpers, not Spring-managed objects.

`platform-core/stereotype` defines `@UseCase` (meta-`@Service`), `@Persistence` (meta-`@Repository`) and `@Adapter` (meta-`@Component`). These annotations express **architecture roles**, not a new dependency injection framework. Do not decorate domain entities or Spring Data repository *interfaces* with them. In Java 25, use records for suitable immutable models, SQL text blocks for long queries, interface default methods only when the port genuinely benefits, and switch expressions/pattern matching as needed. Java text blocks are not a Java-25-exclusive feature. Avoid forcing interface `private`/`static` methods or virtual threads where they add no value.

**Mapping:** MapStruct provides list-to-list generated methods `toDomains(List<...>)`, `toEntities(List<...>)`, `toModels(List<...>)`, `toResponses(List<...>)` where the source contains the needed data. A `Page<T>` **cannot be mapped as an ordinary list** without losing metadata; mapping interfaces expose `default Page<R> to...Page(Page<S> page)` using `page.map(this::to...)` to preserve total elements/pages, pageable and sorting. No hand-written `for` loops for collection mapping.

### One executable JAR or microservices?

**The DDD application is ONE deployable Spring Boot executable JAR (`modular-monolith`).** Maven `identity-module`, `catalog-module` and `platform-core` are library modules compiled and packaged into it. Scaling to 3 replicas starts **three copies of the same JAR**, each containing *all* DDD modules. No HTTP calls between business modules are needed. The gateway and Eureka are separately launched **infrastructure applications** (their own JARs), not standalone Catalog/Identity microservices. This preserves modular-monolith boundaries while letting you learn service discovery and horizontal scaling.

## 2.1 Repository ports and unified MapStruct mappers (v18)

The naming convention prevents confusion between the application's persistence boundary and Spring Data:

```text
identity-module/
  application/port/UserAccountPort.java                 # application-owned abstraction
  infrastructure/persistence/UserAccountPersistenceAdapter.java
  infrastructure/persistence/entity/UserAccountEntity.java
  infrastructure/persistence/repository/UserAccountRepository.java  # extends JpaRepository
  mapper/IdentityMapper.java
catalog-module/
  application/port/CatalogPort.java
  infrastructure/persistence/CatalogPersistenceAdapter.java
  infrastructure/persistence/entity/CatalogEntity.java
  infrastructure/persistence/repository/CatalogRepository.java      # extends JpaRepository
  mapper/CatalogMapper.java
```

Only **ports** are injected as persistence dependencies into application services/security/bootstrap; adapters depend on Spring Data repositories and the shared per-context mapper. JPA entities stay in infrastructure packages and are not domain aggregates. The package path distinguishes `UserAccountRepository` from `CatalogRepository`; `Jpa` suffixes are unnecessary here. JPQL entity names were updated after renaming, without touching SQL tables or Liquibase.

**Unified mapper convention (v18):** each bounded context exposes exactly **one** Spring-managed MapStruct mapper, e.g. `CatalogMapper` or `IdentityMapper` in that module's `mapper/` package. It owns `toEntity`, `toDomain`, `toModel`, `toCommand`, `toResponse`, list conversions and page conversions when applicable. Do **not** create separate `*ApiMapper`, `*RequestMapper`, `*PersistenceMapper` or `*ModelMapper` classes for these demo contexts.

HTTP request/response DTOs were moved to the owning module's `interfaces/dto/{request,response}` to avoid a Maven dependency cycle. `modular-monolith` remains the sole executable business application: modules are **library JARs bundled into it**, not microservices. Controllers remain in the executable module and continue to use `@RestController` and interface-driven routes. Controllers use the module mapper directly; application services and persistence adapters use the same mapper type.

MapStruct generates `List<T>` mappings from a shared generic `EntityMapper<D,E,M>` implemented by each module mapper; its default `toDomainPage(Page<E>)` uses `Page.map(...)` to preserve page metadata. Each bounded context still has exactly one mapper. Partial updates use `@MappingTarget` and `CommonMapperConfig` (`NullValuePropertyMappingStrategy.IGNORE`). Catalog `displayLabel` is still enriched in the application service, not with a MapStruct Java expression. Identity's `UserModel` requires roles/permissions passed explicitly from the repository; do not pretend they exist in `UserAccount`.

**Trade-off:** one mapper per module can make its mapper see application, persistence and transport types. This is deliberate simplification for this starter. If a module grows significantly or you later enforce strict compile-time Clean Architecture boundaries, split mapper implementations by responsibility behind an internal facade rather than introducing cyclic Maven dependencies.

### Simpler wiring (v28)

Use explicit constructor-injected names such as `catalogRepository`, `catalogMapper`,
`catalogUseCase`, `userAccountPort`, `identityMapper`. Do not use ambiguous generic names
like `jpa`, `mapper`, `repository`, `facade` when multiple modules share conventions.
The three former `*ApiFacade` classes only delegated to use cases/mappers, so they have
been removed. Interface-driven controllers still hold Spring MVC annotations and inline
`@PreAuthorize`; implementation classes remain `@RestController` and map DTOs at the HTTP
boundary. Application services return application models and do not depend on HTTP DTOs.
Immutable `CatalogItemModel.withDisplayLabel(...)` lets the service enrich a mapped model
without repeating every record constructor argument. Port JavaDocs are kept concise and
non-duplicative.

## 2.2 Interface-driven HTTP controllers and use cases (v16)

Contract interfaces define paths, HTTP parameter annotations, validation, and **inline permission expressions**. Concrete classes are `@RestController` implementations and contain only HTTP-level response creation and delegation. `@Adapter` is reserved for other custom infrastructure adapters; it is **not** a replacement for `@RestController`.

```java
@RequestMapping("/api/private/catalog")
public interface CatalogApi {
  @GetMapping("/{id}")
  @PreAuthorize("@customPreAuthorizer.hasAnyPermission({'CATALOG_READ'})")
  ApiResponse<CatalogItemResponse> get(@PathVariable("id") UUID id);
}

@RestController
@RequiredArgsConstructor
public class CatalogController implements CatalogApi {
  private final CatalogUseCase catalogUseCase;
  private final CatalogMapper catalogMapper;
  @Override public ApiResponse<CatalogItemResponse> get(UUID id) {
    return ApiResponse.ok(catalogMapper.toResponse(catalogUseCase.get(id)));
  }
}
```

Spring Framework supports controller interface mapping annotations; method security must be enabled (`@EnableMethodSecurity`) and checked with integration tests for your Spring Security version. Controller implementations use `CatalogMapper` to map HTTP requests to commands and application models to response DTOs; no extra `*ApiFacade` layer is necessary for this thin orchestration. `CatalogUseCase` / `IdentityUseCase` interfaces specify business operations independently of the MVC adapter. `UserContext` is accessed in `IdentityApplicationService.profileCurrent()` to read the current user in the use case instead of passing a user ID supplied by an HTTP controller. The Catalog example does not attach a user context unless a business rule requires it.

### MapStruct null-skip and model enrichment

`CommonMapperConfig` sets `nullValuePropertyMappingStrategy=IGNORE`, `nullValueCheckStrategy=ALWAYS`, Spring component model; annotate **update mappings** with `@MappingTarget`. These settings do *not* magically turn a create mapping into a partial update. Example `CatalogMapper.applyPatch(CatalogPatchModel, @MappingTarget CatalogEntity)` and a unit test ensure an omitted SKU cannot overwrite a stored SKU with `null`. Validate business invariants in the domain/use case **before** applying a persistence patch; the method is illustrative and not an exposed PATCH API. `CatalogApplicationService.enrich(...)` calls `model.withDisplayLabel(...)` after MapStruct maps the base application model; no inline `java(...)` expressions in mappers.

### Cache helper, error logging and gateway rate limit

`CacheHelper` is a typed facade around the existing Spring `CacheManager`. Prefer
`get(CacheDefinition<T>, key)`, `getOrLoad(CacheDefinition<T>, key, loader)`,
`put(CacheDefinition<T>, key, value)`, `remove(CacheDefinition<?>, key)` and `clear(definition)`;
legacy name/class overloads remain available. `CacheDefinition` controls each cache's TTL,
Jackson serializer and `${spring.application.name}:<cacheName>:` Redis prefix.
Catalog's actual `get` use case calls this helper; it is **not** just sample code.
Configure Catalog TTL via `app.cache.catalog.item-ttl` / `CATALOG_ITEM_CACHE_TTL`
(default `5m`), without changing source. A single cache name has one configured TTL;
there is **no per-call TTL override**, because standard Spring `Cache.put()` does not
expose that API. If you later need different TTL for every entry, adopt Redis-specific
TTL functions rather than pretending the generic CacheManager controls per-write expiration.
`getOrLoad` is not a distributed cache-stampede lock. Every exception handled by
`GlobalExceptionHandler` logs every handled error at ERROR with a throwable and
trace context. Framework 4xx throwables are logged with a neutral message and
original stack locations, omitting rejected input/cause values. Application errors
and unexpected server exceptions retain their original throwable. Raw request bodies, credentials and access tokens are **not**
logged by the exception handler. In Gateway, `@Qualifier("principalKeyResolver")` resolves the custom `KeyResolver` and does not collide with the framework's built-in resolver. JWT uses a single RSA keypair **without a hardcoded `kid`**; production key rotation would need an explicit key-selection plan.

## 3. First run

Requirements: Docker Engine or Desktop + Compose v2, Bash, Python 3, OpenSSL and enough RAM for Java replicas. Local Maven build requires **JDK 25**. The Dockerfile builds/runs Temurin 25.

```bash
chmod +x scripts/*.sh
./scripts/deploy.sh init           # .env and local admin secret ONLY
./scripts/generate-jwt-keys.sh   # create local RSA keypair ONCE, independently of deploy
./scripts/deploy.sh up 3           # source rebuild + run 3 identical app containers
./scripts/smoke.sh                # login, catalog create/list, logout check
```

Dashboard: `http://127.0.0.1:3000` (username/password from `.env`). API: `http://127.0.0.1:8088`. Eureka and PostgreSQL are not public. `init` does NOT create RSA keys. Run `generate-jwt-keys.sh` once to create `secrets/jwt/private.pem` (0600) and `public.pem` (0644); it refuses to overwrite existing keys. `.env`, `.deploy/` and private PEM files are excluded from Git/Docker builds. **Do not put real production credentials into the project.**

### Pinned infrastructure versions (2026-10-14)

Compose pins full upstream release tags: Nginx 1.31.6-alpine, PostgreSQL
18.6-alpine, Redis 8.10.2-alpine, LGTM 0.36.0, Alloy v1.20.1, socket proxy
v0.5.0, PostgreSQL exporter v0.20.1, Redis exporter v1.93.0 and Python
3.15.0-alpine3.24. Application images use `:local` because they are built here.
Version availability was checked against Docker Hub and Quay registries.

PostgreSQL 18+ requires mounting `postgres_data` at `/var/lib/postgresql`.
An existing database from an older major version needs an explicit backup and
migration before using these tags; changing an image tag does not migrate data.
This local setup started without an existing `ddd-platform` database volume.

### Local OpenTelemetry agent

The pinned agent is stored in `libs/opentelemetry/opentelemetry-javaagent-2.21.0.jar`.
Docker copies it from this directory; builds do not download it again.
Verify with `(cd libs/opentelemetry && shasum -a 256 -c SHA256SUMS)`.
See `libs/opentelemetry/README.md` for provenance and upgrade steps.

## 4. Deploy / scale / restart

```bash
./scripts/deploy.sh up 3         # rebuild current source, start 3 containers
./scripts/deploy.sh scale 5      # resize only, no source build
./scripts/deploy.sh up           # rebuild after git revert / code change
./scripts/deploy.sh restart app  # restart existing containers, no new image
./scripts/deploy.sh restart gateway
./scripts/deploy.sh restart all
./scripts/deploy.sh down         # preserve Docker volumes
```

`scale N` does **not** need N Compose service definitions. Docker Compose creates N containers with distinct IP addresses. Eureka uses default instance IDs and discovers their advertised application network addresses; Gateway uses `lb://modular-app`. Docker restart policy `unless-stopped` retries crashed processes with Engine-managed backoff, but does not restart a merely `unhealthy` container. Optional `scripts/health-watchdog.py` plus systemd sample covers health-stuck cases; test thresholds before enabling. Compose does not guarantee seamless scale-down or rolling deploy.

Image SHA, actual replicas, container create/start timestamps, restarts, CPU/memory, lifecycle events and traces appear in **Grafana** (Ops Observer, Alloy, bundled LGTM). Shell remains deployment-only. These are Docker containers, **not Kubernetes Pods**.

## 5. Login/logout, JWT and permission-based authorization

Roles initially seeded via Liquibase: `ADMIN`, `SYSTEM_ADMIN` only. Only those two codes have privileged permission bypass. (Their role definitions cannot be created/modified through API; assignments and protected-account role changes require an authenticated ADMIN/SYSTEM_ADMIN.) Other roles are dynamic database rows, created through the role API. Fixed permissions (`CATALOG_READ`, `CATALOG_WRITE`, `USERS_READ`, `USERS_MANAGE`) are defined in `identity-module/domain/enums/Permission.java` and grouped in `AppConstants.Permissions`. Role assignments and role-permission relationships live in PostgreSQL. The user-management API allows **ADMIN/SYSTEM_ADMIN callers** to grant those existing roles; ordinary USERS_MANAGE callers cannot grant them or change protected accounts. Since these migrations are **not yet applied**, the initial `002-identity.sql` directly seeds uppercase permission codes; there is no permission-conversion 004 changeset. After deploying against a real database, treat executed Liquibase changesets as immutable and use a new changeset for subsequent changes.

```http
POST /api/public/auth/login             # anonymous, @PreAuthorize("permitAll()")
POST /api/private/auth/logout           # authenticated, denylist JWT jti until expiry
GET  /api/private/users/me              # authenticated, user profile
POST /api/private/users                 # USERS_MANAGE permission
PUT  /api/private/users/{id}/roles      # USERS_MANAGE permission
GET  /api/private/catalog/{id}          # CATALOG_READ permission
GET  /api/private/catalog?page=0&size=20  # CATALOG_READ permission
POST /api/private/catalog               # CATALOG_WRITE permission
```

Both Gateway and backend **permit `/api/public/**`, require authentication for `/api/private/**`, and deny other API paths**. Every application controller endpoint additionally declares `@PreAuthorize`, including the public login endpoint. The Actuator health endpoints are infrastructure checks handled through the security filter chain, not application controllers. URLs changed since v10; update any external clients.

`JwtClaimsSet` contains **only `iss`, `sub` (UUID), `iat`, `nbf`, `exp`, `jti`**. It **does not carry roles or permissions**. On each authenticated backend request, `JwtAuthenticationConverter` validates the user against the database (enabled status) and reads current roles/permissions; it builds Spring Security authorities as `ROLE_*` and permission codes. A `@RequestScope` `UserContext` bean provides `userId()`, `userIdOptional()`, `hasRole()`, `hasPermission()`, and `isPrivileged()`; `SecurityUtils.userIdOptional()` safely handles anonymous requests; a singleton `CustomPreAuthorizer` depends on the request-scoped proxy. API contract interface methods declare `@PreAuthorize("@customPreAuthorizer.hasAnyPermission({'CATALOG_READ'})")` directly; there are no `AppConstants.SecurityExpressions` indirections. `ADMIN` and `SYSTEM_ADMIN` bypass permission checks in `UserContext` without requiring any particular permission; users without those roles must possess the specific permission. A disabled or deleted user cannot authenticate merely because an unexpired token still exists.

**Trade-offs:** Current authorization queries PostgreSQL for each authenticated API call, which improves permission freshness but increases database load. For higher throughput add an explicitly invalidated short-lived authorization cache with a documented revocation policy; do **not** reinsert permissions into long-lived JWT claims. Gateway validates JWT signature/issuer/time but backend also checks Redis token revocation, so a logout may still pass the gateway and then be rejected by the backend. Redis or database outages must not silently authorize requests. BCrypt 12 hashes passwords; demo bootstrap seeds an admin only when absent and never resets an existing admin's roles/credentials. Concurrent startup is protected by the unique username constraint; for real operations bootstrap should be a controlled one-time provisioning step.

**Security keys:** Gateway receives only the public key; in this embedded-token-issuer example every `modular-app` replica still requires the private key to sign login tokens. See the next section for external key mounts and production trade-offs.

## 5.1. Where JWT keys belong: classpath vs external secrets

`JwtKeyResources` is a **pure utility in `platform-core`**, not a separate Maven module. Spring `Resource` accepts both `classpath:` and `file:` URIs, so application code does not need separate loaders. **A JAR is a ZIP archive: `src/main/resources` contents are packaged into the JAR and can be extracted by anyone with the artifact or image. Do not place a real private signing key in `resources`, Git or the JAR.** Putting the private key in a JAR increases the number of copies exposed through CI/build artifacts and image registries; it is not a security boundary. Public verification keys are safe to distribute in `resources` if a static trust anchor is appropriate, but rotation requires updating clients/images. This project mounts both keys as files to support replacement without rebuilding.

| Environment | Private key | Public key |
|---|---|---|
| Local development | `secrets/jwt/private.pem` generated once by `./scripts/generate-jwt-keys.sh` (Git-ignored) | `secrets/jwt/public.pem` |
| Docker Compose | `/run/secrets/jwt-private.pem`, read-only **only for modular-app** | `/run/secrets/jwt-public.pem`, read-only for Gateway and modular-app |
| Production | External secrets manager/KMS/HSM or short-lived mounted secret restricted to token issuer | Public JWK/JWKS or trusted public PEM (classpath/file) |

Current modular monolith also issues JWT during login, so **every app replica holds the signing key**. That is a trade-off of embedded identity: for stronger isolation, move token issuing into a separately secured issuer/IdP that alone has access to the private key. `gateway` validates JWT using only the public key. The local `deploy.sh init` records `APP_UID` / `APP_GID` and the `modular-app` container runs as that unprivileged host user, so it can actually read the `0600` private PEM bind mount on a compatible Docker file-sharing setup; check ownership explicitly on Linux/macOS. Production should use a dedicated secret mount with correctly provisioned file UID/GID/ACL rather than treating this dev mapping as hardening. The default `file:./secrets/jwt/...` Spring Resource paths are for local runs; Docker explicitly overrides them through `JWT_PUBLIC_KEY` / `JWT_PRIVATE_KEY`. Do not switch these defaults to bundled private PEM files. Key rotation needs a planned `kid`/JWKS strategy and token lifetime overlap; simply swapping PEM files may invalidate existing JWTs.

### Security expressions, grouped constants and configuration properties (v12)

`platform-core` contains an `AppConstants` class with `@UtilityClass` nested groups for stable permission codes, roles and Redis keys. SpEL expressions are written inline next to their controller endpoint declarations. Permission codes are uppercase and hard-coded in `Permission` enum; they must match the seed in the initial Liquibase SQL.

```java
@GetMapping("/{id}")
@PreAuthorize("@customPreAuthorizer.hasAnyPermission({'CATALOG_READ'})")
ApiResponse<CatalogItemResponse> get(@PathVariable UUID id);
```

For multiple permissions, use the same expression inline, for example
`@PreAuthorize("@customPreAuthorizer.hasAnyPermission({'CATALOG_READ', 'CATALOG_WRITE'})")`.
`ADMIN` and `SYSTEM_ADMIN` bypass permission checks centrally in `UserContext`.

Grouped Spring settings use `@ConfigurationProperties(prefix = "...")`: `app.security.jwt`, `app.security.bootstrap-admin`, `app.gateway.rate-limit`, `app.executors.application`. Register settings with `@EnableConfigurationProperties`. A single injected scalar such as application name in the cache configuration does not need an additional properties holder.

**RSA keys:** Execute `./scripts/generate-jwt-keys.sh` exactly once after cloning (and before `deploy.sh up`). It uses OpenSSL to create an RSA-3072 private/public keypair outside the JAR and refuses to overwrite existing files. Gateway mounts only the public key; the Modular Monolith currently mounts both because it issues tokens at login. Never commit, bake or copy a private key into runtime resources or images.

## 6. Core API contract, Spring Page<T>, i18n and exceptions

`ApiResponse<T>` has `success`, `data`, `error`, and `timestamp` — **no traceId** field. Errors use `ApiFailure(code, category, message, path, details)`; the error code comes from `ErrorCode` enum and the message is resolved using `messages.properties` / `messages_vi.properties` with the `Accept-Language` HTTP header (**default Vietnamese**; `en` selects English). Jakarta Bean Validation is registered through `LocalValidatorFactoryBean#setValidationMessageSource(messageSource)`, so **`@Valid` messages use the same `messages*.properties`** as the application errors. Messages are never built from raw exception strings for server-side failures.

### Validation messages and locale (v22)

The modular app imports `platform-core` internationalization configuration. It registers a shared `MessageSource` and a `LocalValidatorFactoryBean` wired to that source; **custom constraint keys like `{validation.required}` resolve from the same bundle as `ErrorCode`**. One source of truth is used: `messages.properties` (Vietnamese fallback), `messages_vi.properties` (Vietnamese), and `messages_en.properties` (English). The old `ValidationMessages*.properties` bundles have been removed to avoid maintaining translations in two places.

The `AcceptHeaderLocaleResolver` defaults to `vi` if there is no `Accept-Language` header; `Accept-Language: en` uses English. Error responses include validation field names inside `error.details`; message text is localized. Spring Security's early 401/403 response resolves locale using the same `LocaleResolver`, not `request.getLocale()` (which could otherwise use the host's English default). The `MessageResolver` also chooses Vietnamese when called outside a request context.

```bash
# On a machine with Maven and JDK 25
mvn -pl platform-core -am test
mvn clean verify
```



```json
{"success": false, "data": null,
 "error": {"code":"CATALOG_NOT_FOUND", "category":"RESOURCE",
           "message":"Không tìm thấy sản phẩm", "path":"/api/private/catalog/123", "details":{}},
 "timestamp":"2026-10-13T00:00:00Z"}
```

Set `Accept-Language: vi` for Vietnamese. `MessageResolver` is a common interface with overloads `resolve(ErrorCode, args...)`, `resolve(String key, args...)` and explicit `Locale` variants; `SpringMessageResolver` is the implementation backed by Spring `MessageSource`. Security filter errors use this same interface even though they execute outside MVC advice. `AppException` defines a polymorphic contract; subclasses (`NotFoundException`, `BadRequestException`, `UnauthorizedException`, `ForbiddenException`, `ConflictException`, `DependencyException`, `RateLimitException`, `DomainException`) reside in `platform-core/exception/type`. `GlobalExceptionHandler` maps categories to HTTP status; other handlers cover bean/method validation, JSON parsing, parameters, missing routes, media negotiation, payload size, authentication/authorization, SQL integrity, optimistic lock, query timeout, DB failure, async timeout, generic errors. Security filter errors have a separate JSON entry point because filters execute outside MVC advice.

**Pagination** intentionally does **not** copy a `List<T>` into a custom `PageResult`. `PageQuery` creates bounded Spring Data `Pageable`; `CatalogRepository.search(criteria, pageable)` runs an explicitly parameterized PostgreSQL native query (and a `countQuery` for total), returns `Page<CatalogEntity>`, which is mapped through domain/application to **`ApiResponse<Page<CatalogItemResponse>>`**. Spring Page metadata (`totalElements`, `totalPages`, `number`, `size`, etc.) is preserved. Page JSON serialization details can change between Spring Data versions; clients should use a contract test.

## 6.1. JPA persistence, SQL conventions and locking

Search endpoint: `GET /api/private/catalog?page=0&size=20&keyword=mouse`. `CatalogSearchRequest` is an HTTP-specific DTO (`size`, `page`, `keyword`); it converts to `CatalogSearchCriteria` (an application-layer record). The persistence adapter calls `CatalogRepository.search(criteria, pageable)`. In this example the query is native PostgreSQL `ILIKE`, expressed as Java SQL text blocks with SpEL `:#{#criteria.keyword()}` and a matching `countQuery`. **SpEL values are bound as SQL parameters; never concatenate client input into SQL syntax.**

```java
@Query(value = """
    SELECT c.id, c.sku, c.name
    FROM catalog_item c
    WHERE (:#{#criteria.keyword()} = ''
       OR c.sku ILIKE CONCAT('%', :#{#criteria.keyword()}, '%')
       OR c.name ILIKE CONCAT('%', :#{#criteria.keyword()}, '%'))
    ORDER BY c.created_at DESC, c.id DESC
    """,
    countQuery = """
        SELECT COUNT(*) FROM catalog_item c
        WHERE (:#{#criteria.keyword()} = ''
           OR c.sku ILIKE CONCAT('%', :#{#criteria.keyword()}, '%')
           OR c.name ILIKE CONCAT('%', :#{#criteria.keyword()}, '%'))
        """, nativeQuery = true)
Page<CatalogEntity> search(@Param("criteria") CatalogSearchCriteria criteria, Pageable pageable);
```

**Which persistence approach?** Use Spring Data JPA derived queries, `JpaRepository.save`, JPQL and projections by default. Native `@Query` is justified for PostgreSQL-specific search, nontrivial joins/CTEs, atomic updates, `ON CONFLICT`, `FOR UPDATE` and performance-critical SQL **after observing plans/metrics**. `UserAccountRepository.lockById` uses Spring Data JPA `@Lock(PESSIMISTIC_WRITE)` and JPQL when replacing role mappings. Ordinary user inserts use `saveAndFlush`, roles use a JPA `@ElementCollection` mapped to `auth_user_role`. Only the multi-table role-permission projection remains native SQL. Transactions protect role replacement; the DB uniqueness constraints remain essential. Don't blindly use pessimistic locks for every operation.

**Performance considerations:** `ILIKE '%keyword%'` can cause full scans for large catalogs; consider PostgreSQL `pg_trgm`, appropriate GIN indices and `EXPLAIN (ANALYZE, BUFFERS)` after you have real data. The current filter treats SQL `%`/`_` as patterns: if you want literal search, implement escaping and test it. Offset pagination can get expensive for deep pages; cursor/keyset pagination is deliberately not yet implemented. `JdbcTemplate` is **not** used by the sample repositories; add it only for proven high-volume/batch paths where JPA is inappropriate, and benchmark first. JPA `@Entity` is a persistence model, **not** the DDD aggregate.

**Boot 4 component discovery (v23):** The only executable business entry point is now `dev.demo.ModularApplication` in the **shared root package**, annotated with `@SpringBootApplication` and `@ConfigurationPropertiesScan`. The default Spring Boot component scan, JPA entity discovery and Spring Data repository auto-configuration therefore find `dev.demo.core`, `dev.demo.catalog`, `dev.demo.identity`, and `dev.demo.modular` on the runtime classpath **without** a long `scanBasePackages`, `@EntityScan`, `@EnableJpaRepositories` or `@Import` list. Do not move the entry point back under `dev.demo.modular` without revisiting JPA repository/entity scanning. `@ConfigurationPropertiesScan` discovers all configuration properties classes in that root package; the pre-existing module-local `@EnableConfigurationProperties` declarations remain harmless and allow isolated configuration wiring. Hibernate uses `ddl-auto: validate` while Liquibase owns schema creation/migrations.

### Why UserContext uses a request-scoped proxy

`UserContextConfiguration.userContext()` uses `@RequestScope` (whose **default** `proxyMode` is `ScopedProxyMode.TARGET_CLASS`). This is deliberate: `CustomPreAuthorizer` and application services are singleton beans that need to access a **fresh** `UserContext` for every HTTP request. With `ScopedProxyMode.NO`, injecting the request-scoped bean directly into a singleton would typically fail while the application context is starting (`ScopeNotActiveException`) or resolve outside the intended request. `ScopedProxyMode.NO` works only when lookup is deferred, e.g. by injecting `ObjectProvider<UserContext>` and calling `getObject()` **inside an active request**. Keep `@RequestScope` here; there is no need to explicitly write `proxyMode = TARGET_CLASS` because it is already the annotation default.

## 7. Cache, Jackson, OTel logging

Spring CacheManager uses Jackson 3 and `CacheDefinition<T>(name, type, ttl)` beans. Every Redis key is prefixed with `${spring.application.name}:` automatically, e.g. `modular-app:catalog:item:<UUID>`. JSON values are deserialized into a **known** DTO/model type per cache; no unsafe default typing. Catalog caches `CatalogItemModel` through the typed `CacheHelper`; TTL is configurable with `CATALOG_ITEM_CACHE_TTL` (default `5m`). Use `@CacheEvict` or an explicit invalidation strategy for update/delete use cases; avoid caching security principals and auth decisions without a versioning plan.

Global JSON output (v24): `CoreJsonConfiguration` declares one explicit, Spring-managed **Jackson 3 `JsonMapper` bean named `objectMapper`**, injectable as `tools.jackson.databind.ObjectMapper` by REST, Redis and future Kafka serializers. It sets `JsonInclude.Include.NON_EMPTY` directly via `JsonMapper.builder()`, dropping `null`, `""`, empty lists and maps on **serialization**. It retains `false` and `0`. This policy is **not HTTP-only**: an empty collection stored in Redis/Kafka is omitted and may deserialize as `null`, so do not assume an exact empty/null round-trip or use this shared policy for protocols requiring explicit nulls without a separately configured serializer. Additional `SimpleModule` Spring beans are registered by the shared mapper. Defining an explicit mapper also overrides Spring Boot’s default mapper factory; if you later add Jackson modules or Boot-specific Jackson configuration, verify their registration on this mapper.

Global JSON String trimming: `CoreJsonConfiguration` contributes a Jackson 3 `SimpleModule` Bean (registered with the shared application mapper), so no `@JsonDeserialize` is needed on DTO fields. `app.json.trim.enabled` defaults to `true`; `JsonTrimProperties.excludedFields` has defaults for password, tokens, keys, signatures and opaque payloads. Override excluded names under `app.json.trim.excluded-fields` if your API has other signed or whitespace-significant fields. **This config applies to all usages of the shared Jackson mapper (including Redis serialization), not only HTTP `@RequestBody`**, so review opt-out names carefully. Spring MVC `Formatter`/`ConversionService` serves query/form/path binding, **not** JSON request-body deserialization. Do not trim signed/free-text data where whitespace matters. Logback JSON logs via SLF4J/Lombok `@Slf4j` include OTel MDC `trace_id`/`span_id`. The backend HTTP audit logs safe method/path, sanitized URL query parameters, HTTP status, duration and bounded JSON request/response bodies **only** on the allowlisted Catalog API routes. Login/logout, Actuator, credentials and authentication-token fields are excluded or redacted; an arbitrary free-text field can still contain personal/sensitive data, so disable HTTP body logging for production or extend the allowlist carefully. OTel Java Agent propagates W3C trace context, exports spans and JVM metrics to **`grafana/otel-lgtm`**, and injects MDC trace/span IDs. No independent `validRequestId()` UUID is generated or returned; use OTel trace correlation inside Grafana. Alloy collects Docker stdout logs, while the observer adds Docker image/container metrics plus PostgreSQL/Redis exporter and private Spring Actuator metrics. The OTel Java Agent cannot read Docker lifecycle itself.

### Structured HTTP logging → Grafana Loki

All Java applications now ship a `logback-spring.xml` with the version-pinned Logstash JSON encoder. Docker stdout/stderr is read by Alloy, which forwards to **Loki inside `grafana/otel-lgtm`**. No separate Loki container or direct Grafana push from Java is necessary. OTel Java Agent continues managing trace propagation; its `trace_id` and `span_id` MDC entries are included as searchable JSON fields. The backend servlet filter also emits `http_method`, `http_path`, `http_query`, `http_status`, `duration_ms`, `request_body`, `response_body` and `event=http.audit` using SLF4J's structured key/value logging.

In Grafana **Explore → Loki**, select `DDD Loki` and query:

```logql
{service="modular-app"} | json | event="http.audit"
```

Open the **Observability — Services & Logs** dashboard for API requests and application errors. The Loki data source also supports a `trace_id` link to Tempo.

```dotenv
HTTP_AUDIT_ENABLED=true
HTTP_AUDIT_LOG_BODIES=true
HTTP_AUDIT_MAX_BODY_BYTES=8192
HTTP_AUDIT_MAX_QUERY_CHARS=2048
LOG_LEVEL=INFO
```

Only `/api/private/catalog/**` is allowlisted for body previews by default, and the JSON sanitizer recursively redacts sensitive field names. Excluded `/api/public/auth/**` and `/api/private/auth/**` do not log request/response bodies, and files/multipart/binary/large payloads are omitted. To change routes, edit `app.logging.http.body-paths` in the Modular application YAML; do not log customer-sensitive fields or high-volume request bodies in production. Use `HTTP_AUDIT_LOG_BODIES=false` to keep request/response metadata without any body preview. Headers, passwords, bearer tokens and cookies are deliberately not recorded. Response output is streamed directly with a bounded preview buffer, rather than caching the full response in memory. For async/streaming APIs, disable body auditing or exclude those routes.

## 7.1. Monitoring metrics, alerts, and the `localhost` distinction (v13)

**Why some Grafana data sources use `localhost`:** Grafana, Prometheus, Tempo and Loki run **inside the same `grafana/otel-lgtm` container**. Grafana's server-side `http://localhost:9090` (Prometheus), `http://localhost:3100` (Loki) and `http://localhost:3200` (Tempo) are therefore valid. They are **not URLs used by the browser or other containers**. Java agent → `http://lgtm:4318`, Alloy → `http://lgtm:3100`, and the metrics bridge → `http://lgtm:4318/v1/metrics` use Docker service DNS. The Grafana **web UI** is deliberately host-bound to `127.0.0.1:3000` in local mode.

The Spring Boot applications include `micrometer-registry-prometheus` and expose **`/actuator/prometheus` only on Docker's private application network**; security filter chains permit that internal scrape path, which is never routed by Nginx/Gateway. The observer auto-discovers the internal IP of each Java container through Docker's inspect API, so it can scrape *all replicas*, even after scale up/down. It also collects Docker CPU/memory/container health and forwards selected exporter metrics as OTLP to the bundled LGTM. **This is a curated Python bridge, not a full Prometheus receiver**; it intentionally excludes unbounded metric families and may need extensions for advanced histograms or custom metrics. JVM traces and standard runtime OTel metrics still flow directly through the OpenTelemetry Java Agent.

| Source | Example metrics | Dashboard |
|---|---|---|
| Java / Micrometer | `jvm_memory_used_bytes`, `jvm_gc_pause_seconds_count`, `http_server_requests_seconds_count` | Runtime — JVM, GC, Hikari & HTTP |
| HikariCP | `hikaricp_connections_active`, `hikaricp_connections_max`, `hikaricp_connections_pending` | Runtime — JVM, GC, Hikari & HTTP |
| PostgreSQL exporter | `pg_up`, `pg_stat_database_numbackends`, `pg_stat_database_deadlocks`, `pg_stat_database_xact_commit` | Infrastructure — PostgreSQL & Redis |
| Redis exporter | `redis_up`, `redis_connected_clients`, `redis_memory_used_bytes`, `redis_evicted_keys_total` | Infrastructure — PostgreSQL & Redis |
| Docker API observer | `ddd_service_replicas_running`, `ddd_service_replicas_healthy`, `ddd_container_cpu_percent`, `ddd_container_memory_usage_bytes`, restarts, image SHA | Operations — Replicas, Builds, Images & Logs |
| Prometheus scrape checks | `ddd_metrics_scrape_success{job="postgres|redis|spring-actuator"}` (one series per job/instance) | Runtime / Infrastructure |

The new **Grafana Alerting** rules are provisioned from `infra/lgtm/provisioning/alerting/rules.yaml`: app/gateway/Nginx/Eureka down, missing replicas, Postgres/Redis `up=0`, collector scrape failure, heap usage >85% (5 min), Hikari busy >85% (3 min), Hikari connection waiters, PostgreSQL connections >40 (5 min), deadlocks, Redis memory >85%, and HTTP 5xx rate >0.1 req/s, and sustained CPU >90% of one core. Thresholds are **tutorial defaults**, not automatically tuned production SLOs; adjust them according to your DB `max_connections`, `-Xmx`, workload, CPU quotas, and desired alert severity. A 90% CPU threshold refers to one CPU core; processes may legitimately exceed 100% on multi-core machines. Alert eval interval is 1 minute; alerts do not fire immediately at process exit. `noDataState` is used on critical availability checks to detect missing time series, but can lead to noise during initial startup and telemetry outages.

**Telegram delivery (no hardcoded bot credentials):** Grafana alert rules are provisioned by default. Set the following two variables in the ignored `.env`:

```dotenv
TELEGRAM_BOT_TOKEN=123456789:YOUR_BOT_TOKEN
TELEGRAM_CHAT_ID=-100123456789
```

Run `./scripts/deploy.sh up 3`. The deploy script **automatically enables** the templated `ddd-telegram` contact point and notification policy when both values exist; it **disables** that provisioning file when both are blank, without embedding credentials in Git. To update the bot configuration, edit `.env` and run `./scripts/deploy.sh up` again (or recreate `lgtm`). Test via Grafana → Alerting → Contact points → ddd-telegram → Test. If only one of the two variables is configured, deploy fails fast with a useful error. Configure a bot via BotFather and add it to the destination group/chat before testing. Secrets are passed through Docker environment variables for this local starter; use Docker secrets/external secret management for production.

**Security:** Exporters have **loopback host ports for local testing**, configured in `.env`. The observer has read access to Docker inspect/stats through a Docker-socket proxy. Although it exports only whitelisted metrics/metadata, Docker inspect access can read container environment variables; isolate the collector and avoid plain environment secrets in production. Actuator metrics are private-network only; add stronger network policies/authentication when using shared or untrusted container networks. For production, use a standard OTel Collector/Alloy Prometheus receiver and proper scrape discovery/storage rather than relying on this minimal bridge.

## 8. Thread pools, Hikari and Liquibase

`server.tomcat.threads.max=100` / `min-spare=10` (plus connection and accept queue limits) governs HTTP request workers; `applicationTaskExecutor` is a separate bounded `ThreadPoolTaskExecutor` for explicitly annotated `@Async("applicationTaskExecutor")` work. `ContextPropagatingTaskDecorator` copies active **OpenTelemetry Context**, Spring **SecurityContext**, and SLF4J **MDC** into worker tasks, and restores previous worker state in `finally`, preventing cross-request leaks. Does not automatically propagate Spring transactions; never assume transaction scope crosses a thread boundary.

HikariCP per app replica: `maximum-pool-size=8`, `minimum-idle=2`, timeout/idle/max lifetime and keepalive configured under `spring.datasource.hikari`. Approximate possible JDBC connections = `replicas × maximum-pool-size` plus other clients; check PostgreSQL `max_connections` before scaling. Each app replica starts Liquibase using `db/changelog/master.xml` (XML includes and PostgreSQL SQL files). Liquibase serializes migrations with its database changelog lock; schema migrations are **not** automatically rolled back by reverting Java source. For production, run migrations as a controlled release step and verify forward/backward compatibility.

## 8.1. Liquibase: `master.xml → XML includes → SQL`

```text
modular-monolith/src/main/resources/db/changelog/
├── master.xml                  # only <include> entries
└── changes/
    ├── 001-catalog.xml         # one changeSet with <sqlFile>
    ├── 002-identity.xml        # one changeSet with <sqlFile>
    ├── 003-system-admin.xml    # SYSTEM_ADMIN role seed (not a permission migration)
    └── sql/
        ├── 001-catalog.sql     # PostgreSQL catalog DDL
        ├── 002-identity.sql    # identity DDL and seed
        └── 003-system-admin.sql  # adds SYSTEM_ADMIN
```

**Rules:** `master.xml` only includes the changeSet XML files. Each child XML declares an `id`/`author` and one SQL file; all SQL details live in `.sql`. Use `CREATE ... IF NOT EXISTS` and `INSERT ... ON CONFLICT` only when semantics permit. These clauses **do not** validate column definitions, missing constraints, or schema drift; Hibernate `ddl-auto: validate` is an additional check, not an excuse to ignore Liquibase errors. Put future schema changes into new, uniquely numbered SQL/XML changesets; never overwrite SQL that was already applied.

**Migrating an existing v9 database:** v9 used `db.changelog-master.yaml` and Liquibase *formatted SQL* changeSet identities (`id`,`author`,`filename`). v10 changes both changelog paths and implementation. On a **fresh local development database**, use a fresh volume and run migrations normally. On a database with important data, **back up first; inspect `DATABASECHANGELOG` and create an explicit, reviewed history migration/baseline plan** before deploying this version. Do not blindly use `clearCheckSums`, erase `DATABASECHANGELOG`, or `docker compose down -v`: those approaches can hide drift or destroy data. A Java `git revert` does not undo DB migrations.

## 9. Formatting, quality and build checks

The supplied IDEA XML is retained for IDE formatting under `config/code-style/GoogleStyleHighlyCustomized.xml` and `.idea/codeStyles/Project.xml`. The CLI is now **Spotless + Google Java Format**, with a pinned plugin/formatter version. This works while IntelliJ is running and covers all Maven modules including tests. Google Java Format does **not** reproduce every IntelliJ XML setting (notably wrapping). Prefer the Spotless result for CI consistency.

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"  # macOS
./scripts/format.sh format   # internally: mvn spotless:apply
./scripts/format.sh check    # internally: mvn spotless:check
mvn spotless:apply           # direct Maven invocation
mvn spotless:check           # fail if Java sources differ
mvn clean verify             # compilation + unit tests
mvn -Pquality verify         # JaCoCo coverage
```

Java input examples (JSON fields only):

```json
{"name":"  Keyboard  ","password":"  unchanged  "}
```

`name` becomes `Keyboard`, while `password` remains exactly as submitted. Global string trimming is a normalization policy, not input security validation; never globally modify password/token contents.

**SonarQube:** a successful compilation is not a SonarQube scan; connect an actual SonarQube server/CI scanner and review findings separately. Avoid treating existing static checks as proof of 0 issues.

## 10. Deployment security and known limitations

- Keep Nginx/Grafana on `127.0.0.1` for local work; use HTTPS and protected administration for public deployments. Private app/database networks do not replace firewall policy or egress controls.
- `.env` secrets and mounted private key remain sensitive. With embedded login, app containers can access the signer key; Docker admins can inspect environment. Use dedicated secret management and separate signing infrastructure for real use.
- Bootstrap admin env password is a **demo-only convenience**. Change it and disable the bootstrap mechanism when integrating with a real identity provider.
- Eureka `UP` and Docker healthchecks are not instantaneous failover guarantees. Compose + one Gateway/Eureka/Postgres is not HA; Gateway retrying mutating requests without idempotency can duplicate side effects.
- Compose image release tags were checked against registries on 2026-10-14 and the local Docker stack was started. Tags can be mutable; use image digests for reproducible releases.
- Local Java tests/build and Docker smoke checks have passed. No SonarQube scan or production availability test has been performed.

## 10.1. Docker images and compatibility

Images now use **explicit version tags** in `compose.yaml`, e.g. `nginx:1.31.6-alpine`, `postgres:18.6-alpine`, `redis:8.10.2-alpine`, `grafana/otel-lgtm:0.36.0`, `grafana/alloy:v1.20.1`, `tecnativa/docker-socket-proxy:v0.5.0`, `quay.io/prometheuscommunity/postgres-exporter:v0.20.1`, `oliver006/redis_exporter:v1.93.0` and `python:3.15.0-alpine3.24`. Java Docker build uses Maven `3.9.11` with Temurin 25; runtime uses Temurin 25. The unused `LGTM_IMAGE` variable and its `latest` comment were removed from `.env.example` and Compose.

Release tags were verified on 2026-10-14. Recheck available releases when upgrading and lock image digests for reproducible deployments.

## 10.2. Intentionally not implemented yet

- **No general-purpose JDBC repository:** current catalog and identity modules use JPA; a JDBC implementation can be introduced behind the same application repository port after profiling.
- **No global dynamic search framework:** Catalog shows one native SQL + SpEL filter. Add per-feature search criteria and carefully designed queries; avoid dynamic SQL string interpolation or universal reflection-based filters.
- **No pg_trgm/index optimization or keyset pagination:** add migrations and integration/benchmark tests when data volumes justify them.
- **No cross-module distributed transactions or outbox:** a modular monolith uses one DB transaction where appropriate. If you split it into separate services later, reconsider consistency boundaries.
- **No virtual-thread-by-default pool replacement:** existing bounded executor propagates OTel/SecurityContext/MDC, and HikariCP protects connection concurrency. Benchmark any virtual-thread approach and implement context propagation before adopting.
- **No dedicated auth issuer, HA Eureka/Gateway/DB or zero-downtime rolling deployment:** the demo runs a single Gateway and Eureka on one Compose host; not suitable for high-availability production as-is.
- **No external alert HA:** Grafana Alerting is provisioned locally, but Telegram is opt-in and cannot detect full host/Grafana outages without an external monitor.
- **No image release/rollback commands:** Docker builds source from the current Git checkout. Grafana monitors deployed container identity, restarts and image SHA.
- PostgreSQL integration tests for native SpEL queries and locking (e.g. Testcontainers) should supplement the local unit and Docker smoke checks.

## 11. Troubleshooting

| Problem | Check |
|---|---|
| `cannot find symbol` from Lombok/MapStruct | Java 25 active, annotation processing enabled, root `annotationProcessorPaths`, `mvn clean verify` |
| No Eureka instances | Eureka dashboard/registry, Docker app network, advertised instance IP, discovery caching |
| API always 401 | Bootstrap user exists, JWT issuer, mounted PEMs, Redis reachable, token expired/revoked, role/permission lookup in the database |
| API 403 | Check current DB role/permissions, `UserContext` and `@PreAuthorize`; JWT carries no roles/permissions |
| Postgres migration fails | Liquibase `DATABASECHANGELOGLOCK` / SQL changelog state / duplicate existing Flyway tables |
| Redis cache keys unexpected | Prefix is app-name + cache-name + method key; inspect `CacheDefinition` |
| Hikari connection exhaustion | Multiply maximum pool by replica count, profile expensive queries, consider PgBouncer |
| Trace missing in Grafana | Java agent `JAVA_TOOL_OPTIONS`, OTLP endpoint `http://lgtm:4318`, agent version, sample rate |
| Spotless reports `DeferredDiagnosticHandler.getDiagnostics()` / `NoSuchMethodError` | Old `google-java-format 1.26.0` is incompatible with JDK 25 internals. v27 updates Spotless to 2.46.0 and google-java-format to 1.32.0. Check `mvn -version`, then run `mvn -U spotless:apply`. As a temporary workaround run Spotless with JDK 21, but keep JDK 25 for compiling. |

**Previous Flyway users:** This release switches to Liquibase. Existing databases created by Flyway are **not automatically migrated**; starting Liquibase with the new baseline against a nonempty Flyway database can fail on existing tables. Test on a fresh Docker volume or create a reviewed one-time migration/mark-ran plan. Do not delete a production database to resolve this.

## Maven POM formatting and clean (v9 fix)

The previous archive accidentally serialized `modular-monolith/pom.xml` as
`<ns0:project>` instead of the conventional default Maven namespace. All eight
`pom.xml` files have been normalized to the standard readable Maven structure,
without changing module/dependency/plugin declarations. This is **XML**, as
required by Maven; the `ns0:` prefix was an artifact of the XML writer, **not**
a required Maven convention. Avoid writing POMs with an XML writer that silently
introduces namespace prefixes; use a serializer configured with the default POM
namespace if editing programmatically.

From the **root folder**, on JDK 25:

```bash
mvn -version             # confirm Maven is using Java 25
mvn clean                # cleans all Maven reactor modules
mvn clean verify         # compile and test (may expose unrelated code errors)
python3 -m unittest discover -s tests -p 'test_pom_structure.py'
```

`mvn clean` normally does **not** compile Java. The prior `ns0:` change has been
removed, but a Maven `clean` failure may have another cause; inspect the **first
`[ERROR]` line** rather than assuming the namespace explains every failure.
Java source formatting is handled by `./scripts/format.sh format`, which calls Maven Spotless and does not launch IntelliJ.

**JDK 25 compatibility:** The previous formatter version 1.26.0 triggered a `NoSuchMethodError` with `com.sun.tools.javac.util.Log$DeferredDiagnosticHandler` on JDK 25. This version declares Spotless Maven 2.46.0 and google-java-format 1.32.0 in the root POM. The exact Maven resolution/formatting under JDK 25 is **not verified** in the packaging environment. A known diagnostic/workaround is to run the formatter using JDK 21 while compiling with JDK 25; avoid mixing the JDK used by `mvn clean verify`.

## 11. Known limitations and next steps

- The source has not been compiled by Maven with Java 25 or integration-tested in Docker in the generation environment. Static tests cannot catch Spring bean wiring, Jackson/MapStruct API mismatches, or runtime migration issues.
- This is a fresh-database baseline: uppercase permission seeds are in initial `002-identity.sql`; there is no conversion changeset. If a database was previously initialized using the old seeds, do not rerun or edit its executed Liquibase changesets: create a reviewed migration for that database instead.
- Per-request PostgreSQL authorization lookups cost multiple queries; add DB-backed tests and measured/cache-aware optimization before high load.
- User-role replacement is protected by a pessimistic lock for concurrency. Basic CRUD has no handwritten SQL. Only complex Catalog search and role-permission projection are native PostgreSQL queries.
- Permissions are checked at controller entry; internal asynchronous jobs also need appropriate service-level authorization or explicit trusted-job policy.
- Strong production isolation would separate identity/token issuing from resource servers. JWT private keys must not be bundled into JAR images. Gateway and Eureka single instances remain SPOFs.
- Improve with Testcontainers (PostgreSQL, Redis), MockMvc/WebTestClient security tests, JaCoCo/SonarQube scans, and a true staged rolling deployment; Docker Compose scale is not Kubernetes Pod management.


## Compile fix (v15)

- `modular-monolith` JWT encoder: wrap the `RSAKey` inside `new JWKSet(jwk)` before passing it to `ImmutableJWKSet`. The previous direct `RSAKey` argument did not match the Nimbus API.
- `platform-core` `AppExceptionTest`: add the missing `dev.demo.core.exception.type.NotFoundException` import.

After upgrading, execute `mvn clean verify` with JDK 25 from the project root. This update was statically checked; full Maven compilation was not available in the packaging environment.

## 12. v19 — Shared generic mapper, exception logging and typed TTL cache helper

**Generic MapStruct:** `platform-core/mapper/EntityMapper<D,E>` defines the common
`toDomain`, `toEntity`, `toDomains`, `toEntities` and `toDomainPage` contract.
`CatalogMapper` and `IdentityMapper` extend it with their own domain/entity types;
`CommonMapperConfig` still supplies the Spring component model and null-skip
configuration for `@MappingTarget` updates. Do not use null-skip creation mappings
as substitutes for partial update semantics. Generated MapStruct code, including
inherited generic collection methods, must pass `mvn clean verify` on JDK 25.

**Error logging:** `GlobalExceptionHandler` logs every handled MVC exception exactly
once through a shared `log.error` call with the original throwable. The existing
SLF4J/Logback JSON → Alloy → Loki/Grafana pipeline receives these events, including
OTel trace/span MDC fields when the current request has an active span. Spring Security
filter failures outside MVC advice are still handled by the security entry point;
those are not emitted by MVC's `GlobalExceptionHandler` and should be audited separately.
Do not place plaintext credentials in exception messages.

**Cache TTL:** Register one `CacheDefinition<Model>` bean per cache name, with its own
`Duration`. The catalog bean reads `app.cache.catalog.item-ttl` from Spring configuration.
RedisCacheManager applies the duration, fixed Jackson type and application-name prefix.
The Catalog application use case now calls `cacheHelper.getOrLoad(catalogItemCache, id, ...)`
and is not annotated with `@Cacheable`, avoiding two independent caching paths.
For updates/deletes, invalidate `catalogItemCache` using `cacheHelper.remove(...)`.

```yaml
app:
  cache:
    catalog:
      item-ttl: ${CATALOG_ITEM_CACHE_TTL:5m}
```

To format all Java modules use `./scripts/format.sh format`.
To build/execute unit tests use `mvn clean verify` with Java 25. Docker smoke tests
and full Java compilation were not available in the generation environment.


### v21: Dynamic roles, commands, optional principal and field validation

- **Permissions remain code-defined** (`Permission` enum and `AppConstants.Permissions`) and seeded in `002-identity.sql`. Adding a new permission requires a code change and corresponding DB migration after first deployment.
- **Other roles are created dynamically** in PostgreSQL; only `ADMIN`/`SYSTEM_ADMIN` are special. There is no `RoleCode` enum for ordinary roles. `RolePort` → `RolePersistenceAdapter` → `RoleRepository` and `RoleUseCase` handle role creation/listing/permission updates.
- **Privilege checks:** granting privileged roles and modifying protected accounts requires an authenticated ADMIN/SYSTEM_ADMIN caller. To prevent privilege escalation, non-privileged role creation/modification and role assignment require that the acting user already has *every* permission contained in the target role (ADMIN/SYSTEM_ADMIN bypass that permission-subset condition; special-role definition edits remain forbidden).
- **New endpoints** (require `USERS_MANAGE`, and `@PreAuthorize` lives on the controller interface):

```http
GET  /api/private/roles
POST /api/private/roles
PUT  /api/private/roles/{code}/permissions
```

Role creation example (authenticate first):

```json
{
  "code": "ACCOUNT_SUPPORT",
  "permissions": ["USERS_READ"]
}
```

Then `POST /api/private/users` / `PUT /api/private/users/{id}/roles` accept `"roles": ["ACCOUNT_SUPPORT"]`. Invalid/unknown role codes are rejected. Fresh DB only seeds special roles: create regular roles before assigning them. Changing initial Liquibase SQL is safe **only if no existing database has executed the changeset**; do not edit migrations already applied elsewhere.

- Application command records are organized under `application/command/` (separate from `application/usecase/` interfaces), for example `CreateRoleCommand`, `CreateUserCommand`, `LoginCommand`.
- `EntityMapper<D, E, M>` names **Domain**, **Persistence Entity**, **Application Model**. DTO conversion is defined in the owning module mapper because one module can have multiple request/response types; Page mapping preserves metadata. For Identity, converting account into a full UserModel also requires role/permission projections, so the generic mapper does not falsely fabricate that data.
- `UserContext.userIdOptional()` and `SecurityUtils.userIdOptional()` return `Optional<UUID>` for anonymous/invalid principals. `userId()`/`requireUserId()` still fail with an authentication exception for protected use cases.
- Field validation errors include keys in `error.details`, e.g. `username`, `roles[0]`, or `arg0`. The handler covers DTO field validation, Jakarta constraints, method validation, malformed scalar parameters and missing headers. Validation messages should never echo sensitive rejected values.
- Public interfaces contain method-level JavaDoc, with blank lines between methods. Maven/Spotless remains the formatting tool, not a second IntelliJ process.

**Not verified in this environment:** Maven compilation with JDK 25, Spring context startup, and Docker/Liquibase integration. Run `mvn spotless:apply && mvn clean verify` and the Docker smoke test before deployment.


## Spring Framework 7 - HandlerMethodValidationException compatibility (v25)

`GlobalExceptionHandler.methodValidation()` calls `ex.getParameterValidationResults()` rather than the removed `getAllValidationResults()` API. Keeps parameter validation entries in `error.details` and logs exceptions through the shared error handler. Run `mvn clean verify` with JDK 25 to validate real Java compilation.


## v26 — Spring 7 HTTP status and shared Jackson 3 mapper

- Replaced deprecated `HttpStatus.UNPROCESSABLE_ENTITY` (422) with `HttpStatus.UNPROCESSABLE_CONTENT` (422), and `HttpStatus.PAYLOAD_TOO_LARGE` (413) with `HttpStatus.CONTENT_TOO_LARGE` (413). These are **Spring HTTP status names**, not application `ErrorCode` values: existing `ErrorCode.PAYLOAD_TOO_LARGE` and `error.PAYLOAD_TOO_LARGE` i18n keys remain unchanged for compatibility.
- Replaced unavailable `JsonMapper.Builder.defaultPropertyInclusion(...)` with Jackson 3 `changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_EMPTY))` on the **shared Spring `JsonMapper` bean**.
- Added three explicit Jackson deserialization choices: `FAIL_ON_UNKNOWN_PROPERTIES` disabled (forward-compatible input), `FAIL_ON_TRAILING_TOKENS` enabled (reject trailing payload), `FAIL_ON_NULL_FOR_PRIMITIVES` enabled (reject `null` for primitive `int`/`boolean`/etc.). These are **input** policies, not output formatting. Existing output `NON_EMPTY` and global string-trimming module are preserved.
- `NON_EMPTY` also applies to Redis and to future Kafka serializers injected with this same mapper. Empty collections can disappear instead of round-tripping as empty; use separate serialization policy when an event schema needs those distinctions. `FAIL_ON_UNKNOWN_PROPERTIES=false` is convenient for backward-compatible payloads, but do not use it for messages requiring strict schema enforcement.
- Java unit tests added for unknown fields, null primitives and trailing tokens. Run `mvn clean verify` on JDK 25. This environment does **not** have Maven/JDK 25 and cannot confirm Java compilation or startup; regression checks only verify source consistency.
