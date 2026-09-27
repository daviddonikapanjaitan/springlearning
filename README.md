# springlearning

A Spring Boot learning project with a **Users** API, an **Orders** API and a **Report Orders** API, protected with JWT authentication (Spring Security) and backed by PostgreSQL, Redis (cache) and Kafka (order events).

## Tech stack

| Part | Technology |
|---|---|
| Language / framework | Java 26, Spring Boot 4.1 (Web MVC, Data JPA, Validation) |
| Database | PostgreSQL 17 (schema managed manually with SQL scripts) |
| Cache | Redis 8 (+ RedisInsight GUI) |
| Messaging | Apache Kafka 4.1 (KRaft, single broker) + kafka-ui (kafbat) GUI |
| AI | Spring AI 2.0 (OpenAI-compatible client) with OpenRouter, model `deepseek/deepseek-v4-flash-0731` |
| PDF | OpenPDF 3.0 |
| JSON | Jackson 3 |
| Security | Spring Security 7, JWT (jjwt 0.13, HS256), BCrypt passwords |

## Getting started

### 1. Start the infrastructure

```bash
docker compose up -d postgres redis redisinsight kafka kafka-ui
```

| Service | Address |
|---|---|
| PostgreSQL | `localhost:5432` (db `springlearning`, user/password `postgres`) |
| Redis | `localhost:6379` |
| RedisInsight | http://localhost:5540 (accept the EULA on first open, the `springlearning-redis` connection is pre-configured) |
| Kafka | `localhost:9092` |
| kafka-ui | http://localhost:8090 (cluster `springlearning`: browse topics and read messages, e.g. `stream-order-users`) |

`docker compose up -d` without service names also builds and starts the app itself on port `8080`.

### 2. Add your OpenRouter API key and JWT secret

Copy `.env.example` to `.env` and put your values in it. `.env` is ignored by git, never commit it.

```properties
OPENROUTER_API_KEY=sk-or-v1-...
# Signs the JWT access tokens, at least 32 bytes, base64-encoded. Generate one with: openssl rand -base64 32
JWT_SECRET=...
```

The app loads `.env` from the working directory (`spring.config.import`), and `docker compose` passes it to the app container.

### 3. Create the database schema

Flyway is **disabled**. Run the scripts in `src/main/resources/db/migration` yourself (e.g. with DBeaver), in order:

| Script | What it does |
|---|---|
| `V1__create_users_table.sql` | Creates the `users` table |
| `V2__make_email_and_username_unique.sql` | Makes email and username unique (case-insensitive, including soft-deleted users) |
| `V3__create_orders_table.sql` | Creates the `orders` table |
| `V4__create_report_orders_table.sql` | Creates the `report_orders` table |
| `V5__add_report_summary_to_report_orders.sql` | Adds the `report_summary` column to `report_orders` |
| `V6__add_pdf_report_to_report_orders.sql` | Adds the `pdf_report` column (`BYTEA`) to `report_orders` |
| `V7__create_tokens_table.sql` | Creates the `tokens` table (JWT access tokens) |

Hibernate runs with `ddl-auto: validate`: it never changes the schema, and the app refuses to start if a table or column is missing.

### 4. Run the app

```bash
./mvnw spring-boot:run
```

The app runs on http://localhost:8080.

## Configuration

Everything lives in `src/main/resources/application.yaml`. Connection settings can be overridden with environment variables:

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/springlearning` |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `OPENROUTER_API_KEY` | none, required (from `.env`) |
| `JWT_SECRET` | none, required (from `.env`): base64 key of at least 32 bytes |

App-specific settings:

| Property | Default | Meaning |
|---|---|---|
| `app.cache.orders.ttl` | `30m` | How long a cached order list page lives in Redis |
| `app.security.jwt.secret` | `${JWT_SECRET}` | Key that signs and verifies the JWTs |
| `app.security.jwt.expiration` | `1h` | How long an access token is valid |
| `app.security.jwt.expired-cleanup-interval` | `5m` | How often tokens past their expiry are marked `expired` and `revoked` |

## Common API conventions

- **Authentication**: every endpoint except `POST /api/auth/login` and `POST /api/users` (registration) needs `Authorization: Bearer <token>`. Without a valid token the API answers `401`.
- **The user comes from the token**: no endpoint takes a `userId` anymore. The token's username is stored in `created_by` / `updated_by`.
- **`X-Actor` header** (optional, only for `POST /api/users`): who registers the user, stored in `created_by` / `updated_by`. Defaults to `system`.
- **Timestamps** are stored and returned in UTC (`Instant`, `TIMESTAMPTZ`).
- **Pagination**: `page` (default `0`) and `size` (default `20`, max `100`).
- **Errors** use the RFC 9457 Problem Details format. Validation errors return `400` with an `errors` map per field.

## Auth API — `/api/auth`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/auth/login` | Public. Log in with username and password, returns a JWT valid for 1 hour |
| `POST` | `/api/auth/logout` | Needs the token. Revokes and expires it (`204`) |

