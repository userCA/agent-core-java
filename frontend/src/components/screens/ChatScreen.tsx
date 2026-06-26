import React, { useRef, useEffect } from 'react';
import { AIAvatar, PetAvatar } from '../Icons';
import ReasoningThread from '../streaming/ReasoningThread';
import TypingIndicator from '../streaming/TypingIndicator';
import type { AgentTurn } from '../../lib/stream/types';
import type { Msg } from '../../data/appData';

/* ─── One AI turn (avatar + streaming blocks + answer bubble) ─── */
const AiTurnView: React.FC<{ turn: AgentTurn; time?: string }> = ({ turn, time }) => {
  if (turn.phase === 'typing') return <TypingIndicator />;
  const answering = !turn.answerDone && turn.answer.length > 0;
  return (
    <div className="flex gap-3 animate-slide-down">
      <AIAvatar size={32} />
      <div className="flex-1 min-w-0 space-y-1">
        {turn.blocks.length > 0 && <ReasoningThread blocks={turn.blocks} active={!turn.answerDone} />}
        {turn.answer && (
          <div className="msg-ai inline-block px-4 py-3 max-w-[90%] text-[15px] leading-relaxed">
            {turn.answer}
            {answering && <span className="inline-block w-0.5 h-4 bg-accent-pink ml-0.5 align-middle animate-blink" />}
          </div>
        )}
        {time && turn.answerDone && (
          <span className="text-[10px] text-ink-muted mt-1 ml-1 block font-comic">{time}</span>
        )}
      </div>
    </div>
  );
};

interface ChatScreenProps {
  messages: Msg[];
  turn: AgentTurn;
  isStreaming: boolean;
  inputValue: string;
  onInputChange: (value: string) => void;
  onSend: () => void;
  onStop: () => void;
  onOpenToolDrawer: () => void;
  onOpenHistory: () => void;
  onOpenCompanion: () => void;
}

const ChatScreen: React.FC<ChatScreenProps> = ({
  messages,
  turn,
  isStreaming,
  inputValue,
  onInputChange,
  onSend,
  onStop,
  onOpenToolDrawer,
  onOpenHistory,
  onOpenCompanion,
}) => {
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (scrollRef.current) scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
  }, [messages, turn]);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      onSend();
    }
  };

  const statusLabel = (() => {
    if (!isStreaming) return '在线';
    if (turn.phase === 'typing') return '思考中...';
    const running = [...turn.blocks].reverse().find((b) => b.status === 'running');
    if (running) {
      const map: Record<string, string> = {
        thinking: '思考中...', tool: '调用工具...', skill: '执行技能...',
        code: '生成代码...', search: '搜索中...', diff: '对比代码...', file: '文件操作中...',
      };
      return map[running.spec.kind] ?? '处理中...';
    }
    return turn.answer ? '回复中...' : '处理中...';
  })();

  return (
    <>
      <header className="flex items-center justify-between px-4 pt-12 pb-3 bg-cream-bg/95 backdrop-blur-sm z-20 border-b-2 border-border-passive flex-shrink-0">
        <button onClick={onOpenHistory} aria-label="日记列表" className="w-11 h-11 flex items-center justify-center rounded-button hover:bg-ink/5 transition-colors cursor-pointer">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z" /><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z" />
          </svg>
        </button>
        <div className="flex flex-col items-center">
          <h1 className="text-[16px] font-bold text-ink tracking-tight font-display">咪兔</h1>
          <span className="text-[11px] text-ink-muted flex items-center gap-1.5 font-comic min-h-[18px]">
            {isStreaming ? (
              <span className="w-2.5 h-2.5 rounded-full bg-accent-pink border-2 border-ink" />
            ) : (
              <span className="w-2 h-2 rounded-full bg-accent-sage border-2 border-ink" />
            )}
            {statusLabel}
          </span>
        </div>
        <button onClick={onOpenCompanion} aria-label="伙伴状态" className="pet-bounce w-11 h-11 flex items-center justify-center rounded-button border-2 border-ink bg-cream-card hover:bg-cream-warm transition-all shadow-card cursor-pointer">
          <PetAvatar size={20} />
        </button>
      </header>

      <div ref={scrollRef} className="flex-1 overflow-y-auto custom-scroll px-4 py-4 space-y-4">
        <div className="flex items-center justify-center gap-3 py-2">
          <div className="h-px flex-1 dashed-divider" />
          <span className="text-[13px] text-ink-muted font-bold tracking-wide uppercase font-display">今天</span>
          <div className="h-px flex-1 dashed-divider" />
        </div>

        {messages.map((m) =>
          m.role === 'user' ? (
            <div key={m.id} className="flex gap-3 justify-end animate-slide-up">
              <div className="flex flex-col items-end">
                <div className="msg-user inline-block px-4 py-3 max-w-[85%] text-[15px] leading-relaxed">{m.content}</div>
                <span className="text-[10px] text-ink-muted mt-1 mr-1 font-comic">{m.time}</span>
              </div>
            </div>
          ) : (
            <AiTurnView key={m.id} turn={m.turn} time={m.time} />
          ),
        )}

        {/* in-flight turn */}
        {isStreaming && <AiTurnView turn={turn} />}
      </div>

      {/* Input */}
      <div className="px-4 pb-8 pt-3 bg-cream-bg border-t-2 border-border-passive flex-shrink-0">
        <div className="flex items-center gap-2">
          <button onClick={onOpenToolDrawer} aria-label="打开工具箱" className="w-11 h-11 flex items-center justify-center rounded-button border-2 border-ink bg-cream-card hover:bg-cream-warm transition-all flex-shrink-0 shadow-card cursor-pointer">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#7a6b5f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
            </svg>
          </button>
          <div className="flex-1 relative">
            <label htmlFor="chat-input" className="sr-only">消息输入框</label>
            <textarea
              id="chat-input"
              value={inputValue}
              onChange={(e) => onInputChange(e.target.value)}
              onKeyDown={handleKeyDown}
              rows={1}
              placeholder={isStreaming ? '咪兔正在回复…' : '说点什么...'}
              className="w-full px-4 py-[9px] bg-cream-card border-2 border-ink rounded-button text-[15px] text-ink placeholder:text-ink-muted/70 resize-none input-focus leading-relaxed font-comic"
              style={{ minHeight: '44px', maxHeight: '120px' }}
            />
          </div>
          {isStreaming ? (
            <button onClick={onStop} aria-label="停止生成" className="w-11 h-11 rounded-button flex items-center justify-center flex-shrink-0 border-2 border-ink shadow-card cursor-pointer transition-all active:scale-95" style={{ background: '#e89080' }}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="#3d2b1f" stroke="#3d2b1f" strokeWidth="2" strokeLinejoin="round"><rect x="6" y="6" width="12" height="12" rx="2" /></svg>
            </button>
          ) : (
            <button onClick={onSend} disabled={!inputValue.trim()} aria-label="发送" className="w-11 h-11 btn-dark rounded-button flex items-center justify-center flex-shrink-0 cursor-pointer disabled:opacity-45 disabled:cursor-not-allowed">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fdf6e3" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <line x1="12" y1="19" x2="12" y2="5" /><polyline points="5 12 12 5 19 12" />
              </svg>
            </button>
          )}
        </div>
      </div>
    </>
  );
};

export default ChatScreen;
