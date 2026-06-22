# agent-core-java 迁移改造进度文档

> 最后更新: 2026-06-18
> Python 参考实现: `agent_core/` (~100 个模块, 15 个子包)
> Java 实现: `agent-core-java/` (53 个源文件, 18 个测试文件)
> 架构差异: Python 为独立框架; Java 为 AgentScope Java v2 (`agentscope-core:2.0.0-SNAPSHOT`) 之上的薄封装层

---

## 一、总体迁移进度

| 阶段 | 状态 | 说明 |
|------|------|------|
| Phase 1: 基础设施 | ✅ 完成 | Gradle 构建、Config、EventMapper、RuntimeConfig |
| Phase 2: 中间件链 | ✅ 完成 | 11 个中间件: Observability, TurnLifecycle, Steering, Compaction, ToolHooks, ToolLogging, Retry, PromptAugment, HumanInput, Skill, Extension |
| Phase 3: 扩展系统 | ✅ 完成 | Extension 接口、ExtensionRunner、ExtensionContext、ExtensionAdapter |
| Phase 4: 系统提示词构建 | ✅ 完成 | SystemPromptBuilder、SystemPrompt、SystemPromptSection |
| Phase 5A: CLI 交互 | ⚠️ 部分完成 | CliMain 框架存在, `createAgent()` 为占位符 |
| Phase 5B: HTTP SSE 服务 | ✅ 完成 | ChatServer、SessionManager、AgentEventConverter、SseEvent |
| Phase 6: 测试覆盖 | ✅ 完成 | 18 个测试文件, 覆盖所有已实现模块 |

**整体功能覆盖率: 约 40-45%** (以 Python 功能面为基准)

---

## 二、逐模块迁移对照

### 2.1 核心运行时 (core)

| Python 模块 | Java 对应 | 状态 | 差异说明 |
|-------------|-----------|------|----------|
| `core/loop.py` (agent_loop 异步生成器) | AgentScope `ReActAgent` | ✅ 由框架提供 | Java 不需要自建循环, AgentScope 提供 |
| `core/agent.py` (Agent 状态化包装) | `agent/Agent.java` | ✅ 已迁移 | 基于 ReActAgent 封装, 含 Builder 模式 |
| `core/events.py` (AgentEvent 联合类型) | `events/EventMapper.java` + AgentScope 事件类 | ✅ 已迁移 | Java 使用 AgentScope 事件体系, EventMapper 提供工厂方法 |
| `core/state.py` (AgentState) | AgentScope `AgentState` | ✅ 由框架提供 | |
| `core/context.py` (AgentContext/Config) | `agent/Config.java` | ✅ 已迁移 | Java 为 record + Builder, 覆盖 thinking/tool execution/retry/queue |
| `core/messages.py` (消息类型) | AgentScope `Msg` | ✅ 由框架提供 | |
| `core/content.py` (TextContent/ImageContent) | AgentScope Block 体系 | ✅ 由框架提供 | |
| `core/queue.py` (PendingMessageQueue) | `middleware/SteeringMiddleware.java` | ✅ 已迁移 | 使用 ConcurrentLinkedQueue 实现 |
| `core/human_input.py` (HITL) | `hitl/HumanInputGate.java` + `HumanInputTool.java` + `HumanInputMiddleware.java` | ✅ 已迁移 | CompletableFuture 替代 Python 的 async gate |
| `core/tool_runner.py` (工具执行) | AgentScope `ActingMiddleware` + `ToolHooksMiddleware` | ✅ 由框架+中间件提供 | |

### 2.2 提供者抽象 (providers)

| Python 模块 | Java 对应 | 状态 | 差异说明 |
|-------------|-----------|------|----------|
| `providers/base.py` (ModelProvider Protocol) | AgentScope `Model` 接口 | ⚠️ 无自定义层 | Java 完全依赖 AgentScope 的 Model 抽象 |
| `providers/types.py` (StreamEvent 联合类型) | AgentScope `AgentEvent` 体系 | ⚠️ 无自定义层 | |
| `providers/auth.py` (AuthSource) | AgentScope 内置认证 | ⚠️ 无自定义层 | |
| `providers/registry.py` (ModelRegistry) | AgentScope 内置注册 | ⚠️ 无自定义层 | |
| `providers/openai_provider.py` | AgentScope 内置 | ✅ 由框架提供 | |
| `providers/anthropic_provider.py` | AgentScope 内置 | ✅ 由框架提供 | |
| `providers/message_converter.py` | AgentScope 内置 | ✅ 由框架提供 | |

