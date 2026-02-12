FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# When building from repo root: docker build -f mengine-core/Dockerfile .
# After: ./gradlew :mengine-core:installDist
COPY mengine-core/build/install/mengine-core/ .

EXPOSE 8081

ENTRYPOINT ["bin/MatchingEngineMain"]
