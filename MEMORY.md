# MEMORY.md — Estado del proyecto PantryBase

> **Este fichero es el punto de partida de cada sesión.**
> Al empezar a trabajarlo, léelo. Al terminar una sesión, actualízalo con el estado real.
> Máximo 100 líneas. Es un resumen operativo, no un diario. Última actualización: **18 de octubre de 2026** (verificación post-commit de H8 + bloque H9-1 sin commitear).

## Dónde estamos

**Hito cerrado y commiteado: H8 (Social y personalización).** H0–H6 y H8 **cerrados**; **H7 retirado** (sin IA, no reintroducir sin decisión explícita). Todo el trabajo de H8 —incluidas las dos rondas de correcciones (unificación de favoritos/guardadas en «Mi recetario», filtro por dieta `V22`, backfill de nombres `V23`, barra superior global, Historial fusionado, frontend en inglés, `GET /api/recipes/mine`)— está en el commit **`a2ebdc9` `feat(social): social features, diet filtering and redesigned navigation (H8)`**, ya en `origin/main`. **Árbol limpio; no hay nada sin commitear.**

**Verificación 18-oct-2026:** backend **320 tests verdes** (`.\mvnw.cmd -o test`, BUILD SUCCESS — 318 de `a2ebdc9` + 2 nuevos de H9-1; 7 en `RateLimitFilterIntegrationTest`); frontend **389 tests verdes (35 ficheros)** con `npm test`. Migraciones Flyway hasta **V23**. Ojo: informes stale en `target/surefire-reports` si se compilan con `-q`; el total fiable es la línea Maven final (`Tests run: ...`).

**Único hito vivo: H9 — Producción y endurecimiento.** **Bloque H9-1 cerrado (18-oct):** rate limit por IP en endpoints anónimos (dos niveles sobre el mismo script Lua; política `anonymous` + overrides `strict-paths` para login/registro/reset; IP de `remoteAddr`, sin confiar `X-Forwarded-For`). **Sin commitear** (proponer `feat(api): rate limit anonymous endpoints by IP with stricter auth path overrides`). Resto de la bola 1 de H9 sin definir: alertas Prometheus, dashboards Grafana, `EXPLAIN`/índices + test de concurrencia.

## Qué está construido

- **H0 Fundación:** Spring Boot 4.1.1 / Java 25 runtime, Angular 22 + Material, Vitest, Docker Compose, Jenkins, Prometheus + Grafana, Mailpit.
- **H1 Auth:** registro/login, JWT + refresh en cookie, contraseña olvidada, OAuth2 Google, rate limit (Redis).
- **H2 Catálogo y unidades:** USDA FDC (materializado, caché/frescura, revalidado contra la API real en H2-B-7), motor de unidades/densidades por clase curada.
- **H3 Despensa:** `pantry_items` (bloqueo optimista), ajustes con signo (libro mayor), métricas, alta por diálogo.
- **H4 Recetas:** búsqueda TheMealDB + cobertura binaria honesta (líneas sin emparejar persistidas), alta propia, alérgenos opt-in, recomendación por despensa, refresco por TTL, pre-flight.
- **H5 Modo cocina:** sesiones, descuento al completar (409 de carencia + `acceptShortage`), consumo manual, métricas.
- **H6 Temas:** `ThemeId` (`data-theme`), M3 por paleta, sin FOUC; reset a azul al logout (H8-0).
- **H8 Social y personalización:** `user_favorites`/`user_saved`/`recipe_history` (unicidad por sesión), `recipes.published_at` + `owner_id`; `PUT/DELETE /api/recipes/{id}/favorite|saved|publish`, `GET /api/favorites|/api/saved|/api/history|/api/recipes/published|/api/recipes/mine`, `GET /api/users/me/stats`; `recipe_diets` (`V22`) con etiquetas derivadas de nutrientes por ración (HIGH_PROTEIN/LOW_CARB/LOW_FAT; `BALANCED` nunca se almacena) y filtro `diet` en search/recommend. Frontend: barra superior global, «Mi recetario» (`/saved`) con tres pestañas (Favoritos/Guardadas/Created), Historial con sesiones + nombre/imagen + Cook again, perfil con índice de anclas, `recipe-create-page`, selector Diet, todo el texto visible en inglés.
- **H7 retirado:** el hueco lo cubre `GET /api/recipes/recommend` sin LLM.

## Decisiones ya tomadas (no volver a abrir sin motivo)

