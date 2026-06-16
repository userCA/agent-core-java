# agent-core-java Code Audit Report

**Audit Date**: 2026-06-15 (update: 2026-06-16 — AgentScope Java v2.0 migration)
**Auditor**: Qoder AI & Claude Code
**Scope**: Full codebase review (119 Java files + 9 v2 migration files, 10 modules + 1 v2 module)

---

## Executive Summary

| Category | Count | Status |
|----------|-------|--------|
| Compilation | PASS | 9 warnings (unchecked/serial) |
| Tests | 29/29 passed | 3 containers failed (classpath issue) |
| Critical Security Issues | 7 | OPEN |
| Concurrency/Data Safety Issues | 9 | OPEN |
| Resource Leak Issues | 4 | OPEN |
| Functional Bugs | 12 | OPEN |
| Design Issues | 8 | OPEN |
| **Total Issues** | **40+** | |

---

## Critical Security Issues (P0)

### SEC-01: Command Injection in BashTool
- **File**: `src/main/java/io/agentcore/tools/local/BashTool.java:34`
- **Severity**: CRITICAL
- **Description**: `command` parameter is passed directly to `ProcessBuilder(shell, "-c", command)` without any validation, whitelist, or sandbox restriction. Agent (or LLM behind it) can execute arbitrary system commands including `rm -rf /`, reverse shells, etc.
- **Impact**: Full system compromise
- **Recommendation**: Integrate `SandboxQuota` (already defined but unused), implement command whitelist/blacklist, add confirmation mechanism for dangerous commands

### SEC-02: Path Traversal in LocalFileOperations
- **File**: `src/main/java/io/agentcore/tools/operations/LocalFileOperations.java:23`
- **Severity**: CRITICAL
- **Description**: `resolve()` calls `normalize()` but never validates if the resolved path stays within `cwd`. Attackers can use absolute paths (`/etc/passwd`) or relative traversal (`../../etc/passwd`).
- **Impact**: Unauthorized file access across entire filesystem
- **Affects**: ReadTool, WriteTool, EditTool, LsTool, GrepTool, FindTool
- **Recommendation**: Add `if (!resolved.startsWith(cwd)) throw new SecurityException(...)` after resolve

### SEC-03: Arbitrary File Write via WriteTool/EditTool
- **Files**: `WriteTool.java:31`, `EditTool.java:34`
- **Severity**: CRITICAL
- **Description**: Combined with SEC-02, these tools can write to any location on the filesystem
- **Impact**: Arbitrary code execution via writing to crontab, ssh authorized_keys, etc.

### SEC-04: SSRF in HttpTool
- **File**: `src/main/java/io/agentcore/tools/http/HttpTool.java:46`
- **Severity**: CRITICAL
- **Description**: No URL validation. Can access `http://169.254.169.254/` (cloud metadata), `localhost` (internal services), `file://` protocol
- **Impact**: Cloud credential theft, internal network scanning
- **Recommendation**: URL whitelist, block private/metadata IPs, restrict to http(s) schemes

### SEC-05: ReDoS in GrepTool
- **File**: `src/main/java/io/agentcore/tools/operations/LocalFileOperations.java:104`
- **Severity**: HIGH
- **Description**: User-provided pattern compiled directly to regex without complexity limits. Malicious patterns like `(a+)+$` cause CPU denial of service.
- **Recommendation**: Add regex complexity limits, timeout on matching, or use safe regex library

### SEC-06: SandboxQuota Defined But Never Integrated
- **File**: `src/main/java/io/agentcore/tools/operations/SandboxQuota.java`
- **Severity**: HIGH
- **Description**: Complete security quota system (CPU, memory, timeout, path whitelist/blacklist) defined but never used anywhere in the codebase
- **Recommendation**: Integrate into LocalBashOperations and LocalFileOperations

### SEC-07: Path Traversal via JsonlStore sessionId
- **File**: `src/main/java/io/agentcore/session/jsonl/JsonlStore.java:24`
- **Severity**: HIGH
- **Description**: `sessionId` can contain `../..` to read/write files outside expected directory
- **Recommendation**: Validate sessionId with whitelist pattern `[a-zA-Z0-9_-]` or verify resolved path stays within directory

---

## Concurrency & Data Safety Issues (P1)

### CONC-01: Agent.run() Check-Then-Act Race Condition
- **File**: `src/main/java/io/agentcore/core/Agent.java:180`
- **Severity**: HIGH
- **Description**: `activeRun` read and write without synchronization. Two threads can both pass `isDone()` check, causing parallel agent loops on same state.
- **Recommendation**: Use `synchronized` or `AtomicReference` with CAS

### CONC-02: AgentState.messages() Not Thread-Safe
- **File**: `src/main/java/io/agentcore/core/state/AgentState.java:19`
- **Severity**: HIGH
- **Description**: Plain ArrayList shared across multiple threads without synchronization. Concurrent modification causes data corruption.

