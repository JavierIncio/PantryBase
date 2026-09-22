# PantryBase — Roadmap

Hoja de ruta por hitos. Cada hito termina con una demo jugable o un incremento verificable ("definition of done": testes verdes, lint/format limpio, coverage con quality gate, despliegue en el entorno correspondiente y documentación de la porción de API en OpenAPI).

## Criterios de priorización

1. **Núcleo de dominio antes que brillo:** el motor de unidades, inventario y descontado son el valor diferencial.
2. **Integraciones antes que social:** Edamam y LLM proporcionan el contenido; SPAs de favoritos/historial vienen después.
3. **Observabilidad desde el primer hito:** métricas y logs desde H0, no póstumo.
4. **Cada hito desplegable en Docker** y con CI (Jenkins) verificado.

---

## H0 — Fundación del proyecto

**Objetivo:** esqueleto monorepo con CI/CD y entorno local reproducible.

- [x] Scaffold Spring Boot (Java 21) con paquetes por módulos de dominio (`catalog`, `pantry`, `recipes`, `cooking`, `social`, `ai`).
- [x] Scaffold Angular + Angular Material (layout, tema, rutas base, gzip del API client via OpenAPI).
- [x] `docker-compose.yml` en `infra/`: PostgreSQL 18, Redis 8, Prometheus, Grafana (provisioning básico).
- [x] Flyway con esquema inicial: `users`, `units`, `measure_conversion`, `allergens`.
- [x] Micrometer/Prometheus activos; endpoint `/actuator/prometheus`.
- [x] Jenkinsfile de build: compile → test → quality gate (JaCoCo) → build Docker → publish imagen.
- [x] springdoc-openapi configurado; contrato `/v3/api-docs`.
- [x] Testcontainers base (Postgres/Redis) con test de humo.

**Definition of done:** `git push` a main dispara pipeline verde; `docker compose up` levanta toda la infra; healthchecks OK.

**Estado H0 (sept 2026):** build y tests verdes con quality gate JaCoCo (0.80); `docker compose up` con healthchecks OK para Postgres 18 / Redis 8 / Prometheus / Grafana 13.0.9. Pendiente de entorno real (no bloquea H0): desplegar el servidor Jenkins y configurar el remoto de git para que `git push` dispare el pipeline.

---

## H1 — Autenticación y usuario

- [x] Registro/login con Spring Security + JWT (refresh token).
- [x] Login social OAuth2 (Google) con link-or-create (añadido a H1 en sept 2026).
- [x] Perfil: `UserPreferences` (modo de filtrado por defecto, umbral laxo, dieta).
- [x] Exclusión de alérgenos por usuario (`UserAllergyExclusion`).
- [x] Catálogo de alérgenos consultable (`GET /api/users/allergens`).
- [x] Rate limiting en Redis por usuario + endpoint (token bucket).
- [x] Frontend: pantalla de login, guardas de rutas, interceptores de JWT.
- [x] Frontend: pantalla de perfil: preferencias y exclusión de alérgenos (F2, sept 2026).

**Definition of done:** permisos por rol y datos de preferencias persistidos; sesión expirada redirige al login.

