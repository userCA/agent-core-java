# Changelog

## 2026-06-29 — refactor: Skill模型统一与Harness层集成优化

**问题/需求**: Skill 模块存在两套模型（`skill.Skill` vs `SystemPromptBuilder.Skill`）、Agent 运行时零加载 Skill、硬编码 `.pi` 路径、格式化不一致。

**方案**:
- `Skill` record 新增 `content` 字段（对齐 pi-mono Skill 结构）
- `SkillLoader.loadSkillFromFile()` 将 frontmatter body 填入 `content`
- 删除 `SystemPromptBuilder` 内部重复的 `Skill` record，统一使用 `io.agentcore.skill.Skill`
- `SystemPromptBuilder` skill section 委托 `SkillLoader.formatSkillsForPrompt()` 渲染
- `AgentConfig.createAgent()` 在 Harness 层加载 skills 并注入 system prompt，Agent/AgentLoop 保持薄层
- 全局替换硬编码 `.pi` 路径为 `.agent-core`（SkillLoader、ResourceLoader、PersonaLoader）

**改动范围**: `Skill.java`、`SkillLoader.java`、`SystemPromptBuilder.java`、`AgentConfig.java`、`ResourceLoader.java`、`PersonaLoader.java`、`SystemPromptBuilderTest.java`、`SkillLoaderTest.java`、`ResourceLoaderTest.java`

**影响面**: 无外部 API 变化，纯内部重构。

⚡ 影响范围：`skill`、`prompt`、`config`、`resources`

## 2026-06-26 — refactor: 消除Agent透传方法与过度分层

**问题/需求**: `Agent.addExtensions()` 是对 `ExtensionRunner` 的无意义透传；`buildBaseConfigWithHooks` 与 `buildConfigWithHooks` 分两层但无复用价值。

**方案**:
- 移除 `Agent.addExtensions()`，`AgentSession` 改为 `agent.extensionRunner().addExtensions(...)`
- 合并 `buildBaseConfigWithHooks` 到 `buildConfigWithHooks`，消除单调用者的私有方法分层
- 移除因本次变更已无调用者的 `AgentLoopConfig.withCompactCallback()`
- 同步更新 `ObservabilityExtension` 文档示例

**改动范围**: `Agent.java`、`AgentLoopConfig.java`、`AgentSession.java`、`ObservabilityExtension.java`

**影响面**: 纯内部重构，无外部 API 变化。

⚡ 影响范围：`agent`、`session`、`observability`

## 2026-06-26 — fix: Playground快速导航栏点击无响应

**问题/需求**: Playground页面顶部快速导航栏的锚点链接点击无效，无法跳转到对应Section。

**方案**: 为每个导航链接添加onClick事件处理，使用`element.scrollIntoView({ behavior: 'smooth', block: 'start' })`实现平滑滚动到目标区域，替代默认锚点跳转行为。确保导航功能在各种浏览器环境下稳定工作。

**改动范围**: `Playground.tsx`

**影响范围**: 仅Playground页面的快速导航功能，不影响其他组件

---

## 2026-06-26 — fix: 前端组件UX/UX优化，修复7项问题

**问题/需求**: 对照 ui-ux-pro-max UX 指南和设计系统，发现前端组件存在多项可用性问题。

**方案**:
- CodeBlock 复制按钮触摸目标从 24x24 增大到 32x32，改善移动端点击体验
- GitDiffBlock +N/-N 统计色从 Tailwind 默认色(emerald-600/rose-500)改为设计系统色(accent-sage/accent-coral)
- TypingIndicator 改用 AIAvatar 组件，与其他 AI 消息头像保持一致
- SearchResultBlock 搜索结果移除无实际功能的 cursor-pointer，避免误导用户
- ChatScreen companion 按钮移除无限循环动画(pet-bounce)，符合“无限动画仅用于加载指示器”规范
- index.css 全局添加 touch-action: manipulation，消除移动端 300ms 点击延迟
- HistoryScreen 搜索框添加 inputMode="search"，移动端弹出搜索键盘

**改动范围**: `CodeBlock.tsx`、`GitDiffBlock.tsx`、`TypingIndicator.tsx`、`SearchResultBlock.tsx`、`ChatScreen.tsx`、`HistoryScreen.tsx`、`index.css`

**影响范围**: 各组件独立修改，无破坏性变更，App 主页行为不变

---

## 2026-06-26 — fix: Playground页面全面优化，补全缺失组件demo

**问题/需求**: Playground页面存在多项UI/UX问题：body全局样式污染布局、缺失大量CSS组件demo、缺少语法高亮色和动画展示、无快速导航。

**方案**:
- 将body的flex居中+背景色提取为`.app-stage`类，仅在App主页路由使用，消除对Playground的样式污染
- 补全缺失的CSS组件demo：phone-frame手机容器、journal-card日记卡片、dashed-divider虚线分隔、streaming-card基础容器、trace CSS推理时间线、screen页面转场、overlay遮罩+bottom-sheet底部抽屉（可交互）
- 新增「代码语法高亮色」Section，展示7个token-*语法着色类及实际代码效果
- 补全缺失动画demo：shimmer骨架屏闪烁、pulse-ring脉冲环、skill-stripe条纹滚动，动画分区细分为入场/循环/特效三组
- 修复字体展示区font-sans冗余描述文字
- 添加sticky快速导航栏，14个锚点一键跳转

**改动范围**: `frontend/src/index.css`、`frontend/src/main.tsx`、`frontend/src/pages/Playground.tsx`

**影响面**: Playground页面布局大幅改善，App主页行为不变，无破坏性变更

---

## 2026-06-26 — feat: 前端新增PlayGround组件基准页