Login body and response:

```json
{ "username": "john", "password": "password123" }
```

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3599,
  "expiresAt": "2026-09-27T17:50:15Z"
}
```

Then send `Authorization: Bearer <accessToken>` with every other request.

### How tokens work

- The JWT is signed with HS256 (`JWT_SECRET`) and holds `sub` (user id), `username`, `jti` (random id), `iat` and `exp` (1 hour).
- Every issued token is stored in the **`tokens`** table (`user_id`, `token`, `expires_at`, `expired`, `revoked`, `created_*`, `updated_*`, times in UTC) and cached in Redis until it expires.
- **Every request** checks the signature and expiry of the JWT, then the `tokens` table (also when Redis has the token): the token must be `revoked = false`, `expired = false`, before `expires_at`, and its user enabled and not deleted. Redis only caches the user of the token.
- **Login again**: the user's old tokens are set to `revoked = true` and `expired = true`, and a new token is issued. Two logins of the same user run one after the other (the user row is locked), so only the newest token stays valid.
- **Logout**: the token is set to `revoked = true` and `expired = true`.
- **Expired token**: a request with it gets `401 Token has expired`, and the row is set to `revoked = true` and `expired = true`. A job also does this every 5 minutes for expired tokens that are never sent again (`updated_by = system`).
- **Disabled or deleted user** (`PUT /api/users/me` with `enabled: false`, `DELETE /api/users/me`): all tokens of the user are revoked.

| Situation | Response |
|---|---|
| No `Authorization` header on a protected endpoint | `401` |
| Malformed or wrongly signed token | `401 Token is invalid` |
| Token past its expiry | `401 Token has expired` |
| Token revoked (logout, newer login, user disabled or deleted) | `401 Token has been revoked` |
| Wrong username or password | `401 Invalid username or password` |
| Correct password but user disabled | `403` |

## Users API — `/api/users`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/users` | Public. Register a user (`201` + `Location: /api/users/me`) |
| `GET` | `/api/users?page=0&size=20` | List active users, sorted by id (cached) |
| `GET` | `/api/users/me` | Get the logged-in user (cached) |
| `PUT` | `/api/users/me` | Update the logged-in user |
| `DELETE` | `/api/users/me` | Soft delete the logged-in user (`204`), revokes its tokens |

Rules:
- Email and username are **unique** across all users (including soft-deleted ones), compared **case-insensitively**. Duplicates return `409`.
- Email is stored in lowercase.
- **Email and username cannot be changed** after creation: the update body only accepts `password`, `fullName`, `address`, `gender` and `enabled`.
- The password is stored as a BCrypt hash and never returned.
- Delete is a soft delete (`is_deleted = true`). Deleted users return `404`.

Example create body:

```json
{
  "email": "john@example.com",
  "password": "password123",
  "username": "john",
  "fullName": "John Doe",
  "address": "Jakarta",
  "gender": "male",
  "enabled": true
}
```

## Orders API — `/api/orders`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/orders` | Create an order (`201`) |
| `GET` | `/api/orders?page=0&size=20` | List the logged-in user's orders, newest first (cached) |
| `PATCH` | `/api/orders/{orderId}/complete` | Complete an order of the logged-in user (no body) |

Create body (the user comes from the token; `total_price` and `invoice_number` are generated by the server):

```json
{
  "itemName": "Keyboard",
  "quantity": 3,
  "itemPrice": 150000,
  "orderDescription": "optional"
}
```

### Order flow

```
POST /api/orders ──► orders row: PENDING, order_receive = false
        │
        └─(after commit)─► Kafka topic "stream-order-users"
                                   │
                                   ▼
                       OrderEventListener: IN_PROGRESS, order_receive = true,
                       updated_by = "order-listener"
                                   │
PATCH /api/orders/{id}/complete ───┴─► COMPLETED
```

