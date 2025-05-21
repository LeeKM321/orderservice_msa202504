FROM openjdk:17-jdk-slim AS build

EXPOSE 8888

WORKDIR /app
COPY . .

RUN chmod +x ./gradlew
RUN ./gradlew clean build -x test

FROM openjdk:17-jdk-slim

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]