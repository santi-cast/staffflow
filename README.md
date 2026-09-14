# StaffFlow

Registro horario y gestión de ausencias para pymes, con cumplimiento del RD-ley 8/2019, despliegue self-hosted y fichaje por PIN en una tablet compartida.

![Java](https://img.shields.io/badge/Java-21_LTS-orange)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-green)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1-purple)
[![CI](https://github.com/santi-cast/staffflow/actions/workflows/ci.yml/badge.svg)](https://github.com/santi-cast/staffflow/actions/workflows/ci.yml)
![License](https://img.shields.io/badge/license-MIT-blue)

| Terminal de fichaje por PIN | Parte diario del encargado |
|---|---|
| ![Terminal de fichaje por PIN](docs/img/terminal-pin.png) | ![Parte diario](docs/img/parte-diario.png) |

## El problema

El RD-ley 8/2019 obliga a todas las empresas españolas a registrar la jornada diaria de cada trabajador y a conservar los registros durante cuatro años. Muchas pymes lo resuelven con papel u hojas de cálculo; las suites HR comerciales se pagan por empleado, operan solo en la nube y no contemplan el fichaje en hardware estándar.

## Qué hace

StaffFlow es un sistema autoalojado, sin licencia por usuario y centrado en el control horario de una pyme (no es una suite de talento completa). Se compone de un backend REST (Java 21, Spring Boot 3.5) y una app Android (Kotlin) que funciona como terminal de fichaje en una tablet en modo kiosco y como app de gestión por rol.

- Fichajes de entrada, salida y pausas por PIN de 4 dígitos en un terminal compartido.
- Planificación de ausencias (vacaciones, asuntos propios, permisos, festivos) y saldos anuales calculados automáticamente.
- Parte diario de presencia con 6 estados por empleado.
- Informes de horas y ausencias en HTML y en PDF firmable (iText), listos para la Inspección de Trabajo.
- Correcciones manuales con autor y motivo obligatorios; los fichajes nunca se borran.

## Ingeniería destacada

- **Permisos por tiempo, no solo por rol**: el encargado corrige fichajes y pausas solo del día en curso y planifica ausencias de hoy en adelante; los días ya cerrados solo los toca el ADMIN, con observaciones obligatorias.
- **Modelo de PIN**: PIN aleatorio, único por empleado y visible una sola vez; bloqueo del terminal por dispositivo tras 5 intentos fallidos (HTTP 423) hasta que un encargado lo desbloquea.
- **Cierre nocturno idempotente** (23:55): cierra el día de cada empleado, materializa las ausencias planificadas y recalcula saldos en una única transacción repetible.
- **Parte diario como proyección**: los 6 estados se calculan en tiempo real sobre tres tablas y nunca se persisten, por lo que no pueden desincronizarse.
- **Anti-enumeración**: la recuperación de contraseña y el login responden igual exista o no la cuenta (RNF-S04).
- **Trazabilidad**: cada corrección manual guarda quién y por qué; usuarios y empleados se dan de baja lógica y los fichajes no admiten DELETE.
- **Limitaciones de seguridad documentadas** (PIN en claro en base de datos, bloqueo en memoria, HTTP en claro en la app): ver [`SECURITY.md`](SECURITY.md).

## Cómo funciona por rol

| Rol | Qué hace | Dónde |
|---|---|---|
| EMPLEADO | Ficha por PIN; consulta su perfil, fichajes, ausencias y saldo (endpoints `/me`) | Terminal y app |
| ENCARGADO | Parte diario, corrección de fichajes y pausas del día, planificación de ausencias, informes, desbloqueo del terminal | App |
| ADMIN | Configuración de empresa, usuarios y empleados, recálculo de saldos, corrección de días ya cerrados | App |

Los roles son un reparto por módulo, no una jerarquía: el ADMIN no tiene ficha de empleado y no ficha. Detalle en el [manual de usuario](docs/manual-usuario.md).

## Probar la demo en 5 minutos

Requisitos: JDK 21 LTS. El Maven wrapper viene incluido en el repositorio.

```bash
git clone https://github.com/santi-cast/staffflow.git
cd staffflow/staffflow-backend
./mvnw spring-boot:run            # perfil dev: H2 en memoria y datos de demostración
curl http://localhost:8080/api/health
# {"status":"UP"}
```

Swagger UI: `http://localhost:8080/swagger-ui.html`. Consola H2: `http://localhost:8080/h2-console`.

App Android: descargue `staffflow-android-apk.zip` de la [Release v1.0.0](https://github.com/santi-cast/staffflow/releases/tag/v1.0.0), instale el APK (Android 7.0 o superior) e indique la IP del servidor cuando la app la solicite. Como alternativa, abra `staffflow-android/` en Android Studio y ejecute la app sobre un emulador.

Credenciales de demostración (perfil `dev`):

| Usuario | Contraseña | Rol | PIN terminal |
|---|---|---|---|
| `admin001` | `admin1234` | ADMIN | — |
| `usu001` | `admin1234` | ENCARGADO | 3333 (Laura) |
| `usu002` | `admin1234` | EMPLEADO | 1111 (Ana) |
| `usu003` | `admin1234` | EMPLEADO | 2222 (Carlos) |

Tests del backend:

```bash
./mvnw test                       # 341 tests unitarios y de controlador; sin contexto de Spring ni base de datos
```

## Despliegue en producción

El perfil `mysql` conecta con MySQL 8.0 (esquema creado con [`StaffFlowDDL.sql`](staffflow-backend/docs/StaffFlowDDL.sql) y validado en cada arranque) y toma la configuración sensible del entorno: `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `MAIL_USERNAME` y `MAIL_PASSWORD` deben estar definidas (las de correo pueden ir vacías) o el backend no arranca. Se puede ejecutar como JAR o con el Dockerfile de `docs/instalacion-backend-docker/`. Procedimiento completo, incluido el SQL inicial obligatorio, en [`docs/despliegue.md`](docs/despliegue.md).

## Arquitectura

- **Backend en capas** (`controller → service → domain`) sobre Spring Boot y JPA/Hibernate, con API REST stateless versionada en `/api/v1/` y autenticación JWT. Las entidades nunca cruzan la capa de servicio: la API solo expone DTOs.
- **Autorización matricial por rol**: ADMIN gestiona empresa y usuarios, ENCARGADO la operativa diaria y EMPLEADO solo sus datos vía `/me`; protegida mediante reglas de URL y 57 anotaciones `@PreAuthorize`.
- **Terminal PIN aislado**: vive en una cadena de seguridad propia, separada del flujo JWT, con bloqueo por dispositivo.
- **341 tests de backend sin contexto de Spring** (Mockito, MockMvc standalone, reflexión sobre `@PreAuthorize`) y una regla ArchUnit que rompe el build si un servicio lanza `IllegalStateException`. Son tests unitarios y de controlador aislados. No hay pruebas de integración con Spring y MySQL; el perfil `mysql` valida al arrancar que las entidades JPA coincidan con el esquema, pero esta comprobación no sustituye una suite de integración.
- **App Android** Single Activity + Navigation Component con MVVM; los informes tabulares se renderizan en el servidor y se muestran en un WebView con deep links `staffflow://` hacia formularios nativos.

Más detalle en el [informe técnico](docs/informe-tecnico.md) y en las [decisiones de diseño](docs/decisiones.md).

## Limitaciones conocidas

- Semana laboral fija de lunes a viernes; no hay planificación de turnos.
- El fichaje es solo por PIN. El modelo de datos ya contempla la tarjeta NFC (`codigo_nfc`) para v2, pero no es una funcionalidad operativa en v1.
- No existe flujo de solicitud de ausencias por parte del empleado: las planifica el encargado.
- Las variables SMTP (`MAIL_USERNAME`, `MAIL_PASSWORD`) deben estar definidas en el perfil `mysql`; sin credenciales reales solo falla el envío del correo de recuperación.
- El perfil `mysql` no siembra la base de datos: hay que insertar a mano `terminal_service` y el ADMIN inicial con el SQL de [`docs/despliegue.md`](docs/despliegue.md#datos-iniciales-obligatorios-perfil-mysql).
- El terminal no funciona sin conexión: un PIN marcado con el servidor caído no se registra (los fichajes ya hechos no se pierden). El cierre nocturno tampoco recupera un día en el que no llegó a ejecutarse; las ausencias de ese día las añade administración a mano. Ver [`docs/roadmap.md`](docs/roadmap.md#213-cierre-con-recuperación-y-terminal-offline).

## Roadmap

La v2 prevé una app móvil de empleado (fichaje con GPS y solicitudes de ausencia con aprobación), modelo de turnos, refresh tokens, bloqueo de PIN persistente, multilocal/multitenant, integración con nóminas y configuración SMTP desde la app. Detalle y dependencias en [`docs/roadmap.md`](docs/roadmap.md).

## Documentación

| Documento | Contenido |
|---|---|
| [`docs/manual-usuario.md`](docs/manual-usuario.md) | Manual de usuario por rol: terminal, empleado, encargado, administrador |
| [`docs/informe-tecnico.md`](docs/informe-tecnico.md) | Informe técnico: arquitectura, dominio, seguridad, testing, proceso |
| [`docs/decisiones.md`](docs/decisiones.md) | Decisiones de diseño con su porqué y las 9 ADR |
| [`docs/api.md`](docs/api.md) | Catálogo de los 68 endpoints y las 30 pantallas |
| [`docs/despliegue.md`](docs/despliegue.md) | Guía de despliegue: perfiles, variables, MySQL, Docker, SMTP |
| [`docs/roadmap.md`](docs/roadmap.md) | Roadmap v2 |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | Cómo compilar, probar y proponer cambios |
| [`SECURITY.md`](SECURITY.md) | Política de seguridad y limitaciones conocidas |
| [`openspec/README.md`](openspec/README.md) | Especificaciones Given/When/Then y flujo Spec-Driven Development |
| [`staffflow-backend/docs/StaffFlowDDL.sql`](staffflow-backend/docs/StaffFlowDDL.sql) | DDL canónico de las 7 tablas |

## Estructura del repositorio

```text
staffflow/
├── staffflow-backend/     # API REST — Spring Boot + Java 21 (docs/StaffFlowDDL.sql)
├── staffflow-android/     # App Android — Kotlin + Retrofit
├── docs/                  # Manual, informe técnico, decisiones, API, despliegue, roadmap, img/
├── openspec/              # Especificaciones SDD y ciclos archivados
└── .github/workflows/     # CI: tests del backend y build del APK
```

Ramas: `main` (estable) y `dev` (desarrollo).

## Contribuir

Las contribuciones son bienvenidas: errores y propuestas en los [issues](https://github.com/santi-cast/staffflow/issues); las limitaciones abiertas de `SECURITY.md` son un buen punto de partida. La suite del backend debe quedar en verde y los commits siguen Conventional Commits. Detalles, incluidos los códigos `E`/`P`/`RF`/`M`/`ADR` que aparecen en el código y la documentación, en [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Origen del proyecto

StaffFlow nació como Proyecto Final del Grado Superior en Desarrollo de Aplicaciones Multiplataforma (DAM), entregado en junio de 2026, y fue diseñado y desarrollado individualmente de principio a fin: modelo de dominio, backend, app Android, seguridad, pruebas y documentación. Desde entonces ha evolucionado hacia un producto de código abierto para pymes: revisión de seguridad posterior a la entrega, integración continua, guía de despliegue y documentación reorganizada. El [informe técnico](docs/informe-tecnico.md) recoge el análisis completo del sistema.

## Licencia

Este proyecto se distribuye bajo licencia MIT. Ver [LICENSE](./LICENSE) para el texto completo.

## Autor

**Santiago Castillo**

- GitHub: [@santi-cast](https://github.com/santi-cast)
