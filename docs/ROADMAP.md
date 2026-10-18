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
- [x] Integración **USDA FoodData Central** (v1), bloque H2-B-1: búsqueda (`/foods/search`) y detalle por `fdcId` con parseo de nutrientes (1003/1004/1005/1008). **Corregido en H2-B-7:** la API anida el id en `foodNutrients[].nutrient.id` y llama `amount` a la cantidad, no `nutrientId`/`value`; con la forma anterior el mapeo devolvía siempre nulos.
- [x] Integración **USDA FoodData Central** (v1), bloque H2-B-2: lote, deduplicado y troceado. **Corregido en H2-B-7:** el endpoint real es `GET /foods?fdcIds=` (el `POST` con body JSON se rechaza con 400 *Invalid request*) y responde un array JSON, no un objeto `{"foods": [...]}`; el troceo pasa a 100 ids, límite propio prudencial ante la longitud de la URL, ya que 200 ids se aceptaron contra la API real.
- [x] USDA FDC: porciones domésticas (`foodPortions` → medidas por ingrediente), bloque H2-B-3. **Corregido en H2-B-7:** el nombre de la unidad real está en `modifier`, no en `measureUnit.name` (que el proveedor devuelve como `"undetermined"`), por lo que el adapter descartaba todas las porciones reales.
- [x] Densidad por **clase propia** y curada por ingrediente, bloque H2-B-4 (cierra el hueco de los alimentos sin porciones: queso, Marius, etc.).
- [x] Administración de clases de densidad, bloque H2-B-5: listado de clases con su procedencia y endpoint de asignación protegido por `ROLE_ADMIN`.
- [x] Caché y frescura del catálogo USDA FDC, bloque H2-B-6: `ingredients.synced_at` marca la última lectura exitosa del proveedor y `ingredients.data_type` completa el contrato de la respuesta. El detalle se resuelve sin llamar al proveedor dentro de `cache-ttl` (24 h), refresca fuera de él, y sirve copia local si el proveedor falla dentro de `max-stale` (30 d) — pasado ese plazo responde 502. La búsqueda se cachea en Redis (única consulta sin copia local). Un 429 da lugar a `FdcRateLimitException`: se sirve stale sin reintentar y se cuenta en `pantry.catalog.fdc.quota.exhausted`.
- [x] Sincronización/refresh de datos de nutrientes y categorías: `refreshProviderFields` sobrescribe solo las columnas que el proveedor posee (nombre, categoría, `data_type`, nutrientes) y sella `synced_at`; nunca toca `density_class` ni `ingredient_measures`, que son datos curados nuestros. Antes, la materialización era solo-insert y una corrección de nutrientes nunca llegaba a la BBDD.
- [x] Contenido curado de `density_classes`: `FLOUR`/`SUGAR`/`OIL` (H2-A) revalidados en H2-B-7 y `MILK` en H2-B-5, los cuatro con procedencia registrada (`V9__catalog_density_class_sources.sql`, ampliada por `V11__catalog_density_class_revalidation.sql`). Revalidar una densidad derivándola de la porción del propio proveedor es lo que hace citable el valor: `FLOUR` 0,53 confirmado con 1 cup = 125 g (fdcId 168894/169761, y también 789890), `OIL` 0,92 confirmado con tres medidas que concuerdan en fdcId 172370 (218 g/cup, 13,6 g/tbsp, 4,5 g/cdta), `SUGAR` 0,85 confirmado en SR Legacy (fdcId 169655, 200 g/cup). Ningún valor cambia: lo que cambia es que dejan de ser constantes sin fuente. Se documenta además la discrepancia de `SUGAR` (~6 %) entre SR Legacy (200 g/cup) y Foundation (fdcId 746784, 188 g/cup) en lugar de promediarla, porque un promedio sería un número que ningún alimento del catálogo mide. Se mantiene el descarte del mapeo curado FDC → densidad demostrado en H2-B-4, y `CHEESE` sigue sin entrar por la misma razón de anchura de clase.

**Bloque H2-B-7 cerrado (oct 2026):** revalidación de las densidades curadas contra la API real, que hizo falta porque los tres bloques anteriores se verificaron con fixtures escritos a partir de los records del adapter y no contra una respuesta real — y los tres tenían un desajuste de wire que los tests no podían ver. Lo que se encontró, en orden de gravedad: (1) los nutrientes reales llegan como `foodNutrients[].nutrient.id` + `amount`, no `nutrientId` + `value`, de modo que el adapter leía **siempre** nulos y ningún ingrediente importado tenía calorías ni macronutrientes; (2) la unidad de la porción doméstica está en `foodPortions[].modifier` y `measureUnit.name` vale literalmente `"undetermined"`, así que el bloque H2-B-3 descartaba **todas** las porciones reales y `ingredient_measures` quedaba vacía — el peldaño más alto de la escalera de precedencia no existía; (3) el lote es `GET /foods?fdcIds=` y responde un array, no el `POST` con body JSON que devolvía 400 *Invalid request*. Lección que queda anotada: un fixture de test escrito a partir del código que se quiere probar solo demuestra que el código es consistente consigo mismo; el guard real es una respuesta capturada del proveedor. Los fixtures del adapter pasan ahora a ser un recorte literal de `GET /food/168894`. `FDC_BATCH_MAX` pasa de 20 a 100 (el límite real es la longitud de la URL, no un tope de ids: 200 ids se aceptaron contra la API). `V11__catalog_density_class_revalidation.sql` registra la procedencia de `FLOUR`/`SUGAR`/`OIL` sin cambiar sus valores, todos re-derivados de una porción del propio proveedor y anotando la discrepancia de ~6 % de `SUGAR` entre SR Legacy y Foundation en vez de promediarla. 3 tests nuevos y 6 actualizados en `FdcFoodCatalogClientTest`, gate JaCoCo verificado.

**Definition of done:** convertir `1 taza de harina` → gramos correctos según densidad; el catálogo produce ingredientes buscables con nutrientes.

---

## H3 — Inventario (despensa)

