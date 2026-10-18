# MEMORY.md — Estado del proyecto PantryBase

> **Este fichero es el punto de partida de cada sesión.**
> Al empezar a trabajar, léelo. Al terminar una sesión, actualízalo con el estado real.
> Máximo 100 líneas. Última actualización: **18 de octubre de 2026** (H8 **cerrado** + rediseño de navegación y segunda ronda de correcciones: barra superior global, lista de sesiones fusionada en Historial, perfil con índice de secciones, frontend en inglés, y «Mi recetario» con **tres pestañas** incl. «Created» vía **`GET /api/recipes/mine`**. Backend **318 tests verdes**; frontend **389 tests verdes** en 35 ficheros. **Sin commitear**).

## Dónde estamos

**Hito cerrado: H8 (Social y personalización).** H0–H6 y H8 **cerrados**. **H7 retirado** (sin IA). De H8 están **hechos y verificados**: backend H8-0/H8-A/H8-B/H8-C/H8-D/H8-E y **todo el frontend de H8** (tarjeta de stats en `profile-page`, toggles favorito/guardada en card y detalle, páginas de guardadas e historial con entradas de navegación, publicar/despublicar en el detalle y formulario de receta propia en `/recipes/new` con punto de entrada en la cabecera de la rejilla). **H8-G (cierre) hecho:** `FEATURE_GUIDE` (nueva §14 «Social y personalización (H8)», renumbered 15–23), `README` y `ROADMAP` (H8 cerrado) actualizadas.

**Ronda de correcciones posterior al cierre (16-oct, hecha):** 4 incidencias del usuario resueltas — (1) **eliminada la pestaña/ruta/página «Social»** (placeholder; `PlaceholderPage` se queda porque la usa `not-found`); (2) **favoritos y guardadas unificados** en una sola página **«Mi recetario»** (`/saved`); (3) **arregladas las estadísticas** del perfil (faltaban las clases `.stats-*` de `profile-page.scss`); (4) **filtro por dieta implementado** (bloque `H8-F`, `V22`), con el selector `Diet` en la página de recetas. Docs actualizadas con todo (FEATURE_GUIDE §14 + §13 filtros + tabla de migraciones, ROADMAP bloque H8-F).

**Segunda ronda: rediseño de navegación + 3 bugs (18-oct, hecha):** (a) **barra superior global** (`mat-toolbar` + menú de usuario) en lugar del `mat-sidenav`; nav de **4 entradas** (Pantry, Recipes, My cookbook, History), Profile en el menú de usuario. (b) **La lista de sesiones de cocina se fusiona en «Historial»** (`/history`); se borró `pages/cooking/cooking-page.*`; `/cooking` redirige a `/history` y `/cooking/:id` sigue vivo. (c) **Todo el texto visible del frontend pasa a inglés.** (d) **Bug anclas del perfil:** `href="#id"` con `<base href="/">` resolvía a `/#id` → saltaba a `/pantry`; ahora `routerLink="/profile"`+`fragment` + `withInMemoryScrolling({ anchorScrolling: 'enabled' })` (OJO: sin `scrollOffset`, que no existe en Angular 22) y `scroll-margin-top` por sección. (e) **Bug historial sin nombre/imagen:** `history-page` ahora mapea `Map<number, RecipeSummary>` y pinta miniatura con fallback. (f) **Recetas creadas visibles:** nueva pestaña **«Created»** en «Mi recetario», alimentada por el nuevo **`GET /api/recipes/mine`** (backend `RecipeService.listMine` + `RecipeRepository.findUserRecipesOwnedBy`, sin filtro por cobertura). Pendiente: **solo el commit** (proponer, no hacer sin pedirlo).

Estado verificado (18-oct): **backend 318 tests verdes** (`.\mvnw.cmd -o test`; `RecipeControllerIntegrationTest` 42). **Frontend 389 tests verdes (35 ficheros)** con `npm test` y `npm run build` limpio. Migraciones Flyway hasta **V23**. Ojo: informes stale en `target/surefire-reports`; total fiable = Maven.

## Qué está construido

