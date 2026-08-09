# syntax=docker/dockerfile:1

# ---------- Stage 1 : build ----------
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app

# Copie des fichiers de dépendances en premier pour profiter du cache Docker
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copie des sources et build du jar (tests exécutés en amont dans la CI)
COPY src ./src
RUN ./mvnw clean package -DskipTests -B \
    && mv target/*.jar target/app.jar

# ---------- Stage 2 : runtime ----------
FROM eclipse-temurin:25-jre-alpine AS runtime

# Applique les derniers correctifs de sécurité des paquets système Alpine
# (indépendant de la fraîcheur exacte du tag de l'image de base au moment
# du pull ; corrige les CVE OS détectées par le scan Trivy en CI)
RUN apk update && apk upgrade --no-cache

# Utilisateur non-root
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app

COPY --from=build /app/target/app.jar app.jar
RUN mkdir -p /app/uploads/avatars && chown -R spring:spring /app/uploads
RUN chown spring:spring app.jar
USER spring:spring

ENV JAVA_OPTS=""
EXPOSE 8080

# NB: si Spring Security est activé avec une config restrictive, pensez à
# autoriser /actuator/health en accès public, sinon ce healthcheck échouera.
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
