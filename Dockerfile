FROM eclipse-temurin:17-jre-alpine

# 设置时区
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

WORKDIR /app

# 创建所有上传目录
RUN mkdir -p /app/uploads/images /app/uploads/upStorepic

# 复制静态资源（收款码等）
COPY uploads/Storepic/ /app/uploads/Storepic/

# 直接复制已构建好的 JAR
COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
