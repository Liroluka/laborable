# Laborable

[![CI](https://github.com/Liroluka/laborable/actions/workflows/ci.yml/badge.svg)](https://github.com/Liroluka/laborable/actions/workflows/ci.yml)

REST API for **business days and public holidays in Spain**: national, regional (17 autonomous
communities plus Ceuta and Melilla) and municipal. Ask "what is the date 10 business days from
today in Vilafranca del Penedès?" and get an answer that accounts for all three levels.

Java 17 · Spring Boot 4.1 · PostgreSQL 17 · Flyway · Docker · OpenAPI

> Municipal holidays are currently loaded for **Catalonia** only. For other regions the API says so
> explicitly in a `warnings` field instead of silently ignoring local holidays.

## Why

In Spain, holidays are set at three levels: the state, each autonomous community and each
municipality (up to two local holidays per year). Computing legal and delivery deadlines therefore
depends on *where* you are. Existing holiday APIs stop at the region level. Laborable adds the
municipal level and the business-day arithmetic on top.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/holidays?year=2027&region=CT&municipality=08305` | Holidays for a place |
| GET | `/business-days/is-working?date=2027-03-29&region=CT` | Is this date a working day? |
| GET | `/business-days/add?date=2027-03-24&days=10&municipality=08305` | Date after N business days (negative goes back) |
| GET | `/business-days/between?from=2027-03-22&to=2027-04-02&region=MD` | Number of business days in a range |
| POST | `/admin/holidays/import` | Import holidays from BOE / Catalan open data (API key) |

Interactive docs at `/swagger-ui.html`, OpenAPI at `/v3/api-docs`, health at `/actuator/health`.

```bash
curl "localhost:8080/business-days/add?date=2027-03-24&days=1&region=CT"   # -> 2027-03-25
curl "localhost:8080/business-days/add?date=2027-03-24&days=1&region=MD"   # -> 2027-03-29 (Easter Thursday)
```

Design decisions worth knowing:

- A **municipality** is identified by its INE code (5 digits) or its exact name
  (`vilafranca-del-penedes`). Prefixes are never guessed, so answers do not change when more data is loaded.
- **No silent gaps:** a year without official data returns `422`, not "no holidays".
- `add` does not count the start date; `between` includes `from` and excludes `to` by default.
- Holidays are imported from official sources: the BOE resolution of fiestas laborales
  (national and regional) and the Generalitat de Catalunya open dataset (local).
  The importer validates the data (e.g. the legal limit of two local holidays per municipality).

## Run

```bash
cp .env.example .env     # set DB_USER, DB_PASSWORD, LABORABLE_ADMIN_API_KEY (min. 24 chars)
docker compose up --build
LABORABLE_ADMIN_API_KEY=... ./scripts/import-demo.sh   # load the 2027 data
```

## Develop

```bash
./mvnw verify            # tests on a real embedded PostgreSQL (no Docker needed), coverage, formatting
./mvnw spotless:apply    # format
```

Coverage report: `target/site/jacoco/index.html`.

## Documentation

- [Design document](docs/design.md) (in Ukrainian): domain rules, data model, API, importers, security
- [Research on data sources and alternatives](docs/research.md) (in Ukrainian)
- [Requirements](docs/requirements.md) · [Roadmap analysis](docs/roadmap-analysis.md) (in Ukrainian)

## Disclaimer

Reference data from public sources, not legal advice. Verify legal deadlines against the official bulletins.

---

## En español

**Laborable** es una API REST de **días hábiles y festivos en España** a nivel estatal, autonómico y
**municipal** (hasta dos fiestas locales por municipio y año). Responde, por ejemplo, a «¿qué fecha es
10 días hábiles después de hoy en Vilafranca del Penedès?». Los festivos se importan de fuentes
oficiales (resolución del BOE de fiestas laborales y datos abiertos de la Generalitat de Catalunya).
De momento los festivos locales están cargados solo para Cataluña; para otras comunidades la API lo
indica en el campo `warnings`. Es un servicio de referencia, no asesoramiento jurídico.
