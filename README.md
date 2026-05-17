# StaffFlow

Sistema de control horario y gestión de ausencias para pequeñas y medianas empresas, desarrollado como **Proyecto Final del Grado Superior en Desarrollo de Aplicaciones Multiplataforma (DAM)**.

StaffFlow digitaliza el registro de jornada laboral y la gestión de ausencias en Pymes, cumpliendo con el **Real Decreto‑ley 8/2019**, que obliga a registrar diariamente el horario de trabajo de los empleados.

El proyecto se compone de:

- **Backend:** API REST desarrollada con Java 21 y Spring Boot 3.5
- **Cliente móvil:** aplicación Android nativa en Kotlin
- **Arquitectura desacoplada** que permite futuros clientes (web o escritorio)

---

## Descripción

> Proyecto completamente implementado y verificado. El backend cuenta con 65 endpoints operativos: autenticación JWT completa, gestión de contraseñas con recuperación por contraseña temporal vía email, configuración de empresa, gestión de usuarios y empleados, fichajes, pausas, terminal PIN, ausencias planificadas, presencia en tiempo real, saldos anuales, proceso nocturno automático de cierre de jornada, informes HTML/JSON y PDFs firmables con iText 7. La app Android tiene 30 pantallas implementadas en 6 bloques: terminal PIN (NFC reservado para v2), login, dashboards por rol, gestión de fichajes, pausas, ausencias, saldos, informes y PDFs. Testing completo: 61 tests unitarios + 1 test de arquitectura (ArchUnit) + 1 smoke test (@SpringBootTest) (JUnit 5 + Mockito + ArchUnit) contra MySQL 8.0. Verificación funcional completa con MySQL 8.0 y H2.

El sistema permite a una empresa gestionar el registro horario de sus empleados mediante:

- Fichaje de entrada y salida (desde app o terminal con PIN)
- Registro y gestión de pausas durante la jornada
- Planificación de ausencias (vacaciones, permisos, festivos nacionales y locales)
- Cálculo automático de saldos de horas y días disponibles
- Parte diario de presencia con 6 estados posibles por empleado
- Generación de informes operativos y PDFs firmables

La arquitectura separa completamente **backend y cliente**, permitiendo que múltiples aplicaciones consuman la misma API REST.

---

## Funcionalidades principales

- Autenticación con JWT (12h) y control de acceso por roles (ADMIN, ENCARGADO, EMPLEADO). El JWT no afecta al fichaje, que siempre se realiza por PIN. Afecta a la app de gestión: el ENCARGADO hace login una vez al día y el token persiste en DataStore, evitando reautenticaciones mientras dure la jornada. Un token más corto obligaría a hacer login repetidamente cada vez que se consulta o gestiona algo. La solución para combinar tokens cortos con buena usabilidad es el refresh token, documentado como mejora para v2.0
- Registro de jornada laboral mediante fichaje de entrada y salida
- Terminal de fichaje con PIN de 4 dígitos para dispositivo compartido (los 5 endpoints públicos del flujo de fichaje no requieren JWT; el bloqueo del terminal sí lo requiere). El esquema de BD reserva el campo `codigo_nfc` por empleado para una ampliación futura de fichaje por NFC, no implementada en v1.
- Gestión de pausas durante la jornada
- Planificación de ausencias individuales y festivos globales
- Proceso diario automático que convierte ausencias planificadas en fichajes
- Cálculo de saldo anual: vacaciones, asuntos propios y saldo de horas
- Parte diario de presencia (Jornada iniciada · En pausa · Jornada completada · Ausencia registrada · Ausencia planificada · Sin justificar)
- Informes operativos de horas trabajadas y ausencias en JSON y HTML imprimible
- Generación de informes PDF firmables con iText 7: horas por empleado (E45), horas global de todos los empleados (E46), saldos anuales (E47) y vacaciones/asuntos propios (E57)
- Informes HTML interactivos para WebView Android: horas individuales (E58), tabla semanal global (E59), ausencias globales (E60), informes individuales por empleado (E61, E62) y planificación de vacaciones/asuntos propios (E64)
- Creación de ausencias por rango de fechas en una sola llamada (E63), con detección de conflictos y opción de sobrescritura
- Recuperación de contraseña por email: se genera una contraseña temporal de 8 caracteres y se envía al email registrado via Gmail SMTP. El usuario inicia sesión con ella y la cambia desde la aplicación (E03). La recuperación por token de un solo uso está documentada como mejora para v2.0

