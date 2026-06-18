

# Dify AI Platform (Java 微服务版)

## 项目简介

Dify AI Platform 是基于 Java Spring Cloud 架构构建的企业级 AI 应用开发平台。平台采用微服务架构设计，提供了可视化工作流编排、智能体（Agent）、RAG（检索增强生成）、Function Calling、提示词工程等核心能力，旨在帮助开发者快速构建和部署各类 AI 应用。

本项目是 Dify 的完整 Java 技术栈实现，通过 Spring Cloud Netflix Eureka 实现服务注册与发现，采用 Spring Cloud Gateway 作为统一网关，支持高并发、可扩展的企业级 AI 应用场景。前端使用 Vue 3 + Vite + Element Plus 构建，提供现代化用户体验。

> **注意**： 项目使用时，请确保在配置界面配置了大模型地址，已经验证的大模型包括阿里百炼、魔塔社区。

---

## 技术架构

### 后端技术栈

| 层级 | 技术选型 | 说明 |
|------|---------|------|
| 核心框架 | Spring Boot 2.7.x / Spring Cloud 2021.x | 提供微服务基础架构 |
| 服务治理 | Netflix Eureka | 服务注册与发现中心 |
| API 网关 | Spring Cloud Gateway | 统一路由入口、限流、跨域处理 |
| 持久层 | MyBatis-Plus + MySQL | ORM 框架与关系型数据库 |
| 向量存储 | Chroma（默认） | 支持替换为 Milvus、Qdrant 等 |
| 大语言模型 | OpenAI、通义千问、Ollama、本地模型 | 统一的 LLM 适配层 |
| 缓存层 | Redis + Redisson | 会话缓存与分布式锁 |
| 任务调度 | JDK Scheduled | 定时同步对话历史等 |

### 前端技术栈

| 技术 | 用途 |
|------|------|
| Vue 3 | 渐进式前端框架 |
| Vite | 开发服务器与构建工具 |
| Element Plus | UI 组件库 |
| Axios | HTTP 请求客户端 |

### 基础设施

| 组件 | 要求版本 |
|------|----------|
| JDK | 1.8+ |
| Maven | 3.6+ |
| MySQL | 8.0+ |
| Redis | 5.0+ |
| Docker | 20.10+ （可选） |
| Docker Compose | 1.29+ （可选） |

---

## 模块概览

本项目采用模块化设计，各服务独立部署、协同工作。

### 服务端模块

| 模块                    | 端口   | 功能描述                                  |
|-----------------------|------|---------------------------------------|
| dify-eureka-service   | 8085 | Netflix Eureka 服务注册与发现中心              |
| dify-api-gateway      | 9000 | 统一网关：路由转发、认证鉴权、限流、跨域                  |
| dify-user-service     | 8086 | 用户管理：注册登录、AppKey/Secret 应用凭证          |
| dify-llm-service      | 8081 | LLM 服务：普通对话、条件对话、流式 SSE               |
| dify-rag-service      | 8082 | RAG 服务：文档处理、文本分块、向量化存储、向量检索、Chunks 管理 |
| dify-function-service | 8083 | 函数服务：工具注册、函数列表、动态调用执行                 |
| dify-agent-service    | 8084 | Agent 服务：智能体配置、知识库绑定、工具绑定、对话执行        |
| dify-prompt-engine    | 8087 | 提示词引擎：自动生成提示词、模板 CRUD、版本管理            |
| dify-workflow-service | 8088 | 工作流服务：可视化编排、执行跟踪、历史查询                 |
| dify-model-provider   | 8089 | 模型配置服务：大模型配置、添加、大模型调用                 |

### 公共模块

| 模块 | 说明 |
|------|------|
| dify-common | 公共实体、工具类、统一异常处理 |
| dify-feign-api | Feign 客户端接口定义，供服务间 RPC 调用 |
| dify-web | Vue 3 + Vite 前端项目 |

---

## 快速开始

### 环境准备

确保本地已安装以下软件：

```bash
# 查看 Java 版本
java -version

# 查看 Maven 版本
mvn -version

# 查看 MySQL 版本
mysql --version

# 查看 Docker 版本（可选）
docker --version
```

### 方式一：本地运行

#### 第一步：编译项目

```bash
mvn clean install -DskipTests
```

#### 第二步：启动服务（按顺序）

建议按以下顺序启动各微服务：

```bash
# 1. 启动 Eureka 注册中心
cd dify-eureka-service
mvn spring-boot:run

# 2. 启动其他核心服务（可并行或按需启动）
cd dify-user-service && mvn spring-boot:run
cd dify-llm-service && mvn spring-boot:run
cd dify-rag-service && mvn spring-boot:run
cd dify-function-service && mvn spring-boot:run
cd dify-agent-service && mvn spring-boot:run
cd dify-prompt-engine && mvn spring-boot:run
cd dify-workflow-service && mvn spring-boot:run
cd dify-model-provider && mvn spring-boot:run

# 3. 启动网关（最后启动）
cd dify-api-gateway && mvn spring-boot:run
```

#### 第三步：启动前端

```bash
cd dify-web
npm install
npm run dev
```

访问 `http://localhost:3000` 即可打开控制台界面。

### 方式二：Docker Compose 一键部署

```bash
# 进入项目根目录
cd dify-ai-platform

# 清理并构建后端
mvn clean

# 进入 Docker 编排目录
cd docker

# 构建镜像
docker-compose build --no-cache

# 启动所有服务
docker-compose up -d

# 查看日志
docker-compose logs -f
```

---

## 配置说明

