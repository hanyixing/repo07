# ============== 第一阶段：构建阶段 ==============
FROM maven:3.8-openjdk-8-slim AS builder

WORKDIR /build

# 先复制 pom.xml 以利用 Docker 缓存层
COPY pom.xml .
COPY blog-core/pom.xml blog-core/
COPY blog-admin/pom.xml blog-admin/
COPY blog-web/pom.xml blog-web/
COPY blog-file/pom.xml blog-file/
COPY blog-codegen/pom.xml blog-codegen/

# 下载依赖（利用 Docker 缓存）
RUN mvn dependency:go-offline -B

# 复制源代码
COPY . .

# 构建项目
RUN mvn clean package -DskipTests -Pprod

# ============== 第二阶段：运行阶段 ==============
FROM openjdk:8-jre-slim

LABEL maintainer="yadong.zhang0415@gmail.com"
LABEL description="OneBlog Web Service"

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 创建非 root 用户运行应用（安全最佳实践）
RUN groupadd -r oneblog && useradd -r -g oneblog -u 1001 oneblog

# JVM 调优参数
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
ENV PARAMS=""
ENV SPRING_PROFILES_ACTIVE="prod"

WORKDIR /app

# 从构建阶段复制 JAR 文件
COPY --from=builder /build/blog-web/target/blog-web.jar app.jar

# 修改文件所有者
RUN chown -R oneblog:oneblog /app

# 切换到非 root 用户
USER oneblog

# 暴露端口
EXPOSE 8443

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8443/actuator/health || exit 1

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=$SPRING_PROFILES_ACTIVE $PARAMS"]
