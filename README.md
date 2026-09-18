# PantryBase

Recetario inteligente que recomienda recetas en función de lo que el usuario tiene en su despensa (ingredientes y cantidades registradas), con información nutricional y de alérgenos, modo cocina con seguimiento de progreso y descontado automático de stock, y recomendaciones generadas por LLM.

**Estado:** planificación. Ver [docs/ROADMAP.md](docs/ROADMAP.md).

---

## Visión general

El usuario registra su inventario de ingredientes con cantidades en medidas estandarizadas. PantryBase consulta catálogos externos de alimentos y recetas (Edamam), las filtra según la disponibilidad real de la despensa (modo estricto o laxo) y le ayuda a cocinar paso a paso: al marcar la receta como finalizada se descuentan automáticamente las cantidades consumidas. Un asistente basado en LLM (gratuito, multi-proveedor con rotación y fallback) complementa la recomendación.

### Funcionalidades principales

- **Inventario (despensa):** CRUD de ingredientes con cantidades en unidades estandarizadas.
- **Autenticación:** registro y login con JWT (access token por header, refresh token en cookie `httpOnly` con rotación) y login social OAuth2 (Google) con link-or-create.
- **Catálogo de ingredientes:** datos, nutrición y alérgenos vía API de Edamam (Food Database), cacheado en base de datos.
- **Recetas:** búsqueda y detalle desde Edamam (Recipe Search API): ingredientes con cantidades, nutrición y alérgenos.
- **Filtrado por disponibilidad:**
  - _Estricto:_ solo recetas que el usuario puede hacer con lo que tiene (todas las cantidades cubiertas).
  - _Laxo:_ recetas parcialmente cubiertas, con porcentaje de cobertura configurable (slider 0-100%).
- **Modo cocina:** estados de progreso de la sesión (`EN_CURSO`, `COMPLETADA`, `ABANDONADA`, …); al finalizar se descuentan las cantidades del inventario. También descontado manual.
- **Social y personal:** recetas favoritas, guardadas, publicadas e historial de recetas realizadas.
- **Recomendación con LLM:** asistente que sugiere recetas a partir del inventario (gratis, multi-proveedor con rotación y fallback).
- **Preferencias de usuario:** exclusión de alérgenos, dieta, modo de filtrado por defecto.

---

## Stack tecnológico

Propuesto inicialmente por el cliente, con adiciones/recomendaciones marcadas (+).

| Capa           | Tecnología                              | Uso                                                          |
| -------------- | --------------------------------------- | ------------------------------------------------------------ |
| Backend        | Spring Boot 4.1 (Java 21)               | API REST                                                     |
| Backend        | Spring Security + JWT + OAuth2 (cliente) | Autenticación, autorización y login social |
| BBDD           | PostgreSQL 18 (+)                       | Datos persistentes (inventario, usuarios, catálogo cacheado) |
| BBDD           | Flyway (+)                              | Migraciones versionadas del esquema                          |
| Backend        | Spring Data JPA                         | Acceso a datos                                               |
| Cache / RL     | Redis                                   | Rate limiting y caché de respuestas de Edamam                |
| Resiliencia    | Resilience4j (+)                        | Circuit breaker, retry y rate limiter para Edamam y LLMs     |
| LLM            | Spring AI (+)                           | Capa multi-proveedor con rotación de modelos gratuitos       |
| Docs API       | springdoc-openapi (+)                   | Contrato OpenAPI                                             |
| Frontend       | Angular 22.1 (activa) + Angular Material | SPA                                                          |
| CI/CD          | Jenkins                                 | Pipeline build, test, quality gates y deploy                 |
| Observabilidad | Micrometer + Prometheus + Grafana       | Métricas, dashboards y alertas                               |
| Testing        | JUnit 5 + Testcontainers (back) y Vitest (front) (+) | Tests de integración con infraestructura real    |
| Infra          | Docker Compose (dev) · Docker           | Contenedorización de servicios                               |

> **Versiones fijadas y verificadas del stack en [`docs/VERSIONS.md`](docs/VERSIONS.md).** Cualquier propuesta de dependencia o comando debe consultarlo previamente.

### Justificación de adiciones

