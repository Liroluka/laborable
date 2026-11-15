# Laborable API (робоча назва)

API робочих днів і свят Іспанії: державні, автономій і муніципальні (у v1 муніципальні свята є для Каталонії).
Java 17, Spring Boot 4.1, PostgreSQL 17, Flyway, Docker.

Документація: [дизайн](docs/design.md), [аналіз аналогів і джерел](docs/research.md), [вимоги](docs/requirements.md),
[варіанти назв](docs/naming.md), [що ще додати](docs/roadmap-analysis.md), [стан робіт](PROGRESS.md).

## Запуск
```bash
cp .env.example .env        # заповніть DB_USER, DB_PASSWORD, LABORABLE_ADMIN_API_KEY (≥ 24 символи)
docker compose up --build
LABORABLE_ADMIN_API_KEY=... ./scripts/import-demo.sh   # завантажити дані 2027
```

## Приклади
```bash
curl "localhost:8080/holidays?year=2027&region=CT&municipality=08305"
curl "localhost:8080/business-days/is-working?date=2027-03-29&region=CT"
curl "localhost:8080/business-days/add?date=2027-03-24&days=10&municipality=08305"
curl "localhost:8080/business-days/between?from=2027-03-22&to=2027-04-02&region=MD"
```
Swagger UI: `/swagger-ui.html`, OpenAPI: `/v3/api-docs`, health: `/actuator/health`.

Муніципалітет задається **INE-кодом** (5 цифр) або точною назвою (`vilafranca-del-penedes`).
Якщо для року немає даних, API повертає 422, а не «свят немає». Деталі та семантика: [docs/design.md](docs/design.md).

## Розробка і тести
```bash
./mvnw verify        # тести (справжній PostgreSQL через embedded, Docker не потрібен) + покриття JaCoCo + Spotless
./mvnw spotless:apply
```
Звіт покриття: `target/site/jacoco/index.html`.

## Дані та застереження
Дані беруться з BOE і відкритих даних Generalitat de Catalunya. Це довідковий сервіс, не юридична консультація:
для юридичних строків звіряйтеся з офіційними бюлетенями.
