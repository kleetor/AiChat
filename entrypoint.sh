#!/bin/sh
set -e
# entrypoint.sh - 修复 Docker volume 挂载目录的权限后启动应用

# 确保上传目录存在（volume 首次挂载时为空）
mkdir -p /app/uploads/images /app/uploads/userPic /app/uploads/kb /app/uploads/random-covers /app/uploads/Storepic /app/uploads/upStorepic

# 初始化空卷：如果卷目录为空，从镜像内的备份目录复制默认文件
for subdir in images userPic kb random-covers Storepic upStorepic; do
    target="/app/uploads/$subdir"
    backup="/app/uploads-default/$subdir"
    if [ -d "$backup" ] && [ -z "$(ls -A "$target" 2>/dev/null)" ]; then
        echo "Initializing empty volume: $target from $backup"
        cp -a "$backup"/* "$target"/ 2>/dev/null || true
    fi
done

# 修复上传目录权限（appuser 需要写入这些目录）
# 注意：容器以 root 启动执行 chown，然后切换到 appuser
chown -R appuser:appgroup /app/uploads

# 切换到 appuser 并执行应用
# -Duser.dir=/app 确保 JVM 的 user.dir 指向正确的工作目录，否则图片等资源路径会解析错误
exec su -s /bin/sh appuser -c "java -Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Duser.dir=/app -jar /app/app.jar"
