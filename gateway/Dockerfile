FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# When building from repo root: docker build -f gateway/Dockerfile .
# After: ./gradlew :gateway:installDist
COPY gateway/build/install/gateway/ .

EXPOSE 8080

ENTRYPOINT ["bin/GatewayMain"]
