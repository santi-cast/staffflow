# Guía de contribución

Gracias por el interés en StaffFlow. Esta guía resume cómo compilar, probar y proponer cambios.

## Preparar el entorno

- Backend: JDK 21 LTS. El Maven wrapper (`./mvnw`) viene incluido; no hace falta Maven global.
- Android: Android Studio Panda 1 o superior, o el Gradle wrapper desde línea de comandos.
- Para trabajar sobre el backend basta el perfil `dev` (H2 en memoria, datos de `data.sql`). Ver [Probar la demo en 5 minutos](README.md#probar-la-demo-en-5-minutos).

## Tests del backend

```bash
cd staffflow-backend
./mvnw test
```

La suite tiene 341 tests (291 unitarios de servicio con Mockito, 30 estructurales de seguridad por reflexión, 10 de JWT, 9 del `GlobalExceptionHandler` con MockMvc standalone y 1 de ArchUnit). Ninguno arranca el contexto de Spring ni necesita base de datos, así que la ejecución completa es rápida y determinista. Cualquier cambio en el backend debe dejar la suite en verde.

### Guarda arquitectónica

`src/test/java/com/staffflow/architecture/ServiceLayerArchitectureTest.java` (ArchUnit) impide que las clases de `com.staffflow.service..` dependan de `IllegalStateException`, salvo las incluidas en una lista blanca justificada en su Javadoc (hoy solo `PdfService`). Para "no encontrado" se usa `NotFoundException` (404) y para conflictos `ConflictException` (409); `IllegalStateException` se reserva para errores internos genuinos (5xx). Si el test falla, la solución es usar la excepción de dominio adecuada, no ampliar la lista blanca.

## Compilar la app Android

```bash
cd staffflow-android
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/`. La app no tiene suite de tests más allá de `ApiErrorMapperTest`.

## Integración continua

`.github/workflows/ci.yml` se ejecuta en cada push y pull request a `main` con dos trabajos independientes sobre Temurin 21: `./mvnw -B test` en `staffflow-backend/` y `./gradlew assembleDebug --no-daemon` en `staffflow-android/`. Un pull request debe pasar ambos.

## Ramas y commits

- `main` es la rama estable; `dev` la de desarrollo. Los cambios se integran primero en `dev` y se fusionan a `main` cuando están listos para entregar.
- Los mensajes de commit siguen [Conventional Commits](https://www.conventionalcommits.org/) en español, en imperativo y sin punto final: `feat: anadir despliegue con Docker en docs/instalacion-backend-docker`, `fix: aclarar que E04 es el unico endpoint que dispara SMTP en v1.0`, `docs(readme): añadir Spring Initializr al listado de herramientas`, `chore: sacar docs/ del repo`. El ámbito entre paréntesis es opcional. Los merges a `main` usan `Merge branch 'dev' into main: <resumen>`.
- Sin atribución a herramientas de IA en los commits (ver `openspec/AI_USAGE.md`).

## Convenciones de nomenclatura

Los códigos cortos que aparecen en el README, los tests y la documentación son:

| Código | Significado |
|---|---|
| `E01`–`E68` | Endpoint REST (por ejemplo, E01 = `POST /api/v1/auth/login`). Catálogo en [`docs/api.md`](docs/api.md). |
| `P01`–`P30` | Pantalla Android (Fragment). Catálogo en [`docs/api.md`](docs/api.md#catálogo-de-pantallas-android-p01-p30). |
| `RF-XX` | Requisito funcional. |
| `RNF-S`, `RNF-L`, `RNF-I` | Requisito no funcional de seguridad, legal (RD-ley 8/2019) o de integridad. |
| `M-XXX` | Deuda técnica o gap conocido (por ejemplo, M-042: dependencia de `terminal_service`). |
| `ADR` | Decisión de arquitectura; las principales están numeradas en [`docs/decisiones.md`](docs/decisiones.md#decisiones-de-arquitectura-adr). |
| `SDD` | Spec-Driven Development: cada cambio nace de una especificación versionada en `openspec/`. |

Al añadir un endpoint o una pantalla, asigne el siguiente código libre y actualice el catálogo de `docs/api.md`.

## Flujo Spec-Driven Development

Los cambios de alcance en el backend siguen el flujo SDD documentado en [`openspec/README.md`](openspec/README.md): cada cambio pasa por explore → propose → spec → design → tasks → apply → verify → archive, y deja sus artefactos en `openspec/changes/<cambio>/` (propuesta, specs delta, diseño, tareas, informe de verificación). Al archivarse, las specs delta se fusionan en `openspec/specs/`, que es la fuente de verdad del comportamiento esperado. Para una corrección pequeña no hace falta abrir un ciclo SDD, pero sí mantener coherente la spec afectada si cambia un contrato (por ejemplo, los roles de un endpoint en `openspec/specs/security-authorization/spec.md`).

## Reportar problemas

- Errores y propuestas: abra un [issue en GitHub](https://github.com/santi-cast/staffflow/issues) con pasos para reproducir, perfil usado (`dev` o `mysql`) y versión.
- Vulnerabilidades: no abra un issue público; siga el procedimiento de [`SECURITY.md`](SECURITY.md).
