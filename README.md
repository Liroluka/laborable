# Laborable

[![CI](https://github.com/Liroluka/laborable/actions/workflows/ci.yml/badge.svg)](https://github.com/Liroluka/laborable/actions/workflows/ci.yml)

API REST de **días hábiles y festivos en España** a nivel estatal, autonómico (17 comunidades
autónomas más Ceuta y Melilla) y **municipal**. Responde, por ejemplo, a «¿qué fecha es 10 días
hábiles después de hoy en Vilafranca del Penedès?» teniendo en cuenta los tres niveles.

Java 17 · Spring Boot 4.1 · PostgreSQL 17 · Flyway · Docker · OpenAPI

> Los festivos locales se cargan **por comunidad autónoma**: Cataluña y País Vasco tienen importador
> automático desde datos abiertos, y cualquier otra comunidad se carga con el importador genérico
> (ver [fuentes de festivos locales](docs/design.md#9-importación-de-datos)). Si para una comunidad
> y año no hay festivos locales cargados, la API lo dice en el campo `warnings` en lugar de
> ignorarlos en silencio.

## Por qué

En España los festivos se fijan en tres niveles: el Estado, cada comunidad autónoma y cada
municipio (hasta dos fiestas locales al año). Calcular plazos legales o de entrega depende, por
tanto, de *dónde* se esté. Las APIs de festivos existentes se detienen en el nivel autonómico.
Laborable añade el nivel municipal y la aritmética de días hábiles por encima.

## Endpoints

| Método | Ruta | Para qué sirve |
|---|---|---|
| GET | `/holidays?year=2027&region=CT&municipality=08305` | Festivos de un lugar |
| GET | `/business-days/is-working?date=2027-03-29&region=CT` | ¿Es hábil esta fecha? |
| GET | `/business-days/add?date=2027-03-24&days=10&municipality=08305` | Fecha tras N días hábiles (negativo, hacia atrás) |
| GET | `/business-days/between?from=2027-03-22&to=2027-04-02&region=MD` | Número de días hábiles en un rango |
| POST | `/admin/holidays/import` | Importar festivos: BOE, Cataluña, País Vasco o carga genérica (clave de API) |

Documentación interactiva en `/swagger-ui.html`, OpenAPI en `/v3/api-docs`, estado en `/actuator/health`.

```bash
curl "localhost:8080/business-days/add?date=2027-03-24&days=1&region=CT"   # -> 2027-03-25
curl "localhost:8080/business-days/add?date=2027-03-24&days=1&region=MD"   # -> 2027-03-29 (Jueves Santo)
```

Decisiones de diseño que conviene conocer:

- Un **municipio** se identifica por su código INE (5 dígitos) o por su nombre exacto
  (`vilafranca-del-penedes`). Nunca se adivinan prefijos, así que las respuestas no cambian al
  cargar más datos.
- **Sin huecos silenciosos:** un año sin datos oficiales devuelve `422`, no «no hay festivos».
- `add` no cuenta la fecha de partida; `between` incluye `from` y excluye `to` por defecto.
- Los festivos proceden de fuentes oficiales: la resolución del BOE de fiestas laborales (estatales
  y autonómicas) y, para las locales, los datos abiertos de la Generalitat de Catalunya y de Open
  Data Euskadi; el resto de comunidades se cargan con el importador genérico.
- Todos los importadores locales aplican las mismas reglas: código INE válido de la comunidad
  indicada, fecha dentro del año, nombre real, sin repetir festivos de toda la comunidad y un
  máximo de dos fiestas locales por municipio (art. 37.2 del Estatuto de los Trabajadores).

## Ejecutar

```bash
cp .env.example .env     # define DB_USER, DB_PASSWORD, LABORABLE_ADMIN_API_KEY (mín. 24 caracteres)
docker compose up --build
LABORABLE_ADMIN_API_KEY=... ./scripts/import-demo.sh   # carga los datos de 2027
```

### Cargar festivos locales de otra comunidad

Antes hay que importar el BOE del año (los festivos autonómicos). Después, para cualquier
comunidad (aquí Andalucía, `AN`), con la lista **completa** de esa comunidad y año:

```bash
curl -X POST localhost:8080/admin/holidays/import \
  -H "X-API-Key: $LABORABLE_ADMIN_API_KEY" -H "Content-Type: application/json" \
  -d '{"source":"LOCAL_GENERIC","year":2027,"region":"AN","dryRun":true,
       "entries":[{"ine":"41091","municipality":"Sevilla","date":"2027-05-30","name":"Fiesta local"}]}'
```

`dryRun:true` valida y muestra el resultado sin guardar nada. Para Cataluña y País Vasco basta
`{"source":"CATALONIA_LOCAL","year":2027}` o `{"source":"EUSKADI_LOCAL","year":2027}`.

## Desarrollo

```bash
./mvnw verify            # pruebas sobre un PostgreSQL embebido real (sin Docker), cobertura y formato
./mvnw spotless:apply    # formatear
```

Informe de cobertura: `target/site/jacoco/index.html`.

## Documentación

- [Documento de diseño](docs/design.md): reglas del dominio, modelo de datos, API, importadores, seguridad
- [Investigación de fuentes de datos y alternativas](docs/research.md)
- [Requisitos](docs/requirements.md) · [Análisis de la hoja de ruta](docs/roadmap-analysis.md)
- [Estado del proyecto](docs/PROGRESS.md) · [Documentación por etapas](DOCS.md) · [Cambios](CHANGELOG.md)

## Aviso legal

Datos de referencia procedentes de fuentes públicas; no constituyen asesoramiento jurídico.
Verifique los plazos legales en los boletines oficiales.
