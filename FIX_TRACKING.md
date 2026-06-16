# Fix Progress Tracking

**Project**: agent-core-java
**Created**: 2026-06-15
**Last Updated**: 2026-06-15

---

## Status Legend

| Symbol | Meaning |
|--------|---------|
| [ ] | Not Started |
| [~] | In Progress |
| [x] | Completed |
| [-] | Won't Fix / Deferred |

---

## Phase 1: Critical Security Fixes (P0)

> Goal: Eliminate security vulnerabilities that could lead to system compromise or data breach.

### 1.1 Command Injection Prevention
- [x] **SEC-01a**: Integrate `SandboxQuota` into `LocalBashOperations`
  - File: `src/main/java/io/agentcore/tools/operations/LocalBashOperations.java`
  - Tasks: Add command whitelist/blacklist, enforce timeout, restrict working directory
- [x] **SEC-01b**: Add dangerous command confirmation mechanism to `BashTool`
  - File: `src/main/java/io/agentcore/tools/local/BashTool.java`
  - Tasks: Detect dangerous patterns (rm, sudo, curl|sh, etc.), require confirmation via `ConfirmTool`

### 1.2 Path Traversal Prevention
- [x] **SEC-02a**: Add path boundary validation in `LocalFileOperations.resolve()`
  - File: `src/main/java/io/agentcore/tools/operations/LocalFileOperations.java`
  - Tasks: After `normalize()`, verify `resolved.startsWith(cwd)`, throw `SecurityException` if not
- [x] **SEC-02b**: Add path validation for `WriteTool` and `EditTool`
  - Files: `WriteTool.java`, `EditTool.java`
  - Tasks: Optional: add write-path whitelist via `SandboxQuota.allowWritePaths`
- [x] **SEC-07**: Validate `sessionId` in `JsonlStore`
  - File: `src/main/java/io/agentcore/session/jsonl/JsonlStore.java`
  - Tasks: Validate sessionId matches `[a-zA-Z0-9_-]+`, or verify resolved path stays within directory

### 1.3 SSRF Prevention
- [x] **SEC-04a**: Add URL validation in `HttpTool`
  - File: `src/main/java/io/agentcore/tools/http/HttpTool.java`
  - Tasks: Block private IPs (10.x, 172.16-31.x, 192.168.x, 169.254.x, localhost), restrict to http/https schemes
- [x] **SEC-04b**: Add URL whitelist support
  - Tasks: Allow configuration of permitted domains/patterns

### 1.4 ReDoS Prevention
- [x] **SEC-05**: Add regex safety in `LocalFileOperations.grep()`
  - File: `src/main/java/io/agentcore/tools/operations/LocalFileOperations.java`
  - Tasks: Set regex matching timeout, limit pattern length, catch `PatternSyntaxException` explicitly

### 1.5 SandboxQuota Integration
- [x] **SEC-06a**: Wire `SandboxQuota` into `LocalFileOperations`
  - Tasks: Check `allowWritePaths`, `denyPaths`, `maxFileSize` before file operations
- [x] **SEC-06b**: Wire `SandboxQuota` into `LocalBashOperations`
  - Tasks: Enforce `timeout`, `maxMemory`, `allowedPaths` for process execution

---

## Phase 2: Concurrency & Data Safety Fixes (P1)

> Goal: Eliminate race conditions, data corruption, and thread safety issues.

### 2.1 Agent Core Concurrency
- [x] **CONC-01**: Fix `Agent.run()` race condition
  - File: `src/main/java/io/agentcore/core/Agent.java`
  - Tasks: Use `synchronized` block or `AtomicReference<CompletableFuture>` with CAS for `activeRun`
- [x] **CONC-02**: Fix `AgentState.messages()` thread safety
  - File: `src/main/java/io/agentcore/core/state/AgentState.java`
  - Tasks: Use `CopyOnWriteArrayList` or synchronize access, make `messages` field `volatile`
