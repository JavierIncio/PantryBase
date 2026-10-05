# MEMORY.md — Estado del proyecto PantryBase

> **Este fichero es el punto de partida de cada sesión.**
> Al empezar a trabajar, léelo. Al terminar una sesión, actualízalo con el estado real.
> Máximo 100 líneas. Última actualización: **5 de octubre de 2026** (H3-1 cerrado).

## Dónde estamos

**Hito en curso: H3 (inventario).** H0, H1 y H2 están cerrados y verificados. El bloque **H3-1 está implementado, verificado y commiteado** (`feat(pantry): CRUD of pantry items with unit normalization on write`).

Estado verificado del backend: **215 tests verdes** (192 al cerrar H2 + 23 nuevos de H3-1), gate de JaCoCo superado (`.\mvnw.cmd -o verify`).
Rama `main` sin push: los commits locales siguen sin publicarse.

## Qué está construido

- **H0 Fundación:** Maven + Spring Boot 4.1.1 / Java 21, Angular 22 con Material, Vitest, Docker Compose, Jenkins, Prometheus + Grafana, Mailpit.
- **H1 Autenticación:** registro, login, access token (15 min) + refresh token en cookie `httpOnly` (7 d, hasheado en BD), cambio de contraseña, recuperación por correo con token de 15 min, login con Google (OAuth2), roles, rate limiting por cubo de fichas en Redis.
- **H1 Usuario:** perfil, preferencias de filtrado (`filterMode`, `coverageThreshold`, `diet`) y lista de exclusión de alérgenos.
- **H2-A Unidades:** 12 unidades US customary, tabla `measure_conversions`, densidades por clase y `UnitConversionService` con la escalera de precedencia medida propia > otra medida > clase del ingrediente > clase pasada.
- **H2-B Catálogo:** integración USDA FDC (búsqueda, detalle y lote), materialización idempotente, medidas por ingrediente, densidad por clase curada, administración de clases con procedencia, caché/frescura (`cache-ttl` 24 h, `max-stale` 30 d), Redis para búsqueda y manejo de 429.
- **H3-1 Despensa:** paquete `pantry` nuevo. `GET/POST/PUT/DELETE /api/pantry`, `V12__pantry_items.sql` (`pantry_items` + `consumption_entries`), cantidad normalizada a `GRAM`/`UNIT`, una fila por `(user_id, ingredient_id)`, edición sin campo de cantidad, aislamiento por propietario con `404`, y `UnitConverter.toStorageUnit(...)` como nueva operación del puerto.

## Decisiones ya tomadas de H3 (no volver a abrir sin motivo)

- La cantidad se guarda en **`GRAM` siempre que se pueda** y en `UNIT` para lo que se cuenta. Si un volumen no tiene densidad conocida se guarda en `ML` en vez de fallar: el hueco está en nuestros datos, no en la petición.
- **Una fila por `(user_id, ingredient_id)`**; añadir algo que ya está responde `409` y remite al ajuste, nunca fusiona en silencio.
- **Un único endpoint de ajuste con cantidad con signo**, toda suma o resta deja `ConsumptionEntry`, y se rechaza si dejaría la cantidad en cero o negativa.
- El `PATCH` que se consensuó acabó siendo `PUT`: reemplaza ubicación y nota, y no admite cantidad, para que saltarse la auditoría sea imposible por contrato y no por convención.

## Próximos pasos

1. **H3-2:** endpoint de ajuste con cantidad con signo y `ConsumptionEntry`; comprobar el invariante "la suma de los deltas de una línea es su cantidad". Pendiente además: la carrera entre el `existsByIngredientId` previo y el `insert` puede dar `500` en lugar de `409` si dos altas simultáneas compiten.
2. **H3-3 frontend:** vista "Mi despensa" agrupada por categoría y edición inline (toca el subagente `frontend`).
3. **Frontend del catálogo:** el buscador y el conversor siguen sin pantalla.
4. **Verificar el login de Google** contra un cliente real de OAuth2; hasta ahora solo se ha probado el camino con contraseña.

## Cuellos de botella y riesgos

- **Cuota de USDA FDC:** ~1.000 req/h con key propia, **30 req/h con `DEMO_KEY`**. Las ventanas de caché lo hacen utilizable; cualquier endpoint nuevo que llame al proveedor debe pasar por `FdcCachePolicy` o gastará cuota.
- **Forma del wire del USDA:** ya costó tres correcciones (`298c109`). La regla vigente: los fixtures del adaptador son recortes literales de respuestas reales. No escribir fixtures a partir de los records del adapter.
- **Densidades:** solo hay 4 clases curadas (`FLOUR`, `SUGAR`, `OIL`, `MILK`). La categoría del proveedor **no** sirve como densidad (demostrado en H2-B-4). Ampliar el catálogo solo con valores citables de una porción FDC.
- **Sin densidad, `toStorageUnit` deja la cantidad en `ML`:** es deliberado, pero significa que muchas líneas de despensa quedarán en mililitros mientras falten clases curadas, y H4 tendrá que convertir al comparar. Vigilarlo al llegar a H4.
- **Sesión de OAuth2 sin verificar en producción**, y `app.security.cookie.secure` está en `false`: hay que ponerla en `true` al desplegar.
- **`infra/.env` es local:** cualquier despliegue necesita sus propias variables (`JWT_SECRET`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `USDA_FDC_API_KEY`, `DB_*`). No hay secretos en el repositorio.
- **Deuda técnica anotada en el roadmap:** Vault/CI para secretos, auditoría de llamadas externas, y límite de cuota propio por usuario en el rate limiting (hoy es por usuario o IP, no por usuario con coste).

## Cómo trabajar aquí

- Comandos: backend `.\mvnw.cmd -o verify` en `backend/pantry-api`; infra `docker compose up -d` en `infra/`.
- Documentación: `README.md` (arquitectura y decisiones), `docs/ROADMAP.md` (plan por hitos), `docs/VERSIONS.md` (versiones fijadas, se consulta **antes** de proponer cualquier dependencia), `docs/FEATURE_GUIDE.md` (qué hace cada funcionalidad y dónde vive).
- Reglas de trabajo y de idioma: `AGENTS.md`.
- Al cerrar cada fase: tests verdes + `README.md` y `docs/ROADMAP.md` actualizados + commit con mensaje convencional. Nada de push sin pedirlo.