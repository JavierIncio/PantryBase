# PantryBase — Roadmap

Hoja de ruta por hitos. Cada hito termina con una demo jugable o un incremento verificable ("definition of done": testes verdes, lint/format limpio, coverage con quality gate, despliegue en el entorno correspondiente y documentación de la porción de API en OpenAPI).

## Criterios de priorización

1. **Núcleo de dominio antes que brillo:** el motor de unidades, inventario y descontado son el valor diferencial.
2. **Integraciones antes que social:** USDA FDC, TheMealDB y LLM proporcionan el contenido; SPAs de favoritos/historial vienen después.
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
- [x] Perfil: identidad editable (username + nombres; handle respetado en el enlace social) (sept 2026).
- [x] Rate limiting en Redis por usuario + endpoint (token bucket).
- [x] Tests del token bucket deterministas: el `refill-interval-millis` de los tests queda desactivado a efectos prácticos (`3600000`) porque los tests de agotamiento afirman el `remaining` exacto mientras queman toda la capacidad, y una reposición a mitad de bucle los volvía flaky; la reposición se cubre aparte en `TokenBucketRefillIntegrationTest`, que ejecuta el script Lua con un reloj controlado (el script recibe `now` como argumento) en vez de dormir, lo que además cubre las ramas de saturación y de reloj medido desde la última reposición.
- [x] Cambio/establecimiento de contraseña (`PUT /api/auth/password`) (sept 2026).
- [x] Restablecimiento de contraseña (token por mail) (sept 2026).
- [x] Frontend: pantalla de login, guardas de rutas, interceptores de JWT.
- [x] Frontend: páginas de restablecimiento de contraseña (forgot + reset) y cambio de contraseña desde el perfil (F6, sept 2026).
- [x] Frontend: pantalla de perfil: preferencias y exclusión de alérgenos (F2, sept 2026).

**Definition of done:** permisos por rol y datos de preferencias persistidos; sesión expirada redirige al login.

**Estado H1 (sept 2026):** bloque H1-A y H1-B implementados en backend: registro/login/logout y refresh con JWT (JJWT), refresh token hasheado en BBDD con rotación, cookie `httpOnly` para el refresh, `GET /api/users/me`, login OAuth2 Google con link-or-create y rate limiting en Redis (token bucket). Pendiente dentro de H1: pantalla de perfil en el frontend (F2). **Bloque H1-C cerrado (sept 2026):** perfil (`UserPreferences` con `@Id @MapsId` + `Persistable` para clave asignada, valores por defecto sin fila persistida vía dirty-checking), exclusión de alérgenos (14 códigos UE seedeados, contrato estricto — `"peanut"`/`"EGGS"` → 400) y catálogo `GET /api/users/allergens` con orden determinista. **41 tests de integración verdes y gate JaCoCo verificado.** **Bloques H1-A y H1-B cerrados en backend: 30 tests de integración verdes** (registro, login, refresh con rotación y reuso de token detectado, logout y rate limiting); el gate JaCoCo (≥0.80) verificado en el cierre de la bola (84,4%). **Bloque F1 frontend cerrado (sept 2026):** login/registro y callback OAuth2 públicos fuera del shell, guarda de rutas con restauración de sesión en arranque vía cookie `httpOnly` (D2: `POST /api/auth/refresh` + `GET /api/users/me`), interceptor JWT con `Bearer` y refresh single-flight con reintento único; proxy dev `/api` → `localhost:8080` sin CORS; 30 tests Vitest verdes y `ng build` limpio; requiere Node ≥ 24.15 (Angular CLI 22). **Bloque F2 frontend cerrado (sept 2026):** pantalla `/profile` con dos tarjetas (Preferences con `mat-select` de FilterMode/Diet + `mat-slider` de umbral; Allergy exclusions con checklist del catálogo de 14 alérgenos), guardado explícito con `PUT` de reemplazo, estados de carga/error/reintento por tarjeta y feedback vía snackbar; **45 tests Vitest verdes (30 previos + 15 nuevos)** y `ng build` limpio (budget inicial subido a 600 kB); cero dependencias nuevas. **Bloques F3–F5 cerrados (sept 2026):** CTA «Sign in/up with Google» en login y registro (flujo OAuth2 arrancable desde la SPA con proxy dev `/oauth2/authorization` → backend), callback de la SPA movido a `/auth/callback` fuera del namespace `/oauth2` de Spring, servicio `pantry-api` añadido al stack de compose (`env_file: .env` para secretos + `environment:` para enrutado) e invariante de perfil `threshold == 100 ⟺ STRICT` (slider bloqueado en 100 en modo estricto, normalización en carga); **49 tests Vitest verdes y build limpio.** **Bola identidad editable cerrada (sept 2026):** `PUT /api/users/profile` (`UpdateProfileRequest`: username/nombres opcionales — `null` = «no cambiar» en username, «limpiar» en nombres; validaciones espejo de `RegisterRequest`), unicidad de username excluyendo a sí mismo → 409, el enlace OAuth ya **no sobrescribe el handle** del usuario local y la derivación en creación es única ante colisión (`prefijo` → `prefijo1`…); el username queda editable para todas las cuentas — **el email sigue inmutable** (credencial de login y ancla del enlace); **48 tests de integración verdes y gate JaCoCo verificado.** **Bloque B3 cerrado (sept 2026):** restablecimiento de contraseña por mail — `POST /api/auth/password-reset-token` (siempre 204, anti-enumeración) y `POST /api/auth/password-reset` (204/400, un solo uso, TTL 15 min). Token `SecureRandom` 32 B en hex, **hash SHA-256** en BBDD (misma técnica que los refresh tokens); envío con `spring-boot-starter-mail` vía Mailpit `v1.31.0` y plantilla HTML externa con placeholders, detrás del puerto `PasswordResetMailer` + adaptador SMTP. El reset revoca todos los refresh tokens del usuario; ambos endpoints públicos quedan sujetos al rate limiting por IP existente. **61 tests de integración verdes y gate JaCoCo verificado (90%).**

