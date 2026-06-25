import React from 'react';

interface ToolCallBlockProps {
  toolName?: string;
  icon?: string;
  status?: 'preparing' | 'calling' | 'executing' | 'complete' | 'error';
  params?: { key: string; value: string }[];
  result?: string;
  errorMessage?: string;
  resultCount?: string;
}

const statusConfig = {
  preparing: {
    iconBg: '#c8b8d8',
    labelBg: '#f5ecd0',
    labelText: '准备中...',
    borderColor: '#3d2b1f',
  },
  calling: {
    iconBg: '#a8c8e8',
    labelBg: '#a8c8e8',
    labelText: '调用中...',
    borderColor: '#3d2b1f',
  },
  executing: {
    iconBg: '#a8c8e8',
    labelBg: '#a8c8e8',
    labelText: '执行中...',
    borderColor: '#3d2b1f',
  },
  complete: {
    iconBg: '#a8c4a0',
    labelBg: '#a8c4a0',
    labelText: '已完成',
    borderColor: '#3d2b1f',
  },
  error: {
    iconBg: '#e89080',
    labelBg: '#e89080',
    labelText: '失败',
    borderColor: '#e89080',
  },
};

const ToolCallBlock: React.FC<ToolCallBlockProps> = ({
  toolName = 'Code Search',
  icon = 'search',
  status = 'executing',
  params = [
    { key: 'query', value: 'auth module refactor' },
    { key: 'language', value: 'java' },
  ],
  result,
  errorMessage,
  resultCount = '2 results',
}) => {
  const config = statusConfig[status];
  const isActive = status === 'calling' || status === 'executing';

  const getIconSvg = () => {
    switch (icon) {
      case 'search':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>;
      case 'git-compare':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><circle cx="18" cy="18" r="3"/><circle cx="6" cy="6" r="3"/><path d="M13 6h3a2 2 0 0 1 2 2v7"/><path d="M11 18H8a2 2 0 0 1-2-2V9"/></svg>;
      case 'play':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polygon points="5 3 19 12 5 21 5 3"/></svg>;
      case 'wand-2':
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="m21.64 3.64-1.28-1.28a1.21 1.21 0 0 0-1.72 0L2.36 18.64a1.21 1.21 0 0 0 0 1.72l1.28 1.28a1.2 1.2 0 0 0 1.72 0L21.64 5.36a1.2 1.2 0 0 0 0-1.72"/><path d="m14 7 3 3"/></svg>;
      default:
        return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"/></svg>;
    }
  };

  return (
    <div
      className="streaming-card animate-slide-down"
      style={{ borderColor: config.borderColor }}
    >
      {/* Header */}
      <div className="flex items-center gap-2">
        <div
          className="w-6 h-6 rounded-md flex items-center justify-center flex-shrink-0 relative"
          style={{ background: config.iconBg, border: '1.5px solid #3d2b1f' }}
        >
          {getIconSvg()}
          {isActive && (
            <div
              className="absolute inset-[-3px] rounded-md border-[1.5px] border-accent-sky animate-pulse-ring"
              style={{ borderRadius: '5px' }}
            />
          )}
        </div>
        <span className="text-[13px] font-bold text-ink font-display">{toolName}</span>
        <span
          className="ml-auto text-[10px] font-comic px-2 py-0.5 rounded-pill border-[1.5px] border-ink"
          style={{ background: config.labelBg }}
        >
          {resultCount}
        </span>
      </div>

      {/* Divider */}
      <div className="my-2.5 border-t-[1.5px] border-dashed border-ink-faint" />

      {/* Params */}
      {(status === 'calling' || status === 'executing') && params.length > 0 && (
        <div className="mb-2">
          {params.map((p, i) => (
            <div key={i} className="flex gap-2 py-1 text-[12px] border-b border-dashed border-ink-ghost last:border-0">
              <span className="font-comic font-bold text-ink min-w-[60px] flex-shrink-0">{p.key}</span>
              <span className="font-sans text-ink-muted break-all">{p.value}</span>
            </div>
          ))}
        </div>
      )}

      {/* Status label */}
      <div className="flex items-center gap-2">
        <div
          className="w-4 h-4 rounded-full flex items-center justify-center"
          style={{
            background: status === 'complete' ? '#a8c4a0' : status === 'error' ? '#e89080' : 'transparent',
            border: '1.5px solid #3d2b1f',
          }}
        >
          {status === 'complete' && (
            <svg width="8" height="8" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="20 6 9 17 4 12"/>
            </svg>
          )}
          {status === 'error' && (
            <svg width="8" height="8" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          )}
          {(status === 'preparing' || status === 'calling' || status === 'executing') && (
            <div className="w-1.5 h-1.5 rounded-full bg-accent-sky" />
          )}
        </div>
        <span className="text-[11px] font-comic text-ink-muted">{config.labelText}</span>
      </div>

      {/* Result preview */}
      {status === 'complete' && result && (
        <div className="mt-2 bg-cream-surface border-[1.5px] border-ink rounded-[10px] p-3 text-[12px] text-ink font-sans leading-relaxed">
          {result}
        </div>
      )}

      {/* Error message */}
      {status === 'error' && errorMessage && (
        <div className="mt-2 text-[12px] text-accent-coral font-sans">
          {errorMessage}
        </div>
      )}
    </div>
  );
};

export default ToolCallBlock;