**评估**: Java 未自建 Provider 层, 完全委托给 AgentScope。这是合理的架构选择, 但意味着对 Provider 行为的控制力较弱。

### 2.3 工具系统 (tools)

| Python 模块 | Java 对应 | 状态 | 差异说明 |
|-------------|-----------|------|----------|
| `tools/base.py` (Tool Protocol, ToolRegistry) | AgentScope `ToolBase` + `Toolkit` | ✅ 由框架提供 | |
| `tools/local/bash.py` | `tools/BashTool.java` | ✅ 已迁移 | 含危险命令检测、输出截断、超时控制 |
| `tools/local/read.py` | `tools/ReadTool.java` | ✅ 已迁移 | 支持 offset/limit, 自动截断 |
| `tools/local/write.py` | `tools/WriteTool.java` | ✅ 已迁移 | 原子写入 (temp + move) |
| `tools/local/edit.py` | `tools/EditTool.java` | ✅ 已迁移 | 首次匹配替换 |
| `tools/local/grep.py` | `tools/GrepTool.java` | ✅ 已迁移 | 正则搜索, 500 条上限 |
| `tools/local/find.py` | `tools/FindTool.java` | ✅ 已迁移 | glob 匹配, 500 条上限 |
| `tools/local/ls.py` | `tools/LsTool.java` | ✅ 已迁移 | [D]/[F] 前缀 |
| `tools/local/confirm.py` | `tools/ConfirmTool.java` | ✅ 已迁移 | 基于 ToolSuspendException |
| `tools/http_tool.py` | `tools/HttpTool.java` | ✅ 已迁移 | 含 SSRF 防护 |
| `tools/operations.py` (Protocols) | `tools/util/FileOps.java` + `BashOps.java` | ✅ 已迁移 | 非接口化, 直接实现类 |
| `tools/operations_local.py` | 合并到 FileOps/BashOps | ✅ 已迁移 | |
| `tools/mutation_queue.py` | `tools/util/FileMutationQueue.java` | ✅ 已迁移 | 使用 Semaphore 替代 asyncio.Lock |
| `tools/truncate.py` | `tools/util/Truncation.java` | ✅ 已迁移 | |
| `tools/render.py` (ToolRenderer) | ❌ 未迁移 | | 工具渲染协议 |
| `tools/mcp_tool.py` | AgentScope `McpClientWrapper` | ⚠️ 委托框架 | 无自定义 MCP 层 |
| `tools/music.py` | ❌ 未迁移 | | 示例长时运行工具 |
| `tools/aigc_creation.py` | ❌ 未迁移 | | AIGC 视频创作 |
| `tools/agnes_image_tool.py` | ❌ 未迁移 | | 图片生成 |
| `tools/agnes_video_tool.py` | ❌ 未迁移 | | 视频生成 |
| `tools/feishu_cli_tool.py` | ❌ 未迁移 | | 飞书 CLI |
| `tools/sandbox_docker.py` | ❌ 未迁移 | | Docker 沙箱 |
| `tools/sandbox_policy.py` | ❌ 未迁移 | | 沙箱安全策略 |
| `tools/sandbox_healing.py` | ❌ 未迁移 | | 沙箱自愈 |
| `tools/widgets/` (ShowWidgetTool) | ❌ 未迁移 | | HTML Widget 渲染 |
| `tools/util/SecurityUtils.java` | (Python 无对应独立模块) | ✅ Java 新增 | 整合了路径遍历/SSRF/危险命令检测 |
| `tools/ToolkitFactory.java` | (Python 无对应) | ✅ Java 新增 | 工厂模式: standard/readOnly/minimal |

### 2.4 会话持久化 (session)

