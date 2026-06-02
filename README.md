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
|----------------------|--------|--------------------------------------------------------------------------|
| dify-eureka-service  | 8761   | 服务注册与发现中心                                                       |
| dify-api-gateway     | 9000   | 统一入口、路由、白名单、限流、跨域                                       |
| dify-user-service    | 8086   | 用户注册/登录、应用管理（AppKey/Secret）                                 |
| dify-llm-service     | 8081   | 普通对话、条件对话、RAG 对话、Function Calling、流式 SSE                  |
| dify-rag-service     | 8082   | 文档上传、文本分块、向量化、向量检索、Chunks 管理                         |
| dify-function-service| 8083   | 工具注册、Function 列表、Function 调用执行                               |
| dify-agent-service   | 8084   | Agent 配置、知识库绑定、工具绑定、Agent 执行/对话/流式对话                |
| dify-prompt-engine   | 8087   | 根据需求自动生成提示词、提示词模板 CRUD、测试、复制、状态管理              |
| dify-workflow-service| 8088   | 工作流编排（开始/结束/LLM/RAG/Agent/Function/条件）、执行、发布、历史查询 |
| dify-common          | -      | 通用实体、工具类、异常处理                                               |
| dify-feign-api       | -      | Feign 公共接口（供服务间调用）                                           |
| dify-web             | 80/5173| Vue3 前端项目，提供可视化界面                                             |

---

## 🚀 快速开始

### 1. 环境要求

- JDK 1.8+
- Maven 3.6+
- MySQL 8.0+
- Docker & Docker Compose（可选）
- Chroma 向量数据库（或修改配置使用其他）

### 2. 数据库初始化

```bash
# 创建数据库 dify_platform
mysql -u root -p < sql/ddl.sql