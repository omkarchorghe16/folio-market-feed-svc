---
applyTo: "**"
---

# Project rules: news-scheduler-svc

Repository-specific rules. The shared rules in the other `.github/instructions/` files (managed by
dev-standards) also apply; where this file is more specific, it wins. Edit this file here.

## Layout and reference classes

- Base package `com.stocknews`: `controller`, `service`, `dto`, `model` (entities), `repository`,
  `config` (`AppProperties`, provider `*Properties`, `RestClientConfig`, `WebConfig`), `client` and
  provider packages (`alphavantage`, `india/tapetide`, `india/upstox`, `india/nse`, `us/nasdaq`),
  `scheduler`, `digest`, `notification`, `exception/GlobalExceptionHandler` (ProblemDetail errors).
- Match `StockController` / `StockService` for CRUD and `FmpProfileClient` / `FmpProfileService`
  for a provider. Controller tests: `src/test/java/com/stocknews/controller/*ControllerTest`.
- Lombok is used: `@RequiredArgsConstructor` and `@Slf4j` where nearby code uses them.

## Naming exceptions

- JPA entities are the domain noun without a suffix: `Stock`, `FmpStockProfile`.
- Single-resource services (`StockService`) keep their short verbs: `create`, `find`, `findById`,
  `update`, `delete`, `createBulk`. Provider services, India controllers and schedulers use
  verb + noun (`fetchProfiles`, `getQuote`, `sendDigest`).
- No wildcard imports.

## Persistence

- Hibernate `ddl-auto: update` manages this service's tables (no Flyway). It cannot add a
  `NOT NULL` column to a non-empty table; backfill manually first.
- The database is the shared `folio_pro` PostgreSQL (`localhost:5432` from the host,
  `host.docker.internal:5432` from the Compose container; Compose does not run PostgreSQL). All
  tables of both services live in the `public` schema. Never alter or map the Flyway-managed tables
  of `../invesment-management-svc` (`portfolios`, `holdings`, `watchlists_*`, `watchlist_items_*`,
  `user_preferences`), keep table names unique, and never suggest `flyway clean` on this database.
- H2 for tests via `src/test/resources/application.yml`.
- FMP profiles are upserted by unique symbol, keeping `id` and `created_date`; `updated_date` changes
  on each refresh. GET and POST `/api/fmp/profiles` serve rows updated today (in
  `fmp.profile-refresh-time-zone`) from PostgreSQL and fetch other symbols from FMP one at a time,
  saving each as soon as it is fetched. Key, US-exchange and quota checks run only when a fetch is
  needed.
- Bulk stock-symbol ingestion (`POST /api/stock-symbols`, needs `sectorId` and non-empty `tickers`)
  delegates to `StockService.createBulk`: symbols are normalized, existing tickers in the same
  sector are skipped, and new records are returned.

## Providers

- FMP, Finnhub, Yahoo and Alpha Vantage (US); Tapetide and Upstox (India). Reuse the `RestClient`
  from `RestClientConfig`. Durable daily quotas are counted from persisted calls.
- NSE symbol lists (`india/nse`): keyless CSV downloads through `nseRestClient`
  (`nse.timeout-seconds`, browser-like `User-Agent`). `NseSymbolService` validates both files with
  `NseSymbolCsvParser` before atomically replacing the copies in `nse.storage-directory`; search
  reads the stored CSVs and caches parsed rows until a file's modification time changes.
- US symbol lists (`us/nasdaq`): Nasdaq Trader `nasdaqlisted.txt` / `otherlisted.txt` through
  `nasdaqRestClient`; `NasdaqSymbolFileParser` finds columns by header, skips test issues, maps
  `ETF` to `type` and rejects a file without the closing `File Creation Time` line.
  `NasdaqSymbolService` replaces both files only after both parse.
- Symbol files are on local disk, not in PostgreSQL, and re-downloaded at startup when missing.
- Scheduling and notifications: see the project skill `spring-boot-scheduling-notifications`.

## Secrets and configuration

- Secrets come from environment variables, the git-ignored root `application-secrets.yml`
  (imported by `application.yml`, for host-run Maven/IDE) or the git-ignored `.env` (Compose).
  `application-secrets.yml` is developer-owned: never overwrite, regenerate or move its values.
  `src/main/resources/application-local.yml` is tracked and must stay free of credentials.
- CORS for `/api/**` is in `config/WebConfig.java` (`cors.allowed-origins`, env
  `CORS_ALLOWED_ORIGINS`, default `http://localhost:4200`). Restart after config changes.

## Docs, API contract and deployment

- Postman: `postman/stock-news-scheduler.postman_collection.json` with `{{url}}` and the local
  environment; requests can have real side effects (digests, provider calls).
- `.github/workflows/ci-cd.yml` tests, packages and builds a Docker image; it does not publish or
  deploy. `k8s/` and `scripts/deploy-local-k8s.sh` are an optional single-replica Docker Desktop
  workflow (scheduling is instance-local).

## Before finishing

- [ ] `mvn test` passes; no live provider or notification calls in tests.
- [ ] If an endpoint used by the Angular app (`/api/fmp/*`, `/api/india/*`, `/api/us/*`, `/api/digest/*`) changed,
      `../personal-wealth-management-angular/src/app/core/services/` is checked and updated.
