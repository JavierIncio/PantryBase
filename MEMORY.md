# MEMORY.md — Estado del proyecto PantryBase

> **Este fichero es el punto de partida de cada sesión.**
> Al empezar a trabajarlo, léelo. Al terminar una sesión, actualízalo con el estado real.
> Máximo 100 líneas. Es un resumen operativo, no un diario. Última actualización: **18 de octubre de 2026** (H9-1/H9-4 commiteados; bloques H9-2 y H9-3 cerrados, sin commitear).

## Dónde estamos

**Hito cerrado y commiteado: H8 (Social y personalización).** H0–H6 y H8 **cerrados**; **H7 retirado** (sin IA, no reintroducir sin decisión explícita). H8 está en `a2ebdc9` (pushado).

**Verificación 18-oct-2026:** backend **321 tests verdes** (`.\mvnw.cmd -o test`, BUILD SUCCESS); frontend **389 tests verdes (35 ficheros)** con `npm test`. Migraciones Flyway hasta **V23**. Ojo: informes stale en `target/surefire-reports` si se compilan con `-q`; el total fiable es la línea Maven final (`Tests run: ...`).

**Único hito vivo: H9 — Producción y endurecimiento.** Commiteados: **H9-1** rate limit por IP (`fd7317d` + docs `6658cc5`), **H9-4** EXPLAIN + test de concurrencia (`b3bd001`). **Cerrados SIN commitear (18-oct):** **H9-2** alertas (5 reglas + Alertmanager→Mailpit + gauge de sesiones colgadas) y **H9-3** dashboards Grafana provisionados. **Pendiente en H9 (bola 2):** Vault/CI, auditoría de llamadas externas, backups + DR, pipeline de promoción, servidor Jenkins, medición de caché Redis.

## Qué está construido

- **H0 Fundación:** Spring Boot 4.1.1 / Java 25 runtime, Angular 22 + Material, Vitest, Docker Compose, Jenkins, Prometheus + Grafana, Mailpit.
- **H1 Auth:** registro/login, JWT + refresh en cookie, contraseña olvidada, OAuth2 Google, rate limit (Redis).
- **H2 Catálogo y unidades:** USDA FDC (materializado, caché/frescura, revalidado contra la API real en H2-B-7), motor de unidades/densidades por clase curada.
- **H3 Despensa:** `pantry_items` (bloqueo optimista), ajustes con signo (libro mayor), métricas, alta por diálogo.
- **H4 Recetas:** búsqueda TheMealDB + cobertura binaria honesta (líneas sin emparejar persistidas), alta propia, alérgenos opt-in, recomendación por despensa, refresco por TTL, pre-flight.
- **H5 Modo cocina:** sesiones, descuento al completar (409 de carencia + `acceptShortage`), consumo manual, métricas.
- **H6 Temas:** `ThemeId` (`data-theme`), M3 por paleta, sin FOUC; reset a azul al logout (H8-0).
- **H8 Social y personalización:** favoritos/guardadas/historial, `published_at` + `owner_id`, `recipe_diets` (`V22`) y filtro `diet`; endpoints `/favorite|/saved|/publish|/published|/mine`, stats de perfil. Frontend: barra superior global, «Mi recetario» (3 pestañas), Historial, perfil con anclas, texto visible en inglés.
- **H9-1 Rate limit anónimo (commiteado):** cubo por IP para peticiones sin autenticar, política `anonymous` + `strict-paths`, IP de `remoteAddr`; 7 tests.
- **H9-2 Alertas (sin commitear):** 5 reglas en `infra/prometheus/rules.yml` (ApiDown, ApiHighErrorRate, FdcQuotaExhausted, FdcServingStale, CookingSessionsStuck); Alertmanager `v0.34.1` enruta por correo a Mailpit (e2e verificado: firma, correo y resolución); gauge `pantry.cooking.sessions.stuck` en `CookingService` (scrape-time, umbral `STUCK_AFTER` 1 h) + test.
- **H9-3 Dashboards (sin commitear):** `infra/grafana/dashboards/pantry-health.json` y `pantry-operations.json`, provider en `infra/grafana/provisioning/dashboards/`, datasource por variable `${datasource}`.
- **H7 retirado:** el hueco lo cubre `GET /api/recipes/recommend` sin LLM.

## Decisiones ya tomadas (no volver a abrir sin motivo)