- [x] **H3-1 CRUD `PantryItem`** (ingrediente + cantidad canónica + ubicación/nota): migración `V12`, `GET/POST/PUT/DELETE /api/pantry`, una fila por `(user_id, ingredient_id)`, cantidad normalizada a `GRAM`/`UNIT` al entrar y unidad inmutable, edición sin campo de cantidad, y aislamiento por propietario con `404` en lugar de `403`. La tabla `consumption_entries` se crea ya en V12 aunque la escriba H3-2.
- [x] **H3-3 Frontend «Mi despensa»:** `core/pantry/pantry.service.ts` con los cinco endpoints tipados en un sitio y `pages/pantry/pantry-page.{ts,html,scss}` sustituendo el marcador de posición. Lista agrupada por categoría (sin reordenar en cliente, la API ya viene ordenada), edición inline de ubicación y nota, ajuste de cantidad con signo, borrado con confirmación y lectura del libro de movimientos, todo con estados de carga, vacío y error. 28 tests con Vitest; `npm test` en 116 y `npm run build` sin avisos. El alta desde el catálogo sigue sin pantalla: es bloque aparte.
- [x] **Alta rápida de H3 (H3-4):** buscador de catálogo → detalle → cantidad y unidad, en un **diálogo** sobre la lista para no perder de vista la despensa. `core/catalog/` con `catalog.service.ts` (búsqueda y detalle) y `unit.service.ts` (etiquetas de unidad desde `GET /api/units`, una vez por sesión), más `unit-groups.ts` como función pura que agrupa por categoría y pone las medidas del ingrediente primero con su peso, sin filtrar ninguna unidad. El `409` no es un callejón: entrega el `fdcId` a la página, que localiza la línea y le abre el panel de ajuste. El `404` se repara con la llamada que el propio mensaje nombra, ofrecida y no disparada (cuesta cuota). Nada reintenta solo, porque el `502` del USDA significa tanto proveedor caído como **cuota agotada** (30 req/h sin key). El `POST` lleva `fdcId` y nunca el id interno. **Sustituye el mapa local de unidades** que duplicaba el catálogo semilla. 40 tests nuevos; `npm test` en 158 y `npm run build` sin errores (el umbral de aviso del bundle inicial subió aparte a 650 kB: la subida real fueron 3 kB de *vendor* reubicado, no código de la feature, que va en el chunk perezoso de `pantry-page`).
- [x] **H3-2 Ajuste de cantidades con auditoría** (`ConsumptionEntry`): `POST /api/pantry/{id}/adjustments` con cantidad **con signo** en cualquier unidad (convertida a la unidad de la línea, que es inmutable) y `GET /api/pantry/{id}/entries` para leer el libro. El alta escribe también su asiento, de modo que el invariante «la suma de los asientos de una línea es su cantidad» se cumple desde la primera fila y no solo después del primer ajuste. Un resultado **negativo** se rechaza con `409`; quedar en exactamente cero está permitido, y `V12` se corrigió a `CHECK (quantity >= 0)` para que ese estado sea representable. El cliente no envía la versión: `@Version` decide entre dos escritores concurrentes y el conflicto se traduce a `409`.
- [x] **Métricas: tamaño del inventario, reparto por categoría.** `GET /api/pantry/summary` con `totalItems`, `zeroItems`, `byCategory` y `byUnitType`. **Una sola query `GROUP BY i.category, u.category`** y los dos desgloses se pliegan en Java desde la misma rejilla: dos queries de conteo para un mismo número pueden discrepar, y el cliente no tendría forma de saber cuál creer. Por eso `totalItems` es la suma de `byCategory` y no un segundo conteo. **Una línea en cero cuenta en el total y además se publica su número**, porque el cero es representable a propósito y un total que lo dejara fuera diría que hay menos cosas de las que la lista muestra. La categoría ausente la nombra el servidor (`"Sin categoría"`), para que el cliente no tenga que inventar un encabezado. El reparto por tipo de unidad responde «de qué está hecha esta despensa»: pesos, volúmenes y cuentas no son intercambiables, y un cliente no puede deducirlo de los códigos. En la cabecera de «Mi despensa» solo se imprimen los totales: **el desglose por categoría no se dibuja porque la lista ya lo muestra como secciones**. 9 tests de integración nuevos; **242 en total** con el gate de JaCoCo superado.

**Decisiones de diseño de H3 (acordadas):** la cantidad se guarda en `GRAM` siempre que se pueda y en `UNIT` para lo que se cuenta; una sola fila por ingrediente, así que añadir algo que ya está devuelve `409` y el cliente usa el ajuste; la edición (`PUT`) sustituye ubicación y nota y **no** admite cantidad, de modo que saltarse la auditoría es imposible por contrato y no por convención.

**Por qué un resultado negativo se rechaza y el cero no:** un déficit dejaría cocinar con ingredientes que no existen —la receta diría que hay 400 g y la ausencia de error diría lo mismo—, mientras que una línea en cero dice la verdad y además obliga al usuario a decidir qué hacer con ella. Por eso `409` y no `400`: la petición es válida, lo que no admite el estado.

**Nota sobre `toStorageUnit`:** la regla "a gramos cuando se pueda" vive en el puerto `UnitConverter`, no en el servicio de despensa, porque H4 necesitará exactamente la misma resolución para las líneas de receta. Además **conserva la unidad de entrada** al convertir: canonicalizar antes perdería la medida del ingrediente en esa unidad concreta, que es el peldaño más preciso de la escalera de densidades. Cuando un volumen no tiene densidad conocida se guarda en `ML` en lugar de fallar: el hueco está en nuestros datos, no en la petición, y el usuario sí posee esa cantidad.

**Definition of done:** despensa editable, cantidades siempre en unidad canónica y consistentes con la auditoría.

---

## H4 — Recetas y filtrado por disponibilidad

**Objetivo:** recomendación basada en la despensa (el diferencial del producto).

