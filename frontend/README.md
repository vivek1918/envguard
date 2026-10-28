# EnvGuard Frontend

Angular standalone SPA for EnvGuard — environment configuration approval and deployments.

## Run locally

```bash
cd frontend
npm install
npm start
```

App: http://localhost:4200  
API (dev): `http://localhost:8080/api` (see `src/environments/environment.development.ts`)

Ensure the backend is running and CORS allows `http://localhost:4200`.

## Tests

```bash
npm test
```

Uses Angular’s Vitest unit-test builder (`ng test`).

## Production build

```bash
npm run build
```

Output: `dist/frontend/browser`

## Docker

```bash
docker build -t envguard-frontend .
docker run -p 80:80 envguard-frontend
```

`nginx.conf` serves the SPA and proxies `/api/` to `http://backend:8080/api/`.

## Key routes

| Path | Access |
|------|--------|
| `/login` | Guest (login + first-time register) |
| `/dashboard` | Authenticated |
| `/projects` | Authenticated |
| `/projects/new`, `/projects/:id/edit` | ADMINISTRATOR, DEVELOPER |
| `/projects/:id`, `/projects/:id/environments` | Authenticated |
| `/approvals` | ADMINISTRATOR, DEVELOPER |
| `/deployments/:id` | Authenticated |
| `/audit` | Authenticated |

Roles: `ADMINISTRATOR`, `DEVELOPER`, `VIEWER`. JWT is sent via `Authorization: Bearer`.
