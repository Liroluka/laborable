# DOCS: documentación por etapas

## Etapa 0: Investigación y diseño (2026-11-15)
Qué se hizo: documento de diseño, análisis de alternativas y fuentes, requisitos, opciones de nombre, análisis de carencias.
Para qué: fijar el modelo de festivos (3 niveles), la semántica de «día hábil» y los riesgos antes de escribir código.
Cómo está hecho: `docs/design.md` (principal), `docs/research.md` (hechos verificados sobre el BOE y los datos abiertos).
Importante: las licencias de las fuentes y la marca no están verificadas.

## Etapas 1–4: Implementación v1 (0.1.0)
Qué se hizo: API de festivos y aritmética de días hábiles, importación del BOE y de los festivos locales de Cataluña, acceso admin con clave, Docker.
Para qué: responder a «fecha tras N días hábiles en un municipio» con festivos estatales, autonómicos y locales.
Cómo está hecho: `domain` (calculadora), `persistence` (JdbcClient), `service`, `web`, `imports` (analizadores), Flyway `V1`/`V2`.
Importante: sin datos del año → 422; el municipio solo por código INE o nombre exacto; los festivos insulares de Canarias y de Val d'Aran no se importan (se informan en `notes`); la limitación de tasa está en el proxy.

## Etapa 5: Festivos locales de todas las comunidades (2026-11-21)
Qué se hizo: reglas de validación comunes (`LocalHolidayRules`), importador automático del País Vasco (`EUSKADI_LOCAL`) e importador genérico (`LOCAL_GENERIC`) para cualquier comunidad; toda la documentación traducida al español.
Para qué: que los festivos municipales no dependan de Cataluña; cualquier comunidad se puede cargar con las mismas garantías.
Cómo está hecho: los lectores (`CataloniaLocalParser`, `EuskadiLocalParser`, lista del cuerpo de la petición) producen `LocalEntry`; `LocalHolidayRules` valida (INE de la comunidad, año, nombre, duplicados, máximo 2 por municipio); `ImportService` reemplaza de forma atómica los festivos locales de la comunidad y el año y registra la cobertura.
Importante: hace falta el BOE del año de esa comunidad (409 si no); una fuente sin festivos válidos se rechaza sin tocar los datos; el importador genérico reemplaza la comunidad entera, por lo que se debe enviar la lista completa; Castilla y León, Madrid, Andalucía y el resto no tienen adaptador automático (ver design §9.3).

## Requiere la participación de la propietaria
- Aprobar el nombre y comprobar dominio y marca.
- Verificar las licencias del BOE, la Generalitat y Open Data Euskadi antes del uso comercial.
- Instalar Docker y comprobar `docker compose up --build` (no estaba en la máquina de desarrollo).
- Decidir de dónde se obtienen los festivos locales de las comunidades sin fuente abierta (boletines oficiales) y quién los carga.
