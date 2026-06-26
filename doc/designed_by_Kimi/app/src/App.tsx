import { useState, useEffect } from 'react';
import { ToolIcon, PetAvatar } from './components/Icons';
import ChatScreen from './components/screens/ChatScreen';
import HistoryScreen from './components/screens/HistoryScreen';
import { useAgentStream } from './hooks/useAgentStream';
import { buildScript, DEMO_SCRIPT } from './lib/stream/scripts';
import type { Script } from './lib/stream/types';
import { INITIAL, type Msg, now, TOOLS, HISTORY_ENTRIES } from './data/appData';

type Screen = 'chat' | 'history';

function App() {
  const [screen, setScreen] = useState<Screen>('chat');
  const [showToolDrawer, setShowToolDrawer] = useState(false);
  const [showCompanion, setShowCompanion] = useState(false);
  const [messages, setMessages] = useState<Msg[]>(INITIAL);
  const [inputValue, setInputValue] = useState('');
  const [petReaction, setPetReaction] = useState<string | null>(null);

  const { turn, isStreaming, start, stop } = useAgentStream();

  useEffect(() => {
    if (screen !== 'chat') return;
    // auto-return to chat when streaming starts so the user sees the reply
  }, [screen, isStreaming]);

  /* ── Run a script: stream, then commit the final turn to messages ── */
  const runScript = (script: Script) => {
    start(script, (finalTurn) => {
      setMessages((prev) => [...prev, { id: `a_${Date.now()}`, role: 'ai', turn: finalTurn, time: now() }]);
    });
  };

  const send = (text: string) => {
    const trimmed = text.trim();
    if (!trimmed || isStreaming) return;
    setMessages((prev) => [...prev, { id: `u_${Date.now()}`, role: 'user', content: trimmed, time: now() }]);
    runScript(buildScript(trimmed));
  };

  const handleSend = () => { send(inputValue); setInputValue(''); };

  const handleQuickSend = (text: string) => {
    setShowToolDrawer(false);
    send(text);
  };

  const runDemo = () => {
    if (isStreaming) return;
    setShowToolDrawer(false);
    setMessages((prev) => [...prev, { id: `u_${Date.now()}`, role: 'user', content: '演示全部流式卡片', time: now() }]);
    runScript(DEMO_SCRIPT);
  };

  const handlePetReaction = (type: string) => {
    const reactions: Record<string, string> = {
      poke: '咪兔 咯咯笑着跳来跳去！',
      feed: '咪兔 开心地啃着零食，真香！',
      praise: '咪兔 骄傲地挺起了胸膛！',
    };
    setPetReaction(reactions[type] || '');
    setTimeout(() => setPetReaction(null), 2000);
  };

  return (
    <div className="phone-frame">
      <div className={`screen ${screen === 'chat' ? 'screen-active' : 'screen-left'}`}>
        <ChatScreen
          messages={messages}
          turn={turn}
          isStreaming={isStreaming}
          inputValue={inputValue}
          onInputChange={setInputValue}
          onSend={handleSend}
          onStop={stop}
          onOpenToolDrawer={() => setShowToolDrawer(true)}
          onOpenHistory={() => setScreen('history')}
          onOpenCompanion={() => setShowCompanion(true)}
        />
      </div>
      <div className={`screen ${screen === 'history' ? 'screen-active' : 'screen-right'}`}>
        <HistoryScreen
          entries={HISTORY_ENTRIES}
          onSelect={() => setScreen('chat')}
          onNewChat={() => setScreen('chat')}
        />
      </div>

      {/* Tool Drawer */}
      <div className={`overlay absolute inset-0 bg-ink/20 backdrop-blur-sm z-30 ${showToolDrawer ? '' : 'hidden-overlay'}`} onClick={() => setShowToolDrawer(false)} />
      <div className={`bottom-sheet absolute bottom-0 left-0 right-0 bg-cream-bg rounded-t-[20px] z-40 max-h-[70%] flex flex-col ${showToolDrawer ? '' : 'hidden-sheet'}`}>
        <button type="button" aria-label="关闭工具箱" className="flex justify-center pt-3 pb-2 cursor-pointer w-full" onClick={() => setShowToolDrawer(false)}>
          <div className="w-9 h-1.5 rounded-full bg-ink/25 border border-ink/20" />
        </button>
        <div className="px-5 pb-3 flex items-center justify-between">
          <h2 className="text-[18px] font-bold text-ink font-display">工具箱</h2>
          <button onClick={() => setShowToolDrawer(false)} aria-label="关闭工具箱" className="w-11 h-11 flex items-center justify-center rounded-button hover:bg-ink/5 transition-colors cursor-pointer">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#7a6b5f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>
        <div className="flex-1 overflow-y-auto custom-scroll px-5 pb-6">
          <div className="grid grid-cols-3 gap-3">
            {TOOLS.map((tool, i) => (
              <button key={i} onClick={() => handleQuickSend(tool.name)} className="flex flex-col items-center gap-2 p-4 bg-cream-card border-2 border-ink rounded-card hover:bg-cream-warm transition-all active:scale-95 shadow-card cursor-pointer">
                <div className="w-10 h-10 rounded-button bg-cream-warm border-2 border-ink flex items-center justify-center">
                  <ToolIcon icon={tool.icon} size={20} bg="transparent" />
                </div>
                <span className="text-xs font-bold text-ink font-comic">{tool.name}</span>
              </button>
            ))}
          </div>
          <button onClick={runDemo} disabled={isStreaming} className="btn-light w-full mt-4 py-2.5 text-xs font-comic flex items-center justify-center gap-1.5 cursor-pointer disabled:opacity-45 disabled:cursor-not-allowed">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="m12 3-1.9 5.8a2 2 0 0 1-1.3 1.3L3 12l5.8 1.9a2 2 0 0 1 1.3 1.3L12 21l1.9-5.8a2 2 0 0 1 1.3-1.3L21 12l-5.8-1.9a2 2 0 0 1-1.3-1.3Z" />
            </svg>
            演示全部流式卡片
          </button>
        </div>
      </div>

      {/* Companion */}
      <div className={`overlay absolute inset-0 bg-ink/20 backdrop-blur-sm z-30 ${showCompanion ? '' : 'hidden-overlay'}`} onClick={() => setShowCompanion(false)} />
      <div className={`companion-panel absolute bottom-0 left-0 right-0 bg-cream-bg rounded-t-[20px] border-t-2 border-ink z-40 max-h-[60%] flex flex-col ${showCompanion ? '' : 'hidden-panel'}`}>
        <button type="button" aria-label="关闭伙伴面板" className="flex justify-center pt-3 pb-2 cursor-pointer w-full" onClick={() => setShowCompanion(false)}>
          <div className="w-9 h-1.5 rounded-full bg-ink/25 border border-ink/20" />
        </button>
        <div className="px-5 pb-4 flex items-center gap-3">
          <div className="w-14 h-14 rounded-full bg-accent-pink border-2 border-ink flex items-center justify-center shadow-card">
            <PetAvatar size={36} />
          </div>
          <div>
            <h2 className="text-[18px] font-bold text-ink font-display">咪兔</h2>
            <p className="text-xs text-ink-muted font-comic">你的编程小助手</p>
          </div>
          <button onClick={() => setShowCompanion(false)} aria-label="关闭面板" className="w-11 h-11 flex items-center justify-center rounded-button hover:bg-ink/5 transition-colors ml-auto cursor-pointer">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#7a6b5f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        {petReaction && (
          <div className="px-5 mb-3">
            <div className="animate-pop-in px-4 py-3 rounded-card border-2 border-ink" style={{ background: 'rgba(244,168,168,0.3)' }}>
              <p className="text-xs text-ink font-bold font-comic">{petReaction}</p>
            </div>
          </div>
        )}

        <div className="flex-1 overflow-y-auto custom-scroll px-5 pb-6 space-y-3">
          <div className="bg-cream-card border-2 border-ink rounded-card p-4 shadow-card">
            <div className="flex items-center gap-2 mb-3">
              <svg className="w-5 h-5" viewBox="0 0 24 24" fill="#f4a8a8" stroke="#3d2b1f" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z" />
              </svg>
              <span className="text-[13px] font-bold text-ink uppercase tracking-wide font-display">心情</span>
            </div>
            <div className="flex items-center gap-3">
              <div className="flex-1 hand-progress"><div className="hand-progress-fill" style={{ width: '85%' }} /></div>
              <span className="text-xs font-bold text-ink font-comic">开心</span>
            </div>
          </div>

          <div className="bg-cream-card border-2 border-ink rounded-card p-4 shadow-card">
            <div className="flex items-center gap-2 mb-3">
              <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="#a8c4a0" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M22 12h-4l-3 9L9 3l-3 9H2" />
              </svg>
              <span className="text-[13px] font-bold text-ink uppercase tracking-wide font-display">今日</span>
            </div>
            <div className="grid grid-cols-3 gap-3">
              {[{ val: '6', label: '对话' }, { val: '12', label: '消息' }, { val: '3', label: '工具' }].map((s, i) => (
                <div key={i} className="text-center">
                  <div className="text-[20px] font-bold text-ink font-display">{s.val}</div>
                  <div className="text-[10px] text-ink-muted font-comic">{s.label}</div>
                </div>
              ))}
            </div>
          </div>

          <div className="flex gap-2">
            {[{ key: 'poke', label: '戳一戳' }, { key: 'feed', label: '喂食' }, { key: 'praise', label: '表扬' }].map((action) => (
              <button key={action.key} onClick={() => handlePetReaction(action.key)} className="flex-1 py-2.5 bg-cream-card border-2 border-ink rounded-button text-xs font-bold text-ink hover:bg-cream-warm transition-all active:scale-95 shadow-card font-comic cursor-pointer">
                {action.label}
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

export default App;
