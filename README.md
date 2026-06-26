# Dify AI Platform (Java 微服务版)

## 项目简介

Dify AI Platform 是基于 Java Spring Cloud 架构构建的企业级 AI 应用开发平台。平台采用微服务架构设计，提供了可视化工作流编排、智能体（Agent）、RAG（检索增强生成）、Function Calling、提示词工程等核心能力，旨在帮助开发者快速构建和部署各类 AI 应用。

本项目是 Dify 的完整 Java 技术栈实现，通过 Spring Cloud Netflix Eureka 实现服务注册与发现，采用 Spring Cloud Gateway 作为统一网关，支持高并发、可扩展的企业级 AI 应用场景。前端使用 Vue 3 + Vite + Element Plus 构建，提供现代化用户体验。

> **注意**：项目使用时，请确保在配置界面配置了大模型地址，已验证的大模型包括阿里百炼、魔塔社区。

---

## 技术架构

### 后端技术栈

| 层级 | 技术选型 | 说明 |
|------|---------|------|
| 核心框架 | Spring Boot 2.7.18 / Spring Cloud 2021.x | 提供微服务基础架构 |
| 服务治理 | Netflix Eureka | 服务注册与发现中心 |
| API 网关 | Spring Cloud Gateway | 统一路由入口、限流、跨域处理 |
| 持久层 | MyBatis-Plus + MySQL | ORM 框架与关系型数据库 |
| 向量存储 | Chroma（默认） | 支持替换为 Milvus、Qdrant 等 |
| 大语言模型 | OpenAI、通义千问、Ollama、本地模型 | 统一的 LLM 适配层 |
| 缓存层 | Redis + Redisson | 会话缓存与分布式锁 |
| 任务调度 | JDK Scheduled | 定时同步对话历史等 |
| JSON 处理 | FastJSON2 | 高性能 JSON 序列化/反序列化 |

### 前端技术栈

| 技术 | 用途 |
|------|------|
| Vue 3 | 渐进式前端框架 |
| Vite | 开发服务器与构建工具 |
| Element Plus | UI 组件库 |
| Axios | HTTP 请求客户端 |
| Pinia | 状态管理 |

### 基础设施

| 组件 | 要求版本 |
|------|----------|
| JDK | 11 |
| Maven | 3.6+ |
| MySQL | 8.0+ |
| Redis | 5.0+ |
| Docker | 20.10+ （可选） |
| Docker Compose | 1.29+ （可选） |

> **说明**：项目已全面升级至 JDK 11，以支持大模型工作流中代码节点的运行依赖。

### 架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                     前端 (Vue 3)                              │
│               http://localhost:3000                            │
└─────────────────────────────┬─────────────────────────────────┘
                              │ HTTP
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              dify-api-gateway (9000)                          │
│    路由转发 | 认证鉴权 | 限流 | 跨域处理 | 负载均衡              │
└─────────────────────────────┬─────────────────────────────────┘
                              │
           ┌──────────────────┼──────────────────┐
           │                  │                  │
           ▼                  ▼                  ▼
┌───────────────────┐ ┌───────────────────┐ ┌───────────────────┐
│ dify-user-service │ │ dify-llm-service  │ │ dify-rag-service  │
│   (8086)         │ │   (8081)         │ │   (8082)         │
│ 用户管理         │ │ LLM对话          │ │ RAG检索          │
└───────────────────┘ └───────────────────┘ └───────────────────┘
           │                  │                  │
           └──────────────────┼──────────────────┘
                              │
           ┌──────────────────┼──────────────────┐
           │                  │                  │
           ▼                  ▼                  ▼
┌───────────────────┐ ┌───────────────────┐ ┌───────────────────┐
│ dify-agent-service│ │ dify-function-    │ │ dify-workflow-    │
│   (8084)         │ │ service (8083)    │ │ service (8088)    │
│ Agent智能体      │ │ 函数调用          │ │ 工作流编排        │
└───────────────────┘ └───────────────────┘ └───────────────────┘
           │                  │                  │
           └──────────────────┼──────────────────┘
                              │
           ┌──────────────────┼──────────────────┐
           │                  │                  │
           ▼                  ▼                  ▼