---

## Stack tecnológico

### Backend

- Java 21 LTS (Temurin)
- Spring Boot 3.5.11
- Maven
- JPA / Hibernate
- MySQL 8.0 (producción) · H2 (desarrollo)
- jjwt 0.12.6
- SpringDoc OpenAPI 2.8.16 (Swagger UI)
- Lombok
- spring-boot-starter-mail
- iText 7.2.6 (informes PDF para firmar)
- JUnit 5 + Mockito (61 tests unitarios) + ArchUnit 1.4.0 (1 test de arquitectura) + 1 smoke test (@SpringBootTest)

### Cliente Android

- Kotlin 2.1.0
- AGP 8.13.0
- Retrofit 2.9.0 + OkHttp 4.12.0
- Navigation Component 2.8.0 (Single Activity)
- DataStore Preferences 1.1.1
- Coroutines 1.8.1
- Lifecycle ViewModel 2.8.0
- Material Design 3
- PrintManager + WebView (impresión de informes)

### Herramientas

- Git + GitHub
- IntelliJ IDEA Community 2025.2.2
- Android Studio Panda 1
- MySQL Workbench

---

## Perfiles de ejecución

El backend soporta dos perfiles Spring:

### Perfil `mysql` (por defecto en producción)

Conecta con MySQL 8.0. Requiere base de datos inicializada con el script DDL:

```
Documentos/Memoria final/Diagramas/staffflow_v7_ddl_mysql.sql
```

Configuración en `application-mysql.yml`. El validador de schema (`ddl-auto:validate`) comprueba en cada arranque que las entidades JPA coinciden exactamente con el DDL.

### Perfil `dev` (desarrollo con H2)

Base de datos en memoria. No requiere instalación de MySQL. Los datos de prueba se cargan automáticamente desde `data.sql` en cada arranque:

- 1 configuración de empresa
- 5 usuarios: admin, encargado01, emp01, emp02, terminal\_service
- 3 empleados con PIN asignado: Ana García (1111), Carlos López (2222), Laura Fernández (3333)

Para arrancar con perfil dev:

