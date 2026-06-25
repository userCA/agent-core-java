import React, { useState, useRef, useEffect } from 'react';
import { AIAvatar, PetAvatar, ToolIcon } from './components/Icons';
import ThinkingBlock from './components/streaming/ThinkingBlock';
import ToolCallBlock from './components/streaming/ToolCallBlock';
import SkillCallBlock from './components/streaming/SkillCallBlock';
import CodeBlock from './components/streaming/CodeBlock';
import SearchResultBlock from './components/streaming/SearchResultBlock';
import GitDiffBlock from './components/streaming/GitDiffBlock';
import FileOperationBlock from './components/streaming/FileOperationBlock';

type Screen = 'chat' | 'history';
type DemoScene = 'thinking' | 'tool' | 'skill' | 'code' | 'search' | 'diff' | 'file' | 'all';
type StreamingStatus = 'idle' | 'thinking' | 'searching' | 'tool-calling' | 'skill-running' | 'coding' | 'diffing' | 'file-ops';

interface Message {
  id: string;
  type: 'user' | 'ai' | 'system';
  content?: string;
  time: string;
  components?: React.ReactNode;
  variant?: 'bubble' | 'card';
  status?: StreamingStatus;
}

/* ─── Demo scene picker ─── */
const DEMO_SCENES: { key: DemoScene; label: string }[] = [
  { key: 'thinking', label: '思考过程' },
  { key: 'tool', label: '工具调用' },
  { key: 'skill', label: '技能调用' },
  { key: 'code', label: '代码块' },
  { key: 'search', label: '搜索结果' },
  { key: 'diff', label: '代码对比' },
  { key: 'file', label: '文件操作' },
  { key: 'all', label: '全部展示' },
];

/* ─── Status label map ─── */
const STATUS_LABEL: Record<StreamingStatus, string> = {
  idle: '在线',
  thinking: '思考中...',
  searching: '搜索中...',
  'tool-calling': '调用工具...',
  'skill-running': '执行技能...',
  coding: '生成代码...',
  diffing: '对比代码...',
  'file-ops': '文件操作中...',
};

