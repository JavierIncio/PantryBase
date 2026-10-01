# MEMORY.md — Estado del proyecto PantryBase

> **Este fichero es el punto de partida de cada sesión.**
> Al empezar a trabajar, léelo. Al terminar una sesión, actualízalo con el estado real.
> Máximo 100 líneas. Última actualización: **1 de octubre de 2026** (cierre de H2-B).

## Dónde estamos

**Hito completado: H2 (catálogo de ingredientes y motor de unidades).** H0, H1 y H2 están cerrados y verificados.
Commits relevantes del último trabajo: `298c109` (wire real del proveedor USDA), `ff459c7` (caché y frescura), `4928544` (validación de la key al arrancar), `cad8e8b` (la key no se escribe en logs), `e947623` (administración de clases de densidad).

Estado verificado del backend: **192 tests verdes**, gate de JaCoCo superado (`.\mvnw.cmd -o verify`).
Rama `main` **5 commits por delante de `origin/main`**: nada se ha hecho push todavía.

## Qué está construido

- **H0 Fundación:** Maven + Spring Boot 4.1.1 / Java 21, Angular 22 con Material, Vitest, Docker Compose, Jenkins, Prometheus + Grafana, Mailpit.
- **H1 Autenticación:** registro, login, access token (15 min) + refresh token en cookie `httpOnly` (7 d, hasheado en BD), cambio de contraseña, recuperación por correo con token de 15 min, login con Google (OAuth2), roles, rate limiting por cubo de fichas en Redis.
- **H1 Usuario:** perfil, preferencias de filtrado (`filterMode`, `coverageThreshold`, `diet`) y lista de exclusión de alérgenos.
- **H2-A Unidades:** 12 unidades US customary, tabla `measure_conversions`, densidades por clase y `UnitConversionService` con la escalera de precedencia medida propia > otra medida > clase del ingrediente > clase pasada.
- **H2-B Catálogo:** integración USDA FDC (búsqueda, detalle y lote), materialización idempotente, medidas por ingrediente, densidad por clase curada, administración de clases con procedencia, caché/frescura (`cache-ttl` 24 h, `max-stale` 30 d), Redis para búsqueda y manejo de 429.

## Próximos pasos

1. **H3 — Inventario (despensa).** Es el siguiente hito del roadmap; aún no está definido en bloques. Antes de escribir código, el usuario y el agente deben acordar el alcance, los criterios de aceptación y el corte del primer bloque.
2. **Frontend del catálogo.** Existe la API de ingredientes y unidades, pero **la web no tiene todavía pantallas de catálogo**: el buscador y el conversor están pendientes de implementar (los toca el subagente `frontend`).
3. **Verificar el login de Google** contra un cliente real de OAuth2; hasta ahora solo se ha probado el camino con contraseña.

## Cuellos de botella y riesgos

- **Cuota de USDA FDC:** ~1.000 req/h con key propia, **30 req/h con `DEMO_KEY`**. Las ventanas de caché lo hacen utilizable; cualquier endpoint nuevo que llame al proveedor debe pasar por `FdcCachePolicy` o gastará cuota.
- **Forma del wire del USDA:** ya costó tres correcciones (`298c109`). La regla vigente: los fixtures del adaptador son recortes literales de respuestas reales. No escribir fixtures a partir de los records del adapter.
- **Densidades:** solo hay 4 clases curadas (`FLOUR`, `SUGAR`, `OIL`, `MILK`) y cada vez que falte una el motor de unidades falla con `400` a propósito. La categoría del proveedor **no** sirve como densidad (demostrado en H2-B-4). Ampliar el catálogo solo con valores citables de una porción FDC.
- **Sesión de OAuth2 sin verificar en producción**, y `app.security.cookie.secure` está en `false`: hay que ponerla en `true` al desplegar.
- **`infra/.env` es local:** cualquier despliegue necesita sus propias variables (`JWT_SECRET`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `USDA_FDC_API_KEY`, `DB_*`). No hay secretos en el repositorio.
- **Deuda técnica anotada en el roadmap:** Vault/CI para secretos, auditoría de llamadas externas, y límite de cuota propio por usuario en el rate limiting (hoy es por usuario o IP, no por usuario con coste).

## Cómo trabajar aquí

- Comandos: backend `.\mvnw.cmd -o verify` en `backend/pantry-api`; infra `docker compose up -d` en `infra/`.
- Documentación: `README.md` (arquitectura y decisiones), `docs/ROADMAP.md` (plan por hitos), `docs/VERSIONS.md` (versiones fijadas, se consulta **antes** de proponer cualquier dependencia), `docs/FEATURE_GUIDE.md` (qué hace cada funcionalidad y dónde vive).
- Reglas de trabajo y de idioma: `AGENTS.md`.
- Al cerrar cada fase: tests verdes + `README.md` y `docs/ROADMAP.md` actualizados + commit con mensaje convencional. Nada de push sin pedirlo.