- [x] Integración **TheMealDB API** (v1): búsqueda de recetas por nombre e ingrediente (test key pública); recetas materializadas en BBDD con `Recipe.source = THE_MEAL_DB` y refresco por TTL.
- [x] Persistencia de `Recipe` + `RecipeIngredient` (cantidades originales del proveedor y canónicas) + nutrientes por ración calculados localmente desde el catálogo USDA (motor de unidades como puente) + marcador de origen `Recipe.source` (`THE_MEAL_DB` o `USER`).
- [x] Derivación de alérgenos por receta (mapeo interno sobre ingredientes) y filtrado por exclusiones del usuario.
- [x] Motor de cobertura: ratio = cantidad disponible / cantidad necesaria por ingrediente → **match score** de la receta.
- [x] Filtrado **estricto** (requiere 100% según umbral de cantidad) y **laxo** (porcentaje de cobertura configurable).
- [x] Slider de umbral (0-100%) con recalculado en tiempo real.
- [x] Frontend: grid de recetas con badges de cobertura, detalles de receta, enlaces al step completo.

**Backend del bloque (oct 2026):** `RecipeController` opera cuatro endpoints (`GET /api/recipes`, `GET /api/recipes/recommend`, `GET /api/recipes/{id}`, `POST /api/recipes`) con el motor de cobertura binario (sin crédito parcial), el filtrado estricto/laxo por `threshold` y `allergenSafe` sobre `user_allergy_exclusions` de `V3`. La búsqueda por nombre consulta TheMealDB con caché TTL en Redis y **materializa los hits frescos**: empareja los ingredientes del proveedor con el catálogo por contención de tokens y persiste `Recipe` + líneas canónicas + alérgenos + nutrientes por ración en una transacción `REQUIRES_NEW` (la entrada se hace desde la transacción de solo lectura de `search`). **Cobertura honesta (V14):** todas las líneas del proveedor se persisten —las que no emparejan con `matched=false`, `ingredient_id` nulo y el texto original—, de modo que el denominador de `matchScore` es la receta real y una parcial nunca afirma 100 % (contraejemplo que lo destapó: «Apple Pie» 53575, 2/7 con despensa de quinoa-flour y huevo); el detalle expone esas líneas en `ingredients` y `coverage` solo lista las emparejadas. **`GET /api/recipes/recommend`** cierra el diferencial del objetivo: toma hasta 8 ingredientes de la despensa (cantidad > 0, los de mayor cantidad primero), consulta `filter.php` con su primera palabra (heurística baseWord, sin tabla de alias), reparte hasta 24 hits por **cuota justa** (cada palabra de la despensa reclama un hueco por ronda antes de repetir; los huecos que dejan las listas cortas los rellena el sobrante al final, en el mismo orden de despensa) y los materializa con la misma tubería y los puntúa con `threshold` forzado a 0 (el rango completo cae al slider del cliente); `strict` y `allergenSafe` se respetan. **`GET /api/recipes` con `ingredientId`** cierra el bloque: resuelve la palabra base del `Ingredient` para `filter.php` (caché por palabra compartida con `/recommend`), materializa el set completo de hits y los puntúa igual que los locales; un `ingredientId` inexistente devuelve `404` (no un vacío silencioso). **Refresco por TTL (H4-h):** `V15__recipe_refresh.sql` añade `refreshed_at` (NOT NULL, default `now()`); las filas `THE_MEAL_DB` que superan `app.recipe.refresh-ttl: 24h` se re-consultan al proveedor **in-place** (mismo `Recipe.id`) al abrir el detalle o en búsquedas filtradas; el listado sin filtrar es local. 29 tests de integración en `RecipeControllerIntegrationTest` (25 previos + 4 de refresco por TTL). **271 tests verdes y JaCoCo al 87,34 %.**
 
 **Frontend del bloque (oct 2026):** la página de Recetas (`recipes-page`) con buscador por nombre con debounce, slider de umbral **100 % en cliente** (nunca relanza la búsqueda), toggles de modo estricto (fija el slider al 100) y `allergenSafe`, y una grid de tarjetas con badge de cobertura, faltantes y origen (`USER` / `THE_MEAL_DB`). Preliminarmente el perfil **manda al abrir** (`GET /api/users/preferences` fija el slider y el modo estricto iniciales; el slider queda como refinamiento de sesión), toda búsqueda envía `threshold=0` (el servidor devolvía el set completo y el rango bajo del slider estaba muerto), y el botón **Suggest** llama a `/recommend` reutilizando la grid con estados propios (vacío, carga y error diferenciados de la búsqueda). El detalle (`recipes/:id`) desglosa la cobertura línea a línea (necesario vs. disponible), muestra nutrientes por ración, alérgenos y enlaza al modo cocina, y además **pinta las líneas no catalogadas** (`matched=false`) de forma atenuada con el nombre del proveedor, sin cantidades y con leyenda («not in catalog») — ocultarlas volvería a inflar el %. El buscador añade un **selector de ingrediente** (autocomplete de catálogo, debounce 300 ms) que envía `ingredientId` junto a los demás filtros; el id es el interno, así que al elegir se lee el detalle `GET /api/catalog/ingredients/{fdcId}` para descubrirlo; un `404` de ingrediente obsoleto se muestra como estado propio (no como vacío) y `/recommend` ignora el filtro. **226 tests de frontend verdes (22 ficheros).**

**Pendiente del bloque:** ninguno — H4 queda cerrado (backend + frontend + docs).

**Definition of done:** para un inventario dado, el modo estricto muestra solo recetas factibles y el laxo ordena por score; el slider recalcula sin llamada.

---

## H5 — Modo cocina (progreso y descontado)

**Objetivo:** seguir la receta y reflejar el consumo en la despensa.

- [x] `CookingSession` con estados: `IN_PROGRESS`, `PAUSED`, `COMPLETED`, `ABANDONED` (backend).
- [x] Marcar pasos/progreso de la receta desde el frontend (checklist local, no persistida; el backend solo guarda el estado).
- [x] Al **completar** sesión: generar `ConsumptionEntry` automáticos (descontar cantidades usadas) con confirmación previa (backend).
- [x] Descontado manual desde el inventario (sin sesión) (backend).
- [x] Cálculo de stock tras descuento; advertencias si un ingrediente queda a cero (backend: `remaining` en el plan; frontend: aviso «Runs out» en las líneas que se vacían).
- [x] Métricas: duración de sesiones, tasa de finalización (`GET /api/cooking/sessions/summary` + contadores/timer Micrometer).