### 数据库配置

在各服务的 `src/main/resources/application.yml` 中配置数据库连接：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/dify?useUnicode=true&characterEncoding=utf-8
    username: root
    password: your_password
```

### 大模型配置

系统主要使用的魔塔社区的大模型和阿里的千问大模型，已经测试通过。

### 向量数据库配置

默认使用本地 **Ollama** 部署的 `nomic-embed-text` 向量模型：

```yaml
dify:
  rag:
    embeddingType: ollama
    vectorStoreType: chroma
```

---

## 功能特性

### 已实现功能

#### 1. 普通对话
支持单轮、多轮对话，会话上下文保持。

#### 2. 条件对话
根据预设条件动态选择回复策略。

#### 3. RAG 对话
结合知识库检索结果的问答，可上传 PDF、Word 等文档，系统自动完成：
- 文档解析（PDF、Word、TXT 等格式）
- 智能文本分块（按段落或章节）
- 向量化存储
- 向量相似度检索

> **说明**：文档中的图片保存至本地文件夹，表格转化为 Markdown 格式存储。

#### 4. Function Calling
支持扩展工具函数，AI 可自主判断并调用外部工具。内置函数包括：
- `simple_calc`：执行数学运算。支持加法(add)、减法(subtract)、乘法(multiply)、除法(divide)。
- `get_current_time`：获取当前日期时间信息。可以指定返回格式，支持日期、时间、星期等。
- `get_weather`：查询指定城市的天气信息，包括温度、天气状况、空气质量、湿度、风力等。支持查询指定日期或默认今天。
- `web_search`：在互联网上搜索信息。可以搜索新闻、知识、资讯等各类网络信息。适用于需要获取最新信息、实时数据、网络资料等场景。

#### 5. Agent 智能体
- Agent 配置与管理
- 知识库绑定（多知识库支持）
- 工具绑定（Function Calling）
- 对话执行与流式对话

#### 6. 提示词引擎
- 根据需求自动生成提示词
- 提示词模板 CRUD
- 版本管理与回滚
- 置信度评分

#### 7. 工作流编排（部分）
- 可视化节点编排：开始 → LLM → RAG → Agent → Function → 条件 → 代码 → 结束
- 工作流执行与历史记录

---

## 常见问题

### 服务启动失败，提示数据库连接错误？

**解决方案**：
1. 确认 MySQL 已启动：`mysql -u root -p`
2. 检查 `application.yml` 中的数据库配置是否正确
3. 创建对应的数据库：`CREATE DATABASE dify;`

### 接口返回 401 未授权？

**解决方案**：
1. 检查请求头是否携带有效的 Token
2. 确认接口是否在网关白名单中（`WHITE_LIST` 配置）
3. Token 过期或格式错误，请重新登录获取

### 流式对话没有响应？

**解决方案**：
1. 确认前端 EventSource 配置正确（SSE 连接）
2. 检查后端 SseEmitter 实现和超时设置
3. 确认大模型 API 可正常访问

### RAG 检索无结果？

**解决方案**：
1. 确认 Chroma 向量数据库已启动并正常运行
2. 确认文档已上传并完成向量化（可在知识库页面查看 Chunks）
3. 检查向量模型是否正常工作

### 前端页面空白或加载失败？

**解决方案**：
1. 检查 `npm install` 是否成功完成
2. 确认 `.env.development` 中的 API 地址配置正确
3. 清除浏览器缓存后重试

### web_search函数调用返回为空？

**解决方案**：
1. web_search函数需依赖`searxng`搜索引擎
2. 需要在 `Docker` 中安装`searxng`
3. `searxng`基于Docker安装步骤如下：
```bash
# 使用 8080 端口
docker run -d --name searxng -p 8080:8080 searxng/searxng:latest

# 测试
Start-Sleep -Seconds 10
curl "http://localhost:8888/search?q=test&format=json"
```

---

## 项目规划

以下是未来的功能演进方向，欢迎贡献代码：

| 功能 | 优先级 | 说明 |
|------|--------|------|
| 多租户支持 | 高 | 实现租户隔离、数据权限控制 |
| 工作流完善 | 高 | 完整的可视化编排与调试 |
| 模型管理平台 | 中 | 统一的模型配置、切换、监控 |
| 更多向量库 | 中 | Pinecone、Milvus、Qdrant 等支持 |
| 监控告警 | 低 | 业务指标与系统健康监控 |
| API 限流增强 | 低 | 细粒度限流与配额管理 |

---

## 贡献指南

欢迎通过以下方式参与项目贡献：

1. **提交 Issue**：报告 Bug 或提出新功能建议
2. **提交 Pull Request**：修复问题或增加功能
3. **编写文档**：完善 Wiki 或中文文档
4. **测试反馈**：体验并提出改进意见

请确保代码符合项目现有的编码风格，并添加适当的单元测试。

---

## 许可证

本项目基于 **MIT License** 开源，你可以自由使用、修改和分发，具体见 LICENSE 文件。

---

## 联系方式

- **项目地址**：https://gitee.com/Whshy/dify-ai-platform
- **问题反馈**：hellowangxu@163.com

---

## 参考资源

- [Spring Cloud 官方文档](https://spring.io/projects/spring-cloud)
- [Vue 3 官方文档](https://vuejs.org/)
- [Element Plus 组件库](https://element-plus.org/)
- [Chroma 向量数据库](https://www.trychroma.com/)
- [魔塔社区模型](https://modelscope.cn/)
- [阿里云百炼模型](https://bailian.console.aliyun.com/)