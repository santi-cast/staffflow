# Dockerfile multistage para StaffFlow backend.
#
# Stage 1 (builder): compila el JAR con Maven sobre Eclipse Temurin 21.
# Stage 2 (runtime): ejecuta el JAR sobre una imagen JRE Alpine ligera.
#
# Perfil por defecto: dev (H2 en memoria + data.sql v6 precargado).
# No hace falta Java ni Maven instalados en el host: el build los descarga
# automaticamente dentro del stage builder.
#
# Comando de build (desde la raiz del repo):
#   docker build -f docs/instalacion-backend-docker/Dockerfile \
#                -t staffflow-backend:1.0.0 staffflow-backend/
#
# El archivo Dockerfile.dockerignore (junto a este Dockerfile) excluye target/,
# .git e IDE del contexto. BuildKit lo aplica automaticamente al usar `-f`.
#
# Esta imagen NO contiene secretos. Para el perfil mysql, las variables
# DB_URL, DB_USERNAME, DB_PASSWORD y JWT_SECRET se pasan en tiempo de
# ejecucion (docker run -e ... o docker-compose.yml).

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
# (requiere DB_USERNAME, DB_PASSWORD y JWT_SECRET; DB_URL es opcional).
ENV SPRING_PROFILES_ACTIVE=dev

# Puerto HTTP de la API REST y Swagger UI.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/staffflow-backend.jar"]
