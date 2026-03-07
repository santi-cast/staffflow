# StaffFlow

Sistema de control horario y gestión de ausencias para pequeñas y medianas empresas, desarrollado como **Proyecto Final del Grado Superior en Desarrollo de Aplicaciones Multiplataforma (DAM)**.

StaffFlow digitaliza el registro de jornada laboral y la gestión de ausencias en PYMEs, cumpliendo con el **Real Decreto‑ley 8/2019**, que obliga a registrar diariamente el horario de trabajo de los empleados.

El proyecto se compone de:

- **Backend:** API REST desarrollada con Java 21 y Spring Boot 3.5
- **Cliente móvil:** aplicación Android nativa en Kotlin
- **Arquitectura desacoplada** que permite futuros clientes (web o escritorio)

---

## Descripción

> Actualmente se ha completado la fase de análisis y diseño; las funcionalidades descritas corresponden al alcance funcional definido para la implementación.

El sistema permite a una empresa gestionar el registro horario de sus empleados mediante:

- Fichaje de entrada y salida (desde app o terminal con PIN)
- Registro y gestión de pausas durante la jornada
- Planificación de ausencias (vacaciones, permisos, festivos nacionales y locales)
- Cálculo automático de saldos de horas y días disponibles
- Parte diario de presencia con 5 estados posibles por empleado
- Generación de informes operativos y PDFs firmables

La arquitectura separa completamente **backend y cliente**, permitiendo que múltiples aplicaciones consuman la misma API REST.

---

## Funcionalidades principales

- Autenticación con JWT (8h) y control de acceso por roles (ADMIN, ENCARGADO, EMPLEADO)
- Registro de jornada laboral mediante fichaje de entrada y salida
- Terminal de fichaje con PIN de 4 dígitos para dispositivo compartido (sin JWT)
- Gestión de pausas durante la jornada
- Planificación de ausencias individuales y festivos globales
- Proceso diario automático que convierte ausencias planificadas en fichajes
- Cálculo de saldo anual: vacaciones, asuntos propios y saldo de horas
- Parte diario de presencia (Fichado · En pausa · Ausencia registrada · Ausencia planificada · Sin justificar)
- Informes operativos de horas trabajadas y ausencias en JSON y HTML imprimible
- Generación de informes PDF firmables con iText 7 (mensual, anual, vacaciones)
- Recuperación de contraseña por email con token de un solo uso (30 min)

---

## Stack tecnológico

### Backend

- Java 21 LTS (Temurin)
- Spring Boot 3.5.x
- Maven
- JPA / Hibernate
- MySQL 8.0 (producción) · H2 (desarrollo)
- jjwt 0.12.6
- SpringDoc OpenAPI (Swagger UI)
- Lombok
- spring-boot-starter-mail
- iText 7 (informes PDF firmables)
- JUnit + JaCoCo (cobertura ≥ 70% en capa service)

### Cliente Android

- Kotlin
- AGP 9.0.1
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

- **52 endpoints** en **13 grupos funcionales**
- Control de acceso por roles en cada endpoint
- Terminal de fichaje con PIN en ruta separada `/api/v1/terminal/` (sin JWT, cadena de seguridad propia)
- Bloqueo por fuerza bruta: 5 intentos fallidos de PIN → bloqueo 30 s + HTTP 423

### Grupos de endpoints

| Grupo | Ruta base | Endpoints |
|---|---|---|
| Auth | `/api/v1/auth` | E01–E05 |
| Empresa | `/api/v1/empresa` | E06–E07 |
| Usuarios | `/api/v1/usuarios` | E08–E12 |
| Empleados | `/api/v1/empleados` | E13–E21 |
| Fichajes | `/api/v1/fichajes` | E22–E26 |
| Pausas | `/api/v1/pausas` | E27–E29 |
| Ausencias | `/api/v1/ausencias` | E30–E34 |
| Presencia | `/api/v1/presencia` | E35–E37 |
| Saldos | `/api/v1/saldos` | E38–E41 |
| Informes | `/api/v1/informes` | E42–E44 |
| PDF firmables | `/api/v1/informes/pdf` | E45–E47 |
| Terminal PIN | `/api/v1/terminal` | E48–E51 |
| Health | `/api/health` | E52 |

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

- `master` → código estable. Solo recibe merges al cerrar cada fase.
- `develop` → trabajo diario.

### Estado actual del repositorio

```
master  → b2af932  feat: initial project structure - fase 0
develop → db03d55  feat: add health check endpoint
```

---

## Estado actual

El proyecto tiene completados los siguientes entregables de análisis y diseño:

- Requisitos funcionales (49 RF) y no funcionales (29 RNF)
- Modelo de datos relacional (7 tablas, script DDL MySQL validado)
- Especificación completa de la API REST (52 endpoints, design-first)
- Wireframes Android (24 pantallas, 8 flujos de navegación)

La siguiente fase es la implementación del backend con Spring Boot.

---

## Estado del proyecto

| Fase | Descripción | Estado |
|---|---|---|
| Fase 0 | Configuración del entorno y estructura base | ✅ Completada |
| Fase 1 | Análisis y diseño (requisitos, modelo de datos, API, wireframes) | ✅ Completada |
| Fase 2 | Desarrollo del backend (52 endpoints, JWT, iText 7) | ⏳ Pendiente |
| Fase 3 | Desarrollo de la app Android (Kotlin, Navigation Component, MVVM) | ⏳ Pendiente |
| Fase 4 | Testing | ⏳ Pendiente |
| Fase 5 | Documentación final | ⏳ Pendiente |

**Entrega final:** 22 de abril de 2026 · 225 horas totales

---

## Decisiones de arquitectura

### 1. API REST desacoplada del cliente Android

La lógica de negocio reside íntegramente en el backend. La app Android solo consume la API REST. Esto permite añadir en el futuro otros clientes (web, escritorio) sin modificar el núcleo del sistema.

### 2. Separación entre usuarios y empleados

El modelo distingue entre `usuarios` (autenticación y rol) y `empleados` (perfil laboral). Un ADMIN tiene registro en `usuarios` pero no en `empleados`, ya que no tiene jornada laboral que registrar. ENCARGADO y EMPLEADO tienen registro en ambas tablas.

### 3. Bajas lógicas en lugar de borrado físico

Usuarios y empleados se desactivan con `activo = false`. El historial queda intacto y la integridad referencial se preserva. Fichajes y pausas nunca se eliminan (cumplimiento RD‑ley 8/2019): los errores se corrigen mediante modificación con campo `observaciones` obligatorio.

### 4. Terminal de fichaje con PIN separado del flujo JWT

Los 4 endpoints de terminal (`/api/v1/terminal/`) no requieren JWT. Se identifican por PIN de 4 dígitos con bloqueo por fuerza bruta por dispositivo. El resto de la API (historial, saldos, perfil) requiere siempre JWT, garantizando que un PIN conocido por un compañero no permite acceder a datos personales.

### 5. Single Activity + Navigation Component en Android

La app Android usa una única `MainActivity` con `NavHostFragment`. Cada pantalla es un `Fragment`. Navigation Component gestiona el back stack automáticamente desde `nav_graph.xml`. El Navigation Drawer vive en `MainActivity` y se infla dinámicamente según el rol del JWT.

---

## Autor

Santiago — Proyecto Final del Ciclo **Desarrollo de Aplicaciones Multiplataforma (DAM)** · iLERNA · 2025‑2026