- **H3:** cantidad en `GRAM`/`UNIT`; una fila por ingrediente; ajuste con signo y asiento; negativo `409`; `@Version`.
- **H4 cobertura binaria:** cubierto/total canónico; emparejado por tokens; líneas no emparejadas persistidas con `matched=false`.
- **H4 strict vs threshold:** `strict` exige `==1.0`; si no `threshold` (50); `/recommend` fuerza `threshold=0`.
- **H4 caché/materialización/refresco:** prefijos separados; materialización en `REQUIRES_NEW`; refresco in-place conservando copia si falla.
- **H5 descuadre y carencia:** completar descuenta con `origin=COOKING_SESSION`; primer 409 con plan; aceptar baja a 0 sin asiento; bajar de cero 409.
- **H6 temas:** un `data-theme` en `<html>`; dos tiempos sin FOUC.
- **H8 favoritos ≠ guardadas:** dos tablas/UI. **Publicación = flag `published_at`** (NULL privada). **Stats** en paquete `social` (evita ciclo con recipe).
- **H8 propiedad:** `recipes.owner_id`; search de USER acota a propias + publicadas ajenas; publicar ajena → 403; no-USER → 400; inexistente → 404; idempotente.
- **H8-F dieta:** derivada de nutrientes y materializada (`V22`); umbrales proteína ≥ 20 g, carbos ≤ 20 g, grasa ≤ 10 g; `HIGH_FIBER`/`LOW_SODIUM` retirados.
- **H8 UI:** «Mi recetario» con tres pestañas; la lista de sesiones vive en Historial (`/cooking` redirige); navegación = barra superior global; texto visible en inglés (pendiente i18n).
- **Queries JPQL explícitas** cuando el destino no es la entidad; **tests contra endpoints reales** cuando hay lazy.

## Próximos pasos

1. **H9, resto de la bola 1 (con el usuario):** alertas Prometheus (5xx, cuotas API), dashboards Grafana de salud, índices `EXPLAIN` + test de concurrencia del descontado. Bola 2: Vault/CI, backups + DR, pipeline de promoción; ROADMAP lista además el servidor Jenkins (pendiente desde H0).
2. **Deuda conocida** (no bloquea): emparejado TheMealDB (`provider_name → ingredient_id` + guarda de especificidad), `cookie.secure=false` y OAuth2 sin verificar *antes* de desplegar, budget de bundle 650 kB.

## Cuellos de botella conocidos

- **Emparejado TheMealDB por tokens:** nombres USDA verbose no casan literal; plurales reducidos: heurística.
- **OAuth2 sin verificar en producción** y `cookie.secure=false` hasta desplegar.
- **`infra/.env` es local** (nombres en `.env.example`). Deuda: Vault/CI, auditoría de llamadas externas, coste por usuario en rate limit.
- **Bundle:** aviso a 650 kB compressed; comparar bundles sin tocar el árbol del usuario con `git worktree` + junction a `node_modules` (nunca `[DirectoryInfo]::Delete()`, borra el destino).
- **Budget de estilos:** `anyComponentStyle` a 5 kB (aviso) en `angular.json`; medido sobre CSS emitido/minificado.
- **Commits:** H8 se cerró como monolito (`a2ebdc9`) en vez de unidades lógicas; no reescribir historia ya pushada — la lección es commitear incrementalmente.
- **Lección PowerShell:** `"\uFFFD"` no es escape Unicode; usar `[char]0xFFFD`. No existen `rg`/`grep`/`head`/`tail`/`sed`/`sort`: usar `Select-String`, `Get-Content -TotalCount/-Tail`, `Select-Object`.
- **Vitest:** `npx vitest run` falla (`describe is not defined`); usar `npm test` o `npx ng test --watch=false`.

## Cómo trabajar aquí

- Comandos: backend `.\mvnw.cmd -o test`/`verify` en `backend/pantry-api`; frontend `npm test` / `npm run build`; infra `docker compose up -d` en `infra/`.
- Documentación: `README.md`, `docs/ROADMAP.md`, `docs/VERSIONS.md` (consultar antes de proponer deps), `docs/FEATURE_GUIDE.md`.
- Reglas de trabajo e idioma: `AGENTS.md`. El frontend solo lo toca el subagente `frontend`.
- Al cerrar cada fase: tests verdes + docs actualizadas + commit convencional (proponer, nunca push sin pedirlo).
