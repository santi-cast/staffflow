# Roadmap v2.0

Roadmap de evolución de StaffFlow después de la versión 1.0. **Ninguna de estas líneas forma parte del alcance entregado en v1** (15 de junio de 2026); recogen las continuaciones naturales del proyecto, ordenadas por sus dependencias internas. El estado actual del sistema se describe en el [README](../README.md) y el análisis técnico en el [informe técnico](informe-tecnico.md).

## 2.1 App móvil para empleado

Aplicación Android dedicada al rol EMPLEADO (separada de la app actual, que queda como herramienta multi-rol para ADMIN/ENCARGADO/EMPLEADO en terminal compartido). Concentra todo lo que el empleado necesita en autoservicio:

- Fichar entrada y salida desde el móvil con GPS opcional y geofencing del centro de trabajo.
- Solicitar vacaciones y ausencias, con flujo de aprobación o rechazo gestionado por el ENCARGADO desde la app de escritorio (ver 2.3).
- Consultar fichajes propios y su historial completo.
- Recibir y descargar nóminas (depende de 2.7 para la integración automática; sin ella, requiere subida manual por el ADMIN).
- Ver horarios próximos asignados por el ENCARGADO (depende de 2.2 para el modelo de turnos).
- Consultar saldos de vacaciones y asuntos propios en tiempo real.

## 2.2 Planificación de horarios laborales

Modelo de turnos en el backend y endpoints asociados. No existe en v1: el sistema actual asume una jornada diaria fija por empleado calculada a partir de la jornada semanal contractual. Es prerrequisito para las funciones de horario de 2.1 y 2.3.

## 2.3 App de escritorio para ENCARGADO

Aplicación de escritorio orientada a la productividad del rol ENCARGADO, separada de la app Android actual. Concentra las tareas de gestión que hoy se hacen en la app móvil multi-rol:

- Consulta intensiva de datos (informes, presencia, saldos, listados).
- Planificación de horarios laborales (depende de 2.2).
- Aprobación o rechazo de solicitudes de vacaciones y ausencias enviadas por los empleados desde 2.1.

## 2.4 Refresh tokens

Reemplazar el JWT actual (un único token de 12 h) por el patrón estándar **access token corto (15–30 min) + refresh token largo**. Reduce la ventana de exposición ante un token filtrado y permite revocación efectiva sin invalidar sesiones legítimas.

## 2.5 Bloqueo de PIN persistente

Mover el contador de intentos fallidos del terminal (hoy en un `ConcurrentHashMap` en memoria del proceso) a un campo persistente en la entidad `Empleado`. El estado actual se pierde con cada reinicio del backend y no escala si la aplicación corre en más de una instancia.

## 2.6 Multilocal y multitenant

Cubre dos casos de uso reales con el mismo modelo de dos niveles jerárquicos (empresa → local). El **caso pyme multilocal** es un único empresario con varios centros de trabajo (por ejemplo, un propietario con tres bares): cada local tiene su terminal, su plantilla y sus turnos, pero los saldos y nóminas se consolidan al nivel de la empresa. El **caso SaaS multi-empresa** es la instancia única del backend dando servicio a varias organizaciones independientes con aislamiento de datos garantizado. Requiere añadir la tabla `configuracion_local` (un registro por centro de trabajo), vincular cada entidad del dominio a un local, vincular el local a una empresa, y propagar empresa y local resueltos a través de la cadena de seguridad.

## 2.7 Integración con sistemas de nóminas

Conectores con sistemas de nóminas habituales en pymes españolas (A3 Nóminas, Sage Despachos, etc.) para exportar las horas fichadas y los saldos consolidados sin pasos manuales intermedios. Habilita el punto de nóminas de 2.1.

## 2.8 Recuperación de contraseña por token de un solo uso

Reemplazar el mecanismo actual de recuperación (E04 genera una contraseña temporal de 8 caracteres y la envía al email registrado) por un token de un solo uso con caducidad corta enviado como enlace. El usuario hace clic, fija una contraseña nueva en una pantalla dedicada y el token queda invalidado tras el uso o tras expirar. El patrón actual obliga al usuario a memorizar o copiar una contraseña aleatoria y deja esa contraseña viva en la bandeja de entrada hasta que el usuario la cambie manualmente. El endpoint E05 y las columnas `reset_token`/`reset_token_expiry` ya existen como contrato preparado.

## 2.9 Copias de seguridad y restauración desde el rol ADMIN