```
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

El perfil `dev` es la red de seguridad para la evaluación: permite demostrar todos los endpoints sin dependencia de MySQL.

Adicionalmente, el perfil `dev` expone un endpoint auxiliar **`POST /api/v1/test/cierre-diario`** (`TestProcesoCierreDiarioController`, anotado con `@Profile("dev")`) que permite disparar manualmente el proceso nocturno de cierre de jornada sin esperar al cron de las 23:55. **Este endpoint NO se registra en el perfil `mysql`** — Spring lo excluye del contexto y por tanto no existe en producción.

---

## Arquitectura

StaffFlow utiliza una **arquitectura en capas (Layered Architecture)**:

```
Request → Controller → Service → Repository → Entity → Response
```

Características principales:

- API REST **stateless** con autenticación **JWT**
- Control de acceso basado en **roles** con Spring Security (`@PreAuthorize`)
- Roles con reparto matricial por módulo (no jerarquía estricta):
  - **ADMIN**: gestión total. Único rol con acceso a configuración de empresa (E06-E07), gestión de usuarios (E08-E12) y recálculo forzado de saldos (E40). No tiene perfil de empleado, por lo que NO puede usar los endpoints `/me` ni fichar desde el terminal.
  - **ENCARGADO**: mismos permisos que ADMIN sobre los módulos operativos (empleados, fichajes, pausas, ausencias, presencia, saldos sin recálculo, informes, desbloqueo del terminal E53/E54), pero SIN acceso a empresa, usuarios ni recálculo. Tiene perfil de empleado: usa `/me` y ficha por PIN.
  - **EMPLEADO**: acceso exclusivo a sus propios datos vía endpoints `/me`. Tiene perfil de empleado: ficha por PIN.
- Separación entre **entidades de dominio y DTOs** (nunca se exponen entidades directamente)
- Persistencia mediante **JPA / Hibernate**
- Sin stored procedures ni triggers: toda la lógica de negocio en la capa service

La API usa versionado `/api/v1/` en todos los endpoints salvo `/api/health`.

---

## Diseño de la API

La API se ha definido con enfoque **design‑first**: todos los endpoints están especificados antes de implementar la lógica de negocio.

La especificación incluye:

- **65 endpoints** en **13 grupos funcionales**
- Control de acceso por roles en cada endpoint
- Terminal de fichaje con PIN en ruta separada `/api/v1/terminal/` con cadena de seguridad propia. Los 5 endpoints del flujo de fichaje (entrada, salida, pausa iniciar/finalizar, estado) son públicos; los 2 endpoints de gestión del bloqueo del terminal requieren JWT con rol ADMIN o ENCARGADO
- Bloqueo por fuerza bruta: 5 intentos fallidos de PIN desde el mismo dispositivoId → HTTP 423. El bloqueo persiste hasta que un ADMIN/ENCARGADO desbloquea el terminal vía E54 (DELETE /api/v1/terminal/bloqueo), un PIN exitoso reinicia el contador o el servidor se reinicia (contador in-memory).

### Catálogo de endpoints

Los 65 endpoints están organizados en 13 grupos funcionales. La tabla siguiente lista cada endpoint con su grupo, verbo HTTP, ruta, roles autorizados, descripción y la pantalla Android que lo consume.

Convenciones de la tabla:

- **Path relativo**: la ruta base de cada grupo aparece en el encabezado de la sección.
- **Roles**: `público` (sin autenticación) · `autenticado` (cualquier rol con JWT válido) · uno o más de `EMPLEADO`, `ENCARGADO`, `ADMIN`.
- **Pantalla(s)**: identificador `P##` de la pantalla Android que consume el endpoint, o `—` si la app actual no lo invoca (la API expone capacidades; otros clientes consumirán las suyas).

#### Auth (`/api/v1/auth`)

| E# | Verbo + Path | Roles | Descripción | Pantalla(s) |
|----|--------------|-------|-------------|--------------|
| E01 | POST /login | público | Autentica con email y password, devuelve un JWT con rol y empleadoId | P02 |
| E02 | GET /me | autenticado | Devuelve los datos del usuario asociado al token actual | — |
| E03 | PUT /password | autenticado | Cambia la contraseña del propio usuario autenticado | P04 |
| E04 | POST /password/recovery | público | Solicita recuperación: genera contraseña temporal y la envía por email | P03 |
| E05 | POST /password/reset | público | Restablece la contraseña con un token de un solo uso recibido por email. Implementado como contrato preparado; en v1.0 el flujo activo es contraseña temporal vía E04 (E05 responde siempre "token inválido" porque ningún endpoint popula `resetToken` en producción — populado pendiente para v2.0) | P05 |

#### Empresa (`/api/v1/empresa`)

| E# | Verbo + Path | Roles | Descripción | Pantalla(s) |
|----|--------------|-------|-------------|--------------|
| E06 | GET / | ADMIN | Devuelve la configuración global de la empresa (singleton id=1) | P30 |
| E07 | PUT / | ADMIN | Actualiza la configuración global de la empresa | P30 |

#### Usuarios (`/api/v1/usuarios`)

