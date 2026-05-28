# 使用官方的 OpenJDK 21 作为基础镜像
FROM eclipse-temurin:21-jdk-jammy

# 安装中文字体，避免 Java AWT 绘制中文时出现方块字
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-noto-cjk \
    && fc-cache -f \
    && rm -rf /var/lib/apt/lists/*

# 设置工作目录
WORKDIR /app

COPY target/hotta-data-admin-0.0.1-SNAPSHOT.jar app.jar

# 设置 JVM 启动参数并启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
