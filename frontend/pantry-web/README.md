# PantryBase — Frontend (pantry-web)

Aplicación **Angular 22 + Angular Material (M3)** de PantryBase, el recetario inteligente basado
en la despensa (ver [README](../../README.md) del repositorio). Este proyecto es el scaffold del
hito **H0** del [ROADMAP](../../docs/ROADMAP.md): layout shell con toolbar y navegación lateral,
rutas base con lazy loading y páginas placeholder por área; todavía no hay lógica de negocio.

## Requisitos

- **Node.js 24 LTS** (Angular 22 exige `^22.22.3 || ^24.15 || ^26`; el proyecto fija ≥ 24.20 en
  `docs/VERSIONS.md`). Hay un `.nvmrc` con la serie `24`.
- npm 11+ (bundled con Node 24).

## Comandos

| Comando         | Descripción                                                     |
| --------------- | --------------------------------------------------------------- |
| `npm install`   | Instala dependencias                                            |
| `npm start`     | Dev server en `http://localhost:4200` con proxy `/api` → 8080   |
| `npm run build` | Build de producción                                             |
| `npm test`      | Tests unitarios (Vitest, runner por defecto del CLI 22)         |

## Estructura

```
src/app/
├── core/          # shell (mat-toolbar + mat-sidenav) y modelo de navegación
│   └── layout/    # Shell: host de las rutas hijas
└── pages/         # páginas placeholder por área (pantry, recipes, cooking, social, profile, 404)
```

Las áreas (`pantry`, `recipes`, `cooking`, `social`, `profile`) se desarrollarán en los hitos
H1–H7 del roadmap; cada una vive en su carpeta bajo `pages/`.

## Notas

- **Tema:** Material 3 con paleta `azure-blue` (definido en `src/styles.scss` vía `mat.theme()`).
- **Proxy de desarrollo:** `proxy.conf.json` reenvía `/api` → `http://localhost:8080`
  (`secure: false`, `changeOrigin: true`); configurado en `angular.json` (`serve.options`).
- El contrato OpenAPI del backend (springdoc, `/v3/api-docs`) se consumirá en fases posteriores
  mediante un cliente generado; no hay tipos escritos a mano.