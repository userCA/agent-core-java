import React from 'react';

const TypingIndicator: React.FC = () => (
  <div className="flex gap-3">
    <div className="w-8 h-8 rounded-full bg-cream-card border-2 border-ink flex items-center justify-center flex-shrink-0 mt-1 shadow-card">
      <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 3c.132 0 .263 0 .393 0a7.5 7.5 0 0 0 7.92 12.446a9 9 0 1 1 -8.313 -12.454z"/>
        <path d="M12 8v4"/>
        <path d="M12 16h.01"/>
      </svg>
    </div>
    <div className="msg-ai px-4 py-3 shadow-card">
      <div className="flex gap-1">
        <span className="w-2 h-2 rounded-full bg-ink-muted typing-dot" />
        <span className="w-2 h-2 rounded-full bg-ink-muted typing-dot" />
        <span className="w-2 h-2 rounded-full bg-ink-muted typing-dot" />
      </div>
    </div>
  </div>
);

export default TypingIndicator;
