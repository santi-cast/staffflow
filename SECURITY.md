# Política de seguridad

## Versiones con soporte

| Versión | Soporte |
|---|---|
| 1.0.x | Sí |
| < 1.0 | No |

## Cómo reportar una vulnerabilidad

No abra un issue público para problemas de seguridad. Abra un aviso de seguridad privado en GitHub (**Security → Report a vulnerability**) en el repositorio `santi-cast/staffflow`, indicando el componente afectado (backend o app Android), pasos para reproducir y el impacto que estima.

Se responderá en un plazo razonable. StaffFlow es un proyecto mantenido por una sola persona, sin equipo de seguridad ni compromiso de tiempos de respuesta; se agradece la divulgación responsable y se dará crédito en el aviso si el reportante lo desea.

## Limitaciones de seguridad conocidas

StaffFlow nació como proyecto académico y está pensado para desplegarse en la red local de una pyme. Las siguientes limitaciones están identificadas, se publican como issues en GitHub y no deben reportarse de nuevo salvo que se aporte un vector distinto.

### Abiertas

- **PINs de terminal en claro**: `empleados.pin_terminal` es un `CHAR(4)` sin hash, con búsqueda directa por PIN. Una lectura de la base de datos equivale a un volcado de las credenciales del terminal.
- **Bloqueo por fuerza bruta en memoria y con clave elegida por el cliente**: el contador de intentos fallidos de PIN se guarda en un `ConcurrentHashMap` indexado por el `dispositivoId` que envía el propio cliente; rotar el identificador reinicia el contador y un reinicio del backend borra todos los bloqueos (roadmap 2.5).
- **La app Android registra los cuerpos HTTP completos**: `HttpLoggingInterceptor.Level.BODY` sin guarda de `BuildConfig.DEBUG`, por lo que JWT y PIN pueden acabar en logcat también en compilaciones release.
- **Tráfico HTTP en claro**: el manifiesto declara `usesCleartextTraffic="true"` y la app construye la URL del servidor con esquema `http` y puerto `8080` fijos; no puede hablar HTTPS sin recompilar (roadmap 2.12).
- **JWT en DataStore sin cifrar**: el token de sesión (12 h de validez) se persiste en `DataStore Preferences` en claro.
- **`terminal_service` como punto único de fallo (M-042)**: el cierre nocturno y el terminal localizan al usuario de sistema por `username` y nada en el código protege esa fila. Si falta o se renombra, el cierre nocturno falla con rollback completo y el terminal deja de poder firmar fichajes. Además, en el perfil `mysql` no se crea automáticamente (roadmap 2.11).
- Sin migraciones de esquema (Flyway/Liquibase) y sin tests automatizados en Android más allá de `ApiErrorMapperTest`.

### Mitigaciones recomendadas para quien despliega hoy

- Mantenga el backend en la red local, sin exponer el puerto `8080` a Internet. Si necesita acceso remoto, use una VPN.
- Si expone el servicio fuera de la LAN, hágalo detrás de un proxy inverso con TLS que termine el cifrado y restrinja el origen; tenga en cuenta que la app v1 seguirá hablando HTTP en su tramo.
- Restrinja el acceso a la base de datos MySQL (usuario dedicado, sin acceso remoto) y proteja sus copias de seguridad: contienen los PIN en claro.
- Use las compilaciones de la app solo en dispositivos controlados por la empresa (terminal compartido, tablet del encargado) y evite dar acceso a logcat a terceros.
- Trate `JWT_SECRET` como un secreto: al menos 32 bytes, generado aleatoriamente y fuera del repositorio; rotarlo invalida todas las sesiones.
- Cambie la contraseña del ADMIN inicial en el primer acceso y no elimine ni renombre `terminal_service` (ver [`docs/despliegue.md`](docs/despliegue.md)).

### Corregidas

- **`JWT_SECRET` no fallaba al arrancar** (perfil `mysql`): la configuración entregada usaba `${JWT_SECRET:?mensaje}`, sintaxis de bash que Spring interpreta como valor por defecto, de modo que sin la variable el backend arrancaba firmando tokens con el propio texto del mensaje. Ahora es `${JWT_SECRET}` sin valor por defecto y el arranque sin la variable termina con `Could not resolve placeholder 'JWT_SECRET'`.
- **Credenciales MySQL por defecto**: `application-mysql.yml` traía `username: root` y contraseña vacía. Ahora `DB_USERNAME` y `DB_PASSWORD` son obligatorias y sin valor por defecto; `DB_URL` es opcional.
- **E13 abierto a ENCARGADO**: el alta de perfiles de empleado (`POST /api/v1/empleados`) admitía `hasAnyRole('ADMIN', 'ENCARGADO')` aunque el diseño lo reserva al ADMIN. Ahora es `hasRole('ADMIN')` en el controller, en el test estructural de seguridad y en la spec `openspec/specs/security-authorization`.
