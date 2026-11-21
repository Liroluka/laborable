# Changelog

## [0.1.0] (en curso)
- Añadido: `GET /holidays`, `GET /business-days/{is-working,add,between}`, `POST /admin/holidays/import`.
- Añadido: importación del BOE (tabla de festivos de las comunidades) y de los datos abiertos de Cataluña (festivos locales) con validación.
- Añadido: esquema Flyway, datos de referencia de comunidades y provincias, Docker/Compose, OpenAPI, health.
- Añadido: importación de festivos locales para todas las comunidades: reglas comunes (`LocalHolidayRules`), fuente `EUSKADI_LOCAL` (Open Data Euskadi) y fuente `LOCAL_GENERIC` para cualquier comunidad.
- Cambiado: una importación local sin festivos válidos se rechaza (422) en lugar de vaciar lo ya cargado; la importación local exige el BOE de la comunidad y el año.
- Cambiado: aviso `SUBREGIONAL_HOLIDAYS_NOT_SUPPORTED` también para el País Vasco (festivos territoriales).
- Cambiado: toda la documentación está en español.
