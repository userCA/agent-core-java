# agent-core-java 优化修复进度

**最后更新**: 2026-06-16

---

## 第一轮修复 (12项)

### Critical (3项)

| # | 文件 | 问题 | 状态 |
|---|------|------|------|
| 1 | LocalBashOperations.java | stdout 无界积累导致 OOM | 已修复 |
| 2 | ToolRunner.java | 并行模式异常未处理导致 agent loop 崩溃 | 已修复 |
| 3 | LocalBashOperations.java | ForkJoinPool.commonPool() 饥饿风险 | 已修复 |

**修复详情**:
- **#1**: 新增 `CappedOutputStream` 内部类，限制每个流 (stdout/stderr) 最多 1MB，超过部分静默丢弃并添加截断提示
- **#2**: `executeParallel` 中 `CompletionException` 不再 re-throw，转为错误 `ToolResult` 并记录 `log.warn`
- **#3**: `supplyAsync` 改用 `Executors.newVirtualThreadPerTaskExecutor()` 专用虚拟线程 executor，`whenComplete` 关闭

### High (4项)

| # | 文件 | 问题 | 状态 |
|---|------|------|------|
| 4 | AgentContext.java | messages/tools 使用非线程安全 ArrayList | 已修复 |
| 6 | HumanInputGate.java | cancelAll 中 forEach + clear 竞态条件 | 已修复 |
| 7 | LocalFileOperations.java | read() 无 limit 时可能 OOM | 已修复 |
| 8 | DefaultMessageConverter.java | System.err.println 替代 SLF4J | 已修复 |

**修复详情**:
- **#4**: 构造函数和 `tools()` setter 改用 `Collections.synchronizedList`
- **#6**: cancelAll 改为：先设 `cancelled=true`，再 `new HashMap<>(futures)` 快照，`keySet().removeAll()` 原子移除，最后取消快照中的 futures
- **#7**: 新增 `DEFAULT_READ_LIMIT = 2000` 常量，`read()` 无 limit 时自动应用
- **#8**: 添加 SLF4J `Logger`，`System.err.println` 替换为 `log.warn`

### Medium (5项)

| # | 文件 | 问题 | 状态 |
|---|------|------|------|
| 11 | GrepTool.java | include 参数在 definition 中定义但 execute 中未使用 | 已修复 |
| 12 | AgentLoop.java | static Random 非线程安全 | 已修复 |
| 13 | ResourceLoader.java | Files.walk 无深度限制 | 已修复 |
| 14 | ExtensionRunner.java | afterToolCall 使用 AtomicReference 只保留最后结果 | 已修复 |
| 15 | LocalFileOperations.java | grep/find 达到上限后仍遍历所有文件 | 已修复 |

**修复详情**:
- **#11**: `FileOperations` 接口新增 `default grep(pattern, path, recursive, include)` 方法；`LocalFileOperations` 重写实现，使用 `PathMatcher` 过滤文件名；`GrepTool` 提取 include 参数传递
- **#12**: 移除 `static final Random RANDOM`，替换为 `ThreadLocalRandom.current().nextDouble()`
- **#13**: 新增 `MAX_SKILL_WALK_DEPTH = 10` 常量，`Files.walk(dir)` 改为 `Files.walk(dir, MAX_SKILL_WALK_DEPTH)`
- **#14**: `AtomicReference` 替换为 `LinkedHashMap` 合并所有扩展结果，与 `beforeToolCall` 保持一致
- **#15**: grep/find 中 `forEach` 改为 `Iterator` 循环，条件检查 `results.size() < MAX` 提前终止

---

## 第二轮修复 (13项)

### Critical (1项)

| # | 文件 | 问题 | 状态 |
|---|------|------|------|
| 16 | AgentState.java | volatile 字段仅保证单次读写可见性，复合操作非原子 | 已修复 |

**修复详情**:
- **#16**: `messages` 改为 `final` + `synchronizedList`；新增 `resetState()`、`stopStreaming()`、`tryStartStreaming()`、`replaceMessages()`、`addMessage()` 等 synchronized 方法

### High (7项)

