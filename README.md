# B2B Order Management & Micro-payment API

[![CI](https://github.com/seppam/b2b-order-management/actions/workflows/ci.yml/badge.svg)](https://github.com/seppam/b2b-order-management/actions/workflows/ci.yml)

A Spring Boot REST service that automates business-to-business purchase workflows: client registration, JWT authentication, a product catalog, **wallet-based micro-payments** and transactional order processing.

Built to demonstrate the things that matter in a transactional backend: atomic money movement (`@Transactional` with `BigDecimal`), stateless JWT security with role- and ownership-based authorization, caching, rate limiting and a consistent error model.

## Features

- **JWT authentication** (JJWT, HS256) with BCrypt-hashed passwords; roles `ROLE_CLIENT` / `ROLE_ADMIN`
- **Wallet payments**: an order atomically validates stock and balance, debits the wallet, decrements stock and records a `PAID` order; any failure rolls everything back
- **Authorization**: only admins create products and top up wallets; clients can only place/read orders and read the wallet for *their own* user id
- **Caching**: Caffeine cache for SKU lookups (10 min TTL, 1000 entries)
- **Rate limiting**: Bucket4j, 100 requests/hour per client IP on `/api/**` (HTTP 429)
- **Structured errors**: `GlobalExceptionHandler` maps domain exceptions to JSON (400/401/403/404/500, no stack traces leaked)
- **OpenAPI / Swagger UI** at `/swagger-ui/index.html`

## Architecture

```
Controller (REST, @PreAuthorize)  ->  Service (@Transactional business rules)  ->  Repository (Spring Data JPA)  ->  MySQL / H2
        ^
JwtAuthenticationFilter (Bearer token -> SecurityContext)      Cross-cutting: Caffeine cache, Bucket4j interceptor, exception handler
```

Packages under `com.enterprise.b2b.ordermanagement`: `controller`, `service` (+`impl`), `repository`, `entity`, `dto`, `security`, `config`, `exception`.

## Tech stack

Java 17 · Spring Boot 2.7 (Web, Data JPA, Security, Cache) · MySQL 8 (H2 for local/tests) · JJWT · Caffeine · Bucket4j · springdoc-openapi · JUnit 5, Mockito, MockMvc · Maven · GitHub Actions

## Run it

Requires JDK 17+ and Maven. A JWT signing secret is **required** (no default is shipped):

```bash
export JWT_SECRET=$(openssl rand -base64 48)
```

**Zero-setup (in-memory H2):**

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

**With MySQL:** create the database `b2b_order_mgmt`, set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` (see `.env.example`), then `mvn spring-boot:run`.

The API is served on `http://localhost:8080`. With the app running, `./scripts/demo.sh` executes a full scripted scenario (register, login, create product, top up, order, history).

## Test it

```bash
mvn test
```

38 tests, all passing: controller slice tests (`@WebMvcTest`), service unit tests (Mockito, including the "no partial wallet mutation" invariant), and an end-to-end integration test (`OrderFlowIntegrationTest`) that drives the real security chain, JPA and H2: auth, role checks, top-up, purchase, stock/balance deduction, rollback on failure and cross-user access denial.

## API overview

All endpoints except `/api/v1/auth/**` need `Authorization: Bearer <token>`.

| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/api/v1/auth/register` | public | Register (`username`, `password`, `email`, `role`) |
| POST | `/api/v1/auth/login` | public | Returns `accessToken`, `tokenType`, `username`, `userId` |
| GET | `/api/v1/products` | authenticated | List products |
| GET | `/api/v1/products/sku/{sku}` | authenticated | Product by SKU (cached) |
| POST | `/api/v1/products` | admin | Create product |
| POST | `/api/v1/users/{id}/wallet/topup` | admin | Add credit `{ "amount": 1000 }` |
| GET | `/api/v1/users/{id}/wallet` | owner / admin | Wallet balance |
| POST | `/api/v1/orders` | owner / admin | Place order `{ userId, productId, quantity }` (query-param form also supported) |
| GET | `/api/v1/orders/user/{id}` | owner / admin | Order history |

Quick example:

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"buyer","password":"..."}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/products
```

More detail (business flow, schema, error table, known limitations): [docs/SPECIFICATION.md](docs/SPECIFICATION.md). The original Word specification is kept in the repo root.

## Notes and limitations

- Self-registration accepts `ROLE_ADMIN` to keep the demo self-contained. A real deployment would seed admins or restrict this.
- Order placement is `@Transactional` but not row-locked; under heavy concurrency add pessimistic locking or `@Version` to prevent double-spending.
- No refresh tokens; the rate limiter is in-memory (per instance).

## Author

Muhamad Septian Pamungkas · [github.com/seppam](https://github.com/seppam)

Released under the MIT License.
