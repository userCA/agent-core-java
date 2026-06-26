# Changelog

## 2026-06-26 10:25 — refactor: agentcore 命名规范审计批量修复

**问题/需求**: agentcore 核心框架中存在多处命名不规范问题，包括 `get` 前缀的 Supplier 字段、Boolean 字段缺少 `is`/`should` 前缀、Hook 方法缺少 `on` 前缀、语义不明的字段名、动词式接口名等。

**方案**: 按 P0/P1/P2 优先级分批修复，统一命名风格：
- **P0-1**: `getSteeringMessages`/`getFollowUpMessages` → `steeringMessageSupplier`/`followUpMessageSupplier`
- **P0-2**: Boolean 字段加 `is`/`should` 前缀 (`retryableError`→`isRetryableError`, `terminate`→`shouldTerminate`)
- **P0-3**: Extension Hook 方法加 `on` 前缀 (`beforeToolCall`→`onBeforeToolCall`, `afterToolCall`→`onAfterToolCall`)
- **P0-4**: `ToolContext.signal` → `abortSignal`
- **P1-5**: `TurnContext.newMessages` → `producedMessages`
- **P1-6**: `RetryDecision.newRetryCount` → `nextRetryCount`
- **P1-7**: 局部变量 `newMessages` → `reassembledMessages`（消除同名歧义）
- **P1-8**: `Agent.produced` → `producedMessages`
- **P1-9**: `Agent.pendingToolCalls()` → `pendingToolCallIds()`
- **P1-10**: `allTerminate` → `allTerminated`
- **P2-11**: 接口命名改为名词短语 (`StreamFunction`→`LlmStreamProvider`, `CompactCallback`→`ContextCompactor`, `ShouldStopAfterTurn`→`TurnStopPredicate`)

**改动范围**: ~15 文件，包括 AgentLoopConfig、AgentLoop、Agent、ToolRunner、Message、ToolContext、Extension、ExtensionRunner、HookTypes、StreamAccumulator、BashTool、AgentSession 及测试文件。

**影响面**: 纯重命名，无功能变化。所有测试通过。

## 2026-06-25 23:53 — rename: newMessagesProduced → producedMessages

**问题/需求**: `newMessagesProduced` 命名拗口，"new" 多余且被动语序不自然。

**方案**: 重命名为 `producedMessages`，符合名词短语命名规范。

**改动范围**: `AgentLoop.java` 内部变量名，12 处替换。

**影响面**: 纯重命名，无功能变化。

## 2026-06-25 23:38 — refactor: inline prepareLlmMessages method

**问题/需求**: `prepareLlmMessages()` 方法体仅一行委托调用，`signal` 参数未使用，无附加逻辑，属于不必要的抽象。

**方案**: 内联到 2 处调用点，删除该方法。

**改动范围**: `AgentLoop.java` 删除 `prepareLlmMessages()`，调用处直接写 `config.messageAssembler().assemble(...)`。

**影响面**: 无功能变化，仅代码简化。

## 2026-06-25 23:35 — refactor: replace ConvertToLlm with MessageAssembler

**问题/需求**: AgentLoop 内部直接调用 `convertToLlm().convert()` 完成消息组装，耦合了"消息准备"与"核心循环"两个职责，违背"核心做薄、外层做厚"的设计原则。

**根因/方案**: 引入 `MessageAssembler` 函数接口替代 `ConvertToLlm`，将消息组装职责上移至 Agent 层。AgentLoop 仅依赖抽象回调获取已组装的 LLM 消息，Agent 层在 `buildBaseConfigWithHooks()` 中组合 provider 格式转换与未来扩展点（记忆注入、上下文增强）。

**改动范围**:
- `AgentLoopConfig`: 删除 `ConvertToLlm` 接口，新增 `MessageAssembler` 接口
- `AgentLoop`: `prepareLlmMessages()` 改用 `messageAssembler().assemble()`
- `Agent`: 新增 `messageAssembler` 字段，`buildBaseConfigWithHooks()` 中包装为扩展点
- `AgentConfig`: `createLoopConfig()` 改用 `messageAssembler`
- 测试文件: 3 个文件机械替换

**影响面**: AgentLoop 不再感知 provider 级消息转换逻辑，消息组装职责完全由 Agent 层管理。
