# Technical Specification

Condensed from `B2B_Order_Management_Documentation.docx` and updated to match the current code.

## 1. Business flow

Roles: `ROLE_CLIENT` (register, login, browse products, order, view own history and wallet) and `ROLE_ADMIN` (all of that, plus create products and top up wallets).

Order placement (`POST /api/v1/orders`), all inside one `@Transactional` boundary:

1. Reject `quantity <= 0` (400)
2. Load user and product (404 if missing)
3. Check stock: `quantity > stockQuantity` -> `OutOfStockException` (400)
4. `totalPrice = price x quantity` (`BigDecimal`)
5. Check `walletBalance >= totalPrice`, else `InsufficientBalanceException` (400)
6. Debit wallet, decrement stock, save user, product and a `PAID` order (201)

Any failure rolls back; no partial state is committed. Wallets start at 0.00 and are funded by an admin via the top-up endpoint.

## 2. Data model

| Entity | Fields |
|---|---|
| User (`users`) | id, username (unique, 50), password (BCrypt, never serialized), email (unique, 100), role (`ROLE_CLIENT`/`ROLE_ADMIN`), walletBalance (decimal 19,4) |
| Product (`products`) | id, name, sku (unique index), price (decimal 19,4), stockQuantity |
| Order (`orders`) | id, user (many-to-one, lazy; exposed as `userId`), totalAmount (decimal 19,4), status (`PENDING`/`PAID`/`SHIPPED`/`CANCELLED`) |

## 3. Security

- Stateless JWT (HS256), 24 h default lifetime (`JWT_EXPIRATION_MS`), role carried in a claim; `JwtAuthenticationFilter` populates the security context.
- `/api/v1/auth/**`, Swagger UI and API docs are public; everything else requires a token.
- Method security: admin-only product creation and wallet top-up; owner-or-admin checks (`OrderAccess`) on orders and wallet reads.
- CSRF disabled (stateless API). Passwords hashed with BCrypt.
- Rate limit: Bucket4j token bucket, 100 req/hour per IP (`X-Forwarded-For` honoured), 429 when exceeded.

## 4. Caching

Cache `products` (Caffeine) on `getProductBySku`: max 1000 entries, expire 10 minutes after write.

## 5. Error model

| Exception | HTTP |
|---|---|
| InsufficientBalanceException, OutOfStockException, IllegalArgumentException, malformed JSON | 400 |
| BadCredentialsException / missing or invalid token | 401 |
| AccessDeniedException | 403 |
| ResourceNotFoundException | 404 |
| Rate limit exceeded | 429 |
| Anything else | 500 (generic message, details logged server-side) |

Body: `{ "timestamp", "status", "error", "message" }`.

## 6. Configuration

| Variable | Purpose |
|---|---|
| `JWT_SECRET` | Base64 HMAC key, >= 256 bits (required) |
| `JWT_EXPIRATION_MS` | Token lifetime, default 86400000 |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | MySQL connection |

Profile `h2` runs fully in memory. `app.rate-limit.*` in `application.yml` tunes the limiter.

## 7. Known limitations

- No row locking on order placement (add `SELECT ... FOR UPDATE` or `@Version` for high concurrency)
- No refresh tokens
- Self-registration permits `ROLE_ADMIN` (demo convenience)