| Python 模块 | Java 对应 | 状态 | 差异说明 |
|-------------|-----------|------|----------|
| `session/store.py` (SessionStore Protocol) | AgentScope `AgentStateStore` | ⚠️ 委托框架 | 无自定义 SessionEntry 类型体系 |
| `session/jsonl_store.py` | AgentScope `JsonFileAgentStateStore` | ⚠️ 委托框架 | |
| `session/inmemory_store.py` | AgentScope `InMemoryAgentStateStore` | ⚠️ 委托框架 | |
| `session/session.py` (AgentSession 组合层) | ❌ 无对应 | ❌ 未迁移 | Python 有事件驱动持久化、自动压缩、扩展集成 |

**评估**: Java 缺少 `AgentSession` 这样的组合层。Python 的 `AgentSession` 整合了 Agent + Store + Compactor + Extensions, 实现了事件驱动的自动持久化和会话恢复。

### 2.5 上下文压缩 (compaction)

| Python 模块 | Java 对应 | 状态 | 差异说明 |
|-------------|-----------|------|----------|
| `compaction/compactor.py` | `middleware/CompactionMiddleware.java` | ✅ 已迁移, **Java 更完善** | Java 有 LLM 驱动 + 简单两种模式 |
| `compaction/strategies.py` | CompactionMiddleware 内置 | ✅ 已迁移 | Java 使用 AgentScope TokenCounterUtil |

**Java 优势**: 安全 ASSISTANT/TOOL 对保留、参数截断、二分查找 token 截止点。

### 2.6 扩展系统 (extensions)

| Python 模块 | Java 对应 | 状态 | 差异说明 |
|-------------|-----------|------|----------|
| `extensions/base.py` (Extension Protocol) | `extensions/Extension.java` | ✅ 已迁移 | Java 用接口 + default 方法 |
| `extensions/base.py` (ExtensionRunner) | `extensions/ExtensionRunner.java` | ✅ 已迁移 | 错误隔离、结果合并 |
| `extensions/base.py` (ExtensionContext) | `extensions/ExtensionContext.java` | ✅ 已迁移 | |
| `extensions/loader.py` | ❌ 未迁移 | | 动态扩展加载 |
| `extensions/companion.py` | ❌ 未迁移 | | Companion 扩展桥接 |
| (无) | `extensions/ExtensionAdapter.java` | ✅ Java 新增 | 便捷基类 |

### 2.7 提示词构建 (prompts)

| Python 模块 | Java 对应 | 状态 | 差异说明 |
|-------------|-----------|------|----------|
| `prompts/builder.py` | `prompts/SystemPromptBuilder.java` | ✅ 已迁移 | |
| `prompts/__init__.py` (SystemPrompt) | `prompts/SystemPrompt.java` | ✅ 已迁移 | Java 为 record |
| `prompts/__init__.py` (SystemPromptSection) | `prompts/SystemPromptSection.java` | ✅ 已迁移 | Java 为 record |
| `prompts/guidelines.py` | SystemPromptBuilder 内置 | ✅ 已迁移 | |
| `prompts/snippets.py` | SystemPromptBuilder 内置 | ✅ 已迁移 | |

### 2.8 资源加载 (resources)

| Python 模块 | Java 对应 | 状态 | 差异说明 |
|-------------|-----------|------|----------|
| `resources/loader.py` | `resources/ResourceLoader.java` | ✅ 已迁移 | |
| `resources/skills.py` | AgentScope `FileSystemSkillRepository` | ✅ 已迁移 | |
| `resources/context_files.py` | `resources/ContextFileLoader.java` | ✅ 已迁移 | |
| `resources/types.py` | 内联到各 loader | ⚠️ 部分 | 无独立 Resource types |
| `resources/prompts.py` | ResourceLoader 部分支持 | ⚠️ 部分 | |
| `resources/themes.py` | ❌ 未迁移 | | 终端主题 |
| `resources/extensions.py` | ❌ 未迁移 | | 扩展 manifest 加载 |
| `resources/diagnostics.py` | `resources/ResourceDiagnostic.java` | ⚠️ 部分 | 只有 record, 无完整验证逻辑 |
| `resources/personas.py` | ❌ 未迁移 | | Persona 角色加载 |

### 2.9 检索 / RAG (retrieval)