1. **Create**: the order is saved as `PENDING` with `order_receive = false`, then a message `{orderId, userId, invoiceNumber}` is published to `stream-order-users`. It is published only after the database commit, so the listener never sees an order that does not exist.
2. **Listener**: sets `order_receive = true`, `order_status = IN_PROGRESS`, and updates `updated_at` / `updated_by`. Duplicate messages are skipped (only `PENDING` orders are changed). Malformed messages are logged and skipped.
3. **Complete**: sets `order_status = COMPLETED` and updates `updated_at` / `updated_by`.

### Order rules

- Only users with `is_enabled = true` and `is_deleted = false` can **create** or **complete** orders. A missing or deleted user returns `404`; a disabled user returns `403`.
- Only the user who created the order can complete it. Anyone else gets `403`.
- Only `IN_PROGRESS` orders can be completed. `PENDING` (not received yet) or `COMPLETED` orders return `409`.
- Limits: `quantity` 1 – 1,000,000 and `itemPrice` 0 – 1,000,000,000,000, so `total_price` always fits in a `BIGINT`.
- Order updates lock the row (`SELECT ... FOR UPDATE`), so the listener and the complete API cannot overwrite each other.
- The Kafka topic `stream-order-users` (3 partitions, 1 replica) is created automatically on startup. The order id is the message key.

## Report Orders API (Spring AI tool calling) — `/api/report-orders`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/report-orders` | Start a report of the logged-in user's orders (`202`, no body) |
| `GET` | `/api/report-orders?page=0&size=20` | List the logged-in user's reports, newest first. `pdfAvailable` tells if the PDF can be downloaded |
| `GET` | `/api/report-orders/{id}/pdf` | Download the PDF report (`application/pdf`, e.g. `report-order-15-completed.pdf`). A report of another user returns `404` |

How it works:

1. `POST` checks the user exists (not deleted) and creates **two** `report_orders` rows, one for `COMPLETED` and one for `IN_PROGRESS` orders, with `report_progress = IN_PROGRESS` and an empty `total_amount`. It returns right away.
2. After the commit, a background thread (`@Async`) asks the AI model to build the report with tool calling. The tools are bound to the requested user, so the model cannot read other users' orders:
   - `sumOrderTotalPrice(orderStatus)` sums `orders.total_price` of that user for the status.
   - `generateOrderPdfReport(orderStatus)` builds the PDF with OpenPDF: every order with its invoice number, item name, quantity, item price and total price, and the "Total all orders" row.
3. The model answers with structured JSON (`completedTotalAmount`, `completedSummary`, `inProgressTotalAmount`, `inProgressSummary`). The app checks the totals match exactly what the tool returned, that each summary states its exact total, and that each PDF was generated and its "Total all orders" equals the total, so a made-up number is never stored.
4. Both rows get `total_amount`, `report_summary` (e.g. "The total amount of completed orders is 15000000."), `pdf_report` (the PDF of that status), `report_progress = COMPLETED` and `updated_by = report-ai`. If anything fails (AI error, wrong answer, missing tool call), both rows become `FAILED` instead.

Poll the `GET` API until `reportProgress` is no longer `IN_PROGRESS` (usually a few seconds), then download the PDF. The download returns `404` for an unknown report and `409` while the report is `IN_PROGRESS`, when it `FAILED`, or for reports created before PDF reports existed.

## Redis caching

Cached values are stored as JSON and expire after 30 minutes. If Redis is down or a cached value is unreadable, the app logs a warning and reads from the database instead.

| Key | Content | Evicted when |
|---|---|---|
| `users:{id}` | One user | That user is updated or deleted |
| `users:list:page:{page}:size:{size}` | One page of users | Any user is created, updated or deleted |
| `orders:list:user:{userId}:page:{page}:size:{size}` | One page of one user's orders | Any order is created, received by the listener, or completed |
| `auth:tokens:{sha256 of token}` | The user (`userId`, `username`) of a valid token, lives until the token expires | Logout, new login, user disabled or deleted, token found revoked or expired |
| `users:list:keys` / `orders:list:keys` | Set of cached list keys (used to evict lists without scanning Redis) | Together with the lists |

Eviction happens after the database transaction commits. If you change data directly in the database (e.g. with DBeaver), delete the related keys in RedisInsight, otherwise the API can show old data until the cache expires.

## Project structure

