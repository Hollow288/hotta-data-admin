# 使用官方的 OpenJDK 21 作为基础镜像
FROM eclipse-temurin:21-jdk-jammy

# 设置工作目录
WORKDIR /app

COPY target/hotta-data-admin-0.0.1-SNAPSHOT.jar app.jar

# 设置 JVM 启动参数并启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