**Estado H1 (sept 2026):** bloque H1-A y H1-B implementados en backend: registro/login/logout y refresh con JWT (JJWT), refresh token hasheado en BBDD con rotación, cookie `httpOnly` para el refresh, `GET /api/users/me`, login OAuth2 Google con link-or-create y rate limiting en Redis (token bucket). Pendiente dentro de H1: pantalla de perfil en el frontend (F2). **Bloque H1-C cerrado (sept 2026):** perfil (`UserPreferences` con `@Id @MapsId` + `Persistable` para clave asignada, valores por defecto sin fila persistida vía dirty-checking), exclusión de alérgenos (14 códigos UE seedeados, contrato estricto — `"peanut"`/`"EGGS"` → 400) y catálogo `GET /api/users/allergens` con orden determinista. **41 tests de integración verdes y gate JaCoCo verificado.** **Bloques H1-A y H1-B cerrados en backend: 30 tests de integración verdes** (registro, login, refresh con rotación y reuso de token detectado, logout y rate limiting); el gate JaCoCo (≥0.80) verificado en el cierre de la bola (84,4%). **Bloque F1 frontend cerrado (sept 2026):** login/registro y callback OAuth2 públicos fuera del shell, guarda de rutas con restauración de sesión en arranque vía cookie `httpOnly` (D2: `POST /api/auth/refresh` + `GET /api/users/me`), interceptor JWT con `Bearer` y refresh single-flight con reintento único; proxy dev `/api` → `localhost:8080` sin CORS; 30 tests Vitest verdes y `ng build` limpio; requiere Node ≥ 24.15 (Angular CLI 22). **Bloque F2 frontend cerrado (sept 2026):** pantalla `/profile` con dos tarjetas (Preferences con `mat-select` de FilterMode/Diet + `mat-slider` de umbral; Allergy exclusions con checklist del catálogo de 14 alérgenos), guardado explícito con `PUT` de reemplazo, estados de carga/error/reintento por tarjeta y feedback vía snackbar; **45 tests Vitest verdes (30 previos + 15 nuevos)** y `ng build` limpio (budget inicial subido a 600 kB); cero dependencias nuevas.

---

## H2 — Catálogo de ingredientes y motor de unidades

**Objetivo:** el modelo de medidas estandarizadas, corazón del dominio.

- [ ] Entidades `Ingredient`, `Unit`, `MeasureConversion` y tabla de densidades por categoría.
- [ ] Conversor de unidades: medidas comunes (taza, cdta., cda., oz, lb, pinta…) → canónicas (`GRAM`/`ML`/`UNIT`) usando conversión base + densidad.
- [ ] Integración Edamam **Food Database API**: búsqueda, autocompletado, parse. Cache en BBDD y Redis.
- [ ] Sincronización/refresh de datos de nutrientes y categorías.
- [ ] API de administración (o seed) para ajustar densidades ambiguas.

**Definition of done:** convertir `1 taza de harina` → gramos correctos según densidad; el catálogo produce ingredientes buscables con nutrientes.

---

## H3 — Inventario (despensa)

- [ ] CRUD `PantryItem` (ingrediente + cantidad canónica + ubicación/nota).
- [ ] Alta rápida: búsqueda en catálogo → selector de unidad → conversión automática.
- [ ] Ajuste de cantidades (añadir/consumir) con auditoría (`ConsumptionEntry` manual).
- [ ] Frontend: vista "Mi despensa" con agrupación por categoría y edición inline.
- [ ] Métricas: tamaño del inventario, reparto por categoría.

**Definition of done:** despensa editable, cantidades siempre en unidad canónica y consistentes con la auditoría.

---

## H4 — Recetas y filtrado por disponibilidad

**Objetivo:** recomendación basada en la despensa (el diferencial del producto).

- [ ] Integración **Edamam Recipe Search API**: búsqueda de recetas por ingredientes disponibles.
- [ ] Persistencia de `Recipe` + `RecipeIngredient` (cantidades originales del proveedor y canónicas) + nutrientes por ración.
- [ ] Derivación de alérgenos por receta (mapeo interno sobre ingredientes) y filtrado por exclusiones del usuario.
- [ ] Motor de cobertura: ratio = cantidad disponible / cantidad necesaria por ingrediente → **match score** de la receta.
- [ ] Filtrado **estricto** (requiere 100% según umbral de cantidad) y **laxo** (porcentaje de cobertura configurable).
- [ ] Slider de umbral (0-100%) con recalculado en tiempo real.
- [ ] Frontend: grid de recetas con badges de cobertura, detalles de receta, enlaces al step completo.

**Definition of done:** para un inventario dado, el modo estricto muestra solo recetas factibles y el laxo ordena por score; el slider recalcula sin llamada.

---

## H5 — Modo cocina (progreso y descontado)

**Objetivo:** seguir la receta y reflejar el consumo en la despensa.