- **H0 Fundación:** Spring Boot 4.1.1 / Java 25 runtime, Angular 22 + Material, Vitest, Docker Compose, Jenkins, Prometheus + Grafana, Mailpit.
- **H1 Auth:** registro/login, JWT + refresh en cookie, contraseña olvidada, OAuth2 Google, rate limit (Redis).
- **H2 Catálogo y unidades:** USDA FDC (materializado, caché/frescura), motor de unidades/densidades.
- **H3 Despensa:** `pantry_items` (bloqueo optimista), ajustes con signo (libro mayor), métricas, revamp frontend (H6-c).
- **H4 Recetas:** búsqueda TheMealDB + cobertura binaria, alta propia, alérgenos opt-in, recomendación por despensa, refresco por TTL, pre-flight.
- **H5 Modo cocina:** sesiones, descuento al completar (409 de carencia + `acceptShortage`), consumo manual, métricas.
- **H6 Temas:** `ThemeId` (`data-theme`), M3 por paleta, sin FOUC.
- **H8 backend:** `user_favorites`/`user_saved` (PK compuesta), `recipe_history` (UNIQUE por sesión), `recipes.published_at` + `recipes.owner_id`; endpoints `PUT/DELETE /api/recipes/{id}/favorite|saved`, `GET /api/favorites|/api/saved|/api/history`, `GET /api/users/me/stats`, `POST/DELETE /api/recipes/{id}/publish`, `GET /api/recipes/published`, **`GET /api/recipes/mine`** (recetas propias privadas o publicadas, más recientes primero, sin filtro por cobertura); `favorite`/`saved`/`owned`/`published` en los DTOs de receta. **H8-F:** `recipe_diets` (`V22`) + campo `diet` en `RecipeSearchRequest`, filtrando en `scoreAndFilter` (compartido por `search` y `recommend`).
- **H8 frontend:** `RecipesService` cubre favorite/saved/publish/unpublish/published/**mine**/create/`/api/history`; `ProfileService.stats()`; **barra superior global** (`shell.*`, `mat-toolbar` + menú de usuario, nav de 4 entradas en `core/nav/nav-item.ts`); **«Mi recetario»** (`/saved`, `collection-page`) con **tres pestañas** (Favoritos, Guardadas, **Created** vía `/api/recipes/mine`); **`history-page`** con la lista de sesiones fusionada (nombre + imagen de receta, «Cook again») y `cooking-page` eliminado; **perfil** en una columna con **índice lateral de anclas** (`routerLink`+`fragment`); tarjeta de stats en `profile-page`; toggles favorito/guardada en card y detalle; **publicar/despublicar** en el detalle (solo `owned && source === 'USER'`); **`recipe-create-page`** (`/recipes/new`); **selector `Diet`** en la página de recetas. Todo el texto visible **en inglés**.

## Decisiones ya tomadas (no volver a abrir sin motivo)

- **H3:** cantidad en `GRAM`/`UNIT`; una fila por ingrediente; ajuste con signo y asiento; negativo `409`; `@Version`.
- **H4 cobertura binaria:** cubierto/total, comparación canónica; emparejado por tokens; líneas no emparejadas persistidas con `matched=false`.
- **H4 strict vs threshold:** `strict` exige `==1.0`; si no `threshold` (50); `/recommend` fuerza `threshold=0`.
- **H4 caché/materialización/refresco:** prefijos de caché separados; materialización en `REQUIRES_NEW` (auto-invocación); refresco in-place por TTL conservando copia si falla.
- **H5 descuadre y carencia:** completar descuenta con `origin=COOKING_SESSION`; primer 409 con plan; aceptar baja a 0 sin asiento; bajar de cero 409.
- **H6 temas:** un `data-theme` en `<html>`; dos tiempos sin FOUC.
- **H7 retirado:** no reintroducir IA sin decisión explícita.
- **H8 favoritos ≠ guardadas:** dos tablas/UI. **Publicación = flag `published_at`** (NULL privada). **Stats** = nº de cocinas completadas, nº de recetas distintas y top-5 ingredientes por uso (cada cocción pondera).
- **H8 propiedad de recetas (`V21`):** `recipes.owner_id` (NULL para proveedor). Search de USER acota a **propias + publicadas ajenas**; filas sin dueño quedan invisibles. Publicar receta ajena → **403**; receta no-USER → **400**; inexistente → **404**; idempotente.
- **H8 frontend:** el `recipe-create-page` fija la receta como privada (no envía `published`); publicar es una decisión posterior desde el detalle. Cada línea de ingrediente resuelve el `fdcId` de la búsqueda a `id` interno vía `CatalogService.getIngredient` antes de enviar. Campos no recogidos (`videoUrl`, `tags`, `sourceUrl`) viajan como `null` explícito.
- **H8 sin ciclos:** `StatsController`/`StatsService` viven en el paquete `social` (recipe ya depende de user; meter stats en user cerraría ciclo). Endpoint servido bajo `/api/users/me` pero desde `social`.
- **H8-F dieta:** etiquetas **derivadas de nutrientes por ración** y materializadas en `recipe_diets` (`V22`), como `recipe_allergens`. Umbrales: proteína ≥ 20 g, carbohidratos ≤ 20 g, grasa ≤ 10 g. `BALANCED` = sin filtro y **nunca se almacena**; receta sin nutrientes = **sin etiqueta**. `HIGH_FIBER`/`LOW_SODIUM` **retirados** del enum/UI (no derivables) y `V22` normaliza preferencias antiguas a `BALANCED`. Labels del selector en inglés (`Diet`, `High protein`…), por consistencia local con sus controles hermanos.
- **H8 UI:** **favoritos, guardadas y recetas propias** unificados en una sola página «Mi recetario» con **tres pestañas** (Favoritos / Guardadas / Created) y un único punto de navegación (decisión del usuario); **no existe pestaña «Social»**. La lista de sesiones de cocina vive **dentro de Historial**, no en una página/ruta `/cooking` propia. Navegación = barra superior global.
- **Queries JPQL explícitas** cuando el destino no es la entidad.
- **Los tests usan los endpoints reales** cuando tocan datos con lazy.