- **H3:** cantidad en `GRAM`/`UNIT`; una fila por ingrediente; ajuste con signo y asiento; negativo `409`; `@Version`.
- **H4:** cobertura binaria; `strict` exige `==1.0`, si no `threshold` (50); `/recommend` fuerza `threshold=0`; materialización en `REQUIRES_NEW`.
- **H5:** completar descuenta con `origin=COOKING_SESSION`; primer 409 con plan; aceptar baja a 0 sin asiento; bajar de cero 409.
- **H6:** un `data-theme` en `<html>`; dos tiempos sin FOUC.
- **H8:** favoritos ≠ guardadas (dos tablas/UI); publicación = flag `published_at`; stats en paquete `social`; `owner_id`; dieta materializada de nutrientes (`V22`).
- **H9-1:** dos tiers sobre el mismo script Lua; sujeto `user:{id}` o `ip:{addr}`; IP solo de `remoteAddr`.
- **H9-2:** el gauge se evalúa **en el scrape** (COUNT por pull, sin `@Scheduled`); la alerta de sesiones colgadas se apoya en `CookingSessionRepository.countByStatusAndStartedAtBefore` con umbral 1 h. Alcance de alertas ajustado: «circuito abierto» y «cuota LLM» ya no existen (H7); su hueco lo cubren las alertas FDC.
- **H9-3:** dashboards versionados como JSON provisionado; datasource por variable `${datasource}` (nunca `uid` fijo — ver cuellos). Latencia en **medias** porque los timers son summaries sin histograma; no se inventan paneles de series inexistentes.
- **H9-4 sin V24:** `idx_pantry_items_user_id` redundante (deuda anotada, no retirar aún); concurrencia se prueba con invariantes, no estadísticas.
- **Queries JPQL explícitas** cuando el destino no es la entidad; **tests contra endpoints reales** cuando hay lazy.

## Próximos pasos

1. **Commit del bloque H9-2+H9-3** (proponer `feat(infra): alert rules, alertmanager and provisioned grafana dashboards (H9-2, H9-3)`), toca backend (gauge+test), `infra/**` y docs.
2. **H9 bola 2 (con el usuario):** Vault/CI para secretos, auditoría de llamadas externas, backups de PostgreSQL + DR, pipeline de promoción con gates, servidor Jenkins; medir caché Redis.
3. **Deuda conocida** (no bloquea): emparejado TheMealDB, `cookie.secure=false` y OAuth2 sin verificar *antes* de desplegar, bundle 650 kB, `idx_pantry_items_user_id` redundante, `OR`/`LIKE '%…%'` a re-mirar con escala.

## Cuellos de botella conocidos

- **Boot 4 desactiva la exportación de métricas en tests** (`MetricsContextCustomizerFactory` / `DisableMetricsExportContextCustomizer` vía `spring-boot-starter-actuator-test`): en test el `MeterRegistry` es `SimpleMeterRegistry` y `/actuator/prometheus` **no existe** (404/401), pero sí en runtime. Los tests de métricas deben leer del `MeterRegistry` inyectado, no del endpoint.
- **Grafana 13.0.9:** poner `uid:` en un datasource provisionado rompe el arranque (`Datasource provisioning error: data source not found`); usar variable `${datasource}` en los paneles.
- **Emparejado TheMealDB por tokens:** nombres USDA verbose no casan literal; plurales reducidos: heurística.
- **OAuth2 sin verificar en producción** y `cookie.secure=false` hasta desplegar.
- **`infra/.env` es local** (nombres en `.env.example`); `GRAFANA_PASSWORD` sale de ahí. Deuda: Vault/CI, auditoría de llamadas externas, coste por usuario en rate limit.
- **EXPLAIN a escala pequeña:** los planes actuales (16 líneas) prueban usabilidad de índices, no comportamiento a 10k filas.
- **Bundle:** aviso a 650 kB compressed; comparar bundles sin tocar el árbol del usuario con `git worktree` + junction a `node_modules` (nunca `[DirectoryInfo]::Delete()`).
- **PowerShell:** no existen `rg`/`grep`/`head`/`tail`/`sed`/`sort`; usar `Select-String`, `Get-Content -TotalCount/-Tail`, `Select-Object`. `"\uFFFD"` no es escape Unicode.
- **Vitest:** `npx vitest run` falla (`describe is not defined`); usar `npm test` o `npx ng test --watch=false`.
- **Commits:** H8 se cerró como monolito (`a2ebdc9`); no reescribir historia ya pushada — commitear incrementalmente.

## Cómo trabajar aquí

- Comandos: backend `.\mvnw.cmd -o test`/`verify` en `backend/pantry-api`; frontend `npm test` / `npm run build`; infra `docker compose up -d` en `infra/` (usa `--build` para la API). Validar Prometheus/Alertmanager con `docker run --rm --entrypoint promtool ... check rules` y `--entrypoint amtool ... check-config`.
- Documentación: `README.md`, `docs/ROADMAP.md`, `docs/VERSIONS.md` (consultar antes de proponer deps), `docs/FEATURE_GUIDE.md`.
- Reglas de trabajo e idioma: `AGENTS.md`. El frontend solo lo toca el subagente `frontend`.
- Al cerrar cada fase: tests verdes + docs actualizadas + commit convencional (proponer, nunca push sin pedirlo).