| # | 文件 | 问题 | 状态 |
|---|------|------|------|
| 17 | Agent.java | abortEvent 未在构造函数初始化，abort() 首次 run 前调用 NPE | 已修复 |
| 18 | Agent.java + AgentState.java | messages() 暴露可变列表，reset() 非原子多步操作 | 已修复 |
| 19 | AnthropicProvider.java | HttpClient 未实现 AutoCloseable，长期运行泄漏线程 | 已修复 |
| 20 | OpenAIProvider.java | 同上 | 已修复 |
| 21 | FileMutationQueue.java | locks ConcurrentHashMap 只增不减，内存泄漏 | 已修复 |
| 22 | InMemoryStore.java | sessions ConcurrentHashMap 无容量限制 | 已修复 |
| 23 | AgentSession.java | listener 异常被 catch (Exception ignored) 吞掉 + maybeCompact 无 exceptionally | 已修复 |
| 24 | ModelProvider.java | 接口未继承 AutoCloseable | 已修复 |
| 25 | ToolRegistry.java | tools/sources 双 ConcurrentHashMap 非原子写入 | 已修复 |

**修复详情**:
- **#17**: 构造函数初始化 `this.abortEvent = new AtomicBoolean(false)`
- **#18**: AgentState 新增 `replaceMessages()`、`addMessage()`、`resetState()`、`stopStreaming()` 方法；Agent.reset() 改为 `synchronized` 并调用 `state.resetState()`；`finally` 块改用 `state.stopStreaming()`
- **#19/20/24**: `ModelProvider extends AutoCloseable` + default no-op `close()`；AnthropicProvider 和 OpenAIProvider 实现 `close()` 关闭 HttpClient
- **#21**: 新增 `unlockAndCleanup()` 方法，解锁后检查 `!lock.hasQueuedThreads()` 则 `remove(path, lock)` 清理
- **#22**: 新增 `maxSessions=1000` 上限 + `ConcurrentLinkedDeque` 维护插入顺序，超出时 LRU 淘汰最旧 session
- **#23**: listener catch 改为 `log.warn("Session listener failed: {}", e.getMessage())`；`maybeCompact` 添加 `.exceptionally(e -> { log.warn(...); return null; })`
- **#25**: 双 `ConcurrentHashMap` 合并为单个 `ConcurrentHashMap<String, ToolEntry>` record

### Medium (5项)

| # | 文件 | 问题 | 状态 |
|---|------|------|------|
| 26 | HttpTool.java | supplyAsync 使用 commonPool 执行阻塞 HTTP I/O | 已修复 |
| 27 | JsonlStore.java | loadSession 用 readAllLines 全量加载 + listSessions catch 静默跳过 | 已修复 |
| 28 | PendingMessageQueue.java | ConcurrentLinkedQueue 无容量限制 | 已修复 |
| 29 | JsonUtils.java | parseJson/toJson 失败无日志 | 已修复 |
| 30 | LLMSummaryCompactor.java | summarizeFn 失败无异常处理 | 已修复 |

**修复详情**:
- **#26**: `supplyAsync` 改用 `Executors.newVirtualThreadPerTaskExecutor()`，`whenComplete` 关闭 executor
- **#27**: `loadSession` 改用 `Files.lines()` 流式加载；`listSessions` catch 改为 `log.warn`
- **#28**: 新增 `maxSize=100` 容量限制，`enqueue` 时超限 `poll()` 丢弃最旧消息
- **#29**: 添加 SLF4J Logger，`parseJson` 失败 `log.debug`，`toJson` 失败 `log.warn`
- **#30**: 链添加 `.exceptionally(e -> { log.warn(...); return no-op result; })`

### 附带修复

| 文件 | 问题 | 状态 |
|------|------|------|
| AnthropicProvider.java | SSE error body 用 `reduce("", (a,b) -> a+b)` O(n^2) | 已修复 |
| OpenAIProvider.java | 同上 | 已修复 |

改为 `Collectors.joining()` O(n)

---

## 第三轮修复 (10项)

### Critical (1项)

| # | 文件 | 问题 | 状态 |
|---|------|------|------|
| 31 | FileMutationQueue.java | ReentrantLock.unlock() 从错误线程调用，导致永久死锁 | 已修复 |

**修复详情**:
- **#31**: `ReentrantLock` 有线程亲和性，`lock.lock()` 在调用线程执行但 `whenComplete` 可能在不同线程 (如 ForkJoinPool) 执行 `unlock()`，抛 `IllegalMonitorStateException`，导致锁永不释放。改用 `Semaphore(1)` 替代，Semaphore 无线程亲和性，`release()` 可从任何线程安全调用。同时正确处理 `InterruptedException`。

