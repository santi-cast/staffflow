# Uso de IA en el desarrollo de StaffFlow

Este documento explica de forma transparente cómo se utilizó asistencia de IA
durante el desarrollo de StaffFlow (TFG, ciclo formativo DAM, curso 2025–2026).

---

## Herramientas utilizadas

- **Modelo principal**: Claude Sonnet 4.6 (Anthropic), accedido vía OpenCode CLI.
- **Workflow**: SDD (Spec-Driven Development), un flujo propio basado en agentes
  especializados — `sdd-explore`, `sdd-propose`, `sdd-spec`, `sdd-design`,
  `sdd-tasks`, `sdd-apply`, `sdd-verify`, `sdd-archive` — orquestados por un
  agente coordinador. Cada fase produce un artefacto trazable
  (ver `openspec/changes/archive/`).
- **Memoria persistente**: Engram — base SQLite local que conserva decisiones,
  bugs corregidos y convenciones entre sesiones, evitando que la IA pierda
  contexto al reiniciar.
- **Asistencia puntual**: IntelliJ IDEA con AI Assistant para navegación de
  código, y Android Studio para el módulo Android.

---

## División de responsabilidades

### Lo que decidió **Santiago Castillo** (alumno, Product Owner)

- **Modelo de dominio**: roles (`ADMIN`, `ENCARGADO`, `EMPLEADO`), reglas de
  negocio (cómo se cuenta `diasTrabajados`, qué endpoints `/me` cubre cada
  rol, semántica de fichajes y pausas).
- **Arquitectura**: separación en capas, contratos REST, patrón
  Repository + Service, modelo de excepciones de dominio.
- **Aceptación de deuda residual**: qué riesgos se asumen para el TFG
  (p. ej. secreto JWT en historial git tras rotación) y cuáles requieren
  resolución obligatoria.
- **Validación de cada bloque**: ningún cambio se aplicó sin aprobación
  explícita. La regla de oro del proyecto, registrada desde las primeras
  sesiones, es *"discutir el approach primero, esperar confirmación, luego
  actuar"*.
- **Revisión del código y los commits**: cada uno de los commits del repo
  está firmado por `santicast@gmail.com`. No existe ningún commit con
  atribución a un agente IA — convención deliberada.

### Lo que ejecutó la IA bajo supervisión

- **Búsqueda en código** y exploración de la base existente.
- **Generación de tests** siguiendo TDD estricto (RED → GREEN documentado
  por bloque en los `apply-progress` archivados).
- **Refactors mecánicos** (p. ej. migración de 49 `IllegalStateException`
  a `NotFoundException` / `ConflictException` en el SDD `backend-hardening-high-issues`).
- **Redacción de documentación técnica** (proposals, specs, design docs,
  verify reports, archive reports).
- **Detección estática de inconsistencias** (auditoría de `@PreAuthorize`,
  análisis de `FetchType` en relaciones JPA).

---

## Ejemplos concretos de la trazabilidad

- **Decisión de roles `/me`** (engram #259, archive-report `backend-hardening-high-issues`):
  la IA detectó la inconsistencia entre `@PreAuthorize` y URL-rules.
  El alumno decidió que `ENCARGADO` debe acceder a todos los endpoints `/me`
  porque "un encargado *es* un empleado". La IA documentó la decisión y
  aplicó el cambio uniforme en 7 controllers.
- **Bug en `SaldoServiceTest.mezclaDeTipos`** (commit `7f0bca4`): la IA
  detectó la discrepancia entre test y código de producción. El alumno
  decidió cuál era la semántica correcta de `diasTrabajados` (la del código)
  basándose en el modelo de negocio. La IA ajustó el test.
- **ArchUnit anti-`IllegalStateException`** (`ServiceLayerArchitectureTest`):
  el alumno aceptó la sugerencia de añadir un guardarraíl arquitectónico,
  eligió la estrategia (whitelist por clase en vez de match por mensaje),
  validó la regla con un sanity-check inverso.

---

## Garantías de calidad

- **Tests automáticos**: 45/45 verde al cierre de la deuda backend
  (suite safe + tests específicos del SDD + ArchUnit).
- **Code review humano**: cada commit revisado por el alumno antes de
  cerrarse. El proyecto tiene un hook local `gga` (Gentleman Guardian
  Angel) configurado para revisión automática pre-commit.
- **Convenciones explícitas**: `AGENTS.md` (no versionado, config local)
  define las reglas de code review aplicadas tanto por la IA como por
  el revisor humano.
- **Trazabilidad completa**: el flujo SDD garantiza que para cada cambio
  importante existe propuesta, diseño, especificación, tareas, apply
  log, verificación y archivo. Ver `openspec/changes/archive/` para
  el ejemplo completo del cierre de los 5 issues HIGH del backend.

---

## Postura del autor

La IA se utilizó como **herramienta de productividad supervisada**, no como
generadora autónoma. El alumno entiende cada línea del código que firma,
defiende cada decisión técnica con criterio propio, y asume la
responsabilidad íntegra del producto entregado.

Esta transparencia es deliberada: en 2026 el uso responsable de IA en
desarrollo de software es parte de la profesión, y ocultarlo o maquillarlo
sería menos honesto que documentarlo.

---

**Autor**: Santiago Castillo  
**Última actualización**: 2026-05-09
