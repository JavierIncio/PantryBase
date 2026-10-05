# Guía de funcionalidades — PantryBase

Documento de referencia que explica **qué hace cada parte de la aplicación y qué archivos la implementan**. Está pensado para leerse de principio a fin por alguien que está entrando en el proyecto, incluidos los que no han desarrollado antes con Spring Boot o Angular.

No sustituye al `README.md` (visión, arquitectura y decisiones) ni al `docs/ROADMAP.md` (plan de trabajo). Aquí no se explica *por qué* se eligió una tecnología, sino *cómo funciona* la funcionalidad y dónde vive.

**Estado actual:** hay funcionalidad terminada de H0 a H2 (base del proyecto, autenticación, usuario y catálogo de ingredientes con motor de unidades). Los apartados que describen cada pieza indican si esa funcionalidad existe ya o si está planificada; el resto de áreas de producto todavía no existe y sus páginas web son marcadores de posición.

---

## Índice

1. [Cómo está montado el proyecto](#1-cómo-está-montado-el-proyecto)
2. [Arranque del backend y configuración](#2-arranque-del-backend-y-configuración)
3. [Autenticación: registro, login, sesiones y tokens](#3-autenticación-registro-login-sesiones-y-tokens)
4. [Contraseña olvidada (email)](#4-contraseña-olvidada-email)
5. [Login con Google (OAuth2)](#5-login-con-google-oauth2)
6. [Perfil, preferencias y alérgenos](#6-perfil-preferencias-y-alérgenos)
7. [Catálogo de ingredientes (USDA FoodData Central)](#7-catálogo-de-ingredientes-usda-fooddata-central)
8. [Frescura y caché del catálogo](#8-frescura-y-caché-del-catálogo)
9. [Motor de unidades y densidades](#9-motor-de-unidades-y-densidades)
10. [Administración de clases de densidad](#10-administración-de-clases-de-densidad)
11. [Despensa: inventario del usuario (H3-1 y H3-2)](#11-despensa-inventario-del-usuario-h3-1-y-h3-2)
12. [Rate limiting](#12-rate-limiting)
13. [Manejo de errores de la API](#13-manejo-de-errores-de-la-api)
14. [Observabilidad](#14-observabilidad)
15. [Correo de desarrollo (Mailpit)](#15-correo-de-desarrollo-mailpit)
16. [Frontend: sesión, guardas y estructura](#16-frontend-sesión-guardas-y-estructura)
17. [Migraciones de base de datos](#17-migraciones-de-base-de-datos)
18. [Tests](#18-tests)
19. [CI/CD e imágenes Docker](#19-cicd-e-imágenes-docker)
20. [Zona horaria y notas de mantenimiento](#20-zona-horaria-y-notas-de-mantenimiento)

---

## 1. Cómo está montado el proyecto

La aplicación tiene tres piezas que se desarrollan y despliegan por separado, pero se usan juntas:

| Pieza | Tecnología | Ruta | Para qué sirve |
|---|---|---|---|
| Backend (API) | Java 21 + Spring Boot | `backend/pantry-api/` | Toda la lógica de negocio. Es el único que habla con la base de datos y con APIs externas. |
| Frontend (web) | Angular 22 + Material | `frontend/pantry-web/` | La interfaz que ve el usuario. |
| Infraestructura | Docker Compose | `infra/` | Los servicios que necesita el backend: base de datos, caché, correo, métricas. |

Las dos primeras piezas se comunican por HTTP/JSON. El frontend nunca accede a la base de datos: pide todo a la API, y por eso todas las rutas `/api/**` exigen un token de sesión.

**La idea central del backend es la separación en paquetes por funcionalidad**, no por capas técnicas. Cada carpeta reúne todo lo que pertenece a una característica concreta:

```
com.pantrybase.api
├── auth/        autenticación, tokens, recuperación de contraseña
├── user/        perfil, preferencias, alérgenos
├── catalog/     catálogo de ingredientes, unidades y densidades
├── common/      configuración transversal, seguridad, rate limiting, errores
└── PantryApiApplication.java   clase que arranca Spring Boot
```

Dentro de cada paquete, las subcarpetas sí siguen capas (`domain`, `dto`, `repository`, `service`, `web`, `client`), que es la convención habitual de Spring:

- `domain` — las clases que representan los datos (entidades JPA y records de dominio). No saben nada de HTTP ni de SQL.
- `dto` — los objetos que viajan por la API. Existen para no exponer las entidades de base de datos directamente.
- `repository` — las interfaces que hablan con la base de datos (Spring Data genera la implementación).
- `service` — la lógica de negocio.
- `web` — los controladores: leen peticiones HTTP y devuelven respuestas.
- `client` — los adaptadores hacia APIs externas (actualmente solo USDA FDC).
- `exception` — los errores de dominio, que el manejador global traduce a códigos HTTP.

---

## 2. Arranque del backend y configuración

**Qué hace:** levanta el servidor web en el puerto 8080, conecta con PostgreSQL, ejecuta las migraciones pendientes y registra los beans (los objetos que gestiona Spring).

| Elemento | Ubicación |
|---|---|
| Clase principal | `backend/pantry-api/src/main/java/com/pantrybase/api/PantryApiApplication.java` |
| Fichero de configuración | `backend/pantry-api/src/main/resources/application.yml` |
| Definición Maven y dependencias | `backend/pantry-api/pom.xml` |
| Imagen Docker | `backend/pantry-api/Dockerfile` |

**Bloques de `application.yml`:**

| Bloque | Para qué sirve |
|---|---|
| `spring.datasource` | Credenciales y URL de PostgreSQL. |
| `spring.jpa` | `ddl-auto: validate` hace que la aplicación **falle al arrancar** si el esquema no coincide con las entidades. Flyway es quien crea el esquema, no Hibernate. |
| `spring.flyway` | Activa las migraciones automáticas al arrancar. |
| `spring.data.redis` | Conexión a Redis (caché y rate limiting). |
| `spring.security.oauth2` | Credenciales de Google para el login social. |
| `spring.mail` | Servidor SMTP de envío de correos. |
| `app.jwt` | Secreto de firma y duración de los tokens de acceso y refresco. |
| `app.security.cookie` | Marca `secure` de la cookie de refresco (falso en local, **debe ser `true` en producción**). |
| `app.security.oauth2` | URL a la que Google devuelve al usuario tras el login. |
| `app.rate-limit` | Capacidad del cubo de fichas, intervalo de recarga y rutas exentas. |
| `app.mail` / `app.frontend-base-url` | Remitente de correos y base de las URLs de los enlaces. |
| `app.catalog.fdc` | Key de USDA FDC, tipos de dato, tamaño de página, timeouts y ventanas de caché. |
| `management.endpoints` | Qué expone el actuator: `health`, `info`, `prometheus`. |
| `springdoc` | Documentación interactiva de la API (Swagger). |

**Servicios en `infra/docker-compose.yml`:** `postgres`, `redis`, `pantry-api`, más los de soporte `prometheus`, `grafana` y `mailpit`. Los secretos se leen de `infra/.env` (ignorado por git); `infra/.env.example` solo tiene los nombres de variable con valores de ejemplo.

---

## 3. Autenticación: registro, login, sesiones y tokens

**Qué hace:** comprueba quién es el usuario y mantiene su sesión abierta sin que tenga que escribir su contraseña cada vez.

**El concepto central: access token + refresh token.** Son dos tokens con propósitos distintos, y la separación es deliberada:

- **Access token** (15 minutos): es lo que viaja en cada petición, en la cabecera `Authorization: Bearer <token>`. Es corto para que revocar el acceso a un token robado sea rápido.
- **Refresh token** (7 días): no viaja en las peticiones, solo se guarda en una **cookie `httpOnly`**. Al ser `httpOnly`, el JavaScript del navegador no puede leerla, lo que la protege frente a un ataque de robo de sesión por JavaScript inyectado.

Cuando el access token caduca, el frontend llama a `/api/auth/refresh` enviando la cookie, y el servidor emite un par nuevo. Cada refresh token se guarda en la base de datos **hasheado**, no en claro: si alguien lee la tabla `refresh_tokens`, no puede usarla para iniciar sesión.

**Cómo viaja una petición autenticada:**

1. `JwtAuthFilter` (un filtro de servlet) lee la cabecera `Authorization` y valida la firma del token con el secreto de `app.jwt.secret`.
2. Si el token es válido, deja el usuario asociado a la petición; si no, la petición sigue sin autenticar y la regla de acceso decide si eso es aceptable.
3. `SecurityConfig.filterChain` define qué rutas son públicas (login, registro, actuator, documentación) y cuáles exigen `authenticated()`.

**Clases y ficheros:**

| Clase | Ruta |
|---|---|
| `AuthController` (`/api/auth`) | `auth/web/AuthController.java` |
| `AuthService` | `auth/service/AuthService.java` |
| `JwtService` (firma y verificación) | `common/security/JwtService.java` |
| `JwtAuthFilter` | `common/security/JwtAuthFilter.java` |
| `CookieService` (crea la cookie de refresco) | `common/security/CookieService.java` |
| `TokenService` (persistencia hasheada de refresh tokens) | `auth/service/TokenService.java` |
| `SecurityConfig` | `common/config/SecurityConfig.java` |
| `RestAuthenticationEntryPoint` (respuesta 401) | `common/security/RestAuthenticationEntryPoint.java` |
| Tablas | `db/migration/V1__init.sql` (`users`), `V2__auth_mods.sql` (`refresh_tokens`, `user_roles`) |

**Endpoints:**

| Endpoint | Acceso |
|---|---|
| `POST /api/auth/register` | Público |
| `POST /api/auth/login` | Público |
| `POST /api/auth/refresh` | Público |
| `POST /api/auth/logout` | Público |
| `POST /api/auth/password-reset-token` | Público |
| `POST /api/auth/password-reset` | Público |
| `PUT /api/auth/password` | Requiere sesión |

**Detalle de diseño importante:** los seis endpoints de la tabla que son públicos siguen siendo públicos **aunque el usuario ya tenga una sesión iniciada**, y están declarados como tales en `SecurityConfig` en lugar de deducirse. Es lo contrario de lo que suele hacerse (restringir las rutas de autenticación para impedir el «ataque de cambio de sesión»), pero aquí es intencionado: son precisamente las operaciones que crean, renuevan o destruyen la propia sesión, y bloquearlas obligaría al cliente a un cierre de sesión previo que no siempre es posible. El único que exige token es el cambio de contraseña, porque para cambiar la contraseña hay que saber quién eres.

---

## 4. Contraseña olvidada (email)

**Qué hace:** permite recuperar el acceso con un enlace de un solo uso, sin que la respuesta de la API revele si una cuenta existe.

| Clase | Ruta |
|---|---|
| `PasswordResetService` | `auth/service/PasswordResetService.java` |
| `PasswordResetMailer` (interfaz) / `SmtpPasswordResetMailer` (implementación) | `auth/mail/` |
| `MailConfig` | `common/config/MailConfig.java` |
| Plantilla del correo | `resources/email/password-reset.html` |
| Tabla | `db/migration/V4__password_reset_tokens.sql` |

**Endpoints:** `POST /api/auth/password-reset-token` (pide el enlace) y `POST /api/auth/password-reset` (establece la nueva contraseña).

**Conceptos:** el token se genera con `SecureRandom`, caduca a los 15 minutos y se guarda hasheado. Además, el proceso **revoca todos los refresh tokens** del usuario: recuperar una contraseña sirve para cerrar las sesiones que hubiera abierto alguien con la antigua, y esa es la razón de que el servicio toque la tabla de refresh tokens.

La API responde con el mismo mensaje tanto si el correo existe como si no (anti-enumeración de cuentas). En local, el correo aparece en la interfaz web de Mailpit.

---

## 5. Login con Google (OAuth2)

**Qué hace:** permite entrar con una cuenta de Google sin escribir contraseña.

**Cómo funciona, en breve:** el usuario pulsa un botón, el backend lo redirige a Google, Google confirma su identidad y devuelve al backend (`/login/oauth2/code/**`). Spring Security procesa ese retorno, obtiene el correo del perfil de Google y **vincula o crea** un usuario local (`AuthService.linkOrCreateOAuthUser`). A partir de ahí, el comportamiento es idéntico al login con contraseña: los tokens son los mismos.

| Clase | Ruta |
|---|---|
| `OAuth2AuthenticationSuccessHandler` | `common/security/OAuth2AuthenticationSuccessHandler.java` |
| `OAuth2AuthenticationFailureHandler` | `common/security/OAuth2AuthenticationFailureHandler.java` |
| Configuración de CORS y la cadena de filtros | `common/config/SecurityConfig.java` |

El usuario derivado de Google recibe un **nombre de usuario único derivado del correo**, porque el campo `username` no puede repetirse en la base de datos. Este camino todavía no se ha verificado contra un cliente real de Google; la configuración se lee de `spring.security.oauth2.client.registration.google` y de `app.security.oauth2.redirect-uri`.

---

## 6. Perfil, preferencias y alérgenos

**Qué hace:** guarda los datos personales y las preferencias de filtrado de cada usuario, y su lista de alérgenos que quiere excluir.

| Concepto | Explicación |
|---|---|
| `User` | Identidad, correo, nombre, hash de la contraseña, roles y lista de alérgenos excluidos. |
| `UserPreferences` | Preferencias de filtrado (`filterMode`, `coverageThreshold`, `diet`). Viven en **otra tabla** que comparte la clave primaria con el usuario (`@MapsId`), de modo que guardar una preferencia no reescribe la fila de la cuenta. |
| `FilterMode` | `STRICT` exige cobertura total de los ingredientes; `LAX` aplica el umbral configurado. |
| `Diet` | Preferencia de dieta. Está marcada como provisional en el código porque TheMealDB no expone etiquetas de dieta y el mapeo aún no está validado. |
| `Allergen` / `user_allergy_exclusions` | Catálogo de alérgenos y lista de exclusión por usuario. Los alérgenos se eligen de un catálogo cerrado, no se escriben como texto libre. |

| Clase | Ruta |
|---|---|
| `UserController` (`/api/users`) | `user/web/UserController.java` |
| `UserService` | `user/service/UserService.java` |
| Entidades | `user/domain/` (`User`, `UserPreferences`, `Allergen`, `Diet`, `Role`, `FilterMode`) |
| Repositorios | `user/repository/` |
| Tablas | `db/migration/V1__init.sql` (`users`, `allergens`), `V2__auth_mods.sql` (`user_roles`), `V3__profile.sql` (`user_preferences`, `user_allergy_exclusions`) |

**Endpoints:** `GET /me`, `PUT /profile`, `GET|PUT /preferences`, `GET|PUT /allergy-exclusions`, `GET /allergens`.

Las preferencias y las exclusiones se guardan con semántica de **reemplazo completo** (se manda la lista entera y sustituye a la anterior), no de parcheo. Un código de alérgeno desconocido produce `400`: si se aceptara, el filtro de recetas dejaría de excluir algo y el usuario creería que está protegido.

---

## 7. Catálogo de ingredientes (USDA FoodData Central)

**Qué hace:** permite buscar alimentos en la base de datos pública del USDA y guardar sus nutrientes y porciones en nuestra propia base de datos.

**Por qué existe el puerto `FoodCatalogPort`:** el dominio habla con una interfaz (`catalog/domain/FoodCatalogPort.java`), no con la API del USDA directamente. El adaptador `FdcFoodCatalogClient` implementa esa interfaz. La ventaja es que los tests pueden sustituir el USDA por un stub y comprobar la lógica sin gastar cuota ni depender de internet.

| Pieza | Ruta |
|---|---|
| Interfaz del dominio | `catalog/domain/FoodCatalogPort.java` |
| Adaptador real (USDA) | `catalog/client/FdcFoodCatalogClient.java` |
| Propiedades y validación de configuración | `catalog/client/FdcProperties.java` |
| Construcción del cliente HTTP | `catalog/client/FdcClientConfig.java` |
| Orquestación (búsqueda, detalle, lote, materialización) | `catalog/service/IngredientCatalogService.java` |
| `IngredientController` (`/api/catalog/ingredients`) | `catalog/web/IngredientController.java` |
| DTOs | `catalog/dto/` (`IngredientSearchResponse`, `IngredientDetailResponse`, `IngredientMeasureResponse`) |
| Entidades | `catalog/domain/` (`Ingredient`, `IngredientMeasure`, `NutrientProfile`, `Portion`, `FoodProfile`) |
| Tablas | `db/migration/V6__catalog_ingredients.sql`, `V7__catalog_ingredient_measures.sql`, `V10__catalog_ingredient_freshness.sql` |

**Endpoints:** `GET /api/catalog/ingredients?query=` (búsqueda) y `GET /api/catalog/ingredients/{fdcId}` (detalle).

**El detalle incluye las medidas del ingrediente** (`measures`: lista de `{unitCode, gramPerUnit}`, ordenada por código de unidad). Van dentro de la misma respuesta y no en un endpoint aparte porque **la petición de detalle es justo la llamada que captura esas medidas**: separarlas solo daría dos respuestas que pueden discrepar tras un refresco del proveedor. Con la lista vacía el USDA no pesó ninguna medida doméstica para ese alimento, que es un resultado normal y llega como `[]` y nunca como `null`. El nombre de la unidad no viaja aquí: los rótulos pertenecen a `GET /api/units`, que es una tabla fija.

**Por qué la búsqueda no devuelve un id interno:** la búsqueda **nunca materializa** —sus resultados no se guardan en nuestra base de datos—, así que un hit no tiene id local que devolver. Existe la tentación de incluirlo igualmente, pero obligaría a materializar cada resultado de la búsqueda, es decir, una llamada al proveedor por cada alimento que el usuario ni siquiera ha elegido. El flujo real es buscar → el usuario elige → detalle (materializa) → entrar la cantidad, y esa llamada de detalle es necesaria igualmente porque sin datos no hay nada que guardar en la despensa.

**Qué significa "materializar":** cuando se pide un detalle, los datos del proveedor no se quedan en memoria, se **insertan en nuestra base de datos**. El insert usa `ON CONFLICT DO NOTHING` sobre la columna única `fdc_id`, y después se relee de la base de datos. Esto hace que la operación sea idempotente (repetirla no duplica nada) y que dos peticiones simultáneas no se pisen: es la base de datos la que arbitra la carrera, no el código de aplicación.

**Algo que conviene saber:** la forma de la respuesta del USDA es particular y no coincide con la forma "natural" que se podría suponer. Los nutrientes llegan con el identificador anidado (`foodNutrients[].nutrient.id`) y la cantidad en un campo llamado `amount`; el nombre de la unidad de una porción está en `foodPortions[].modifier` mientras que `measureUnit.name` vale literalmente `"undetermined"`; y el endpoint de lote es un `GET` que responde con un array JSON. Los tests del adaptador usan un recorte literal de una respuesta real precisamente para que un cambio en el proveedor rompa un test en lugar de devolver valores nulos en silencio.

---

## 8. Frescura y caché del catálogo

**Qué hace:** decide si un ingrediente se sirve desde nuestra base de datos o se vuelve a pedir al USDA, y qué hacer si el USDA no responde.

| Concepto | Explicación |
|---|---|
| `synced_at` | Momento de la última lectura correcta del proveedor. Es lo que permite saber la antigüedad del dato. |
| `cache-ttl` (24 h) | Dentro de esta ventana no se llama al proveedor: se sirve la copia local. |
| `max-stale` (30 d) | Si el proveedor falla, se sigue sirviendo la copia local mientras no pase de esta edad. |
| Pasado `max-stale` | Se responde `502`. Un dato tan viejo se considera inservible. |
| `CatalogSearchCache` | Cachea en Redis los resultados de **búsqueda** únicamente, porque la búsqueda es la única consulta cuyo resultado no se materializa en la base de datos. El detalle ya tiene su copia en PostgreSQL. |

| Clase | Ruta |
|---|---|
| `FdcCachePolicy` (las tres decisiones: `FRESH`, `STALE`, `EXPIRED`) | `catalog/service/FdcCachePolicy.java` |
| `CatalogSearchCache` (Redis) | `catalog/service/CatalogSearchCache.java` |
| `IngredientCatalogService` (aplica la política) | `catalog/service/IngredientCatalogService.java` |
| `FdcRateLimitException` (HTTP 429 del proveedor) | `catalog/exception/FdcRateLimitException.java` |
| Métricas contadas | `pantry.catalog.fdc.quota.exhausted` y contador de copias rancias servidas |

**Por qué importa la cuota:** la API del USDA permite unas 1.000 peticiones por hora con key propia y solo 30 con la key pública de demostración. Sin estas ventanas de caché, navegar el catálogo vaciaría la cuota en minutos. Cuando el USDA responde `429`, la aplicación **sirve la copia local y no reintenta**: reintentar solo gastaría una cuota que ya no existe.

**Un detalle de diseño que conviene conocer:** `refreshProviderFields` sobrescribe únicamente las columnas que pertenecen al proveedor (nombre, categoría, nutrientes, `synced_at`) y nunca toca `density_class` ni `ingredient_measures`, porque esas dos son **datos curados por nosotros**, no del USDA.

---

## 9. Motor de unidades y densidades

**Qué hace:** convierte medidas de cocina («2 tazas de harina») en gramos, que es la unidad en la que se calculan los nutrientes.

**El problema conceptual:** las medidas de volumen (taza, cucharada, cucharadita) y las de peso (gramo, onza) son de familias distintas, y no se puede pasar de una a otra sin saber la densidad del material concreto. Un gramo de harina y un gramo de aceite ocupan volúmenes muy distintos.

**Las tablas que lo resuelven:**

| Tabla | Contenido | Migración |
|---|---|---|
| `units` | Catálogo de unidades con su categoría (`WEIGHT`, `VOLUME`, `COUNT`) y la marca de la unidad canónica. | `V1__init.sql` / `V5__catalog_units.sql` |
| `measure_conversions` | Factor de conversión de cada unidad a su canónica (taza = 236,588 ml, onza = 28,3495 g). | `V5__catalog_units.sql` |
| `density_classes` | Densidades curadas (`FLOUR` 0,53 g/ml, `SUGAR` 0,85, `OIL` 0,92, `MILK` 1,031288) con la **fuente** de cada valor. | `V5`, `V8`, `V9`, `V11` |
| `ingredient_measures` | Peso en gramos de una unidad de volumen **de un ingrediente concreto** (1 taza de leche = 244 g). | `V7__catalog_ingredient_measures.sql` |

**Orden en que se busca la respuesta** (lo primero que se encuentra, gana). Este orden es el corazón de la funcionalidad y está implementado en `UnitConversionService`:

1. **Medida exacta del ingrediente** para la unidad de volumen que se está usando. Es la más precisa: si el usuario pide tazas y el ingrediente tiene «1 taza = 244 g», se usa ese dato.
2. **Cualquier otra medida del mismo ingrediente** (la de mayor volumen, por ser la más precisa). Permite convertir con un ingrediente del que solo se capturó, por ejemplo, la cucharada.
3. **Clase de densidad curada del ingrediente**, en `ingredients.density_class`.
4. **Clase de densidad que el cliente pase en la petición**.
5. Si no hay ninguna de las anteriores, se lanza un error `400`. **Falla ruidosamente en lugar de adivinar**: una densidad inventada daría un error de peso silencioso en el cálculo de nutrientes de una receta, que es mucho peor que un error visible.

**Un detalle de precisión:** cuando hay medida propia, la conversión se hace como **cociente** (ml por unidad ÷ gramos por unidad) en vez de aplanarse a una densidad redondeada. Así, 2 tazas de leche dan exactamente 488 g y no 488,000130 g.

| Clase | Ruta |
|---|---|
| `UnitController` (`/api/units`) | `catalog/web/UnitController.java` |
| `UnitConversionService` | `catalog/service/UnitConversionService.java` |
| `UnitCatalogService` | `catalog/service/UnitCatalogService.java` |
| Puerto de dominio | `catalog/domain/UnitConverter.java` |
| Entidades | `catalog/domain/` (`Unit`, `MeasureConversion`, `DensityClass`, `IngredientMeasure`, `QuantityInfo`, `UnitCategory`) |
| DTOs | `catalog/dto/` (`ConvertUnitsRequest`, `UnitConversionResponse`, `UnitResponse`, `AssignDensityClassRequest`, `DensityClassResponse`) |

**Endpoints:** `GET /api/units` (catálogo) y `POST /api/units/convert` (conversión, con el campo opcional `ingredientId`).

---

## 10. Administración de clases de densidad

**Qué hace:** consultar el catálogo de clases de densidad y asignar una a un ingrediente concreto.

| Clase | Ruta |
|---|---|
| `DensityClassController` | `catalog/web/DensityClassController.java` |
| `DensityClassService` | `catalog/service/DensityClassService.java` |

**Endpoints:** `GET /api/catalog/density-classes` (lectura, para cualquier usuario autenticado) y `PATCH /api/catalog/density-classes/ingredients/{id}` (asignación, **restringida a `ROLE_ADMIN`**).

Dos decisiones a destacar:

- El listado es abierto a cualquier autenticado porque el cliente necesita saber qué clases existen para poder advertir si una conversión va a funcionar.
- La asignación está protegida por rol, y esa protección usa `@PreAuthorize` sobre el método, no una regla de URL. Para que la anotación se aplique hace falta `@EnableMethodSecurity` en `SecurityConfig`; sin esa línea, Spring la ignoraría en silencio y el endpoint quedaría abierto a cualquiera. Este detalle está documentado en el propio código porque es una forma habitual de fallo de seguridad difícil de detectar.

Asignar una clase que no existe produce un error `404`: así este endpoint no se convierte en una vía para introducir densidades sin justificar. La asignación exige que la clase esté previamente en la tabla, que es donde vive su justificación (`source`).

---

## 11. Despensa: inventario del usuario (H3-1 y H3-2)

**Qué hace:** guarda qué tiene cada usuario en casa, cuánto y dónde, y lleva el registro de los movimientos que explican esa cantidad. El alta rápida con búsqueda desde el catálogo sigue en un bloque posterior.

| Pieza | Ruta |
|---|---|
| `PantryController` (`/api/pantry`) | `pantry/web/PantryController.java` |
| `PantryService` (reglas de alta, edición, borrado y ajuste) | `pantry/service/PantryService.java` |
| Entidades | `pantry/domain/PantryItem.java`, `pantry/domain/ConsumptionEntry.java`, `pantry/domain/ConsumptionOrigin.java` |
| Repositorios | `pantry/repository/PantryItemRepository.java`, `pantry/repository/ConsumptionEntryRepository.java` |
| DTOs | `pantry/dto/` (`CreatePantryItemRequest`, `UpdatePantryItemRequest`, `AdjustPantryItemRequest`, `PantryItemResponse`, `ConsumptionEntryResponse`) |
| Excepciones | `pantry/exception/` (`PantryItemNotFoundException`, `IngredientAlreadyInPantryException`, `IngredientNotInCatalogException`, `InsufficientStockException`, `ZeroAdjustmentException`) |
| Tablas | `db/migration/V12__pantry_items.sql` |
| Tests | `pantry/PantryControllerIntegrationTest.java`, `pantry/PantryAdjustmentIntegrationTest.java`, `src/app/core/pantry/pantry.service.spec.ts`, `src/app/pages/pantry/pantry-page.spec.ts` |

**Endpoints:** `GET /api/pantry`, `POST /api/pantry`, `PUT /api/pantry/{id}`, `DELETE /api/pantry/{id}`, `POST /api/pantry/{id}/adjustments` y `GET /api/pantry/{id}/entries`. Todos requieren usuario autenticado y toman el propietario del token, nunca del cuerpo de la petición.

**La regla central: la cantidad se guarda en `GRAM` siempre que se pueda, y en `UNIT` para lo que se cuenta.** Se normaliza al entrar y nunca al salir. El motivo es que una despensa se lee mucho más de lo que se escribe, y todo lo que venga después (cobertura de recetas en H4, descontado en H5) tiene que comparar cantidades entre sí; guardar "2 tazas" obligaría a convertir primero, y esa conversión depende de una densidad que el ingrediente puede que no tenga.

Esa regla la implementa `UnitConverter.toStorageUnit(...)`, en el paquete `catalog`, y **no** el servicio de despensa. Vive en el puerto porque H4 necesita exactamente la misma resolución para las líneas de receta, y dos copias de la misma regla divergirían un día.

Tres comportamientos que conviene entender:

- **Conserva la unidad de entrada al convertir.** "2 CUP" pasa a gramos directamente, sin pasar antes por mililitros. Si se canonicalizara primero, se perdería la medida del ingrediente en esa unidad concreta, que es el peldaño más preciso de la escalera de densidades.
- **Un volumen sin densidad conocida se guarda en `ML`, no falla.** El hueco está en nuestros datos, no en la petición, y el usuario sí posee esa cantidad: inventar una densidad almacenaría un peso seguro y equivocado. Un error de cliente (unidad inexistente, `ingredientId` que no existe) sí se propaga como `400`/`404`.
- **La edición no admite cantidad.** `PUT /api/pantry/{id}` solo acepta `location` y `note`. Mover stock es un ajuste, y el ajuste deja asiento en `consumption_entries`; que el campo no exista en el contrato es lo que hace imposible saltarse la auditoría, en lugar de dejarla en manos de la buena voluntad del cliente.

**Una fila por `(user_id, ingredient_id)`, no por envase.** Dos botellas de leche son una línea del inventario, y `location` y `note` describen el ingrediente, no el envase. La alternativa obligaría a agregar en cada lectura y dejaría ambiguo qué fila se está editando. Por eso añadir algo que ya está responde `409` y remite al endpoint de ajuste, en vez de fusionar en silencio las dos botellas.

**Aislamiento entre usuarios.** Cada lectura y cada escritura se filtran por el propietario en la propia consulta. `PantryItemRepository` sí hereda un `findById` de `JpaRepository` — no hay forma de quitarlo sin dejar de ser un `JpaRepository`—, pero el servicio no lo usa nunca: toda carga pasa por `findByIdAndUserId`, que lleva el propietario dentro de la consulta y por eso convierte un id ajeno en un `404` en lugar de una lectura correcta. Una línea de otro usuario produce `404`, no `403`: un `403` confirmaría que el id existe, y eso basta para recorrer el inventario ajeno viendo qué ids se niegan en lugar de faltar.

**El libro de movimientos, y por qué lo escribe también el alta.** `POST /api/pantry/{id}/adjustments` mueve la cantidad con un número **con signo**: positivo añade, negativo consume. La cantidad se convierte a la unidad que la línea ya guarda, porque esa unidad es inmutable desde que se crea y cambiarla reinterpretaría todos los asientos ya escritos. `GET /api/pantry/{id}/entries` devuelve el historial de la línea.

El asiento con signo es lo que hace útil la tabla: **la suma de los asientos de una línea es su cantidad**, y eso se puede comprobar en un test en vez de darse por bueno. Para que la identidad se cumpla desde la primera fila, el alta escribe también su propio asiento con la cantidad completa. Sin él, un saldo inicial no registrado sería indistinguible de una línea a la que nadie ha tocado la cantidad, y ninguna migración posterior podría separar los dos casos.

Un ajuste de `0` se rechaza con `400`: es un movimiento mal formado, no un conflicto, y aceptarlo añadiría una entrada que no explica nada.

**Por qué un resultado negativo se rechaza con `409` y el cero se permite.** Un déficit dejaría cocinar con ingredientes que no existen: la receta diría que hay 400 g y la ausencia de error diría lo mismo, así que el plato se empezaría con algo que nadie tiene. Estar corto de un ingrediente debe costar la receta, no la sorpresa en la mesa. Una línea en cero, en cambio, dice la verdad y además obliga al usuario a decidir qué hacer con ella, que es justo lo que se quiere. El código es `signum() < 0`, no `<= 0`: `BigDecimal.ZERO.signum()` devuelve `0`, y un `<= 0` rechazaría precisamente el caso que tiene que funcionar. `V12` se corrigió a `CHECK (quantity >= 0)` para que ese estado sea representable.

El `409` y no el `400` porque la petición es válida en sí misma; lo que no la admite es el estado sobre el que se aplica. Es el mismo conflicto que el alta duplicada: el estado que el cliente no podía ver.

**Las versiones no las manda el cliente.** `PantryItem.version` (`@Version`) la incrementa Hibernate en cada escritura, dentro de la transacción, entre leer la línea y guardarla. Si dos escritores la han tocado, el segundo recibe `409` con un mensaje que le pide releer y reintentar, en vez de pisar al primero con números viejos. No hace falta que el cliente devuelva la versión: un ajuste es un delta, así que "añadir 100 g" sigue siendo válido aunque la cantidad haya cambiado mientras el formulario estaba abierto. Lo que protege son escrituras que se solapan, no una vista obsoleta.

Dos detalles del esquema que parecen rarezas y son deliberados: `consumption_entries` **no lleva columna de unidad**, porque la de la línea es inmutable y repetirla allí solo podría divergir de `pantry_items.unit_code` sin que nada lo notara; y los asientos se **borran en cascada** con su línea, porque una entrada sin línea a la que pertenecer no es un registro sino un huérfano, y conservarla obligaría a un segundo endpoint cuyo único propósito sería poder borrar.

---

## 12. Rate limiting

**Qué hace:** protege la API de abuso limitando cuántas peticiones puede hacer un cliente en una ventana de tiempo.

**Algoritmo: cubo de fichas (*token bucket*).** Se imaginan fichas que se van reponiendo a ritmo constante. Cada petición gasta una ficha; si no hay fichas disponibles, se rechaza con `429`. Su ventaja frente a otros algoritmos es que permite **ráfagas**: un cliente puede gastar de golpe las fichas acumuladas y luego vuelve al ritmo normal de reposición.

| Clase | Ruta |
|---|---|
| `RateLimitFilter` (filtro de servlet) | `common/ratelimit/RateLimitFilter.java` |
| `TokenBucketRateLimiter` | `common/ratelimit/TokenBucketRateLimiter.java` |
| `RateLimitProperties` | `common/ratelimit/RateLimitProperties.java` |
| `RateLimitConfig` (carga el script Lua) | `common/config/RateLimitConfig.java` |
| Script de Lua | `resources/common/ratelimit/restrict-token-bucket.lua` |

**Por qué un script en Lua y no Java:** el cubo vive en Redis, así que la operación «comprobar y descontar fichas» tiene que ser **atómica**. Si se hiciera en dos viajes (preguntar cuántos tokens hay y después restar), dos peticiones simultáneas podrían gastarlos dos veces. Un script de Lua se ejecuta dentro de Redis de una sola vez, sin que nada pueda interponerse.

**Quién consume cada ficha:** el cubo se identifica por `rate:{user}:{method}:{path}`. Si hay un usuario autenticado se usa su identificador; si no, la dirección IP de la petición. Se incluye método y ruta en la clave, de modo que el cubo limita cada endpoint por separado. El script fija además una caducidad (el doble del tiempo que tardaría en llenarse el cubo) para que las claves de Redis no crezcan sin límite.

Cuando la petición se rechaza, la respuesta incluye las cabeceras `X-RateLimit-Limit` y `X-RateLimit-Remaining`, que permiten al cliente saber su margen sin recibir el cuerpo de un error.

**Configuración** (`app.rate-limit` en `application.yml`): `capacity` (fichas con las que arranca el cubo), `refill-interval-millis` (cuánto tarda en reponerse **una** ficha) y `excluded-paths` (rutas libres, como el actuator y la documentación).

---

## 13. Manejo de errores de la API

**Qué hace:** convierte los errores internos en respuestas HTTP consistentes, con un formato único para toda la API.

Formato de la respuesta de error (`common/dto/ErrorResponse.java`):

```json
{
  "timestamp": "2026-10-01T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "descripción legible del problema",
  "path": "/api/units/convert"
}
```

| Clase | Función |
|---|---|
| `GlobalExceptionHandler` (`@RestControllerAdvice`) | Traduce cada excepción de dominio a su código HTTP. |
| `ErrorResponse` / `ErrorResponseFactory` | Construyen el cuerpo de la respuesta. |
| `RestAuthenticationEntryPoint` | Produce la respuesta `401`. |

**El principio de la política de errores:** `400` para todo fallo atribuible al cliente (unidad desconocida, alérgeno desconocido, clase de densidad que no existe) y `500` solo para defectos propios, como un factor de conversión que falte en la base de datos. Ese último caso está además blindado por un test de integración que comprueba que toda unidad no canónica tiene su factor: si un seed quedara incompleto, rompería el build y no la conversión en producción.

---

## 14. Observabilidad

**Qué hace:** expone la salud de la aplicación y métricas que se pueden consultar y representar en gráficos.

| Componente | Ubicación |
|---|---|
| Endpoints del actuator | `/actuator/health`, `/actuator/info`, `/actuator/prometheus` |
| Prometheus (recoge las métricas) | `infra/prometheus/prometheus.yml` → servicio `prometheus` de `docker-compose.yml` |
| Grafana (representa las métricas) | `infra/grafana/provisioning/datasources/datasource.yml` → servicio `grafana` |
| Métricas de negocio | `pantry.catalog.fdc.quota.exhausted`, contador de copias rancias servidas |

Prometheus se ejecuta en un contenedor y consulta la API desde fuera, por eso `prometheus.yml` apunta a `host.docker.internal:8080`.

---

## 15. Correo de desarrollo (Mailpit)

**Qué hace:** captura los correos que envía la aplicación en lugar de mandarlos de verdad, y los muestra en una interfaz web.

- Servicio `mailpit` en `infra/docker-compose.yml`, puerto **8025** para la interfaz web y **1025** para SMTP.
- `spring.mail.host` apunta a `mailpit`, que es el nombre del servicio dentro de la red de Docker.
- Los correos de recuperación de contraseña se ven en `http://localhost:8025` durante el desarrollo.

---

## 16. Frontend: sesión, guardas y estructura

**Qué hace:** gestiona la sesión en el navegador y decide a qué páginas puede entrar el usuario.

| Pieza | Ruta |
|---|---|
| Rutas | `src/app/app.routes.ts` |
| Servicio de autenticación | `src/app/core/auth/auth.service.ts` |
| Estado de sesión (signals) | `src/app/core/auth/session.state.ts` |
| Interceptor HTTP | `src/app/core/auth/jwt.interceptor.ts` |
| Guarda de rutas | `src/app/core/auth/auth.guard.ts` |
| Estructura visual (barra + menú) | `src/app/core/layout/shell.ts` |
| Servicio de perfil | `src/app/core/user/profile.service.ts` |
| Servicio de despensa | `src/app/core/pantry/pantry.service.ts` |
| Servicios de catálogo (búsqueda, detalle, unidades) | `src/app/core/catalog/` (`catalog.service.ts`, `unit.service.ts`, `unit-groups.ts`) |
| Páginas de autenticación | `src/app/pages/auth/` |
| Vista «Mi despensa» | `src/app/pages/pantry/pantry-page.{ts,html,scss}` |
| Diálogo de alta desde el catálogo | `src/app/pages/pantry/add-pantry-item-dialog.{ts,html,scss}` |
| Proxy de desarrollo | `proxy.conf.json` |

**Las cuatro piezas de la sesión y por qué están separadas:**

- **`SessionState`** guarda el access token y el perfil en *signals* de Angular. Vive fuera de la capa HTTP para que los componentes reaccionen a los cambios de sesión sin depender del transporte.
- **`jwt.interceptor`** añade la cabecera `Authorization` a cada petición. Si recibe un `401`, intenta **renovar el token una sola vez** y repite la petición original. Si la renovación falla, descarta la sesión y lleva al usuario al login. La renovación es *single-flight*: si cinco peticiones reciben un `401` a la vez, hacen una sola llamada HTTP entre todas.
- **`auth.guard`** protege las rutas del producto. Un usuario anónimo no es expulsado sin más: como la cookie de refresco es la única credencial del lado del cliente, el guard intenta **restaurar la sesión** (renovar el token y cargar el perfil) antes de rendirse. Si tampoco funciona, recuerda la URL visitada como `returnUrl` para devolver al usuario allí tras identificarse.
- **`proxy.conf.json`** redirige `/api` y `/oauth2` al backend durante el desarrollo, de modo que el navegador solo habla con el puerto 4200 y no aparece el problema de CORS.

**Estado actual de las páginas:** login, registro, recuperación de contraseña, perfil, «Mi despensa» y la página «no encontrado» están implementadas. Las áreas `recipes`, `cooking` y `social` existen como rutas pero muestran un marcador de posición pendiente de sus hitos.

**La vista «Mi despensa» (H3-3)** es la primera pantalla real del producto, y su pieza nueva es el servicio `PantryService`, que tipa los cinco endpoints en un único sitio para que el componente sea solo presentación. Cuatro decisiones suyas que conviene conocer:

- **Un solo panel abierto a la vez.** Editar, ajustar, ver el historial y eliminar son las cuatro acciones de una línea, pero cuatro paneles independientes volverían la lista imposible de escanear. Con un único `openPanel` (`{id, kind}`) solo una fila queda alta y el resto compacta.
- **No reordena la lista.** La API ya responde por categoría y luego por nombre; ordenar en el cliente solo podría discrepar de la colación del servidor, y costaría una copia de la lista en cada escritura. El agrupado es una sola pasada y sin `sort`.
- **Reemplaza en sitio en vez de recargar.** Ni una edición ni un ajuste pueden cambiar el nombre o la categoría, así que el orden sigue siendo válido y la respuesta del servidor ya trae la cantidad autoritativa. Recargar tras cada escritura añadiría una petición y un parpadeo por línea tocada.
- **Un `404` cierra el panel y recarga; un `409` se queda en el panel.** Un `404` significa que la línea ya no existe y no hay nada local que reparar, así que solo cabe volver a preguntarle al servidor. Un `409` de un ajuste insuficiente, en cambio, va de la cifra que tecleó el usuario: el mensaje del backend trae las tres cantidades implicadas y la corrección es teclear un número menor, no reintentar lo mismo.

**Los nombres de unidad vienen de `GET /api/units`, no de un mapa local.** `UnitService` es el único sitio donde un código se convierte en etiqueta, y carga ese catálogo una vez por sesión publicándolo en un *signal*. Antes había una tabla en `pantry-page.ts` que duplicaba el catálogo semilla: tres pantallas necesitan ya esas etiquetas (las filas, el ajuste y el alta) y tres copias de la misma tabla solo podían discrepar entre sí. La regla que sostiene el servicio es que **un código que el catálogo no conoce se muestra tal cual**: un `FLOZ` en crudo es información accionable, mientras que una celda vacía parece una cantidad que falta. Una carga fallida no es fatal ni se cachea como fallo: los códigos siguen siendo enviables, solo faltan las etiquetas, y una llamada posterior reintenta.

**El alta rápida desde el catálogo (H3-4)** es un diálogo sobre la lista de la despensa, no una página: el valor de la funcionalidad es añadir una línea *sin perder de vista* la despensa, y una ruta la sustituiría. El flujo tiene tres pasos porque el catálogo los impone — buscar, elegir (que obliga a leer el detalle, y ese detalle es lo que **materializa** el ingrediente: sin él el alta se rechaza con `404`) y decir cuánto hay — y ese tercer paso no puede existir antes de que el detalle responda.

| Decisión | Por qué |
|---|---|
| **Las medidas del ingrediente salen primero en el selector, con su peso** («CUP · 244 g») | Son las únicas unidades que el USDA pesó de verdad para ese alimento, y las que el usuario busca. El resto se ofrece igualmente y **no se filtra nada**: la conversión no necesita una medida capturada, porque el motor cae a la clase de densidad curada y, si no hay ninguna, conserva el volumen. Ocultar «por si acaso» escondería combinaciones que funcionan. |
| **Partición estable, no orden** | Las medidas se mueven al principio de su grupo y el resto conserva el orden del catálogo. Un `sort` podría reordenar de más maneras de las previstas; una partición solo mueve lo que tiene que mover. |
| **El `409` no es un callejón sin salida** | El backend pide ajustar la cantidad existente en lugar de añadir una segunda línea, así que el diálogo entrega el `fdcId` y la página localiza esa línea en la lista que ya tiene y **le abre el panel de ajuste**. |
| **El `404` se repara con la llamada que lo arregla** | Es el único fallo con reparación concreta: volver a leer el detalle. El backend nombra esa llamada en su mensaje, y el diálogo la ofrece en vez de reintentar un alta que volvería a fallar. Se ofrece, no se dispara sola, porque esa lectura puede costar cuota del proveedor. |
| **Nada reintenta solo** | El USDA responde `502` tanto si está caído como si ha agotado su cuota (su `429` se traduce a `502`), y la cuota son **30 peticiones por hora sin key**. Reintentar en silencio gastaría una cuota que ya no existe, así que el texto nombra las dos causas y decide el usuario. |
| **Tras guardar, la lista se recarga** | El orden y el agrupado son del servidor; buscar la posición alfabética de la línea nueva en el cliente es la duplicación que la página evita en todas partes. El aviso de confirmación enseña la cantidad **tal como se guardó**, porque el backend normaliza a la unidad de la línea y enseñar lo tecleado mentiría sobre el dato. |
| **El `POST` lleva `fdcId`, nunca el id interno** | Es el contrato deliberado: el cliente solo ve ids del proveedor en el catálogo, y esa elección es la que permite responder `409` y `404` con un mensaje accionable. |
| **Contadores de secuencia en las dos lecturas** | El template ya deshabilita el botón de buscar mientras busca y los resultados mientras se lee un detalle, así que hoy no hay forma de solaparlas. Los contadores están para que **los métodos no dependan de esa disposición**: `add()` ya refusaba un segundo clic en código y no en el botón, y estas dos lecturas eran la excepción. |



---

## 17. Migraciones de base de datos

**Qué hace:** crea y modifica el esquema de la base de datos de forma versionada y reproducible.

Las migraciones son ficheros SQL numerados en `backend/pantry-api/src/main/resources/db/migration/` y se aplican **en orden alfabético** en el arranque (`V1`, `V2`, `V3`…). Flyway registra cuáles se han aplicado, así que solo ejecuta las nuevas.

| Migración | Qué introduce |
|---|---|
| `V1__init.sql` | `users`, `units`, `measure_conversion`, `allergens`. |
| `V2__auth_mods.sql` | `refresh_tokens`, `user_roles`, columnas de usuario. |
| `V3__profile.sql` | `user_preferences`, `user_allergy_exclusions` y catálogo de alérgenos. |
| `V4__password_reset_tokens.sql` | Tokens de recuperación de contraseña. |
| `V5__catalog_units.sql` | Catálogo de unidades US customary, tabla `measure_conversions` y densidades iniciales. |
| `V6__catalog_ingredients.sql` | `ingredients` con nutrientes e índice único por `fdc_id`. |
| `V7__catalog_ingredient_measures.sql` | `ingredient_measures` y la unidad `FLOZ`. |
| `V8__catalog_density_classes.sql` | `ingredient_densities` pasa a llamarse `density_classes`; `ingredients.density_class`. |
| `V9__catalog_density_class_sources.sql` | Columna `source` (procedencia) y la clase `MILK`. |
| `V10__catalog_ingredient_freshness.sql` | `data_type`, `synced_at` e índice de frescura. |
| `V11__catalog_density_class_revalidation.sql` | Procedencia de `FLOUR`, `SUGAR` y `OIL`. |
| `V12__pantry_items.sql` | `pantry_items` (con `version` para bloqueo optimista y `CHECK (quantity >= 0)`) y `consumption_entries` (`delta_quantity` con signo, sin columna de unidad, borrado en cascada). |

**Regla importante:** una migración ya aplicada **no se edita jamás**; se añade una nueva. Editarla dejaría los entornos desincronizados sin que nada avise. Por eso la procedencia de los valores de `FLOUR`/`SUGAR`/`OIL` no se corrigió en `V5` sino que se documentó en `V11`.

---

## 18. Tests

**Qué se prueba y cómo:**

| Tipo | Ubicación | Qué comprueba |
|---|---|---|
| Unitarios | `backend/pantry-api/src/test/java/.../` | Lógica aislada: el mapeo del adaptador del USDA, la política de caché, el motor de conversión. |
| Integración | `backend/pantry-api/src/test/java/.../` | La aplicación entera contra servicios reales (`@SpringBootTest`). |
| Frontend | `frontend/pantry-web/src/**/*.spec.ts` | Componentes, servicios e interceptores con Vitest + jsdom. |

Los tests Java replican la estructura del código principal: un test de `catalog/client/FdcFoodCatalogClient.java` vive en `catalog/client/FdcFoodCatalogClientTest.java`. `AbstractIntegrationTest` es la clase base de los tests de integración y aporta, entre otras cosas, un helper que autentica al usuario en la base de datos y vuelve a hacer login, para que el rol llegue en el token.

**Los tests de integración no usan bases de datos de mentira.** `TestcontainersConfiguration` arranca un PostgreSQL y un Redis reales en contenedores, de modo que las migraciones, las claves foráneas y las consultas se ejecutan tal como en producción. Un test que pasa contra un motor de base de datos falso puede fallar en producción por una diferencia sutil de sintaxis SQL.

**`AbstractIntegrationTest` limpia `pantry_items` antes que `users`.** El orden importa: es la única tabla que a la vez contiene datos y es referenciada por las dos que otros tests vacían. Si se quedara, borrar un usuario o un ingrediente fallaría por la clave foránea y el fallo parecería un error en el borrado en lugar de un orden de limpieza equivocado.

Para los tests que necesitan el catálogo, `StubFoodCatalogPortConfig` registra `StubFoodCatalogPort` como `@Primary`, de manera que el USDA real no se invoca nunca y no se gasta cuota. El `src/test/resources/application.yml` desactiva además la recarga del cubo de fichas (`refill-interval-millis: 3600000`) para que los tests que cuentan tokens exactos no se vuelvan intermitentes; la recarga se cubre por separado en `TokenBucketRefillIntegrationTest`, que sobrescribe ese intervalo.

**Puertas de calidad:** JaCoCo verifica la cobertura y el `Jenkinsfile` ejecuta los tests como paso obligatorio del pipeline.

---

## 19. CI/CD e imágenes Docker

**Qué hace:** construye, prueba y publica la imagen de la API automáticamente.

Etapas del `jenkins/Jenkinsfile`: `Checkout` → `Backend: build & tests (JaCoCo gate)` → `Frontend: build` → `Docker: build image` → `Docker: publish`.

**El `Dockerfile` usa dos etapas (*multi-stage build*):**

1. **Construcción** con la imagen de Maven, donde se compila y se empaqueta el JAR.
2. **Ejecución** con una imagen de JRE mínima, que solo contiene el artefacto ya compilado.

La razón es el tamaño y la superficie: la imagen final no incluye Maven, ni el código fuente, ni las dependencias de compilación, así que ocupa mucho menos y tiene menos superficie de ataque.

**Levantar todo el entorno** se hace desde `infra/`: `docker compose up -d`. El servicio `pantry-api` espera a que PostgreSQL y Redis estén sanos antes de arrancar, gracias a las condiciones `service_healthy` de `docker-compose.yml`.

---

## 20. Zona horaria y notas de mantenimiento

- **Zona horaria:** la aplicación trabaja en UTC. `TimeConfig` expone un bean `Clock` en lugar de llamar a `Instant.now()` directamente en el código, lo que permite sustituir el reloj en los tests y comprobar vencimientos de tokens y de caché sin esperar en tiempo real.
- **Los secretos nunca se versionan:** viven en `infra/.env`, ignorado por git. En `infra/.env.example` solo aparecen los nombres de las variables.
- **Cambios de dependencias:** cualquier versión nueva o modificada tiene que registrarse en `docs/VERSIONS.md` en el mismo commit, y hay que consultar ese documento antes de proponer una dependencia.
- **Antes de tocar el proveedor externo (USDA FDC):** la forma de su respuesta se verificó contra la API real, no contra la documentación ni contra los registros del adaptador. Los tests de `FdcFoodCatalogClientTest` usan un recorte literal de una respuesta real; mantener esa práctica es lo que evita que un cambio de wire pase desapercibido.
