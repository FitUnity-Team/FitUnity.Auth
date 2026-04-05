# Project Structure

```
fitunity-auth/
├── src/main/java/com/fitunity/auth/
│   ├── controller/
│   │   ├── AuthController.java          // /register, /login, /refresh
│   │   ├── ProfileController.java       // /profile (GET, PUT)
│   │   └── AdminController.java         // /admin/**
│   ├── service/
│   │   ├── AuthService.java             // Business logic for auth ops
│   │   ├── TokenService.java            // JWT generation/validation
│   │   └── RedisService.java            // Redis operations wrapper
│   ├── domain/
│   │   ├── Utilisateur.java             // JPA Entity
│   │   ├── Abonnement.java              // JPA Entity
│   │   ├── RefreshTokenRecord.java      // JPA Entity
│   │   ├── Role.java                    // Enum: CLIENT, COACH, SUB_ADMIN, ADMIN
│   │   └── StatutAbonnement.java        // Enum: ACTIVE, EXPIREE, NONE
│   ├── repository/
│   │   ├── UtilisateurRepository.java   // extends JpaRepository<Utilisateur, UUID>
│   │   ├── AbonnementRepository.java    // extends JpaRepository<Abonnement, UUID>
│   │   └── RefreshTokenRepository.java  // extends JpaRepository<RefreshTokenRecord, UUID>
│   ├── dto/
│   │   ├── RegisterRequest.java         // @RequestBody for /register
│   │   ├── LoginRequest.java            // @RequestBody for /login
│   │   ├── LoginResponse.java           // body of /login response
│   │   └── TokenResponse.java           // body of /refresh response
│   ├── security/
│   │   ├── SecurityConfig.java          // Spring Security config
│   │   ├── JwtAuthenticationFilter.java // OncePerRequestFilter
│   │   └── JwtUserDetails.java          // implements UserDetails
│   ├── config/
│   │   └── RedisConfig.java             // RedisConnectionFactory bean
│   └── exception/
│       └── GlobalExceptionHandler.java  // @ControllerAdvise for error format
├── src/main/resources/
│   ├── application.properties           // prod placeholders
│   └── application-dev.properties       // dev values
├── Dockerfile
├── docker-compose.yml
├── .env
├── pom.xml
├── TASKS.md
├── CLAUDE.md                             // Implementation notes
└── README.md

```

## Package Conventions

- All classes use Java naming conventions (PascalCase)
- Interfaces ending with `Repository`, services ending with `Service`
- Entities are JPA `@Entity` with getters/setters (Lombok `@Data` or manual)
- DTOs use `@JsonProperty` if needed (usually not)
- Controllers are `@RestController`
- Services are `@Service`
- Repositories are `@Repository`

## Configuration Files

### pom.xml Must Include:

```xml
<dependencies>
  <!-- Spring Boot Starters -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
  </dependency>

  <!-- Database -->
  <dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
  </dependency>

  <!-- JWT -->
  <dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
  </dependency>
  <dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
  </dependency>
  <dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
  </dependency>

  <!-- Lombok -->
  <dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
  </dependency>
</dependencies>
```

### application.properties

```properties
server.port=8081
spring.application.name=fitunity-auth

# MySQL - using placeholders for docker-compose service names
spring.datasource.url=jdbc:mysql://mysql-placeholder:3306/fitunity_auth
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false

# Redis
spring.data.redis.host=redis-placeholder
spring.data.redis.port=6379

# JWT
jwt.issuer=fitunity-auth
jwt.audience=fitunity-client
jwt.access-token-expiry-minutes=15
jwt.refresh-token-expiry-days=30
jwt.refresh-token-inactivity-days=7

# Cookie
cookie.secure=true
cookie.same-site=Strict
```

### application-dev.properties

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/fitunity_auth
spring.datasource.username=root
spring.datasource.password=dev_password

spring.data.redis.host=localhost
spring.data.redis.port=6379

# Dev secret (min 32 chars for HS256)
jwt.secret=fitunity-dev-secret-key-minimum-32-characters-long

# Dev overrides
cookie.secure=false
cookie.same-site=Lax
```

## Dockerfile

Multi-stage build:

```dockerfile
# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

## docker-compose.yml

Orchestrates:
- `auth-service` (this)
- `api-gateway` (pre-built)
- `mysql` (fitunity_auth DB)
- `redis`

All on bridge network `fitunity-network`.

Health checks for MySQL and Redis.

Only expose `5020` (gateway) to host. Internal services use service names.

See separate `docker-compose.yml` spec file.
