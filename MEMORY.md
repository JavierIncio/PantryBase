# MEMORY.md — Estado del proyecto PantryBase

> **Este fichero es el punto de partida de cada sesión.**
> Al empezar a trabajar, léelo. Al terminar una sesión, actualízalo con el estado real.
> Máximo 100 líneas. Última actualización: **5 de octubre de 2026** (H3-1, H3-2 y H3-3 cerrados, y las medidas del ingrediente ya dentro del detalle).

## Dónde estamos

**Hito en curso: H3 (inventario).** H0, H1 y H2 están cerrados y verificados. **H3-1, H3-2 y H3-3 están implementados, verificados y commiteados**, y el backend que necesita el alta rápida también.

Estado verificado: **backend 233 tests verdes** (215 al cerrar H3-1 + 15 de H3-2 + 3 de medidas), gate de JaCoCo superado con `.\mvnw.cmd -o verify`. **Frontend 116 tests verdes** con `npm test` y `npm run build` sin avisos.
Rama `main`: commits locales sin publicar, nunca hay push sin pedirlo.

## Qué está construido

- **H0 Fundación:** Maven + Spring Boot 4.1.1 / Java 21, Angular 22 con Material, Vitest, Docker Compose, Jenkins, Prometheus + Grafana, Mailpit.
- **H1 Autenticación:** registro, login, access token (15 min) + refresh token en cookie `httpOnly` (7 d, hasheado en BD), cambio de contraseña, recuperación por correo con token de 15 min, login con Google (OAuth2), roles, rate limiting por cubo de fichas en Redis.
- **H1 Usuario:** perfil, preferencias de filtrado (`filterMode`, `coverageThreshold`, `diet`) y lista de exclusión de alérgenos.
- **H2-A Unidades:** 12 unidades US customary, tabla `measure_conversions`, densidades por clase y `UnitConversionService` con la escalera de precedencia medida propia > otra medida > clase del ingrediente > clase pasada.
- **H2-B Catálogo:** integración USDA FDC (búsqueda, detalle y lote), materialización idempotente, medidas por ingrediente, densidad por clase curada, administración de clases con procedencia, caché/frescura (`cache-ttl` 24 h, `max-stale` 30 d), Redis para búsqueda y manejo de 429.
- **H3-1 Despensa:** paquete `pantry` nuevo. `GET/POST/PUT/DELETE /api/pantry`, `V12__pantry_items.sql` (`pantry_items` + `consumption_entries`), cantidad normalizada a `GRAM`/`UNIT`, una fila por `(user_id, ingredient_id)`, edición sin campo de cantidad, aislamiento por propietario con `404`, y `UnitConverter.toStorageUnit(...)` como nueva operación del puerto.
- **H3-2 Ajuste y auditoría:** `POST /api/pantry/{id}/adjustments` (cantidad con signo, en cualquier unidad, convertida a la de la línea) y `GET /api/pantry/{id}/entries`. El alta escribe también su asiento. `InsufficientStockException` → `409` solo si el resultado es negativo; ajuste de `0` → `400`. `OptimisticLockingFailureException` → `409`.
- **H3-3 Frontend «Mi despensa»:** `core/pantry/pantry.service.ts` y `pages/pantry/pantry-page.*`, con los cinco endpoints tipados, lista agrupada por categoría, edición inline, ajuste con signo, borrado con confirmación e historial. 28 tests con Vitest. **El frontend lo hizo el subagente `frontend`; `pantry` no lo toca.**
- **Medidas en el detalle de ingrediente:** `GET /api/catalog/ingredients/{fdcId}` añade `measures` (`{unitCode, gramPerUnit}`, ordenada por código, `[]` si el USDA no pesó ninguna). Sin endpoint nuevo, porque el detalle es la llamada que las captura.

## Decisiones ya tomadas de H3 (no volver a abrir sin motivo)

- La cantidad se guarda en **`GRAM` siempre que se pueda** y en `UNIT` para lo que se cuenta. Si un volumen no tiene densidad conocida se guarda en `ML` en vez de fallar: el hueco está en nuestros datos, no en la petición.
- **Una fila por `(user_id, ingredient_id)`**; añadir algo que ya está responde `409` y remite al ajuste, nunca fusiona en silencio.
- **Un único endpoint de ajuste con cantidad con signo**, y toda suma o resta deja `ConsumptionEntry`. **El alta también deja asiento**, para que «la suma de los asientos = la cantidad» sea cierto desde la primera fila y no solo después del primer ajuste.
- **Negativo se rechaza con `409`; cero se permite.** Un déficit deja cocinar con ingredientes que no hay; una línea en cero dice la verdad. Por eso `signum() < 0` y no `<= 0`, y `V12` se corrigió a `CHECK (quantity >= 0)`.
- **La versión no la manda el cliente.** `@Version` decide entre dos escritores concurrentes dentro de la transacción; un ajuste es un delta, así que una vista obsoleta del cliente no lo invalida.
- El `PATCH` que se consensuó acabó siendo `PUT`: reemplaza ubicación y nota, y no admite cantidad, para que saltarse la auditoría sea imposible por contrato y no por convención.
- **La búsqueda no lleva id interno.** No materializa, así que no hay id que devolver, y hacerlo exigiría una llamada al proveedor por cada hit. El flujo es buscar → elegir → detalle (materializa) → cantidad.
- **La escala de `gramPerUnit` no es parte del contrato:** lo que manda el USDA y lo que guarda la columna no coinciden en ceros, así que se compara numéricamente y no con `equals`.
- **El cliente no reordena la lista ni filtra unidades por convertibilidad.** La API ya devuelve el orden, y qué par de unidades convierte depende de la densidad del ingrediente, que solo conoce el servidor.