| E# | Verbo + Path | Roles | Descripción | Pantalla(s) |
|----|--------------|-------|-------------|--------------|
| E08 | POST / | ADMIN | Crea un usuario nuevo (autenticación + rol) | P29 |
| E09 | GET / | ADMIN | Lista usuarios con filtros opcionales (rol, activo) | P28, P29 |
| E10 | GET /{id} | ADMIN | Detalle de un usuario por id | P29 |
| E11 | PATCH /{id} | ADMIN | Actualiza email, rol o estado activo de un usuario | P29 |
| E12 | DELETE /{id} | ADMIN | Desactiva un usuario (baja lógica, no borrado físico) | P29 |

#### Empleados (`/api/v1/empleados`)

| E# | Verbo + Path | Roles | Descripción | Pantalla(s) |
|----|--------------|-------|-------------|--------------|
| E13 | POST / | ADMIN, ENCARGADO | Crea un empleado nuevo. Genera PIN único y número de empleado automáticos | P15, P29 |
| E14 | GET / | ADMIN, ENCARGADO | Lista empleados con filtros opcionales (q, activo, categoría) | P13 |
| E15 | GET /{id} | ADMIN, ENCARGADO | Detalle de un empleado. ADMIN ve `pinTerminal` y `email`; ENCARGADO los recibe a `null` (Opción A) | P14, P15 |
| E16 | PATCH /{id} | ADMIN, ENCARGADO | Actualiza campos parciales del empleado (PIN de terminal NO se modifica aquí — usar E65) | P15 |
| E17 | PATCH /{id}/baja | ADMIN, ENCARGADO | Da de baja lógica al empleado (activo=false). Conserva historial | — |
| E18 | PATCH /{id}/reactivar | ADMIN, ENCARGADO | Reactiva un empleado dado de baja | — |
| E19 | GET /estado | ADMIN, ENCARGADO | Resumen del estado de presencia de cada empleado | — |
| E20 | GET /export | ADMIN, ENCARGADO | Exporta el listado de empleados a CSV o PDF | — |
| E65 | POST /{id}/regenerar-pin | ADMIN, ENCARGADO | Regenera el PIN de terminal del empleado y lo devuelve en la respuesta. El PIN queda persistido; tras la regeneración solo es re-consultable por ADMIN vía E15 | P14 |
| E21 | GET /me | EMPLEADO, ENCARGADO | Perfil del empleado autenticado | P08 |

#### Fichajes (`/api/v1/fichajes`)

| E# | Verbo + Path | Roles | Descripción | Pantalla(s) |
|----|--------------|-------|-------------|--------------|
| E22 | POST / | ADMIN, ENCARGADO | Crea un fichaje manual con observaciones obligatorias | P20 |
| E23 | PATCH /{id} | ADMIN, ENCARGADO | Modifica un fichaje existente con observaciones obligatorias | P20 |
| E24 | GET / | ADMIN, ENCARGADO | Lista fichajes con filtros (empleado, rango de fechas, tipo) | P16 |
| E25 | GET /incompletos | ADMIN, ENCARGADO | Lista fichajes sin hora de salida (jornadas abiertas) | — |
| E26 | GET /me | EMPLEADO, ENCARGADO | Lista los fichajes del empleado autenticado en formato JSON | — |

#### Pausas (`/api/v1/pausas`)

| E# | Verbo + Path | Roles | Descripción | Pantalla(s) |
|----|--------------|-------|-------------|--------------|
| E27 | POST / | ADMIN, ENCARGADO | Registra una pausa manual sobre un fichaje | P20 |
| E28 | PATCH /{id} | ADMIN, ENCARGADO | Cierra o modifica una pausa existente | P20 |
| E29 | GET / | ADMIN, ENCARGADO | Lista pausas con filtros opcionales | P16 |
| E55 | GET /me | EMPLEADO, ENCARGADO | Lista las pausas del empleado autenticado en formato JSON | — |

#### Ausencias (`/api/v1/ausencias`)

