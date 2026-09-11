# Derleme Docker icinde: eskiden once "./mvnw package" sonra "docker compose
# build" gerekiyordu, biri unutulunca eski jar gidiyordu. Bagimliliklar
# BuildKit onbelleginde (/root/.m2) kaliyor, tum servisler paylasiyor.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
