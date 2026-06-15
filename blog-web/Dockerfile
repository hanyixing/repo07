# ===== 构建阶段：解压 Spring Boot 分层 jar =====
FROM eclipse-temurin:8-jre-alpine AS builder
WORKDIR /build
# 复用本地已 mvn package 生成的 jar（保持原有 build 流程与 compose 构建上下文不变）
COPY target/blog-web.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ===== 运行阶段：精简 Alpine JRE 镜像 =====
FROM eclipse-temurin:8-jre-alpine
LABEL maintainer="yadong.zhang0415@gmail.com"

# JVM调优参数等额外参数
ENV TZ=Asia/Shanghai \
    PARAMS=""

# 时区数据 + 非 root 运行用户 + 预创建可写应用目录
RUN apk add --no-cache tzdata \
 && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime \
 && echo $TZ > /etc/timezone \
 && addgroup -g 1000 app && adduser -D -u 1000 -G app app \
 && mkdir -p /var/tmp/oneblog/logs /var/tmp/oneblog/blog-web \
 && chown -R app:app /var/tmp/oneblog

WORKDIR /app
# 分层拷贝：依赖层在前（变动少，最大化构建缓存命中），应用代码层在后（变动频繁）
COPY --from=builder --chown=app:app /build/dependencies/ ./
COPY --from=builder --chown=app:app /build/spring-boot-loader/ ./
COPY --from=builder --chown=app:app /build/snapshot-dependencies/ ./
COPY --from=builder --chown=app:app /build/application/ ./

USER app

# 指定容器端口
EXPOSE 8443

# 轻量健康检查：直接访问应用端口（busybox wget，无需 actuator 或额外依赖）
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q -T 4 -O /dev/null http://localhost:8443/ || exit 1

# 通过 sh -c 修复原 exec-form 下 $PARAMS 不被展开的问题；exec 保证 java 进程为 PID 1
ENTRYPOINT ["sh","-c","exec java $PARAMS -Djava.security.egd=file:/dev/./urandom org.springframework.boot.loader.JarLauncher"]
