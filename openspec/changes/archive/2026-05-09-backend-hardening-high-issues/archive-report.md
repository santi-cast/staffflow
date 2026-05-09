# Archive Report — backend-hardening-high-issues

**Change**: `backend-hardening-high-issues`  
**Fecha de archivo**: 2026-05-09  
**Archivado por**: sdd-archive (claude-sonnet-4-6)  
**Artifact store**: hybrid (Engram + openspec/)

---

## Resumen Ejecutivo

El change `backend-hardening-high-issues` implementó los 5 issues HIGH pendientes del
backend de StaffFlow: modelo de dominio de excepciones (`NotFoundException`), autorización
por método (`@EnableMethodSecurity` + auditoría de 53 `@PreAuthorize`), externalización
del secreto JWT a variable de entorno, y declaración explícita de `FetchType.LAZY` en
8 relaciones JPA. Los 8 commits pasan sin regresiones (29/30 tests, la única falla es
pre-existente de baseline). La verificación retornó PASS WITH WARNINGS (0 CRITICALs).
Las especificaciones delta fueron sincronizadas a `openspec/specs/` como fuente de verdad.

---

## Estado Final del Branch

| Campo | Valor |
|-------|-------|
| Branch | `feat/backend-hardening-high-issues` |
| HEAD | `48d84ae` |
| Master HEAD | `9f1b04a` |
| Commits del change | 8 |
| Líneas modificadas (backend/) | +572 / -132 = 704 total |
| Archivos modificados (backend/) | 34 |
| Estado del working tree | Limpio (clean) |
| Branch pusheada | No |
| Branch mergeada | No |
| Tag de backup | `sdd-backup-pre-rebase` → `a638ecb` (mantener hasta merge) |

---

## Commits del Change

| # | Hash | Mensaje |
|---|------|---------|
| 1 | `48c5b9f` | feat(backend): nueva NotFoundException y remap del GlobalExceptionHandler |
| 2 | `648c5fa` | refactor(backend): migra IllegalStateException a NotFoundException y ConflictException |
| 3 | `701c9ae` | feat(backend): externaliza el secreto JWT a variable de entorno y rota el fallback de dev |
| 4 | `55391cf` | refactor(backend): fetch LAZY explicito en relaciones JPA |
| 5 | `ba9bbe6` | refactor(backend): protege rutas de lectura lazy con @Transactional y JOIN FETCH |
| 6 | `d95c677` | feat(backend): activa @EnableMethodSecurity y armoniza @PreAuthorize en endpoints /me |
| 7 | `1e0165d` | chore(backend): cierra deuda colateral detectada en auditoria del Block 5 |
| 8 | `48d84ae` | docs(backend): alinea javadoc y comentarios con el codigo real |

---

## Capacidades Archivadas

| Capacidad | Delta spec | Spec canónica | Issues cerrados |
|-----------|-----------|----------------|-----------------|
| `exception-domain-model` | `specs/exception-domain-model/spec.md` | `openspec/specs/exception-domain-model/spec.md` | ISE-01, ISE-02 |
| `security-authorization` | `specs/security-authorization/spec.md` | `openspec/specs/security-authorization/spec.md` | ISE-03 |
| `jwt-configuration` | `specs/jwt-configuration/spec.md` | `openspec/specs/jwt-configuration/spec.md` | ISE-04 |
| `jpa-fetch-strategy` | `specs/jpa-fetch-strategy/spec.md` | `openspec/specs/jpa-fetch-strategy/spec.md` | ISE-05 |

Las 4 specs canónicas son creaciones nuevas (directorio `openspec/specs/` estaba vacío antes de
este archive). El delta IS la spec completa en cada caso.

---

## Resultado de Verificación

**Veredicto**: PASS WITH WARNINGS

| Métrica | Valor |
|---------|-------|
| TDD checks | 6/6 |
| Spec scenarios | 28/28 (4 capabilities × scenarios) |
| Tests totales ejecutados | 30 |
| Tests que pasan | 29 |
| Fallas pre-existentes (baseline) | 1 (`SaldoServiceTest.recalcularParaProceso_mezclaDeTipos`) |
| Nuevas regresiones | 0 |
| `MethodSecurityConfigTest` | 11/11 |
| `GlobalExceptionHandlerNotFoundTest` | 3/3 |
| CRITICALs | 0 |
| WARNINGs | 1 (W-01 — resuelto post-verify, ver abajo) |
| Smoke matrix Block 5 | 35/35 OK |

### W-01 (resuelto)
`.atl/skill-registry.md` documentaba la convención antigua de `IllegalStateException`.
Actualizado después de verify para reflejar `NotFoundException`. El fix no está commiteado
(`.atl/` no está trackeado en git — decisión deliberada). Engram ref: #279.

---

## Sincronización de Specs

| Acción | Ruta |
|--------|------|
| Creada | `openspec/specs/exception-domain-model/spec.md` |
| Creada | `openspec/specs/security-authorization/spec.md` |
| Creada | `openspec/specs/jwt-configuration/spec.md` |
| Creada | `openspec/specs/jpa-fetch-strategy/spec.md` |

Las specs canónicas incluyen sección `## Archive Metadata` con hashes de commits, fecha
de canonización y conteo de scenarios verificados.

---

## Carpeta de Cambio Archivada

