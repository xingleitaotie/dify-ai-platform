# ==========================
# 第一阶段：构建阶段
# ==========================
FROM maven:3.6.3-openjdk-8 AS builder
WORKDIR /app

# 先复制 pom.xml 文件，利用 Docker 缓存
COPY pom.xml /app/
COPY dify-common/pom.xml /app/dify-common/
COPY dify-feign-api/pom.xml /app/dify-feign-api/
COPY dify-eureka-server/pom.xml /app/dify-eureka-server/
COPY dify-api-gateway/pom.xml /app/dify-api-gateway/
COPY dify-user-service/pom.xml /app/dify-user-service/
COPY dify-llm-service/pom.xml /app/dify-llm-service/
COPY dify-rag-service/pom.xml /app/dify-rag-service/
COPY dify-function-service/pom.xml /app/dify-function-service/
COPY dify-agent-service/pom.xml /app/dify-agent-service/
COPY dify-prompt-engine/pom.xml /app/dify-prompt-engine/
COPY dify-workflow-service/pom.xml /app/dify-workflow-service/

# 下载依赖（利用缓存）
RUN mvn dependency:go-offline -B

# 复制源代码
COPY . /app/

# 构建指定模块
ARG MODULE_NAME
RUN mvn clean package -DskipTests -pl ${MODULE_NAME} -am

# ==========================
# 第二阶段：运行阶段
# ==========================
FROM openjdk:8u342-jre-slim

LABEL maintainer="dify-ai-platform"
LABEL description="Dify AI Platform Microservice"

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m"

RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 创建配置目录
RUN mkdir -p /app/config

WORKDIR /app

ARG MODULE_NAME
COPY --from=builder /app/${MODULE_NAME}/target/*.jar /app/app.jar

# 复制配置文件（如果不存在会跳过，但不会报错）
COPY --from=builder /app/${MODULE_NAME}/src/main/resources/application-docker.yml /app/config/application-docker.yml

# 使用 application-docker.yml 中的配置启动
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app/app.jar --spring.profiles.active=docker --spring.config.additional-location=/app/config/"]