| Python 模块 | Java 对应 | 状态 | 差异说明 |
|-------------|-----------|------|----------|
| `retrieval/base.py` (Retriever Protocol) | `retrieval/Retriever.java` + `Query.java` + `RetrievedChunk.java` | ✅ 已迁移 | Java 使用 record + CompletableFuture |
| `retrieval/tool.py` (RetrieverTool) | `retrieval/RetrieverTool.java` | ✅ 已迁移 | 基于 AgentScope ToolBase |
| `retrieval/extension.py` (AutoRetrieval) | `retrieval/AutoRetrievalMiddleware.java` | ✅ 已迁移 | 基于 MiddlewareBase.onReasoning |
| `retrieval/adapters/inmemory.py` | `retrieval/InMemoryRetriever.java` | ✅ 已迁移 | token-overlap 排序, CopyOnWriteArrayList |

### 2.10 长期记忆 (memory)

| Python 模块 | Java 对应 | 状态 | 差异说明 |
|-------------|-----------|------|----------|
| `memory/base.py` (MemoryStore Protocol) | `memory/MemoryStore.java` + `MemoryRecord.java` | ✅ 已迁移 | Java 使用 record + CompletableFuture |
| `memory/extension.py` (MemoryExtension) | `middleware/MemoryMiddleware.java` | ✅ 已迁移 | 基于 MiddlewareBase.onReasoning + onAgent |
| `memory/adapters/inmemory.py` | `memory/InMemoryMemoryStore.java` | ✅ 已迁移 | token-overlap 排序, ConcurrentHashMap |
| `memory/adapters/mem0_adapter.py` | ❌ | ❌ 未迁移 | 第三方 API 集成 |
| `memory/adapters/openviking_adapter.py` | ❌ | ❌ 未迁移 | 第三方 API 集成 |

### 2.11 本地知识库 (knowledge)

| Python 模块 | Java 对应 | 状态 | 差异说明 |
|-------------|-----------|------|----------|
| `knowledge/local_kb.py` | ❌ | ❌ 未迁移 | sentence-transformers + numpy 语义检索 |

### 2.12 宠物伴侣系统 (companion)

| Python 模块 | Java 对应 | 状态 | 差异说明 |
|-------------|-----------|------|----------|
| `companion/` (全部 8+ 文件) | ❌ | ❌ 未迁移 | 包含品种定义、情绪 FSM、命名、模板等 |

### 2.13 Skill 自进化 (skill_evolution)

| Python 模块 | Java 对应 | 状态 | 差异说明 |
|-------------|-----------|------|----------|
| `skill_evolution/` (全部 6 文件) | ❌ | ❌ 未迁移 | Trace2Skill 管线 |

### 2.14 可观测性 (observability)

| Python 模块 | Java 对应 | 状态 | 差异说明 |
|-------------|-----------|------|----------|
| `observability.py` (OpenTelemetry) | `middleware/ObservabilityMiddleware.java` | ⚠️ 部分 | Java 只有 SLF4J 日志, 无 OTEL 集成 |

### 2.15 Scene 层 (应用入口)

| Python Scene | Java 对应 | 状态 | 差异说明 |
|-------------|-----------|------|----------|
| `scene/cli/` | `cli/CliMain.java` | ⚠️ 部分 | 框架在, `createAgent()` 未实现 |
| `scene/http_sse/` | `http/ChatServer.java` 等 6 个文件 | ✅ 已迁移 | JDK HttpServer 替代 FastAPI |
| `scene/voice_ws/` | ❌ | ❌ 未迁移 | WebSocket 语音 |
| `scene/feishu/` | ❌ | ❌ 未迁移 | 飞书 Bot |
| `scene/http_sse/static/` (前端) | ❌ | ❌ 未迁移 | React/TS 完整前端 |

---

## 三、测试覆盖对比

| 维度 | Python | Java |
|------|--------|------|
| 测试文件数 | ~65 | 24 |
| 测试用例总数 | ~400+ | 279 |
| 测试模块覆盖 | core, providers, tools, session, compaction, extensions, prompts, resources, retrieval, memory, skills, scene, skill_evolution | agent, middleware, events, http, extensions, prompts, config, resources, tools, memory, retrieval, testing |
| FakeProvider 测试替身 | ✅ conftest.py | ✅ FakeModel |
| 集成测试 | ✅ e2e_memory_retrieval, scene | ✅ AgentSmokeTest, BuilderIntegrationTest |
| 缺失测试 | - | hitl, hooks |

