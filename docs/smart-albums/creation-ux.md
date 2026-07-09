# Creación de álbumes inteligentes — UX (en discusión)

Restricción de Marc: **la creación NO sale del buscador**. Un álbum inteligente es una
intención de primera clase, no el subproducto de una búsqueda transitoria. Este documento
recoge las opciones para *cómo* se crea; queda **abierto** para decidir juntos.

## Por qué no desde el buscador

El buscador produce una consulta efímera y exploratoria. Convertir "lo último que busqué" en
un álbum permanente mezcla dos mentalidades (explorar vs. curar) y hace frágil el resultado
(cambio un chip para ver otra cosa y sin querer redefino el álbum). Además el buscador está
pensado para *encontrar una foto*, no para *definir un conjunto que evoluciona*.

## Opciones

### A) Editor de reglas dedicado ("Nuevo álbum inteligente")
Entrada propia en la pestaña **Álbumes** (junto a "Nuevo álbum"): "Nuevo álbum inteligente".
Pantalla con chips de condición (Personas · Carpetas · Fechas · Escenas · Objetos · Tags),
toggle AND/OR, y **preview en vivo** ("N fotos coinciden" + rejilla). Es el patrón "smart
playlist" (Apple Photos / Lightroom smart collection).

- ✅ Potente, todas las dimensiones, un solo sitio.
- ⚠️ Abstracto; el usuario parte de una hoja en blanco.

### B) Creación contextual (seed desde una entidad)
Empezar desde algo concreto que el usuario ya navega y generalizarlo:
- Ficha de **Persona** → "Crear álbum de esta persona" (pre-rellena `person`).
- **Carpeta** (personal o compartida) → "Álbum inteligente de esta carpeta" (pre-rellena `folder`).
- Selección múltiple en la rejilla de **Personas** → "Álbum con estas personas".
- (Futuro) zona del **mapa** → "Álbum de esta zona".

- ✅ Discoverable, nada abstracto, arranca desde el contexto real.
- ⚠️ Por sí solo no cubre combinaciones ricas.

### C) Wizard guiado por dimensión primaria
"¿De qué quieres el álbum?" → Personas / Carpetas / Momentos → refinar paso a paso.

- ✅ Cero fricción para el caso simple.
- ⚠️ Se queda corto para el usuario avanzado; más pantallas.

## Decisión (Marc, 2026-07-09)

**Entrada principal = editor de reglas dedicado**, al que se llega desde **"Nuevo álbum
inteligente"** en la pestaña Álbumes. Las entradas contextuales (persona, carpeta) quedan
como **atajos posteriores** que abren ESTE MISMO editor con una condición pre-rellenada — no
son flujos aparte. Una sola superficie que mantener y donde el usuario ve el preview antes de
guardar. Creación intencional y de primera clase (no buscador).

Flujo tipo:
1. Entro por "Nuevo álbum inteligente" (editor vacío).
2. Añado/quito condiciones; veo "N coinciden" + rejilla en vivo.
3. Nombre + portada (o portada automática = primera por `CapturedAt`).
4. Guardar → `POST /api/albums` con `kind=smart` + `smartRule`.

## La pantalla "Nuevo álbum inteligente" (spec MVP)

```
┌─ Nuevo álbum inteligente ──────────────────┐
│  Nombre  [ Verano con la abuela        ]   │
│                                            │
│  Coincidir con  ( Todas ▸ | Cualquiera )   │  ← combinación entre condiciones
│                                            │
│  Condiciones                               │
│   ┌──────────────────────────────────┐     │
│   │ 👤 Personas: Abuela, Nieto  (cualquiera)│  editable / ✕
│   │ 📁 Carpeta: Viajes  (+ subcarpetas)│     │
│   │ 📅 Fechas: desde 2024            │     │
│   └──────────────────────────────────┘     │
│   [ + Añadir condición ]                    │
│                                            │
│  ── 128 fotos coinciden ──                 │  ← preview en vivo (dry-run)
│  [▦▦▦▦▦▦▦▦]  rejilla                        │
│                                            │
│              [ Crear álbum ]               │
└────────────────────────────────────────────┘
```

- **[+ Añadir condición]** abre un bottom sheet con las dimensiones: Personas · Carpetas ·
  Fechas · Escenas · Objetos · Tags. Cada una lleva a su selector propio (rejilla de
  personas, folder picker personal+compartido con toggle "incluir subcarpetas", date range,
  pickers de labels/tags).
- Cada condición añadida es una fila/chip **editable** y **eliminable**.
- **Preview en vivo** contra el backend (dry-run) cada vez que cambia una condición.

### Dos decisiones de semántica (pendientes)

1. **Combinación ENTRE condiciones.** Recomiendo un toggle plano **"Todas / Cualquiera"**
   (AND/OR de primer nivel) en el MVP, en vez de un árbol anidado AND/OR/NOT. El motor
   soporta el árbol completo (`rule-schema.md`), pero la UI del MVP solo expone la lista
   plana + ese toggle. Cubre el 95% de casos reales sin la complejidad de agrupar.
2. **Semántica DENTRO de "Personas".** Ojo: el buscador hoy usa **intersección** (la foto debe
   contener a TODAS las personas seleccionadas). Para un álbum "de mis hijos" el usuario casi
   siempre quiere **cualquiera** (fotos con uno U otro), no solo donde salgan juntos.
   → Recomiendo que la condición `person` **por defecto sea `match: "any"`** (cualquiera), con
   opción de cambiar a "todas juntas". Esto DIVERGE del default de Search; por eso el
   `AssetQueryBuilder` recibe `match` explícito por dimensión y no asume intersección.

### Preview: endpoint dry-run

`POST /api/albums/preview` con `{ smartRule }` → resuelve la regla con el mismo
`AssetQueryBuilder` + gate de visibilidad y devuelve `{ count, sample[] }` sin persistir nada.
Reutiliza exactamente el motor de resolución del álbum, así que preview == contenido real.

## Preguntas abiertas para decidir

1. ¿AND/OR visible al usuario, o de momento **solo AND** (todas las condiciones se cumplen)
   como hace el buscador hoy, y dejamos OR/NOT para "modo avanzado"? *(Recomiendo arrancar
   solo-AND en UI aunque el motor soporte el árbol completo.)*
2. ¿El preview en vivo consulta el backend en cada cambio de chip (endpoint "dry-run" que
   resuelve la regla sin guardarla) o estimamos en cliente? *(Recomiendo dry-run:
   `POST /api/albums/preview` que reutiliza el `AssetQueryBuilder`.)*
3. ¿Distinguimos visualmente un smart album de uno manual en la rejilla de álbumes (badge/icono)?
4. ¿Overrides (pin/exclude) desde el propio álbum ya en el MVP o en fase 4?
5. Puntos de entrada contextuales del MVP: ¿solo Persona y Carpeta, o también selección
   múltiple en la rejilla de personas?

> Nada aquí toca el buscador. Si más adelante quisiéramos un atajo "buscar → guardar", sería
> opt-in y explícito, nunca el camino principal.