/* ─── Demo data ─── */
const DEMO_MESSAGES: Record<DemoScene, Message[]> = {
  thinking: [
    { id: 't-u', type: 'user', content: '帮我重构一下认证模块', time: '8:32' },
    {
      id: 't-a', type: 'ai', content: '让我先分析一下认证模块的结构。', time: '8:32',
      components: (
        <ThinkingBlock
          isComplete={true}
          content="1. 首先理解当前的认证流程：用户发送凭证 → 服务端校验 → 签发 Token。2. 发现 AuthProvider 类第 147 行存在潜在 NPE，token 使用前没有判空。3. TokenManager.issueToken() 在凭证无效时可能返回 null，但调用方未处理。4. 应该添加卫语句，将校验逻辑抽取为独立方法。"
        />
      ),
    },
  ],

  tool: [
    { id: 'to-u', type: 'user', content: '搜索认证相关的代码', time: '8:33' },
    {
      id: 'to-a1', type: 'ai', content: '正在搜索代码库中认证相关的代码。', time: '8:33',
      components: (
        <ToolCallBlock
          toolName="代码搜索"
          icon="search"
          status="complete"
          result="找到 3 个文件：AuthProvider.java、TokenManager.java、JwtUtil.java"
          resultCount="3 个结果"
          params={[{ key: '关键词', value: '认证模块' }, { key: '语言', value: 'java' }]}
        />
      ),
    },
    {
      id: 'to-a2', type: 'ai', content: '我还检查一下 Git 改动记录。', time: '8:33',
      components: (
        <ToolCallBlock
          toolName="Git 对比"
          icon="git-compare"
          status="executing"
          resultCount="检查中..."
          params={[{ key: '分支', value: 'main' }]}
        />
      ),
    },
  ],

  skill: [
    { id: 'sk-u', type: 'user', content: '对认证系统做一次全面分析', time: '8:35' },
    {
      id: 'sk-a', type: 'ai', content: '正在启动认证系统的全面分析。', time: '8:35',
      components: <SkillCallBlock progress={66} progressText="第 2 / 3 步：分析依赖关系..." />,
    },
  ],

  code: [
    { id: 'co-u', type: 'user', content: '给我看一下 AuthProvider 的代码', time: '8:36' },
    {
      id: 'co-a', type: 'ai', content: '这是 AuthProvider.java 的实现：', time: '8:36',
      components: <CodeBlock language="java" isStreaming={false} />,
    },
  ],

  search: [
    { id: 'se-u', type: 'user', content: '找出所有认证相关文件', time: '8:37' },
    { id: 'se-a', type: 'ai', content: '在代码库中找到 3 个匹配文件：', time: '8:37', components: <SearchResultBlock /> },
  ],

  diff: [
    { id: 'di-u', type: 'user', content: '看一下代码改动', time: '8:38' },
    { id: 'di-a', type: 'ai', content: '这是 AuthProvider.java 的改动内容：', time: '8:38', components: <GitDiffBlock /> },
  ],

  file: [
    { id: 'fi-u', type: 'user', content: '应用这次重构', time: '8:39' },
    { id: 'fi-a', type: 'ai', content: '正在应用认证模块的重构改动：', time: '8:39', components: <FileOperationBlock /> },
  ],

  /* ═══ ALL — timeline of independent cards ═══ */
  all: [
    { id: 'al-u', type: 'user', content: '完整重构认证模块', time: '8:40' },

    /* 1. AI starts → thinking bubble */
    {
      id: 'al-a1', type: 'ai', content: '我来完整重构认证模块。先分析一下现状。', time: '8:40',
      status: 'thinking',
    },

    /* 2. Thinking card (standalone) */
    {
      id: 'al-c1', type: 'system', time: '8:40', variant: 'card', status: 'thinking',
      components: (
        <ThinkingBlock
          isComplete={true}
          content="认证模块分析：1) AuthProvider.authenticate() 第 147 行使用 token 前未判空，存在 NPE 风险。2) 凭证校验逻辑散落在多处，应抽取为独立 Validator 类。3) 缺少审计日志，无法追踪认证行为。4) TokenManager.issueToken() 异常处理不完善。"
        />
      ),
    },

    /* 3. Search results (standalone card) */
    {
      id: 'al-c2', type: 'system', time: '8:41', variant: 'card', status: 'searching',
      components: <SearchResultBlock />,
    },

    /* 4. First tool call (standalone card) */
    {
      id: 'al-c3', type: 'system', time: '8:41', variant: 'card', status: 'tool-calling',
      components: (
        <ToolCallBlock
          toolName="代码分析"
          icon="wand-2"
          status="complete"
          result="发现问题：第 147 行 NPE，缺少参数校验，异常处理不完善"
          resultCount="3 个问题"
          params={[{ key: '目标', value: 'AuthProvider.java' }]}
        />
      ),
    },

    /* 5. Second tool call (standalone card) */
    {
      id: 'al-c4', type: 'system', time: '8:42', variant: 'card', status: 'tool-calling',
      components: (
        <ToolCallBlock
          toolName="代码搜索"
          icon="search"
          status="complete"
          result="找到关联类：TokenManager、JwtUtil、AuditLog"
          resultCount="3 个类"
          params={[{ key: '关键词', value: 'auth token audit' }]}
        />
      ),
    },

    /* 6. Code block (standalone card) */
    {
      id: 'al-c5', type: 'system', time: '8:42', variant: 'card', status: 'coding',
      components: <CodeBlock language="java" isStreaming={false} />,
    },

    /* 7. Skill orchestrator (standalone card) */
    {
      id: 'al-c6', type: 'system', time: '8:43', variant: 'card', status: 'skill-running',
      components: <SkillCallBlock progress={100} progressText="重构完成！" status="complete" />,
    },

    /* 8. Git diff (standalone card) */
    {
      id: 'al-c7', type: 'system', time: '8:43', variant: 'card', status: 'diffing',
      components: <GitDiffBlock />,
    },

    /* 9. File operations (standalone card) */
    {
      id: 'al-c8', type: 'system', time: '8:44', variant: 'card', status: 'file-ops',
      components: <FileOperationBlock />,
    },

    /* 10. Final summary bubble */
    {
      id: 'al-a2', type: 'ai', content: '重构已全部完成！主要改动：添加了空值安全检查、抽取了凭证校验逻辑、新增了审计日志、优化了异常处理。共修改 2 个文件，新增 1 个文件。', time: '8:44',
    },
  ],
};