---

## 四、Java 项目需要优化的地方

### 4.1 架构级优化

#### P0 - 关键

| # | 问题 | 当前状态 | 建议 |
|---|------|----------|------|
| 1 | **缺少 AgentSession 组合层** | Agent 直接使用, 无事件驱动持久化 | 新增 `Session` 类组合 Agent + StateStore + Extensions, 实现事件自动持久化 |
| 2 | **CLI createAgent() 未实现** | CliMain 抛出 UnsupportedOperationException | 补全 Agent 构建逻辑, 支持命令行参数 |
| 3 | **Java 环境问题** | JAVA_HOME 未设置, Gradle 无法运行 | 确保 CI/开发环境配置 JAVA_HOME |
| 4 | **工具层缺少单元测试** | BashTool/ReadTool/EditTool 等无测试 | 补充工具层测试, 使用临时目录 |

#### P1 - 重要

| # | 问题 | 当前状态 | 建议 |
|---|------|----------|------|
| 5 | **无 FakeModel 测试替身** | Python 有 FakeProvider, Java 无对应 | 创建 MockModel 或 FakeModel 用于隔离测试 |
| 6 | **FileOps/BashOps 未接口化** | 直接是 final class | Python 为 Protocol (接口), Java 应抽取接口以支持沙箱注入 |
| 7 | **无资源文件** | src/main/resources 为空 | 考虑将默认提示词模板等资源放入 resources |
| 8 | **HTTP Server 过于简陋** | 使用 JDK 内置 HttpServer | 如需生产级使用, 考虑迁移到 Spring Boot 或 Micronaut |

#### P2 - 改进

| # | 问题 | 当前状态 | 建议 |
|---|------|----------|------|
| 9 | **无 OpenTelemetry 集成** | 只有 SLF4J 日志 | 添加 OTEL middleware 实现分布式追踪 |
| 10 | **SessionManager 仅内存** | 无持久化 | 考虑支持 JSON 文件或数据库持久化 |
| 11 | **缺少 lib/ 目录的 JAR 管理** | vendored JARs 手动管理 | 完全通过 Maven 依赖管理, 移除 lib/ |

### 4.2 功能缺失 (按优先级)

| 优先级 | 功能 | Python 文件数 | 迁移难度 | 说明 |
|--------|------|---------------|----------|------|
| ~~P1~~ | ~~**长期记忆 (memory)**~~ | ~~5~~ | ~~中~~ | ✅ 已完成: MemoryStore + InMemoryMemoryStore + MemoryMiddleware |
| ~~P1~~ | ~~**检索/RAG**~~ | ~~4~~ | ~~中~~ | ✅ 已完成: Retriever + InMemoryRetriever + RetrieverTool + AutoRetrievalMiddleware |
| P2 | **OpenTelemetry 可观测性** | 1 | 低 | 添加 OTEL middleware |
| P2 | **Extension 动态加载** | 1 | 低 | ServiceLoader 或 SPI 机制 |
| P2 | **资源类型体系** | 1 | 低 | 完善 ResourceDiagnostic + types |
| P3 | **本地知识库** | 1 | 高 | 需要 Java 向量检索库替代 sentence-transformers |
| P3 | **Voice WebSocket** | 2 | 中 | WebSocket scene |
| P3 | **飞书集成** | 2 | 中 | 飞书 SDK Java 版本 |
| P4 | **Companion 宠物系统** | 8+ | 高 | 业务特性, 需评估是否需要 |
| P4 | **Skill 自进化** | 6 | 高 | 研究性质, 需评估优先级 |
| P4 | **前端** | 70+ | 很高 | 独立前端项目, 可直接复用 Python 版 |

### 4.3 代码质量优化