| E# | Verbo + Path | Roles | Descripción | Pantalla(s) |
|----|--------------|-------|-------------|--------------|
| E30 | POST / | ADMIN, ENCARGADO | Planifica una ausencia individual o festivo global (empleadoId null) | P24 |
| E31 | PATCH /{id} | ADMIN, ENCARGADO | Modifica una ausencia planificada no procesada | P24 |
| E32 | DELETE /{id} | ADMIN, ENCARGADO | Elimina una ausencia planificada no procesada | P24 |
| E33 | GET / | ADMIN, ENCARGADO | Lista ausencias planificadas con filtros opcionales | P16 |
| E34 | GET /me | EMPLEADO, ENCARGADO | Lista las ausencias del empleado autenticado en formato JSON | — |
| E61 | GET /me/informe | EMPLEADO, ENCARGADO | Informe HTML de ausencias del empleado autenticado | P11 |
| E62 | GET /{empleadoId}/informe | ADMIN, ENCARGADO | Informe HTML de ausencias de un empleado concreto | P22 |
| E63 | POST /rango | ADMIN, ENCARGADO | Planifica un rango de ausencias en una sola llamada con detección de conflictos (`RangoConflictException` 409 con payload de fechas en conflicto si no se fuerza la sobrescritura) | P24 |
| E64 | GET /planificacion-vac-ap | ADMIN, ENCARGADO | Días pendientes de planificar para vacaciones y asuntos propios | P23, P24 |

#### Presencia (`/api/v1/presencia`)

| E# | Verbo + Path | Roles | Descripción | Pantalla(s) |
|----|--------------|-------|-------------|--------------|
| E35 | GET /parte-diario | ADMIN, ENCARGADO | Parte diario de presencia con 6 estados por empleado | P17 |
| E36 | GET /sin-justificar | ADMIN, ENCARGADO | Lista de empleados sin fichaje ni ausencia justificada en una fecha | P18 |
| E37 | GET /parte-diario/me | EMPLEADO, ENCARGADO | Estado de presencia del propio empleado en una fecha | P12 |

#### Saldos (`/api/v1/saldos`)

| E# | Verbo + Path | Roles | Descripción | Pantalla(s) |
|----|--------------|-------|-------------|--------------|
| E38 | GET /{empleadoId} | ADMIN, ENCARGADO | Saldo anual de un empleado concreto (vacaciones, AP, horas) | P25 |
| E39 | GET / | ADMIN, ENCARGADO | Lista de saldos anuales de todos los empleados activos en formato JSON | — |
| E40 | POST /{empleadoId}/recalcular | ADMIN | Fuerza el recálculo idempotente del saldo anual de un empleado | P24, P25 |
| E41 | GET /me | EMPLEADO, ENCARGADO | Saldo anual del empleado autenticado | P09 |

#### Informes HTML (`/api/v1/informes`)

| E# | Verbo + Path | Roles | Descripción | Pantalla(s) |
|----|--------------|-------|-------------|--------------|
| E42 | GET /horas/{empleadoId} | ADMIN, ENCARGADO | Informe HTML de horas trabajadas de un empleado en un rango | P21, P27 |
| E43 | GET /horas | ADMIN, ENCARGADO | Informe HTML de horas trabajadas globales en un rango | P27 |
| E44 | GET /saldos | ADMIN, ENCARGADO | Informe HTML de saldos anuales de todos los empleados | P26, P27 |
| E58 | GET /me/horas | EMPLEADO, ENCARGADO | Informe HTML de horas del empleado autenticado | P10 |
| E59 | GET /semana | ADMIN, ENCARGADO | Tabla HTML semanal interactiva con fichajes, pausas y ausencias | P19 |
| E60 | GET /ausencias | ADMIN, ENCARGADO | Tabla HTML interactiva de ausencias de todos los empleados en un rango | P23 |

#### PDF para firmar (`/api/v1/informes/pdf`)

