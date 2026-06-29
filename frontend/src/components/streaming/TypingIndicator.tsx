import React from 'react';
import { AIAvatar } from '../Icons';

const TypingIndicator: React.FC = () => (
  <div className="flex gap-3">
    <AIAvatar size={32} />
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