---

## H2 — Catálogo de ingredientes y motor de unidades

**Objetivo:** el modelo de medidas estandarizadas, corazón del dominio.

**Estado H2 (sept 2026):** **bloque H2-A cerrado.** Motor de unidades: migración `V5__catalog_units.sql` (catálogo `units` con `code` como PK natural y flag `canonical`, tabla factor-estrella `measure_conversions`, densidades por categoría `ingredient_densities`, 11 unidades US customary seedeadas), entidades `Unit`/`MeasureConversion`/`IngredientDensity` con `UnitCategory`, y el puerto de dominio `UnitConverter` implementado por `UnitConversionService` (normalización al ancla `GRAM`/`ML`/`UNIT` mediante `canonical`, conversión intra-categoría por factor, cruce volumen↔peso por densidad y `UnsupportedConversionException` para parejas sin sentido como COUNT↔WEIGHT). Frontera HTTP: `GET /api/units` (catálogo determinista por `code`) y `POST /api/units/convert`, con DTOs en la frontera, validación de forma en el record (`@PositiveOrZero` amount, `@NotBlank` unidades) y validación de significado en el dominio. Política de errores: `400` para todo fallo atribuible al cliente (unidad desconocida, categoría ausente, densidad no registrada, conversión no soportada) y `500` para un factor ausente en `measure_conversions`, que es un defecto de nuestros seeds; ese caso se blinda además con un test de integración que verifica que toda unidad no canónica tiene factor, de modo que un seed incompleto rompe el build y no la conversión. **23 tests de catálogo verdes (12 unitarios + 11 de integración con Testcontainers), 84 en total, gate JaCoCo verificado.** **Bloque H2-B-1 cerrado (sept 2026):** integración USDA FoodData Central detrás del puerto `FoodCatalogPort` (el dominio queda desacoplado del proveedor): adapter `FdcFoodCatalogClient` con wire records sobre `RestClient` — el bean se construye a mano en `FdcClientConfig` (con timeouts) porque `spring-boot-restclient` no aporta `RestClient.Builder` al runtime con `spring-boot-starter-webmvc` —, `FdcProperties` desde entorno (`USDA_FDC_API_KEY`), búsqueda en caliente `GET /api/catalog/ingredients?query=` (rechaza query en blanco con 400) y detalle `GET /api/catalog/ingredients/{fdcId}` con **materialización idempotente**: `INSERT ... ON CONFLICT (fdc_id) DO NOTHING` + re-lectura, de modo que la BD arbitra las carreras (verificado con un test de 2 hilos concurrentes → una sola fila y el mismo id). Migración `V6__catalog_ingredients.sql` (columna UNIQUE `fdc_id`, nutrientes `DOUBLE PRECISION`), DTOs en la frontera (`IngredientSearchResponse`, `IngredientDetailResponse`) y política de errores 400/404/502 (`IllegalArgumentException`, `IngredientNotFoundException`, `FdcProviderException` con `log.error`). **12 tests nuevos verdes (5 unitarios del adapter con `MockRestServiceServer` + 7 de integración con Testcontainers y stub `@Primary`), 96 en total; gate JaCoCo verificado (87,7 %).** Pendiente en H2-B: lote `POST /foods` (≤ 20 ids), porciones domésticas (`foodPortions` → densidades) y caché en BBDD/Redis. Deuda anotada: densidades de harina/azúcar/aceite pendientes de revalidar contra las porciones domésticas de USDA FDC (`foodPortions`, peso en gramos) en H2-B.