- **PostgreSQL + Flyway:** base relacional robusta para el dominio (inventario, conversiones, sesiones) con esquema versionado. Mejor que MySQL/H2 para consultas con joins y filtros complejos.
- **Resilience4j:** Edamam y los proveedores de LLM son servicios externos; el circuit breaker evita cascadas y habilita la rotación de fallos de forma controlada.
- **Spring AI:** abstrae el acceso a múltiples LLMs (Ollama, OpenRouter, Groq, Gemini…) y facilita el fallback entre modelos.
- **Testcontainers:** tests de integración repetibles contra PostgreSQL/Redis reales, sin acoplar a un entorno local.
- **springdoc-openapi:** documentación viva de la API y cliente generable para Angular.

### Opcionales a evaluar en producción

- **Loki + Promtail** para agregación de logs ligera (alternativa a un ELK completo).
- **Vault / secrets manager** para secretos de API keys.
- **Kafka/RabbitMQ** solo si se necesita procesamiento asíncrono (p. ej. ingestión masiva de recetas); para el MVP no es necesario.

---

## Arquitectura

```
                       ┌──────────────────────── externos ──────────────────────────┐
                       │  Edamam (Food DB / Recipe API)    LLMs gratuitos (rotación)│
                       └───────────────┬──────────────────────┬─────────────────────┘
                                       │ REST (Retry/Circuit) │ Spring AI (fallback)
                                       ▼                      ▼
┌────────────┐   JWT    ┌──────────────────────────────── Spring Boot ──────────────────────────────┐
│ Frontend   │ ───────► │                                                                           │
│ Angular+Mat│ ◄─────── │  Modulos: Security · Catalog · Pantry · Recipes · Cooking · Social · AI   │
└────────────┘ Rest/JSON│                                                                           │
                        └───┬──────────────┬───────────────┬───────────────┬───────────────┬────────┘
                            │              │               │               │               │
                            ▼              ▼               ▼               ▼               ▼
                       PostgreSQL      Redis (cache +   Micrometer ───► Prometheus ──► Grafana
                        (Flyway)       rate limiting)      │
                                                           └─► métricas de endpoint, DB, LLM

Jenkins: build → test (JaCoCo) → quality gate → build Docker → deploy (docker compose / compose-swarm)
```

**Flujo principal:** el usuario registra la despensa → PantryBase calcula cobertura sobre el catálogo de recetas → presenta resultados según modo de filtrado → el usuario cocina (sesión con estados) → al completar se deducen cantidades → historial agregado.

---

## Modelo de dominio (esquema objetivo)

- **User** — identidad y credenciales.
- **Ingredient** — catálogo de alimentos (nombre normalizado, categoría, nutrientes, alérgenos) cacheado desde Edamam.
- **Unit** — unidad canónica (`GRAM`, `ML`, `UNIT`) con tabla de conversión de medidas comunes (taza, cucharada, cucharadita, onza, libra…) a la canónica según categoría/densidad del ingrediente.
- **PantryItem** — `Ingredient` + `User` + cantidad en unidad canónica + `location/nota`.
- **Recipe** — cabecera de receta (fuente Edamam, dietas, nutrientes por ración, imagen) con sus **RecipeIngredient\*** line items (ingrediente, cantidad, unidad, peso/shorthand del proveedor).
- **Allergen / UserAllergyExclusion** — ontología de alérgenos y exclusiones por usuario.
- **UserPreferences** — modo de filtrado (laxo/estricto), umbral de cobertura por defecto, dieta.
- **CookingSession** — sesión activa de una receta con estados de progreso (`EN_CURSO`, `COMPLETADA`, `ABANDONADA`, `EN_PAUSA`).
- **ConsumptionEntry** — registro de descontado (automático al completar sesión o manual): `PantryItem`, cantidad, origen y timestamp.
- **Favorite / SavedRecipe / PublishedRecipe** — vínculos personales con recetas.
- **RecipeHistory** — histórico de recetas realizadas.

Relaciones clave: `User 1─* PantryItem`, `Recipe 1─* RecipeIngredient`, `User 1─* CookingSession 1─* ConsumptionEntry *─1 PantryItem`.

---

## Estandarización de medidas

Tres unidades canónicas:

| Tipo      | Unidad canónica |
| --------- | --------------- |
| Sólidos   | gramos (g)      |
| Líquidos  | mililitros (ml) |
| Contables | unidades (u)    |