**Backend del bloque (oct 2026):** el paquete `cooking` abre el modo cocina con `POST /api/cooking/sessions` (`{recipeId}` → 201; 404 si la receta no existe), `GET /api/cooking/sessions` (historial propio, más recientes primero), `GET /api/cooking/sessions/summary` (totales por estado, duración media y tasa de finalización), `GET /api/cooking/sessions/{id}` (404 si la sesión es ajena) y `PUT /api/cooking/sessions/{id}/state` (`{status, acceptShortage}`). La máquina de estados es `IN_PROGRESS → PAUSED | COMPLETED | ABANDONED`, `PAUSED → IN_PROGRESS | COMPLETED | ABANDONED`, y `COMPLETED`/`ABANDONED` terminales (repetir estado → 409 `InvalidCookingStateTransitionException`); `completedAt` se sella al pasar a `COMPLETED` o `ABANDONED`. **Las recetas no tienen pasos estructurados** (solo `instructions` de texto libre), así que la sesión persiste únicamente estados y el marcado de pasos es responsabilidad del frontend. **Descontado al completar:** con las líneas canónicas que dejó H4 (`RecipeIngredient.canonicalAmount` / `canonicalUnitCode`, ya en la unidad de almacenamiento), `CookingService` casa cada línea por `ingredientId` con la despensa, y si todo el stock llega genera los `ConsumptionEntry` con `origin=COOKING_SESSION` (delta negativo) y baja las líneas. **Stock insuficiente:** si alguna línea no llega, la primera llamada devuelve **409 con el plan de descuento** (`CompletionDeductionResponse`, no el `ErrorResponse` habitual) y ningún cambio; el cliente muestra qué falta y, si el usuario acepta, reenvía con `acceptShortage=true`, con lo que se descuenta `min(necesario, disponible)`, la línea queda a 0 y la parte no registrada se asume aportada sin asiento (los huecos off-book no se inventan). **Descontado manual sin sesión:** `POST /api/pantry/{itemId}/consume` (`{amount>0, unitCode}`) reutiliza la misma tubería de conversión y libro mayor con `origin=MANUAL`, y 409 si baja de cero. `ConsumptionOrigin` gana `COOKING_SESSION` (V16 crea `cooking_sessions`). **Métricas:** `GET /api/cooking/sessions/summary` pliega las sesiones del usuario en un solo recuento (`totalSessions` = suma de los cuatro estados, `averageDurationSeconds` sobre las sesiones cerradas, `completionRate = completed / (completed + abandoned)`, ambos `null` sin sesiones cerradas); además `CookingService` incrementa los contadores Micrometer `pantry.cooking.sessions.completed`/`.abandoned` y registra el `Timer` `pantry.cooking.sessions.duration`. **290 tests verdes, cobertura LINE 87,81 %.**

**Frontend del bloque (oct 2026):** `core/cooking/cooking.service.ts` tipa los cuatro endpoints (crear, listar, leer, cambiar estado) siguiendo el patrón de `RecipesService`, y `cooking.models.ts` expone el contrato con uniones de literales (`CookingSessionStatus`) y un helper `asCompletionDeduction()` que **distingue el 409 de carencia del 409 de transición** por la forma del cuerpo (`hasShortage` + `lines` frente al `ErrorResponse`). La ruta `/cooking` pasa de marcador a la **lista de sesiones** (vivas primero, luego historial) con enlace a cada sesión, y una ruta nueva `/cooking/:id` muestra la **sesión activa**: carga la sesión y la receta (`RecipesService.getDetail`), trocea `instructions` en pasos para una **checklist local que no se persiste** (el backend solo guarda el estado) y ofrece Pause/Resume/Complete/Abandon según la máquina de estados, con los estados terminales en solo-lectura. Al completar, si el backend responde 409 con el plan de carencia, un **diálogo Material** lista las líneas cortas (necesario/disponible/se descuenta/queda) y ofrece cancelar o «Cook anyway», que reenvía con `acceptShortage=true`; la respuesta exitosa muestra el resumen de lo descontado. Tanto en el diálogo como en el resumen de deducción, el helper `runsOut()` marca con un aviso «Runs out» las líneas que se vacían (`deducted > 0` y `remaining === 0`). El botón «Start cooking» del detalle de receta ahora **crea la sesión** y navega a `/cooking/:sessionId`. El origen `COOKING_SESSION` se añadió a los modelos y etiquetas del libro mayor de la despensa. **266 tests verdes (28 ficheros)** y build limpio.

**Pendiente del bloque:** nada; H5 cerrado.

**Definition of done:** completar una receta de 3 ingredientes reduce la despensa exactamente en las cantidades canónicas (verificado con tests de integración); un usuario puede ver sus sesiones y sus totales (duración media y tasa de finalización) por API.

---

## H6 — Temas de usuario (Material theming)

**Objetivo:** que el usuario elija su paleta de colores sin salir del sistema de diseño de Angular Material.