- [ ] `CookingSession` con estados: `EN_CURSO`, `EN_PAUSA`, `COMPLETADA`, `ABANDONADA`.
- [ ] Marcar pasos/progreso de la receta desde el frontend.
- [ ] Al **completar** sesión: generar `ConsumptionEntry` automáticos (descontar cantidades usadas) con confirmación previa.
- [ ] Descontado manual desde el inventario (sin sesión).
- [ ] Cálculo de stock tras descuento; advertencias si un ingrediente queda a cero.
- [ ] Métricas: duración de sesiones, tasa de finalización.

**Definition of done:** completar una receta de 3 ingredientes reduce la despensa exactamente en las cantidades usadas (verificado con tests de integración).

---

## H6 — Recomendador con LLM

**Objetivo:** asistente gratuito, multi-proveedor, que recomiende sobre catálogo real.

- [ ] Spring AI configurado con cadena de proveedores gratuitos (orden configurable): **Ollama** (local) en dev; en prod **OpenRouter free**, **Groq free tier**, **Gemini free tier**, etc.
- [ ] Mecanismo **fallback/rotación**: sobre error, quota o timeout → siguiente proveedor; circuit breaker por proveedor degradado.
- [ ] Prompt estructurado + tool-calling: el LLM devuelve JSON con `recipeId`s del catálogo y justificación de cobertura (nunca recetas inventadas).
- [ ] Endpoint `POST /ai/recommend` con el inventario como contexto.
- [ ] Métricas: latencia por proveedor, tasa de fallback, éxito global.

**Definition of done:** con Ollama caído o apertura de quórum agotada en el primero, la cadena rota y devuelve recomendaciones del catálogo; respuesta con formato estable.

---

## H7 — Social y personalización

- [ ] Favoritos (`Favorite`), guardadas (`SavedRecipe`), publicaciones (`PublishedRecipe`).
- [ ] Historial de recetas realizadas (`RecipeHistory`) alimentado automáticamente al completar sesión.
- [ ] Vista de perfil con estadísticas (nº recetas, ingredientes más usados).
- [ ] Publicar receta propia: formulario de receta manual (ingresar/editar ingredientes y pasos) que alimenta el catálogo.

**Definition of done:** toda la interacción social requiere auth; historial 100% consistente con las sesiones completadas.

---

## H8 — Producción y endurecimiento

- [ ] Hardening de seguridad: rate limiting por IP, auditoría de llamadas externas, secretos en Vault/CI.
- [ ] Dashboards de Grafana finales: salud, cuotas Edamam/LLM, cobertura de despensa, rendimiento del recomendador.
- [ ] Alertas (Prometheus Alertmanager) para: errores 5xx, circuito abierto, cuota de LLM agotada, sesiones colgadas.
- [ ] Carga y escalado: caché Redis efectiva, índices de BBDD analizados con `EXPLAIN`, test de concurrencia sobre descontado.
- [ ] Backups de PostgreSQL + estrategia de restauración.
- [ ] Pipeline de promoción a producción con gates de calidad.

**Definition of done:** revisión de seguridad, runbook de operación redactado, alertas configuradas y prueba de recuperación tras desastre completada.

---

## Fuera de alcance / ideas futuras

- [ ] Lista de compra generada a partir de recetas no factibles (recetas “casi” → qué falta comprar).
- [ ] Compartir despensa entre usuarios (multiusuario/colegas de casa).
- [ ] Red social pública de recetas (feed, likes, comentarios).
- [ ] Planificación de menú semanal con recetas del historial.
- [ ] App móvil o PWA con modo offline.
- [ ] Traducción multi-idioma (el dominio ya normaliza ingredientes).

## Decisiones abiertas / deuda técnica a resolver en H0/H2

- **Fuente de verdad para alérgenos:** Edamam no garantiza campo alergeno directo; validar mapeo propio vs. datos del proveedor antes de H4.
- **Conversión de sólidos en tazas:** necesidad de tabla de densidades por ingrediente frente a peso provisto por Edamam; decidir prioridad (peso del proveedor > densidad propia).
- **Cache de catálogo:** política de refresco (TTL vs. invalidación manual) y límites de cuota de Edamam.
- **Modelo multi-proveedor LLM:** el prompt debe funcionar por igual en modelos sin tool-calling (fallback a JSON estricto).
