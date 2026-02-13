FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# wget for docker-compose healthcheck
RUN apk add --no-cache wget

# When building from repo root: ./gradlew :gateway:bootJar && docker build -f gateway/Dockerfile .
COPY gateway/build/libs/gateway.jar .

EXPOSE 8080

ENTRYPOINT ["java", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.reflect=ALL-UNNAMED", "-jar", "gateway.jar"]
