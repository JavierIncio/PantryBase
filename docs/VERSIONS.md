# Versiones del proyecto (registro de dependencias)

> **Última verificación:** septiembre 2026.
> Este fichero es la **fuente única de verdad de versiones**. Cualquier agente o guía DEBE consultarlo antes de sugerir una dependencia, un comando de scaffolding o una imagen Docker, y DEBE actualizarlo en el mismo commit en que cambie una dependencia (regla de cierre de fase).

## Reglas de gestión de versiones

1. **«Regla número uno»: dejar que Spring Boot gestione.** No fijar versión manual para dependencias cubiertas por el BOM de Spring Boot 4.1.1 (Jackson, Flyway, JUnit, AssertJ, Micrometer, Prometheus client, Spring Data/Web/Security, etc.). La columna _Notas_ lo indica con **(BOM)**.
2. Marcar con `(verificar en H0)` todo aquello cuya etiqueta/tag exacta se decide al levantar la infraestructura; fijarla entonces y quitar la marca.
3. Antes de proponer una upgrade, comprobar en Maven Central / npm / Docker Hub. Nunca inventar versiones.
4. Herramientas útiles para verificar desfases: `mvn versions:display-dependency-updates`, `npm outdated`, `npx ng update`.

---

## Backend (Java)

| Componente        | Versión                                | Notas                                                                               |
| ----------------- | -------------------------------------- | ----------------------------------------------------------------------------------- |
| JDK               | **21 (LTS)** mínima; 25 (LTS) opcional | Spring Boot 4.1 soporta Java 17–26                                                  |
| Spring Boot       | **4.1.1** (ago 2026)                   | OSS ~12 meses (EOL jul 2027). **No usar 3.x**: el OSS de 3.5 terminó en jun 2026    |
| Spring Framework  | 7.0.x                                  | Gestionada por Boot (**BOM**), no versionar                                         |
| Jackson           | 3.2.x                                  | Spring Boot 4 usa **Jackson 3** (`tools.jackson.*`). (**BOM**, no versionar manual) |
| springdoc-openapi | **3.1.1**                              | Artefacto `springdoc-openapi-starter-webmvc-ui`; línea 3.x solo para Boot 4         |
| Spring AI         | **2.0.1**                              | Requiere Spring Boot 4.x; soporta multi-proveedor                                   |
| Resilience4j      | **2.4.0**                              | Usar artefacto `resilience4j-spring-boot4` (compatibilidad Boot 4)                  |
| Flyway            | **13.7.0** (standalone)                | En el proyecto usar `spring-boot-starter-flyway` (**BOM**)                          |
| Testcontainers    | **2.0.5**                              | Importar `testcontainers-bom` con esa versión                                       |
| JaCoCo            | **0.8.15**                             | Plugin Maven; quality gate en `verify`: cobertura de línea ≥ 0.80 (goal `check`)    |
| JWT (JJWT)        | **0.13.0**                             | Artefactos `jjwt-api`, `jjwt-impl`, `jjwt-jackson` (0.13.0, verificada ago 2025)    |
| oauth2-jose       | (BOM) Spring Security 7               | `spring-security-oauth2-jose`: `JwtEncoder`/`JwtDecoder` (Nimbus); gestionada por Boot, no versionar |
| BCrypt            | (BOM)                                 | `BCryptPasswordEncoder` via `spring-security-crypto` (incluida en `spring-boot-starter-security`) |

### Dependencias gestionadas por el BOM (no fijar manualmente)

JUnit 5, AssertJ, Mockito, Micrometer, `micrometer-registry-prometheus`, PostgreSQL JDBC, Hibernate, Lombok (opcional), `spring-boot-starter-*`, `spring-boot-starter-actuator`, `spring-security-oauth2-jose`, `spring-security-crypto` (BCrypt).

---

## Frontend

| Componente            | Versión                                | Notas                                                                      |
| --------------------- | -------------------------------------- | -------------------------------------------------------------------------- |
| Angular + Angular CLI | **22.1.x**                             | v22 activa (jun 2026 – jun 2027); v21 LTS, v20 LTS                         |
| Angular Material      | **`@angular/material` 22.1.x**         | Mismo calendario de soporte que el framework                               |
| Node.js               | **24 LTS «Krypton» (≥ 24.15)**         | Angular 22 requiere `^22.22.3 \|\| ^24.15 \|\| ^26`; `.nvmrc` = `24`, npm 11.x |
| TypeScript            | `~6.0.2`                               | Gestionada por el CLI de Angular (Angular 22 usa TS 6.x)                       |
| RxJS                  | `~7.8.0`                               |                                                                                |
| Testing               | **Vitest** (decidido en H0)            | `vitest ^4.0.8` + `jsdom ^28.0.0`; `prettier ^3.8.1` para formato               |

---

## Infraestructura (imágenes Docker)

| Servicio                | Versión                     | Notas                                                         |
| ----------------------- | --------------------------- | ------------------------------------------------------------- |
| PostgreSQL              | **18.6** (13 ago 2026)      | Imagen `postgres:18-alpine`                                   |
| Redis                   | serie **8.x** (OSS 8.6)     | Imagen `redis:8-alpine`; caché de Edamam + rate limiting      |
| Prometheus              | **3.14.0**                  | Imagen `prom/prometheus:v3.14.0`; 3.13 es la línea LTS actual |
| Grafana                 | **13.0.9**                  | Imagen `grafana/grafana:13.0.9`; provisioning de datasource Prometheus (`infra/grafana/provisioning/`, `apiVersion: 1`) |
| Jenkins                 | LTS 2.x                     | Imagen `jenkins/jenkins:lts-jdk21`; despliegue del servidor CI pendiente (H0 local)                               |
| OpenTelemetry Collector | opcional                    | Solo si se decide adopción de OTel en vez de scrape directo   |

---

## APIs externas y LLM

| Recurso                    | Detalle                                                                                                                                                                     |
| -------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Edamam — Food Database API | Búsqueda/auto-completado de ingredientes, nutrientes y categorías. Credenciales app_id + app_key.                                                                           |
| Edamam — Recipe Search API | Recetas completas: cantidades, nutrición por ración, dietas. Alérgenos se derivan con mapeo interno (sin campo directo garantizado).                                        |
| LLM gratuitos (rotación)   | Ollama (local, 100% gratis) → OpenRouter (`:free`) → Groq free tier → Gemini free tier. Orden configurable vía `LLM_PROVIDERS`. Ver revisar modelos `:free` vigentes en H6. |
| Rate limiting              | Redis (resilience4j) por usuario + endpoint; cuotas externas vigiladas en Grafana.                                                                                          |

---

## Procedimiento cuando algo cambia

1. Cambiar la versión en `pom.xml` / `package.json` / `docker-compose.yml`.
2. Actualizar este fichero en el mismo commit.
3. Si la versión nueva rompe compatibilidad con el BOM o convenciones, registrarlo aquí.
4. Cerrar la fase con `docs` actualizadas y commit (regla general del proyecto, ver `AGENTS.md`).
