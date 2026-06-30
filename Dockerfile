# Stage 1: Build jar
FROM maven:3.9.12-eclipse-temurin-21 AS builder
WORKDIR /app

# 複製 Maven 設定和程式碼
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src

# Build jar
RUN mvn clean package -DskipTests

# Stage 2: Run jar
FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app

# 從 builder 複製 jar
COPY --from=builder /app/target/*.jar app.jar

# 不指定 profile !!!
ENTRYPOINT ["java","-jar","app.jar"]
