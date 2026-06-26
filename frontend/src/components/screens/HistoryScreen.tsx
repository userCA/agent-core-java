import React from 'react';
import type { HistoryEntry } from '../../data/appData';

const HistoryCard: React.FC<{ entry: HistoryEntry; onClick: () => void }> = ({ entry, onClick }) => (
  <button onClick={onClick} className="journal-card w-full text-left bg-cream-card border-2 border-ink rounded-card p-4 shadow-card cursor-pointer">
    <div className="flex items-start justify-between mb-2">
      <span className="text-[12px] font-bold text-ink-muted uppercase tracking-wide font-display">{entry.date}</span>
      <span className="text-[11px] text-ink-muted font-comic">{entry.time}</span>
    </div>
    <h3 className="text-[15px] font-bold text-ink mb-1 font-display">{entry.title}</h3>
    <p className="text-xs text-ink-muted leading-relaxed line-clamp-2 font-comic">{entry.desc}</p>
    <div className="flex items-center gap-2 mt-3">
      <span className="px-2 py-0.5 bg-cream-warm rounded-pill text-[10px] text-ink-muted border border-ink font-comic">{entry.messages}</span>
      <span className="px-2 py-0.5 bg-cream-warm rounded-pill text-[10px] text-ink-muted border border-ink font-comic">{entry.tools}</span>
    </div>
  </button>
);

interface HistoryScreenProps {
  entries: HistoryEntry[];
  onSelect: () => void;
  onNewChat: () => void;
}

const HistoryScreen: React.FC<HistoryScreenProps> = ({ entries, onSelect, onNewChat }) => {
  return (
    <>
      <header className="flex items-center justify-between px-4 pt-12 pb-3 bg-cream-bg/95 backdrop-blur-sm z-20 border-b-2 border-border-passive flex-shrink-0">
        <button onClick={onSelect} aria-label="返回聊天" className="w-11 h-11 flex items-center justify-center rounded-button hover:bg-ink/5 transition-colors cursor-pointer">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <line x1="19" y1="12" x2="5" y2="12" /><polyline points="12 19 5 12 12 5" />
          </svg>
        </button>
        <h1 className="text-[16px] font-bold text-ink tracking-tight font-display">日记列表</h1>
        <button onClick={onNewChat} aria-label="新建对话" className="w-11 h-11 flex items-center justify-center rounded-button hover:bg-ink/5 transition-colors cursor-pointer">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
          </svg>
        </button>
      </header>

      <div className="flex-1 overflow-y-auto custom-scroll px-4 py-4 space-y-3">
        <div className="relative mb-4">
          <svg className="w-4 h-4 text-ink-muted absolute left-3 top-1/2 -translate-y-1/2" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <label htmlFor="history-search" className="sr-only">搜索日记</label>
          <input id="history-search" type="text" placeholder="搜索日记..." className="w-full pl-10 pr-4 py-2.5 bg-cream-card border-2 border-ink rounded-button text-sm text-ink placeholder:text-ink-faint input-focus font-comic" />
        </div>

        <div className="flex items-center gap-2 py-1">
          <span className="text-[13px] font-bold text-ink-muted uppercase tracking-wider font-display">2026年6月</span>
          <div className="h-px flex-1 dashed-divider" />
        </div>
        {entries.slice(0, 3).map((entry, i) => <HistoryCard key={i} entry={entry} onClick={onSelect} />)}

        <div className="flex items-center gap-2 py-1 mt-2">
          <span className="text-[13px] font-bold text-ink-muted uppercase tracking-wider font-display">2026年5月</span>
          <div className="h-px flex-1 dashed-divider" />
        </div>
        {entries.slice(3).map((entry, i) => <HistoryCard key={i} entry={entry} onClick={onSelect} />)}
      </div>
    </>
  );
};

export default HistoryScreen;
