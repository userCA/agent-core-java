import React, { useEffect, useState } from 'react';
import CodeBlock from './CodeBlock';
import SearchResultBlock from './SearchResultBlock';
import GitDiffBlock from './GitDiffBlock';
import FileOperationBlock from './FileOperationBlock';
import type { LiveBlock, RunStatus } from '../../lib/stream/types';

/* ───────────────────────────────────────────────
   ReasoningThread — 折叠推理时间线
   把一连串过程步骤(思考/工具/技能/代码/搜索/对比/文件)收进
   一条带虚线轨道的可折叠时间线，而不是平铺成一堆卡片。
   完成后整体折叠为「推理完成 · N 步」。
   ─────────────────────────────────────────────── */

const Chevron: React.FC<{ open: boolean }> = ({ open }) => (
  <svg className={`t-chev ${open ? 'open' : ''}`} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="9 18 15 12 9 6" />
  </svg>
);

const Check: React.FC<{ size?: number }> = ({ size = 11 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="20 6 9 17 4 12" />
  </svg>
);

const SubStatusIcon: React.FC<{ status: RunStatus; size?: number }> = ({ status, size = 16 }) => {
  if (status === 'complete') {
    return (
      <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="#6b8f5e" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
        <polyline points="20 6 9 17 4 12" />
      </svg>
    );
  }
  if (status === 'error') {
    return (
      <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="#c65b4a" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
        <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
      </svg>
    );
  }
  if (status === 'running') {
    return (
      <span className="t-spin" style={{ width: size, height: size, display: 'block' }} />
    );
  }
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="#9e8f80" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="6" />
    </svg>
  );
};

const fileRun = (s: RunStatus): 'pending' | 'success' | 'error' =>
  s === 'complete' ? 'success' : s === 'error' ? 'error' : 'pending';

function tagOf(kind: string): 'think' | 'tool' | 'skill' {
  if (kind === 'thinking') return 'think';
  if (kind === 'skill' || kind === 'file') return 'skill';
  return 'tool';
}

function labelOf(b: LiveBlock): string {
  const s = b.spec;
  switch (s.kind) {
    case 'thinking': return '思考';
    case 'tool': return `工具 · ${s.toolName}`;
    case 'skill': return `技能 · ${s.skillName}`;
    case 'code': return '代码';
    case 'search': return '搜索';
    case 'diff': return '对比';
    case 'file': return '文件';
  }
}

function subOf(b: LiveBlock): string {
  const s = b.spec;
  const running = b.status === 'running';
  switch (s.kind) {
    case 'thinking': return running ? '整理思路…' : '已理清思路';
    case 'tool': return b.status === 'complete' ? (s.resultCount ?? '已完成') : b.status === 'error' ? '失败' : '执行中…';
    case 'skill': {
      const n = s.subTools.length;
      const done = b.subStatus.filter((x) => x === 'complete').length;
      return b.status === 'complete' ? '已完成' : `第 ${Math.min(done + 1, n)} / ${n} 步`;
    }
    case 'code': return s.language;
    case 'search': return s.count;
    case 'diff': return s.filename.split('/').pop() ?? s.filename;
    case 'file': return `${s.operations.length} 个操作`;
  }
}