┌───────────────────┐ ┌───────────────────┐ ┌───────────────────┐
│ dify-prompt-     │ │ dify-model-       │ │ dify-eureka-      │
│ engine (8087)    │ │ provider (8089)   │ │ service (8085)    │
│ 提示词引擎       │ │ 模型配置          │ │ 服务注册中心      │
└───────────────────┘ └───────────────────┘ └───────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    基础设施层                                   │
│  MySQL (数据存储) | Redis (缓存) | Chroma (向量) | Ollama     │
└─────────────────────────────────────────────────────────────────┘
```

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
# 查看 Java 版本（必须为 11）
java -version

# 查看 Maven 版本
mvn -version

# 查看 MySQL 版本
mysql --version

# 查看 Redis 版本
redis-server --version

# 查看 Docker 版本（可选）
docker --version
```

### 方式一：本地运行

#### 第一步：创建数据库

```bash
mysql -u root -p
CREATE DATABASE dify DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

#### 第二步：编译项目

```bash
mvn clean install -DskipTests
```

#### 第三步：启动服务（按顺序）

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

#### 第四步：启动前端

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
    url: jdbc:mysql://localhost:3306/dify?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: your_password
```

### 大模型配置

系统已测试通过的大模型包括：

| 模型 | 提供商 | 配置方式 |
|------|--------|----------|
| 通义千问 | 阿里云百炼 | 配置 API Key 和模型名称 |
| 魔塔社区模型 | 阿里达摩院 | 配置 API Key 和模型名称 |
| Ollama | 本地部署 | 配置本地 Ollama 地址 |
| OpenAI | OpenAI | 配置 API Key 和模型名称 |

### 向量数据库配置

默认使用本地 **Ollama** 部署的 `nomic-embed-text` 向量模型：

```yaml
dify:
  rag:
    embeddingType: ollama
    vectorStoreType: chroma
```

### Redis 配置

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    password: your_password
```

---

## 功能特性

### 已实现功能

#### 1. 普通对话
- 支持单轮、多轮对话
- 会话上下文保持
- 流式响应支持

#### 2. 条件对话
- 根据预设条件动态选择回复策略
- 支持多条件分支

#### 3. RAG 对话
结合知识库检索结果的问答，可上传 PDF、Word 等文档，系统自动完成：
- 文档解析（PDF、Word、TXT 等格式）
- 智能文本分块（按段落或章节）
- 向量化存储
- 向量相似度检索

> **说明**：文档中的图片保存至本地文件夹，表格转化为 Markdown 格式存储。

#### 4. Function Calling
支持扩展工具函数，AI 可自主判断并调用外部工具。内置函数包括：

| 函数名 | 功能 | 参数 | 返回值 |
|--------|------|------|--------|
| `simple_calc` | 执行数学运算 | `operation`: add/subtract/multiply/divide, `num1`, `num2` | 计算结果 |
| `get_current_time` | 获取当前日期时间 | `format`: date/time/week/all | 格式化时间字符串 |
| `get_weather` | 查询天气信息 | `city`, `date`(可选) | 温度、天气状况、空气质量等 |
| `web_search` | 互联网搜索 | `query`, `count`(可选) | 搜索结果列表 |

#### 5. Agent 智能体
- Agent 配置与管理
- 知识库绑定（多知识库支持）
- 工具绑定（Function Calling）
- 对话执行与流式对话
- 自主决策能力

#### 6. 提示词引擎
- 根据需求自动生成提示词
- 提示词模板 CRUD
- 版本管理与回滚
- 置信度评分
- 动态路由分发

#### 7. 工作流编排
- 可视化节点编排：开始 → LLM → RAG → Agent → Function → 条件 → 代码 → 结束
- 工作流执行与历史记录
- 变量传递与上下文管理
- 错误处理与重试机制

---

## API 接口

### 认证接口

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/user/login` | 用户登录 |
| POST | `/api/user/register` | 用户注册 |
| POST | `/api/user/logout` | 用户登出 |

