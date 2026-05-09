# StaffFlow

Sistema de control horario y gestión de ausencias para pequeñas y medianas empresas, desarrollado como **Proyecto Final del Grado Superior en Desarrollo de Aplicaciones Multiplataforma (DAM)**.

StaffFlow digitaliza el registro de jornada laboral y la gestión de ausencias en Pymes, cumpliendo con el **Real Decreto‑ley 8/2019**, que obliga a registrar diariamente el horario de trabajo de los empleados.

El proyecto se compone de:

- **Backend:** API REST desarrollada con Java 21 y Spring Boot 3.5
- **Cliente móvil:** aplicación Android nativa en Kotlin
- **Arquitectura desacoplada** que permite futuros clientes (web o escritorio)

---

## Descripción

> Proyecto completamente implementado y verificado. El backend cuenta con 64 endpoints operativos: autenticación JWT completa, gestión de contraseñas con recuperación por contraseña temporal vía email, configuración de empresa, gestión de usuarios y empleados, fichajes, pausas, terminal PIN, ausencias planificadas, presencia en tiempo real, saldos anuales, proceso nocturno automático de cierre de jornada, informes HTML/JSON y PDFs firmables con iText 7. La app Android tiene 30 pantallas implementadas en 6 bloques: terminal PIN/NFC, login, dashboards por rol, gestión de fichajes, pausas, ausencias, saldos, informes y PDFs. Testing completo: 52 tests unitarios + 1 test de arquitectura (JUnit 5 + Mockito + ArchUnit) y smoke test de los endpoints operativos contra MySQL 8.0. Verificación funcional completa con MySQL 8.0 y H2.

El sistema permite a una empresa gestionar el registro horario de sus empleados mediante:

- Fichaje de entrada y salida (desde app o terminal con PIN/NFC)
- Registro y gestión de pausas durante la jornada
- Planificación de ausencias (vacaciones, permisos, festivos nacionales y locales)
- Cálculo automático de saldos de horas y días disponibles
- Parte diario de presencia con 5 estados posibles por empleado
- Generación de informes operativos y PDFs firmables

La arquitectura separa completamente **backend y cliente**, permitiendo que múltiples aplicaciones consuman la misma API REST.

---

## Funcionalidades principales

- Autenticación con JWT (12h) y control de acceso por roles (ADMIN, ENCARGADO, EMPLEADO). El JWT no afecta al fichaje, que siempre se realiza por PIN. Afecta a la app de gestión: el ENCARGADO hace login una vez al día y el token persiste en DataStore, evitando reautenticaciones mientras dure la jornada. Un token más corto obligaría a hacer login repetidamente cada vez que se consulta o gestiona algo. La solución para combinar tokens cortos con buena usabilidad es el refresh token, documentado como mejora para v2.0
- Registro de jornada laboral mediante fichaje de entrada y salida
- Terminal de fichaje con PIN de 4 dígitos y NFC para dispositivo compartido (sin JWT)
- Gestión de pausas durante la jornada
- Planificación de ausencias individuales y festivos globales
- Proceso diario automático que convierte ausencias planificadas en fichajes
- Cálculo de saldo anual: vacaciones, asuntos propios y saldo de horas
- Parte diario de presencia (Fichado · En pausa · Ausencia registrada · Ausencia planificada · Sin justificar)
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
- JUnit 5 + Mockito (52 tests unitarios) + ArchUnit 1.4.0 (1 test de arquitectura)

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
staffflow-backend/src/main/resources/staffflow_v7_ddl_mysql.sql
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

---

## Arquitectura

StaffFlow utiliza una **arquitectura en capas (Layered Architecture)**:

```
Request → Controller → Service → Repository → Entity → Response
```

Características principales:

- API REST **stateless** con autenticación **JWT**
- Control de acceso basado en **roles** con Spring Security (`@PreAuthorize`)
- Roles acumulativos: EMPLEADO ⊂ ENCARGADO ⊂ ADMIN
- Separación entre **entidades de dominio y DTOs** (nunca se exponen entidades directamente)
- Persistencia mediante **JPA / Hibernate**
- Sin stored procedures ni triggers: toda la lógica de negocio en la capa service

