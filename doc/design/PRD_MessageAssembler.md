# PRD: MessageAssembler — 消息组装职责上移至 Agent 层

## 背景

当前 `AgentLoop` 内部直接调用 `config.convertToLlm().convert(context.messagesSnapshot())` 完成消息组装，耦合了"消息准备"与"核心循环"两个职责。按照"核心做薄、外层做厚"的设计原则，AgentLoop 应仅驱动 LLM 调用→工具执行→循环控制，消息组装（含格式转换、记忆注入、上下文增强）应由外层 Agent harness 统一管理。

## 目标

1. AgentLoop 不再依赖 `convertToLlm`，改为依赖抽象的 `MessageAssembler` 回调
2. Agent 层实现 `MessageAssembler`，组合完整的消息准备管线（上下文增强 → provider 格式转换）
3. AgentLoop 保持薄核心：拿到 `List<Map<String, Object>>` 就调 LLM，不关心怎么来的

## 设计

### 新增接口

```java
@FunctionalInterface
public interface MessageAssembler {
    List<Map<String, Object>> assemble(List<Message> messages);
}
```

### 调用流程

```mermaid
sequenceDiagram
    participant Agent as Agent (harness)
    participant Loop as AgentLoop (thin core)
    participant LLM as LLM Provider

    Agent->>Agent: 构建 MessageAssembler 实现<br/>(记忆注入 + convertToLlm)
    Agent->>Loop: 传入 config(messageAssembler)
    
    loop 每个 turn
        Loop->>Agent: assembler.assemble(messages)
        Agent-->>Loop: List<Map<String, Object>>
        Loop->>LLM: stream(messages, tools, ...)
        LLM-->>Loop: AssistantMessage
        Loop->>Loop: 执行工具 / 判断终止
    end
```

### 变更清单

| 文件 | 变更 |
|------|------|
| `AgentLoopConfig` | 删除 `ConvertToLlm` 接口和 `convertToLlm` 字段，替换为 `MessageAssembler` + `messageAssembler` 字段（required） |
| `AgentLoop` | `prepareLlmMessages()` 改为调用 `config.messageAssembler().assemble(context.messagesSnapshot())` |
| `Agent` | 持有 converter 实例，在 `buildBaseConfigWithHooks()` 中构建 `MessageAssembler`（包装 converter + 预留扩展点） |
| `Agent.create()` | 将 converter 包装为 `MessageAssembler` 传入 builder |
| `AgentConfig` | `.convertToLlm(converter)` → `.messageAssembler(converter::convert)` |
| 测试文件 | `.convertToLlm(...)` → `.messageAssembler(...)` |

### Agent 层实现示例

```java
// Agent.buildBaseConfigWithHooks() 中
var converter = /* 从 baseConfig 或实例字段获取 */;
MessageAssembler assembler = messages -> {
    // 未来扩展点：记忆注入、上下文增强等
    return converter.convert(messages);
};
b.messageAssembler(assembler);
```

### 不变更

- `StreamAccumulator` — 只接收已组装好的 `List<Map<String, Object>>`
- `AgentContext` — 不变
- `MessageConverter` / `AnthropicMessageConverter` — provider 级转换器不变，由 Agent 层持有

## 影响范围

⚡ 影响范围：`AgentLoopConfig`、`AgentLoop`、`Agent`、`AgentConfig`、测试文件（`CoreModelTest`、`AgentLoopIntegrationTest`、`AgentHttpServerTest`）
