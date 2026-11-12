FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY user-service ./user-service
COPY billing-service ./billing-service
COPY order-service ./order-service
COPY notification-service ./notification-service
COPY event-contracts ./event-contracts
COPY service-common ./service-common

RUN chmod +x gradlew && ./gradlew :user-service:bootJar :billing-service:bootJar \
    :order-service:bootJar :notification-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup --system application && adduser --system --ingroup application application
ARG SERVICE=user-service
ARG HTTP_PORT=8000
COPY --from=builder "/workspace/${SERVICE}/build/libs/${SERVICE}-1.0.0.jar" /app/app.jar
USER application
EXPOSE ${HTTP_PORT}
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