La API usa versionado `/api/v1/` en todos los endpoints salvo `/api/health`.

---

## Diseño de la API

La API se ha definido con enfoque **design‑first**: todos los endpoints están especificados antes de implementar la lógica de negocio.

La especificación incluye:

- **64 endpoints** en **13 grupos funcionales**
- Control de acceso por roles en cada endpoint
- Terminal de fichaje con PIN/NFC en ruta separada `/api/v1/terminal/` (sin JWT, cadena de seguridad propia)
- Bloqueo por fuerza bruta: 5 intentos fallidos de PIN → bloqueo 30 s + HTTP 423

### Grupos de endpoints

| Grupo | Ruta base | Endpoints | Estado |
|---|---|---|---|
| Auth | `/api/v1/auth` | E01–E05 | ✅ Operativos |
| Empresa | `/api/v1/empresa` | E06–E07 | ✅ Operativos |
| Usuarios | `/api/v1/usuarios` | E08–E12 | ✅ Operativos |
| Empleados | `/api/v1/empleados` | E13–E21 | ✅ Operativos |
| Fichajes | `/api/v1/fichajes` | E22–E26 | ✅ Operativos |
| Pausas | `/api/v1/pausas` | E27–E29, E55 | ✅ Operativos |
| Ausencias | `/api/v1/ausencias` | E30–E34, E61–E64 | ✅ Operativos |
| Presencia | `/api/v1/presencia` | E35–E37 | ✅ Operativos |
| Saldos | `/api/v1/saldos` | E38–E41 | ✅ Operativos |
| Informes HTML | `/api/v1/informes` | E42–E44, E58–E60 | ✅ Operativos |
| PDF para firmar | `/api/v1/informes/pdf` | E45–E47, E57 | ✅ Operativos |
| Terminal PIN/NFC | `/api/v1/terminal` | E48–E54 | ✅ Operativos |
| Health | `/api/health` | E56 | ✅ Operativo |

**64 endpoints operativos** (verificados con MySQL 8.0 y H2).

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

- `main` → única rama del repositorio. Refleja el estado entregable del proyecto.

---

## Estado del proyecto

| Fase | Descripción | Estado |
|---|---|---|
| Fase 0 | Configuración del entorno y estructura base | ✅ Completada |
| Fase 1 | Análisis y diseño (requisitos, modelo de datos, API, wireframes) | ✅ Completada |
| Fase 2 | Desarrollo del backend (64 endpoints, JWT, iText 7) | ✅ Completada — 64/64 endpoints operativos |
| Fase 3 | Desarrollo de la app Android (30 pantallas, Kotlin, Navigation Component) | ✅ Completada — 30 pantallas en 6 bloques |
| Fase 4 | Testing | ✅ Completada — 52 tests unitarios (JUnit 5 + Mockito) + 1 test de arquitectura (ArchUnit) + smoke test de endpoints + matrix de seguridad 35/35 |
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
| P08 | MiPerfilFragment | 2 — Empleado | E21 | EMPLEADO+ |
| P09 | MiSaldoFragment | 2 — Empleado | E41 | EMPLEADO+ |
| P10 | MisFichajesFragment | 2 — Empleado | E58 | EMPLEADO+ |
| P11 | MisAusenciasFragment | 2 — Empleado | E61 | EMPLEADO+ |
| P12 | MiHoyFragment | 2 — Empleado | E37 | EMPLEADO+ |
| P13 | EmpleadosFragment | 3 — Gestión | E14 | ENCARGADO+ |
| P14 | DetalleEmpleadoFragment | 3 — Gestión | E15 | ENCARGADO+ |
| P15 | FormEmpleadoFragment | 3 — Gestión | E13, E15, E16 | ENCARGADO+ |
| P16 | DetalleDiaFragment | 4 — Encargado | E24, E29, E33 | ENCARGADO+ |
| P17 | ParteDiarioFragment | 4 — Encargado | E35, E53, E54 | ENCARGADO+ |
| P18 | SinJustificarFragment | 4 — Encargado | E36 | ENCARGADO+ |
| P19 | ResumenSemanalFragment | 4 — Encargado | E59 | ENCARGADO+ |
| P20 | FormFichajeFragment | 4 — Encargado | E22, E23, E27, E28, E40 | ENCARGADO+ |
| P21 | InformeFichajesEmpleadoFragment | 4 — Encargado | E42 | ENCARGADO+ |
| P22 | InformeAusenciasEmpleadoFragment | 4 — Encargado | E62 | ENCARGADO+ |
| P23 | AusenciasFragment | 4 — Encargado | E60, E64 | ENCARGADO+ |
| P24 | FormAusenciaFragment | 4 — Encargado | E30, E31, E32, E40, E63, E64 | ENCARGADO+ |
| P26 | SaldoFragment | 4 — Encargado | E38, E40 | ENCARGADO+ |
| P27 | SaldosGlobalesFragment | 4 — Encargado | E44 | ENCARGADO+ |
| P28 | InformesFragment | 4 — Encargado | E14, E42–E47, E57 | ENCARGADO+ |
| P31 | UsuariosFragment | 5 — Admin | E09 | ADMIN |
| P32 | FormUsuarioFragment | 5 — Admin | E08–E13 | ADMIN |
| P34 | EmpresaFragment | 5 — Admin | E06, E07 | ADMIN |