- [x] **CONC-03**: Add HumanInputGate support to `ToolRunner.executeParallel()`
  - File: `src/main/java/io/agentcore/core/toolrunner/ToolRunner.java`
  - Tasks: Pass `humanInputGate` to `executeParallel`, add `handleHumanInput` logic

### 2.2 File & Session Concurrency
- [x] **CONC-04**: Fix `FileMutationQueue` async lock release
  - File: `src/main/java/io/agentcore/tools/mutation/FileMutationQueue.java`
  - Tasks: Move lock release to `CompletableFuture.whenComplete` callback
- [x] **CONC-05**: Fix `InMemoryStore` concurrent modification
  - File: `src/main/java/io/agentcore/session/memory/InMemoryStore.java`
  - Tasks: Use `Collections.synchronizedList` or `CopyOnWriteArrayList` for entries
- [x] **CONC-06**: Fix `JsonlStore` concurrent file writes
  - File: `src/main/java/io/agentcore/session/jsonl/JsonlStore.java`
  - Tasks: Add per-session `ReentrantLock` or write queue

### 2.3 Other Concurrency
- [x] **CONC-07**: Fix `HumanInputGate.cancelAll()` race condition
  - File: `src/main/java/io/agentcore/core/humaninput/HumanInputGate.java`
  - Tasks: Add `volatile boolean cancelled` flag, check in `requireInput()`
- [x] **CONC-08**: Fix `LocalBashOperations` StringBuilder thread safety
  - File: `src/main/java/io/agentcore/tools/operations/LocalBashOperations.java`
  - Tasks: Use `StringBuffer` or `ByteArrayOutputStream`, or ensure `join()` before read
- [x] **CONC-09**: Fix `AssistantMessage` record mutability
  - File: `src/main/java/io/agentcore/core/messages/AssistantMessage.java`
  - Tasks: Change default to `List.of()`, callers explicitly pass `new ArrayList<>()` if mutation needed

---

## Phase 3: Resource Leak Fixes (P1)

> Goal: Eliminate resource leaks that cause memory/thread/fd exhaustion.

### 3.1 HttpClient Lifecycle
- [x] **LEAK-01a**: Create shared `HttpClient` in `AnthropicProvider`
  - File: `src/main/java/io/agentcore/providers/anthropic/AnthropicProvider.java`
  - Tasks: Initialize in constructor as instance field, reuse across requests
- [x] **LEAK-01b**: Create shared `HttpClient` in `OpenAIProvider`
  - File: `src/main/java/io/agentcore/providers/openai/OpenAIProvider.java`
  - Tasks: Same as above
- [x] **LEAK-01c**: Create shared `HttpClient` in `HttpTool`
  - File: `src/main/java/io/agentcore/tools/http/HttpTool.java`
  - Tasks: Same as above

### 3.2 ObjectMapper Lifecycle
- [x] **LEAK-02**: Create static final `ObjectMapper` singletons
  - Files: `AnthropicProvider.java`, `OpenAIProvider.java`, `HttpTool.java`
  - Tasks: Replace `new ObjectMapper()` calls with `static final` field

### 3.3 Executor Lifecycle
- [x] **LEAK-03**: Fix `ToolRunner.executeSequential` executor leak
  - File: `src/main/java/io/agentcore/core/toolrunner/ToolRunner.java`
  - Tasks: Create executor once before loop, `close()` after loop completes
- [x] **LEAK-04**: Add finally block for `SubmissionPublisher` lifecycle
  - File: `src/main/java/io/agentcore/providers/anthropic/AnthropicProvider.java`
  - Tasks: Move `publisher.close()` to finally block

---

## Phase 4: Functional Bug Fixes (P2)

> Goal: Fix bugs that cause incorrect behavior or data loss.

### 4.1 Session Persistence
- [x] **BUG-01**: Fix `AgentSession.messageToMap` to include message content
  - File: `src/main/java/io/agentcore/session/AgentSession.java`
  - Tasks: Serialize full content list based on message type (UserMessage, AssistantMessage, ToolResultMessage)