- Las medidas de receta del proveedor (p. ej. “1 taza de harina”) se convierten a canónicas con una **tabla de conversión** base (taza=240 ml; cucharada=15 ml; cucharadita=5 ml; etc.) corregida por **densidad por ingrediente/categoría** para los sólidos (Edamam devuelve gramos por medida en la mayoría de casos — se aprovecha ese peso cuando exista).
- El inventario registra siempre en unidad canónica; la UI usa selectores de unidades con conversión automática.
- Decisiones sobre ingredientes con densidad ambigua quedan registradas en una tabla `measure_conversion` editable (sobrescribible manualmente).

---

## Contexto del uso de las APIs de Edamam

- **Food Database API:** búsqueda/autocompletado de ingredientes, datos de nutrientes y categorías → cacheado como `Ingredient`.
- **Recipe Search API:** búsqueda de recetas por ingredientes disponibles; proporciona cantidades, nutrición por ración y etiquetas de dieta (los **alérgenos** se derivan del análisis de ingredientes y de un mapeo mantenido internamente, ya que no hay un campo alérgeno directo garantizado).
- Las respuestas se cachean en Redis/local para controlar cuotas y latencia. Toda llamada externa pasa por Resilience4j (retry + circuit breaker) y rate limiting.

---

## LLM de recomendación (gratuito)

- **Spring AI** con una cadena de proveedores gratuitos configurable: Ollama (local, 100 % gratis) primero en desarrollo, y en producción OpenRouter (modelos free: `qwen`, `llama`, …), Groq free tier o Gemini free tier.
- **Rotación y fallback automático:** si un proveedor responde error, quota agotada o time-out, se rota al siguiente de la cola; un circuit breaker aísla al proveedor degradado y se reintenta tras un cooldown.
- El LLM **no inventa recetas**: opera sobre el catálogo real (resultados de Edamam ++ inventario del usuario) mediante tool-calling/prompt estructurado que devuelve JSON con ids de recetas y justificación de cobertura.

---

## Estructura del repositorio (objetivo)

```
PantryBase/
├── README.md
├── docs/
│   └── ROADMAP.md
├── backend/
│   └── pantry-api/            # Spring Boot
│       └── src/main/java/com/pantrybase/api/
│           ├── config/        # seguridad, openapi, redis, ai
│           ├── user/          # auth, preferencias
│           ├── catalog/       # ingredientes, medidas, conversiones, alergenos
│           ├── pantry/        # inventario
│           ├── recipes/       # recetas Edamam, filtrado
│           ├── cooking/       # sesiones, estados, descontado
│           ├── social/        # favoritas, guardadas, publicadas, historial
│           ├── ai/            # recomendador LLM (rotación/fallback)
│           └── common/        # utilidades, mappers
├── frontend/
│   └── pantry-web/            # Angular + Material
├── infra/
│   ├── docker-compose.yml     # postgres, redis, prometheus, grafana
│   ├── prometheus/
│   └── grafana/provisioning/
└── jenkins/                   # Jenkinsfile y pipelines
```

---

## Puesta en marcha (dev, planificada)

```bash
# 1. Infraestructura local
docker compose -f infra/docker-compose.yml up -d

# 2. Backend
cd backend/pantry-api
./mvnw spring-boot:run          # conf.vía env: DB, Redis, EDAЯAM_API_*, LLM providers

# 3. Frontend
cd frontend/pantry-web
npm install && npm start        # dev server con proxy a la API

# API docs en http://localhost:8080/swagger-ui.html
```

Variables de entorno clave: `SPRING_DATASOURCE_*`, `SPRING_DATA_REDIS_*`, `JWT_SECRET`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `EDAMAM_APP_ID`, `EDAMAM_APP_KEY`, `LLM_PROVIDERS` (lista ordenada de proveedores con fallback). Secretos solo en `infra/.env` (ignorado por git).

---

## Observabilidad

- **Métricas ya incluidas:** tiempos y errores por endpoint (Micrometer), latencia a Edamam y a cada proveedor LLM, tasa de fallback entre modelos, tamaño del inventario, sesiones de cocina completadas.
- **Dashboards en Grafana** para: salud de servicios, cuotas de APIs externas, uso de la despensa y rendimiento del recomendador.
- **Rate limiting** implementado en Redis (por usuario y por endpoint) y expuesto en métricas para detectar abusos.

---

## Roadmap

Ver el detalle de fases, tareas y criterios de aceptación en [docs/ROADMAP.md](docs/ROADMAP.md).

Principales etapas: **H0 Fundación → H1 Auth → H2 Catálogo y unidades → H3 Inventario → H4 Recetas y filtrado → H5 Modo cocina → H6 LLM → H7 Social → H8 Producción**.
