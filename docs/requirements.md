# Requisitos

## Para quién
Gestorías y abogados (plazos), departamentos de personal (vacaciones), tiendas en línea (entregas), desarrolladores de otras aplicaciones.

## Escenarios principales (v1)
1. Obtener la lista de festivos de un año para una comunidad y/o un municipio.
2. Saber si una fecha concreta es hábil en un lugar.
3. Encontrar la fecha tras N días hábiles (hacia delante y hacia atrás).
4. Contar los días hábiles entre dos fechas.
5. La persona administradora carga los festivos desde el BOE, los datos abiertos de Cataluña y del País Vasco, o una lista propia para cualquier otra comunidad (con `dryRun`).

## Requisitos no funcionales
- p95 < 100 ms para `GET` con caché caliente; arranque < 15 s.
- Respuestas deterministas y reproducibles; la ausencia de datos es un error o un aviso explícito.
- Cobertura de pruebas ~80 % (≥ 90 % en `domain` e `imports`).
- Despliegue con Docker en un solo comando; configuración solo mediante variables de entorno.

## Fuera de v1
Perfiles de plazos judiciales, adaptadores automáticos para comunidades sin fuente abierta
verificada (se usa la carga genérica), festivos insulares (Canarias), de Val d'Aran y territoriales
del País Vasco, años provisionales, cuentas de cliente y cuotas, interfaz de administración, fiestas escolares.
Ver [roadmap-analysis.md](roadmap-analysis.md).
