FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# When building from repo root: ./gradlew :gateway:bootJar && docker build -f gateway/Dockerfile .
COPY gateway/build/libs/gateway.jar .

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "gateway.jar"]
