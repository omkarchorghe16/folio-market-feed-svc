# Stock News Scheduler Service

A complete Java Spring Boot application that fetches daily stock market news for a configurable watchlist (US and India markets) and sends formatted digests to Slack, Telegram, and WhatsApp on a schedule.

## Features

- **Scheduled Digest**: Automatic daily news digest (configurable cron, default: 9 AM weekdays)
- **Multi-Market Support**: Separate fetches for US (NYSE/NASDAQ) and India (NSE) markets
- **Multiple News Sources**: Integrates Finnhub and NewsAPI.org
- **Smart Deduplication**: SHA-256 based dedup with 3-day history stored in PostgreSQL
- **Multi-Channel Notifications**: Slack, Telegram, WhatsApp (Twilio)
- **On-Demand Triggers**: REST endpoints to manually trigger digests without waiting for schedule
- **Stock Profiles**: Fetch Finnhub, Yahoo Finance, Alpha Vantage, and FMP company information and persist it in PostgreSQL
- **India Stocks**: Tapetide (NSE/BSE fundamentals, profiles, financials, shareholding, forecasts, history) and Upstox (live quotes, candles) under `/api/india/*`, cached in PostgreSQL to save provider quota
- **Browser access (CORS)**: `/api/**` accepts calls from the Folio Pro Angular app (`http://localhost:4200` by default)
- **Sector and Stock Management**: CRUD endpoints for sectors and sector-associated stock tickers, including bulk stock creation
- **API Documentation**: Interactive Swagger UI and generated OpenAPI specification
- **Database**: PostgreSQL for application data; H2 is used only by tests
- **Local Development**: Docker Compose starts the Spring Boot service with local PostgreSQL
- **Development Standards**: Repository-specific Copilot instructions and reusable backend skills for architecture, REST APIs, JPA, provider integrations, scheduling/notifications, and testing
- **Configurable Everything**: @ConfigurationProperties binds all settings from environment variables

## Tech Stack

- **Java 21**
- **Spring Boot 3.5.16**
- **Maven**
- **Spring Data JPA + PostgreSQL** (H2 for tests)
- **Spring RestClient (6.1+)**
- **Lombok**
- **SLF4J Logging**

## Project Structure

```
com/stocknews/
├── StockNewsApplication.java
├── config/
│   ├── AppProperties.java          # app.* (notifications, APIs, schedule)
│   ├── FmpProperties.java / TapetideProperties.java / UpstoxProperties.java / NseProperties.java
│   ├── OpenApiConfiguration.java
│   ├── RestClientConfig.java       # one RestClient per provider
│   └── WebConfig.java              # CORS for /api/** (cors.allowed-origins)
├── scheduler/
│   ├── DigestScheduler.java        # Scheduled cron job
│   └── NseSymbolSyncScheduler.java # Weekly NSE stock/ETF symbol-list download
├── digest/
│   ├── DigestService.java          # Orchestrates fetch → dedup → format → notify
│   └── DigestFormatter.java        # Formats messages for each channel
├── client/
│   ├── NewsItem.java               # Record: title, url, source, publishedAt, symbol, market
│   ├── NewsSourceClient.java       # Interface
│   ├── FinnhubNewsClient.java      # Finnhub API integration
│   ├── NewsApiClient.java          # NewsAPI.org integration
│   └── FmpProfileClient.java       # FMP HTTP request and response parsing
├── notification/
│   ├── Notifier.java               # Interface
│   ├── SlackNotifier.java          # Slack Webhook
│   ├── TelegramNotifier.java       # Telegram Bot API
│   └── WhatsAppNotifier.java       # Twilio WhatsApp API
├── controller/
│   ├── DigestController.java        # /api/digest/* (on-demand daily digest)
│   ├── FmpProfileController.java    # /api/fmp/profiles
│   ├── NseSymbolController.java     # /api/india/nse/* (stock + ETF ticker search)
│   ├── SectorController.java
│   ├── StockController.java
│   ├── StockSymbolController.java
│   ├── TapetideStockController.java # /api/india/tapetide/* (fundamentals)
│   ├── UpstoxMarketDataController.java # /api/india/upstox/* (quotes, candles)
│   └── UsStockProfileController.java # Finnhub / Yahoo / Alpha Vantage profiles
├── india/
│   ├── IndiaSymbols.java            # NSE symbol normalization
│   ├── nse/                         # NSE symbol-list CSV client, parser + search service
│   ├── tapetide/                    # Tapetide client + service (PostgreSQL-cached)
│   └── upstox/                      # Upstox market-data client + service
├── dto/                            # Validated requests and API response records
├── model/                          # JPA entities and provider-specific records
├── repository/                     # Spring Data repositories (PostgreSQL)
├── alphavantage/
│   ├── AlphaVantageOverviewClient.java
│   ├── AlphaVantageOverviewService.java
│   └── AlphaVantageRateLimiter.java
├── service/                        # Stock/profile workflows, FMP quotas, normalization, and rate limits
└── exception/                      # API exceptions and centralized ProblemDetail mapping
```

Repository coding guidance lives in `.github/copilot-instructions.md` and focused standards in
`.github/instructions/`. Reusable workflows are in `.github/skills/`:

- `spring-boot-architecture`: cross-layer design, boundaries, and architecture changes
- `spring-boot-rest-api`: controllers, validation, OpenAPI, MockMvc, and Postman
- `spring-boot-jpa`: entities, repositories, transactions, and database-backed services
- `spring-boot-provider-integration`: provider clients, quotas, parsing, and safe errors
- `spring-boot-scheduling-notifications`: digest scheduling, orchestration, and delivery
- `spring-boot-testing`: focused JUnit, MockMvc, provider, and persistence tests

