FROM maven:3.9-eclipse-temurin-21-alpine AS build

ARG GITHUB_USERNAME
ARG GITHUB_TOKEN

WORKDIR /app

RUN printf '<?xml version="1.0" encoding="UTF-8"?>\n\
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"\n\
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"\n\
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0\n\
                              https://maven.apache.org/xsd/settings-1.2.0.xsd">\n\
    <servers>\n\
        <server>\n\
            <id>github</id>\n\
            <username>%s</username>\n\
            <password>%s</password>\n\
        </server>\n\
    </servers>\n\
</settings>\n' "$GITHUB_USERNAME" "$GITHUB_TOKEN" > settings.xml

COPY pom.xml .
RUN mvn dependency:go-offline -B -s settings.xml

COPY src ./src
RUN mvn clean package -DskipTests -s settings.xml

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd -g 1001 appuser && \
    useradd -u 1001 -g appuser -m appuser

COPY --from=build /app/target/customer-service.jar app.jar

RUN chown appuser:appuser app.jar

USER appuser

EXPOSE 8082 9092

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dio.netty.transport.noNative=true", \
    "-Dio.grpc.netty.shaded.io.netty.transport.noNative=true", \
    "-jar", \
    "app.jar"]
