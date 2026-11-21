# Análisis de alternativas y fuentes de datos (2026-11-15, ampliado el 2026-11-21)

Marcas: **[verificado]**: visto directamente durante la investigación; **[no verificado]**: de conocimiento general, confirmar antes de tomar decisiones.

## Alternativas

| Producto | Qué hace | Limitaciones para nuestra tarea |
|---|---|---|
| Nager.Date (API de código abierto) | Festivos por país; subdivisiones ISO 3166-2 de primer nivel **[verificado en la documentación]** | No tiene ciudades ni municipios **[verificado]**; sin aritmética de días hábiles **[no verificado]** |
| `holidays` de Python (biblioteca) | España con comunidades (`AN`, `CT`, `IB`…) **[verificado]** | Es una biblioteca, no un servicio; contrastada oficialmente solo para 2010–2023 **[verificado]**; sin municipios |
| OpenHolidays API | Festivos de países de la UE con subdivisiones **[no verificado]** | Nivel municipal y aritmética **[no verificado]** |
| Calendarific / Abstract API Holidays | APIs comerciales de festivos, regiones españolas **[no verificado]** | Precio, límites, festivos municipales **[no verificado]** |
| Excel `NETWORKDAYS` / `WORKDAY` | Aritmética de días hábiles con una lista de festivos | El usuario reúne la lista de festivos a mano |

**Conclusión:** el nicho es **festivos municipales + aritmética de días hábiles en una sola API** para
España. Todos los productos que se pudieron comprobar terminan en el nivel autonómico.

## Fuentes de datos (verificadas el 2026-11-15, salvo indicación)

**1. BOE: resolución de la Dirección General de Trabajo sobre las fiestas del año**
- 2027: BOE-A-2026-21254, resolución de 8.10.2026, publicada el 13.10.2026.
- XML: `https://www.boe.es/diario_boe/xml.php?id=BOE-A-2026-21254`. API abierta de sumarios: `https://www.boe.es/datosabiertos/api/boe/sumario/YYYYMMDD` (JSON).
- Anexo: tabla de 19 columnas (17 comunidades + Ceuta + Melilla); códigos `*` estatal no sustituible, `**` estatal sustituible, `***` propio de la comunidad.
- Las notas bajo la tabla contienen festivos subregionales: islas de Canarias (Decreto 115/2026), Val d'Aran (Orden EMT/52/2026).
- Particularidades de 2027: el 15.08 es domingo, por lo que algunas comunidades tienen el 16.08 o una sustitución propia (p. ej. Extremadura 11.10).

**2. Datos abiertos de Cataluña: «Calendari de festes locals»**
- Socrata: `https://analisi.transparenciacatalunya.cat/resource/b4eh-r8up.json` (SODA, filtro `$where`, `$limit`).
- Campos: `any_calendari`, `data`, `ajuntament_o_nucli_municipal`, `codi_municipi_ine`, `pedania`, `festiu`.
- 115 006 filas, años 2012–2026. Hay basura de prueba en años antiguos (`Prueba 2`, `sal`, `abc`, etc.) y fechas de toda Cataluña (`Any nou`, `La Diada`).
- 2026: 2794 filas, 909 municipios, todas `Festiu local`. Ejemplo: Vilafranca del Penedès (INE 08305) tiene 2026-08-31 y 2026-09-01.
- Los datos de 2027 aún no están publicados (a 2026-11-15 el máximo es 2026).

**3. Open Data Euskadi: «Calendario laboral de Euskadi»** (verificado el 2026-11-21 con el fichero de 2026)
- Ficha: `https://opendata.euskadi.eus/catalogo/-/calendario-laboral-de-euskadi-para-el-2026/`. Licencia Creative Commons Reconocimiento (CC BY). Formatos: CSV, JSON, XML, XLSX, iCal; también hay una API REST.
- JSON: `https://opendata.euskadi.eus/contenidos/ds_eventos/calendario_laboral_2026/opendata/calendario_laboral_2026.json` (UTF-8; el año va en el nombre).
- Campos: `date` (`yyyy/MM/dd`), `descripcionEs`, `descriptionEu`, `municipalityEs`, `MunicipalityEu`, `territory`, `municipalitycode` (número, nulo en las filas generales y territoriales), `latwgs84`, `lonwgs84`.
- 298 filas en 2026: 12 de toda la comunidad (`municipalityEs = "CAE"`), 3 territoriales (Bizkaia, Gipuzkoa, Araba) y 283 municipales. `municipalitycode` es el número INE dentro de la provincia (INE = `01`/`20`/`48` + tres cifras).
- Algunos códigos concentran muchas filas (p. ej. un código alavés con 24), lo que indica entidades sub-municipales o concejos: el límite legal de dos las deja como anomalías.

**4. Otras comunidades** (verificado el 2026-11-21)
- **Castilla y León:** el conjunto «Fiestas locales: Calendario de Fiestas de Carácter Local» (datosabiertos.jcyl.es, CC BY 4.0) ofrece CSV anuales (2024–2026) con municipio, provincia y código INE; la propia ficha avisa de fechas en blanco o repetidas del año anterior. La descarga redirige a `transparencia.jcyl.es`, cuyo certificado no se pudo verificar desde nuestro entorno, así que no hay adaptador todavía **[sin verificar el formato]**.
- **Madrid:** el portal del Ayuntamiento (datos.madrid.es) publica el calendario laboral solo de la ciudad. Para el resto de municipios, los festivos locales se publican en el BOCM; no se encontró un conjunto abierto único.
- **Andalucía:** los festivos locales se publican cada año por resolución de la Dirección General de Trabajo en el BOJA, con un anexo por municipio; la Junta ofrece una consulta web, pero no se encontró un conjunto de datos abiertos.
- **Resto:** no se ha localizado una fuente abierta legible por máquina para toda la comunidad **[no verificado]**; se cargan con `LOCAL_GENERIC`.

## Base jurídica (pendiente de revisión por un abogado)
- RD 2001/1983, arts. 45–46: fiestas estatales y autonómicas; **hasta 2 fiestas locales al año** por municipio.
- Estatuto de los Trabajadores, art. 37.2: hasta 14 días festivos al año.
- Ley 39/2015, art. 30: los plazos administrativos en días son días hábiles, es decir, sin sábados, domingos ni festivos.

## Soluciones tipo que adoptamos
La clave del municipio es el código INE; la respuesta dice explícitamente la cobertura de los datos;
la importación es idempotente y se comprueba con invariantes legales, comunes a todas las comunidades.