- [x] Definir 5 paletas base (azul, verde, ámbar, rosa, neutro) sobre el sistema M3 de Angular Material 22: no existen los tokens M2 (`$theme-blue`, `accent`, `warn`), así que cada paleta es una llamada a `mat.theme(...)` con `color: (primary, tertiary)`. `amber` mapea a `$orange-palette`/`$yellow-palette`, `rose` usa `$rose-palette`/`$magenta-palette` y `neutral` es una paleta gris propia (`src/styles/_neutral-palette.scss`, reutiliza el `error` de azul).
- [x] Generar un tema M3 compilado por paleta y volcar sus system tokens en `:root[data-theme='blue']`, `:root[data-theme='green']`, … de modo que el cambio de `data-theme` en `<html>` sea instantáneo sin recargar (incluidos diálogos, menús y tooltips, que heredan del root).
- [x] Persistencia: `themeId` en `UserPreferences` (`GET/PUT /api/users/preferences`, migración `V17`), serializado en minúsculas (`@JsonValue`) para casar con el `data-theme`. En el frontend, `ThemeService` aplica `document.documentElement.dataset.theme` y cachea en `localStorage`; `App` arranca con el valor cacheado (o `blue`) y lo reconcilia con las preferencias del servidor.
- [x] Selector en «Perfil / Preferencias»: `mat-button-toggle-group` con las 5 paletas y preview *token-driven* (`--mat-sys-primary`/`tertiary`/`surface-container-highest`), accesible (`aria-labelledby`) y con contraste AA en todas.
- [x] **Sin Tailwind ni FontAwesome**: todo el estilo vive en el theming nativo de Material; los componentes (`<mat-icon>`, `<mat-menu>`, `<mat-select>`, chips, badges, steppers) conservan sus iconos Material y su comportamiento.
- [x] Tests: cambio de tema persiste tras recarga; `ThemeService`/`App`/preferencias cubiertos con Vitest; ninguna regla de componente se rompe al cambiar `data-theme` (los componentes standalone con `styleUrl` heredan los tokens del root).

**Definition of done:** un usuario cambia de "azul" a "ámbar" en preferencias, recarga la página, y toda la UI (incluidos diálogos, menús, tooltips) refleja la nueva paleta sin FOUC ni reglas rotas.

**Pendiente del bloque:** nada; H6 cerrado.

---

## H7 — Recomendador con LLM — [retirado]

**Decisión (2026-10-14):** la feature completa del recomendador con IA se **retiró** del proyecto. Se eliminó todo lo construido en el hito: paquete `com.pantrybase.api.ai` (Spring AI + Ollama, cadena de proveedores con Resilience4j y métricas por proveedor), endpoint `POST /api/ai/recommend`, la preferencia `useAi` (columna `user_preferences.use_ai` y migraciones `V18`/`V19`/`V20`) y su frontend (`core/ai/`, tercera vista IA de recetas, toggle en preferencias). También se retiraron las dependencias `spring-ai-*` y `resilience4j-*` y sus bloques de configuración. El hueco funcional lo cubre el recomendador guiado por despensa de H4 (`GET /api/recipes/recommend`), sin LLM ni credenciales.

---

## H8 — Social y personalización

**Objetivo:** persistir la relación del usuario con las recetas (favoritos/guardadas/historial), exponer estadísticas de perfil y permitir publicar recetas propias. El paquete `social` ya está scaffoldeado (ruta Angular placeholder). Las migraciones nuevas empiezan en **V18** (V18–V20 quedaron libres tras retirar H7).

**Decisiones de producto acordadas (2026-10-14):**
1. **Favoritos y guardadas son dos conceptos distintos**: «favorito» = me gusta (corazón), «guardada» = para cocinar después (marcador). Tablas y UI separadas.
2. **Publicación = flag en `Recipe`** (`published_at`), sin entidad nueva: reutiliza el modelo de H4 en lugar de duplicar recetas.
3. **Estadísticas de perfil:** nº de recetas cocinadas, nº de recetas distintas y ranking de ingredientes más usados.
4. **Tema al cerrar sesión:** reset a `blue` y limpieza de caché, para que las pantallas no autenticadas tengan color por defecto (ver H8-0).
5. **Filtrado por dieta (acordado 2026-10-16, ver H8-F):** derivado de los nutrientes por ración y materializado en `recipe_diets`; las etiquetas no derivables (`HIGH_FIBER`, `LOW_SODIUM`) se retiran; `BALANCED` significa «sin filtro».

### Bloque H8-0 — Tema por defecto en pantallas no autenticadas (frontend, independiente)

**Bug de partida:** si el usuario guarda un `themeId` en preferencias y cierra sesión, el tema sigue aplicado. Diagnóstico confirmado en `frontend/pantry-web`:
- `ThemeService.apply()` (`core/theme/theme.service.ts:20`) escribe `data-theme` **y** `localStorage['pantry.themeId']`; **no existe reset**.
- El logout solo hace `SessionState.clear()` (`core/auth/session.state.ts:52`): no toca el tema. Lo mismo en los tres caminos de cierre (expiración, fallo y logout explícito: `core/auth/auth.service.ts:68`, `core/layout/shell.ts:109`, `core/auth/jwt.interceptor.ts:56`).
- `App` (`app.ts:37-46`) filtra `isAuthenticated === true` → **ignora el paso a `false`**; al recargar, `ngOnInit` re-aplica el tema cacheado del perfil (`app.ts:50`).

**Qué hacer:**
- `ThemeService.reset()`: aplica `DEFAULT_THEME` (`blue`) al DOM y **borra** la clave de caché.
- `App` reacciona en ambos sentidos de `isAuthenticated`: `true` → prefs del servidor; `false` → `reset()`. Cuidar el arranque en frío anónimo para no pisar `restoreCached()` ni provocar FOUC.
- Tests: `theme.service.spec.ts` (reset) y `app.spec.ts` («logout vuelve a default»).

**DoD:** tras logout la UI muestra el azul por defecto y la caché queda limpia; al re-entrar el mismo usuario recupera su tema desde `GET /api/users/preferences`.

### Bloque H8-A — Modelo social y persistencia (backend)

- Migración `V18` (favoritos + guardadas), `V19` (`recipe_history`) y `V20` (`recipes.published_at`).
- Entidades y repositorios con FK y unicidad: una fila por `(user_id, recipe_id)` en favoritos y guardadas; historial idempotente por `session_id`.
- `recipes.published_at` (NULL = privada).

**DoD:** las migraciones aplican y hay tests de integración de los repositorios.

### Bloque H8-B — API favoritos y guardadas (backend)

