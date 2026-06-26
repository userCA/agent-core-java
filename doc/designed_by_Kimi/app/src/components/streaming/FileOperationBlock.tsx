import React from 'react';

interface FileOp {
  type: 'create' | 'read' | 'update' | 'delete';
  path: string;
  meta?: string;
  status: 'pending' | 'success' | 'error';
}

interface FileOperationBlockProps {
  operations?: FileOp[];
  bare?: boolean;
}

const fileOpConfig = {
  create: {
    bg: '#a8c4a0',
    icon: (
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/><path d="M9 15h6"/><path d="M12 18v-6"/>
      </svg>
    ),
    label: '已创建',
  },
  read: {
    bg: '#a8c8e8',
    icon: (
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/><path d="M10 9H8"/><path d="M16 13H8"/><path d="M16 17H8"/>
      </svg>
    ),
    label: '已读取',
  },
  update: {
    bg: '#f9d5c0',
    icon: (
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/><path d="M10 18 16 12"/><path d="m14 12 2 2"/><path d="m16 12-2-2"/>
      </svg>
    ),
    label: '已修改',
  },
  delete: {
    bg: '#e89080',
    icon: (
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/><path d="M9 15h6"/>
      </svg>
    ),
    label: '已删除',
  },
};

const statusStyle = {
  pending: { bg: '#f5ecd0', border: '1px solid #3d2b1f', text: '#7a6b5f' },
  success: { bg: '#a8c4a0', border: '1px solid #3d2b1f', text: '#3d2b1f' },
  error: { bg: '#e89080', border: '1px solid #3d2b1f', text: '#3d2b1f' },
};

const FileOperationBlock: React.FC<FileOperationBlockProps> = ({
  operations = [
    { type: 'read', path: 'src/auth/AuthProvider.java', meta: '1.2 KB', status: 'success' },
    { type: 'read', path: 'src/auth/TokenManager.java', meta: '890 B', status: 'success' },
    { type: 'update', path: 'src/auth/AuthProvider.java', meta: 'Added null check', status: 'success' },
    { type: 'create', path: 'src/auth/AuthResult.java', meta: '245 B', status: 'pending' },
  ],
  bare = false,
}) => {
  return (
    <div className={bare ? '' : 'streaming-card animate-slide-down'}>
      {!bare && (
        <>
          {/* Header */}
          <div className="flex items-center gap-2 mb-2.5">
            <div
              className="w-6 h-6 rounded-md flex items-center justify-center"
              style={{ background: '#f5ecd0', border: '1.5px solid #3d2b1f' }}
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/><path d="M10 9H8"/><path d="M16 13H8"/><path d="M16 17H8"/>
              </svg>
            </div>
            <span className="text-[13px] font-bold text-ink font-display">文件操作</span>
            <span className="ml-auto text-[10px] font-comic text-ink-muted px-2 py-0.5 bg-cream-warm border border-ink rounded-pill">{operations.length} 个操作</span>
          </div>

          {/* Divider */}
          <div className="mb-2.5 border-t-[1.5px] border-dashed border-ink-faint" />
        </>
      )}

      {/* Operations */}
      <div className="space-y-0">
        {operations.map((op, i) => {
          const config = fileOpConfig[op.type];
          const st = statusStyle[op.status];
          return (
            <div key={i} className="flex items-center gap-2.5 py-2 border-b border-dashed border-ink-ghost last:border-0">
              <div
                className="w-7 h-7 rounded-lg flex items-center justify-center flex-shrink-0"
                style={{ background: config.bg, border: '1.5px solid #3d2b1f' }}
              >
                {config.icon}
              </div>
              <div className="flex-1 min-w-0">
                <div className="text-[12px] text-ink font-sans truncate">{op.path}</div>
                {op.meta && (
                  <div className="text-[10px] text-ink-muted font-comic mt-0.5">{op.meta}</div>
                )}
              </div>
              <span
                className="text-[10px] font-comic px-2 py-0.5 rounded-pill flex-shrink-0"
                style={{ background: st.bg, border: st.border, color: st.text }}
              >
                {config.label}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default FileOperationBlock;