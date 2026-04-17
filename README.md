# FitUnity Auth Service

Spring Boot 3.4.2 authentication microservice for the FitUnity platform. Provides JWT-based authentication with secure refresh token rotation, replay attack detection, and role-based access control.

## Architecture

```
Frontend (React :5173)
  -> API Gateway (YARP ASP.NET :5020)
    -> Auth Service (Spring Boot :8081) <- YOU ARE HERE
      -> MySQL        (users, roles, refresh tokens)
      -> Redis        (refresh tokens, blacklist, login attempts, session tracking)
```

The API Gateway forwards `/api/auth/*` requests to this service. JWT tokens issued here are validated by the Gateway and all other microservices.

## Features

- **User Registration** - Email/password registration with BCrypt password hashing
- **JWT Authentication** - HS256-signed tokens with claims: `userId`, `role`, `statutAbonnement`, `jti`
- **Secure Refresh Tokens** - UUID-based tokens stored in HttpOnly cookies, SHA-256 hashed in MySQL
- **Token Rotation** - Refresh tokens rotate on every use with replay attack detection
- **Token Family Revocation** - Compromise detection revokes all sessions in the same token family
- **Login Throttling** - Redis-based rate limiting (5 attempts per email)
- **Role-Based Access** - CLIENT, COACH, SUB_ADMIN, ADMIN roles with granular permissions
- **Subscription Status** - Reads subscription state from Redis (maintained by Payment Service via RabbitMQ)

## Tech Stack

- **Java 17**
- **Spring Boot 3.4.2** (Web, Security, Data JPA, Data Redis, Validation, AMQP)
- **MySQL 8.0** - User and refresh token persistence
- **Redis** - Session tracking, blacklist, login throttling
- **RabbitMQ** - Subscription event consumption
- **JJWT 0.12.6** - JWT generation and validation
- **BCrypt** - Password hashing
- **Lombok** - Boilerplate reduction

## API Endpoints

### Public Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/register` | Register a new user (role: CLIENT) |
| POST | `/login` | Authenticate user, returns JWT + sets refresh cookie |
| POST | `/refresh` | Rotate access token using refresh cookie |
| GET | `/oauth2/authorize/google` | Start Google OAuth redirect flow |
| GET | `/oauth2/callback/google` | Handle Google OAuth callback and issue local session |

### Authenticated Endpoints

| Method | Endpoint | Required Role |
|--------|----------|---------------|
| GET | `/profile` | Any authenticated user |
| PUT | `/profile` | Any authenticated user |
| POST | `/logout` | Any authenticated user |

### Admin Endpoints

| Method | Endpoint | Required Role |
|--------|----------|---------------|
| GET | `/admin/users` (paginated) | ADMIN |
| PUT | `/admin/users/{id}/role` | ADMIN |
| PUT | `/admin/users/{id}/activate` | ADMIN |
| PUT | `/admin/users/{id}/deactivate` | ADMIN |

## Configuration

### Environment Variables

| Variable | Description |
|----------|-------------|
| `JWT_SECRET` | Secret key for JWT signing (required) |
| `RABBITMQ_HOST` | RabbitMQ server hostname |
| `RABBITMQ_USER` | RabbitMQ username |
| `RABBITMQ_PASSWORD` | RabbitMQ password |

### application.properties

```properties
# Server
server.port=8081
spring.application.name=fitunity-auth

# MySQL
spring.datasource.url=jdbc:mysql://localhost:3306/fitunity_auth
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.hibernate.ddl-auto=update

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379

# JWT
jwt.issuer=fitunity-auth
jwt.audience=fitunity-client
jwt.access-token-expiry-minutes=15
jwt.refresh-token-expiry-days=30
jwt.refresh-token-inactivity-days=7
jwt.secret=${JWT_SECRET}

# RabbitMQ
spring.rabbitmq.host=${RABBITMQ_HOST}
spring.rabbitmq.port=5672
spring.rabbitmq.username=${RABBITMQ_USER}
spring.rabbitmq.password=${RABBITMQ_PASSWORD}

# Cookie
cookie.secure=true
cookie.same-site=Strict
```

## Security

### Password Handling
- BCrypt hashing only
- Passwords never returned in responses or logged

### Refresh Token Security
- Stored as HttpOnly, Secure, SameSite=Strict cookie
- SHA-256 hashed before MySQL storage
- Rotated on every `/refresh` call
- Replay attack detection: revoked token reuse triggers full token family revocation