const INITIAL_MESSAGES: Message[] = [
  {
    id: 'init', type: 'ai',
    content: '欢迎使用 Journal Chat 原型！点击下方气泡按钮探索各种流式消息格式。',
    time: '8:30',
  },
];

const HISTORY_ENTRIES = [
  { date: '今天', time: '8:33', title: '认证模块重构', desc: '搜索了认证相关代码，找到 AuthProvider.java 和 TokenManager.java，讨论了重构策略。', messages: '6 条消息', tools: '2 个工具' },
  { date: '昨天', time: '16:15', title: '数据库表结构评审', desc: '评审了新的用户表结构，建议为 email 和 created_at 字段添加索引，验证了迁移脚本。', messages: '12 条消息', tools: '3 个工具' },
  { date: '6月22日', time: '10:42', title: 'API 接口设计', desc: '设计了新计费模块的 REST 接口，讨论了分页策略和错误处理模式。', messages: '8 条消息', tools: '1 个工具' },
  { date: '5月30日', time: '14:20', title: '性能优化', desc: '分析了分析仪表板中的慢查询，建议添加缓存层和重构查询语句。', messages: '15 条消息', tools: '4 个工具' },
  { date: '5月28日', time: '9:00', title: '代码评审', desc: '评审了 PR #247，发现异步处理器中存在潜在的竞态条件并提出了修复建议。', messages: '20 条消息', tools: '2 个工具' },
];

const TOOLS = [
  { icon: 'search', name: '代码搜索' },
  { icon: 'git-compare', name: 'Git 对比' },
  { icon: 'play', name: '运行测试' },
  { icon: 'wand-2', name: '重构' },
  { icon: 'bug', name: '调试' },
  { icon: 'file-text', name: '写文档' },
  { icon: 'message-square', name: '解释代码' },
  { icon: 'shield-check', name: '生成测试' },
  { icon: 'eye', name: '代码评审' },
];

/* ═══════════════════════════════════════════
   App
   ═══════════════════════════════════════════ */