- Endpoints autenticados e idempotentes (`PUT`/`DELETE`) con verificación de propiedad y DTOs en la frontera; documentados en OpenAPI.
- `GET /api/recipes` y el detalle exponen `favorite`/`saved` del usuario actual para renderizar los toggles.

**DoD:** el toggle funciona y es idempotente; receta inexistente → 404; tests de integración.

### Bloque H8-C — Historial alimentado al completar sesión (backend)

- Al pasar `CookingSession` a `COMPLETED`, insertar el historial en la **misma transacción** (no en `ABANDONED`), con unicidad por `session_id`.
- `GET /api/history`.

**DoD:** el historial es 100 % consistente con las sesiones completadas; un abandono no registra; un doble complete no duplica.

### Bloque H8-D — Estadísticas de perfil (backend + frontend)

- `GET /api/users/me/stats` (recetas cocinadas, distintas, top de ingredientes) + tarjeta en `profile-page`.

**DoD:** los números cuadran con historial/descuento, cubierto con test.

### Bloque H8-E — Publicar receta propia (backend + frontend)

- `POST`/`DELETE /api/recipes/{id}/publish` (solo el autor, solo recetas `source = USER`) y un listado público mínimo de publicadas.
- El backend de creación ya existe desde H4-b (`POST /api/recipes`, `CreateRecipeRequest` con `ingredients[] {ingredientId, amount, unitCode, position}` y canónicos vía `toStorageUnit`); falta la **visibilidad pública** y el **formulario Angular** de receta propia, con `RecipesService.create(...)` tipando ese DTO.

**DoD:** publicar/despublicar funciona; no se publica receta ajena ni de TheMealDB; el formulario crea una receta.

### Bloque H8-D frontend — UI social

- Toggle de favorito/guardada en card y detalle, páginas de guardadas e historial y entradas de navegación.

**DoD:** rutas y estados (vacío/carga/error) cubiertos con tests.

### Bloque H8-F — Filtro por dieta (backend + frontend)

**Decisión de producto (2026-10-16):** filtrar por dieta **derivando las etiquetas de los nutrientes por ración**, en lugar de pedírselas al proveedor (TheMealDB no expone etiquetas de dieta y el USDA tampoco, así que el valor llegaría inventado). El perfil guardaba la preferencia desde H1 pero nada la aplicaba. Umbrales por ración: `HIGH_PROTEIN` proteína ≥ 20 g · `LOW_CARB` carbohidratos ≤ 20 g · `LOW_FAT` grasa ≤ 10 g. Una receta puede llevar varias etiquetas; una sin nutrientes no recibe ninguna; `BALANCED` nunca se almacena (es la ausencia de filtro). `HIGH_FIBER` y `LOW_SODIUM` **se eliminan**: no derivan de `recipe_nutrients` y estaban sin efecto real.

- Migración `V22__recipe_diets.sql`: tabla `recipe_diets` (clave primaria `(recipe_id, diet)` + índice por `diet`, patrón de `recipe_allergens`) y normalización de las preferencias persistidas con `HIGH_FIBER`/`LOW_SODIUM` a `BALANCED`.
- Enum `Diet` reducido a `BALANCED | HIGH_PROTEIN | LOW_CARB | LOW_FAT`; `RecipeDiet`/`RecipeDietId`/`RecipeDietRepository` y campo `diet` en `RecipeSearchRequest`.
- `RecipeService` materializa las etiquetas al crear, al refrescar por TTL y al materializar (`deriveAndStoreDiets`), y filtra en `scoreAndFilter` —compartido por `search` y `recommend`—, así que `GET /api/recipes` y `GET /api/recipes/recommend` respetan el mismo `diet`.
- Frontend: `diet?: Diet` en `RecipeSearchQuery`, `DIET_OPTIONS` compartido con el perfil y selector `Diet` en la página de recetas (fija desde `GET /api/users/preferences`, re-busca y re-sugiere al cambiar; omite `diet` si es `BALANCED`).

**DoD:** crear o refrescar una receta materializa sus etiquetas; `diet=HIGH_PROTEIN` excluye las que no lo son y `BALANCED` no excluye nada; los dos valores retirados ya no se pueden enviar ni persistir.

### Bloque H8-G — Cierre

- Docs (`FEATURE_GUIDE`, `README`, `ROADMAP`, `VERSIONS` si cambian dependencias), build y tests verdes, runtime Docker.

**Riesgos anotados:** añadir `favorite`/`saved` a `GET /api/recipes` toca los DTOs y los modelos/tests de recetas del frontend; el listado público debe acotarse para no filtrar recetas privadas ajenas; la inserción del historial convive con el descuento `REQUIRES_NEW` de H5 y hay que ubicar bien el punto de escritura.

**Orden propuesto:** H8-0 → H8-A → H8-C → H8-B → H8-D → H8-E → H8-D frontend (UI) → H8-G → H8-F.

**Definition of done (hito):** toda la interacción social requiere auth; el historial es 100 % consistente con las sesiones completadas; publicar/despublicar respeta la propiedad; el filtrado por dieta excluye solo etiquetas derivadas de datos reales.

**Estado (16-oct-2026): H8 cerrado.** Backend y frontend implementados y verificados (backend 312 tests verdes; frontend 378 tests verdes en 36 ficheros; build limpio); migraciones Flyway hasta V21. **Pendiente del bloque:** nada.

**Estado (16-oct-2026, tarde): H8 cerrado + ronda de correcciones.** Cinco incidencias del usuario resueltas y verificadas: (1) retirada la pestaña/ruta/página «Social» (placeholder); (2) favoritos y guardadas unificados en una única página **«Mi recetario»** (`/saved`) con dos pestañas; (3) arregladas las estadísticas del perfil (faltaban las clases `.stats-*` de `profile-page.scss`); (4) **filtro por dieta implementado** (bloque H8-F); (5) **arreglado el nombre de los ingredientes** en top de estadísticas y etiquetas de deducción (`V23` backfill, ahora se guarda el nombre del catálogo para líneas emparejadas). Verificación: **backend 315 tests verdes** (V23 aplicada), **frontend 389 tests verdes en 36 ficheros**, build limpio. `docs/VERSIONS.md` sin cambios (ninguna dependencia nueva).

