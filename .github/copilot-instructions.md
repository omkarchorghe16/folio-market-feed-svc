# Repository instructions: news-scheduler-svc

Spring Boot (Java 21, Maven) service on port 8082. It fetches market data and news from external
providers (FMP, Finnhub, Yahoo, Alpha Vantage for US; Tapetide and Upstox for India), stores it in
the shared `folio_pro` PostgreSQL database, and sends scheduled news digests (Slack, Telegram,
WhatsApp).

For every code change, follow:

- `.github/instructions/project.instructions.md`: this repository's packages, reference classes,
  shared-database rules, provider specifics, secrets files and final checks. Edited here.
- Shared standards, copied from https://github.com/omkarchorghe16/dev-standards (do not edit them
  here; change them there and run its `sync.sh`):
  - `.github/instructions/change-hygiene.instructions.md`
  - `.github/instructions/coding-standards.instructions.md`
  - `.github/instructions/naming-and-readability.instructions.md`
  - `.github/instructions/architecture.instructions.md`
  - `.github/instructions/spring-boot.instructions.md`
  - `.github/instructions/logging.instructions.md`

## Skills

Reusable workflows are in `.github/skills/*/SKILL.md`. Load every relevant skill when working on
REST endpoints, architecture, JPA persistence, external providers, scheduled digests and
notifications, or tests. `spring-boot-scheduling-notifications` is specific to this repository and
edited here; the other skills are shared from dev-standards. The instruction files stay
authoritative for rules that apply to every change, and `project.instructions.md` wins where it is
more specific.