Funcionalidad nativa accesible desde la aplicación de gestión que permita al ADMIN generar copias completas de la base de datos y restaurarlas posteriormente sin requerir acceso SSH al servidor ni utilidades externas como `mysqldump`. Cubre el caso de uso típico de las pymes que se autoalojan: el responsable funcional puede salvaguardar y recuperar el estado del sistema con una interfaz amigable, sin depender de un perfil técnico. La copia incluye el esquema y los datos; la restauración valida la versión del esquema antes de aplicar para evitar incompatibilidades.

## 2.10 Configuración SMTP desde el rol ADMIN

StaffFlow ya aplica el principio "configuración dinámica vía interfaz, no estática vía ficheros" en la app Android: la URL del backend se detecta de forma automática entre dos hosts (`10.0.2.2` para emulador y `127.0.0.1` para demo en la misma tablet) y, si la detección falla, se puede fijar desde el diálogo de recuperación que muestra la propia aplicación (no desde una pantalla de ajustes permanente, ver 2.12), persistida en DataStore, sin recompilar el APK ni editar ficheros del servidor. La versión 2.0 extiende este principio al backend para la configuración del servidor de correo saliente. Hoy las credenciales SMTP (`MAIL_USERNAME`, `MAIL_PASSWORD`, host, puerto) viven en `application-dev.yml` y `application-mysql.yml` como variables de entorno, lo que obliga a reiniciar el backend para rotarlas y expone al operador a editar ficheros del servidor. La v2.0 las mueve a la tabla `configuracion_empresa` (singleton, id=1) con cuatro columnas nuevas: `smtp_host`, `smtp_puerto`, `smtp_usuario` y `smtp_password_cifrada`. El rol ADMIN gestiona estos campos desde la pantalla "Mi empresa" de la app Android, con un botón "Probar conexión" que envía un correo de prueba antes de guardar. La contraseña se almacena cifrada en la base de datos (AES-GCM con clave maestra en variable de entorno) y nunca viaja al cliente. Beneficios: rotación de credenciales sin parada, paridad con el patrón ya usado para la URL del backend en Android, separación clara entre operador funcional (ADMIN desde la app) y administrador del servidor, y preparación natural para el escenario multilocal y multitenant de 2.6 (cada empresa con sus propias credenciales SMTP).

## 2.11 Inicialización automática del perfil mysql

Hoy el perfil `mysql` arranca sobre una base de datos vacía y nada crea las dos filas sin las que el sistema no funciona: el usuario de sistema `terminal_service` (autor de los fichajes automáticos del cierre nocturno y del terminal) y un ADMIN inicial. El operador debe insertarlas a mano con el SQL documentado en [`despliegue.md`](despliegue.md#datos-iniciales-obligatorios-perfil-mysql). La v2.0 añade un bootstrap en el arranque que, si la tabla `usuarios` está vacía, crea `terminal_service` y el ADMIN inicial a partir de variables de entorno (`ADMIN_USERNAME`, `ADMIN_PASSWORD`, `ADMIN_EMAIL` o equivalentes), y que además protege la fila `terminal_service` frente a desactivación o renombrado (limitación M-042).

## 2.12 Configuración de la URL del servidor desde ajustes

La app Android solo permite fijar la dirección del backend desde el diálogo que aparece cuando falla la conexión ("No se pudo conectar al servidor"), y con esquema y puerto fijos (`http://<ip>:8080/api/v1/`). Cambiar de servidor una vez conectado exige provocar de nuevo ese fallo o reinstalar la app, y no es posible usar HTTPS ni otro puerto. La v2.0 añade una pantalla de ajustes con la URL completa editable (esquema, host y puerto), prueba de conexión contra `GET /api/health` y persistencia en DataStore, y hace posible desplegar el backend detrás de un proxy inverso con TLS.

## 2.13 Cierre con recuperación y terminal offline

Hoy el cierre nocturno (`ProcesoCierreDiario`, 23:55) solo procesa el día en curso. Si el backend está apagado a esa hora, no se pierde ningún fichaje (cada marcaje del terminal se persiste en el momento y el cierre nunca modifica filas existentes), pero los empleados que no ficharon ese día se quedan sin su `AUSENCIA_INJUSTIFICADA` y el ADMIN debe crearla a mano con E22; las planificaciones pendientes y los saldos sí se recuperan en la siguiente ejecución. La v2.0 hace que el proceso cierre todos los días pendientes desde el último cierre registrado hasta ayer, aprovechando que ya es idempotente. En el mismo bloque, la app Android incorpora una cola local de marcajes (Room o DataStore) para el terminal: si el servidor no responde durante la jornada, el PIN se guarda con su hora original y se reenvía al recuperar la conexión, en lugar de perderse, que es hoy el riesgo real de una caída en horario de trabajo.
