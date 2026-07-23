FROM eclipse-temurin:17-jre-alpine

# 设置时区 & 安装 curl、su、Tesseract OCR（用于扫描件识别）
RUN apk add --no-cache tzdata curl tesseract-ocr tesseract-ocr-data-chi_sim tesseract-ocr-data-eng && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

WORKDIR /app

# 创建上传目录
RUN mkdir -p /app/uploads/images /app/uploads/userPic /app/uploads/kb /app/uploads/random-covers /app/uploads/Storepic /app/uploads/upStorepic

# 复制 JAR 包
COPY target/aichat-0.0.1-SNAPSHOT.jar app.jar

# 复制初始上传文件到镜像内备份目录（卷挂载后会覆盖 /app/uploads/，entrypoint.sh 会用此备份初始化空卷）
COPY uploads/ /app/uploads-default/

# 复制 entrypoint 脚本
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# 创建非 root 用户
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
RUN chown -R appuser:appgroup /app

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 使用 entrypoint 脚本启动（以 root 运行，脚本内会切换用户）
ENTRYPOINT ["/entrypoint.sh"]
