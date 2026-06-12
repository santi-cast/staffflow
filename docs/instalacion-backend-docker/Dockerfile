# Dockerfile multistage para StaffFlow backend.
#
# Stage 1 (builder): compila el JAR con Maven sobre Eclipse Temurin 21.
# Stage 2 (runtime): ejecuta el JAR sobre una imagen JRE Alpine ligera.
#
# Perfil por defecto: dev (H2 en memoria + data.sql v6 precargado).
# El tutor NO necesita Java ni Maven instalados: el build los descarga
# automaticamente dentro del stage builder.
#
# Comando de build (desde la raiz del repo):
#   docker build -f docs/instalacion-backend-docker/Dockerfile \
#                -t staffflow-backend:1.0.0 staffflow-backend/

# ============================================================================
# Stage 1: builder
# ============================================================================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copiar primero los descriptores de Maven para aprovechar la cache de capas:
# si el pom.xml no cambia, las dependencias se reutilizan entre builds.
COPY pom.xml ./
COPY .mvn ./.mvn
COPY mvnw ./

# Descargar dependencias en una capa separada (cache friendly).
RUN ./mvnw dependency:go-offline -B

# Copiar el codigo fuente y empaquetar el JAR sin ejecutar los tests
# (los tests se validan fuera del flujo Docker, ver README).
COPY src ./src
RUN ./mvnw package -DskipTests -B

# ============================================================================
# Stage 2: runtime
# ============================================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copiar UNICAMENTE el JAR ejecutable desde el stage builder.
# El JAR generado por Spring Boot incluye todas las dependencias.
COPY --from=builder /build/target/staffflow-backend-0.0.1-SNAPSHOT.jar /app/staffflow-backend.jar

# Perfil dev por defecto: H2 en memoria + data.sql precargado.
# El operador puede sobrescribirlo pasando SPRING_PROFILES_ACTIVE=mysql
# (requiere variables de entorno adicionales, ver README).
ENV SPRING_PROFILES_ACTIVE=dev

# Puerto HTTP de la API REST y Swagger UI.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/staffflow-backend.jar"]
