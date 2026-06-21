FROM eclipse-temurin:17-jre-alpine

# 设置时区 & 安装 curl（用于健康检查）
RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

WORKDIR /app

# 创建上传目录（与 docker-compose volumes 对应，volume 挂载时会被覆盖，未挂载时确保目录存在）
RUN mkdir -p /app/uploads/images /app/uploads/userPic /app/uploads/kb

# 直接复制已构建好的 JAR（使用具体文件名避免匹配错误）
COPY target/aichat-0.0.1-SNAPSHOT.jar app.jar

# 创建非 root 用户运行应用
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
