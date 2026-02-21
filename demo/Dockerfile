# Stage 1: Build jar
FROM maven:3.9.12-eclipse-temurin-21 AS builder
WORKDIR /app

# 複製 Maven 設定和程式碼
COPY pom.xml .
COPY src ./src

# Build jar
RUN mvn clean package -DskipTests

# Stage 2: Run jar
FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app

# 從 builder 複製 jar
COPY --from=builder /app/target/*.jar app.jar

# 使用 Render 提供的 PORT
ENV SPRING_PROFILES_ACTIVE=docker
ENTRYPOINT ["sh", "-c", "java -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} -Dserver.port=$PORT -jar app.jar"]