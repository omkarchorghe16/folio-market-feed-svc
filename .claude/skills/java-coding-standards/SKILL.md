---
name: java-coding-standards
description: Coding, naming, architecture and logging standards for news-scheduler-svc. Use before writing, changing, reviewing or renaming any Java code, REST endpoint, provider client, scheduler or notifier in this repository.
---

# Java coding standards for news-scheduler-svc

This skill applies the project's rules to Claude Code. The rules themselves live in `.github/` and
are shared with GitHub Copilot, so there is one source of truth. Do not copy them here; update the
`.github/` file instead.

## 1. Load the rules

Read every file below before writing code. All of them apply to every Java change.

- `.github/copilot-instructions.md`: repository rules and documentation/skill sync policy
- `.github/instructions/coding-standards.instructions.md`
- `.github/instructions/naming-and-readability.instructions.md`: class, method and field names; layout
- `.github/instructions/architecture.instructions.md`
- `.github/instructions/spring-boot.instructions.md`
- `.github/instructions/logging.instructions.md`

Then read the task-specific workflow from `.github/skills/`:

| Task | Skill |
|---|---|
| New or changed endpoint, DTO, status code | `.github/skills/spring-boot-rest-api/SKILL.md` |
| Entity or repository | `.github/skills/spring-boot-jpa/SKILL.md` |
| New package, layer or cross-cutting design | `.github/skills/spring-boot-architecture/SKILL.md` |
| FMP, Finnhub, Yahoo, Alpha Vantage, Tapetide, Upstox or a new provider | `.github/skills/spring-boot-provider-integration/SKILL.md` |
| Digest schedule, Slack, Telegram, WhatsApp | `.github/skills/spring-boot-scheduling-notifications/SKILL.md` |
| Tests | `.github/skills/spring-boot-testing/SKILL.md` |

## 2. Look at nearby code

Open the closest existing controller, service, client and test for the same feature and match
them, for example `StockController` and `StockService` for CRUD, or `FmpProfileClient` and
`FmpProfileService` for a provider.

## 3. Write the code

- Put code in the feature's or provider's own classes. Create new `<Resource>Controller`,
  `<Resource>Service` or `<Provider><Resource>Client` classes instead of adding to unrelated ones.
- Controller → service → client or repository, never controller → client or repository.
- Provider parsing and error translation stay in that provider's client.
- Table names must not collide with the Flyway-managed tables of `../invesment-management-svc`,
  because both services use the `folio_pro` database.
- Tests never call live providers; mock the client or the HTTP server.

## 4. Check before finishing

- [ ] Class, method and field names follow `naming-and-readability.instructions.md`.
- [ ] No one-line method bodies, single-letter parameters, generic class names or untyped
      (`JsonNode`, `Map`) responses in new endpoints.
- [ ] No API keys, tokens, credential-bearing URLs or full provider payloads in code, tests, Postman
      or logs.
- [ ] Tests added or updated for changed behavior, and `mvn test` passes.
- [ ] README, OpenAPI annotations, the Postman collection and config examples are updated when the
      API, configuration or behavior changed.
- [ ] Every `.github/skills/*` file affected by the change is updated.
- [ ] If an endpoint used by the Angular app (`/api/fmp/*`, `/api/india/*`) changed,
      `../personal-wealth-management-angular/src/app/core/services/` is checked and updated.