**Estado (18-oct-2026): H8 cerrado + rediseño de navegación y segunda ronda de correcciones.** Cambios de frontend: (a) la navegación pasa a una **barra superior global** (`mat-toolbar`) con menú de usuario, en lugar del cajón lateral; (b) la **lista de sesiones de cocina se fusiona en «Historial»** (`/history`, con nombre e imagen de la receta y «Cook again»; `/cooking` redirige a `/history` y `/cooking/:id` sigue vivo); (c) **todo el texto visible del frontend pasa a inglés** (a la espera de i18n); (d) el **perfil** se reordena en una columna con **índice lateral** de anclas (usando `routerLink`+`fragment`+`anchorScrolling`, porque un `href="#…"` con `<base href="/">` saltaba a la despensa); (e) «Mi recetario» gana una **tercera pestaña «Created»** con las recetas propias, servidas por el nuevo **`GET /api/recipes/mine`** (backend `RecipeService.listMine` + `RecipeRepository.findUserRecipesOwnedBy`, sin filtro por cobertura). Verificación: **backend 42 tests en `RecipeControllerIntegrationTest`** (suite completa 318 `Tests run`), **frontend 389 tests verdes en 35 ficheros**, build limpio. `docs/VERSIONS.md` sin cambios.

---

## H9 — Producción y endurecimiento

### Bloque H9-1 — Rate limit por IP en endpoints anónimos (cerrado 18-oct-2026)

Dos niveles de cubo de fichas sobre el mismo script Lua: los endpoints autenticados ya tenían cubo por **usuario** desde H1; este bloque añade el cubo por **IP** con su propia política para las peticiones que llegan **sin autenticar** (login, registro, refresh —que va en cookie, sin token— y restablecimiento de contraseña), que es por donde entra la fuerza bruta de credenciales y donde no existe identidad que usar como clave. El filtro, colocado tras `JwtAuthFilter`, decide la clave según el `SecurityContext`; `TokenBucketRateLimiter` pasa a ser mecanismo sin política (recibe capacidad e intervalo como argumentos), y `RateLimitProperties` gana el bloque `anonymous` con política por defecto y overrides más estrechos (`strict-paths`) para login/registro/reset. La IP se toma de `remoteAddr` y no de `X-Forwarded-For` (sin proxy de confianza delante, la cabecera la controla el cliente). Verificado: **suite backend en 320 tests verdes** (7 en `RateLimitFilterIntegrationTest`: aislamiento entre cubos, política estricta, cabeceras con la capacidad aplicada). FEATURE_GUIDE §15 actualizado. Sin dependencias nuevas.

- [x] Cubo por IP para peticiones no autenticadas, con política `anonymous` y overrides `strict-paths`.
- [x] Tests de aislamiento de cubos (agotar el de usuario no consume el de IP) y cabeceras con capacidad aplicada.

**Pendiente del bloque:** nada.

### Bloque H9-4 — Índices analizados con EXPLAIN y concurrencia del descontado (cerrado 18-oct-2026)

**H9-4a (análisis, con `EXPLAIN ANALYZE` real contra la BD local, 123 recetas / 16 líneas de despensa):** todas las queries calientes resuelven por índice — historial de sesiones (`idx_cooking_sessions_user`), historial social (`idx_recipe_history_user_created`, cubre filtro y orden), recetas visibles (`idx_recipes_source`), libro mayor (`idx_consumption_entries_pantry_item_id`) y lookup de descuento. **Hallazgo:** `idx_pantry_items_user_id` (V12) es **redundante** — `UNIQUE (user_id, ingredient_id)` sirve también cualquier `WHERE user_id = …` por prefijo izquierdo; coste: cada escritura de despensa mantiene dos índices donde bastaría uno. **Decisión: no se retira aún** (deuda anotada, impacto bajo); retirada para cuando haya una medida que la justifique. **Límite honesto:** con 16 líneas los planes prueban existencia/usabilidad de los índices, no comportamiento a escala — a vigilar con el crecimiento: el `OR` de recetas visibles y los `LIKE '%…%'` de búsqueda (candidatos a trigram/FTS). **Conclusión: sin `V24`** — el análisis es el entregable, no una migración por cumplir.

**H9-4c (test):** nueva clase `CookingCompletionConcurrencyIntegrationTest`: 8 sesiones del mismo usuario completadas simultáneamente (`CyclicBarrier`) sobre la misma línea de despensa con stock justo para todas (8 × 200 g), de modo que un 409 solo puede ser carrera optimista, nunca carencia. Afirmaciones **invariantes, no estadísticas**: ningún 5xx; cantidad final = stock − lo cobraron los éxitos; identidad del libro (suma de deltas == cantidad); el perdedor queda `IN_PROGRESS` (rollback completo de la transacción) y **puede reintentarse con éxito** (409 recuperable). No exige que haya conflicto: si el planificador serializa, los invariantes siguen valiendo. **Suite en 321 tests verdes.** Sin dependencias nuevas.

**Pendiente del bloque:** nada.

### Bloque H9-2 — Alertas de Prometheus con Alertmanager (cerrado 18-oct-2026)

