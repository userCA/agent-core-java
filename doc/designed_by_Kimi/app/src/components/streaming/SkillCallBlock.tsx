import React from 'react';
import { StatusIcon } from '../Icons';

interface SubTool {
  name: string;
  status: 'pending' | 'running' | 'complete' | 'error';
}

interface SkillCallBlockProps {
  skillName?: string;
  description?: string;
  icon?: string;
  status?: 'running' | 'complete' | 'error';
  progress?: number;
  progressText?: string;
  subTools?: SubTool[];
}

const SkillCallBlock: React.FC<SkillCallBlockProps> = ({
  skillName = 'Fullstack Analysis',
  description = 'Multi-step code analysis and refactoring',
  status = 'running',
  progress = 65,
  progressText = 'Step 2 of 3: Analyzing dependencies...',
  subTools = [
    { name: 'Search codebase', status: 'complete' },
    { name: 'Analyze dependencies', status: 'running' },
    { name: 'Generate refactor plan', status: 'pending' },
  ],
}) => {
  const isComplete = status === 'complete';

  const getIconSvg = () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="m21.64 3.64-1.28-1.28a1.21 1.21 0 0 0-1.72 0L2.36 18.64a1.21 1.21 0 0 0 0 1.72l1.28 1.28a1.2 1.2 0 0 0 1.72 0L21.64 5.36a1.2 1.2 0 0 0 0-1.72"/><path d="m14 7 3 3"/><path d="M5 6v4"/><path d="M19 14v4"/><path d="M10 2v2"/><path d="M7 8H3"/><path d="M21 16h-4"/><path d="M11 3H9"/>
    </svg>
  );

  return (
    <div
      className="relative overflow-hidden animate-slide-down"
      style={{
        background: 'linear-gradient(135deg, #fdf6e3 0%, #f9d5c0 100%)',
        border: '2.5px solid #3d2b1f',
        borderRadius: '16px',
        padding: '16px',
        boxShadow: '4px 4px 0 #3d2b1f',
        marginTop: '8px',
        maxWidth: '90%',
      }}
    >
      {/* Corner badge */}
      <div
        className="absolute top-[6px] right-[-22px] w-[88px] text-center py-[2px] border border-ink"
        style={{
          transform: 'rotate(45deg)',
          background: isComplete ? '#a8c4a0' : '#f4a8a8',
          color: '#3d2b1f',
          fontSize: '8px',
          fontFamily: 'ZCOOL KuaiLe, Noto Sans SC, cursive',
          fontWeight: 700,
          letterSpacing: '1px',
        }}
      >
        技能
      </div>

      {/* Complete stamp */}
      {isComplete && (
        <div
          className="absolute bottom-3 right-3 w-12 h-12 rounded-full border-[2.5px] border-accent-sage flex items-center justify-center pointer-events-none opacity-60"
          style={{ transform: 'rotate(-15deg)' }}
        >
          <span className="text-[10px] font-display font-bold text-accent-sage">完成</span>
        </div>
      )}

      {/* Header */}
      <div className="flex items-center gap-3 mb-3">
        <div
          className="w-8 h-8 rounded-button flex items-center justify-center shadow-card"
          style={{ background: '#f4b89a', border: '2px solid #3d2b1f' }}
        >
          {getIconSvg()}
        </div>
        <div>
          <div className="text-[15px] font-bold text-ink font-display">{skillName}</div>
          <div className="text-[11px] text-ink-muted font-comic">{description}</div>
        </div>
      </div>

      {/* Progress bar */}
      <div className="my-3">
        <div className="hand-progress">
          <div
            className="hand-progress-fill relative overflow-hidden transition-all duration-400"
            style={{
              width: `${progress}%`,
              background: isComplete ? '#a8c4a0' : 'linear-gradient(90deg, #f4b89a 0%, #f4a8a8 100%)',
            }}
          >
            {!isComplete && (
              <div
                className="absolute inset-0 animate-skill-stripe"
                style={{
                  background: 'repeating-linear-gradient(45deg, transparent, transparent 4px, rgba(255,255,255,0.3) 4px, rgba(255,255,255,0.3) 8px)',
                }}
              />
            )}
          </div>
        </div>
        <div className="text-[10px] text-ink-muted font-comic mt-1 text-center">{progressText}</div>
      </div>

      {/* Sub-tools list */}
      <div className="mt-3 pt-3 border-t-[1.5px] border-dashed border-ink-faint">
        {subTools.map((tool, i) => (
          <div key={i} className="flex items-center gap-2 py-1.5">
            <StatusIcon status={tool.status} size={16} />
            <span
              className={`text-[12px] font-sans ${tool.status === 'complete' ? 'text-ink-muted line-through' : 'text-ink'}`}
            >
              {tool.name}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
};

export default SkillCallBlock;
