# Documento de diseño: API de días hábiles y festivos de España

Nombre: **Laborable** (alternativas consideradas: [naming.md](naming.md)). Estado: borrador v1,
actualizado el 2026-11-21 (importación de festivos locales para todas las comunidades).
Pila: Java 17, Spring Boot 4.1, PostgreSQL 17, Flyway, Docker.

## 1. Problema

En España los festivos se fijan en tres niveles: el Estado, la comunidad autónoma y el municipio.
Por eso es difícil responder a «¿qué fecha es 10 días hábiles después de hoy en Vilafranca?».
Lo necesitan las gestorías y los abogados (plazos de presentación), los departamentos de personal
(vacaciones), las tiendas en línea (fechas de entrega) y los desarrolladores de otras aplicaciones.

Las APIs existentes (ver [research.md](research.md)) se detienen en el nivel autonómico. No
incluyen los festivos municipales, que son justo los que rompen el cálculo de plazos.

## 2. Objetivos y no objetivos

**Objetivos v1**
- Responder a 4 consultas: lista de festivos, «¿es día hábil?», «fecha tras N días hábiles» y «cuántos días hábiles hay entre dos fechas».
- Conocer los festivos de tres niveles: estatales, autonómicos (19 territorios) y municipales (de cualquier comunidad).
- Cargar los datos desde fuentes oficiales abiertas (BOE, datos abiertos de Cataluña y del País Vasco) o con un importador genérico para el resto de comunidades.
- No dar nunca una respuesta «silenciosamente errónea»: si faltan datos, la API lo dice de forma explícita.

**No objetivos v1**
- Asesoramiento jurídico. Es una herramienta de consulta, no un dictamen.
- Fiestas escolares y bancarias, horarios comerciales.
- Importadores automáticos para las comunidades sin fuente abierta legible por máquina (se cargan con el importador genérico; ver §9).
- Festivos insulares de Canarias, de Val d'Aran y territoriales del País Vasco (ver §4.4).

## 3. Usuarios y escenarios

| Usuario | Escenario |
|---|---|
| Gestoría / abogado | «Presentar un documento 10 días hábiles después del 2027-03-24 en Barcelona» → fecha |
| RR. HH. | Cuántos días hábiles hay en unas vacaciones del 2027-08-02 al 2027-08-20 en Madrid |
| Tienda en línea | Si hay reparto en Vilafranca ese día; fecha de entrega a 3 días hábiles |
| Desarrollador | Si hoy es festivo en el municipio X (para la lógica de su aplicación) |

## 4. Reglas del dominio

### 4.1. Jerarquía de festivos
Un festivo se aplica a un lugar si es estatal, O de la comunidad del lugar, O del municipio del lugar.

| Scope | Fuente | Ejemplo |
|---|---|---|
| `NATIONAL` | Fila del BOE con la misma marca `*` o `**` en las 19 columnas (`*` = `NATIONAL_FIXED`, `**` = `NATIONAL_SUBSTITUTABLE`) | 1 de enero, 6 de enero, 1 de mayo |
| `REGIONAL` | Resto de celdas marcadas: `**` solo en parte de las comunidades y `***` (festivo propio de la comunidad) | Jueves Santo, Sant Joan |
| `LOCAL` | Datos abiertos o boletín de la comunidad (hasta 2 festivos al año por municipio) | Festa major |

`**` y `***` se guardan como `REGIONAL` por separado para cada comunidad. Así se tratan las
sustituciones: una comunidad puede no tener Jueves Santo o tener su propio Lunes de Pascua.

### 4.2. Qué es un «día hábil» (v1)
Día hábil = no sábado, no domingo y no festivo en el lugar. Coincide con el art. 30 de la Ley 39/2015
para los plazos administrativos. Otros regímenes (judicial con agosto inhábil, jornada laboral de
cinco o seis días) se dejan para perfiles futuros (hoja de ruta).

