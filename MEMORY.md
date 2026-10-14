# MEMORY.md — Estado del proyecto PantryBase

> **Este fichero es el punto de partida de cada sesión.**
> Al empezar a trabajar, léelo. Al terminar una sesión, actualízalo con el estado real.
> Máximo 100 líneas. Última actualización: **14 de octubre de 2026** (H7 **retirado por completo**: recomendador con IA/Ollama, Spring AI, Resilience4j, preferencia `useAi` y migraciones `V18`–`V20` eliminados de código, deps, config y docs; volumen Postgres recreado, Flyway `V1`–`V17` OK, `pantry-api` healthy. Backend 291 tests, 87,99 % LINE; frontend 336 tests (33 ficheros), bundle 637,63 kB. Siguiente: H8 Social).

## Dónde estamos

**Hito en curso: H8 (Social y personalización).** H0–H6 **cerrados**, documentados y verificados. **H7 (recomendador con IA) retirado del alcance** el 14-oct: no queda código ni dependencias de IA en el repositorio.

Estado verificado: **backend 291 tests verdes** con `.\mvnw.cmd -o clean verify` (gate JaCoCo 0,80 superado; **87,99 %** LINE — sube al irse el código de IA) y `pantry-api` **healthy** en Docker Compose (`infra/`). **Frontend 336 tests verdes (33 ficheros)** con `npm test` y `npm run build` limpio (bundle inicial 637,63 kB). Ojo: en `target/surefire-reports` hay informes stale de clases eliminadas; los totales fiables salen de Maven (los totales fiables salen de Maven).

## Qué está construido

- **H0 Fundación:** Spring Boot 4.1.1 / Java 21, Angular 22 + Material, Vitest, Docker Compose, Jenkins, Prometheus + Grafana, Mailpit.
- **H1 Auth:** registro/login, JWT de acceso + refresh en cookie, contraseña olvidada (email), OAuth2 Google, rate limit por token bucket (Redis).
- **H2 Catálogo y unidades:** USDA FoodData Central (materializado, con caché/frescura y `FdcCachePolicy`), motor de unidades/densidades (`UnitConversionService`).
- **H3 Despensa:** `pantry_items` (bloqueo optimista, `CHECK quantity >= 0`), ajustes con signo (libro mayor `consumption_entries`), métricas de despensa, revamp interactivo del frontend (H6-c).
- **H4 Recetas:** búsqueda TheMealDB + cobertura binaria, alta propia, alérgenos opt-in, recomendación por despensa, refresco por TTL, pre-flight en el frontend.
- **H5 Modo cocina:** sesiones (`cooking_sessions`), descuento de despensa al completar (con 409 de carencia y `acceptShortage`), consumo manual, métricas/resumen, frontend de sesión activa.
- **H6 Temas:** `ThemeId` (`@JsonValue` en minúsculas = `data-theme`), `mat.theme` M3 por paleta, dos tiempos sin FOUC; revamp de despensa y pre-flight de receta.

## Decisiones ya tomadas (no volver a abrir sin motivo)