```
src/main/java/com/course/springlearning
├── config/          GlobalExceptionHandler, PasswordConfig, AsyncConfig, SchedulingConfig
├── auth/
│   ├── config/      SecurityConfig (stateless SecurityFilterChain)
│   ├── controller/  AuthController (login, logout)
│   ├── dto/         LoginRequest, LoginResponse
│   ├── entity/      Token
│   ├── exception/   InvalidCredentialsException, InvalidTokenException
│   ├── repository/  TokenRepository
│   ├── security/    JwtAuthenticationFilter, RestAuthenticationEntryPoint, AuthenticatedUser
│   └── service/     AuthService, TokenService, JwtService, TokenCache (Redis), ExpiredTokenJob (@Scheduled)
├── user/
│   ├── controller/  UserController
│   ├── dto/         CreateUserRequest, UpdateUserRequest, UserResponse, PageResponse
│   ├── entity/      User
│   ├── exception/   DuplicateUserException, UserNotFoundException, UserDisabledException
│   ├── repository/  UserRepository
│   └── service/     UserService, UserCache
└── order/
    ├── controller/  OrderController
    ├── dto/         CreateOrderRequest, OrderResponse, OrderCreatedMessage
    ├── entity/      Order, OrderStatus
    ├── exception/   OrderNotFoundException, OrderAccessDeniedException, InvalidOrderStatusException
    ├── kafka/       OrderKafkaConfig, OrderEventPublisher, OrderEventListener
    ├── repository/  OrderRepository
    └── service/     OrderService, OrderCache
└── report/
    ├── ai/          OrderReportAiClient (ChatClient), OrderReportTools (@Tool), GeneratedReport
    ├── controller/  ReportOrderController
    ├── dto/         ReportOrderResponse, ReportResult, ReportPdfFile
    ├── entity/      ReportOrder, ReportProgress
    ├── exception/   ReportOrderNotFoundException, ReportPdfNotAvailableException
    ├── pdf/         OrderPdfReportGenerator (OpenPDF)
    ├── repository/  ReportOrderRepository
    └── service/     ReportOrderService, ReportOrderProcessor (@Async)
```

## Development history

1. **Users CRUD API** with a Docker setup for PostgreSQL, Kafka and Redis.
2. **Unique email and username**: `V2` migration makes them unique across all users and case-insensitive.
3. **Configuration**: Flyway disabled (SQL scripts are run manually) and `application.properties` replaced by `application.yaml`.
4. **Update user** no longer accepts email and username.
5. **Redis cache for users**: user detail and user list are cached for 30 minutes and evicted on create, update and delete.
6. **RedisInsight** added to `docker-compose.yml` as a Redis GUI client.
7. **Orders API**: `orders` table, create order, Kafka topic `stream-order-users` with a listener that moves orders to `IN_PROGRESS`, and a complete order API restricted to the order's creator.
8. **Order list API** with a Redis cache, evicted by create, the listener and complete. The cache TTL is configurable with `app.cache.orders.ttl`.
9. **Active users only**: only enabled, non-deleted users can create and complete orders.
10. **kafka-ui** added to `docker-compose.yml` on port `8090` to inspect Kafka topics and messages.
11. **Spring AI report orders**: `report_orders` table, a background AI process that uses tool calling to sum order totals for `COMPLETED` and `IN_PROGRESS`, and a list API. The OpenRouter key lives in the git-ignored `.env`.
12. **Report summary**: `V5` adds `report_summary`, an AI-written summary per report row.
13. **PDF reports**: `V6` adds `pdf_report`. The AI generates a PDF per report row through tool calling (OpenPDF), and `GET /api/report-orders/{id}/pdf` downloads it.
14. **JWT authentication** (Spring Security): `POST /api/auth/login` and `/logout`, 1-hour tokens stored in the `tokens` table (`V7`) and cached in Redis. Orders, reports and users are protected; `userId` in bodies, query parameters and paths is replaced by the user of the token (`/api/users/{id}` became `/api/users/me`).

## Known limitations

- If Kafka is unavailable at the moment an order is created, the order is saved but the message is not sent, so it stays `PENDING` (the error is logged). The outbox pattern would fix this.
- There is no API to get a single order yet.
- A report that is `IN_PROGRESS` when the app stops stays `IN_PROGRESS` (the background work is lost). Start a new report instead.
- The report list loads the `pdf_report` bytes of each row from the database (not returned in the JSON). Fine for small PDFs; a very large number of big PDFs would need a lighter query.
- The user cache TTL is fixed at 30 minutes in `UserCache` (only the order cache TTL is configurable).
