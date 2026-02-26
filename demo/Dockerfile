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

# 複製 wait-for-it.sh
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh

# 環境變數
ENV SPRING_PROFILES_ACTIVE=docker
ENV PORT=8080

# 使用 wait-for-it.sh 等資料庫啟動
ENTRYPOINT ["./wait-for-it.sh", "db:5432", "--timeout=60", "--strict", "--", "sh", "-c", "java -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} -Dserver.port=$PORT -jar app.jar"]