## Próximos pasos

1. **Alta rápida de H3:** solo falta la pantalla (toca el subagente `frontend`): buscar → detalle → selector de unidad → cantidad. El backend ya responde con las medidas.
2. **H5 tiene dos deudas conocidas:** `ConsumptionOrigin` pasa a tener dos miembros (`COOKING_SESSION`) y el tipo del frontend es una unión de uno solo, así que el compilador avisará; y el mapa local de nombres de unidad en `pantry-page.ts` duplica el catálogo semilla, pendiente de sustituir por `GET /api/units`.
3. **H3-4:** métricas de inventario (tamaño y reparto por categoría).
4. **Frontend del catálogo:** el buscador y el conversor siguen sin pantalla.
5. **Verificar el login de Google** contra un cliente real de OAuth2; hasta ahora solo se ha probado el camino con contraseña.

## Cuellos de botella y riesgos

- **Cuota de USDA FDC:** ~1.000 req/h con key propia, **30 req/h con `DEMO_KEY`**. Las ventanas de caché lo hacen utilizable; cualquier endpoint nuevo que llame al proveedor debe pasar por `FdcCachePolicy` o gastará cuota.
- **Cada ingrediente nuevo que el usuario meta en la despensa cuesta una llamada al proveedor** (la de detalle, que es la que materializa), aunque no gaste más después: la copia local dura 24 h y la comparten todos los usuarios. Con `DEMO_KEY` son ~30 alimentos distintos por hora, así que la pantalla de alta debe avisar del 429 y no reintentar a ciegas.
- **Forma del wire del USDA:** ya costó tres correcciones (`298c109`). La regla vigente: los fixtures del adaptador son recortes literales de respuestas reales. No escribir fixtures a partir de los records del adapter.
- **Densidades:** solo hay 4 clases curadas (`FLOUR`, `SUGAR`, `OIL`, `MILK`). La categoría del proveedor **no** sirve como densidad (demostrado en H2-B-4). Ampliar el catálogo solo con valores citables de una porción FDC.
- **Sin densidad, `toStorageUnit` deja la cantidad en `ML`:** es deliberado, pero significa que muchas líneas de despensa quedarán en mililitros mientras falten clases curadas, y H4 tendrá que convertir al comparar. Vigilarlo al llegar a H4.
- **Carrera en el alta de despensa:** el `existsByIngredientId` previo al `insert` no es atómico, así que dos altas simultáneas del mismo ingrediente pueden dar `500` por violación de unicidad en vez de `409`. No se ha arreglado: mapear `DataIntegrityViolationException` a `409` de forma global enmascararía otros errores de integridad.
- **La carrera por bloqueo optimista no tiene test.** Reproducir una lectura y una escritura solapadas de forma determinista necesita un cerrojo dentro de la petición, y un test basado en tiempos sería el primero intermitente del proyecto, así que no se escribió.
- **Sesión de OAuth2 sin verificar en producción**, y `app.security.cookie.secure` está en `false`: hay que ponerla en `true` al desplegar.
- **`infra/.env` es local:** cualquier despliegue necesita sus propias variables (`JWT_SECRET`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `USDA_FDC_API_KEY`, `DB_*`). No hay secretos en el repositorio.
- **Deuda técnica anotada en el roadmap:** Vault/CI para secretos, auditoría de llamadas externas, y límite de cuota propio por usuario en el rate limiting (hoy es por usuario o IP, no por usuario con coste).

## Cómo trabajar aquí

- Comandos: backend `.\mvnw.cmd -o verify` en `backend/pantry-api`; infra `docker compose up -d` en `infra/`.
- Documentación: `README.md` (arquitectura y decisiones), `docs/ROADMAP.md` (plan por hitos), `docs/VERSIONS.md` (versiones fijadas, se consulta **antes** de proponer cualquier dependencia), `docs/FEATURE_GUIDE.md` (qué hace cada funcionalidad y dónde vive).
- Reglas de trabajo y de idioma: `AGENTS.md`.
- Al cerrar cada fase: tests verdes + `README.md` y `docs/ROADMAP.md` actualizados + commit con mensaje convencional. Nada de push sin pedirlo.