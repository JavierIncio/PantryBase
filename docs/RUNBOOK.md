# Runbook de operación — PantryBase

Guía operativa para arrancar, supervisar y mantener PantryBase. Complementa al [`README.md`](../README.md) (visión y arquitectura), a [`FEATURE_GUIDE.md`](FEATURE_GUIDE.md) (qué hace cada funcionalidad) y a [`ROADMAP.md`](ROADMAP.md) (qué se decidió hacer y qué queda como deuda).

> **Alcance honesto:** esto cubre el stack en local/Docker. El servidor Jenkins **no está desplegado**, los **backups no están automatizados** y el pipeline de promoción **no existe**: son deuda anotada en `ROADMAP.md` («Deuda técnica conocida»). No hay entorno de producción montado todavía.

---

## 1. Piezas y puertos

Servicio definido en `infra/docker-compose.yml` (variables en `infra/.env`, ver `infra/.env.example`):

| Servicio     | Puerto (host) | Qué es                                                        |
| ------------ | ------------- | -------------------------------------------------------------- |
| pantry-api   | 8080          | API Spring Boot (Swagger en `/swagger-ui.html`)                |
| postgres     | 5432          | PostgreSQL 18 (volumen `postgres_data`)                        |
| redis        | 6379          | Redis 8 (caché de búsquedas + rate limiting)                   |
| prometheus   | 9090          | Scraping y reglas de alerta                                    |
| alertmanager | 9093          | Enrutado de alertas (correo → Mailpit en local)                |
| grafana      | 3000          | Dashboards provisionados (usuario `admin`)                     |
| mailpit      | 1025 / 8025   | SMTP dev / UI de correos (ver correos de reset y alertas)      |

El frontend Angular (`frontend/pantry-web`) corre fuera de compose: `npm start` en el 4200 con proxy `/api` → `8080`.

---

## 2. Arranque y parada

```bash
# Stack completo (infra + API). --build obligatorio si cambió el código Java
docker compose -f infra/docker-compose.yml up -d --build

# Solo infraestructura, con la API en local (desarrollo)
docker compose -f infra/docker-compose.yml up -d postgres redis prometheus alertmanager grafana mailpit
cd backend/pantry-api && ./mvnw.cmd spring-boot:run     # requiere las env de application.yml

# Frontend
cd frontend/pantry-web && npm install && npm start

# Parada (los volúmenes de datos se conservan)
docker compose -f infra/docker-compose.yml down
```

**Verificación de arranque:**

1. `GET http://localhost:8080/actuator/health` → `{"status":"UP"}` (también es el healthcheck del contenedor).
2. Prometheus `http://localhost:9090/targets` → job `pantry-api` en **UP** (hace scrape a `host.docker.internal:8080`).
3. Grafana `http://localhost:3000/api/health` → `ok`; los dashboards **PantryBase / Health** y **PantryBase / Operations** aparecen en «Browse».
4. Mailpit `http://localhost:8025` → UI accesible (los correos salientes de la app aterrizan aquí).

---

## 3. Esquema de base de datos (Flyway)

- Las migraciones viven en `backend/pantry-api/src/main/resources/db/migration` (`V1`…`V23`) y **se aplican automáticamente al arrancar** la API (`spring.flyway.enabled: true`).
- `spring.jpa.hibernate.ddl-auto: validate`: si el esquema no cuadra con las entidades, **la API no arranca** — es a propósito.
- Comprobar el historial:

  ```bash
  docker exec pantry-postgres psql -U pantry -d pantrybase -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
  ```

- **Nunca** editar una migración ya aplicada: se crea `V{n+1}__*.sql`. Versiones fijadas del esquema y del stack en [`VERSIONS.md`](VERSIONS.md).

---

## 4. Alertas

Reglas en `infra/prometheus/rules.yml` (5), validadas con `promtool check rules`:

| Alerta                | Significado                                                     |
| --------------------- | --------------------------------------------------------------- |
| `ApiDown`             | La API no responde al scrape durante 2 min (crítico)            |
| `ApiHighErrorRate`    | >10 % de respuestas 5xx durante 5 min                           |
| `FdcQuotaExhausted`   | Cuota de USDA FDC agotada en 15 min                             |
| `FdcServingStale`     | El catálogo sirve datos obsoletos (más allá de `max-stale`)     |
| `CookingSessionsStuck`| Sesiones `IN_PROGRESS` abiertas más de 1 h                      |

- Ver estado: Prometheus `http://localhost:9090/alerts` y Alertmanager `http://localhost:9093`.
- **Entrega:** `infra/alertmanager/alertmanager.yml` manda todo por correo a Mailpit (`smtp_require_tls: false`); el aviso se ve en `http://localhost:8025`. En producción hay que cambiar el receptor por un proveedor SMTP real (ver §6).
- **Prueba de extremo a extremo:** para la API → `ApiDown` pasa a `firing` en ~2 min → correo «[FIRING:1] ApiDown» en Mailpit → al levantarla, las alertas se resuelven.
- Silenciar temporalmente: `amtool silences add --alertmanager.url http://localhost:9093 --duration 1h ApiDown`.
- Validar cambios de config: `docker run --rm --entrypoint amtool -v .../alertmanager.yml:/config.yml prom/alertmanager:v0.34.1 check-config /config.yml`.

---

## 5. Métricas y dashboards

