/* ───────────────────────────────────────────────
   Streaming engine — shared types
   设计文档参考: journal-chat-theme.md §8
   ─────────────────────────────────────────────── */

/** Tool-call lifecycle states (matches ToolCallBlock). */
export type ToolStatus = 'preparing' | 'calling' | 'executing' | 'complete' | 'error';

/** Generic per-item run state (sub-tools, file ops, blocks). */
export type RunStatus = 'pending' | 'running' | 'complete' | 'error';

/* ─── Block specs: the static "script" description of one streaming card.
   Each maps 1:1 onto an existing streaming component's props. ─── */

export interface ThinkingSpec {
  kind: 'thinking';
  content: string;
}

export interface ToolSpec {
  kind: 'tool';
  toolName: string;
  icon: string;
  params?: { key: string; value: string }[];
  result?: string;
  resultCount?: string;
  /** When true, the tool ends in the error state with this message. */
  errorMessage?: string;
}

export interface SkillSpec {
  kind: 'skill';
  skillName: string;
  description: string;
  subTools: { name: string }[];
}

export interface CodeSpec {
  kind: 'code';
  language: string;
  code: string;
}

export interface SearchResultItem {
  path: string;
  filename: string;
  preview: string;
  highlights?: string[];
}
export interface SearchSpec {
  kind: 'search';
  title: string;
  count: string;
  results: SearchResultItem[];
}

export interface DiffLine {
  type: 'add' | 'del' | 'neutral';
  oldNum?: string;
  newNum?: string;
  content: string;
}
export interface DiffSpec {
  kind: 'diff';
  filename: string;
  stats: { add: number; del: number };
  lines: DiffLine[];
}

export interface FileOp {
  type: 'create' | 'read' | 'update' | 'delete';
  path: string;
  meta?: string;
}
export interface FileSpec {
  kind: 'file';
  operations: FileOp[];
}

export type BlockSpec =
  | ThinkingSpec
  | ToolSpec
  | SkillSpec
  | CodeSpec
  | SearchSpec
  | DiffSpec
  | FileSpec;

/** A full assistant turn: a sequence of streaming blocks then a text answer. */
export interface Script {
  steps: BlockSpec[];
  answer: string;
}

/* ─── Live runtime state the orchestrator derives from a BlockSpec ─── */
export interface LiveBlock {
  id: string;
  spec: BlockSpec;
  status: RunStatus;
  /** thinking content / code: characters revealed so far. */
  typed: string;
  /** tool lifecycle state. */
  toolStatus: ToolStatus;
  /** skill progress 0–100. */
  progress: number;
  /** skill sub-tool states, aligned with SkillSpec.subTools. */
  subStatus: RunStatus[];
  /** file-op states, aligned with FileSpec.operations. */
  fileStatus: RunStatus[];
}

export interface AgentTurn {
  phase: 'idle' | 'typing' | 'streaming' | 'done';
  blocks: LiveBlock[];
  answer: string;
  answerDone: boolean;
}

/* ─── StreamSource abstraction ───
   The UI consumes an async stream of patches. Today a MockStreamSource
   replays a Script on timers; later an SseStreamSource can map real
   agent-core-java server events onto the same AgentTurn shape — the hook
   and components stay unchanged. ─── */
export interface StreamSource {
  /** Run the source, invoking onTurn on every state change. Resolves when done. */
  run(onTurn: (turn: AgentTurn) => void, signal: { cancelled: boolean }): Promise<void>;
}
