# Base 이미지
FROM openjdk:17-jdk-slim

COPY build/libs/*.jar /app.jar

# 애플리케이션 실행 명령
ENTRYPOINT ["java", "-jar", "/app.jar"]