**Bloque H2-B-2 cerrado (oct 2026):** lote `POST /foods` de USDA FDC: el puerto `FoodCatalogPort.getByIds` devuelve `Map<Long, FoodProfile>` con contrato explícito (ids desconocidos se omiten — la ausencia es dato normal — y el fallo del proveedor no-2xx lanza `FdcProviderException`); el adapter llama al endpoint de lote con `format=full` reutilizando el wire del detalle y protegiéndose de `foodNutrients` nulos; el servicio `IngredientCatalogService` deduplica con `LinkedHashSet`, trocea en llamadas de ≤20 ids (techo de FDC), rechaza colecciones vacías (`IllegalArgumentException`) y materializa cada perfil mediante `materializeAndMerge` compartido con `getById`. Sin endpoint HTTP (YAGNI hasta H3/H4). 9 tests nuevos (4 unitarios del adapter —incluida la aserción del body serializado con `content().json`— + 5 de integración que verifican la materialización, la omisión de desconocidos, exactamente 2 llamadas [1..20],[21..25] para 25 ids, y la ausencia de filas ante fallo o lista vacía) y gate JaCoCo verificado (88,15 %).
- [x] Entidades `Unit`, `MeasureConversion` y tabla de densidades (inicialmente por categoría, depois por clase propia: ver H2-B-4).
**Bloque H2-B-5 cerrado (oct 2026):** la curación de clases de densidad pasa a ser operable por API. `density_classes.source` (nuevo, `V9__catalog_density_class_sources.sql`) registra **de dónde sale cada densidad**, para que un valor sea auditable y no un número desnudo; el seed añade solo `MILK` = 1,031288 g/ml derivado de la porción del propio proveedor (fdcId 171265: 1 cup = 244 g), y **no** añade `CHEESE`: FDC fdcId 169901 da 1 cup = 224 g para el cheddar rallado (0,946715 g/ml) mientras un bloque ronda 0,40 g/ml, de modo que una única clase "queso" desviaría más del doble según la forma y no cumple el criterio de clase estrecha. Servicios y frontera: `DensityClassService` (separa la curación de nuestros datos de la orquestación del proveedor, que es justo el campo que la materialización no debe tocar nunca) y `DensityClassController` con `GET /api/catalog/density-classes` (lectura abierta a cualquier autenticado, porque el cliente necesita saber qué clases existen para prever si una conversión va a funcionar) y `PATCH /api/catalog/density-classes/ingredients/{id}`, restringido a `ROLE_ADMIN` porque muta datos compartidos por todos los usuarios y una clase sin verificar falsearía en silencio las recetas de cualquiera. La asignación **exige que la clase ya exista** (`DensityClassNotFoundException`), lo que impide que este endpoint se convierta en una vía para introducir densidades sin justificar. Se habilita `@EnableMethodSecurity` en `SecurityConfig` porque hasta ahora no había `@PreAuthorize` en el proyecto y sin method security la anotación se ignoraría en silencio y el endpoint caería en la regla de URL (autenticado), es decir, abierto a cualquiera. `IngredientDetailResponse` gana `densityClass` para que el frontend sepa si un ingrediente es convertible. 7 tests de integración nuevos (listado con procedencia y orden, requiere autenticación, convierte un alimento sin porciones tras asignar, 403 para no-admin, 400 para clase inexistente, 404 para ingrediente desconocido, y la clase sobrevive a un lookup posterior y se refleja en el detalle) más el helper `authenticateAsAdmin()` en `AbstractIntegrationTest`, que concede el rol en BD y **vuelve a hacer login** para que el claim llegue en el JWT. **141 tests verdes**, gate JaCoCo verificado.