### Login Throttling
- Redis counter: `login_attempts:{email}`
- 5 attempts allowed before 429 response
- Counter cleared on successful login

### Error Responses
- Login failures return generic message: "Email ou mot de passe incorrect"
- 401: Authentication required or invalid
- 403: Insufficient privileges
- 429: Too many login attempts

## Role Permissions

| Role | Capabilities |
|------|-------------|
| CLIENT | Login, logout, view/edit own profile |
| COACH | Login, logout, view/edit own profile |
| SUB_ADMIN | Same as CLIENT (reserved for future privileges) |
| ADMIN | Full access including `/admin/**` endpoints |

## Subscription Statuses

The `statutAbonnement` claim in JWT reflects subscription state:

- `ACTIVE` - Valid subscription
- `EXPIREE` - Expired subscription
- `NONE` - No subscription

This value is read from Redis (`user_sub:{userId}`) at login time, maintained by the Payment Service via RabbitMQ events.

## Running Locally

### Prerequisites
- Java 17+
- MySQL 8.0+
- Redis 6+
- RabbitMQ 3.9+

### Start Infrastructure (Docker)

```bash
docker-compose up -d mysql redis rabbitmq
```

### Build

```bash
mvn clean package
```

### Run

```bash
export JWT_SECRET="your-secret-key-here"
export RABBITMQ_HOST=localhost
export RABBITMQ_USER=guest
export RABBITMQ_PASSWORD=guest

java -jar target/fitunity-auth-0.0.1-SNAPSHOT.jar
```

## Project Structure

```
src/main/java/com/fitunity/auth/
├── config/
│   ├── SecurityConfig.java        # JWT filter, CORS, method security
│   ├── RedisConfig.java           # Redis connection
│   └── RabbitMQConfig.java        # RabbitMQ queues/exchanges
├── controller/
│   ├── AuthController.java        # /register, /login, /refresh, /logout
│   ├── ProfileController.java     # /profile endpoints
│   └── AdminController.java       # /admin/** endpoints
├── service/
│   ├── AuthService.java           # Authentication business logic
│   ├── TokenService.java          # JWT generation/validation
│   ├── RedisService.java          # Redis operations with failsafe
│   ├── JwtUserDetailsService.java # Spring Security user details
│   └── SubscriptionEventListener.java # RabbitMQ consumer
├── domain/
│   ├── Utilisateur.java           # User entity
│   ├── RefreshTokenRecord.java    # Refresh token entity
│   ├── Role.java                  # Role enum
│   └── StatutAbonnement.java      # Subscription status enum
├── dto/
│   ├── RegisterRequest.java
│   ├── LoginRequest.java
│   ├── AuthResponse.java
│   ├── RefreshResponse.java
│   └── SubscriptionEvent.java
├── filter/
│   └── JwtAuthenticationFilter.java
└── exception/
    ├── GlobalExceptionHandler.java
    ├── EmailExistsException.java
    ├── InvalidCredentialsException.java
    ├── AccountDisabledException.java
    ├── TooManyAttemptsException.java
    ├── ReplayAttackException.java
    └── SessionExpiredException.java
```

## Testing

```bash
mvn clean test
```

## Google OAuth

- Authorization entrypoint: `GET /oauth2/authorize/google`
- Callback endpoint: `GET /oauth2/callback/google`
- Callback redirects to frontend URL configured by `oauth.frontend.redirect-uri`

Required environment variables:

- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`
- `GOOGLE_REDIRECT_URI` (optional override; default `http://localhost:8081/oauth2/callback/google`)
- `OAUTH_FRONTEND_REDIRECT_URI` (optional override; default `http://localhost:5173/auth/callback`)

## Performance Check

Run mixed-load benchmark:

```bash
./load-test.sh
```

Optional tuning parameters:

- `PROFILE_RATE` (default `140`)
- `ADMIN_RATE` (default `40`)
- `LOGIN_RATE` (default `20`)
- `TEST_DURATION` (default `60s`)

## Documentation

- [Architecture](docs/context/architecture.md)
- [Authentication Flows](docs/context/auth-flows.md)
- [JWT Specification](docs/context/jwt-spec.md)
- [Refresh Token Security](docs/context/refresh-token-security.md)
- [API Specifications](docs/context/api-specs.md)
- [Security Checklist](docs/context/security-checklist.md)
- [Implementation Plan](docs/tasks/implementation-plan.md)

## Task Tracking

See [TASKS.md](TASKS.md) for current progress and pending work.

## License

Proprietary - FitUnity