| E# | Verbo + Path | Roles | Descripción | Pantalla(s) |
|----|--------------|-------|-------------|--------------|
| E45 | GET /horas/{empleadoId} | ADMIN, ENCARGADO | PDF firmable del informe de horas de un empleado (iText 7) | P27 |
| E46 | GET /horas | ADMIN, ENCARGADO | PDF firmable del informe de horas globales | P27 |
| E47 | GET /saldos | ADMIN, ENCARGADO | PDF firmable del informe de saldos anuales | P27 |
| E57 | GET /vacaciones | ADMIN, ENCARGADO | PDF firmable del informe de vacaciones y asuntos propios | P27 |

#### Terminal PIN (`/api/v1/terminal`)

| E# | Verbo + Path | Roles | Descripción | Pantalla(s) |
|----|--------------|-------|-------------|--------------|
| E48 | POST /entrada | público | Registra el inicio de jornada con PIN de 4 dígitos | P06 |
| E49 | POST /salida | público | Registra el fin de jornada con PIN | P06 |
| E50 | POST /pausa/iniciar | público | Inicia una pausa con PIN y tipo (comida, descanso, etc.) | P06 |
| E51 | POST /pausa/finalizar | público | Finaliza la pausa activa del empleado | P06 |
| E52 | POST /estado | público | Verifica el PIN y devuelve el estado actual del empleado para mostrar bienvenida | P01 |
| E53 | GET /bloqueo | ADMIN, ENCARGADO | Consulta si algún terminal está bloqueado por intentos fallidos | P17 |
| E54 | DELETE /bloqueo | ADMIN, ENCARGADO | Desbloquea el terminal tras un bloqueo por fuerza bruta | P17 |

#### Health (`/api/health`)

| E# | Verbo + Path | Roles | Descripción | Pantalla(s) |
|----|--------------|-------|-------------|--------------|
| E56 | GET /api/health | público | Health check para herramientas de monitorización (status: UP) | — |

> **Sobre la columna "Pantalla(s)"**: el guión (—) en pantalla indica que la app Android actual no consume ese endpoint. La API expone el contrato completo del dominio (operaciones de gestión avanzada, listados JSON para tablas nativas, monitorización externa); cada cliente que se conecte a futuro consumirá las capacidades que necesite. Esta separación es la base de la arquitectura desacoplada del proyecto: el backend no asume qué cliente lo invoca.

### Convención PUT / PATCH

- **PUT** → formulario completo (empresa, cambio de contraseña)
- **PATCH** → cambio de estado o campos parciales (baja, reactivar, modificar fichaje/pausa/ausencia)

---

## Modelo de datos

El sistema utiliza **7 tablas** relacionales:

| Tabla | Descripción |
|---|---|
| `configuracion_empresa` | Singleton (id=1). Nombre, CIF, logo. Aparece en cabeceras de PDFs. |
| `usuarios` | Autenticación y rol. Separada de empleados para permitir ADMIN sin jornada laboral. |
| `empleados` | Perfil laboral. PIN de terminal (UNIQUE), jornada diaria, vacaciones, categoría. |
| `fichajes` | Central. UNIQUE(empleado\_id, fecha). Sin DELETE (RD‑ley 8/2019). |
| `pausas` | Sin DELETE. `hora_fin = NULL` indica pausa activa. |
| `planificacion_ausencias` | `procesado = false` hasta que el proceso diario crea el fichaje correspondiente. |
| `saldos_anuales` | Calculado por SaldoService. UNIQUE(empleado\_id, anio). Recálculo idempotente. |

---

## Cumplimiento legal — RD‑ley 8/2019

| Obligación | Implementación |
|---|---|
| Registro diario con hora de inicio y fin | `UNIQUE(empleado_id, fecha)` en `fichajes` |
| Conservación mínima 4 años | Sin endpoint DELETE en `/fichajes` ni `/pausas` |
| Acceso de los trabajadores | RF‑51: el EMPLEADO consulta su historial en cualquier momento |
| Acceso para Inspección de Trabajo | RF‑38, RF‑39, RF‑40: PDFs firmables con iText 7 |
| Correcciones con trazabilidad | Modificación con campo `observaciones` obligatorio y no vacío |

