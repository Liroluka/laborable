# Qué más añadir: análisis

Valoración: **Valor** (para el usuario), **Esfuerzo** (S ≤ 1 día, M ≤ 1 semana, L > 1 semana). Prioridad P1 son las próximas versiones.

## P1. Sin esto el producto no es fiable

| Función | Para qué | Valor | Esfuerzo |
|---|---|---|---|
| **Adaptadores automáticos de festivos locales por comunidad** (hecho: Cataluña, País Vasco y carga genérica para cualquiera; pendiente: BOCM, BOJA, DOGV, BOIB…, Castilla y León cuando se verifique su CSV) | Evitar la carga manual de la lista; hoy el resto de comunidades depende del importador genérico | Alto | L |
| **Callejero de municipios del INE** (≈ 8 100 registros, descarga del INE) | Búsqueda por nombre y validación del código INE en todas las comunidades; hoy un municipio solo existe tras cargar sus festivos | Alto | S |
| **Islas de Canarias, Val d'Aran y territorios del País Vasco** | El BOE y Open Data Euskadi lo dan aparte; hace falta una tabla `area` + correspondencia municipio→área | Medio | M |
| **Festivos provisionales** para años sin resolución (algoritmo de Pascua + fechas fijas), con estado en la respuesta | Fechas de 2028+ sin 422, pero con la marca «no oficial» | Alto | M |
| **Versionado de datos y consulta «a fecha de»** (`asOf`) | Un abogado necesita reproducir la respuesta que dio la API el día de la presentación | Alto | M |
| **Limitación de tasa + claves de API para consumidores** (cuotas, claves, contabilidad) | Protección frente a abusos; base de la monetización | Alto | M |
| **Vigilancia de la frescura de los datos** (métricas «año X importado», alerta antes del 1 de octubre) | La resolución del año siguiente sale en octubre; no dejarla pasar | Medio | S |

## P2. Aumenta claramente el valor

| Función | Para qué | Valor | Esfuerzo |
|---|---|---|---|
| **Perfiles de semana laboral**: `ADMINISTRATIVE` (lun–vie), `LABOR_MON_SAT`, `JUDICIAL` (agosto inhábil y otras excepciones; **confirmar con un abogado**) | Cada régimen da fechas distintas; los plazos judiciales difieren | Alto | M |
| **Calculadora de plazos de la Ley 39/2015** (días, meses, años; «desde el día siguiente»; traslado al siguiente hábil) | Cubre el escenario principal de la gestoría | Alto | M |
| `GET /business-days/next` y `/previous` (día hábil más cercano) | Necesidad típica de las tiendas en línea | Medio | S |
| **Puentes**: `GET /bridges?year=&region=` | Particularidad española muy popular; útil para RR. HH. y turismo | Medio | S |
| **Feed iCal** (`/holidays.ics?region=…`) | Suscripción en Google/Outlook Calendar sin código | Medio | S |
| **Consulta por lotes** `POST /business-days/batch` | Las tiendas en línea calculan cientos de fechas a la vez | Medio | S |
| **Nombres en varios idiomas** (es, ca, gl, eu, va, en) | Para Cataluña, Galicia, País Vasco y clientes internacionales | Medio | M |
| **Webhook / feed de cambios** («Madrid cambió un festivo de 2027») | Los sistemas de RR. HH. no deben perderse las correcciones | Medio | M |
| **SDK de cliente** (generados desde OpenAPI: Java, TS, Python) | Adopción rápida | Medio | S |

## P3. Más adelante

- Calendarios escolares (por comunidad), días festivos de centros.
- Días inhábiles de organismos concretos (p. ej. AEAT/Seguridad Social).
- Otros países (ES → Portugal, Andorra) con el mismo modelo de scope.
- Interfaz de administración: ver importaciones, corrección manual con auditoría.
- Página pública «estado de los datos» con la cobertura por años y comunidades.

## Aspectos no funcionales que faltan hoy
- **Limitación de tasa** y cuotas (hoy solo en el proxy).
- Métricas de Prometheus (latencia, errores 422, aciertos de caché) y registros estructurados.
- Copias de seguridad de PostgreSQL y política de migraciones para producción.
- Texto legal: condiciones de uso, aviso «no es asesoramiento jurídico», política de datos (RGPD; la API no recoge datos personales, solo IP en los registros).
- Licencias de las fuentes: BOE, datos abiertos de la Generalitat y Open Data Euskadi (CC BY); verificar los requisitos de atribución.
- Accesibilidad: Swagger UI es público; controlar que no muestre rutas admin en producción (desactivarlo si hace falta).

## Orden recomendado
1. Callejero INE → 2. Años provisionales → 3. Perfiles + calculadora de la Ley 39/2015 → 4. Límite de tasa + claves → 5. Adaptadores automáticos de las demás comunidades uno a uno (Madrid, Valencia, Andalucía) según el volumen de usuarios.
