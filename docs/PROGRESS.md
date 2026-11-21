# PROGRESS

**Objetivo:** API de días hábiles y festivos de España (Java 17, Spring Boot 4.1, PostgreSQL, Docker). Plan: [design.md](design.md).

## Etapas
- [x] Etapa 0: investigación, documento de diseño, nombres, análisis (2026-11-15)
- [x] Etapa 1: esqueleto (Spring Boot, esquema Flyway, Docker, health) (2026-11-15)
- [x] Etapa 2: lugar y `GET /holidays` (2026-11-15)
- [x] Etapa 3: `business-days` (is-working, add, between) (2026-11-15)
- [x] Etapa 4: importación BOE + Cataluña + autorización admin (2026-11-15)
- [x] Etapa 5: festivos locales de todas las comunidades: reglas comunes, País Vasco automático, importador genérico, documentación en español (2026-11-21)
- [ ] Etapa 6: endurecimiento (revisión independiente, rate limit en el proxy, comprobación de Docker, versión 0.1.0)

## Estado
`./mvnw verify`: 51 pruebas en verde (unitarias + integración sobre PostgreSQL real), Spotless limpio.
Toda la documentación está en español.

## Próximos pasos
1. Instalar Docker y comprobar `docker compose up --build` + `scripts/import-demo.sh` con el BOE real.
2. Revisión independiente por otro agente (requisito de CLAUDE.md antes de una versión), hasta 2 rondas de correcciones.
3. Importación real de Cataluña y País Vasco en red (el BOE de 2026 y el fichero de Euskadi de 2027, cuando se publique) y comprobación de `HttpRemoteFetcher`.
4. Adaptador de Castilla y León cuando se pueda verificar el CSV y el certificado de `transparencia.jcyl.es`.
5. Callejero INE y años provisionales (ver roadmap-analysis).

## Decisiones
- JDBC (JdbcClient), no JPA: modelo plano, `ON CONFLICT`, inserciones masivas.
- La clave del municipio es el código INE; deliberadamente no hay búsqueda por prefijo (design §5).
- Un festivo es estatal si el BOE lo marca igual en las 19 columnas (`*` o `**`); si no, `REGIONAL` por comunidad.
- Pruebas sobre PostgreSQL real con zonky embebido: no hay Docker en la máquina de desarrollo.
- Festivos locales: un único conjunto de reglas (`LocalHolidayRules`) para todas las comunidades; cada fuente solo aporta un lector de filas. Un import sin festivos válidos se rechaza (422) para no borrar datos ya cargados.
- No se escribe un adaptador sin haber visto el formato real de la fuente (Castilla y León queda pendiente); mientras tanto, `LOCAL_GENERIC`.
- Los festivos territoriales del País Vasco no se asignan a municipios (no hay lista completa de municipios); se informan en `notes` y con `SUBREGIONAL_HOLIDAYS_NOT_SUPPORTED`.
- El proyecto vive en su propio repositorio (github.com/Liroluka/laborable).

## Problemas conocidos y preguntas a la propietaria
- Docker y Maven del sistema no están instalados: `Dockerfile`/compose **no se han ejecutado** (la compilación pasa con `./mvnw`).
- `HttpRemoteFetcher` no se ha probado contra BOE, Socrata ni Open Data Euskadi en vivo (los analizadores se probaron con el fixture real del BOE 2027, un fixture sintético de Cataluña con la estructura real y un fixture sintético de Euskadi con la estructura del fichero real de 2026).
- La limitación de tasa solo está en el proxy, no en la aplicación.
- Falta la revisión independiente.
- Licencias del BOE, la Generalitat y Open Data Euskadi (CC BY, atribución), marca/dominio y nombre definitivo sin resolver (aprobado: Laborable).
- Los festivos insulares de Canarias, de Val d'Aran y territoriales del País Vasco no están soportados (se informan en `notes`/`warnings`).
- Fuera de Cataluña y País Vasco, las comunidades dependen de que alguien aporte la lista con `LOCAL_GENERIC`.

## Cómo ejecutar
`./mvnw verify` (pruebas + cobertura + Spotless). En local: `docker compose up --build` (hace falta `.env`, ver `.env.example`).
