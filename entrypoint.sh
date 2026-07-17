#!/bin/sh

# entrypoint.sh — 用于生产部署 (Dockerfile000)
# 以 root 运行，脚本内切换到 appuser 执行应用

set -e

# 确保上传目录权限正确
chown -R appuser:appgroup /app/uploads

# 切换到 appuser 执行 JAR
exec su -s /bin/sh appuser -c "java ${JAVA_OPTS} -jar /app/app.jar"
