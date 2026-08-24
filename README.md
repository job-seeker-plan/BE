# Backend

Spring Boot backend API.

The frontend calls this backend only. The backend owns OAuth/session security, policy matching, cash-flow calculation, and proxy calls to the FastAPI AI service.

## Run

```bash
cp .env.example .env
mvn spring-boot:run
```

Required runtime services:

- PostgreSQL on `DATABASE_URL`
- AI FastAPI service on `AI_SERVICE_URL` with the same `AI_SERVICE_TOKEN`
- OAuth provider credentials for Google/Kakao/Naver
- `GOV_API` for Ontong Youth policy API

For a complete local stack, run `docker compose --env-file BE/.env up --build` from the project root. The AI container is internal-only; browsers call the Spring API on port `8000`.