- Endpoint: `GET /actuator/prometheus` (expuesto junto a `health` e `info`).
- Series de aplicación (prefijo `pantry_`): `pantry_cooking_sessions_completed_total`, `pantry_cooking_sessions_abandoned_total`, `pantry_cooking_sessions_duration_seconds_*`, `pantry_cooking_sessions_stuck`, `pantry_catalog_fdc_stale_served_total`, `pantry_catalog_fdc_quota_exhausted_total`; más las estándar de HTTP, JVM, Hikari y Micrometer.
- Los timers son **summaries** (no hay histograma): la latencia solo se puede mostrar como **media**, no p90/p95. No existen series de cuota TheMealDB ni de cobertura del recomendador; no se inventan paneles que no puedan alimentarse.
- **No tocar `uid` en los paneles:** los dashboards referencian el datasource con la variable `${datasource}`. Fijar un `uid` en el YAML del datasource rompe el arranque de Grafana 13 («data source not found»).

---

## 6. Checklist «antes de desplegar»

Nada de esto está aplicado todavía; es la lista mínima para pasar de local a producción:

1. **Cookies:** `COOKIE_SECURE=true` (marca `Secure` de la cookie de refresco; `false` solo sirve en local sin TLS). La property es `app.security.cookie.secure`.
2. **Secretos:** `JWT_SECRET` fuerte y distinto del de dev; `USDA_FDC_API_KEY` propia (sin key, la API cae a `DEMO_KEY` con 30 req/h y emite warning); `GRAFANA_PASSWORD`. Todo en `.env` fuera de git; considerar Vault cuando haya CI (deuda en ROADMAP).
3. **OAuth2 con Google (sin verificar aún):** crear el cliente real en Google Cloud, ajustar `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`, `app.security.oauth2.redirect-uri` y `FRONTEND_BASE_URL`, y **probar un login completo**: el camino no se ha ejecutado contra un cliente real de Google.
4. **Correo real:** en `infra/alertmanager/alertmanager.yml` sustituir el receiver `local-mailpit` por el SMTP de producción (con TLS) y revisar `MAIL_HOST`/`MAIL_FROM` de la app.
5. **TLS y proxy inverso:** servir la API tras HTTPS. **Atención al rate limit por IP:** la clave usa `remoteAddr`, así que sin `X-Forwarded-For` de confianza (`server.forward-headers-strategy` + proxy que sobrescriba la cabecera) **todas las peticiones anónimas compartirán cubo**, incluidas las de todos los usuarios tras el proxy. Esto todavía no está configurado.
6. **CORS/cookies del frontend:** el dominio de producción debe coincidir con `FRONTEND_BASE_URL`; la cookie de refresh va en el path `/api/auth` con `SameSite=Lax`.
7. **Backups:** ver §7 — antes de dar servicio real, tener el dump probado.

---

## 7. Respaldo y restauración de PostgreSQL (manual)

La automatización (cron/retención) **no está implementada**; mientras tanto, procedimiento manual:

```bash
# Respaldo (dump comprimido)
docker exec pantry-postgres pg_dump -U pantry -d pantrybase -Fc -f /tmp/pantry.dump
docker cp pantry-postgres:/tmp/pantry.dump ./backups/pantry-$(date +%Y%m%d).dump

# Inspección sin restaurar
pg_restore --list ./backups/pantry-20261019.dump

# Restauración en una BD limpia (PARAR la API antes)
docker exec -i pantry-postgres createdb -U pantry restore_check
docker exec -i pantry-postgres pg_restore -U pantry -d restore_check --clean --if-exists < /tmp/pantry.dump
```

- **Un respaldo no probado no es un respaldo:** restaurar en una BD de prueba y verificar (`flyway_schema_history` completo, recuentos de `pantry_items`/`recipes`/`cooking_sessions`) antes de depender de él.
- Los volúmenes `postgres_data` y `grafana_data` son la otra cara: `docker compose down` **no** los borra; `down -v` sí (nunca en un entorno con datos reales).

---

## 8. Problemas conocidos

| Síntoma                                            | Causa y solución                                                                                             |
| -------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| La API no arranca: falta `JWT_SECRET`/`GOOGLE_*`   | Son obligatorias sin default; definirlas en el entorno (fallo deliberado al arrancar).                        |
| Warning `DEMO_KEY` / errores 429 del catálogo      | Falta `USDA_FDC_API_KEY` o cuota agotada; se sirve la copia local hasta `max-stale` y luego 502.              |
| Grafana: `Datasource provisioning error`           | Un panel o datasource con `uid` fijo; usar `${datasource}`.                                                   |
| Prometheus sin targets                              | La API no está en 8080 o no corre en el host; el job apunta a `host.docker.internal:8080`.                    |
| 409 al completar cocina                            | Falta stock: la primera llamada devuelve el plan; aceptar descuenta lo disponible. Comportamiento previsto.   |
| Reset de contraseña / alertas no llegan            | Mirar Mailpit (`:8025`); en local no hay MTA real.                                                            |
| Correos de test de métricas: `/actuator/prometheus` 404 | **No es un bug en runtime:** los starters de test de Boot 4 desactivan la exportación de métricas; en test se lee el `MeterRegistry` inyectado. |

---

## 9. Verificación periódica recomendada

1. `docker compose ps` → todo `healthy`.
2. Prometheus: targets UP y pestaña Alerts sin `firing`.
3. Grafana: dashboards sin «No data» inesperado (si lo hay, comprobar que la API se scrapea).
4. Espacio en disco de los volúmenes `postgres_data`/`grafana_data`.
5. Tras cualquier cambio de infra: `promtool check rules infra/prometheus/rules.yml` y `amtool check-config infra/alertmanager/alertmanager.yml`.

---

*Documento de cierre de H9 (19-oct-2026). El trabajo no realizado que describe como pendiente (backups automatizados, Vault/CI, Jenkins, pipeline de promoción, auditoría de llamadas externas, medición de caché) está registrado en `docs/ROADMAP.md`.*
