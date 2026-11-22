# news-scheduler-svc — guide for Claude Code

Spring Boot (Java 21, Maven) service on port **8082**. It fetches market data and news from external
providers (FMP, Finnhub, Yahoo, Alpha Vantage for US; Tapetide and Upstox for India), stores it in the
shared `folio_pro` PostgreSQL database, and sends scheduled news digests (Slack, Telegram, WhatsApp).
The Folio Pro Angular app (`../personal-wealth-management-angular`) calls its `/api/fmp/*` and
`/api/india/*` endpoints; `../invesment-management-svc` (port 8081) owns the watchlist tables.

## Rules

Before any code change, use the `java-coding-standards` skill (`.claude/skills/java-coding-standards/SKILL.md`).
It loads the project rules, which live in `.github/` and apply to Claude Code as well:

- `.github/copilot-instructions.md` — repository overview and the list of rule files.
- `.github/instructions/project.instructions.md` — rules for this repository only (packages, shared
  database, providers, secrets files, final checks).
- `.github/instructions/*.instructions.md` (the rest) — shared change-hygiene, coding standards,
  naming and readability, architecture, Spring Boot and logging rules.
- `.github/skills/*/SKILL.md` — workflows for REST APIs, JPA, providers, logging, tests, and the
  repository-only `spring-boot-scheduling-notifications`.

### Where to change a rule

- Rules for this repository only: edit `project.instructions.md`, `copilot-instructions.md` or
  `spring-boot-scheduling-notifications` here.
- Shared rules and skills: every file that starts with a `Managed by dev-standards` comment, plus
  `.claude/skills/java-coding-standards`, is copied from
  https://github.com/omkarchorghe16/dev-standards (cloned at `../dev-standards`) and listed in
  `.github/dev-standards.lock`. Do not edit those copies here. Change them in `../dev-standards`, then
  run `./sync.sh --all` there and commit the result in each repository.
- `../dev-standards/sync.sh --check .` (run from this repository) reports drift.

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
