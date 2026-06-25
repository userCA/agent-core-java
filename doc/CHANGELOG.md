# Changelog

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