### 4.3. Semántica de las operaciones
- `add(date, n)`: la fecha de partida **no se cuenta**; se cuenta desde el día siguiente. `n < 0` va hacia atrás. `n = 0` devuelve `date`.
- `between(from, to)`: por defecto `from` incluido y `to` excluido (parámetros `includeFrom`, `includeTo`).
- Coherencia que verifican las pruebas: `add(a, n) = d` ⇒ `between(a, d, includeFrom=false, includeTo=true) = n` para `n > 0`.
- Límites: `|days| ≤ 3660`, rango de `between` ≤ 3660 días (protección frente a abusos).

### 4.4. Cobertura de datos (principio clave)
Un error silencioso es peor que uno explícito. Por eso:
- Si no hay datos oficiales de la comunidad para el año → **422** `data-not-available`, no «no hay festivos».
- Si la consulta es de un municipio y los festivos locales de su comunidad no están cargados para ese año → `warnings: LOCAL_HOLIDAYS_NOT_LOADED`.
- Si no se indica región → solo se aplican los `NATIONAL` y se devuelve `warnings: NO_REGION`.
- Para `CN` (islas), `CT` (Val d'Aran) y `PV` (festivos territoriales de Araba, Bizkaia y Gipuzkoa) → `SUBREGIONAL_HOLIDAYS_NOT_SUPPORTED`.

## 5. Identificación del lugar

- Comunidad: código ISO 3166-2:ES (`CT`, `MD`, `AN`, `IB`, …), 19 valores (17 comunidades + `CE`, `ML`).
- Municipio: el **código INE (5 dígitos)** es la clave canónica. También se acepta el nombre completo
  o un slug (`vilafranca-del-penedes`), normalizado (mayúsculas, diacríticos, guiones).
- No se adivinan prefijos. `vilafranca` devuelve **404** con `suggestions`, y no «la única
  coincidencia de hoy»: de lo contrario la respuesta cambiaría al cargar Mallorca. Un nombre exacto
  ambiguo devuelve **409** con `candidates`.
- Si solo se indica el municipio, la comunidad se deduce del prefijo INE (provincia → comunidad, 52 filas en la migración).
  Si se indica una comunidad que contradice la provincia → 400.

## 6. Arquitectura

```
 cliente ──HTTP──► [controladores Spring MVC] ──► [servicios] ──► [repositorios / JdbcClient] ──► PostgreSQL
                    web/                          service/ + domain/          persistence/
 admin ──X-API-Key──► /admin/holidays/import ──► ImportService ──► BOE | Cataluña | País Vasco | genérico
                                                      │ lista de hosts permitidos, HTTPS, límite de tamaño
                                                      └► LocalHolidayRules (validación común de festivos locales)
```

Paquetes (`app.laborable.*`):
- `domain`: tipos puros y `BusinessDayCalculator` (sin Spring, se prueba sin base de datos).
- `persistence`: repositorios con JdbcClient (lectura, upsert, `ON CONFLICT`).
- `service`: `PlaceResolver`, `HolidayService`, `BusinessDayService`.
- `web`: controladores, DTO como record, `ApiExceptionHandler` (RFC 9457 problem+json).
- `imports`: `BoeAnnexParser`; lectores de festivos locales `CataloniaLocalParser` y `EuskadiLocalParser`; `LocalHolidayRules` (reglas comunes); `ImportService`; `RemoteFetcher`.
- `config`: seguridad, caché, CORS, propiedades.

Por qué JDBC y no JPA: el modelo es plano, hacen falta `ON CONFLICT` e inserciones masivas y no hay
relaciones uno-a-muchos que justifiquen la carga. Menos magia y arranque más rápido.

## 7. Modelo de datos (Flyway `V1`, `V2`)

```
region(code PK, name)                         -- 19 filas, datos iniciales
province(code PK, name, region_code FK)       -- 52 filas, datos iniciales
municipality(ine_code PK, name, name_normalized, province_code FK)
import_run(id PK, source, year, source_ref, dry_run, status, started_at, finished_at, stats jsonb)
holiday(id PK, holiday_date, scope, kind, region_code?, municipality_ine?, name, import_run_id FK)
   CHECK: NATIONAL sin región ni municipio; REGIONAL solo con región; LOCAL solo con municipio
   UNIQUE (holiday_date, scope, coalesce(region_code,''), coalesce(municipality_ine,''))
coverage(year, region_code, level REGIONAL|LOCAL, status, import_run_id)  PK(year, region_code, level)
```

Índice por `holiday_date`; la consulta de un año es una consulta por rango. El resultado se cachea
(Caffeine, clave `year|region|ine`) y se vacía tras cada importación.

## 8. API

Común: `GET`, JSON, fechas ISO `YYYY-MM-DD`, errores `application/problem+json`.
`region` y `municipality` son opcionales, ver §4.4.

| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/holidays?year=2027&region=CT&municipality=08305` | `{year, region, municipality, coverage, holidays[{date,name,scope,kind}], warnings[]}` |
| GET | `/business-days/is-working?date=2027-03-29&region=CT` | `{date, working, reason: WORKING/WEEKEND/HOLIDAY, holiday?, warnings[]}` |
| GET | `/business-days/add?date=…&days=10&region=…` | `{start, days, result, holidaysSkipped[{date,name}], warnings[]}` |
| GET | `/business-days/between?from=…&to=…&includeFrom=true&includeTo=false&region=…` | `{from, to, count, holidaysInRange[], warnings[]}` |
| POST | `/admin/holidays/import` | `{runId, source, year, dryRun, status, parsed, notes[], anomalies[]}` |
| GET | `/actuator/health`, `/v3/api-docs`, `/swagger-ui.html` | de servicio |

Códigos de error: 400 (entrada no válida, rango), 401 (admin), 404 (lugar), 409 (municipio ambiguo
o falta el BOE del año), 422 (no hay datos del año o la fuente no produjo festivos válidos), 500
(sin detalles internos).

## 9. Importación de datos

`POST /admin/holidays/import`. Cuerpo común: `{"source":…,"year":2027,"dryRun":true}`. Fuentes:

| `source` | Qué carga | Campos extra |
|---|---|---|
| `BOE` | Festivos estatales y autonómicos | `boeId` (p. ej. `BOE-A-2026-21254`) |
| `CATALONIA_LOCAL` | Festivos locales de Cataluña (`CT`) | — |
| `EUSKADI_LOCAL` | Festivos locales del País Vasco (`PV`) | — |
| `LOCAL_GENERIC` | Festivos locales de **cualquier** comunidad | `region` (p. ej. `AN`) y `entries[{ine, municipality, date, name}]` (máx. 20 000) |

### 9.1. BOE
(Verificado el 2026-11-15 con BOE-A-2026-21254, `https://www.boe.es/diario_boe/xml.php?id=…`.)
El anexo de la resolución es una tabla: filas «número + nombre», 19 columnas de comunidades y
`*`/`**`/`***` en las celdas. El análisis se hace por posición de columna, los nombres de las
comunidades se normalizan y los meses son filas separadoras. Las notas bajo la tabla (islas de
Canarias, Val d'Aran) no se importan, sino que se devuelven en `notes`.
Validación: el año del texto coincide con el solicitado, exactamente 19 columnas y como máximo 14
fechas distintas por comunidad (si no, aviso). Guardado: en una transacción se borran los
`NATIONAL`/`REGIONAL` del año y se insertan los nuevos (una resolución corregida sustituye a la
anterior); después se actualiza `coverage`.

### 9.2. Festivos locales: reglas comunes (`LocalHolidayRules`)
Todos los importadores locales (Cataluña, País Vasco y genérico) pasan por las mismas reglas:
1. Hace falta el BOE de ese año cargado **para esa comunidad** (si no, 409 `boe-required`). Las filas con una fecha que ya es festivo estatal o de la comunidad se descartan como duplicados.
2. Se descartan las filas con INE que no sea de 5 dígitos, con INE de una provincia de **otra comunidad** (`municipality_of_another_region`), con año distinto al pedido, con nombre sin letras o con patrón de prueba, o sin nombre de municipio.
3. **Invariante legal** (RD 2001/1983, art. 46; Estatuto de los Trabajadores, art. 37.2): ≤ 2 festivos locales por municipio. Si hay más, todas las filas del municipio pasan a `anomalies` y no se importan.
4. Si la fuente no produce ningún festivo válido → 422 `no-local-holidays` y **no se toca nada** (no se borra lo ya cargado).
5. Se reemplazan de forma atómica los festivos locales de esa comunidad y año, y se guarda `coverage(year, región, LOCAL)`. El importador genérico debe recibir la lista **completa** de la comunidad y el año, porque el reemplazo es total.

### 9.3. Estado de las fuentes por comunidad
Verificado el 2026-11-21.

| Comunidad | Importador | Fuente |
|---|---|---|
| Cataluña (`CT`) | Automático `CATALONIA_LOCAL` | Socrata `analisi.transparenciacatalunya.cat/resource/b4eh-r8up.json` |
| País Vasco (`PV`) | Automático `EUSKADI_LOCAL` | Open Data Euskadi, «Calendario laboral de Euskadi» (JSON, CC BY 4.0). Verificado con el fichero de 2026 |
| Castilla y León (`CL`) | `LOCAL_GENERIC` | Existe CSV abierto (CC BY 4.0) en datosabiertos.jcyl.es, pero redirige a `transparencia.jcyl.es`, cuya cadena de certificados TLS no se pudo verificar. Sin adaptador hasta confirmar formato y certificado |
| Madrid (`MD`), Andalucía (`AN`) y resto | `LOCAL_GENERIC` | Boletines oficiales (BOCM, BOJA, DOGV, BOIB…). No se ha verificado una fuente abierta legible por máquina para toda la comunidad |

Añadir un adaptador nuevo consiste en escribir un lector que convierta las filas de la fuente en
`LocalEntry` y llamar a `LocalHolidayRules.apply`; la validación, la persistencia y la cobertura ya
están hechas.

**Datos de Cataluña.** Problemas conocidos del conjunto: los años antiguos contienen basura de
prueba (`Prueba 2`, `sal`, `abc`), fechas de toda Cataluña (`Any nou`, `La Diada`) y filas con
`pedania ≠ 000` (entidades sub-municipales, descartadas como `sub_municipal_entity`). Para 2026:
2794 filas, 909 municipios, todas con el nombre `Festiu local`.

**Datos del País Vasco.** Un único JSON con filas de toda la comunidad (`municipalityEs = "CAE"`),
filas territoriales (Araba, Bizkaia, Gipuzkoa, sin código de municipio) y una fila por festivo
municipal. El código de municipio es el número INE dentro de la provincia, de modo que
INE = prefijo de provincia (`01`, `20`, `48`) + código con tres cifras. Los festivos territoriales
(p. ej. San Ignacio, 31 de julio, en Bizkaia y Gipuzkoa) no se pueden asignar a municipios
concretos con estos datos: se devuelven en `notes` y la API avisa con
`SUBREGIONAL_HOLIDAYS_NOT_SUPPORTED`. Algunos municipios alaveses aparecen con decenas de filas
(concejos): superan el máximo legal y quedan en `anomalies`.

**Seguridad de la importación:** la URL se construye en el servidor a partir de una lista fija de
hosts permitidos (`www.boe.es`, `analisi.transparenciacatalunya.cat`, `opendata.euskadi.eus`);
`boeId` se valida con una expresión regular; las redirecciones están desactivadas; solo HTTPS;
tiempo máximo y límite de tamaño de la respuesta (no hay SSRF posible). El XML se analiza con DTD
y entidades externas desactivados. En la carga genérica los datos llegan en el cuerpo (máx. 20 000
filas) y pasan por la misma validación. `dryRun=true` muestra lo que se cargaría y no escribe nada
(salvo el registro en `import_run`).

## 10. Seguridad
- `GET` públicos sin autenticación; `/admin/**` solo con `X-API-Key`. La clave se toma de `LABORABLE_ADMIN_API_KEY`,
  comparación en tiempo constante, mínimo 24 caracteres. Si no hay clave, el admin queda desactivado (fail-closed).
- Sin estado, sin cookies ni CSRF. Cabeceras de seguridad de Spring Security. CORS solo para la lista `LABORABLE_CORS_ORIGINS`.
- Todo el SQL está parametrizado. Los errores no revelan rutas, trazas ni SQL.
- La limitación de tasa (rate limiting) no está implementada en la aplicación en v1; se aplica en el proxy inverso (Caddy/nginx). Es una carencia conocida, registrada en PROGRESS.
- Los secretos solo en variables de entorno; en el repositorio solo `.env.example` sin valores.

## 11. Pruebas
- Unitarias: calculadora (fines de semana, festivos, hacia atrás, cambio de año, identidad add/between), analizadores (con el fixture real del BOE 2027 y fixtures de Cataluña y País Vasco) y las reglas comunes de festivos locales (otra comunidad, límite de dos, duplicados, nombres, año).
- Integración: Spring + PostgreSQL real (zonky embebido, sin Docker): importación → consultas → errores → autorización admin → importación del País Vasco y genérica (Andalucía) con sus validaciones.
- Objetivo de cobertura: ~80 % global, ≥ 90 % en `domain` e `imports`. Informe JaCoCo (`target/site/jacoco`).
- Antes de una versión: revisión independiente por otro agente.

## 12. Despliegue
`Dockerfile` multietapa (Maven → JRE 17, sin root). `docker-compose.yml`: `postgres:17` con healthcheck + aplicación.
Configuración mediante `.env`. Primer arranque: base de datos vacía (Flyway crea el esquema y los datos de referencia) y después importación mediante el endpoint admin (`scripts/import-demo.sh`).

## 13. Etapas
1. Esqueleto: Spring Boot, Flyway, Docker, health. 2. Lectura: `/holidays` + lugar. 3. Cálculo: los tres `business-days`.
4. Importación BOE + Cataluña + admin. 5. Festivos locales de todas las comunidades (País Vasco + genérico). 6. Endurecimiento: revisión, rate limit, documentación, versión 0.1.0.
Perspectivas y análisis de carencias: [roadmap-analysis.md](roadmap-analysis.md).

## 14. Riesgos y preguntas abiertas
| Riesgo | Mitigación |
|---|---|
| Cambia el formato del BOE | Analizador con comprobaciones estrictas (19 columnas, año) + prueba con fixture; el error es visible, no silencioso |
| Faltan datos municipales de comunidades sin fuente abierta verificada | Avisos explícitos (`warnings`); importador genérico para cargarlos desde el boletín oficial; adaptadores por comunidad en la hoja de ruta |
| Datos locales cargados con el importador genérico sin revisar | Mismas reglas de validación, `dryRun`, `anomalies` y `notes` en la respuesta; la carga exige la lista completa |
| Responsabilidad legal por los plazos | Aviso legal, `source` en cada festivo, versionado de datos en la hoja de ruta |
| Licencia de los datos abiertos | Verificar las condiciones de BOE, Generalitat y Open Data Euskadi (CC BY, requiere atribución) antes del uso comercial (**pregunta a la propietaria**) |
| Nombre, dominio, marca | No se han comprobado, ver naming.md (**pregunta a la propietaria**) |
