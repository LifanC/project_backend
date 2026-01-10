# 使用官方推薦的 OpenJDK 21
FROM eclipse-temurin:21-jdk-jammy

# 設定容器內工作目錄
WORKDIR /app

# 複製 Maven build 出來的 jar
COPY target/*.jar app.jar

# 對外開放 Spring Boot 預設 port
EXPOSE 8080

# 啟動 Spring Boot
ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-jar", "app.jar"]