**Bloque H2-B-4 cerrado (oct 2026):** la densidad deja de resolverse por la **categoría del proveedor** y pasa a resolverse por una **clase propia curada por ingrediente**, porque la categoría FDC es demasiado gruesa para soportar una densidad. Evidencia recogida contra la API real: la leche (`171265`) y el cheddar (`170899`) comparten `foodCategory` `0100 Dairy and Egg Products`, pero la leche trae 1 cup = 244 g (≈1,03 g/ml) y el queso en lonchas solo trae porciones `undetermined` (que el adapter descarta) con una densidad de ≈0,40 g/ml: un único número para esa categoría desviaría el peso **más de un factor 2**, y un error así acaba callado en el cálculo de nutrientes de una receta. Decisión: fallar ruidosamente (400) antes que adivinar. Migración `V8__catalog_density_classes.sql`: `ingredient_densities` se renombra a `density_classes` (PK `code`) y `ingredients` gana `density_class VARCHAR(50)` con FK a esa tabla; la columna es **dato curado** y por eso se documenta que la materialización nunca la toca (solo hace `INSERT ... ON CONFLICT DO NOTHING`, así que sobrevive a cada lookup del proveedor). Renombres para que el modelo no vuelva a inducir el error: `IngredientDensity` → `DensityClass`, `IngredientDensityRepository` → `DensityClassRepository`, `IngredientDensityNotFoundException` → `DensityClassNotFoundException`, `IngredientCategoryRequiredException` → `DensityClassRequiredException`, y el campo del DTO `ingredientCategory` → `densityClass`. Escalera de precedencia final: (1) medida exacta de la unidad de volumen involucrada → (2) cualquier otra medida del ingrediente → (3) **clase curada del ingrediente** (más específica que la que pase el cliente) → (4) clase pasada en `densityClass` → (5) `DensityClassRequiredException`. La categoría FDC se conserva en `ingredients.category` como metadata de búsqueda, pero **nunca** como fuente de densidad. Tests nuevos: 3 unitarios (no usa la categoría del proveedor, la clase del ingrediente gana sobre la pasada, requiere clase) y 4 de integración (convierte con clase curada sin pista, la clase sobrevive a un lookup posterior que además captura medida, la categoría por sí sola sigue dando 400), más los existentes renombrados. **134 tests verdes**, gate JaCoCo verificado.

**Bloque H2-B-3 cerrado (oct 2026):** porciones domésticas de USDA FDC (`foodPortions`) convertidas en **medidas por ingrediente**, que tienen prioridad sobre la densidad de la clase. Migración `V7__catalog_ingredient_measures.sql`: tabla `ingredient_measures` con PK compuesta `(ingredient_id, unit_code)`, `CHECK (gram_per_unit > 0)` y FK a `ingredients`; la misma migración añade la unidad `FLOZ` (fluid ounce US customary) con factor `29.5735`, dejando el catálogo en 12 unidades. Dominio: record `Portion(unitCode, gramPerUnit)` y `FoodProfile` ampliado con sus porciones; entidad `IngredientMeasure` con clave compuesta `IngredientMeasureId`. Adapter: `gramPerUnit = gramWeight / amount` con `amount` nulo o ≤ 0 tratado como 1; solo se capturan **unidades de volumen** (`TSP`, `TBSP`, `CUP`, `FLOZ`, `PINT`) porque `oz`/`lb` son pesos ya cubiertos por `measure_conversions`; el nombre de la unidad se resuelve por la **medida base antes de la coma**, porque FDC califica la porción con el modo de preparación (`cup, nf`, `cup, chopped`, `tbsp, level`) y todas las calificaciones del mismo volumen describen el mismo volumen; si el proveedor repite unidad, gana la primera porción reconocida. Servicio: `materializeAndMerge` persiste las porciones con `ON CONFLICT DO NOTHING` (idempotente y apta para carreras, como ya se hacía con el ingrediente). Conversión: `POST /api/units/convert` acepta el nuevo campo **opcional** `ingredientId`; para el cruce volumen↔peso la precedencia es (1) medida propia del ingrediente para la unidad de volumen involucrada, (2) densidad de categoría (la del ingrediente si viene `ingredientId`, si no la que se pase). La medida se usa como **ratio `mlPorUnidad` ↔ `gramosPorUnidad`** en lugar de aplanarse a una densidad redondeada, de modo que 2 tazas de un ingrediente de 244 g/taza son exactamente 488 g y no 488,000130 g. Un `ingredientId` inexistente es error del cliente (404) y **no** degrada en silencio a la conversión por categoría, aunque se pase categoría; sin medida, sin ingrediente y sin categoría se mantiene `IngredientCategoryRequiredException` (400). También se añadió que **cualquier otra medida capturada del ingrediente defina la densidad** (se elige la de mayor volumen, la más precisa), de modo que un ingrediente del que solo capturamos la cucharada siga conviertiendo tazas sin abandonar sus propios datos. Escalera de precedencia final: (1) medida exacta de la unidad de volumen involucrada → (2) cualquier otra medida del ingrediente → (3) densidad de categoría → (4) `IngredientCategoryRequiredException`. Deuda que queda anotada: `ingredient_densities` sigue con claves `FLOUR`/`SUGAR`/`OIL` y `Ingredient.category` trae descripciones FDC (`Dairy and Egg Products`), así que el peldaño (3) no acierta con datos reales; resolverlo exige un mapeo curado FDC → densidad (tarea de seed, ya prevista abajo), no una tabla inventada. 15 tests nuevos (5 unitarios del adapter, 6 unitarios del servicio, 6 de integración) más los existentes actualizados por la firma nueva; **129 tests verdes**, gate JaCoCo verificado (88,1 %).
- [x] Entidad `Ingredient` con nutrientes y categorías (migración `V6__catalog_ingredients.sql`; llega con USDA FDC en H2-B).
- [x] Conversor de unidades: medidas comunes (taza, cdta., cda., oz, lb, pinta…) → canónicas (`GRAM`/`ML`/`UNIT`) usando conversión base + densidad.
- [x] Integración **USDA FoodData Central** (v1), bloque H2-B-1: búsqueda (`/foods/search`) y detalle por `fdcId` con parseo de nutrientes por `nutrientId` (1003/1004/1005/1008).
- [x] Integración **USDA FoodData Central** (v1), bloque H2-B-2: lote (`POST /foods` ≤ 20 ids, deduplicado y troceado).
- [x] USDA FDC: porciones domésticas (`foodPortions` → medidas por ingrediente), bloque H2-B-3.
- [x] Densidad por **clase propia** y curada por ingrediente, bloque H2-B-4 (cierra el hueco de los alimentos sin porciones: queso, Marius, etc.).
- [x] Administración de clases de densidad, bloque H2-B-5: listado de clases con su procedencia y endpoint de asignación protegido por `ROLE_ADMIN`.
- [ ] USDA FDC pendiente: caché en BBDD/Redis (y revalidación de las densidades de harina/azúcar/aceite frente a las porciones ya capturadas).
- [ ] Sincronización/refresh de datos de nutrientes y categorías.
- [ ] Contenido curado de `density_classes`: solo hay `FLOUR`/`SUGAR`/`OIL` (de H2-A) y `MILK` (verificada en H2-B-5). Falta decidir la fuente de referencia de las densidades que no se pueden derivar de una porción FDC, y ampliar el catálogo solo con valores citables. Se ha descartado el mapeo curado FDC → densidad por lo demostrado en H2-B-4.

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

