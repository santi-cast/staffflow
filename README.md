# StaffFlow

Sistema de control horario y gestión de ausencias para pequeñas y
medianas empresas, desarrollado como **Proyecto Final del Grado Superior
en Desarrollo de Aplicaciones Multiplataforma (DAM)**.

StaffFlow tiene como objetivo digitalizar el registro de jornada laboral
y la gestión de ausencias en PYMEs, cumpliendo con el **Real Decreto‑ley
8/2019**, que obliga a registrar diariamente el horario de trabajo de
los empleados.

El proyecto se compone de:

-   **Backend:** API REST desarrollada con Java 21 y Spring Boot
-   **Cliente móvil:** aplicación Android nativa en Kotlin
-   **Arquitectura desacoplada** que permite futuros clientes (web o
    escritorio)

------------------------------------------------------------------------

# Descripción

El sistema permite a una empresa gestionar el registro horario de sus
empleados mediante:

-   fichaje de entrada y salida
-   registro de pausas durante la jornada
-   planificación de ausencias (vacaciones, permisos, festivos)
-   cálculo automático de saldos de horas
-   generación de informes operativos

La arquitectura está diseñada para separar completamente **backend y
cliente**, permitiendo que múltiples aplicaciones consuman la misma API.

Actualmente el proyecto se encuentra en **fase de análisis y diseño**,
con el modelo de datos, los requisitos funcionales y la especificación
completa de la API definidos antes de comenzar el desarrollo.

------------------------------------------------------------------------

# Funcionalidades principales

-   Autenticación con JWT y control de acceso por roles (ADMIN,
    ENCARGADO, EMPLEADO)
-   Registro de jornada laboral mediante fichaje de entrada y salida
-   Gestión de pausas durante la jornada
-   Planificación de ausencias (vacaciones, permisos, festivos)
-   Cálculo automático de saldo de horas y días disponibles
-   Parte diario de presencia de empleados
-   Informes operativos de horas trabajadas y ausencias
-   Generación de informes PDF firmables
-   Terminal de fichaje mediante PIN para uso en dispositivo compartido

------------------------------------------------------------------------

# Stack tecnológico

## Backend

-   Java 21
-   Spring Boot
-   JPA / Hibernate
-   MySQL (producción)
-   H2 (desarrollo)
-   JWT (autenticación)
-   SpringDoc OpenAPI (Swagger)

## Cliente Android

-   Kotlin
-   Android nativo
-   Retrofit
-   OkHttp
-   Material Design 3
-   Navigation Component
-   Coroutines

## Herramientas

-   Git
-   IntelliJ IDEA
-   Android Studio

------------------------------------------------------------------------

# Arquitectura

StaffFlow utiliza una **arquitectura en capas (Layered Architecture)**:

Controller → Service → Repository → Entity

Características principales:

-   API REST **stateless**
-   Autenticación mediante **JWT**
-   Control de acceso basado en **roles**
-   Separación entre **entidades de dominio y DTOs**
-   Persistencia mediante **JPA / Hibernate**

La API utiliza versionado:

/api/v1/

Esto permite introducir cambios en futuras versiones sin romper clientes
existentes.

------------------------------------------------------------------------

# Diseño de la API

La API REST se ha definido siguiendo un enfoque **design‑first**,
especificando todos los endpoints antes de implementar la lógica de
negocio.

Actualmente la especificación incluye:

-   **52 endpoints**
-   **13 grupos funcionales**
-   control de acceso por roles
-   terminal de fichaje mediante **PIN sin autenticación JWT**

Principales recursos:

/api/v1/auth\
/api/v1/usuarios\
/api/v1/empleados\
/api/v1/fichajes\
/api/v1/pausas\
/api/v1/ausencias\
/api/v1/presencia\
/api/v1/saldos\
/api/v1/informes\
/api/v1/terminal

------------------------------------------------------------------------

# Estructura del repositorio

staffflow/ ├── staffflow-backend/ \# API REST --- Spring Boot + Java\
└── staffflow-android/ \# App Android --- Kotlin + Retrofit

Cada módulo se desarrolla de forma independiente para mantener una
arquitectura desacoplada.

------------------------------------------------------------------------

# Decisiones de arquitectura

## 1. API REST desacoplada del cliente Android

El sistema se ha diseñado como una API REST independiente del cliente
móvil. Esto permite que la lógica de negocio resida en el backend y que
en el futuro puedan añadirse otros clientes (por ejemplo, una aplicación
web o de escritorio) sin rediseñar el núcleo del sistema.

## 2. Separación entre usuarios y empleados

El modelo distingue entre **usuarios** y **empleados** para separar
autenticación/autorización de la información laboral. Esto permite que
un usuario con rol ADMIN exista sin perfil de empleado, mientras que
ENCARGADO y EMPLEADO sí disponen de ambos registros.

## 3. Bajas lógicas en lugar de borrado físico

Los registros de usuarios y empleados no se eliminan físicamente: se
desactivan mediante un campo `activo`. Esto preserva la trazabilidad del
sistema y evita romper referencias históricas en fichajes, pausas y
auditoría.

## 4. Endpoints específicos para terminal de fichaje

Los fichajes desde terminal utilizan una ruta propia
(`/api/v1/terminal/...`) separada de la API autenticada por JWT. El
terminal representa un dispositivo compartido donde la identificación se
realiza mediante PIN y se aplican reglas específicas como bloqueo
temporal por intentos fallidos.

------------------------------------------------------------------------

# Estado del proyecto

  -----------------------------------------------------------------------
  Fase          Descripción                         Estado
  ------------- ----------------------------------- ---------------------
  Fase 0        Configuración del entorno y         Completada
                estructura base                     

  Fase 1        Análisis y diseño (requisitos,      En progreso
                modelo de datos, API)               

  Fase 2        Desarrollo del backend              Pendiente

  Fase 3        Desarrollo de la app Android        Pendiente

  Fase 4        Testing                             Pendiente

  Fase 5        Documentación final                 Pendiente
  -----------------------------------------------------------------------

Actualmente el proyecto se encuentra finalizando la **fase de diseño del
sistema**.

------------------------------------------------------------------------

# Autor

Santiago

Proyecto Final del Ciclo\
**Desarrollo de Aplicaciones Multiplataforma (DAM)**\
iLERNA · 2025‑2026
