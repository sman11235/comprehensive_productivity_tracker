# ---------- build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies first
COPY consumer/pom.xml .
RUN mvn -q -e -DskipTests dependency:go-offline

# Copy source and build
COPY consumer/src ./src
RUN mvn -q -DskipTests package

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# good practice: run as non-root
RUN useradd -m appuser
USER appuser

# copy jar
COPY --from=build /app/target/*.jar app.jar

# Spring Boot default
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
