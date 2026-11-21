---
applyTo: "**/*.java"
---

# Naming and readability standards

These rules decide how new and changed code is named and laid out. They complement
`coding-standards.instructions.md` and `architecture.instructions.md`. When existing code breaks a
rule, do not rewrite it as a side effect of another task; follow the rule in new and changed code.

## One feature per class

- Name each controller, service and client after the single feature, resource or provider it owns:
  `StockController` and `StockService`, `FmpProfileController` and `FmpProfileService`,
  `TapetideClient` and `TapetideStockService`.
- Never use generic names such as `ApiController`, `MainService`, `CommonService`, `Helper`,
  `Manager` or `Utils`. They grow into classes that do everything.
- When a class starts serving a second resource or provider, split it before adding more code.
- Provider-specific code lives in its own package when it has several classes
  (`alphavantage`, `india/tapetide`, `india/upstox`).

## Class and type suffixes

| Kind | Pattern | Example |
|---|---|---|
| REST controller | `<Resource>Controller` | `SectorController` |
| Use-case service | `<Resource>Service` | `StockService` |
| External API client | `<Provider><Resource>Client` | `FmpProfileClient`, `FinnhubNewsClient` |
| Provider rate limiter | `<Provider>RateLimiter` | `AlphaVantageRateLimiter` |
| Provider settings | `<Provider>Properties` | `FmpProperties` |
| Notification channel | `<Channel>Notifier` | `SlackNotifier` |
| Scheduled job | `<Job>Scheduler` | `DigestScheduler` |
| Spring Data repository | `<Entity>Repository` | `StockRepository` |
| JPA entity | the domain noun, no suffix | `Stock`, `FmpStockProfile` |
| Request / response DTO | `<Thing>Request` / `<Thing>Response` | `StockRequest`, `BulkStockResponse` |
| Not-found / conflict errors | `<Cause>Exception` | `ResourceNotFoundException` |

Market-specific types start with the market or provider: `India...`, `Tapetide...`, `Upstox...`.

## Method names

- Within a single-resource service, short verbs are fine because the class names the resource:
  `StockService.create`, `find`, `findById`, `update`, `delete`, `createBulk`. Keep that style when
  adding to those classes.
- In classes that handle several things (provider services, India controllers, schedulers), use
  verb + noun: `fetchProfiles`, `getProfiles`, `sendDigest`, `getQuote`.
- Use `fetch...` for a call that goes to an external provider and `get...` / `find...` for a read
  from the database. Use `refresh...` for "fetch from the provider and save".
- Keep a controller method's name the same as, or clearly matched to, the service method it calls.
- Do not use noun-only method names (`profile()`, `financials()`) in new code; write
  `getProfile()`, `getFinancials()`.
- Do not use a `null` argument to switch behavior. Write two methods.
- A lookup that throws when nothing is found returns the entity; one that may find nothing returns
  `Optional<X>`.

## Fields, parameters and variables

- Inject dependencies into `private final` fields named after their type: `stockService`,
  `fmpProfileClient`. Never `service`, `client`, `repo` or a single letter.
- Use Lombok `@RequiredArgsConstructor` where nearby code does; otherwise write the constructor.
- Single-letter names are allowed only for short lambda parameters. Method parameters and local
  variables use full words: `request`, `symbol`, `profiles`.
- Collections are plural; maps say what they map (`quotesBySymbol`). Booleans read as a question
  (`isEnabled`, `hasApiKey`).
- Repeated literals, error messages and provider endpoints become `private static final` constants
  or configuration properties.

## Layout

- One statement per line, four-space indentation, braces on every `if`/`for`, and lines of 120
  characters or fewer. Never put a whole method body on one line.
- Order members as: constants, fields, constructor, public methods, private helpers, mapping
  methods last.
- Use explicit imports, as the existing code does; avoid wildcard imports.
- New endpoints return typed DTOs or entities, not `JsonNode`, `Map` or `Object`.
- Add a short Javadoc sentence to a public method only when its name does not already explain the
  behavior: rate limits, caching, partial-failure handling.

## Renaming existing code

- A rename must not change public behavior: URL paths, JSON field names, status codes, table names
  and property keys stay the same unless the change is requested.
- Update tests, `README.md`, the Postman collection and the skills under `.github/skills/` that
  mention the old name.
- Run `mvn test` after renaming.
