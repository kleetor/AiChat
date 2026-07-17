FROM eclipse-temurin:17-jre-alpine

# 构建参数：JAR 文件路径
ARG JAR_FILE=target/aichat-0.0.1-SNAPSHOT.jar

# 设置时区 & 安装 curl（用于健康检查）
RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 创建非 root 用户（提前创建以利用 Docker 层缓存）
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# 创建上传目录（与 docker-compose volumes 对应，volume 挂载时会被覆盖，未挂载时确保目录存在）
RUN mkdir -p /app/uploads/images /app/uploads/userPic /app/uploads/kb

# 复制已构建好的 JAR
COPY ${JAR_FILE} app.jar

RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8080

# JVM 调优参数（可在运行时通过 JAVA_OPTS 环境变量覆盖）
# -Xmx512m: 最大堆内存 512MB, -XX:+UseZGC: 启用 ZGC 低延迟垃圾回收
ENV JAVA_OPTS="-Xmx512m -XX:+UseZGC -XX:MaxGCPauseMillis=50"

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