### LLM 接口

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/llm/chat` | 普通对话 |
| POST | `/api/llm/chat/stream` | 流式对话 |

### RAG 接口

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/rag/{name}/search` | 知识库检索 |
| POST | `/api/rag/upload` | 上传文档 |
| GET | `/api/rag/documents` | 获取文档列表 |

### Agent 接口

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/agent/create` | 创建 Agent |
| POST | `/api/agent/{id}/chat` | Agent 对话 |

### 工作流接口

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/workflow/create` | 创建工作流 |
| POST | `/api/workflow/{id}/execute` | 执行工作流 |

---

## 开发规范

### 代码风格

- 遵循 Java 编码规范（Google 风格）
- 使用 Lombok 简化代码
- 类和方法必须添加 Javadoc 注释
- 异常处理统一使用 `AppException` 体系
- 使用 `log.info()`、`log.warn()`、`log.error()` 分级日志

### Git 规范

- 分支命名：`feature/xxx`、`bugfix/xxx`、`hotfix/xxx`
- Commit 格式：`type(scope): subject`
  - `feat`: 新功能
  - `fix`: 修复 Bug
  - `docs`: 文档更新
  - `refactor`: 代码重构
  - `test`: 测试代码

### 数据库规范

- 表名使用小写下划线格式：`user_info`
- 字段名使用小写下划线格式：`user_name`
- 使用 MyBatis-Plus 注解
- 实体类使用 `@Data`、`@TableName`、`@TableId` 注解

---

## 测试指南

### 运行单元测试

```bash
# 运行所有测试
mvn test

# 运行指定模块测试
mvn test -pl dify-common

# 跳过测试编译
mvn compile -DskipTests
```

### 代码质量检查

```bash
# 运行检查样式（如果配置了 Checkstyle）
mvn checkstyle:check

# 运行静态分析（如果配置了 SonarQube）
mvn sonar:sonar
```

---

## 部署指南

### 生产环境部署

1. **配置环境变量**：设置数据库密码、API Key 等敏感信息
2. **配置日志**：配置生产环境日志路径和级别
3. **配置监控**：集成 Prometheus 和 Grafana
4. **配置安全**：启用 HTTPS、配置防火墙规则

### 性能优化

- 使用连接池配置：HikariCP 连接池参数调优
- Redis 缓存策略：热点数据缓存、缓存过期策略
- 异步处理：使用 `@Async` 注解处理耗时操作
- 批量操作：数据库批量插入、批量查询

### 安全注意事项

- API 接口必须进行参数校验
- Token 使用 JWT 并设置合理过期时间
- 敏感信息加密存储（密码、API Key）
- 防止 SQL 注入、XSS 攻击
- 配置 CORS 白名单

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

### web_search 函数调用返回为空？

**解决方案**：
1. web_search 函数需依赖 `searxng` 搜索引擎
2. 需要在 Docker 中安装 `searxng`
3. `searxng` 基于 Docker 安装步骤如下：

```bash
# 使用 8080 端口
docker run -d --name searxng -p 8080:8080 searxng/searxng:latest

# 找到 SearXNG 的配置文件 settings.yml，通常为 /etc/searxng/settings.yml
# 允许所有搜索格式（包括 json）
# search:
#   formats:
#     - html
#     - json

# 重启 SearXNG
docker restart searxng

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

### 贡献流程

```
1. Fork 项目
2. 创建特性分支：git checkout -b feature/xxx
3. 提交代码：git commit -m "feat(module): 描述"
4. 推送到远程：git push origin feature/xxx
5. 创建 Pull Request
```

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
- [Ollama 本地模型](https://ollama.com/)
- [MyBatis-Plus 官方文档](https://baomidou.com/)
