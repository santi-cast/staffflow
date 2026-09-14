# Guía de despliegue

Esta guía explica cómo poner StaffFlow en marcha en producción: backend con MySQL y la app Android conectada por red local. Está pensada para una pequeña empresa o para un desarrollador que instala el sistema por primera vez. Para ejecutar el proyecto en modo desarrollo (H2 en memoria, datos de demostración) basta con la sección [Probar la demo en 5 minutos](../README.md#probar-la-demo-en-5-minutos) del README.

## Índice

- [Requisitos](#requisitos)
- [Perfiles de ejecución](#perfiles-de-ejecución)
- [Base de datos](#base-de-datos)
- [Datos iniciales obligatorios (perfil mysql)](#datos-iniciales-obligatorios-perfil-mysql)
- [Arranque del backend](#arranque-del-backend)
- [Correo (SMTP)](#correo-smtp)
- [App Android](#app-android)
- [Comprobación](#comprobación)

## Requisitos

| Componente | Requisito |
|---|---|
| Backend | Java 21 LTS (Temurin recomendado). El JAR es autocontenido; no hace falta Maven instalado para ejecutarlo. |
| Base de datos | MySQL 8.0. El DDL canónico está escrito para esta versión (InnoDB, `utf8mb4`). |
| Cliente | Android 7.0 Nougat (API 24) o superior, con unos 50 MB libres. |
| Red | El backend debe ser accesible desde los dispositivos Android por la red local en el puerto `8080` (HTTP). |

Para construir la imagen Docker no se necesita Java en el host: el `Dockerfile` compila el JAR en una etapa intermedia.

## Perfiles de ejecución

El backend tiene dos perfiles Spring. Sin indicar nada se activa `dev` (fijado en `application.yaml`).

| | `dev` | `mysql` |
|---|---|---|
| Base de datos | H2 en memoria (`MODE=MySQL`), esquema recreado en cada arranque (`ddl-auto: create-drop`) | MySQL 8.0 externo, esquema validado en cada arranque (`ddl-auto: validate`) |
| Datos | `data.sql` se carga siempre: 1 empresa, 5 usuarios, 3 empleados con PIN | Ninguno. La base de datos empieza vacía (ver [Datos iniciales](#datos-iniciales-obligatorios-perfil-mysql)) |
| Credenciales | `admin001` / `admin1234` (ADMIN), `usu001` (ENCARGADO, PIN 3333), `usu002` (EMPLEADO, PIN 1111), `usu003` (EMPLEADO, PIN 2222); todas con contraseña `admin1234` | Las que cree el operador |
| Secreto JWT | Valor de respaldo marcado como solo desarrollo | `JWT_SECRET` obligatoria |
| Extras | Consola H2 en `/h2-console` y endpoint `POST /api/v1/test/cierre-diario` para disparar el cierre nocturno a mano | No se registran |

### Variables de entorno del perfil `mysql`

| Variable | Obligatoria | Descripción |
|---|---|---|
| `DB_USERNAME` | Sí | Usuario de MySQL. No tiene valor por defecto: sin ella la conexión falla al arrancar (`Access denied`). |
| `DB_PASSWORD` | Sí | Contraseña de MySQL. Sin valor por defecto. |
| `JWT_SECRET` | Sí | Clave de firma de los tokens. Sin valor por defecto: el arranque termina con `Could not resolve placeholder 'JWT_SECRET'`. |
| `DB_URL` | No | URL JDBC. Por defecto `jdbc:mysql://localhost:3306/staffflow?useSSL=false&serverTimezone=Europe/Madrid&allowPublicKeyRetrieval=true&sslMode=DISABLED`. |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Ver [Correo](#correo-smtp) | Credenciales SMTP. En este perfil no tienen valor por defecto. |

Las variables `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD` también funcionan gracias al enlace relajado de Spring Boot y tienen prioridad sobre el archivo, pero la forma documentada es `DB_*`.

### Generar un `JWT_SECRET` robusto

`JwtTokenProvider` deriva la clave HMAC con `Keys.hmacShaKeyFor` a partir de los bytes UTF-8 del secreto. El mínimo que acepta son **32 bytes (256 bits)**; con 32–47 bytes firma con HS256, con 48–63 con HS384 y con 64 o más con HS512. Un valor adecuado:

```bash
openssl rand -base64 64
```

Genera 88 caracteres (más de 64 bytes), así que los tokens se firman con HS512. Guarde el valor fuera del repositorio (gestor de secretos, archivo de entorno con permisos restringidos). Cambiarlo invalida todas las sesiones abiertas.

## Base de datos

1. Cree una base de datos vacía (por ejemplo `staffflow`) y un usuario con permisos sobre ella.
2. Ejecute el DDL canónico [`../staffflow-backend/docs/StaffFlowDDL.sql`](../staffflow-backend/docs/StaffFlowDDL.sql), que crea las 7 tablas con sus índices, claves foráneas y restricciones `CHECK`:

   ```bash
   mysql -u root -p staffflow < staffflow-backend/docs/StaffFlowDDL.sql
   ```

3. No modifique el esquema a mano. El perfil `mysql` arranca con `spring.jpa.hibernate.ddl-auto: validate`: si las tablas no coinciden con las entidades JPA, el backend no arranca. No hay herramienta de migraciones (Flyway/Liquibase); la evolución del esquema es manual.

La zona horaria de la conexión es `Europe/Madrid` (fijada en `DB_URL` y en `hibernate.jdbc.time_zone`).

## Datos iniciales obligatorios (perfil mysql)

En el perfil `mysql` nada siembra la base de datos. Antes del primer arranque hay que insertar dos filas en `usuarios`:

1. **`terminal_service`**: usuario de sistema que firma todos los registros generados automáticamente. `ProcesoCierreDiario` (cierre nocturno a las 23:55) y `TerminalService` (fichajes por PIN) lo buscan por `username` exacto. Si no existe, el cierre nocturno falla con `NotFoundException` y hace rollback completo (ningún fichaje automático ese día), y el terminal no puede registrar fichajes.
2. **Un usuario ADMIN** con el que entrar por primera vez en la app y crear el resto (empresa, usuarios, empleados).

El script siguiente respeta las columnas `NOT NULL` de la tabla `usuarios` (`username`, `password_hash`, `email`, `rol`, `activo`, `fecha_creacion`). Los hashes BCrypt son los mismos que usa `data.sql` en el perfil `dev`:

```sql
-- Usuario de sistema. Nunca se usa para iniciar sesión.
INSERT INTO usuarios (username, password_hash, email, rol, activo, fecha_creacion)
VALUES ('terminal_service',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lmuS',
        'terminal@staffflow.internal',
        'ADMIN', 1, NOW());

-- Administrador inicial. Contraseña: admin1234 (cámbiela en el primer acceso).
INSERT INTO usuarios (username, password_hash, email, rol, activo, fecha_creacion)
VALUES ('admin001',
        '$2a$10$HaOeyYyuQOjcaNZ/zkhOsu/2f.SYeFK3G1XCfWXVAftuRHvKUb9eW',
        'admin@su-empresa.es',
        'ADMIN', 1, NOW());
```

Indicaciones:

- Sustituya `admin@su-empresa.es` por un correo real: es la dirección a la que llegaría una recuperación de contraseña (E04). `email` y `username` son `UNIQUE`.
- La contraseña de `admin001` es la de demostración (`admin1234`). **Cámbiela inmediatamente después del primer inicio de sesión** desde el menú lateral, opción "Cambiar contraseña".
- **No borre ni renombre `terminal_service`.** La búsqueda es por nombre literal y no hay ninguna protección en el código sobre esa fila (limitación M-042, ver [`../SECURITY.md`](../SECURITY.md)). Desactivarlo (`activo = 0`) es inocuo para el sistema, porque la búsqueda por `username` ignora `activo`; además impide que esa cuenta pueda iniciar sesión. El único efecto es que los registros automáticos quedan firmados por un usuario inactivo.
- La contraseña que corresponde al hash de `terminal_service` no está documentada y la cuenta no está pensada para iniciar sesión.
- El registro de `configuracion_empresa` (datos de la empresa para cabeceras e informes) no hace falta insertarlo por SQL: se crea desde la app con el ADMIN en "Mi empresa" (E07). Orden de puesta en marcha recomendado: **Mi empresa → Usuarios → Empleados**.

La creación automática de `terminal_service` y del ADMIN inicial a partir de variables de entorno está prevista en el [roadmap 2.11](roadmap.md#211-inicialización-automática-del-perfil-mysql).

## Arranque del backend

### JAR ejecutable

Construya el JAR (o descargue `staffflow-backend-jar.zip` de la [Release v1.0.0](https://github.com/santi-cast/staffflow/releases/tag/v1.0.0)):

```bash
cd staffflow-backend
./mvnw package -DskipTests
# genera target/staffflow-backend-0.0.1-SNAPSHOT.jar
```

Arranque con el perfil `mysql` y las variables definidas en el entorno:

```bash
export DB_URL='jdbc:mysql://localhost:3306/staffflow?useSSL=false&serverTimezone=Europe/Madrid&allowPublicKeyRetrieval=true&sslMode=DISABLED'
export DB_USERNAME=staffflow_user
export DB_PASSWORD='su-contraseña'
export JWT_SECRET="$(openssl rand -base64 64)"   # guárdelo: debe ser el mismo en cada arranque
export MAIL_USERNAME=''                            # ver sección Correo
export MAIL_PASSWORD=''

java -jar staffflow-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=mysql
```

En Windows use `set VARIABLE=valor` en lugar de `export`. El servidor escucha en el puerto `8080`.

### Docker

El directorio [`instalacion-backend-docker/`](instalacion-backend-docker/) contiene un `Dockerfile` multietapa (compila con Maven sobre Temurin 21 y ejecuta sobre JRE Alpine) y un `docker-compose.yml`. La imagen **se construye desde el código fuente** y no contiene secretos; todas las variables se pasan en tiempo de ejecución. Desde la raíz del repositorio:

```bash
SPRING_PROFILES_ACTIVE=mysql \
DB_URL='jdbc:mysql://host-mysql:3306/staffflow?useSSL=false&serverTimezone=Europe/Madrid&allowPublicKeyRetrieval=true&sslMode=DISABLED' \
DB_USERNAME=staffflow_user DB_PASSWORD='su-contraseña' \
JWT_SECRET='su-secreto' MAIL_USERNAME='' MAIL_PASSWORD='' \
docker compose -f docs/instalacion-backend-docker/docker-compose.yml up --build
```

El `compose` publica el puerto `8080`, reinicia el contenedor salvo parada manual (`restart: unless-stopped`) y, sin `SPRING_PROFILES_ACTIVE`, arranca en `dev`. MySQL no forma parte del `compose`: debe existir fuera del contenedor. Si `DB_URL` no se exporta, el `compose` usa por defecto `jdbc:mysql://host.docker.internal:3306/staffflow?...`, que apunta al MySQL de la máquina anfitriona (el `compose` declara `extra_hosts: host.docker.internal:host-gateway` para que también resuelva en Docker Engine para Linux, Docker 20.10 o superior). Exporte `DB_URL` solo si MySQL está en otra máquina; nunca use `localhost`, que dentro del contenedor es el propio contenedor. Este flujo Docker está pendiente de validación en la máquina del mantenedor.

## Correo (SMTP)

El correo solo se usa en E04 (solicitar recuperación de contraseña), que envía una contraseña temporal al email registrado del usuario. El resto de la aplicación no depende del SMTP.

La configuración fija el servidor `smtp.gmail.com`, puerto `587` con STARTTLS, y toma las credenciales de dos variables:

| Variable | `dev` | `mysql` |
|---|---|---|
| `MAIL_USERNAME` | Opcional; respaldo `demo@staffflow.local` | Sin valor por defecto. **Debe estar definida** (puede estar vacía): `EmailService` la lee a través de `staffflow.mail.from` con `@Value`, y un marcador sin resolver impide el arranque. |
| `MAIL_PASSWORD` | Opcional; respaldo `demo-password` | Sin valor por defecto. Defínala junto a la anterior. |

Sin credenciales reales la aplicación funciona con normalidad, salvo que E04 falla al intentar enviar el correo. Para activar el envío hace falta un **App Password de Gmail** (no la contraseña de la cuenta): active la verificación en dos pasos, genere una contraseña de aplicación en `https://myaccount.google.com/apppasswords` y use la cadena de 16 caracteres sin espacios. Para usar otro proveedor SMTP hay que editar `host`/`port` en `application-mysql.yml`; la configuración desde la app está prevista en el roadmap (2.10).

## App Android

1. Descargue `staffflow-android-apk.zip` de la [Release v1.0.0](https://github.com/santi-cast/staffflow/releases/tag/v1.0.0), copie el APK al dispositivo e instálelo (hay que permitir la instalación de orígenes desconocidos).
2. En el primer arranque la app sondea `http://10.0.2.2:8080` y `http://127.0.0.1:8080` (emulador y demo en la misma tablet). En una instalación real ninguno responde, así que el primer intento de fichar o de iniciar sesión termina en el diálogo **"No se pudo conectar al servidor"**, que pide la IP del servidor.
3. Introduzca solo la IP (por ejemplo `192.168.1.20`), pulse "Probar conexión" (abre un socket al puerto `8080`) y después "Guardar y continuar". La app guarda `http://<ip>:8080/api/v1/` en DataStore; el esquema (`http`) y el puerto (`8080`) son fijos y no se pueden cambiar desde la app. La URL sobrevive a los cierres de sesión; para cambiarla hay que provocar de nuevo el diálogo (por ejemplo, con el servidor apagado) o reinstalar la app. Un ajuste dedicado está previsto en el roadmap (2.12).
4. El dispositivo y el servidor deben estar en la misma red local, con el puerto `8080` accesible (revise el cortafuegos del servidor).

El detalle de uso está en el [manual de usuario, sección 0](manual-usuario.md#0-instalación-y-primer-acceso).

## Comprobación

```bash
curl http://<ip-servidor>:8080/api/health
# {"status":"UP"}
```

Swagger UI queda disponible en `http://<ip-servidor>:8080/swagger-ui.html` en ambos perfiles (`SecurityConfig` deja `/swagger-ui/**` y `/v3/api-docs/**` sin autenticación y no hay ninguna propiedad que lo desactive en `mysql`). Los endpoints protegidos siguen exigiendo JWT, pero si no quiere exponer la documentación de la API, bloquee esas rutas en el proxy inverso. La consola H2 (`/h2-console`) solo existe en `dev`.

Verifique también que el cierre nocturno puede ejecutarse: tras la primera noche (23:55) debe aparecer en el log `ProcesoCierreDiario iniciado para la fecha ...` sin errores. Si registra `Usuario 'terminal_service' no encontrado en BD`, revise la sección de [datos iniciales](#datos-iniciales-obligatorios-perfil-mysql).

### Recomendaciones para producción

- Exponga el backend solo en la red local o detrás de un proxy inverso con TLS; la app usa HTTP en claro y no puede cambiarse a HTTPS sin recompilar.
- Haga copias de seguridad periódicas de MySQL: la conservación de cuatro años de registros que exige el RD-ley 8/2019 depende de ellas.
- Consulte [`../SECURITY.md`](../SECURITY.md) para las limitaciones de seguridad conocidas.