---

## Estructura del repositorio

```
staffflow/
├── staffflow-backend/    # API REST — Spring Boot + Java 21
└── staffflow-android/    # App Android — Kotlin + Retrofit
```

### Ramas

- `main` → rama estable. Refleja siempre el último estado entregable del proyecto.
- `dev` → rama de desarrollo activo. Los cambios se integran primero aquí y se mergean a `main` cuando alcanzan el estado de entrega.

---

## Estado del proyecto

| Fase | Descripción | Estado |
|---|---|---|
| Fase 0 | Configuración del entorno y estructura base | ✅ Completada |
| Fase 1 | Análisis y diseño (requisitos, modelo de datos, API, wireframes) | ✅ Completada |
| Fase 2 | Desarrollo del backend (65 endpoints, JWT, iText 7) | ✅ Completada — 65/65 endpoints operativos |
| Fase 3 | Desarrollo de la app Android (30 pantallas, Kotlin, Navigation Component) | ✅ Completada — 30 pantallas en 6 bloques |
| Fase 4 | Testing | ✅ Completada — 61 tests unitarios (JUnit 5 + Mockito) + 1 test de arquitectura (ArchUnit) + 1 smoke test (@SpringBootTest) + matrix de seguridad 35/35 |
| Fase 5 | Documentación final | 🔄 En curso — memoria final en redacción |

**Entrega final:** 15 de junio de 2026 · 225 horas totales

---

## Decisiones de arquitectura

### 1. API REST desacoplada del cliente Android

La lógica de negocio reside íntegramente en el backend. La app Android solo consume la API REST. Esto permite añadir en el futuro otros clientes (web o escritorio) sin modificar el núcleo del sistema.

### 2. Separación entre usuarios y empleados

El modelo distingue entre `usuarios` (autenticación y rol) y `empleados` (perfil laboral). Un ADMIN tiene registro en `usuarios` pero no en `empleados`, ya que no tiene jornada laboral que registrar. ENCARGADO y EMPLEADO tienen registro en ambas tablas.

### 3. Bajas lógicas en lugar de borrado físico

Usuarios y empleados se desactivan con `activo = false`. El historial queda intacto y la integridad referencial se preserva. Fichajes y pausas nunca se eliminan (cumplimiento RD‑ley 8/2019): los errores se corrigen mediante modificación con campo `observaciones` obligatorio.

### 4. Terminal de fichaje con PIN separado del flujo JWT

Los 5 endpoints públicos de terminal (`/api/v1/terminal/entrada`, `/salida`, `/pausa/iniciar`, `/pausa/finalizar`, `/estado` — E48 a E52) no requieren JWT. Se identifican por PIN de 4 dígitos con bloqueo por fuerza bruta por dispositivo. Los 2 endpoints de gestión del bloqueo (`/terminal/bloqueo` GET y DELETE — E53 y E54) sí requieren JWT con rol ADMIN o ENCARGADO. El resto de la API (historial, saldos, perfil) requiere siempre JWT, garantizando que un PIN conocido por un compañero no permite acceder a datos personales.

### 5. Single Activity + Navigation Component en Android

La app Android usa una única `MainActivity` con `NavHostFragment`. Cada pantalla es un `Fragment`. Navigation Component gestiona el back stack automáticamente desde `nav_graph.xml`. El Navigation Drawer vive en `MainActivity` y se infla dinámicamente según el rol del JWT.

### 6. Catálogo de pantallas Android