Cinco reglas en `infra/prometheus/rules.yml` (validadas con `promtool check rules`: 5 rules found) sobre las series que el scrape real expone: `ApiDown` (crítico, 2 min sin scrapear), `ApiHighErrorRate` (>10 % de 5xx durante 5 min; sin tráfico no hay división, así que una API ociosa no alerta), `FdcQuotaExhausted` (cuota de USDA FDC agotada en 15 min), `FdcServingStale` (catalog sirviendo caché obsoleta) y `CookingSessionsStuck` (sesión colgada 30 min). **Ajuste honesto al texto del hito:** «circuito abierto» y «cuota de LLM» ya no existen tras retirar H7; su hueco lo ocupan las dos alertas de degradación del proveedor FDC. **Prerrequisito implementado:** no había forma de detectar «sesiones colgadas», así que se añade el gauge `pantry.cooking.sessions.stuck` (`CookingService` + `CookingSessionRepository.countByStatusAndStartedAtBefore`): sesiones `IN_PROGRESS` abiertas hace más de 1 h, evaluado **en el scrape** (un COUNT por pull, sin tarea programada). Alertmanager (`prom/alertmanager:v0.34.1`, config validada con `amtool check-config`) enruta todo por correo a Mailpit (`smtp_require_tls: false`), de modo que el aviso se ve en `http://localhost:8025`. **Verificación end-to-end real:** parar `pantry-api` → `ApiDown` pasa a `firing` en Prometheus → `active` en Alertmanager con receiver `local-mailpit` → correo **«[FIRING:1] ApiDown»** entregado en Mailpit; al reiniciar la API las alertas quedan limpias. Test del gauge en `CookingControllerIntegrationTest` (se lee del `MeterRegistry`: los starters de test de Boot 4 **desactivan la exportación de métricas en tests** a propósito, así que `/actuator/prometheus` no existe en el contexto de test pero sí en el runtime — verificado contra el contenedor). **Suite backend 321 tests verdes** (el test del gauge sustituye a ningún otro). Dependencia nueva: imagen Alertmanager (anotada en `VERSIONS.md`).

- [x] Reglas de alerta para 5xx, caída, degradación del proveedor FDC y sesiones colgadas.
- [x] Alertmanager enroutando avisos por correo a Mailpit, con entrega verificada de extremo a extremo.
- [x] Gauge de sesiones colgadas con test (sin tarea programada: se evalúa en el scrape).

**Pendiente del bloque:** nada.

### Bloque H9-3 — Dashboards de Grafana provisionados (cerrado 18-oct-2026)

Dos dashboards **versionados como código** en `infra/grafana/dashboards/` y cargados por el provider `infra/grafana/provisioning/dashboards/dashboards.yml` (namespace Grafana 13: los ficheros se montan en `/etc/grafana/dashboards`): **PantryBase / Health** (uptime, CPU, heap, hilos, tráfico por estado, latencia media, pool Hikari, carga del host) y **PantryBase / Operations** (sesiones colgadas, ratio 5xx, cuota FDC, tasa de completadas/abandonadas, duración media de sesión, degradación del proveedor). Los paneles referencian el datasource con la **variable `${datasource}`** en vez de un `uid` fijo: el `uid` de un datasource provisionado lo genera Grafana en cada instalación (aquí `PBFA97CFB590B2093`), y fijarlo en YAML **rompe el arranque en Grafana 13.0.9** con `Datasource provisioning error: data source not found` (incidencia real durante este bloque). **Límites documentados:** los timers HTTP y de sesiones son *summaries* (count/sum/max), no histogramas, así que la latencia se muestra como **media**, no p90/p95; no hay métricas de cuota de TheMealDB, cobertura de despensa ni rendimiento del recomendador, así que esos paneles del texto original **no se fabrican**. Verificado: los dos dashboards aparecen vía API de Grafana (`/api/search`) y el datasource se resuelve.

- [x] Dashboards de salud y operación provisionados como JSON versionado.
- [x] Datasource referenciado por variable (reproducible en cualquier instalación).
- [x] Contenido honesto: solo series que el scrape expone de verdad; el resto, anotado como pendiente.

**Pendiente del bloque:** nada.

---

- [ ] Hardening de seguridad: rate limiting por IP (hecho en H9-1), auditoría de llamadas externas, secretos en Vault/CI. Hecho en H2-B-5: la key de USDA FDC viaja en la query string (la API no admite otra forma) y ya no se escribe en los logs, porque las URLs de error se sanean antes de entrar en `FdcProviderException`; además `FdcProperties` está validada al arrancar, de modo que una key ausente, `data-types` vacío, `page-size < 1` o timeouts nulos/negativos rompen el arranque en lugar de degradarse en silencio, y la caída a `DEMO_KEY` (30 req/h) emite un warning. Pendiente: auditoría y Vault/CI.
- [ ] Dashboards de Grafana finales: salud y operación (hecho en H9-3: dos dashboards en `infra/grafana/dashboards/`, con `PantryBase / Health` y `PantryBase / Operations`). No hay series de cuota de TheMealDB, cobertura de despensa ni recomendador: no se inventan paneles (o se añaden cuando existan métricas). La cuota de USDA FDC sí está cubierta.
- [ ] Alertas (Prometheus Alertmanager) para: errores 5xx, caída de la API, degradación del proveedor FDC y sesiones colgadas (hecho en H9-2; «circuito abierto» y «cuota de LLM» se retiraron con H7, su hueco lo cubren las alertas FDC).
- [ ] Carga y escalado: índices de BBDD analizados con `EXPLAIN` y test de concurrencia sobre descontado (hecho en H9-4: sin V24, `idx_pantry_items_user_id` anotado como redundante y `CookingCompletionConcurrencyIntegrationTest` con invariantes). Pendiente: caché Redis efectiva (medición).
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

- **Fuente de verdad para alérgenos (resuelto en H4):** ni USDA FDC ni TheMealDB garantizan campo alérgeno directo; se resolvió con un mapeo propio curado — `allergen_mapping` (ingrediente → código EU) sembrado en `V13__recipes.sql` con los códigos del catálogo de `V3`, y `recipe_allergens` derivado al crear cada receta. El extracto automático sobre la descripción del proveedor no está implementado.
- **Conversión de sólidos en tazas:** resuelta en H2-B-3 y H2-B-4, con la prioridad final porción FDC > medida propia > clase de densidad curada; la categoría del proveedor queda descartada como fuente de densidad.
- **Cache de catálogo y recetas:** política de refresco (TTL vs. invalidación manual), cuota de USDA FDC (~1.000 req/h/IP; 429 + bloqueo 1 h) y respeto de la etiqueta de TheMealDB (cachear recetas, no servirlas en caliente).
- **Modelo multi-proveedor LLM:** el prompt debe funcionar por igual en modelos sin tool-calling (fallback a JSON estricto).
