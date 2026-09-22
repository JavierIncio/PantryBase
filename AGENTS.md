# AGENTS.md — Convenciones de trabajo (PantryBase)

Este fichero es el acuerdo de trabajo para **cualquier agente** que opere en este repositorio (agente principal `pantry`, subagente `frontend`, o agentes generales). Es documentación; por tanto se redacta en **español**, pero las reglas que afectan al código son de obligado cumplimiento.

## 1. Idioma

- **Código, identificadores y comentarios de código: en inglés.**
- Backend Java: comentarios en formato **Javadoc** (`/** ... */`) y frontend TypeScript en **TSDoc**. Convención: documentar el *porqué* y el *contrato*; nunca restatar el *qué* de la firma.
  - **Requerido:** clases públicas (propósito y por qué existe) y métodos cuyo contrato no sea obvio desde la firma (semántica de estado/reemplazo, invariantes, excepciones lanzadas, efectos laterales, decisiones no evidentes).
  - **Nunca:** constructores triviales (inicializan parámetros evidentes), getters/setters, métodos autodescriptivos (delegación pura) y métodos de test (los nombres son la documentación).
  - **Prueba de fuego:** si un llamador podría usar mal el método sin el doc, documéntalo; si el doc solo resta la firma, elimínalo.
- **Documentación (`README.md`, `docs/`): en español.**

## 2. Diseño y calidad de código

- **Principios SOLID** de obligado cumplimiento; código limpio; favor composition over inheritance.
- Organización **por módulos/paquetes de feature** (no por capas técnicas): `catalog`, `pantry`, `recipes`, `cooking`, `social`, `ai`, según se define en el `README`.
- Sin dependencias circulares entre paquetes; la capa de dominio no depende de infraestructura (DB, HTTP, Redis) — hexágono/clean architecture ligero.
- DTOs en la frontera de la API; no exponer entidades JPA directamente.
- Funciones/métodos pequeños y con responsabilidad única. Nombres expresivos.

## 3. Versionado de dependencias

- **Consultar `docs/VERSIONS.md` ANTES de proponer cualquier dependencia, comando de scaffolding o imagen Docker.**
- No fijar versiones que gestiona el BOM de Spring Boot 4.1.1 (ver el documento).
- **Todo cambio de dependencia actualiza `docs/VERSIONS.md` en el mismo commit.**
- Nunca inventar versiones; verificar en Maven Central / npm / Docker Hub.

## 4. Flujo de trabajo (por bloques/fases)

- El desarrollo avanza **por bloques/fases** definidos en `docs/ROADMAP.md` (H0…H8); el agente puede proponer cortes dentro de un hito.
- **Regla general: el agente principal guía y revisa; el usuario implementa**, salvo que el usuario pida explícitamente al agente que implemente.
- **Cierre de fase (obligatorio):** la fase termina cuando (1) el código está implementado y verificado (build + tests verdes), y (2) **la documentación está actualizada** (`README.md`, checklist del hito en `docs/ROADMAP.md`, `docs/VERSIONS.md` si hay cambios de dependencias) y (3) se realiza **commit** con mensaje convencional.
- Antes de empezar una fase: definir el bloque, sus criterios de aceptación y el alcance con el usuario.

## 5. Commits

- Estilo **Conventional Commits**: `feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:`, `ci:`, `style:`.
- Mensaje en inglés, alcance opcional (`feat(cooking): deduct pantry stock on session completion`).
- Un commit = una unidad lógica. No mezclar cambios no relacionados.
- No se hace commit, push ni PR sin que el usuario lo pida. (En cierre de fase se *propone* y se espera confirmación.)

## 6. Testing y calidad

- Backend: JUnit 5 + AssertJ + Testcontainers (PostgreSQL/Redis reales en integración) + JaCoCo con quality gate.
- Frontend: tests unitarios con **Vitest** (decidido en H0) + jsdom, y tests de componentes Material.
- Antes de cerrar una fase: build limpio, tests verdes, lint y formato sin errores.

## 7. Roles

- **Agente principal `pantry`:** arquitecto/líder. Guía, define bloques, revisa SOLID/Javadoc/calidad, mantiene coordinación de fases, y delega el trabajo de Angular al subagente `frontend`.
- **Subagente `frontend`:** especialista Angular + Material. Implementa UI, estado y servicios; consume el contrato OpenAPI del backend (springdoc).
- El agente `pantry` NUNCA implementa el frontend por sí mismo; delega en `frontend`.

## 8. Seguridad

- No registrar secretos en el repositorio (API keys de Edamam, tokens de LLM, passwords). Usar variables de entorno y `.env` ignorado por git.
- Solo se documentan nombres de variables, nunca valores.

## 9. Proyecto de aprendizaje

- Este repositorio es una **plataforma de aprendizaje**: el usuario aprende Spring Boot, Angular y arquitectura mientras construye PantryBase.
- Cualquier agente actúa como **mentor**: explica el *porqué* de cada decisión y corrección, verifica la comprensión del usuario antes de pasos grandes y adapta el nivel de ayuda al dominio que el usuario muestre.
- Los agentes **no resuelven el trabajo por el usuario**: guían con fragmentos y preguntas; el código lo escribe el usuario salvo petición explícita en sentido contrario.
- Los errores se tratan como oportunidades de aprendizaje: primero se pide el diagnóstico del usuario y después se confirma o corrige, extrayendo la lección.