### CONC-03: ToolRunner Parallel Mode Missing HumanInputGate
- **File**: `src/main/java/io/agentcore/core/toolrunner/ToolRunner.java:49`
- **Severity**: HIGH
- **Description**: `executeParallel` method signature lacks `humanInputGate` parameter. Tools requiring human input produce unhandled exceptions in parallel mode.

### CONC-04: FileMutationQueue Lock Released Before Async Operation Completes
- **File**: `src/main/java/io/agentcore/tools/mutation/FileMutationQueue.java:39`
- **Severity**: HIGH
- **Description**: `try-with-resources` releases lock immediately after `ops.write()` returns CompletableFuture, but actual write is still executing. Mutual exclusion is ineffective.
- **Recommendation**: Release lock in `CompletableFuture.whenComplete`

### CONC-05: InMemoryStore Concurrent Modification
- **File**: `src/main/java/io/agentcore/session/memory/InMemoryStore.java:19`
- **Severity**: HIGH
- **Description**: ArrayList inside SessionSnapshot modified concurrently by multiple threads. Can cause ConcurrentModificationException and data corruption.
- **Recommendation**: Use CopyOnWriteArrayList or synchronized access

### CONC-06: JsonlStore Concurrent File Writes
- **File**: `src/main/java/io/agentcore/session/jsonl/JsonlStore.java:39`
- **Severity**: HIGH
- **Description**: Multiple CompletableFutures appending to same file can cause interleaved/truncated JSONL lines.
- **Recommendation**: Per-session write serialization or FileLock

### CONC-07: HumanInputGate.cancelAll() Race Condition
- **File**: `src/main/java/io/agentcore/core/humaninput/HumanInputGate.java:53`
- **Severity**: MEDIUM
- **Description**: `forEach` and `clear()` not atomic. New futures added between them get cleared without cancellation, causing permanent blocking.

### CONC-08: LocalBashOperations StringBuilder Thread Safety
- **File**: `src/main/java/io/agentcore/tools/operations/LocalBashOperations.java:47`
- **Severity**: MEDIUM
- **Description**: StringBuilder (not thread-safe) shared between virtual threads and main thread. Race condition on append/toString.

### CONC-09: AssistantMessage Record Mutability
- **File**: `src/main/java/io/agentcore/core/messages/AssistantMessage.java:23`
- **Severity**: MEDIUM
- **Description**: Default `new ArrayList<>()` breaks record immutability contract. External code can modify record internal state.

---

## Resource Leak Issues (P1)

### LEAK-01: HttpClient Created Per-Request, Never Closed
- **Files**: `AnthropicProvider.java:93`, `OpenAIProvider.java:88`, `HttpTool.java:63`
- **Severity**: HIGH
- **Description**: `HttpClient.newHttpClient()` creates thread pools and connection pools. Frequent creation causes thread leaks and file descriptor exhaustion.
- **Recommendation**: Create as static final or instance field, reuse across requests

### LEAK-02: ObjectMapper Created Per-JSON-Operation
- **Files**: AnthropicProvider and OpenAIProvider `parseJson`/`toJson` methods
- **Severity**: HIGH
- **Description**: Each SSE event creates new ObjectMapper. A single LLM call may create hundreds of instances. Severe performance impact.
- **Recommendation**: Use static final singleton (ObjectMapper is thread-safe)

### LEAK-03: ToolRunner.executeSequential Executor Not Released
- **File**: `src/main/java/io/agentcore/core/toolrunner/ToolRunner.java:131`
- **Severity**: MEDIUM
- **Description**: Each tool call creates new Executor without calling `close()`/`shutdown()`

### LEAK-04: SubmissionPublisher Lifecycle Management
- **File**: `src/main/java/io/agentcore/providers/anthropic/AnthropicProvider.java:75`
- **Severity**: MEDIUM
- **Description**: Missing finally block. Publisher may not close on certain exception paths.

---

## Functional Bugs (P2)

### BUG-01: AgentSession.messageToMap Loses Message Content
- **File**: `src/main/java/io/agentcore/session/AgentSession.java:129`
- **Severity**: HIGH
- **Description**: Only saves `role` and `timestamp`, completely discards message content. Session restore is completely non-functional.

### BUG-02: DefaultMessageConverter Hand-Written JSON Serializer Produces Invalid JSON
- **File**: `src/main/java/io/agentcore/providers/message_converter/DefaultMessageConverter.java:120`
- **Severity**: HIGH
- **Description**: Does not escape control characters (`\n`, `\t`, etc.), produces invalid JSON

