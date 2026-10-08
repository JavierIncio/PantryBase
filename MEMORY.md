# MEMORY.md — Estado del proyecto PantryBase

> **Este fichero es el punto de partida de cada sesión.**
> Al empezar a trabajar, léelo. Al terminar una sesión, actualízalo con el estado real.
> Máximo 100 líneas. Última actualización: **8 de octubre de 2026** (H4 backend: recetas + cobertura + materialización; 257 tests verdes, JaCoCo 86,7 %).

## Dónde estamos

**Hito en curso: H4 (recetas y filtrado por disponibilidad).** Backend H4 cerrado y verde, incluidos la materialización de hits de TheMealDB y los nutrientes por ración. Pendiente de H4: la búsqueda fresca por ingrediente (`ingredientId` filtra solo filas locales) y todo el frontend de recetas. H0–H3 cerrados y documentados.

Estado verificado: **backend 257 tests verdes** con `.\mvnw.cmd -o verify` (gate JaCoCo 0,80 superado, **86,7 %**). **Frontend 165 tests verdes** con `npm test` y `npm run build` sin avisos. Ojo: en `target/surefire-reports` hay un informe stale de `PantryMeasureEquivalenceIntegrationTest` (clase eliminada el 05/10); los totales fiables salen de Maven, no de los `.txt` huérfanos.
Rama `main`: commits locales sin publicar, nunca hay push sin pedirlo.

## Qué está construido

- **H0 Fundación:** Maven + Spring Boot 4.1.1 / Java 21, Angular 22 con Material, Vitest, Docker Compose, Jenkins, Prometheus + Grafana, Mailpit.
- **H1 Autenticación:** registro, login, access token (15 min) + refresh token en cookie `httpOnly` (7 d), recuperación por correo, login con Google (OAuth2), roles, rate limiting por cubo de fichas en Redis.
- **H1 Usuario:** perfil, preferencias (`filterMode`, `coverageThreshold`, `diet`) y `user_allergy_exclusions` (dueño: `V3__profile.sql`, códigos EU sembrados en `allergens`).
- **H2 Unidades y catálogo:** `UnitConversionService` con escalera de precedencia, USDA FDC (búsqueda, detalle, lote), materialización idempotente, densidades por clase, caché/frescura (`cache-ttl` 24 h), Redis y manejo de 429.
- **H3 Despensa:** `GET/POST/PUT/DELETE /api/pantry`, ajustes con signo y asiento en `consumption_entries` (el alta también escribe asiento), cantidad en `GRAM`/`UNIT`, una fila por `(user_id, ingredient_id)`, aislamiento con `404`, métricas `GET /api/pantry/summary` (`GROUP BY` de dos ejes). Frontend «Mi despensa» y alta rápida (subagente `frontend`).
- **H4-a TheMealDB:** `TheMealDbClient` (search/lookup), `RecipeSearchCache` (TTL 24 h en Redis, prefijo `catalog:themdb:`), `RecipeWebClientConfig` (bean `WebClient.Builder`).
- **H4-b Recetas:** `V13__recipes.sql`, paquete `recipe` completo, `RecipeController` (`GET /api/recipes` con `name`/`ingredientId`/`strict`/`threshold`/`allergenSafe`, `GET /api/recipes/{id}`, `POST /api/recipes`), `RecipeNotFoundException` → 404 en `GlobalExceptionHandler`. Cobertura binaria, alta propia con canónicos vía `toStorageUnit` y derivación de alérgenos (`allergen_mapping`, códigos EU de `V3`). **Materialización TheMealDB cerrada:** `TheMealDbClient.lookupById` parsea `strIngredient1..20`/`strMeasure1..20` (JSON crudo como mapa), emparejado por contención de tokens (plurales reducidos, ganador más corto/alfabético, sin match se omite y si ninguno empareja la receta no se materializa), medida→canónico con `toStorageUnit`, persistencia en transacción `REQUIRES_NEW` vía `TransactionTemplate` (auto-invocación desde `search`, que correría en la tx de solo lectura). **`computeNutrients` persiste la fila** de `recipe_nutrients` (solo líneas con peso canónico; por ración; `null` si nada es ponderable) — el detalle ya no diverge del `POST`. 15 tests de integración (12 previos + 3 nuevos).

## Decisiones ya tomadas (no volver a abrir sin motivo)