**问题/需求**: 前端项目缺少PlayGround页面，不符合rules.md第14条要求，持续开发中无法直观校验组件视觉一致性。

**方案**:
- 启用react-router路由，`/`为主应用，`/playground`为PlayGround页
- 创建PlayGround页面，分区展示：色彩体系、字体体系、按钮系统、消息气泡、图标组件、流式卡片、推理线程、打字指示器、表单元素、动画展示、圆角与阴影参考
- 删除Vite脚手架遗留文件（App.css、Home.tsx）
- 重新生成package-lock.json（原lock文件引用了不可用的mirror）

**改动范围**: `frontend/src/main.tsx`、`frontend/src/pages/Playground.tsx`（新增）、`frontend/src/App.css`（删除）、`frontend/src/pages/Home.tsx`（删除）、`frontend/package-lock.json`

**影响面**: 前端新增路由和PlayGround页，不影响现有App.tsx和所有组件功能。

## 2026-06-26 — refactor: 重构Agent事件分发与Extension加载机制

**问题/需求**: AgentEventDispatcher作为独立类职责不清，Extension加载逻辑散落在Runner中，缺乏独立加载器。

**方案**:
- 移除`AgentEventDispatcher`，事件分发逻辑内聚到Agent层
- 新增`ExtensionLoader`，扩展加载职责独立
- 重构`ExtensionRunner`，简化扩展执行流程
- 适配`SelfHealingExtension`、`ObservabilityExtension`
- 调整`AgentSession`会话管理逻辑
- 设计文档整理至`doc/design/`目录
- 新增对话原型页面（claude/cursor/minimax风格）
- `.gitignore`增加`.qoder/`排除规则

**改动范围**: `Agent.java`、`ExtensionRunner.java`、`ExtensionLoader.java`（新增）、`SelfHealingExtension.java`、`ObservabilityExtension.java`、`AgentSession.java`、对应测试文件、`doc/design/`、`doc/prototype/`

**影响面**: Agent层事件分发更内聚，Extension体系更清晰，无外部API破坏性变更。

⚡ 影响范围：`agent`、`extensions`、`session`、`observability`

## 2026-06-26 14:45 — refactor: 前端工程独立目录迁移

**问题/需求**: 前端 React 工程位于 `doc/designed_by_Kimi/app/`，位置不合理（doc 应放文档而非完整工程），命名以工具名命名不表达内容含义。

**方案**: 迁移为独立前端工程：
- `doc/designed_by_Kimi/app/` → `frontend/`
- 删除 `doc/designed_by_Kimi/` 旧目录
- `package.json` name 从 `my-app` 修正为 `agent-core-chat`
- 移除 `kimi-plugin-inspect-react` 依赖
- `index.html` title 更新为 `Agent Core Chat — 咪兔`
- 根目录 `.gitignore` 添加 `frontend/node_modules/`、`frontend/dist/` 规则
- 设计系统文档随工程一起迁移，无硬编码路径无需修改

**改动范围**: 目录迁移 + `.gitignore`、`package.json`、`index.html`、`info.md` 更新。

**影响面**: 纯目录结构调整，无功能变化。前端工程独立运行，与 Java 后端解耦。

⚡ 影响范围：`frontend/`、`.gitignore`

## 2026-06-26 14:30 — refactor: Agent.java Harness 层重构

**问题/需求**: Agent.java（535 行）混合了 config 构建、hook 装配、队列管理、ToolRunner/StreamAccumulator 生命周期、12 个 prompt 重载等职责，不符合「Harness 厚编排层、Loop 薄循环」原则。经全量 grep 验证，~17 个公共方法零外部调用者。

**方案**: 删除死代码 + 内部重构：
- **删除死代码**：移除 2 个构造函数重载、6 个 async 方法、2 个 Message 类型 prompt 重载，共删除 ~10 个零调用者方法
- **保留队列管理公共 API**（steer/followUp 等 8 个方法）供未来对话场景承接追加提问
- **提取 ToolCallTracker** 为独立 package-private 类
- **提取 AgentResources** 管理 ToolRunner/StreamAccumulator 懒创建与销毁，隔离资源生命周期
- **重构 runLoop()** 拆分为 6 个职责清晰的私有方法：`resetRunState`→`buildEventPipeline`→`prepareLoop`→`executeLoop`→`handleRunFailure`→`finalizeRun`

**改动范围**: `Agent.java` 从 535 行减至 423 行，公共 API 从 ~30 个精简到 ~20 个。新增 `AgentResources.java`、`ToolCallTracker.java`。

**影响面**: 纯内部重构，所有现有调用者零修改。全量测试通过。

## 2026-06-26 12:26 — refactor: AgentLoop 双层循环简化为单层循环

**问题/需求**: AgentLoop 使用嵌套双层循环（内层处理工具调用 + steering，外层处理 follow-up），状态变量在层间传递（`pendingMessages`、`hasMoreToolCalls`、`TurnOutcome`），4 处重复 `AgentEnd` 发射，控制流复杂度高。

**方案**: 采用单层 `while(true)` + `continue`/`break` 控制流（与 Python 参考实现对齐）：
- `run()`: 消除内层循环和 `pendingMessages` 变量，steering/follow-up 在主循环中顺序检查
- `executeTurn()`: 移除 `pendingMessages` 参数，steering 注入职责上移至主循环
- `TurnOutcome` → `TurnResult`：语义更清晰
- 统一退出路径：所有退出走 `break` → 单一 `AgentEnd` 发射点

**改动范围**: `AgentLoop.java`，净减 19 行。

**影响面**: 纯控制流重构，无功能变化。所有测试通过。

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
