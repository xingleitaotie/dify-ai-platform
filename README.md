# Dify AI Platform (Java 微服务版)

## 📖 项目简介

Dify AI Platform 是一个基于 Java  Spring Cloud 架构的 AI 应用开发平台，提供可视化的工作流编排、Agent 智能体、RAG 检索增强生成、Function Calling、提示词工程等核心能力。  
本项目是 Dify 的 Java 技术栈实现，采用微服务架构，支持高并发、可扩展的企业级 AI 应用落地。

---

## 🏗️ 技术栈

| 类型       | 技术选型                                                                 |
|-----------|--------------------------------------------------------------------------|
| 框架       | Spring Boot 2.7 / Spring Cloud 2021.x                                    |
| 服务发现   | Netflix Eureka                                                           |
| 网关       | Spring Cloud Gateway + 限流 + 跨域                                       |
| 持久层     | MyBatis-Plus + MySQL                                                     |
| 向量数据库 | Chroma（可替换为 Milvus / Qdrant）                                       |
| LLM 接入   | 支持 OpenAI、阿里通义、本地模型（通过统一适配层）                          |
| 前端       | Vue 3 + Vite + Element Plus                                              |
| 容器化     | Docker + Docker Compose                                                  |

---

## 📦 模块说明

| 模块名                 | 端口   | 功能描述                                                                 |
|----------------------|------|--------------------------------------------------------------------------|
| dify-eureka-service  | 8085 | 服务注册与发现中心                                                       |
| dify-api-gateway     | 9000 | 统一入口、路由、白名单、限流、跨域                                       |
| dify-user-service    | 8086 | 用户注册/登录、应用管理（AppKey/Secret）                                 |
| dify-llm-service     | 8081 | 普通对话、条件对话、RAG 对话、Function Calling、流式 SSE                  |
| dify-rag-service     | 8082 | 文档上传、文本分块、向量化、向量检索、Chunks 管理                         |
| dify-function-service| 8083 | 工具注册、Function 列表、Function 调用执行                               |
| dify-agent-service   | 8084 | Agent 配置、知识库绑定、工具绑定、Agent 执行/对话/流式对话                |
| dify-prompt-engine   | 8087 | 根据需求自动生成提示词、提示词模板 CRUD、测试、复制、状态管理              |
| dify-workflow-service| 8088 | 工作流编排（开始/结束/LLM/RAG/Agent/Function/条件）、执行、发布、历史查询 |
| dify-common          | -    | 通用实体、工具类、异常处理                                               |
| dify-feign-api       | -    | Feign 公共接口（供服务间调用）                                           |
| dify-web             | 3000 | Vue3 前端项目，提供可视化界面                                             |

---

## 🚀 快速开始

### 一、 环境要求

- JDK 1.8+
- Maven 3.6+
- MySQL 8.0+
- Docker & Docker Compose（可选）
- Chroma 向量数据库（或修改配置使用其他）

### 二、项目启动
### 方式一 :  本地运行（按顺序启动）

#### 1. 安装依赖
mvn clean install

#### 2. 启动Eureka服务注册中心
cd dify-eureka-service
mvn spring-boot:run

#### 3. 启动其他服务（按需启动）
- cd ../dify-llm-service && mvn spring-boot:run
- cd ../dify-rag-service && mvn spring-boot:run
- cd ../dify-function-service && mvn spring-boot:run
- cd ../dify-agent-service && mvn spring-boot:run
- cd ../dify-prompt-engine && mvn spring-boot:run
- cd ../dify-workflow-service && mvn spring-boot:run
- cd ../dify-api-gateway && mvn spring-boot:run
- cd ../dify-user-service && mvn spring-boot:run


### 方式二 ：Docker Compose一键部署
- cd dify-ai-platform
- mvn clean
- cd docker
- docker-compose build --no-cache
- docker-compose up -d
- docker-compose logs -f

# 项目情况及功能说明
1. 项目大模型使用的是魔塔社区的免费LLM
2. 向量大模型是基于本地ollama搭建的nomic-embed-text
3. 工作流目前已经测试了LLM和RAG节点，**注意**输入必须是query，下一节点输出字段必须是{{input.query}}，后面的节点可以根据大模型输出选择
4. 目前实现了大模型对话、agent、rag、prompt、function、workflow（部分功能）等功能
5. rag工程可以实现对上传的文档按照段落或者章节进行分块，同时涉及的图片目前是保存到本地文件夹，后期可以放到对象存储中，对于涉及的表格，是转化成md格式进行存储

# 常见问题
* Q: 服务启动失败，提示数据库连接错误？
* A: 检查MySQL是否启动，确认application.yml中的数据库配置正确。

* Q: 接口返回401未授权？
* A: 检查请求头是否携带token，或确认接口是否在网关白名单中。

* Q: 流式对话没有响应？
* A: 确认前端EventSource配置正确，后端SseEmitter实现无误。

* Q: RAG检索无结果？
* A: 确认Chroma向量库已启动，文档已上传并完成向量化。

# 后续规划
1. 多租户支持

2. 工作流编排

3. 模型管理平台

4. 更多向量数据库支持（Pinecone、Milvus）

5. 监控告警系统

6. API速率限制增强

# 贡献指南
- 欢迎提交Issue和Pull Request。

# 许可证
MIT License

# 联系方式
项目地址：https://github.com/xingleitaotie/dify-ai-platform.git

问题反馈：hellowangxu@163.com

**注意**：请确保在使用前配置好大模型API密钥和向量数据库连接。

