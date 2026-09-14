# StaffFlow — Catálogo de la API

Catálogo de referencia de los 68 endpoints REST (E01–E68) y de las 30 pantallas Android (P01–P30) que los consumen. La API se ha definido con enfoque **design-first**: todos los endpoints se especificaron antes de implementar la lógica de negocio. La visión de conjunto está en el [informe técnico, §7](informe-tecnico.md#7-api-rest); Swagger UI (`http://localhost:8080/swagger-ui.html`) expone la misma información con los esquemas de request y response.

La especificación incluye:

- **68 endpoints** en **13 grupos funcionales**
- Control de acceso por roles en cada endpoint
- Terminal de fichaje con PIN en ruta separada `/api/v1/terminal/` con cadena de seguridad propia. Los 5 endpoints del flujo de fichaje (entrada, salida, pausa iniciar/finalizar, estado) son públicos; los 2 endpoints de gestión del bloqueo del terminal requieren JWT con rol ADMIN o ENCARGADO
- Bloqueo por fuerza bruta: 5 intentos fallidos de PIN desde el mismo dispositivoId → HTTP 423. El bloqueo persiste hasta que un ADMIN/ENCARGADO desbloquea el terminal vía E54 (DELETE /api/v1/terminal/bloqueo), un PIN exitoso reinicia el contador o el servidor se reinicia (contador in-memory).

## Catálogo de endpoints

Los 68 endpoints están organizados en 13 grupos funcionales. La tabla siguiente lista cada endpoint con su grupo, verbo HTTP, ruta, roles autorizados, descripción y la pantalla Android que lo consume.

Convenciones de la tabla:

- **Path relativo**: la ruta base de cada grupo aparece en el encabezado de la sección.
- **Roles**: `público` (sin autenticación) · `autenticado` (cualquier rol con JWT válido) · uno o más de `EMPLEADO`, `ENCARGADO`, `ADMIN`.
- **Pantalla(s)**: identificador `P##` de la pantalla Android que consume el endpoint, o `—` si la app actual no lo invoca (la API expone capacidades; otros clientes consumirán las suyas).

### Auth (`/api/v1/auth`)

| E# | Verbo + Path | Roles | Descripción | Pantalla(s) |
|----|--------------|-------|-------------|--------------|
| E01 | POST /login | público | Autentica con username y password, devuelve un JWT firmado con HMAC-SHA (algoritmo HS256, HS384 o HS512 seleccionado por jjwt según la longitud del `JWT_SECRET`) con expiración de 12 h, junto con `rol`, `username`, `empleadoId` (null si ADMIN) y `nombre` para mostrar (nombre + apellido1 del empleado, o `username` como fallback si ADMIN) | P02 |
| E02 | GET /me | autenticado | Devuelve los datos del usuario asociado al token actual; 404 si el `username` extraído del token ya no existe en BD | — |
| E03 | PUT /password | autenticado | Cambia la contraseña del propio usuario autenticado verificando primero la contraseña actual (RNF‑S01); 400 si la contraseña actual no coincide, 404 si el usuario del token no existe | P04 |
| E04 | POST /password/recovery | público | Solicita recuperación: si el email existe en BD, genera una contraseña temporal de 8 caracteres alfanuméricos sin caracteres ambiguos (excluidos `0/1/O/I/i/l/o`), sobrescribe el `passwordHash` y envía la temporal al email **registrado en BD** (no al tipeado, que solo identifica la cuenta). Por anti‑enumeración (RNF‑S04) siempre devuelve 200 con el mismo mensaje genérico, exista o no el email, impidiendo que un atacante deduzca qué emails están registrados mediante consultas en batch (ataque de enumeración por respuesta diferenciada) | P03 |
| E05 | POST /password/reset | público | Restablece la contraseña con un token de un solo uso recibido por email. Implementado como contrato preparado; en v1.0 el flujo activo es contraseña temporal vía E04 y E05 responde siempre 400 «token inválido o ya utilizado» porque ningún endpoint popula `resetToken` en producción. En v2.0, además, la rama 400 «ha caducado» se activará cuando `resetTokenExpiry` sea null o anterior al instante actual — populado pendiente para v2.0 | P05 |

### Empresa (`/api/v1/empresa`)

| E# | Verbo + Path | Roles | Descripción | Pantalla(s) |
|----|--------------|-------|-------------|--------------|
| E06 | GET / | ADMIN | Devuelve la configuración global de la empresa (singleton id=1). 404 si el singleton aún no existe en BD (sistema sin configurar; situación normal antes del primer PUT vía E07) | P30 |
| E07 | PUT / | ADMIN | Actualiza la configuración global de la empresa (singleton id=1). Crea el registro si no existe (primera configuración del sistema) | P30 |

### Usuarios (`/api/v1/usuarios`)

| E# | Verbo + Path | Roles | Descripción | Pantalla(s) |
|----|--------------|-------|-------------|--------------|
| E08 | POST / | ADMIN | Crea un usuario nuevo (autenticación + rol). HTTP 409 si el username o el email ya existen (validación preventiva con mensaje específico por campo en conflicto); P29 reacciona ante el 409 de username regenerando automáticamente el username con el siguiente prefijo libre sin perder el resto del formulario. Solo crea el `Usuario`; para los roles ENCARGADO y EMPLEADO el perfil de empleado vinculado se crea por separado vía E13 (P29 lo encadena en el flujo de alta combinada) | P29 |
| E09 | GET / | ADMIN | Lista usuarios con filtros opcionales (rol, activo) | P28, P29 |
| E10 | GET /{id} | ADMIN | Detalle de un usuario por id | P29 |
| E11 | PATCH /{id} | ADMIN | Actualiza email y rol de un usuario (el estado activo no se modifica por esta vía; ver E12; la contraseña se gestiona por E66). HTTP 409 si la transición de rol viola la invariante rol↔empleado (ADMIN puro no puede cambiar de rol; usuario con empleado asociado no puede ser promovido a ADMIN). Enviar el mismo rol que el actual no dispara el guard y devuelve 200 (no-op aceptado). El guard refuerza la separación usuario↔empleado (Decisión 2): el modelo no soporta crear ni borrar perfiles de empleado como side-effect de un cambio de rol, lo que evita estados híbridos | P29 |
| E12 | DELETE /{id} | ADMIN | Desactiva un usuario (baja lógica, no borrado físico) | P29 |
| E66 | PATCH /{id}/password | ADMIN | Restablece la contraseña de un usuario directamente (caso de uso helpdesk). Sin envío de correo. Mínimo 8 caracteres | P29 |
| E67 | PATCH /{id}/reactivar | ADMIN | Reactiva un usuario previamente desactivado (activo = true). Simétrico a E12 (desactivar) y a E18 (reactivar empleado). HTTP 409 si el usuario ya estaba activo | P29 |

### Empleados (`/api/v1/empleados`)

| E# | Verbo + Path | Roles | Descripción | Pantalla(s) |
|----|--------------|-------|-------------|--------------|
| E13 | POST / | ADMIN | Crea un empleado nuevo. Genera PIN único y número de empleado automáticos. Calcula `jornadaDiariaMinutos = Math.round(jornadaSemanalHoras / 5 × 60)` a partir del valor contractual semanal. `fechaAlta` es opcional: si llega, debe ser ≥ hoy (altas diferidas); si se omite, se asigna `LocalDate.now()`. HTTP 400 si `fechaAlta` es anterior a hoy; HTTP 404 si `usuarioId` no existe; HTTP 409 si `dni` o `codigoNfc` ya pertenecen a otro empleado. El rechazo de alta retroactiva preserva la coherencia con el cálculo prorrateado de saldos anuales (un empleado no puede acumular horas ni vacaciones antes de su fecha de alta); las correcciones posteriores se hacen vía E16, que sí admite `fechaAlta` retroactiva por diseño contrastado | P29 |
| E14 | GET / | ADMIN, ENCARGADO | Lista empleados con filtros opcionales (q, activo, categoría). Sin filtros devuelve todos (activos e inactivos). HTTP 400 si el valor de `categoría` no es un enum válido | P13 |
| E15 | GET /{id} | ADMIN, ENCARGADO | Detalle de un empleado. ADMIN ve `pinTerminal`, `email`, `username` y `rol` del usuario asociado; ENCARGADO los recibe a `null` (Opción A). HTTP 404 si el `id` no existe | P14, P15 |
| E16 | PATCH /{id} | ADMIN, ENCARGADO | Actualiza campos parciales del empleado (PATCH selectivo: solo los campos enviados se aplican). Campos editables: `nombre`, `apellido1`, `apellido2`, `dni`, `fechaAlta` (sin restricción de rango: pasado o futuro), `categoria`, `jornadaSemanalHoras`, `jornadaDiariaMinutos`, `diasVacacionesAnuales`, `diasAsuntosPropiosAnuales`, `codigoNfc`. HTTP 409 si `dni` o `codigoNfc` ya pertenecen a otro empleado. PIN de terminal NO se modifica aquí — usar E65; `numeroEmpleado` y `usuarioId` son inmutables | P15 |
| E17 | PATCH /{id}/baja | ADMIN, ENCARGADO | Da de baja lógica al empleado (activo=false). Conserva historial. HTTP 404 si el `id` no existe | P14 |
| E18 | PATCH /{id}/reactivar | ADMIN, ENCARGADO | Reactiva un empleado dado de baja. HTTP 404 si el `id` no existe; HTTP 409 si el empleado ya estaba activo | P14 |
| E19 | GET /estado | ADMIN, ENCARGADO | Resumen del estado de presencia de cada empleado. Acepta `?fecha` opcional (formato ISO, default = hoy). Respuesta idéntica a E35 (ParteDiarioResponse) | — |
| E20 | GET /export | ADMIN, ENCARGADO | Exporta el listado de empleados a CSV o PDF. El parámetro `formato` es obligatorio (`csv` o `pdf`); HTTP 400 si el valor no es válido. Acepta `?activo` opcional para filtrar por estado (defecto: solo activos, asimetría intencional con E14 que sin filtros devuelve todos) | — |
| E65 | POST /{id}/regenerar-pin | ADMIN, ENCARGADO | Regenera el PIN de terminal del empleado y lo devuelve en la respuesta. El PIN queda persistido; tras la regeneración solo es re-consultable por ADMIN vía E15 | P14 |
| E68 | GET /by-usuario/{usuarioId} | ADMIN | Devuelve el empleado vinculado a un usuario dado (relación 1:1 garantizada por UNIQUE sobre `usuario_id`). Alimenta la cabecera read-only de P29 que identifica al empleado y permite saltar a P14. HTTP 404 si el usuario no tiene empleado asociado (caso típico: usuario ADMIN). Devuelve `EmpleadoResponse` sin `pinTerminal`, `email`, `username` ni `rol` (la cabecera solo consume nombre, apellidos y `numeroEmpleado`) | P29 |
| E21 | GET /me | EMPLEADO, ENCARGADO | Perfil del empleado autenticado. HTTP 404 si el usuario autenticado no tiene perfil de empleado asociado | P08 |

### Fichajes (`/api/v1/fichajes`)

| E# | Verbo + Path | Roles | Descripción | Pantalla(s) |
|----|--------------|-------|-------------|--------------|
| E22 | POST / | ADMIN, ENCARGADO | Crea un fichaje manual con observaciones obligatorias (RNF-L02). ENCARGADO solo puede registrarlo hoy o en el futuro; ADMIN sin restricción de fecha. Fichajes en fecha futura prohibidos para cualquier rol. 404 si el empleado no existe o si el username del JWT no está en BD; 409 si ya existe fichaje para ese empleado en esa fecha (UNIQUE empleado+fecha) | P20 |
| E23 | PATCH /{id} | ADMIN, ENCARGADO | Modifica un fichaje existente con observaciones obligatorias (RNF-L02). Misma restricción de fecha que E22, validada sobre la fecha del fichaje cargado de BD. Si llegan `horaEntrada` y `horaSalida`, recalcula `jornadaEfectivaMinutos` con `Math.ceil` descontando el `totalPausasMinutos` ya almacenado (E23 no toca pausas). 404 si el fichaje no existe o si el username del JWT no está en BD | P20 |
| E24 | GET / | ADMIN, ENCARGADO | Lista fichajes con filtros opcionales y combinables: `empleadoId`, `desde`, `hasta`, `tipo`. Sin filtros devuelve todos. La query usa `JOIN FETCH` sobre `empleado` para evitar el problema N+1 | P16 |
| E25 | GET /incompletos | ADMIN, ENCARGADO | Lista fichajes con entrada registrada y sin hora de salida (jornadas abiertas) para una fecha. Parámetro `fecha` opcional (defecto: hoy vía `Clock` inyectado). Útil para detectar al cierre del día quién olvidó fichar la salida | — |
| E26 | GET /me | EMPLEADO, ENCARGADO | Lista los fichajes del empleado autenticado en formato JSON. Filtros opcionales `desde`, `hasta`, `tipo` con la misma lógica que E24. 404 si el usuario autenticado no tiene perfil de empleado | — |

### Pausas (`/api/v1/pausas`)

| E# | Verbo + Path | Roles | Descripción | Pantalla(s) |
|----|--------------|-------|-------------|--------------|
| E27 | POST / | ADMIN, ENCARGADO | Registra una pausa manual para un empleado. ENCARGADO solo puede gestionarla hoy o en el futuro; ADMIN sin restricción de fecha. `horaFin` opcional: si se omite, la pausa queda activa. 404 si el `empleadoId` no existe. 409 si ya hay una pausa activa abierta (`horaFin=null`) ese día para el empleado — solo puede existir una | P20 |
| E28 | PATCH /{id} | ADMIN, ENCARGADO | Cierra o modifica una pausa existente. Observaciones obligatorias (RNF-L02). Misma restricción de fecha que E27 sobre la fecha de la pausa cargada de BD. 404 si la pausa no existe. Si llega `horaFin`, calcula `duracionMinutos` con `Math.floor` y, salvo `AUSENCIA_RETRIBUIDA`, actualiza `totalPausasMinutos` y recalcula `jornadaEfectivaMinutos` del fichaje del día (si existe). Asimetría intencional de redondeo: la pausa usa `Math.floor` y la jornada efectiva `Math.ceil` — ambas decisiones benefician al empleado (descuenta menos por la pausa, suma más por la jornada). Las pausas `AUSENCIA_RETRIBUIDA` (consultas médicas, trámites legales) no descuentan jornada efectiva por RF-35; su tiempo se acumula en `horas_ausencia_retribuida` del `SaldoAnual` | P20 |
| E29 | GET / | ADMIN, ENCARGADO | Lista pausas con filtros opcionales y combinables: `empleadoId`, `desde`, `hasta`, `tipoPausa` | P16 |
| E55 | GET /me | EMPLEADO, ENCARGADO | Lista las pausas del empleado autenticado en formato JSON. Filtros opcionales `desde` y `hasta` (a diferencia de E29, no acepta `tipoPausa` porque el lookup se hace por empleado, no por filtros administrativos). 404 si el username del JWT no existe en BD o no tiene perfil de empleado | — |

### Ausencias (`/api/v1/ausencias`)

| E# | Verbo + Path | Roles | Descripción | Pantalla(s) |
|----|--------------|-------|-------------|--------------|
| E30 | POST / | ADMIN, ENCARGADO | Planifica una ausencia individual o festivo global (`empleadoId` null). ENCARGADO solo puede planificar para hoy o fechas futuras; ADMIN sin restricción. 404 si el `empleadoId` no existe o si el username del JWT no está en BD. 409 si ya existe una ausencia planificada para ese empleado en esa fecha | P24 |
| E31 | PATCH /{id} | ADMIN, ENCARGADO | Modifica una ausencia planificada no procesada. ENCARGADO solo puede modificar ausencias cuya fecha (la de la entidad, no la del request) sea hoy o futura; ADMIN sin restricción. 404 si la ausencia no existe o si el username del JWT no está en BD. 409 si la ausencia ya tiene fichaje generado (`procesado=true`) — para modificar el fichaje, usar E23 | P24 |
| E32 | DELETE /{id} | ADMIN, ENCARGADO | Elimina una ausencia planificada no procesada. 204 No Content si éxito. 404 si la ausencia no existe. 409 si la ausencia ya tiene fichaje generado (`procesado=true`) — no se puede eliminar por RNF-L01 | P24 |
| E33 | GET / | ADMIN, ENCARGADO | Lista ausencias planificadas con filtros opcionales: `empleadoId`, `desde`, `hasta` y `procesado`. Sin filtros devuelve todas, incluyendo festivos globales (`empleado_id = null`) | P16 |
| E34 | GET /me | EMPLEADO, ENCARGADO | Lista las ausencias del empleado autenticado en formato JSON. 404 si el username del JWT no está en BD o no tiene perfil de empleado | — |
| E61 | GET /me/informe | EMPLEADO, ENCARGADO | Informe HTML de ausencias del empleado autenticado. Params opcionales: `?desde=`, `?hasta=` (defecto: año actual completo) y `?filtro=VACACIONES_AP` (defecto `TODAS`). Combina planificaciones y fichajes; el fichaje tiene prioridad en la misma fecha | P11 |
| E62 | GET /{empleadoId}/informe | ADMIN, ENCARGADO | Informe HTML de ausencias de un empleado concreto. Mismos params opcionales que E61 (`?desde=`, `?hasta=`, `?filtro=VACACIONES_AP`). Misma lógica que E61 resolviendo por `empleadoId` | P22 |
| E63 | POST /rango | ADMIN, ENCARGADO | Planifica un rango de ausencias en una sola llamada. ENCARGADO solo puede iniciar el rango con `fechaDesde` hoy o futura; ADMIN sin restricción. Si algún día del rango tiene `procesado=false` y `sobrescribir=false`, devuelve 409 (`RangoConflictException` con `fechasConflictivas`). Si algún día tiene `procesado=true` (ya materializado en fichaje), devuelve 400: no se puede sobrescribir un fichaje generado. Con `sobrescribir=true`, los conflictos `procesado=false` se eliminan antes de crear el rango nuevo: preserva la constraint `UNIQUE(empleado, fecha)` sin requerir lógica de UPSERT | P24 |
| E64 | GET /planificacion-vac-ap | ADMIN, ENCARGADO | Días pendientes de planificar para vacaciones y asuntos propios de un empleado en un año concreto (params `empleadoId` obligatorio y `anio` opcional, defecto año actual). Si no existe `SaldoAnual` para ese año, lo crea on-demand (find-or-create). Calcula `pendientes = max(0, diasDisponibles − diasPlanificados)` para evitar valores negativos en reprogramaciones (cuando el empleado ya tiene más días planificados que los disponibles tras un ajuste). Devuelve `anioFuturoSinCierre=true` cuando el año consultado es posterior al actual | P23, P24 |

### Presencia (`/api/v1/presencia`)

| E# | Verbo + Path | Roles | Descripción | Pantalla(s) |
|----|--------------|-------|-------------|--------------|
| E35 | GET /parte-diario | ADMIN, ENCARGADO | Parte diario de presencia con 6 estados por empleado. Acepta `?fecha` opcional (formato ISO, default = hoy). Incluye solo empleados operativos a la fecha consultada (`activo = true` AND `fechaAlta <= fecha`); los empleados con alta diferida no aparecen hasta su primer día de trabajo | P17 |
| E36 | GET /sin-justificar | ADMIN, ENCARGADO | Lista de empleados sin fichaje ni ausencia justificada en una fecha. Acepta `?fecha` opcional (formato ISO, default = hoy) | P18 |
| E37 | GET /parte-diario/me | EMPLEADO, ENCARGADO | Estado de presencia del empleado autenticado. Acepta `?fecha` opcional (formato ISO, default = hoy). HTTP 404 si el usuario autenticado no tiene perfil de empleado | P12 |

### Saldos (`/api/v1/saldos`)

| E# | Verbo + Path | Roles | Descripción | Pantalla(s) |
|----|--------------|-------|-------------|--------------|
| E38 | GET /{empleadoId} | ADMIN, ENCARGADO | Saldo anual de un empleado concreto (vacaciones, AP, horas). 404 si el empleado no existe. Para el año actual, si no hay registro de saldo persistido, lo crea on-demand antes de devolverlo (find-or-create); en años pasados o futuros sin registro devuelve 404. El patrón find-or-create solo aplica al año actual (E38, E39, E41) o cuando lo dispara E40: evita poblar `saldos_anuales` con registros vacíos para años sin actividad | P25 |
| E39 | GET / | ADMIN, ENCARGADO | Lista de saldos anuales de todos los empleados con registro en ese año en formato JSON. Para el año actual, crea on-demand el saldo de cada empleado activo sin registro antes de listar (find-or-create restringido a activos); en años pasados o futuros se devuelven solo los registros ya persistidos, incluyendo empleados inactivos con histórico | — |
| E40 | POST /{empleadoId}/recalcular | ADMIN | Fuerza el recálculo idempotente del saldo anual de un empleado. 404 si el empleado no existe. Si no existe registro de saldo para el año lo crea con los valores iniciales del contrato (find-or-create) antes de recalcular desde cero | P20, P24, P25 |
| E41 | GET /me | EMPLEADO, ENCARGADO | Saldo anual del empleado autenticado. 404 si el usuario autenticado no tiene perfil de empleado, si el año solicitado es posterior al actual, o si es anterior a la fechaAlta del empleado. Para años válidos sin registro, lo crea on-demand antes de devolverlo (find-or-create) | P09 |

### Informes HTML (`/api/v1/informes`)

Endpoints dual-format JSON/HTML solo en E42, E43 y E44: por defecto devuelven JSON; añadiendo `?formato=html` devuelven HTML para WebView. La app Android los consume siempre con `?formato=html`, por eso se agrupan aquí como "Informes HTML". E58, E59 y E60 son HTML-only (la firma del controller no acepta `?formato=` y el service siempre genera HTML). E61 y E62 (informes de ausencias, agrupados bajo `/api/v1/ausencias`) también son HTML-only por el mismo motivo.

| E# | Verbo + Path | Roles | Descripción | Pantalla(s) |
|----|--------------|-------|-------------|--------------|
| E42 | GET /horas/{empleadoId} | ADMIN, ENCARGADO | Informe de horas trabajadas de un empleado en un rango. Dual-format JSON/HTML (`?formato=`, defecto JSON); con `?formato=html` devuelve HTML imprimible para WebView + PrintManager. Filtro opcional `?tipo=` por uno o varios `TipoFichaje` separados por coma, mas `DIA_LIBRE` y `SIN_REGISTRO`. 404 si el empleado no existe | P21, P27 |
| E43 | GET /horas | ADMIN, ENCARGADO | Informe global de horas trabajadas de todos los empleados activos en un rango. Dual-format JSON/HTML igual que E42. Mismo filtro opcional `?tipo=`. Solo incluye empleados operativos en el rango (`fechaAlta <= hasta`) | P27 |
| E44 | GET /saldos | ADMIN, ENCARGADO | Informe de saldos anuales de empleados. Dual-format JSON/HTML. `?anio=` opcional (defecto año actual). `?empleadoId=` lista opcional de ids (sin parametro = todos los activos). `?campos=` opcional con bloques (`DIAS_VACACIONES`, `DIAS_ASUNTOS_PROPIOS`, `RESTO_DIAS`, `HORAS`, `CONTROL`) o campos individuales. Efecto colateral find-or-create: si el año ya tiene al menos un `SaldoAnual`, completa on-demand los empleados activos sin registro via `SaldoService.recalcularParaProceso`. 404 si ningun empleado activo tiene saldo para ese año | P26, P27 |
| E58 | GET /me/horas | EMPLEADO, ENCARGADO | Informe HTML de horas del empleado autenticado. HTML-only (la firma del controller no acepta `?formato=`). Delega en E42 con `formato=html`. `?desde=` y `?hasta=` obligatorios. 404 si el usuario autenticado no existe en BD o no tiene perfil de empleado (caso tipico: ENCARGADO puro sin ficha) | P10 |
| E59 | GET /semana | ADMIN, ENCARGADO | Tabla HTML semanal de presencia de todos los empleados activos (empleado × dia). HTML-only. Cada celda muestra fichaje, pausas y/o ausencia planificada del dia; saldo inicial al lunes y contribucion semanal al saldo en columnas dedicadas. URLs `staffflow://` permiten editar celdas desde el WebView Android: ADMIN edita cualquier fecha no futura, ENCARGADO solo hoy (fichajes/pausas); ADMIN cualquier fecha y ENCARGADO hoy y futuro (ausencias planificadas) | P19 |
| E60 | GET /ausencias | ADMIN, ENCARGADO | Tabla HTML interactiva de ausencias de todos los empleados activos en un rango (empleado × dia). HTML-only. Incluye fichajes de tipo != `NORMAL` y != `DIA_LIBRE` (ausencias ejecutadas), planificaciones individuales y festivos globales (`empleado=null`) replicados en cada celda del dia del festivo. Edicion via URLs `staffflow://`: ADMIN edita fichajes de ausencia en fechas no futuras (ENCARGADO no edita fichajes desde este informe); ADMIN cualquier fecha y ENCARGADO hoy y futuro (ausencias planificadas). Selector JS multi-celda para acciones masivas | P23 |

### PDF para firmar (`/api/v1/informes/pdf`)

| E# | Verbo + Path | Roles | Descripción | Pantalla(s) |
|----|--------------|-------|-------------|--------------|
| E45 | GET /horas/{empleadoId} | ADMIN, ENCARGADO | PDF firmable del informe de horas de un empleado en un periodo (iText 7). Mismo contenido que E42 (Opción C: reutiliza datos vía InformeService) en formato firmable con espacio para firma física. 404 si el empleado no existe. Nombre del fichero: `informe_horas_{id}_{desde}_{hasta}.pdf` | P27 |
| E46 | GET /horas | ADMIN, ENCARGADO | PDF firmable del informe de horas de todos los empleados activos en un periodo. Genera un PDF E45 por empleado activo y los concatena con PdfMerger; si no hay empleados activos, devuelve un PDF sin páginas. Nombre del fichero: `informe_horas_global_{desde}_{hasta}.pdf` | P27 |
| E47 | GET /saldos | ADMIN, ENCARGADO | PDF firmable del informe de saldos anuales. `anio` opcional (defecto: año actual). `empleadoId` opcional (lista; defecto: todos los empleados activos con saldo en ese año, ordenados por nombre); si se pasan ids sin saldo registrado se omiten silenciosamente. Si no hay saldos, devuelve un PDF de una página con mensaje informativo. Nombre del fichero: `informe_saldos_{yyyyMMdd}.pdf` | P27 |
| E57 | GET /vacaciones | ADMIN, ENCARGADO | PDF firmable del informe de vacaciones y asuntos propios disfrutados por un empleado en un año. `empleadoId` obligatorio. `anio` opcional (defecto: año actual). 404 si el empleado no existe. Si no hay registro de SaldoAnual para el año, los días pendientes se reportan como 0. Nombre del fichero: `informe_vacaciones_{id}_{yyyyMMdd}.pdf` | P27 |

### Terminal PIN (`/api/v1/terminal`)

| E# | Verbo + Path | Roles | Descripción | Pantalla(s) |
|----|--------------|-------|-------------|--------------|
| E48 | POST /entrada | público | Registra el inicio de jornada por PIN de 4 dígitos. Crea un nuevo `Fichaje` con tipo `NORMAL`, `horaEntrada=now()`, `horaSalida=null` y `usuario_id=terminal_service` (autor técnico, RNF-L01). Restricción «1 fichaje por empleado y día» (constraint UNIQUE `(empleado_id, fecha)`, RNF-I02): 409 si ya hay fichaje hoy. 400 si el empleado está de baja (`activo=false`). 404 si el PIN no existe (incrementa el contador del `dispositivoId`). 423 tras 5 intentos fallidos consecutivos desde el mismo dispositivo (RNF-S05). 200 reinicia el contador del dispositivo | P06 |
| E49 | POST /salida | público | Registra el fin de jornada por PIN y calcula la jornada efectiva. Persiste `jornadaEfectivaMinutos = Math.ceil(minutosBrutos − totalPausasMinutos)` en la entidad `Fichaje` (consumida por `SaldoService`) y devuelve además `jornadaEfectivaSegundos = max(0, segundosBrutos − totalPausasSegundos)` calculado sobre los segundos exactos de las pausas cerradas no retribuidas del día para el display del terminal. 400 si no hay entrada registrada hoy. 409 si la salida ya está registrada o si hay una pausa activa pendiente de cerrar. 404 PIN inexistente. 423 dispositivo bloqueado | P06 |
| E50 | POST /pausa/iniciar | público | Inicia una pausa por PIN. El cliente envía además el `tipoPausa` (`COMIDA`, `DESCANSO`, `AUSENCIA_RETRIBUIDA`, `OTROS`) seleccionado previamente en P07. Crea una `Pausa` con `horaInicio=now()`, `horaFin=null`, `duracionMinutos=null` y `usuario_id=terminal_service`. Solo puede haber una pausa activa por empleado y día: 409 si ya existe pausa con `horaFin=null` hoy. 400 si no hay entrada registrada hoy. 404 PIN inexistente. 423 dispositivo bloqueado | P06 |
| E51 | POST /pausa/finalizar | público | Finaliza la pausa activa del empleado. Persiste `duracionMinutos = Math.floor(minutos)` en la `Pausa` (consumida por `SaldoService`) y devuelve `duracionSegundos` exactos para el display. Si el `tipoPausa` NO es `AUSENCIA_RETRIBUIDA` actualiza `totalPausasMinutos` en el fichaje del día sumando la duración (las retribuidas no descuentan de jornada efectiva). Caso borde: si no existe fichaje del día la pausa se cierra igual sin tocar ningún fichaje y sin error. NO emite 409 (el único conflicto posible —ausencia de pausa activa— se mapea a 400). 404 PIN inexistente. 423 dispositivo bloqueado | P06 |
| E52 | POST /estado | público | Verifica el PIN y devuelve el estado actual del empleado para la pantalla de bienvenida. Solo lectura: no modifica ningún dato. Llamado desde P01 (`TerminalFragment`) tras introducir el PIN; los datos pasan a P06 ya cargados (P06 NO invoca E52). Respuesta: `nombre`, `estado` (`SIN_ENTRADA`, `EN_JORNADA`, `EN_PAUSA` o `JORNADA_CERRADA`, enum `EstadoTerminal` calculado en tiempo de ejecución y no persistido), `horaEntrada`, `horaSalida`, `horaInicioPausa` y `tipoPausa` según el estado del día. 404 PIN inexistente. 423 dispositivo bloqueado. 200 reinicia el contador del dispositivo | P01 |
| E53 | GET /bloqueo | ADMIN, ENCARGADO | Consulta si hay ALGÚN dispositivo de terminal bloqueado por intentos fallidos de PIN (RNF-S05). Devuelve `{"bloqueado": true/false}` agregando globalmente todos los `dispositivoId` del `ConcurrentHashMap` en memoria; NO desglosa por dispositivo. Requiere JWT. 401 sin token o token inválido. 403 rol insuficiente. La autorización viaja a través de `SecurityConfig.requestMatchers` y NO de `@PreAuthorize` en el método (defensa en profundidad pendiente para v2.0) | P17 |
| E54 | DELETE /bloqueo | ADMIN, ENCARGADO | Desbloquea el terminal tras un bloqueo por fuerza bruta. Resetea TODOS los contadores de intentos fallidos de TODOS los dispositivos haciendo `clear()` global del mapa en memoria (no permite desbloqueo individual por `dispositivoId`). Devuelve `{"bloqueado": false}` confirmando el estado tras el reset. Llamado desde P17 cuando un ADMIN o ENCARGADO confirma el desbloqueo manual en el diálogo del banner. Requiere JWT. 401/403 sin/con rol insuficiente. Misma observación que E53 sobre `@PreAuthorize` | P17 |

### Health (`/api/health`)

| E# | Verbo + Path | Roles | Descripción | Pantalla(s) |
|----|--------------|-------|-------------|--------------|
| E56 | GET /api/health | público | Health check para herramientas de monitorización (status: UP) | — |

> **Sobre la columna "Pantalla(s)"**: el guión (—) en pantalla indica que la app Android actual no consume ese endpoint. La API expone el contrato completo del dominio (operaciones de gestión avanzada, listados JSON para tablas nativas, monitorización externa); cada cliente que se conecte a futuro consumirá las capacidades que necesite. Esta separación es la base de la arquitectura desacoplada del proyecto: el backend no asume qué cliente lo invoca.


## Convención PUT / PATCH

- **PUT** → formulario completo (empresa, cambio de contraseña)
- **PATCH** → cambio de estado o campos parciales (baja, reactivar, modificar fichaje/pausa/ausencia)

## Catálogo de pantallas Android (P01-P30)

La tabla siguiente lista las 30 pantallas con su bloque funcional, endpoints principales y roles que pueden acceder a cada una:

| ID | Fragment | Bloque | Endpoints principales | Roles |
|---|---|---|---|---|
| P01 | TerminalFragment | 1 — Terminal | E52 | público |
| P02 | LoginFragment | 1 — Auth | E01 | público |
| P03 | RecoveryFragment | 1 — Auth | E04 | público |
| P04 | CambiarPasswordFragment | 1 — Auth | E03 | autenticado |
| P05 | ResetPasswordFragment | 1 — Auth | E05 | público (deep link) |
| P06 | ConfirmacionFragment | 1 — Terminal | E48, E49, E50, E51 | público |
| P07 | TipoPausaFragment | 1 — Terminal | (local) | público |
| P08 | MiPerfilFragment | 2 — Empleado | E21 | EMPLEADO, ENCARGADO |
| P09 | MiSaldoFragment | 2 — Empleado | E41 | EMPLEADO, ENCARGADO |
| P10 | MisFichajesFragment | 2 — Empleado | E58 | EMPLEADO, ENCARGADO |
| P11 | MisAusenciasFragment | 2 — Empleado | E61 | EMPLEADO, ENCARGADO |
| P12 | MiHoyFragment | 2 — Empleado | E37 | EMPLEADO, ENCARGADO |
| P13 | EmpleadosFragment | 3 — Gestión | E14 | ADMIN, ENCARGADO |
| P14 | DetalleEmpleadoFragment | 3 — Gestión | E15, E65 | ADMIN, ENCARGADO |
| P15 | FormEmpleadoFragment | 3 — Gestión | E15, E16 | ADMIN |
| P16 | DetalleDiaFragment | 4 — Encargado | E24, E29, E33 | ADMIN, ENCARGADO |
| P17 | ParteDiarioFragment | 4 — Encargado | E35, E53, E54 | ADMIN, ENCARGADO |
| P18 | SinJustificarFragment | 4 — Encargado | E36 | ADMIN, ENCARGADO |
| P19 | ResumenSemanalFragment | 4 — Encargado | E59 | ADMIN, ENCARGADO |
| P20 | FormFichajeFragment | 4 — Encargado | E22, E23, E27, E28, E40 | ADMIN, ENCARGADO |
| P21 | InformeFichajesEmpleadoFragment | 4 — Encargado | E42 | ADMIN, ENCARGADO |
| P22 | InformeAusenciasEmpleadoFragment | 4 — Encargado | E62 | ADMIN, ENCARGADO |
| P23 | AusenciasFragment | 4 — Encargado | E60, E64 | ADMIN, ENCARGADO |
| P24 | FormAusenciaFragment | 4 — Encargado | E30, E31, E32, E40, E63, E64 | ADMIN, ENCARGADO |
| P25 | SaldoFragment | 4 — Encargado | E38, E40 | ADMIN, ENCARGADO |
| P26 | SaldosGlobalesFragment | 4 — Encargado | E44 | ADMIN, ENCARGADO |
| P27 | InformesFragment | 4 — Encargado | E14, E42–E47, E57 | ADMIN, ENCARGADO |
| P28 | UsuariosFragment | 5 — Admin | E09 | ADMIN |
| P29 | FormUsuarioFragment | 5 — Admin | E08–E13, E66, E67, E68 | ADMIN |
| P30 | EmpresaFragment | 5 — Admin | E06, E07 | ADMIN |