- [ ] Integración **TheMealDB API** (v1): búsqueda de recetas por nombre e ingrediente (test key pública); recetas materializadas en BBDD con `Recipe.source = THE_MEAL_DB` y refresco por TTL.
- [ ] Persistencia de `Recipe` + `RecipeIngredient` (cantidades originales del proveedor y canónicas) + nutrientes por ración calculados localmente desde el catálogo USDA (motor de unidades como puente) + marcador de origen `Recipe.source` (`THE_MEAL_DB` o `USER`).
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
- [ ] Publicar receta propia: formulario de receta manual (ingresar/editar ingredientes y pasos) que alimenta el catálogo reutilizando el modelo local de `Recipe` con `source = USER`.

**Definition of done:** toda la interacción social requiere auth; historial 100% consistente con las sesiones completadas.

---

## H8 — Producción y endurecimiento

- [ ] Hardening de seguridad: rate limiting por IP, auditoría de llamadas externas, secretos en Vault/CI.
- [ ] Dashboards de Grafana finales: salud, cuotas de APIs externas (USDA FDC, TheMealDB, LLM), cobertura de despensa, rendimiento del recomendador.
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

- **Fuente de verdad para alérgenos:** ni USDA FDC ni TheMealDB garantizan campo alérgeno directo; validar el mapeo propio sobre la descripción e ingredientes antes de H4.
- **Conversión de sólidos en tazas:** resuelta en H2-B-3 y H2-B-4, con la prioridad final porción FDC > medida propia > clase de densidad curada; la categoría del proveedor queda descartada como fuente de densidad.
- **Cache de catálogo y recetas:** política de refresco (TTL vs. invalidación manual), cuota de USDA FDC (~1.000 req/h/IP; 429 + bloqueo 1 h) y respeto de la etiqueta de TheMealDB (cachear recetas, no servirlas en caliente).
- **Modelo multi-proveedor LLM:** el prompt debe funcionar por igual en modelos sin tool-calling (fallback a JSON estricto).