For every feature or behavior change, review **all** skills and update those affected by the code or
shared conventions. Keep this README and directly related API, configuration, and operational
documentation synchronized with the implementation. Do not leave relevant documentation stale or
make unrelated skill edits just to create churn.

## Prerequisites

### Required API Keys & Credentials

1. **Finnhub API** (optional but recommended for US market news)
   - Sign up: https://finnhub.io/
   - Get API key from dashboard

2. **NewsAPI.org** (optional but recommended for general market news)
   - Sign up: https://newsapi.org/
   - Get API key from dashboard

3. **Alpha Vantage** (optional, for company financial overviews)
   - Create an API key: https://www.alphavantage.co/support/#api-key
   - Free-tier quota used by this service: 25 requests/day and 5 requests/minute

4. **Slack** (optional for Slack notifications)
   - Create an Incoming Webhook: https://api.slack.com/apps/
   - Webhook URL format: `https://hooks.slack.com/services/YOUR/WEBHOOK/URL`
   - Optionally create a second webhook for portfolio channel

5. **Telegram** (optional for Telegram notifications)
   - Create a bot via @BotFather on Telegram
   - Get Bot Token and Chat ID

6. **Twilio WhatsApp** (optional for WhatsApp notifications)
   - Sign up: https://www.twilio.com/
   - Set up WhatsApp Sandbox: https://www.twilio.com/console/sms/whatsapp/learn
   - Get Account SID, Auth Token, and WhatsApp numbers
   - Note: Requires message template approval for production use

7. **Tapetide** (optional, for India/NSE fundamentals and profiles)
   - Create a token: https://tapetide.com/settings/tokens
   - Set `TAPETIDE_TOKEN` (or `tapetide.token` in `application-secrets.yml`). Free plan: 50 calls/day

8. **Upstox** (optional, for India live quotes and candles)
   - Create a read-only Analytics Token: https://account.upstox.com/developer/apps (Analytics tab)
   - Set `UPSTOX_ACCESS_TOKEN` (or `upstox.access-token` in `application-secrets.yml`)

## Setup & Running Locally

### Keeping API keys and credentials out of Git

`application.yml` reads secrets from environment variables (for example, `FINNHUB_API_KEY`). The
`.env` file is git-ignored; copy the committed template with `cp .env.example .env` and fill in
local development values before running.

#### Shared `folio_pro` database