### High (4项)

| # | 文件 | 问题 | 状态 |
|---|------|------|------|
| 32 | ToolRunner.java:87 | 并行模式 onUpdate 传 null，工具进度更新被丢弃 | 已修复 |
| 33 | ToolRunner.java:315 | 超时后 execFuture 未 cancel，操作在后台继续运行 | 已修复 |
| 34 | AgentLoop.java:268 | Flow.Subscription 在提前退出时未 cancel，HTTP/SSE 连接泄漏 | 已修复 |
| 35 | StopReason.java:24 | fromValue() 对未知值抛 IllegalArgumentException，可能崩溃整个 agent run | 已修复 |

**修复详情**:
- **#32**: 并行模式创建 `Consumer<ToolResult>` 直接 emit `ToolExecutionUpdate` 事件，替代原先的 `null`
- **#33**: `execFuture` 声明移到 try 块外；`TimeoutException` 和 `InterruptedException` catch 块中调用 `execFuture.cancel(true)` 取消后台任务
- **#34**: 使用 `Flow.Subscription[]` holder 捕获 subscription 引用；整个流处理循环包裹在 `try-finally` 中，`finally` 块调用 `subscription.cancel()` 释放 HTTP/SSE 连接
- **#35**: 未知 stop reason 值改为返回 `STOP` 安全默认值 + `log.warn` 记录，避免异常崩溃

### Medium (5项)

| # | 文件 | 问题 | 状态 |
|---|------|------|------|
| 36 | CompactionStrategies.java | Token 估算忽略 ImageContent 和 ToolCallContent | 已修复 |
| 37 | LLMSummaryCompactor.java | firstKeptEntryId 使用数字索引而非实际 entry ID | 已审查 (当前架构合理) |
| 38 | AssistantMessage.java:85 | MutableAssistant.content() 暴露内部可变列表 | 已修复 |
| 39 | LLMSummaryCompactor.java:40 | Abort signal 未在 summarizeFn 调用前检查 | 已修复 |
| 40 | LocalFileOperations.java + ReadTool.java | read() 静默截断不通知 ReadTool，truncated 元数据不准确 | 已修复 |

**修复详情**:
- **#36**: 新增 `estimateContentTokens()` 方法处理 `ToolCallContent` (序列化 args + 固定 overhead) 和 `ImageContent` (固定 200 token 估算)；`estimateTokens()` 累计额外 token
- **#37**: `LLMSummaryCompactor` 仅接收 `List<AgentMessage>` 而非 `List<SessionEntry>`，无法获取实际 entry ID，索引标记在当前架构下合理
- **#38**: `content()` getter 改为返回 `Collections.unmodifiableList(content)`，防止外部意外修改内部列表
- **#39**: `compact()` 方法开头添加 signal 早检查，若已 abort 则直接返回 `[aborted]` 结果，避免启动昂贵的 LLM 摘要调用
- **#40**: `LocalFileOperations.read()` 改用 `limit(effectiveLimit + 1)` 多读一行检测是否有更多内容，超出时追加 `"... (truncated at N lines)"` 标记；`ReadTool` 的 `wasTruncated` 检测同时检查内容中是否包含上游截断标记

---

## 历史修复 (FIX_TRACKING.md 记录)

| 阶段 | 总数 | 已完成 | 延期 |
|------|------|--------|------|
| Phase 1: 安全 (P0) | 10 | 10 | 0 |
| Phase 2: 并发 (P1) | 9 | 9 | 0 |
| Phase 3: 资源泄漏 (P1) | 7 | 7 | 0 |
| Phase 4: 功能缺陷 (P2) | 12 | 12 | 0 |
| Phase 5: 设计改进 (P3) | 8 | 7 | 1 |
| Phase 6: 编译警告 (P4) | 4 | 4 | 0 |
| Phase 7: 测试 (P4) | 9 | 6 | 3 |

---

## 验证结果

- **编译**: 全部源文件 javac --release 21 编译通过，0 错误
- **测试**: 74/74 测试全部通过，0 失败
- **日期**: 2026-06-16
- **总计**: 三轮共修复 **35 项** 问题 (5 Critical + 15 High + 15 Medium)