- **H3:** cantidad en `GRAM`/`UNIT` (volumen sin densidad → `ML`); una fila por ingrediente; ajuste con signo y asiento obligatorio; negativo `409`; `@Version` del servidor; `PUT` sin campo de cantidad.
- **H4 cobertura binaria:** un ingrediente cuenta entero o no cuenta; `matchScore = cubiertos/total`; comparación canónica contra canónica (`toStorageUnit`). Emparejado por contención de tokens (empate → el más corto); toda línea se persiste y las no emparejadas van con `matched=false` + nombre del proveedor.
- **H4 strict vs threshold:** `strict=true` exige `== 1.0`; si no, `threshold` (50 por defecto) y el slider recalcula en cliente. `/recommend` fuerza `threshold=0`; el perfil fija el umbral inicial.
- **H4 caché TheMealDB:** `search.php` y `filter.php` con prefijos de caché separados; candidatos ≤8 por cantidad desc, 24 hits repartidos por cuota justa.
- **H4 materialización desde `search`:** persistencia en `TransactionTemplate` `REQUIRES_NEW` (auto-invocación no pasa por el proxy; tx externa solo-lectura); la carrera la arbitra `UNIQUE (source, external_id)`.
- **H4 refresco por TTL (`V15`):** refresco in-place (mismo id) en detalle y búsquedas filtradas; si el proveedor falla o nada casa, se conserva la copia local y se avanza `refreshed_at`.
- **H5 descontado y carencia:** completar descuenta líneas canónicas con `origin=COOKING_SESSION`; si falta stock, primer 409 con el plan (`deducted = min(necesario, disponible)`); aceptar → la línea baja a 0 sin inventar asiento. Bajar de cero siempre 409. El frontend distingue el 409 de carencia por la forma del body.
- **H5 métricas:** resumen en una sola lectura; `completionRate = completed / (completed + abandoned)`, `null` sin sesiones cerradas; contadores Micrometer globales del proceso, resumen por usuario.
- **H6 temas:** un solo `data-theme` en `<html>`; Material 22 solo M3, un `mat.theme(...)` por paleta bajo `:root[data-theme='<id>']`; `themeId` en minúsculas; aplicación en dos tiempos (localStorage → servidor) sin FOUC.
- **H6-b pre-flight:** clasificación cliente en 4 cubetas (cubierto/bajo/ausente/no catalogado); sin endpoint nuevo.
- **H6-c despensa interactiva:** edición en línea con **delta con signo**, nunca PUT de cantidad; 409 deja la fila intacta. Content projection para paneles; `pantry-page.scss` aplanado bajo budget.
- **H7 retirado (14-oct):** el recomendador con IA se descartó del proyecto. Sin credenciales de terceros no hay IA que cifrar, y el valor real lo cubre `/api/recipes/recommend` (determinista, por despensa). No reintroducir IA sin decisión explícita.
- **Queries JPQL explícitas cuando el destino no es la entidad:** los derived finder devuelven entidades.
- **Los tests usan los endpoints reales** cuando tocan datos con lazy; tocar colecciones fuera de sesión revienta con `LazyInitializationException`.

## Próximos pasos

1. **H8 Social y personalización:** Favoritos (`Favorite`), guardadas (`SavedRecipe`), publicaciones (`PublishedRecipe`); historial de recetas realizadas alimentado al completar sesión; perfil con estadísticas; publicar receta propia.
2. **H9 Producción:** pendiente de definir con el usuario.

## Cuellos de botella conocidos

- **Emparejado TheMealDB por tokens:** los nombres USDA verbose no casan literal; la contención admite falsos positivos de especificidad. Deuda: guarda de especificidad + tabla de alias `provider_name → ingredient_id`.
- **Emparejado con plurales reducidos:** heurística, no singularización completa; revisar si aparecen fallos.
- **OAuth2 sin verificar en producción** y `cookie.secure=false` hasta desplegar.
- **`infra/.env` es local** (nombres en `.env.example`, ningún secreto versionado). Deuda: Vault/CI, auditoría de llamadas externas, coste por usuario en rate limit.
- **Bundle:** aviso a 650 kB compressed; para comparar bundles sin tocar el árbol del usuario, `git worktree` en temp con junction a `node_modules` (nunca `[DirectoryInfo]::Delete()`, borra el destino).
- **Lección PowerShell:** `"\uFFFD"` no es escape Unicode; usar `[char]0xFFFD`.

## Cómo trabajar aquí

- Comandos: backend `.\mvnw.cmd -o verify` en `backend/pantry-api`; frontend `npm test` / `npm run build`; infra `docker compose up -d` en `infra/`.
- Documentación: `README.md`, `docs/ROADMAP.md`, `docs/VERSIONS.md` (consultar antes de proponer dependencias), `docs/FEATURE_GUIDE.md` (6 perfil/preferencias, 11 despensa, 12 recetas, 13 cocina, 18 frontend).
- Reglas de trabajo y de idioma: `AGENTS.md`. El frontend solo lo toca el subagente `frontend`.
- Al cerrar cada fase: tests verdes + docs actualizadas + commit convencional (proponer, nunca push sin pedirlo).