function App() {
  const [screen, setScreen] = useState<Screen>('chat');
  const [activeDemo, setActiveDemo] = useState<DemoScene | null>(null);
  const [showToolDrawer, setShowToolDrawer] = useState(false);
  const [showCompanion, setShowCompanion] = useState(false);
  const [messages, setMessages] = useState<Message[]>(INITIAL_MESSAGES);
  const [inputValue, setInputValue] = useState('');
  const [petReaction, setPetReaction] = useState<string | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  /* ── scroll to bottom ── */
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages, screen]);

  /* ── derive current streaming status ── */
  const currentStatus: StreamingStatus = (() => {
    if (!activeDemo) return 'idle';
    // walk backwards to find the latest non-idle status
    for (let i = messages.length - 1; i >= 0; i--) {
      if (messages[i]?.status && messages[i].status !== 'idle') {
        return messages[i].status as StreamingStatus;
      }
    }
    return 'idle';
  })();

  const loadDemo = (scene: DemoScene) => {
    setActiveDemo(scene);
    setMessages(DEMO_MESSAGES[scene]);
  };

  const resetToInitial = () => {
    setActiveDemo(null);
    setMessages(INITIAL_MESSAGES);
  };

  const handleQuickSend = (text: string) => {
    setShowToolDrawer(false);
    const now = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false });
    const userMsg: Message = { id: `u_${Date.now()}`, type: 'user', content: text, time: now };
    setMessages(prev => [...prev, userMsg]);
  };

  const handleSend = () => {
    if (!inputValue.trim()) return;
    handleQuickSend(inputValue);
    setInputValue('');
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

  /* ─── Status dot animation ─── */
  const StatusDot = ({ status }: { status: StreamingStatus }) => {
    if (status === 'idle') {
      return <span className="w-2 h-2 rounded-full bg-accent-sage border-2 border-ink" />;
    }
    return (
      <span className="relative flex h-2.5 w-2.5">
        <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-accent-pink opacity-75" />
        <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-accent-pink border-2 border-ink" />
      </span>
    );
  };

  /* ═══ RENDER: Chat Screen ═══ */
  const renderChatScreen = () => (
    <div className="screen screen-active">
      {/* ── HEADER ── */}
      <header className="flex items-center justify-between px-4 pt-12 pb-3 bg-cream-bg/95 backdrop-blur-sm z-20 border-b-2 border-border-passive flex-shrink-0">
        <button
          onClick={() => setScreen('history')}
          className="w-10 h-10 flex items-center justify-center rounded-button hover:bg-ink/5 transition-colors"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/>
          </svg>
        </button>

        {/* Dynamic status in header center */}
        <div className="flex flex-col items-center">
          <h1 className="text-[16px] font-bold text-ink tracking-tight font-display">咪兔</h1>
          <span className="text-[11px] text-ink-muted flex items-center gap-1.5 font-comic min-h-[18px]">
            <StatusDot status={currentStatus} />
            {STATUS_LABEL[currentStatus]}
          </span>
        </div>

        <button
          onClick={() => setShowCompanion(true)}
          className="pet-bounce w-10 h-10 flex items-center justify-center rounded-button border-2 border-ink bg-cream-card hover:bg-cream-warm transition-all shadow-card"
          title="咪兔"
        >
          <PetAvatar size={20} />
        </button>
      </header>

      {/* ── MESSAGES ── */}
      <div ref={scrollRef} className="flex-1 overflow-y-auto custom-scroll px-4 py-4 space-y-4">
        {/* Date divider */}
        <div className="flex items-center justify-center gap-3 py-2">
          <div className="h-px flex-1 dashed-divider" />
          <span className="text-[13px] text-ink-muted font-bold tracking-wide uppercase font-display">今天</span>
          <div className="h-px flex-1 dashed-divider" />
        </div>

        {messages.map((msg, i) => {
          /* ── Standalone card (no avatar, no bubble) ── */
          if (msg.variant === 'card') {
            return (
              <div key={msg.id} className="pl-11 animate-slide-down" style={{ animationDelay: `${i * 0.08}s` }}>
                {msg.components}
              </div>
            );
          }

          /* ── Normal user / AI message ── */
          return (
            <div
              key={msg.id}
              className={`flex gap-3 ${msg.type === 'user' ? 'justify-end' : ''} animate-slide-down`}
              style={{ animationDelay: `${i * 0.08}s` }}
            >
              {msg.type === 'ai' && <AIAvatar size={32} />}
              <div className={`flex-1 ${msg.type === 'user' ? 'flex flex-col items-end' : ''}`}>
                {/* Text bubble */}
                {msg.content && (
                  <div className={`${msg.type === 'user' ? 'msg-user' : 'msg-ai'} inline-block px-4 py-3 max-w-[85%] text-[15px] leading-relaxed`}>
                    {msg.content}
                  </div>
                )}
                {/* Timestamp */}
                <span className={`text-[10px] text-ink-muted mt-1 font-comic ${msg.type === 'user' ? 'mr-1' : 'ml-1 block'}`}>
                  {msg.time}
                </span>
                {/* Inline components (attached to bubble) */}
                {msg.type === 'ai' && msg.components && (
                  <div className="mt-1 ml-1">{msg.components}</div>
                )}
              </div>
            </div>
          );
        })}

        {/* ── Demo selector pills ── */}
        {!activeDemo && (
          <div className="flex flex-wrap gap-2 py-4">
            {DEMO_SCENES.map((scene) => (
              <button
                key={scene.key}
                onClick={() => loadDemo(scene.key)}
                className="tool-pill flex items-center gap-1.5 px-3 py-2 bg-cream-card hover:bg-cream-warm shadow-card text-xs text-ink font-comic"
              >
                <span>{scene.label}</span>
              </button>
            ))}
          </div>
        )}

        {/* ── Reset button ── */}
        {activeDemo && (
          <div className="flex justify-center py-4">
            <button onClick={resetToInitial} className="btn-light px-4 py-2 text-xs font-comic">
              返回演示列表
            </button>
          </div>
        )}
      </div>

      {/* ── INPUT ── */}
      <div className="px-4 pb-8 pt-3 bg-cream-bg border-t-2 border-border-passive flex-shrink-0">
        <div className="flex items-center gap-2">
          <button
            onClick={() => setShowToolDrawer(true)}
            className="w-11 h-11 flex items-center justify-center rounded-button border-2 border-ink bg-cream-card hover:bg-cream-warm transition-all flex-shrink-0 shadow-card"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#7a6b5f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
          </button>
          <div className="flex-1 relative">
            <textarea
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend(); } }}
              rows={1}
              placeholder="说点什么..."
              className="w-full px-4 py-[9px] bg-cream-card border-2 border-ink rounded-button text-[15px] text-ink placeholder:text-ink-muted/50 resize-none input-focus leading-relaxed font-comic"
              style={{ minHeight: '44px', maxHeight: '120px' }}
            />
          </div>
          <button
            onClick={handleSend}
            className="w-11 h-11 btn-dark rounded-button flex items-center justify-center flex-shrink-0"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fdf6e3" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <line x1="12" y1="19" x2="12" y2="5"/><polyline points="5 12 12 5 19 12"/>
            </svg>
          </button>
        </div>
      </div>
    </div>
  );

  /* ═══ RENDER: History Screen ═══ */
  const renderHistoryScreen = () => (
    <div className="screen screen-active">
      <header className="flex items-center justify-between px-4 pt-12 pb-3 bg-cream-bg/95 backdrop-blur-sm z-20 border-b-2 border-border-passive flex-shrink-0">
        <button onClick={() => setScreen('chat')} className="w-10 h-10 flex items-center justify-center rounded-button hover:bg-ink/5 transition-colors">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/>
          </svg>
        </button>
        <h1 className="text-[16px] font-bold text-ink tracking-tight font-display">日记列表</h1>
        <button onClick={() => setScreen('chat')} className="w-10 h-10 flex items-center justify-center rounded-button hover:bg-ink/5 transition-colors">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
        </button>
      </header>

      <div className="flex-1 overflow-y-auto custom-scroll px-4 py-4 space-y-3">
        <div className="relative mb-4">
          <svg className="w-4 h-4 text-ink-muted absolute left-3 top-1/2 -translate-y-1/2" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
          <input type="text" placeholder="搜索日记..." className="w-full pl-10 pr-4 py-2.5 bg-cream-card border-2 border-ink rounded-button text-sm text-ink placeholder:text-ink-ghost input-focus font-comic" />
        </div>

        <div className="flex items-center gap-2 py-1">
          <span className="text-[13px] font-bold text-ink-muted uppercase tracking-wider font-display">2026年6月</span>
          <div className="h-px flex-1 dashed-divider" />
        </div>

        {HISTORY_ENTRIES.slice(0, 3).map((entry, i) => (
          <button key={i} onClick={() => setScreen('chat')} className="journal-card w-full text-left bg-cream-card border-2 border-ink rounded-card p-4 shadow-card">
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
        ))}

        <div className="flex items-center gap-2 py-1 mt-2">
          <span className="text-[13px] font-bold text-ink-muted uppercase tracking-wider font-display">2026年5月</span>
          <div className="h-px flex-1 dashed-divider" />
        </div>

        {HISTORY_ENTRIES.slice(3).map((entry, i) => (
          <button key={i} onClick={() => setScreen('chat')} className="journal-card w-full text-left bg-cream-card border-2 border-ink rounded-card p-4 shadow-card">
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
        ))}
      </div>
    </div>
  );

  /* ═══════════════════════════════════════════
     Root render
     ═══════════════════════════════════════════ */
  return (
    <div className="phone-frame">
      {/* Chat */}
      <div className={`screen ${screen === 'chat' ? 'screen-active' : 'screen-left'}`}>
        {renderChatScreen()}
      </div>

      {/* History */}
      <div className={`screen ${screen === 'history' ? 'screen-active' : 'screen-right'}`}>
        {renderHistoryScreen()}
      </div>

      {/* ── Tool Drawer overlay ── */}
      <div
        className={`overlay absolute inset-0 bg-ink/20 backdrop-blur-sm z-30 ${showToolDrawer ? '' : 'hidden-overlay'}`}
        onClick={() => setShowToolDrawer(false)}
      />
      <div className={`bottom-sheet absolute bottom-0 left-0 right-0 bg-cream-bg rounded-t-[20px] z-40 max-h-[70%] flex flex-col ${showToolDrawer ? '' : 'hidden-sheet'}`}>
        <div className="flex justify-center pt-3 pb-2 cursor-pointer" onClick={() => setShowToolDrawer(false)}>
          <div className="w-9 h-1.5 rounded-full bg-ink/25 border border-ink/20" />
        </div>
        <div className="px-5 pb-3 flex items-center justify-between">
          <h2 className="text-[18px] font-bold text-ink font-display">工具箱</h2>
          <button onClick={() => setShowToolDrawer(false)} className="w-8 h-8 flex items-center justify-center rounded-button hover:bg-ink/5 transition-colors">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#7a6b5f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div className="flex-1 overflow-y-auto custom-scroll px-5 pb-6">
          <div className="grid grid-cols-3 gap-3">
            {TOOLS.map((tool, i) => (
              <button key={i} onClick={() => handleQuickSend(`${tool.name}`)} className="flex flex-col items-center gap-2 p-4 bg-cream-card border-2 border-ink rounded-card hover:bg-cream-warm transition-all active:scale-95 shadow-card">
                <div className="w-10 h-10 rounded-button bg-cream-warm border-2 border-ink flex items-center justify-center">
                  <ToolIcon icon={tool.icon} size={20} bg="transparent" />
                </div>
                <span className="text-xs font-bold text-ink font-comic">{tool.name}</span>
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* ── Companion overlay ── */}
      <div
        className={`overlay absolute inset-0 bg-ink/20 backdrop-blur-sm z-30 ${showCompanion ? '' : 'hidden-overlay'}`}
        onClick={() => setShowCompanion(false)}
      />
      <div className={`companion-panel absolute bottom-0 left-0 right-0 bg-cream-bg rounded-t-[20px] border-t-2 border-ink z-40 max-h-[60%] flex flex-col ${showCompanion ? '' : 'hidden-panel'}`}>
        <div className="flex justify-center pt-3 pb-2 cursor-pointer" onClick={() => setShowCompanion(false)}>
          <div className="w-9 h-1.5 rounded-full bg-ink/25 border border-ink/20" />
        </div>
        <div className="px-5 pb-4 flex items-center gap-3">
          <div className="w-14 h-14 rounded-full bg-accent-pink border-2 border-ink flex items-center justify-center shadow-card">
            <PetAvatar size={36} />
          </div>
          <div>
            <h2 className="text-[18px] font-bold text-ink font-display">咪兔</h2>
            <p className="text-xs text-ink-muted font-comic">你的编程小助手</p>
          </div>
          <button onClick={() => setShowCompanion(false)} className="w-8 h-8 flex items-center justify-center rounded-button hover:bg-ink/5 transition-colors ml-auto">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#7a6b5f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
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
                <path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"/>
              </svg>
              <span className="text-[13px] font-bold text-ink uppercase tracking-wide font-display">心情</span>
            </div>
            <div className="flex items-center gap-3">
              <div className="flex-1 hand-progress">
                <div className="hand-progress-fill" style={{ width: '85%' }} />
              </div>
              <span className="text-xs font-bold text-ink font-comic">开心</span>
            </div>
          </div>

          <div className="bg-cream-card border-2 border-ink rounded-card p-4 shadow-card">
            <div className="flex items-center gap-2 mb-3">
              <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="#a8c4a0" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M22 12h-4l-3 9L9 3l-3 9H2"/>
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
            {[
              { key: 'poke', label: '戳一戳' },
              { key: 'feed', label: '喂食' },
              { key: 'praise', label: '表扬' },
            ].map((action) => (
              <button
                key={action.key}
                onClick={() => handlePetReaction(action.key)}
                className="flex-1 py-2.5 bg-cream-card border-2 border-ink rounded-button text-xs font-bold text-ink hover:bg-cream-warm transition-all active:scale-95 shadow-card font-comic"
              >
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
