# MEMORY.md — Estado del proyecto PantryBase

> **Este fichero es el punto de partida de cada sesión.**
> Al empezar a trabajarlo, léelo. Al terminar una sesión, actualízalo con el estado real.
> Máximo 100 líneas. Es un resumen operativo, no un diario. Última actualización: **19 de octubre de 2026** — **proyecto cerrado** (H0–H6, H8, H9; H7 retirado).

## Dónde estamos

**Cierre del proyecto (19-oct-2026):** todos los hitos ejecutables están cerrados y commiteados; H9 quedó **cerrado con salvedades** (el trabajo no hecho está anotado como deuda, no abandonado en silencio). No hay hito vivo ni trabajo de código pendiente acordado.

**Verificación de cierre (19-oct-2026):** backend **325 tests verdes** con `.\mvnw.cmd -o verify` (BUILD SUCCESS, gate JaCoCo met); frontend **389 tests verdes (35 ficheros)** con `npm test` y `npm run build` sin avisos de budget; `promtool check rules` (5 rules) y `amtool check-config` OK. Migraciones Flyway hasta **V23**. Ojo: informes stale en `target/surefire-reports` si se compila con `-q`; el total fiable es la línea Maven final.

**Git:** H8 (`a2ebdc9`) y todo H9 + cierre commiteados; push a `origin/main` realizado el 19-oct-2026. No reescribir historia ya pushada.

## Qué está construido

- **H0 Fundación:** Spring Boot 4.1.1 / Java 25 runtime, Angular 22 + Material, Vitest, Docker Compose, Jenkins (Jenkinsfile local; **servidor CI sin desplegar**), Prometheus + Grafana + Alertmanager, Mailpit.
- **H1 Auth:** registro/login, JWT + refresh en cookie, contraseña olvidada, OAuth2 Google, rate limit (Redis).
- **H2 Catálogo y unidades:** USDA FDC (materializado, caché/frescura, revalidado contra la API real en H2-B-7), motor de unidades/densidades por clase curada.
- **H3 Despensa:** `pantry_items` (bloqueo optimista), ajustes con signo (libro mayor), métricas, alta por diálogo (H3-4).
- **H4 Recetas:** búsqueda TheMealDB + cobertura binaria honesta, alta propia, alérgenos opt-in, recomendación por despensa, refresco por TTL, pre-flight.
- **H5 Modo cocina:** sesiones, descuento al completar (409 de carencia + `acceptShortage`), consumo manual, métricas.
- **H6 Temas:** `ThemeId` (`data-theme`), M3 por paleta, sin FOUC; reset a azul al logout (H8-0).
- **H8 Social:** favoritos/guardadas/historial, `published_at` + `owner_id`, `recipe_diets` (`V22`) y filtro `diet`, «Mi recetario», perfil con stats.
- **H9 Producción (cerrado con salvedades):** H9-1 rate limit anónimo por IP (`fd7317d`), H9-2 alertas (5 reglas + Alertmanager→Mailpit + gauge de sesiones colgadas), H9-3 dashboards provisionados (Health + Operations), H9-4 EXPLAIN + test de concurrencia (`b3bd001`); H9-2+H9-3 en `a3f1a0d`.
- **Cierre 19-oct:** `docs/RUNBOOK.md` (operación, checklist «antes de desplegar», respaldo manual de Postgres), `COOKIE_SECURE` configurable (`app.security.cookie.secure`, default false) + `CookieServiceTest`, docs sincronizadas (README, FEATURE_GUIDE, VERSIONS, ROADMAP).

## Decisiones ya tomadas (no volver a abrir sin motivo)

- **H3:** cantidad en `GRAM`/`UNIT`; una fila por ingrediente; ajuste con signo; negativo `409`; `@Version`.
- **H4:** cobertura binaria; `strict` exige `==1.0`, si no `threshold` (50); `/recommend` fuerza `threshold=0`.
- **H5:** completar descuenta con `origin=COOKING_SESSION`; primer 409 con plan; bajar de cero 409.
- **H7:** retirado — no reintroducir IA sin decisión explícita.
- **H8:** favoritos ≠ guardadas; publicación = flag `published_at`; dieta materializada (`V22`).
- **H9-1:** dos tiers sobre el mismo Lua; sujeto `user:{id}` o `ip:{addr}`; IP solo de `remoteAddr` (sin proxy de confianza delante).
- **H9-2/H9-3:** gauge en scrape (sin `@Scheduled`); datasource por `${datasource}` (nunca `uid` fijo); latencia en medias (timers son summaries); no inventar paneles.
- **Cierre:** H9 se cierra **con salvedades** — auditoría externa, Vault/CI, medición de caché, backups+DR automatizados, pipeline de promoción y Jenkins quedan como deuda en `docs/ROADMAP.md` («Deuda técnica conocida»). Runbook = procedimiento manual, no automatización.

## Próximos pasos

1. **Ninguno acordado.** El proyecto está cerrado; si se retoma algo (p. ej. despliegue real), el punto de partida es `docs/ROADMAP.md` → «Deuda técnica conocida» y el checklist de `docs/RUNBOOK.md` §6 «antes de desplegar».
2. **Antes de cualquier desplegue real (documentado, no hecho):** `COOKIE_SECURE=true`, OAuth2 Google verificado con cliente real, Alertmanager→SMTP real, `server.forward-headers-strategy` tras proxy (si no, el rate limit por IP ve solo la IP del proxy), backups con prueba de restauración.

## Cuellos de botella conocidos

- **Boot 4 desactiva la exportación de métricas en tests:** en test no existe `/actuator/prometheus` (404/401); leer del `MeterRegistry` inyectado (patrón en `CookingControllerIntegrationTest`).
- **Grafana 13.0.9:** `uid:` fijo en un datasource provisionado rompe el arranque; usar `${datasource}`.
- **OAuth2 sin verificar con Google real** y receptor Alertmanager = Mailpit (ambos documentados en RUNBOOK §6).
- **Emparejado TheMealDB por tokens:** nombres USDA verbose no casan literal; heurística de plurales.
- **Deuda de escala:** `idx_pantry_items_user_id` redundante, `OR`/`LIKE '%…%'` candidatos a trigram/FTS; planes verificados solo a escala pequeña.
- **Bundle:** budget de aviso a 650 kB; build de cierre en 533 kB iniciales (128 kB transfer). Comparar bundles con `git worktree` + junction a `node_modules` (nunca `[DirectoryInfo]::Delete()`).
- **PowerShell:** no existen `rg`/`grep`/`head`/`tail`/`sed`; usar `Select-String`, `Get-Content -TotalCount/-Tail`. `"\uFFFD"` no es escape Unicode.
- **Vitest:** `npx vitest run` falla (`describe is not defined`); usar `npm test` o `npx ng test --watch=false`.
- **Commits:** no reescribir historia ya pushada; conventional commits incrementales.

## Cómo trabajar aquí

- Comandos: backend `.\mvnw.cmd -o test`/`verify` en `backend/pantry-api`; frontend `npm test` / `npm run build`; infra `docker compose up -d` en `infra/` (`--build` para la API). Validar infra con `promtool check rules` y `amtool check-config`.
- Documentación: `README.md`, `docs/ROADMAP.md` (incluye la deuda), `docs/FEATURE_GUIDE.md`, `docs/RUNBOOK.md` (operación), `docs/VERSIONS.md`.
- Reglas de trabajo e idioma: `AGENTS.md`. El frontend solo lo toca el subagente `frontend`.
- Cierre de fase: tests verdes + docs + commit convencional (proponer; nunca push sin pedirlo — en este cierre el push quedó autorizado).