```
openspec/changes/archive/2026-05-09-backend-hardening-high-issues/
  proposal.md
  design.md
  tasks.md
  specs/
    exception-domain-model/spec.md
    security-authorization/spec.md
    jwt-configuration/spec.md
    jpa-fetch-strategy/spec.md
  verify-report.md
  archive-report.md  ← este archivo (copia)
```

---

## Deuda Residual Preservada

| ID | Descripción | Engram ref | Responsable | Estado |
|----|-------------|-----------|-------------|--------|
| DEBT-01 | Secreto JWT sigue en historia de git. Requiere `git filter-repo` o BFG Repo-Cleaner. | spec jwt-configuration §"Deuda Residual" | Equipo — antes de publicar el repo | ACEPTADA — TFG con datos inventados, secreto rotado, sin riesgo operativo |
| DEBT-02 | E52/E53 tienen numeración duplicada con PresenciaController (E52→E55 resuelto para las caps afectadas; quedan duplicados residuales). Requiere change `endpoints-numbering-sanity`. | Engram #276 | Próximo ciclo SDD | RESUELTA — commit del cierre (2026-05-09): `HealthController` reasignado a E56 (antes duplicaba E52 con `TerminalController#estado`); `PdfController#vacaciones` reasignado a E57 (antes duplicaba E53 con `TerminalController#bloqueo`). Numeración final consistente: E48–E54 TerminalController, E55 PausaController, E56 Health, E57 PdfController/vacaciones. README sincronizado. Cero impacto runtime — solo Javadoc/`@Tag` |
| DEBT-03 | `SaldoServiceTest.recalcularParaProceso_mezclaDeTipos` falla desde baseline. No es regresión de este change. | apply-progress #271 | Deuda de tests pre-SDD | RESUELTA — commit `7f0bca4` (2026-05-09): test esperaba semántica equivocada de `diasTrabajados`. Corregido el assert (1→2) — el código de producción ya era correcto (BAJA_MEDICA y PERMISO_RETRIBUIDO suman jornada consumida). Suite safe pasa de 29/30 a 30/30 |
| DEBT-04 | Pre-commit hook `chmod +x mvnw` recomendado para CI en Linux. | S-03 verify-report | Opcional — CI setup | NO APLICA — verificado 2026-05-09: `mvnw` ya está marcado `100755` en el índice git desde el commit inicial. La sugerencia original era falsa alarma |
| DEBT-05 | ArchUnit test para detectar ISE en paths not-found (S-01 del verify-report). | #278 §SUGGESTION | Próximo ciclo SDD | RESUELTA — commit del cierre (2026-05-09): añadida dependencia `archunit-junit5:1.4.0` + clase `ServiceLayerArchitectureTest` con regla de whitelist (PdfService, SaldoService) que prohíbe ISE en `com.staffflow.service..`. Validada con sanity-check inverso: la regla detecta correctamente violaciones |

---

## Engram Artifacts

| Artifact | ID / Topic key |
|----------|----------------|
| Project init | #248 — `sdd-init/staffflow` |
| Proposal | #253 |
| Spec: exception-domain-model | #254 — `sdd/backend-hardening-high-issues/spec` (original) |
| Spec: security-authorization | #255 |
| Spec: jwt-configuration | #256 |
| Spec: jpa-fetch-strategy | #257 |
| Design | #260 |
| Tasks | #262 |
| Apply-progress (final) | #271 — `sdd/backend-hardening-high-issues/apply-progress` |
| Verify report | #278 — `sdd/backend-hardening-high-issues/verify-report` |
| Archive report | `sdd/backend-hardening-high-issues/archive-report` |
| Canonical spec: exception-domain-model | `staffflow/specs/exception-domain-model` |
| Canonical spec: security-authorization | `staffflow/specs/security-authorization` |
| Canonical spec: jwt-configuration | `staffflow/specs/jwt-configuration` |
| Canonical spec: jpa-fetch-strategy | `staffflow/specs/jpa-fetch-strategy` |
| W-01 post-verify fix | #279 — `staffflow/conventions/exception-mapping` |
| Endpoint numbering debt | #276 — `staffflow/contract/endpoints-numbering` |

---

## Pasos Recomendados Post-Archivo

1. **Commit docs**: `git add openspec/ && git commit -m "docs(openspec): archiva backend-hardening-high-issues y sincroniza specs canonicos"`
2. **Push**: `git push origin feat/backend-hardening-high-issues`
3. **Pull Request**: abrir PR de `feat/backend-hardening-high-issues` → `master`; incluir resumen del change y tabla de deuda residual
4. **Merge**: hacer squash merge o merge commit (sin rebase si el equipo prefiere historia limpia)
5. **Eliminar tag de backup**: `git tag -d sdd-backup-pre-rebase` (SOLO después de confirmar que el merge fue exitoso)
6. **Deuda DEBT-01**: planificar scrubbing de historia git (BFG Repo-Cleaner) como tarea separada antes de hacer el repo público

---

## Skill Resolution

- `sdd-archive` skill: **injected** — leído desde `~/.config/opencode/skills/sdd-archive/SKILL.md`
- Skill registry: `staffflow/.atl/skill-registry.md` leído y alineado (post W-01 fix)
- `cognitive-doc-design`: no aplicable para este tipo de artifact técnico de cierre de ciclo

---

## Ciclo SDD Completo

```
propose → spec → design → tasks → apply → verify → archive ✅
```

El change `backend-hardening-high-issues` ha sido completamente planificado, implementado,
verificado y archivado. Los 5 issues HIGH del backend de StaffFlow están cerrados.
Listo para el siguiente ciclo.
