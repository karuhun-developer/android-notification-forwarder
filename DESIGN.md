# Design direction: Relay board

This file is the design source for the Android app. The UI is a relay utility for developers and integrators: notifications travel from a source app, through a rule, to an HTTP endpoint. The interface should read like a technical logbook, not a dashboard template.

## Design read

Reading this as: Android relay utility for developers and integrators, with a technical logbook language, dial ENERGY 2 / RHYTHM 2 / MOTION 1.

- **ENERGY 2:** confident, but quiet. No gradients, glows, or decorative color.
- **RHYTHM 2:** the status block, rule rows, and delivery timeline each use a different composition.
- **MOTION 1:** state transitions only. Expand, collapse, sheet, and progress. No endless loops.

## Identity

The product's own motif is a route: a source node, a line, and an endpoint. It appears as a small drawn mark in empty states and as the structural order of every rule row (source apps, method, endpoint). The motif carries meaning. It is not repeated as decoration.

## Palette

The palette is paper and ink with one signal color for a live relay. Semantic colors are reserved for state.

| Role | Light | Dark |
| --- | --- | --- |
| Background | `#F4F1EA` paper | `#121316` graphite |
| Surface | `#FBFAF6` | `#191B1F` |
| Raised surface | `#EFEBE1` | `#21242A` |
| Primary text | `#17181A` ink | `#EDEAE2` bone |
| Secondary text | `#5E5B54` | `#A7A399` |
| Hairline | `#D9D4C8` | `#2B2E34` |
| Accent | `#1E6B4E` | `#6FD3A3` |
| Error | `#B3261E` | `#FF8A80` |
| Warning | `#8A5A00` | `#E0B15C` |

Reasons:

- Paper and graphite are the product's ground. They are neutral, so status color can carry meaning.
- Ink and bone carry hierarchy. Primary actions are solid ink, not the accent.
- The accent is used only for a live relay and a delivered delivery. It is not a brand wash.
- Error and warning appear only when something is actually wrong.

## Typography

- UI: the platform sans. Reason: this is a system utility, so it should follow Android text rendering and the user's font settings.
- Technical values: the platform monospace. Reason: endpoints, methods, headers, package names, and delivery IDs are scanned character by character. Monospace helps.

No uppercase tracking as a style. HTTP methods stay uppercase because that is their real form.

## Shape and elevation

- Controls: 6dp radius.
- Surfaces: 10dp radius.
- Tags: 4dp radius.
- No shadows on content. Dialogs and sheets may lift because they are overlays.
- No gradients, no glow, no glass.

## Layout

- Main screen: relay status first, test notification second, rules third. The rules are the primary content, not a card placed after a large test panel.
- Rule rows are rows separated by hairlines, not a stack of identical cards. Each row reads: name, method, endpoint, source apps, history.
- Add rule: one form with labeled sections and hairline separators. No five identical cards.
- History: a delivery timeline with real state, attempt count, HTTP status, and a retry action for failed deliveries.

## Copy

- Specific, plain, and honest. No hype words, no decorative emoji, no em dashes.
- Empty states name the cause and the next action.
- Loading states say what is loading.
- Error states say what failed and offer a retry.