Los huecos de numeración (P25, P29, P30, P33) reflejan la agrupación por bloques funcionales heredada del diseño original.

Las pantallas reutilizan patrones de Fragment cuando el comportamiento visual lo permite: el formulario de login (P02) sirve de base para P03, P04 y P05; la pantalla de detalle (P14) define el patrón aplicado a otros detalles; las pantallas con WebView de informe (P10, P11, P19, P22, P23, P27, P28) comparten el mismo esqueleto. Esta estrategia redujo el tiempo de implementación de ~60–70 horas a ~30 horas sin impacto visible para el usuario.

---

## Endurecimiento de seguridad y robustez

Sobre la base funcional se aplicó una capa adicional de hardening centrada en seguridad y resiliencia:

- **Modelo de excepciones de dominio**: nueva clase `NotFoundException` (404) que reemplaza el uso indebido de `IllegalStateException` para casos "no encontrado". `IllegalStateException` queda reservada para errores internos genuinos (5xx).
- **Autorización por método**: activación de `@EnableMethodSecurity` con auditoría completa de las 54 anotaciones `@PreAuthorize` del proyecto. Las verificaciones de "ownership" (que un EMPLEADO solo acceda a sus propios datos) se delegan a la capa de servicio en lugar de SpEL inline, manteniendo la lógica testeable.
- **Externalización del secreto JWT**: eliminado del código y movido a la variable de entorno `JWT_SECRET`. En perfil `mysql` el arranque falla si la variable no está definida; en perfil `dev` existe un fallback claramente marcado como dev-only.
- **Estrategia de fetch JPA explícita**: todas las relaciones `@ManyToOne` y `@OneToOne` declaran `fetch = FetchType.LAZY` explícitamente. Las rutas de lectura que atraviesan asociaciones lazy están protegidas con `@Transactional(readOnly = true)` y `JOIN FETCH` para prevenir `LazyInitializationException`.
- **Cobertura de tests reforzada**: se añadieron `MethodSecurityConfigTest` (11 tests estructurales sobre las anotaciones `@PreAuthorize`) y `GlobalExceptionHandlerNotFoundTest` (3 tests sobre el remap del nuevo modelo de excepciones).

---

## Autor

Santiago — Proyecto Final del Ciclo **Desarrollo de Aplicaciones Multiplataforma (DAM)** · iLERNA · 2025‑2026
