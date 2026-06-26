import React from 'react';

interface DiffLine {
  type: 'add' | 'del' | 'neutral';
  oldNum?: string;
  newNum?: string;
  content: string;
}

interface GitDiffBlockProps {
  filename?: string;
  stats?: { add: number; del: number };
  lines?: DiffLine[];
  bare?: boolean;
}

const GitDiffBlock: React.FC<GitDiffBlockProps> = ({
  filename = 'src/auth/AuthProvider.java',
  stats = { add: 42, del: 8 },
  lines = [
    { type: 'neutral', oldNum: '...', newNum: '...', content: '...' },
    { type: 'del', oldNum: '145', newNum: '', content: '        String token = tokenManager.getToken(creds);' },
    { type: 'del', oldNum: '146', newNum: '', content: '        return token;' },
    { type: 'add', oldNum: '', newNum: '145', content: '        if (creds == null || !creds.isValid()) {' },
    { type: 'add', oldNum: '', newNum: '146', content: '            throw new AuthException("Invalid credentials");' },
    { type: 'add', oldNum: '', newNum: '147', content: '        }' },
    { type: 'add', oldNum: '', newNum: '148', content: '        String token = tokenManager.issueToken(creds);' },
    { type: 'add', oldNum: '', newNum: '149', content: '        auditLog.record("AUTH", creds.getUser());' },
    { type: 'add', oldNum: '', newNum: '150', content: '        return new AuthResult(token);' },
    { type: 'neutral', oldNum: '...', newNum: '...', content: '...' },
  ],
  bare = false,
}) => {
  return (
    <div className={bare ? 'border-[1.5px] border-ink rounded-[12px] overflow-hidden' : 'streaming-card animate-slide-down p-0 overflow-hidden'}>
      {/* Header */}
      <div className="flex items-center gap-2 px-4 py-2.5 bg-cream-warm border-b-[1.5px] border-ink">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="18" cy="18" r="3"/><circle cx="6" cy="6" r="3"/><path d="M13 6h3a2 2 0 0 1 2 2v7"/><path d="M11 18H8a2 2 0 0 1-2-2V9"/>
        </svg>
        <span className="text-[12px] font-semibold text-ink font-sans">{filename}</span>
        <div className="ml-auto flex gap-2 font-comic text-[10px]">
          <span className="text-emerald-600">+{stats.add}</span>
          <span className="text-rose-500">-{stats.del}</span>
        </div>
      </div>

      {/* Diff content */}
      <div className="font-mono text-[11.5px] leading-[1.6] overflow-x-auto custom-scroll p-2">
        {lines.map((line, i) => (
          <div
            key={i}
            className={`flex min-w-full ${line.type === 'add' ? 'diff-line-add' : line.type === 'del' ? 'diff-line-del' : ''}`}
          >
            <span className="w-9 text-right pr-3 text-ink-muted text-[10px] flex-shrink-0 select-none">
              {line.oldNum}
            </span>
            <span className="w-9 text-right pr-3 text-ink-muted text-[10px] flex-shrink-0 select-none">
              {line.newNum}
            </span>
            <span className="w-4 text-center flex-shrink-0 diff-prefix">
              {line.type === 'add' ? '+' : line.type === 'del' ? '-' : ' '}
            </span>
            <span className="text-ink pr-3.5 whitespace-pre">{line.content}</span>
          </div>
        ))}
      </div>
    </div>
  );
};

export default GitDiffBlock;