- [x] **BUG-07**: Fix `JsonlStore` CompactionEntry serialization
  - File: `src/main/java/io/agentcore/session/jsonl/JsonlStore.java`
  - Tasks: Add `details` and `from_extension` fields to `entryToMap`

### 4.2 Provider Compatibility
- [x] **BUG-02**: Replace hand-written JSON serializer with Jackson
  - File: `src/main/java/io/agentcore/providers/message_converter/DefaultMessageConverter.java`
  - Tasks: Use `ObjectMapper` for JSON serialization, remove `serializeJson` method
- [x] **BUG-03**: Fix `ModelProvider.toolsToProviderFormat` for Anthropic
  - File: `src/main/java/io/agentcore/providers/base/ModelProvider.java`
  - Tasks: Converted to default instance method, added Anthropic-specific format (`input_schema` instead of `parameters`)
- [x] **BUG-04**: Unify OpenAI and Anthropic provider behavior
  - Tasks: Standardized error handling, abort signal checking, context overflow detection (added `isContextOverflow` to AnthropicProvider)

### 4.3 Agent Loop Issues
- [x] **BUG-05**: Fix AgentLoop stream timeout silent exit
  - File: `src/main/java/io/agentcore/core/loop/AgentLoop.java`
  - Tasks: Set `stopReason` to ERROR on timeout, log warning, set retryable flag
- [x] **BUG-06**: Fix ExtensionRunner blocking with .get()
  - File: `src/main/java/io/agentcore/extensions/ExtensionRunner.java`
  - Tasks: Use `thenCompose` chain instead of `.get()`

### 4.4 Other Functional Bugs
- [x] **BUG-08**: Apply HttpTool timeout
  - File: `src/main/java/io/agentcore/tools/http/HttpTool.java`
  - Tasks: Configure `HttpClient` connect timeout and `HttpRequest` request timeout
- [x] **BUG-09**: Fix ReadTool truncation flag
  - File: `src/main/java/io/agentcore/tools/local/ReadTool.java`
  - Tasks: Check both maxBytes and maxLines when setting truncated flag (compare truncated vs original content)
- [x] **BUG-10**: Make `LocalFileOperations.edit` atomic
  - File: `src/main/java/io/agentcore/tools/operations/LocalFileOperations.java`
  - Tasks: Write to temp file, use `Files.move` with `ATOMIC_MOVE`
- [x] **BUG-11**: Specify UTF-8 charset in LocalBashOperations
  - File: `src/main/java/io/agentcore/tools/operations/LocalBashOperations.java`
  - Tasks: Use `new String(buf, 0, n, StandardCharsets.UTF_8)`
- [x] **BUG-12**: Fix LocalFileOperations.find glob-to-regex
  - File: `src/main/java/io/agentcore/tools/operations/LocalFileOperations.java`
  - Tasks: Escape regex special characters before glob replacement

---

## Phase 5: Design Improvements (P3)

> Goal: Improve code quality, type safety, and maintainability.

### 5.1 Type Safety
- [x] **DES-01**: Refactor `AgentLoopConfig` to Builder pattern
  - Tasks: Create `AgentLoopConfig.Builder`, make all fields `final`
- [x] **DES-02**: Add type-safe `CustomMessage` content
  - Tasks: Use `JsonNode` type instead of `Object`, add `contentAsText()` accessor
- [x] **DES-03**: Define typed result records for tool hooks
  - Tasks: Created `BeforeToolCallResult`, `AfterToolCallResult` records

### 5.2 Error Handling
- [-] **DES-04**: Replace exception-based control flow for human input
  - Status: Deferred — requires deep refactor of tool execution interface
- [x] **DES-07**: Fix `StopReason.fromValue()` null return
  - Tasks: Throws `IllegalArgumentException` for unknown values