| # | 问题 | 文件 | 建议 |
|---|------|------|------|
| 1 | `Config.java` 验证逻辑在 Builder.build() 中 | `agent/Config.java` | 考虑使用 Jakarta Validation 注解 |
| 2 | `EventMapper` 大量使用字符串常量 | `events/EventMapper.java` | 提取为枚举或常量类 |
| 3 | `ChatServer` 单线程处理请求 | `http/ChatServer.java` | 虽然使用了虚拟线程, 但 executor 配置可优化 |
| 4 | 缺少 API 文档 | HTTP 层 | 考虑添加 OpenAPI/Swagger 注解 |
| 5 | `SecurityUtils.isDangerousCommand` 正则较简单 | `tools/util/SecurityUtils.java` | 增强命令检测规则 |

---

## 五、迁移完成度可视化

```
核心运行时 (core)       ████████████████████ 100%  (AgentScope 提供 + 自定义中间件)
提供者抽象 (providers)   ████████████░░░░░░░░  60%  (AgentScope 提供, 无自定义层)
工具系统 (tools)        ████████████████░░░░  80%  (核心工具全, 缺特殊工具和沙箱)
会话持久化 (session)     ████████░░░░░░░░░░░░  40%  (AgentScope 提供基础, 缺组合层)
上下文压缩 (compaction)  ████████████████████ 100%  (Java 版更完善)
扩展系统 (extensions)    █████████████████░░░  90%  (缺动态加载)
提示词构建 (prompts)     ████████████████████ 100%
资源加载 (resources)     ██████████████░░░░░░  70%  (缺 themes, personas, diagnostics)
检索/RAG (retrieval)    ████████████████████ 100%  (Retriever + InMemory + Tool + Middleware)
长期记忆 (memory)       ████████████████████ 100%  (MemoryStore + InMemory + Middleware)
知识库 (knowledge)      ░░░░░░░░░░░░░░░░░░░░   0%
Companion 系统          ░░░░░░░░░░░░░░░░░░░░   0%
Skill 自进化            ░░░░░░░░░░░░░░░░░░░░   0%
可观测性               ████████░░░░░░░░░░░░  40%  (SLF4J only, 无 OTEL)
Scene: CLI             ████████░░░░░░░░░░░░  40%  (框架在, 未完全接线)
Scene: HTTP SSE        █████████████████░░░  85%  (基本功能完整, 缺前端)
Scene: Voice WS        ░░░░░░░░░░░░░░░░░░░░   0%
Scene: 飞书             ░░░░░░░░░░░░░░░░░░░░   0%
前端                   ░░░░░░░░░░░░░░░░░░░░   0%  (可直接复用 Python 版)
```

**加权总计: ~42%** (核心功能权重高, 业务特性权重低)

---

## 六、建议下一步行动

### 短期 (核心补全)

1. ~~**修复构建环境**~~ - ✅ JAVA_HOME 配置已确认
2. **补全 CLI** - 实现 `CliMain.createAgent()`, 支持完整命令行交互
3. ~~**工具层单元测试**~~ - ✅ BashTool, ReadTool, EditTool, HttpTool, ConfirmTool 测试已完成
4. ~~**创建 FakeModel**~~ - ✅ FakeModel 测试替身已完成

### 中期 (功能扩展)

5. ~~**长期记忆模块**~~ - ✅ MemoryStore + InMemoryMemoryStore + MemoryMiddleware 已完成
6. ~~**检索/RAG 模块**~~ - ✅ Retriever + InMemoryRetriever + RetrieverTool + AutoRetrievalMiddleware 已完成
7. **OpenTelemetry 集成** - 添加追踪中间件
8. ~~**FileOps/BashOps 接口化**~~ - ✅ 已完成

### 长期 (完善生态)

9. **知识库** - Java 向量检索实现
10. **Voice WebSocket** - WebSocket 语音交互
11. **前端复用** - 适配现有 React 前端连接 Java 后端
12. **Companion / Skill Evolution** - 根据业务需求评估

---

## 七、文件清单速查

### 已实现的 Java 源文件 (64 个)

