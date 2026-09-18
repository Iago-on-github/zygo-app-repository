FROM maven:3.9-eclipse-temurin-21 AS builder
LABEL authors="Zyggo"

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests -Dfile.encoding=UTF-8

FROM eclipse-temurin:21-jre-alpine
LABEL authors="Zyggo"

WORKDIR /app

RUN apk add --no-cache wget

RUN adduser -D appuser

COPY --from=builder /app/target/*.jar app.jar

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]