- **H3:** cantidad en `GRAM`/`UNIT` (volumen sin densidad → `ML`); una fila por ingrediente; ajuste con signo y asiento obligatorio; negativo `409` y cero permitido; `@Version` del servidor; `PUT` sin campo de cantidad.
- **H4 cobertura binaria:** un ingrediente cuenta entero o no cuenta (99/100 no cubre); `matchScore = cubiertos/total`; comparación canónica contra canónica (misma `toStorageUnit` que la despensa).
- **H4 strict vs threshold:** `strict=true` exige `== 1.0`; si no, manda `threshold` (50 por defecto) y el slider recalcula en cliente sobre `matchScore`/`missingCount`, sin llamada.
- **H4 prioridad de candidatos:** USER → `THE_MEAL_DB` materializadas → fresca solo si hay `name`; dedup con `LinkedHashMap` + `putIfAbsent`. Sin paginación.
- **H4 alérgenos opt-in** con `allergenSafe` sobre `user_allergy_exclusions` (V3, sin tabla nueva); `recipe_allergens` se materializa al crear.
- **H4 materialización: emparejado por contención de tokens** en lugar de fallo ruidoso o alias: palabras del proveedor ⊆ palabra completa del nombre local; caso de empate gana el más corto (determinista). Los no emparejados se descartan; si no empareja ninguno, la receta no se materializa. Deuda anotada: tabla de alias `provider_name → ingredient_id` en el roadmap.
- **H4 escritura desde `search`:** la auto-invocación no pasa por el proxy transaccional y la transacción externa es de solo lectura, así que la persistencia va en un `TransactionTemplate` con `REQUIRES_NEW`; la carrera la arbitra el `UNIQUE (source, external_id)` y se ignora.
- **Queries JPQL explícitas cuando el destino no es la entidad:** `findAllergenCodesByRecipeId` sin `@Query` devolvía entidades y la conversión a `String` caía en un `500`; los derived finder devuelven entidades.
- **Los tests usan los endpoints reales** cuando tocan datos con lazy (p. ej. `excludeFromDiet` vía `PUT /api/users/allergy-exclusions`): tocar `User.allergyExclusions` fuera de sesión revienta con `LazyInitializationException`.

## Próximos pasos

1. **Cerrar H4:** (a) búsqueda fresca por ingrediente (hoy `ingredientId` filtra solo filas locales), (b) frontend de recetas — grid, badges, slider, detalle — **con el subagente `frontend`**.
2. **H5 (modo cocina):** `ConsumptionOrigin` gana `COOKING_SESSION`; el tipo del frontend es unión de uno solo y avisará el compilador.
3. **H6 (Material theming):** 4-5 paletas SCSS → `:root[data-theme=...]`, selector en preferencias; sin Tailwind ni FontAwesome. Frontend puro.
4. Frontend del catálogo (conversor de unidades sin pantalla) y verificar login de Google contra cliente real.

## Cuellos de botella y riesgos

- **Emparejado TheMealDB por tokens:** los nombres USDA verbose («Milk, whole, approximately 3.25% fat») no casan literal con TheMealDB; la contención por tokens empareja más pero la cobertura solo cuenta líneas persistidas (parcial = sobreestimación). Deuda: tabla de alias curada.
- **Cuota USDA FDC:** ~1.000 req/h con key, 30/h con `DEMO_KEY`; todo endpoint nuevo pasa por `FdcCachePolicy`. `429` → `502` (indistinguible de caída salvo por mensaje).
- **Wire del USDA costó tres correcciones:** fixtures literales de respuestas reales, nunca a partir de los records.
- **Densidades:** solo 4 clases curadas; sin densidad `toStorageUnit` deja `ML` y H4 compara igual (la línea de receta cae en `ML` también). Ampliar solo con valores citables de una porción FDC.
- **Carrera en alta de despensa sin mapear** (`existsBy` previo no atómico → `500` posible) y **carrera de bloqueo optimista sin test** (evitar tests basados en tiempos).
- **OAuth2 sin verificar en producción** y `cookie.secure=false` hasta desplegar.
- **`infra/.env` es local** (nombres en `.env.example`, ningún secreto versionado). Deuda anotada en roadmap: Vault/CI, auditoría de llamadas externas, coste por usuario en rate limit.
- **Bundle:** aviso a 650 kB compressed; para comparar bundles sin tocar el árbol del usuario, `git worktree` en temp con junction a `node_modules` (nunca `[DirectoryInfo]::Delete()`, borra el destino).
- **Lección PowerShell:** `"\uFFFD"` no es escape Unicode; usar `[char]0xFFFD`.

## Cómo trabajar aquí

- Comandos: backend `.\mvnw.cmd -o verify` en `backend/pantry-api`; frontend `npm test` / `npm run build`; infra `docker compose up -d` en `infra/`.
- Documentación: `README.md`, `docs/ROADMAP.md`, `docs/VERSIONS.md` (consultar antes de proponer dependencias), `docs/FEATURE_GUIDE.md` (sección 12 = recetas).
- Reglas de trabajo y de idioma: `AGENTS.md`. El frontend solo lo toca el subagente `frontend`.
- Al cerrar cada fase: tests verdes + docs actualizadas + commit convencional (proponer, nunca push sin pedirlo).