### 5.3 Other Improvements
- [x] **DES-05**: Use SnakeYAML in ResourceLoader frontmatter parser
  - File: `src/main/java/io/agentcore/resources/ResourceLoader.java`
- [x] **DES-06**: Add CustomMessage support in CompactionStrategies
- [x] **DES-08**: Clarify `Usage.totalTokens()` semantics
  - totalTokens() = input + output; totalTokensWithCache() includes cache breakdowns

---

## Phase 6: Compilation Warning Cleanup (P4)

> Goal: Clean compile with -Xlint:all -Werror

- [x] Add `serialVersionUID` to `RequiresHumanInput`, `MissingCredentialsException`, `UnknownProviderException`
- [x] Fix unchecked cast in `AnthropicProvider` and `OpenAIProvider` Flow.Subscriber
  - Changed `doStream` signature to `Flow.Subscriber<? super StreamEvent>`, eliminated cast
- [x] Fix rawtypes/unchecked warning in `ExtensionRunner`
  - Replaced `Map[]` array with `AtomicReference<Map<String, Object>>`
- [x] Fix serial field warning in `RequiresHumanInput.inputSchema`
  - Marked `inputSchema` as `transient`

---

## Phase 7: Testing & Verification (P4)

> Goal: Ensure all fixes are verified with tests.

- [x] Fix test classpath issue (Jackson ClassNotFoundException in 3 containers)
- [x] Add security tests for path traversal prevention (SEC-02, SEC-06, SEC-07)
- [x] Add security tests for sessionId validation
- [x] Add concurrency tests for InMemoryStore and JsonlStore (CONC-05, CONC-06)
- [x] Add provider compatibility tests — tool format for both OpenAI and Anthropic (BUG-03)
- [x] Add context overflow detection tests (BUG-04)
- [-] Add concurrency tests for Agent.run() race condition (requires integration test harness)
- [-] Add resource leak tests (requires mock HttpClient)
- [-] Achieve >80% code coverage (requires coverage tooling setup)

---

## Progress Summary

| Phase | Total Items | Completed | In Progress | Deferred |
|-------|-------------|-----------|-------------|----------|
| Phase 1: Security (P0) | 10 | 10 | 0 | 0 |
| Phase 2: Concurrency (P1) | 9 | 9 | 0 | 0 |
| Phase 3: Resource Leaks (P1) | 7 | 7 | 0 | 0 |
| Phase 4: Functional Bugs (P2) | 12 | 12 | 0 | 0 |
| Phase 5: Design (P3) | 8 | 7 | 0 | 1 |
| Phase 6: Warnings (P4) | 4 | 4 | 0 | 0 |
| Phase 7: Testing (P4) | 9 | 6 | 0 | 3 |
| **Total** | **59** | **55** | **0** | **4** |

### Build Results
- **Compilation**: 0 warnings (was 9 with -Xlint:all)
- **Tests**: 74/74 passed (was 52/52, +22 new tests)
- **Containers**: 13/13 successful (was 10/10, +3 new test classes)

---

## Change Log

| Date | Phase | Item | Action |
|------|-------|------|--------|
| 2026-06-15 | - | - | Initial audit completed, report created |
| 2026-06-15 | Phase 1 | SEC-01~SEC-07 | All security fixes completed |
| 2026-06-15 | Phase 2 | CONC-01~09 | All concurrency fixes completed |
| 2026-06-15 | Phase 3 | LEAK-01~04 | All resource leak fixes completed |
| 2026-06-15 | Phase 4 | BUG-01~12 | All functional bug fixes completed |
| 2026-06-15 | Phase 7 | Classpath | Fixed test classpath issue, all 52 tests passing |
| 2026-06-16 | Phase 5 | DES-01~08 | 7/8 design improvements completed (DES-04 deferred) |
| 2026-06-16 | Phase 6 | All | All compilation warnings eliminated (0 warnings) |
| 2026-06-16 | Phase 7 | Tests | Added security, concurrency, and provider tests (74/74 pass) |
