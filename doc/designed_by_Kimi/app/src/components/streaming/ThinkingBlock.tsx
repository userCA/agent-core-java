import React, { useState } from 'react';

interface ThinkingBlockProps {
  content?: string;
  isComplete?: boolean;
  isStreaming?: boolean;
}

const ThinkingBlock: React.FC<ThinkingBlockProps> = ({
  content = "The user wants to refactor the auth module. Let me first understand the current codebase structure, then identify the key components that need refactoring...",
  isComplete = false,
  isStreaming = true,
}) => {
  const [expanded, setExpanded] = useState(true);

  return (
    <div className="streaming-card animate-slide-down">
      <div
        className="flex items-center gap-2 cursor-pointer"
        onClick={() => setExpanded(!expanded)}
      >
        <div
          className="w-6 h-6 rounded-full flex items-center justify-center flex-shrink-0"
          style={{
            background: isComplete ? '#a8c4a0' : '#f9d5c0',
            border: '1.5px solid #3d2b1f',
          }}
        >
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="m12 3-1.912 5.813a2 2 0 0 1-1.275 1.275L3 12l5.813 1.912a2 2 0 0 1 1.275 1.275L12 21l1.912-5.813a2 2 0 0 1 1.275-1.275L21 12l-5.813-1.912a2 2 0 0 1-1.275-1.275L12 3Z"/>
          </svg>
        </div>
        <span className="text-[13px] font-bold text-ink font-display">思考中</span>
        {isComplete && (
          <svg className="ml-1" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#a8c4a0" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="20 6 9 17 4 12"/>
          </svg>
        )}
        <span className="text-[11px] text-ink-muted font-comic ml-auto">
          {isStreaming && !isComplete ? (
            <span className="flex items-center gap-1">
              思考中
              <span className="w-1 h-1 rounded-full bg-accent-pink animate-pulse" />
            </span>
          ) : isComplete ? (
            '已完成'
          ) : (
            '点击展开'
          )}
        </span>
        <svg
          width="12"
          height="12"
          viewBox="0 0 24 24"
          fill="none"
          stroke="#7a6b5f"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="transition-transform duration-200"
          style={{ transform: expanded ? 'rotate(180deg)' : 'rotate(0deg)' }}
        >
          <polyline points="6 9 12 15 18 9"/>
        </svg>
      </div>

      {expanded && (
        <div className="mt-2.5 pt-2.5 border-t-[1.5px] border-dashed border-ink-faint">
          <p
            className="text-[13px] leading-relaxed font-sans"
            style={{ color: isComplete ? '#3d2b1f' : '#7a6b5f' }}
          >
            {content}
            {isStreaming && !isComplete && (
              <span className="inline-block w-0.5 h-4 bg-accent-pink ml-0.5 animate-blink align-middle" />
            )}
          </p>
        </div>
      )}
    </div>
  );
};

export default ThinkingBlock;
