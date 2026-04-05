# Architectural Context

## Platform Position

```
Frontend (React :5173)
  -> API Gateway (YARP ASP.NET :5020)
    -> Auth Service (Spring Boot :8081) <- YOU ARE HERE
      -> MySQL        (users, roles, subscriptions)
      -> Redis        (refresh tokens, blacklist, login attempts)
```

- The API Gateway forwards `/api/auth/*` to this service
- Gateway strips `/api/auth` prefix → service receives: `/login`, `/register`, `/refresh`, `/logout`
- Public routes bypass gateway JWT validation
- JWT tokens issued here are validated by Gateway (primary) and all other microservices (secondary)
- Same JWT_SECRET shared across all services; only Auth signs tokens

## Actors

| Actor | Capabilities |
|---|---|
| Visiteur | Register, login only |
| Utilisateur | Authenticated: view/edit profile, logout |
| Sous-Admin | Same as Utilisateur (future privileges reserved) |
| Administrateur | Full access including `/admin/**` |

## Role Permissions

| Role | Permission |
|---|---|
| CLIENT | login, logout, own profile read/edit |
| COACH | login, logout, own profile read/edit |
| SUB_ADMIN | same as CLIENT (reserved) |
| ADMIN | everything + `/admin/**` |

## Subscription Statuses

`ACTIVE`, `EXPIREE`, `NONE`
