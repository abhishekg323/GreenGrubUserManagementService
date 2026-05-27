FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

COPY settings.xml .
COPY pom.xml .
RUN mvn dependency:go-offline -B -s settings.xml

COPY src ./src
RUN mvn clean package -DskipTests -s settings.xml

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -g 1001 appuser && \
    adduser -D -u 1001 -G appuser appuser

COPY --from=build /app/target/customer-service.jar app.jar

RUN chown appuser:appuser app.jar

USER appuser

EXPOSE 8082 9092

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", \
    "app.jar"]