## Próximos pasos

1. **Commit de H8:** todo el árbol sigue **sin commitear**. Proponer (no ejecutar sin pedirlo) algo así: `fix(theme): reset palette to default on logout` → `feat(social): favorites, saved recipes, history, profile stats and recipe publishing` (backend) → `feat(recipes): add GET /api/recipes/mine for user's own recipes` (backend + tests) → `feat(social): unify favorites, saved and created recipes into My cookbook` (frontend) → `refactor(layout): replace sidenav with global top toolbar` → `feat(history): merge cooking sessions list and show recipe name/image` → `fix(profile): scroll to sections via routerLink fragments` → `feat(recipes): derive diet tags from per-serving nutrients and filter by diet` → `fix(recipes): store catalog ingredient names on matched lines and backfill them` → `docs: ...`. **Nunca push sin pedirlo.**
2. **H9 Producción:** pendiente de definir con el usuario.

## Cuellos de botella conocidos

- **Emparejado TheMealDB por tokens:** names USDA verbose no casan literal; deuda: guarda de especificidad + alias `provider_name → ingredient_id`. Plurales reducidos: heurística.
- **OAuth2 sin verificar en producción** y `cookie.secure=false` hasta desplegar.
- **`infra/.env` es local** (nombres en `.env.example`). Deuda: Vault/CI, auditoría de llamadas externas, coste por usuario en rate limit.
- **Bundle:** aviso a 650 kB compressed; comparar bundles sin tocar el árbol del usuario con `git worktree` + junction a `node_modules` (nunca `[DirectoryInfo]::Delete()`, borra el destino).
- **Budget de estilos:** `anyComponentStyle` subido de **4 → 5 kB** (aviso) en `angular.json`; `recipes-page.scss` quedó en ~4.06 kB al añadir el botón de cabecera. Medido sobre **CSS emitido/minificado** (los comentarios no cuentan).
- **Lección PowerShell:** `"\uFFFD"` no es escape Unicode; usar `[char]0xFFFD`. No existen `rg`/`grep`/`head`/`tail`/`sed`/`sort`: usar `Select-String`, `Get-Content -TotalCount/-Tail`, `Select-Object`.
- **Vitest:** `npx vitest run` falla (`describe is not defined`); usar `npm test` o `npx ng test --watch=false`.

## Cómo trabajar aquí

- Comandos: backend `.\mvnw.cmd -o test`/`verify` en `backend/pantry-api`; frontend `npm test` / `npm run build`; infra `docker compose up -d` en `infra/`.
- Documentación: `README.md`, `docs/ROADMAP.md`, `docs/VERSIONS.md` (consultar antes de proponer deps), `docs/FEATURE_GUIDE.md`.
- Reglas de trabajo e idioma: `AGENTS.md`. El frontend solo lo toca el subagente `frontend`.
- Al cerrar cada fase: tests verdes + docs actualizadas + commit convencional (proponer, nunca push sin pedirlo).