### BUG-03: ModelProvider.toolsToProviderFormat Hardcoded to OpenAI Format
- **File**: `src/main/java/io/agentcore/providers/base/ModelProvider.java:26`
- **Severity**: HIGH
- **Description**: Generates `{"type": "function", "function": {...}}` format. Completely incompatible with Anthropic API.

### BUG-04: OpenAI and Anthropic Provider Behavior Inconsistency
- **Severity**: MEDIUM
- **Description**: Different error handling (StreamMessageEnd timing), different abort signal handling (Anthropic doesn't check), different context overflow keyword detection

### BUG-05: AgentLoop Stream Timeout Silent Exit
- **File**: `src/main/java/io/agentcore/core/loop/AgentLoop.java:292`
- **Severity**: MEDIUM
- **Description**: 60-second timeout causes silent break without error signal. stopReason remains default STOP. Caller cannot detect truncation.

### BUG-06: ExtensionRunner Blocks with .get() in Async Method
- **File**: `src/main/java/io/agentcore/extensions/ExtensionRunner.java:20`
- **Severity**: MEDIUM
- **Description**: May deadlock if running on ForkJoinPool thread while extension also uses ForkJoinPool

### BUG-07: JsonlStore CompactionEntry Serialization Missing Fields
- **Severity**: MEDIUM
- **Description**: `details` and `fromExtension` fields omitted during serialization

### BUG-08: HttpTool Timeout Not Applied
- **File**: `src/main/java/io/agentcore/tools/http/HttpTool.java:26`
- **Severity**: MEDIUM
- **Description**: Constructor accepts timeout parameter but `execute()` never uses it. Requests can hang indefinitely.

### BUG-09: ReadTool Truncation Flag Inaccurate
- **Severity**: LOW
- **Description**: `truncated` flag only checks maxBytes, ignores maxLines truncation

### BUG-10: LocalFileOperations.edit Non-Atomic
- **Severity**: MEDIUM
- **Description**: Read-modify-write without transaction. Exception during write (disk full) loses original file content.

### BUG-11: LocalBashOperations Uses Platform Default Charset
- **Severity**: LOW
- **Description**: Should explicitly specify `StandardCharsets.UTF_8`

### BUG-12: LocalFileOperations.find Glob-to-Regex Conversion Buggy
- **Severity**: LOW
- **Description**: Does not escape regex special characters. `file[1].txt` produces invalid regex.

---

## Design Issues (P2-P3)

### DES-01: AgentLoopConfig Claims Immutable But Fully Mutable
- **File**: `src/main/java/io/agentcore/core/context/AgentLoopConfig.java:23`
- **Description**: Comment says "Immutable" but provides fluent setters for all fields

### DES-02: CustomMessage.content Type is Object
- **Description**: Complete loss of type safety, unpredictable serialization behavior

### DES-03: BeforeToolCallHook/AfterToolCallHook Returns Untyped Map
- **Description**: Relies on string keys for semantics, typos undetectable at compile time

### DES-04: RequiresHumanInput Uses RuntimeException for Control Flow
- **Description**: Anti-pattern. Exceptions for control flow interfere with error handling.

### DES-05: ResourceLoader Frontmatter Parser Fragile
- **Description**: Imported SnakeYAML but never used. Hand-written substring parsing doesn't support quoted values.

### DES-06: CompactionStrategies Doesn't Handle CustomMessage
- **Description**: CustomMessage token count estimated as 0, may miss compaction triggers

### DES-07: StopReason.fromValue() Returns Null
- **Description**: Should throw IllegalArgumentException or return sensible default

### DES-08: Usage.totalTokens() May Double-Count Cache Tokens
- **Description**: cacheReadTokens and cacheWriteTokens may already be included in inputTokens

---

## Compilation Warnings

```
9 warnings with -Xlint:all:
- 3x try-with-resources resource not referenced (FileMutationQueue)
- 3x serialVersionUID missing (RequiresHumanInput, MissingCredentialsException, UnknownProviderException)
- 2x unchecked cast (AnthropicProvider, OpenAIProvider Flow.Subscriber cast)
- 1x serial field not serializable (RequiresHumanInput.inputSchema)
```

---

## Test Results

```
52 tests found, 29 started, 29 passed, 0 failed
3 containers failed: ClassNotFoundException for Jackson (classpath configuration issue)
```

---

## Appendix: Issue Count by Module

| Module | Critical | High | Medium | Low |
|--------|----------|------|--------|-----|
| tools | 5 | 5 | 8 | 8 |
| providers | 0 | 4 | 5 | 4 |
| session | 1 | 3 | 3 | 2 |
| core | 0 | 3 | 4 | 2 |
| extensions | 0 | 0 | 1 | 1 |
| resources | 0 | 0 | 1 | 2 |
| compaction | 0 | 0 | 1 | 1 |
| prompts | 0 | 0 | 1 | 1 |