Las 30 pantallas de la app Android se organizan en 6 bloques funcionales por rol:

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
| P15 | FormEmpleadoFragment | 3 — Gestión | E13, E15, E16 | ADMIN, ENCARGADO |
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
| P27 | InformesFragment | 4 — Encargado | E42–E47, E57 | ADMIN, ENCARGADO |
| P28 | UsuariosFragment | 5 — Admin | E09 | ADMIN |
| P29 | FormUsuarioFragment | 5 — Admin | E08–E13 | ADMIN |
| P30 | EmpresaFragment | 5 — Admin | E06, E07 | ADMIN |

Las 30 pantallas se numeran de forma continua P01–P30 sin huecos.

Las pantallas reutilizan patrones de Fragment cuando el comportamiento visual lo permite: el formulario de login (P02) sirve de base para P03, P04 y P05; las pantallas con WebView de informe (P10, P11, P19, P23, P26, P27) comparten el mismo esqueleto, y P21/P22 reutilizan literalmente los layouts de P10/P11 cambiando solo el endpoint que invocan. Esta estrategia redujo el tiempo de implementación de ~60–70 horas a ~30 horas sin impacto visible para el usuario.

### 7. Auto-detección de la URL del backend en Android

En el primer arranque la app sondea una lista de hosts candidatos hasta encontrar el backend activo, eliminando la necesidad de configurar manualmente la URL. La dirección detectada se persiste en `DataStore` y sobrevive a los cierres de sesión (`SessionManager.clear()` la preserva intencionalmente). Si la detección automática falla, el usuario puede introducir la URL manualmente desde la pantalla de configuración de la app.

---

## Endurecimiento de seguridad y robustez

Sobre la base funcional se aplicó una capa adicional de hardening centrada en seguridad y resiliencia:

- **Modelo de excepciones de dominio**: nueva clase `NotFoundException` (404) que reemplaza el uso indebido de `IllegalStateException` para casos "no encontrado". `IllegalStateException` queda reservada para errores internos genuinos (5xx).
- **Autorización por método**: activación de `@EnableMethodSecurity` con auditoría completa de las 54 anotaciones `@PreAuthorize` de la capa controller del proyecto. Las verificaciones de "ownership" (que un EMPLEADO solo acceda a sus propios datos) se delegan a la capa de servicio en lugar de SpEL inline, manteniendo la lógica testeable.
- **Externalización del secreto JWT**: eliminado del código y movido a la variable de entorno `JWT_SECRET`. En perfil `mysql` el arranque falla si la variable no está definida; en perfil `dev` existe un fallback claramente marcado como dev-only.
- **Estrategia de fetch JPA explícita**: todas las relaciones `@ManyToOne` y `@OneToOne` declaran `fetch = FetchType.LAZY` explícitamente. Las rutas de lectura que atraviesan asociaciones lazy están protegidas con `@Transactional(readOnly = true)` y `JOIN FETCH` para prevenir `LazyInitializationException`.
- **Cobertura de tests reforzada**: se añadieron `MethodSecurityConfigTest` (11 tests estructurales sobre las anotaciones `@PreAuthorize`) y `GlobalExceptionHandlerNotFoundTest` (3 tests sobre el remap del nuevo modelo de excepciones).

La trazabilidad completa del hardening (proposal, specs delta, design, tasks, verify report y archive report) vive en `openspec/changes/archive/2026-05-09-backend-hardening-high-issues/` siguiendo el flujo Spec-Driven Development. Los specs canónicos resultantes (`exception-domain-model`, `jpa-fetch-strategy`, `jwt-configuration`, `security-authorization`) están en `openspec/specs/`.

El endpoint E65 (`POST /empleados/{id}/regenerar-pin`) fue especificado e implementado siguiendo el mismo flujo SDD. Su trazabilidad completa (proposal, spec, design, tasks, verify report y archive report) vive en `openspec/changes/archive/2026-05-10-regenerar-pin-empleado/`.

---

## Autor

Santiago — Proyecto Final del Ciclo **Desarrollo de Aplicaciones Multiplataforma (DAM)** · iLERNA · 2025‑2026
