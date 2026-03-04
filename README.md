# StaffFlow
Sistema de control horario y gestión de RRHH para PYMEs, desarrollado como Proyecto Final del Grado Superior en Desarrollo de Aplicaciones Multiplataforma (DAM).

## Descripción
StaffFlow digitaliza y automatiza el control horario en pequeñas y medianas empresas, dando cumplimiento al Real Decreto-ley 8/2019 de registro obligatorio de jornada. El sistema cubre fichaje de empleados, gestión de ausencias, cálculo automático de saldos y generación de informes operativos.

## Stack tecnológico
**Backend:** Java · Spring Boot · JPA/Hibernate · MySQL · H2  
**Frontend:** Kotlin · Android nativo · Retrofit · Material Design  
**Herramientas:** Git · IntelliJ IDEA · Android Studio · Postman

## Arquitectura
API REST stateless con separación de capas (controller–service–repository) y DTOs. Diseño desacoplado preparado para escalar con futuros clientes (desktop JavaFX, app móvil para empleados) sin modificar el backend.

## Estructura del repositorio
```
staffflow/
├── staffflow-backend/     # API REST — Spring Boot + Java 21
└── staffflow-android/     # App Android — Kotlin + Retrofit
```

## Estado del proyecto
| Fase | Descripción | Estado |
|------|-------------|--------|
| Fase 0 | Configuración del entorno y estructura base | ✅ Completada |
| Fase 1 | Análisis y diseño | 🔄 En progreso |
| Fase 2 | Autenticación JWT y gestión de usuarios | ⏳ Pendiente |
| Fase 3 | Fichaje y pausas | ⏳ Pendiente |
| Fase 4 | Ausencias y saldos | ⏳ Pendiente |
| Fase 5 | Informes y cierre | ⏳ Pendiente |

🚧 **En desarrollo** — Proyecto Final de Ciclo (DAM · iLERNA · 2025-2026)
