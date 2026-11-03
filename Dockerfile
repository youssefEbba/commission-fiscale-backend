# Snyk Container analyse chaque FROM du Dockerfile (l’image Maven
# builder comprise). eclipse-temurin:17-jre (Ubuntu 26.04) ramenait
# ~156 CVE OS (brotli, curl, openssl, perl) sans lien avec l’appli.
FROM maven:3.9.16-eclipse-temurin-17-alpine AS build
WORKDIR /app
RUN apk upgrade --no-cache
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN apk upgrade --no-cache \
    && addgroup -S app && adduser -S -G app app
COPY --from=build --chown=app:app /app/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
