# JWT Configuration Specification

## Purpose

Externalizar el secreto JWT a una variable de entorno para eliminar la credencial
del repositorio de código. El perfil de desarrollo mantiene un fallback conveniente;
el perfil de producción (mysql) falla rápido si `JWT_SECRET` no está definida.

## Requirements

### Requirement: JWT secret read from environment variable

El sistema MUST leer el secreto JWT desde la variable de entorno `JWT_SECRET` en todos
los perfiles de configuración. El literal hardcoded actual DEBE eliminarse de los
archivos YAML versionados.

Valor actual a eliminar (comprometido, requiere rotación):
`staffflow-secret-key-para-desarrollo-minimo-32-caracteres`

#### Scenario: aplicación carga el secreto desde entorno

- GIVEN la variable de entorno `JWT_SECRET=mi-secreto-seguro-de-al-menos-32-caracteres`
- WHEN la aplicación arranca con cualquier perfil
- THEN `JwtTokenProvider` usa ese valor como secreto de firma
- AND el log de arranque no revela el valor del secreto

---

### Requirement: application-dev.yml falls back to dev-only literal

El sistema SHOULD proveer un valor de fallback en `application-dev.yml` para que los
desarrolladores puedan arrancar sin definir `JWT_SECRET` localmente.

El valor de fallback DEBE:
- Ser diferente al valor comprometido actual
- Estar claramente marcado como dev-only en un comentario inline
- Tener al menos 32 caracteres (requisito mínimo de HS384)

Formato en YAML: `secret: ${JWT_SECRET:staffflow-dev-only-secret-no-usar-en-prod-ok}`

#### Scenario: arranque dev sin JWT_SECRET definida

- GIVEN perfil `dev` activo y `JWT_SECRET` no definida en el entorno
- WHEN la aplicación arranca
- THEN arranca correctamente usando el valor de fallback
- AND el log contiene un WARNING indicando que se usa el secreto de desarrollo

#### Scenario: arranque dev con JWT_SECRET definida

- GIVEN perfil `dev` activo y `JWT_SECRET=valor-custom` definida en el entorno
- WHEN la aplicación arranca
- THEN usa `valor-custom` (env var tiene precedencia sobre fallback)

---

### Requirement: application-mysql.yml fails fast if JWT_SECRET unset

El sistema MUST fallar en el arranque si `JWT_SECRET` no está definida cuando se usa
el perfil `mysql`. No hay fallback en producción.

Formato en YAML: `secret: ${JWT_SECRET:?JWT_SECRET must be set for mysql profile}`

#### Scenario: arranque mysql sin JWT_SECRET

- GIVEN perfil `mysql` activo y `JWT_SECRET` no definida
- WHEN la aplicación intenta arrancar
- THEN el proceso falla con un error claro antes de conectarse a la base de datos
- AND el mensaje de error indica que `JWT_SECRET` es requerida

#### Scenario: arranque mysql con JWT_SECRET definida

- GIVEN perfil `mysql` activo y `JWT_SECRET=secreto-produccion` definida
- WHEN la aplicación arranca
- THEN arranca correctamente sin errores de configuración

---

### Requirement: leaked secret documented as rotation risk

El sistema DEBE documentar en el `CHANGELOG` o en las release notes del change que el
secreto literal actual (`staffflow-secret-key-para-desarrollo-minimo-32-caracteres`)
está comprometido por su presencia en la historia de git.

Nota de alcance: el scrubbing de la historia de git (BFG Repo-Cleaner / `git filter-repo`)
queda FUERA del scope de este change. Es un riesgo conocido y aceptado que DEBE quedar
documentado para tratarse como tarea de seguimiento.

#### Scenario: riesgo documentado

- GIVEN el archivo `openspec/changes/backend-hardening-high-issues/specs/jwt-configuration/spec.md`
- WHEN el equipo revisa el change
- THEN el riesgo de secreto en historial de git es visible y tiene un plan de seguimiento documentado

---

### Requirement: developer setup documentation exists

El sistema SHOULD tener un archivo `.env.example` en la raíz del proyecto (o en
`staffflow-backend/`) que documente las variables de entorno necesarias para arrancar
localmente.

#### Scenario: .env.example contiene JWT_SECRET

- GIVEN el archivo `.env.example` en el repositorio
- WHEN un desarrollador nuevo clona el proyecto
- THEN ve `JWT_SECRET=` con instrucciones sobre el formato requerido
- AND puede copiar `.env.example` a `.env` y completar el valor sin consultar documentación adicional