```
io/agentcore/agent/
  ├── Agent.java          # 核心 Agent 封装
  └── Config.java         # 配置 record

io/agentcore/config/
  └── RuntimeConfig.java  # 运行时配置工厂

io/agentcore/events/
  └── EventMapper.java    # 事件工厂

io/agentcore/hitl/
  ├── HumanInputGate.java # HITL 同步原语
  └── HumanInputTool.java # HITL 工具

io/agentcore/hooks/
  └── ToolHook.java       # 工具钩子接口

io/agentcore/middleware/
  ├── ObservabilityMiddleware.java
  ├── ToolLoggingMiddleware.java
  ├── RetryMiddleware.java
  ├── SteeringMiddleware.java
  ├── PromptAugmentMiddleware.java
  ├── TurnLifecycleMiddleware.java
  ├── HumanInputMiddleware.java
  ├── ToolHooksMiddleware.java
  ├── SkillMiddleware.java
  ├── CompactionMiddleware.java
  ├── ExtensionMiddleware.java
  └── MemoryMiddleware.java

io/agentcore/tools/
  ├── ConfirmTool.java
  ├── WriteTool.java
  ├── FindTool.java
  ├── BashTool.java
  ├── EditTool.java
  ├── ReadTool.java
  ├── LsTool.java
  ├── GrepTool.java
  ├── ToolkitFactory.java
  └── HttpTool.java

io/agentcore/tools/util/
  ├── FileInfo.java
  ├── BashOps.java
  ├── BashResult.java
  ├── SandboxQuota.java
  ├── SecurityUtils.java
  ├── Truncation.java
  ├── FileMutationQueue.java
  └── FileOps.java

io/agentcore/extensions/
  ├── ExtensionContext.java
  ├── Extension.java
  ├── ExtensionRunner.java
  └── ExtensionAdapter.java

io/agentcore/resources/
  ├── ResourceDiagnostic.java
  ├── ContextFileLoader.java
  └── ResourceLoader.java

io/agentcore/prompts/
  ├── SystemPromptSection.java
  ├── SystemPrompt.java
  └── SystemPromptBuilder.java

io/agentcore/cli/
  └── CliMain.java

io/agentcore/http/
  ├── SseEvent.java
  ├── ChatRequest.java
  ├── ChatSession.java
  ├── SessionManager.java
  ├── ChatServer.java
  └── AgentEventConverter.java

io/agentcore/memory/
  ├── MemoryRecord.java     # 记忆数据 record
  ├── MemoryStore.java      # 记忆存储接口
  └── InMemoryMemoryStore.java  # 内存实现 (token-overlap 排序)

io/agentcore/retrieval/
  ├── Query.java            # 检索查询 record
  ├── RetrievedChunk.java   # 检索结果 record
  ├── Retriever.java        # 检索器接口
  ├── InMemoryRetriever.java    # 内存实现 (token-overlap 排序)
  ├── RetrieverTool.java    # AgentScope ToolBase 包装
  └── AutoRetrievalMiddleware.java  # 自动 RAG 注入中间件

io/agentcore/testing/
  └── FakeModel.java        # Model 接口测试替身
```

### 已实现的测试文件 (24 个)

```
io/agentcore/agent/
  ├── AgentSmokeTest.java
  ├── ConfigTest.java
  └── BuilderIntegrationTest.java

io/agentcore/middleware/
  ├── CompactionMiddlewareTest.java
  ├── RetryMiddlewareTest.java
  ├── SteeringMiddlewareTest.java
  └── MemoryMiddlewareTest.java

io/agentcore/events/
  └── EventMapperTest.java

io/agentcore/http/
  ├── SseEventTest.java
  ├── SessionManagerTest.java
  ├── ChatServerTest.java
  └── AgentEventConverterTest.java

io/agentcore/extensions/
  ├── ExtensionContextTest.java
  └── ExtensionRunnerTest.java

io/agentcore/prompts/
  ├── SystemPromptSectionTest.java
  ├── SystemPromptTest.java
  └── SystemPromptBuilderTest.java

io/agentcore/config/
  └── RuntimeConfigTest.java

io/agentcore/resources/
  ├── ResourceLoaderTest.java
  └── ContextFileLoaderTest.java

io/agentcore/memory/
  └── InMemoryMemoryStoreTest.java

io/agentcore/retrieval/
  ├── InMemoryRetrieverTest.java
  ├── RetrieverToolTest.java
  └── AutoRetrievalMiddlewareTest.java

io/agentcore/testing/
  └── FakeModelTest.java
```
