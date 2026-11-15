# news-scheduler-svc — guide for Claude Code

Spring Boot (Java 21, Maven) service on port **8082**. It fetches market data and news from external
providers (FMP, Finnhub, Yahoo, Alpha Vantage for US; Tapetide and Upstox for India), stores it in the
shared `folio_pro` PostgreSQL database, and sends scheduled news digests (Slack, Telegram, WhatsApp).
The Folio Pro Angular app (`../personal-wealth-management-angular`) calls its `/api/fmp/*` and
`/api/india/*` endpoints; `../invesment-management-svc` (port 8081) owns the watchlist tables.

## Rules

The project rules live in `.github/` and apply to Claude Code as well:

- `.github/copilot-instructions.md` — repository rules, including the documentation/skill sync policy.
- `.github/instructions/*.instructions.md` — coding standards, architecture, Spring Boot, logging.
- `.github/skills/*/SKILL.md` — workflows for REST APIs, JPA, providers, scheduling/notifications, tests.

## Commands

```bash
mvn test                      # unit + web-layer tests; never call live providers
mvn spring-boot:run           # default profile "local"; reads optional ./application-secrets.yml
docker compose up --build -d  # app container only; uses the host PostgreSQL via .env
```

## Things to know

- Secrets come from environment variables, the git-ignored `application-secrets.yml`, or the
  git-ignored `.env` (copy `.env.example`). Never commit real values or log them.
- Hibernate `ddl-auto: update` manages this service's tables. Table names must not collide with the
  Flyway-managed tables of `invesment-management-svc` in the same database.
- CORS for `/api/**` is configured in `config/WebConfig.java` via `cors.allowed-origins`
  (`CORS_ALLOWED_ORIGINS`, default `http://localhost:4200`).
- Config changes (CORS, properties) only take effect after restarting the service.