This service shares one PostgreSQL database, `folio_pro`, with
[investment-management-svc](https://github.com/omkarchorghe16/invesment-management-svc) (folio-pro).
Both applications keep their tables together in the `public` schema:

| Tables | Owner application | Managed by |
| --- | --- | --- |
| `portfolios`, `holdings`, `watchlists_*`, `watchlist_items_*`, `user_preferences`, `flyway_schema_history` | investment-management-svc | Flyway migrations (`ddl-auto: validate`) |
| `sectors`, `stocks`, `sent_articles`, `fmp_*`, `finnhub_*`, `yahoo_*`, `alpha_vantage_*` | this service | Hibernate `ddl-auto: update` |

Because the schema is shared:

- Keep table names unique across both services. A folio-pro migration that creates a table with one of
  this service's names (or the reverse) will fail or collide.
- Never run `flyway clean` against `folio_pro`; it drops every object in `public`, including this
  service's tables and data.
- Hibernate `update` only adds tables/columns for this service's entities and never drops folio-pro's
  tables, while folio-pro's `validate` ignores tables it does not map.

Connect as the `folio_pro` role, which owns the database; do not use a superuser for the application.

The database runs on the Mac (Homebrew PostgreSQL on `localhost:5432`). To run Spring Boot from a
terminal, start Maven; the defaults already point at the shared database:

```bash
# Defaults: POSTGRES_URL=jdbc:postgresql://localhost:5432/folio_pro, POSTGRES_USER=folio_pro
export POSTGRES_PASSWORD="your-local-postgres-password"   # leave unset if local auth is trust
mvn spring-boot:run
```

For an IDE run configuration, set `POSTGRES_URL`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` under
**Environment variables** if they differ from the defaults. Compose reads `.env` automatically, but
Maven and IDE run configurations do not. An ignored `application-secrets.yml` overrides these values,
so keep its `spring.datasource` entries pointing at `folio_pro` too. Do not commit `.env` or IDE run
configuration files containing credentials, or paste secrets into Postman exports.

An optional local YAML override is also supported: copy
`src/main/resources/application-local.yml` to the repository root as
`application-local.yml`, then run with the `local` Spring profile. The root override is ignored by Git.
Prefer referencing environment variables in the local file rather than writing literal secrets:

```yaml
app:
  apis:
    finnhub-key: ${FINNHUB_API_KEY}
```

If you specifically want a local secrets file, create `application-secrets.yml` in the repository
root (the same directory as `pom.xml`). `application.yml` imports this file at startup, and
`.gitignore` excludes it. Put credentials under the matching configuration keys:

```yaml
app:
  notifications:
    telegram:
      bot-token: "your-new-telegram-bot-token"
      chat-id: "your-telegram-chat-id"
  apis:
    finnhub-key: "your-finnhub-api-key"
    newsapi-key: "your-newsapi-key"
```

Create it locally without adding it to Git, restrict its filesystem permissions (for example,
`chmod 600 application-secrets.yml` on macOS/Linux), and check `git status` to confirm it is ignored.
The import is optional for direct local runs. Docker Compose passes configuration through `.env` and
does not mount this file. The optional Docker Desktop Kubernetes workflow can mount it as a local
Kubernetes Secret; it is excluded from the Docker build context and image.

Keep credentials in local environment variables or ignored local files; never put them in the image,
source repository, Postman exports, or committed manifests. Automated tests use mocks and do not
require live provider keys.

Credentials pasted into chat or other shared systems should be treated as exposed. Revoke/regenerate
credentials immediately if they are accidentally exposed.

### Run Spring Boot in Docker for development

Compose automatically reads `.env` from the repository root. Create it from the template with
`cp .env.example .env` (the template has blank secret values) and populate it with development-only
credentials. `.env` is git-ignored. Compose runs only the
application container; it does not start its own PostgreSQL. The container connects to the shared
`folio_pro` database on the Mac through `host.docker.internal:${POSTGRES_HOST_PORT}` (default
`5432`), so that PostgreSQL server must be running and accept connections from Docker Desktop.

Set `POSTGRES_PASSWORD` in `.env` if your server requires one, as well as the
`FMP_API_KEY`, `FINNHUB_API_KEY`, `NEWSAPI_KEY`, `ALPHA_VANTAGE_API_KEY`, and notification
credentials you use. Do not commit real credentials in `.env`.
Compose passes these environment variables to Spring Boot, where `application.yml` already maps them
to the corresponding settings. Database credentials also come from `.env`. The app container does not need `application-secrets.yml`; that file remains optional for direct
local Spring Boot runs and the separate Kubernetes setup. Only `.env.example` is tracked; keep real
values in the ignored `.env`. The template also
includes provider URLs/limits, WhatsApp enablement, provider timeout/retries, and
digest schedule/timezone settings. Leave defaults unchanged unless you want to override them.

Start the service from the repository root:

```bash
docker compose up --build -d
```

On your Mac, browse the data with DBeaver, pgAdmin, or IntelliJ's Database tool using `localhost`,
port `5432`, database `folio_pro`, user `folio_pro`, and schema `public`. With the PostgreSQL
command-line client:

```bash
psql -h localhost -p 5432 -U folio_pro -d folio_pro -c '\dt'
```

View logs with `docker compose logs -f news-scheduler`, stop with `docker compose down`, or restart
with `docker compose up -d`. `docker compose down` never touches database data, because the database
lives outside Compose. The Kubernetes configuration below targets the same database at
`host.docker.internal:5432/folio_pro`.

### CI image build and Kubernetes deployment

The GitHub Actions workflow runs Maven package/tests and builds the Docker image on pushes, pull
requests, and manual dispatches. Non-PR runs publish commit-tagged and `latest` images to GHCR.
Pushes to `main` and manual workflow runs also deploy to the `dev` namespace. Configure these
repository secrets before enabling deployment:

- `KUBE_CONFIG`: kubeconfig contents for the target cluster, with permission to manage the `dev`
  namespace and its Deployment, Service, Ingress, and Secret.
- `APP_SECRETS_YAML`: contents of the Spring `application-secrets.yml` file, including database
  credentials and provider keys.

The deployed image must be pullable by the cluster. Make the GHCR package public or configure an
image pull secret on the `dev` namespace. The existing manifests target the `dev` namespace, use
`host.docker.internal:5432/folio_pro` for PostgreSQL, and use an example ingress host; update these
for the cluster and database before deploying remotely. Keep the deployment at one replica because
each instance runs the scheduled digest job.

### Deploy to Docker Desktop Kubernetes

The `k8s/` manifests target the **Docker Desktop Kubernetes cluster on this Mac** and keep PostgreSQL
on the Mac host. The pod connects to `host.docker.internal:5432`; this requires PostgreSQL to accept
connections from Docker Desktop's Kubernetes network. Do not use this database arrangement for a
remote cluster. Keep the deployment at one replica because every application instance runs the
scheduled digest job; increasing replicas can send duplicate digests.

1. Enable Kubernetes in Docker Desktop and make sure its context is active:

   ```bash
   kubectl config use-context docker-desktop
   kubectl get nodes
   kubectl apply -f k8s/00-namespace.yaml
   ```

2. Build the image locally; Docker Desktop Kubernetes uses the image from the local Docker engine:

   ```bash
   mvn --batch-mode -DskipTests package
   docker build -t news-scheduler-svc:local .
   ```

3. Add your actual PostgreSQL credentials and provider/notification keys to the ignored
   repository-root `application-secrets.yml`. The deploy helper uploads this file as the Kubernetes
   Secret `news-scheduler-secrets`, mounted read-only at `/app/application-secrets.yml`. Spring
   already imports that path from `application.yml`, so no secret values are baked into the image or
   passed on the command line. The Deployment sets `SPRING_DATASOURCE_URL` to
   `jdbc:postgresql://host.docker.internal:5432/folio_pro` (the shared database); change that value in
   `k8s/deployment.yaml` if your database name or port differs.

   Put credentials under the same Spring property hierarchy used by the application, for example:

   ```yaml
   spring:
     datasource:
       username: "your-postgres-user"
       password: "your-postgres-password"
   fmp:
     api-key: "your-fmp-key"
   app:
     apis:
       finnhub-key: "your-finnhub-key"
       newsapi-key: "your-newsapi-key"
       alpha-vantage-key: "your-alpha-vantage-key"
   ```

   `application.yml` resolves environment variables such as `FMP_API_KEY` for local/Compose use;
   Kubernetes instead supplies the same Spring properties through the mounted secrets YAML file.

   Kubernetes Secrets are not encrypted just because their values are base64-encoded. This local
   workflow assumes a single-user Docker Desktop cluster; restrict cluster access and use a managed
   secret store for shared or production clusters.

4. Pass the local image tag to the helper. It verifies the Docker Desktop context and image,
   syncs the local secrets file into the cluster, applies the manifests, restarts/waits for the
   Deployment, and runs a readiness smoke test through a temporary port-forward on port `18080`:

   ```bash
   bash scripts/deploy-local-k8s.sh local
   kubectl get pods,services,ingress -n dev
   kubectl logs -n dev deployment/news-scheduler-svc
   ```

   Rebuild the image before redeploying after application changes. On every deployment, the helper
   refreshes the cluster Secret from the local file and restarts the pod, so updates to credentials
   are picked up. The deploy command fails if the readiness endpoint does not return a successful
   response. The Actuator dependency and health probe settings are already enabled in `pom.xml` and
   `application.yml`.

5. The ingress manifest expects an NGINX Ingress Controller with class `nginx`. If one is installed,
   map `dev.news-scheduler.example.com` to `127.0.0.1` in `/etc/hosts` on this Mac and open
   `http://dev.news-scheduler.example.com`. Otherwise, access the service without ingress:

   ```bash
   kubectl port-forward -n dev service/news-scheduler-svc 8080:80
   ```

   Then use `http://localhost:8080`. Readiness and liveness endpoints are at
   `/actuator/health/readiness` and `/actuator/health/liveness`.

### 1. Clone the Repository

```bash
git clone https://github.com/omkarchorghe16/news-scheduler-svc.git
cd news-scheduler-svc
```

### 2. Set Up Environment Variables

Export environment variables in your shell (Spring Boot does not automatically load a plain `.env` file):

```bash
# News APIs
export FINNHUB_API_KEY="your_finnhub_api_key"
export NEWSAPI_KEY="your_newsapi_key"

# Slack
export SLACK_WEBHOOK_URL="https://hooks.slack.com/services/YOUR/WEBHOOK/URL"
export SLACK_PORTFOLIO_WEBHOOK_URL="https://hooks.slack.com/services/YOUR/PORTFOLIO/URL"

# Telegram
export TELEGRAM_BOT_TOKEN="your_telegram_bot_token"
export TELEGRAM_CHAT_ID="your_telegram_chat_id"

# Twilio WhatsApp (optional; disabled by default)
export TWILIO_ACCOUNT_SID="your_account_sid"
export TWILIO_AUTH_TOKEN="your_auth_token"
export TWILIO_WHATSAPP_FROM="whatsapp:+1234567890"
export TWILIO_WHATSAPP_TO="whatsapp:+0987654321"
```

For local Maven or IDE runs, you can instead keep credentials in the ignored root-level
`application-secrets.yml`. Copy `application-secrets.example.yml` to `application-secrets.yml` and
fill in the values there. The application imports that file automatically when the `local` profile
is active (the default). Set IntelliJ's working directory to the repository root so the file is
found. Restart the application after changing secrets; Spring reads the file at startup. Never put
real credentials in `src/main/resources` or commit `application-secrets.yml`.

### 3. Build the Project

```bash
mvn clean install
```

### 4. Run Locally

**Run the application from Maven/IDE against the shared `folio_pro` database**
```bash
mvn spring-boot:run
```

The default Spring profile is `local`. The datasource defaults to
`jdbc:postgresql://localhost:5432/folio_pro` with user `folio_pro` (tables in `public`); set
`POSTGRES_URL`, `POSTGRES_USER`, or `POSTGRES_PASSWORD` only to override them.
`.env` is read by Docker Compose, not automatically by Maven or IDEs.
The application listens on `http://localhost:8082` by default; override this with `SERVER_PORT` if
needed. Docker Compose also exposes it at `http://localhost:8082`.

**Optional local schedule override (every 5 minutes)**
```bash
cp src/main/resources/application-local.yml application-local.yml
mvn spring-boot:run
```

### 5. Verify the Application

Check health endpoint:
```bash
curl http://localhost:8082/api/digest/health
```

Expected response:
```
Stock News Scheduler Service is running
```

## REST Endpoints

### Swagger / OpenAPI

With the application running on its default local port `8082`, browse to
`http://localhost:8082/swagger-ui/index.html` for interactive API documentation. The generated
OpenAPI JSON is available at `http://localhost:8082/v3/api-docs`.

### Stock Profiles

Fetch one or more symbols by POSTing a JSON list. Each provider has a separate endpoint:

```bash
curl -X POST http://localhost:8082/api/stocks/finnhub/profiles \
  -H "Content-Type: application/json" \
  -d '{"symbols":["AAPL","MSFT"]}'

curl -X POST http://localhost:8082/api/stocks/yahoo/profiles \
  -H "Content-Type: application/json" \
  -d '{"symbols":["AAPL","MSFT"]}'

curl -X POST http://localhost:8082/api/stocks/alphavantage/overview \
  -H "Content-Type: application/json" \
  -d '{"symbols":["AAPL","MSFT"]}'

curl -X POST http://localhost:8082/api/fmp/profiles \
  -H "Content-Type: application/json" \
  -d '{"symbols":["AAPL","MSFT"]}'
```

The Finnhub response includes company name, industry, IPO date, market capitalization (in millions),
logo, country, currency, exchange, shares outstanding, phone, and website. Finnhub's `stock/profile2`
does not provide a business summary. Yahoo Finance provides business summary, sector, industry,
employee count, company/address details, market capitalization, and the raw provider payload.
Alpha Vantage's OVERVIEW includes description, sector, industry, market capitalization, P/E, EPS,
margins, and other financial metrics when returned by the provider. Profiles are upserted by symbol
in provider-specific PostgreSQL tables.

FMP profiles use `FMP_API_KEY` and the `https://financialmodelingprep.com/stable` base URL by
default. You can override it with `FMP_BASE_URL`. The service enforces the configurable
`FMP_DAILY_REQUEST_LIMIT` (default 250 calls in a rolling 24-hour window), tracked in PostgreSQL.
Every `fmp_stock_profiles` row has `created_date` (set once, when the symbol is first saved) and
`updated_date` (set on every FMP refresh). Both `GET` and `POST /api/fmp/profiles` apply the same
per-symbol rule:

- `updated_date` is **today** → the saved row is returned from PostgreSQL; no FMP call, no quota used.
- Not updated today, or not saved yet → FMP is called **for that symbol only**, the row is upserted by
  ticker symbol (keeping its `id` and `created_date`), and the fresh record is returned.

"Today" is evaluated in `FMP_PROFILE_REFRESH_TIME_ZONE` (default `America/Chicago`), so host and
container runs agree on the date. Each refreshed symbol is saved as soon as it is fetched, so if a later
symbol fails, earlier ones are kept. The API key, US-exchange, and quota checks apply only when at least
one symbol needs an FMP call.
FMP Basic/free accounts are US-exchange-only; requests containing `.NS` or `.BO` are rejected by
default. Set `FMP_US_EXCHANGES_ONLY=false` only when your FMP plan supports those symbols.
Every FMP profile record includes the complete raw provider record in `raw_payload`.

`GET /api/fmp/profiles?symbols=AAPL,MSFT` takes symbols as a query parameter (comma-separated or
repeated, `?symbols=AAPL&symbols=MSFT`); `POST /api/fmp/profiles` takes `{"symbols": [...]}`. Both return
one full profile per symbol in request order. Because a stale symbol is refreshed from FMP, `GET` can
call FMP and write to PostgreSQL, and a symbol FMP does not know returns `404`.

```json
[
  {
    "id": 1,
    "symbol": "AAPL",
    "companyName": "Apple Inc.",
    "currency": "USD",
    "exchange": "NASDAQ Global Select",
    "industry": "Consumer Electronics",
    "marketCap": 3000000000000,
    "price": 210.5,
    "sector": "Technology",
    "country": "US",
    "description": "Company description...",
    "createdDate": "2026-09-29T23:34:18",
    "updatedDate": "2026-11-14T16:30:26"
  }
]
```

The response contains the persisted FMP profile fields; provider fields may be null, and
`rawPayload` contains the provider record. The Postman collection includes both the FMP upsert request
and the GET lookup request.
Finnhub requests are spaced to stay within the provider's 60-calls-per-minute limit for this endpoint.
Yahoo Finance is an unofficial API and may rate-limit requests or change its response format.
Alpha Vantage requests are limited to 25 per day and 5 per minute. The daily count is persisted in
PostgreSQL, and requests wait when the per-minute quota is reached. Each symbol consumes one call.
Create your own API key at [alphavantage.co](https://www.alphavantage.co/support/#api-key) and set
`ALPHA_VANTAGE_API_KEY` in your local environment or ignored `.env` file.
The Postman collection includes profile requests for all four providers and a validation test for an
empty symbol list. Import one collection, `postman/stock-news-scheduler.postman_collection.json`,
and the Local environment from `postman/`. The Local environment points to
`http://localhost:8082`; configure Finnhub, Alpha Vantage, and FMP API keys in the running application
to run Stock Profiles. The Yahoo request does not require a key.

The collection includes requests that create/update/delete database records, trigger digests and
notifications, and call provider APIs. Review the request before sending it because some requests
have database, notification, or provider side effects.

### India Stocks (Tapetide + Upstox)

All India endpoints are under `/api/india`. Tapetide responses are saved to `tapetide_stock_datasets`
(and quotes to `tapetide_stock_quotes`) and served from PostgreSQL while they are fresher than
`tapetide.quote-refresh-minutes` (default 60) or `tapetide.data-refresh-minutes` (default 1440). A
symbol Tapetide does not know returns **204 No Content** and is logged, not treated as an error.
Symbols are bare NSE tickers (`RELIANCE`, not `RELIANCE.NS`).

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/india/tapetide/search?query=&limit=` | Find a symbol by name, symbol, BSE code or ISIN (not saved) |
| GET | `/api/india/tapetide/quote/{symbol}` | Price, P/E (TTM), P/B, 52-week high/low |
| GET | `/api/india/tapetide/quotes?symbols=A,B` | Quotes for up to 200 symbols; unknown symbols are skipped |
| GET | `/api/india/tapetide/profile/{symbol}?include=ratings,peers` | Company overview, fundamentals, growth, pros/cons; `include` adds `technicals`, `ratings`, `peers`. Used by the Folio Pro India watchlist page |
| GET | `/api/india/tapetide/financials/{symbol}?section=&periods=` | Quarterly/annual P&L, balance sheet, cash flow, ratios |
| GET | `/api/india/tapetide/shareholding/{symbol}?type=quarterly` | Promoter, FII, DII and public holdings |
| GET | `/api/india/tapetide/forecasts/{symbol}` | Analyst estimates vs actuals, price target |
| GET | `/api/india/tapetide/history/{symbol}?days=&interval=daily` | Daily or weekly OHLCV with delivery % |
| GET | `/api/india/upstox/quotes?symbols=A,B&exchange=NSE` | Live quotes for up to 500 symbols, saved to PostgreSQL |
| GET | `/api/india/upstox/candles/{symbol}?exchange=NSE&unit=days&interval=1&from=&to=` | Historical candles, saved to PostgreSQL |

```bash
curl 'http://localhost:8082/api/india/tapetide/profile/RELIANCE?include=ratings,peers'
```

Postman requests are in the "India Stocks (Tapetide + Upstox)" folder of the collection.

### India NSE Symbols (stock + ETF ticker search)

Type-ahead data for an India stock/ETF dropdown. `NseSymbolSyncScheduler` downloads NSE's free,
official lists once a week (default **Sunday 06:00 Asia/Kolkata**) — no API key needed:

- Equities: `https://nsearchives.nseindia.com/content/equities/EQUITY_L.csv`
- ETFs: `https://nsearchives.nseindia.com/content/equities/eq_etfseclist.csv`

They are saved as `nse-equities.csv` and `nse-etfs.csv` in `nse.storage-directory` (default
`./data/nse`, git-ignored). Both files are replaced only after both downloads parse, so a failed or
blocked download keeps the previous lists. When the files are missing at startup (first run, new
container) they are downloaded once immediately (`nse.sync-on-startup`). The search endpoint reads
the CSV files and keeps the parsed rows in memory until a file changes.

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/india/nse/symbols?query=reli&type=EQUITY&limit=20` | Search by ticker or name, best matches first (exact ticker, ticker prefix, name prefix, word prefix, then contains). `type` is `EQUITY` or `ETF` (omit for both); `limit` 1–5000 (omit for all). Omit `query` for the full list. **503** until the lists have been downloaded once |
| POST | `/api/india/nse/sync` | Download both lists now; returns `{equities, etfs, syncedAt}`. **502** if NSE fails (previous lists kept) |

```bash
curl 'http://localhost:8082/api/india/nse/symbols?query=nifty&type=ETF&limit=10'
```

```json
[{"symbol":"NIFTYBEES","name":"Nippon India ETF Nifty 50 BeES","type":"ETF","series":null,"isin":"INF204KB14I2"}]
```

Symbols are bare NSE tickers (`RELIANCE`), the same format the Tapetide and Upstox endpoints take.
For a dropdown, debounce typing (~250 ms) and call with `limit=20`. Postman requests are in the
"India NSE Symbols" folder.

### Adding Stock Symbols

Add a list of ticker symbols to an existing sector. The service normalizes tickers, creates records
that do not already exist in that sector, and reports already-present tickers in `skipped`:

```bash
curl -X POST http://localhost:8082/api/stock-symbols \
  -H "Content-Type: application/json" \
  -d '{"sectorId":3,"tickers":["AAPL","MSFT","NVDA"]}'
```

The request requires a non-empty `tickers` array and an existing `sectorId`. The response contains
`created` stock records and a `skipped` ticker list. The existing `POST /api/stocks/bulk` endpoint
continues to support the same bulk-creation operation.

### On-Demand Triggers

#### Trigger Daily Digest
```bash
curl -X POST http://localhost:8082/api/digest/run
```

Response:
```json
{
  "message": "Daily digest executed successfully"
}
```

#### Health Check
```bash
curl http://localhost:8082/api/digest/health
```

Response:
```
Stock News Scheduler Service is running
```

## Configuration

All configuration is managed through `application.yml` and environment variables. Edit `src/main/resources/application.yml` to override defaults.

### Key Configuration Properties

CORS for browser clients (the Folio Pro Angular dev server):

```yaml
cors:
  # Comma-separated origins allowed to call /api/** (configured in config/WebConfig.java).
  allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:4200}
```

```yaml
app:
  notifications:
    slack:
      webhook-url: ${SLACK_WEBHOOK_URL}  # Main channel
      portfolio-webhook-url: ${SLACK_PORTFOLIO_WEBHOOK_URL}  # Portfolio channel
    telegram:
      bot-token: ${TELEGRAM_BOT_TOKEN}
      chat-id: ${TELEGRAM_CHAT_ID}
    whatsapp:
      enabled: false  # Disabled by default
      twilio-sid: ${TWILIO_ACCOUNT_SID}
      twilio-token: ${TWILIO_AUTH_TOKEN}
      from-number: ${TWILIO_WHATSAPP_FROM}
      to-number: ${TWILIO_WHATSAPP_TO}
  apis:
    finnhub-key: ${FINNHUB_API_KEY:}
    newsapi-key: ${NEWSAPI_KEY:}
    finnhub-base-url: ${FINNHUB_BASE_URL:https://finnhub.io/api/v1}
    yahoo-base-url: ${YAHOO_FINANCE_BASE_URL:https://query1.finance.yahoo.com}
    alpha-vantage-key: ${ALPHA_VANTAGE_API_KEY:}
    alpha-vantage-base-url: ${ALPHA_VANTAGE_BASE_URL:https://www.alphavantage.co/query}
    timeout-seconds: 5  # HTTP timeout
    max-retries: 1  # Retry attempts
  schedule:
    cron-expression: "0 0 9 * * MON-FRI"  # 9 AM weekdays
    timezone: "America/Chicago"
```

NSE symbol lists (no key; see "India NSE Symbols" above):

```yaml
nse:
  equity-list-url: ${NSE_EQUITY_LIST_URL:https://nsearchives.nseindia.com/content/equities/EQUITY_L.csv}
  etf-list-url: ${NSE_ETF_LIST_URL:https://nsearchives.nseindia.com/content/equities/eq_etfseclist.csv}
  storage-directory: ${NSE_STORAGE_DIRECTORY:./data/nse}
  timeout-seconds: ${NSE_TIMEOUT_SECONDS:30}
  sync-on-startup: ${NSE_SYNC_ON_STARTUP:true}
```

In Docker/Kubernetes the files live in the container (`/app/data/nse`) and are re-downloaded on
startup after a restart; mount a volume at that path to keep them.

### Stock News API Keys

The service supports two news providers. At least one key is required to receive news:

- **Finnhub (recommended for stock-market news):** create a free account at
  [finnhub.io](https://finnhub.io/register), then copy the API key from the dashboard.
  This provider supplies general market news and company news for the configured watchlist.
- **NewsAPI (optional):** create an account at [newsapi.org](https://newsapi.org/register),
  then copy the key from the account page. This provider supplies search-based market news.

Do not paste keys into Git. Configure them as environment variables before starting Spring Boot:

```bash
export FINNHUB_API_KEY="paste-your-finnhub-key-here"
export NEWSAPI_KEY="paste-your-newsapi-key-here" # optional
export ALPHA_VANTAGE_API_KEY="paste-your-alpha-vantage-key-here" # optional
mvn spring-boot:run
```

For local development, you can instead create an untracked `application-secrets.yml` in the project
root. This filename is in `.gitignore` and is imported by the application. Example:

```yaml
app:
  apis:
    finnhub-key: "your-finnhub-key"
    newsapi-key: "your-newsapi-key"
    alpha-vantage-key: "your-alpha-vantage-key"
  notifications:
    telegram:
      bot-token: "your-telegram-bot-token"
      chat-id: "your-telegram-chat-id"
spring:
  datasource:
    username: "your-postgres-user"
    password: "your-postgres-password"
```

Never commit this local file or package it into the application image.

In IntelliJ IDEA, open **Run | Edit Configurations**, select `StockNewsApplication`, and add
`POSTGRES_USER=...`, `POSTGRES_PASSWORD=...`, and any provider variables such as
`FINNHUB_API_KEY=...`, `NEWSAPI_KEY=...`, or `ALPHA_VANTAGE_API_KEY=...` under **Environment variables**.
The placeholders in `src/main/resources/application.yml` read these values automatically.

The default schedule runs once at 09:00 America/Chicago on weekdays; it is not a continuous
real-time stream. Finnhub is the primary source for stock news, while NewsAPI free plans may
have provider-specific delays and usage limits.

## Scheduling

The service runs on a configurable cron expression. Default is **9:00 AM on weekdays (Mon-Fri) in America/Chicago timezone**.

To change the schedule, modify the cron expression in `application.yml`:

```yaml
app:
  schedule:
    cron-expression: "0 0 9 * * MON-FRI"  # Cron format: second, minute, hour, day, month, day-of-week
    timezone: "America/Chicago"
```

The NSE symbol-list download has its own weekly schedule:

```yaml
nse:
  schedule:
    cron-expression: "0 0 6 * * SUN"  # NSE_SCHEDULE_CRON_EXPRESSION
    timezone: "Asia/Kolkata"           # NSE_SCHEDULE_TIMEZONE
```

### Common Cron Examples

- `0 0 9 * * MON-FRI` - 9 AM on weekdays
- `0 0 9,17 * * MON-FRI` - 9 AM and 5 PM on weekdays
- `0 */30 * * * *` - Every 30 minutes (for testing)
- `0 0 8 * * *` - Daily at 8 AM

## Deduplication

The service stores SHA-256 hashes of sent article URLs in PostgreSQL.
Articles sent within the last **3 days** are excluded from future digests.

Old records are automatically cleaned up on application startup. To manually clean up:

Edit `DigestService.java` and adjust:
```java
private static final int DEDUP_DAYS = 3;  // Change to desired number
```

## Database setup

The application reads and writes data through Spring Data JPA repositories in
`model`, using the PostgreSQL datasource. Hibernate creates or updates
the PostgreSQL tables from the JPA entities at startup. H2 is used only for automated tests.

The `folio_pro` database and `folio_pro` role are created by investment-management-svc's setup; this
service needs no database of its own. If you are setting up from scratch:

```sql
CREATE USER folio_pro WITH PASSWORD 'set-a-local-password';
CREATE DATABASE folio_pro OWNER folio_pro;
```

The application writes to the database in the effective `POSTGRES_URL`
(including overrides in `application-secrets.yml`); inspect that exact database in pgAdmin or
IntelliJ. Do not put credentials into committed configuration.

Hibernate creates or updates these tables in the shared `public` schema from the corresponding JPA
entities:

| Table | Contents |
| --- | --- |
| `FINNHUB_STOCK_PROFILES` | Finnhub company profile fields, market capitalization, and raw provider JSON |
| `YAHOO_STOCK_PROFILES` | Yahoo company summary, sector, industry, employee count, and raw provider JSON |
| `ALPHA_VANTAGE_OVERVIEWS` | Alpha Vantage description and financial metrics |
| `ALPHA_VANTAGE_API_CALLS` | Persisted quota history for the daily/minute provider limits |
| `FMP_STOCK_PROFILES` | FMP profile fields and complete raw provider records |
| `FMP_API_CALLS` | FMP requests tracked for the rolling daily quota |
| `SENT_ARTICLES` | Article URL hashes used for digest deduplication |

### Browse PostgreSQL data in IntelliJ IDEA or pgAdmin

Add a PostgreSQL data source matching the effective `POSTGRES_URL`, username, and password from the
running application. Refresh its `public` schema (shared with folio-pro's tables), then open the
profile tables or use the SQL console:

```sql
SELECT * FROM finnhub_stock_profiles ORDER BY updated_at DESC;
SELECT * FROM yahoo_stock_profiles ORDER BY updated_at DESC;
SELECT * FROM alpha_vantage_overviews ORDER BY updated_at DESC;
SELECT * FROM fmp_stock_profiles ORDER BY updated_date DESC;
SELECT called_at FROM fmp_api_calls ORDER BY called_at DESC;
SELECT * FROM sent_articles ORDER BY sent_at DESC;
```

## Running Tests

```bash
mvn test
```

### Test Coverage

- **Unit Tests**:
  - `DigestFormatterTest`: Message formatting for each channel
  - `DigestServiceTest`: Deduplication logic
  - `SlackNotifierTest`, `TelegramNotifierTest`, `WhatsAppNotifierTest`: Notifier configuration

- **Integration Tests**:
  - `StockNewsApplicationTest`: Spring context loads successfully
  - `DigestControllerTest`: REST endpoints respond correctly

## Logging

The application writes logs to the console (stdout). Profile endpoint logs include provider, request
symbol count, and saved/returned record counts; provider failures log the symbol and exception type.
API credentials, authorization data, and full provider payloads must not be logged.

The digest workflow logs events similar to:

```
2024-09-13 09:00:00 - Starting daily digest job
2024-09-13 09:00:01 - Fetching US market news...
2024-09-13 09:00:02 - Fetched 15 US news items
2024-09-13 09:00:02 - Fetching India market news...
2024-09-13 09:00:03 - Fetched 12 India news items
2024-09-13 09:00:03 - After dedup: 10 US items, 8 India items
2024-09-13 09:00:04 - Sending US digest to Telegram
2024-09-13 09:00:04 - Telegram message sent successfully
2024-09-13 09:00:05 - Sending India digest to Telegram
2024-09-13 09:00:05 - Telegram message sent successfully
2024-09-13 09:00:06 - Daily digest job completed
```

Configure log levels in `application.yml`:

```yaml
logging:
  level:
    root: INFO
    com.stocknews: DEBUG  # Change to INFO for less verbose logs
```

## Troubleshooting

### Application won't start
- Check all required environment variables are set
- Verify Java 21 is installed: `java -version`
- Check Maven version: `mvn -version` (3.6+)

### Digests not sending
- Verify webhook URLs and API keys in environment variables
- Check logs for HTTP errors
- Confirm firewall allows outbound HTTPS connections

### No articles found
- Verify Finnhub and NewsAPI keys are valid
- Check API rate limits (free tiers have limits)
- Confirm market symbols in watchlist are correct

### Deduplication not working
- Check that `SentArticleRepository` is saving records to PostgreSQL

### WhatsApp messages not sending
- Confirm WhatsApp is enabled: `app.notifications.whatsapp.enabled=true`
- Verify Twilio credentials are correct
- Check if message template is approved (Twilio Sandbox requirement)
- Ensure both `from-number` and `to-number` are in WhatsApp format: `whatsapp:+1234567890`

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                   DigestScheduler (cron)                    │
└─────────────┬───────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────────┐
│                    DigestService                            │
│  (Orchestrates: Fetch → Dedup → Format → Notify)          │
└─────────┬──────────┬────────────────────────────────────────┘
          │          │
    ┌─────▼──┐  ┌────▼────┐
    │Finnhub │  │ NewsAPI │
    │Client  │  │ Client  │
    └────────┘  └─────────┘
          │          │
          └────┬─────┘
                    ▼
          ┌──────────────────┐
          │  NewsItem List   │
          └────────┬─────────┘
                   │
                   ▼
          ┌──────────────────┐
          │ Dedup (SHA-256)  │◄──────┐
          │ vs PostgreSQL    │       │
          └────────┬─────────┘       │
                   │         ┌───────┴────────┐
                   │         │  SentArticle   │
                   │         │  Repository    │
                   ▼         └────────────────┘
          ┌──────────────────┐
          │ DigestFormatter  │
          │ (Markdown/JSON)  │
          └────────┬─────────┘
                   │
         ┌─────────┼─────────┬──────────┐
         ▼         ▼         ▼          ▼
       Slack   Telegram  WhatsApp  REST Response
```

## Contributing

To extend the application:

1. **Add a new news source**: Implement `NewsSourceClient` interface
2. **Add a new notifier**: Implement `Notifier` interface
3. **Change schedule**: Modify cron expression in config
4. **Document changes**: Update the README and directly affected docs, and review all
   `.github/skills/*/SKILL.md` files; update every skill affected by the implementation or shared
   project conventions.

## License

MIT

## Support

For issues and feature requests, open an issue on GitHub.
