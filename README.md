# Backend

Spring Boot backend API.

The frontend calls this backend only. The backend owns OAuth/session security, policy matching, cash-flow calculation, and proxy calls to the FastAPI AI service.

전체 설치 절차는 [DOCS 초기 세팅 튜토리얼](https://github.com/job-seeker-plan/DOCS/blob/main/INITIAL_SETUP.md)을 확인하세요.

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

For a complete local stack, run `docker compose -f BE/docker-compose.yml --env-file BE/.env up --build` from the shared parent directory. The AI container is internal-only; browsers call the Spring API on port `8000`.