const StepBody: React.FC<{ b: LiveBlock }> = ({ b }) => {
  const s = b.spec;
  const running = b.status === 'running';
  switch (s.kind) {
    case 'thinking':
      return (
        <div className="t-body-inner">
          {b.typed}
          {running && <span className="inline-block w-0.5 h-4 bg-accent-pink ml-0.5 align-middle animate-blink" />}
        </div>
      );
    case 'tool':
      return (
        <div className="t-body-inner">
          {s.params && s.params.length > 0 && (
            <div className="space-y-1">
              {s.params.map((p, i) => (
                <div key={i} className="flex gap-2 text-[12px]">
                  <span className="font-bold text-ink min-w-[52px] flex-shrink-0">{p.key}</span>
                  <span className="text-ink-muted break-all font-sans">{p.value}</span>
                </div>
              ))}
            </div>
          )}
          {b.status === 'complete' && s.result && <div className="t-result font-sans">{s.result}</div>}
          {b.status === 'error' && s.errorMessage && <div className="t-result" style={{ color: '#e89080' }}>{s.errorMessage}</div>}
        </div>
      );
    case 'skill':
      return (
        <div className="t-body-inner">
          <div className="hand-progress" style={{ height: 10 }}>
            <div className="hand-progress-fill" style={{ width: `${b.progress}%`, background: b.status === 'complete' ? '#a8c4a0' : undefined }} />
          </div>
          <div className="mt-2 space-y-1">
            {s.subTools.map((sub, i) => (
              <div key={i} className="flex items-center gap-2 text-[12px]">
                <SubStatusIcon status={b.subStatus[i]} size={16} />
                <span className={b.subStatus[i] === 'complete' ? 'text-ink-muted line-through' : 'text-ink'}>{sub.name}</span>
              </div>
            ))}
          </div>
        </div>
      );
    case 'code':
      return <CodeBlock language={s.language} code={b.status === 'complete' ? s.code : b.typed} isStreaming={running} bare />;
    case 'search':
      return <SearchResultBlock title={s.title} count={s.count} results={s.results} bare />;
    case 'diff':
      return <GitDiffBlock filename={s.filename} stats={s.stats} lines={s.lines} bare />;
    case 'file':
      return <FileOperationBlock operations={s.operations.map((o, i) => ({ ...o, status: fileRun(b.fileStatus[i]) }))} bare />;
  }
};

interface ReasoningThreadProps {
  blocks: LiveBlock[];
  /** turn still in flight (controls spinner vs check). */
  active: boolean;
}

const ReasoningThread: React.FC<ReasoningThreadProps> = ({ blocks, active }) => {
  const allDone = blocks.length > 0 && blocks.every((b) => b.status === 'complete' || b.status === 'error');
  const [collapsed, setCollapsed] = useState(allDone);
  const [openMap, setOpenMap] = useState<Record<string, boolean>>({});
  const wasDone = React.useRef(allDone);

  // auto-collapse the whole thread once when it finishes
  useEffect(() => {
    if (allDone && !wasDone.current) setCollapsed(true);
    wasDone.current = allDone;
  }, [allDone]);

  const n = blocks.length;
  const runningIdx = blocks.findIndex((b) => b.status === 'running');
  const doneCount = blocks.filter((b) => b.status === 'complete' || b.status === 'error').length;
  const headLabel = allDone ? `推理完成 · ${n} 步` : `推理中 · 第 ${Math.min((runningIdx < 0 ? doneCount : runningIdx) + 1, n)} / ${n} 步`;
  const spinning = active && !allDone;

  return (
    <div className={`trace animate-slide-down ${collapsed ? 'collapsed' : ''}`}>
      <button type="button" className="trace-head" aria-expanded={!collapsed} onClick={() => setCollapsed((c) => !c)}>
        <span className="thead-ic">
          {spinning ? <span className="t-spin" style={{ width: 14, height: 14, display: 'block' }} /> : <Check size={13} />}
        </span>
        <span className="thead-label font-display">{headLabel}</span>
        <Chevron open={!collapsed} />
      </button>

      <div className="trace-rail">
        {blocks.map((b, i) => {
          const open = openMap[b.id] ?? b.status === 'running';
          const tag = tagOf(b.spec.kind);
          const nodeCls =
            b.status === 'complete' ? 'done' : b.status === 'error' ? 'error' : b.status === 'running' ? 'running' : tag;
          return (
            <div key={b.id} className={`t-step ${i === n - 1 ? 'is-last' : ''}`}>
              <span className={`t-node ${nodeCls}`}>
                {b.status === 'running' ? (
                  <span className="t-spin" style={{ width: 10, height: 10, display: 'block' }} />
                ) : b.status === 'complete' ? (
                  <Check size={10} />
                ) : b.status === 'error' ? (
                  <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" /></svg>
                ) : (
                  <span className="w-1.5 h-1.5 rounded-full bg-ink-muted" />
                )}
              </span>

              <button type="button" className="t-head" onClick={() => setOpenMap((m) => ({ ...m, [b.id]: !open }))}>
                <span className="t-kind font-display">{labelOf(b)}</span>
                <span className="t-sub font-comic">{subOf(b)}</span>
                <Chevron open={open} />
              </button>

              {open && <StepBody b={b} />}
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default ReasoningThread;
