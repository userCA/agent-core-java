import React, { useState } from 'react';
import CodeBlock from '../components/streaming/CodeBlock';
import SearchResultBlock from '../components/streaming/SearchResultBlock';
import GitDiffBlock from '../components/streaming/GitDiffBlock';
import FileOperationBlock from '../components/streaming/FileOperationBlock';
import ReasoningThread from '../components/streaming/ReasoningThread';
import TypingIndicator from '../components/streaming/TypingIndicator';
import { AIAvatar, PetAvatar, StatusIcon, ToolIcon } from '../components/Icons';
import type { LiveBlock } from '../lib/stream/types';

/* ───────────────────────────────────────────────
   PlayGround — 设计系统与组件可视化基准页
   作为持续开发中视觉一致性的参考来源。
   新增组件后在此追加 demo，只增不删。
   ─────────────────────────────────────────────── */

/* ── Section wrapper ── */
const Section: React.FC<{ id: string; title: string; children: React.ReactNode }> = ({ id, title, children }) => (
  <section id={id} className="mb-10 scroll-mt-20">
    <h2 className="text-[20px] font-bold text-ink font-display mb-4 pb-2 border-b-2 border-ink">{title}</h2>
    {children}
  </section>
);

/* ── Color swatch ── */
const Swatch: React.FC<{ token: string; hex: string; label: string }> = ({ token, hex, label }) => (
  <div className="flex flex-col items-center gap-1.5">
    <div className="w-14 h-14 rounded-card border-2 border-ink shadow-card" style={{ background: hex }} />
    <span className="text-[10px] font-bold text-ink font-comic">{token}</span>
    <span className="text-[9px] text-ink-muted font-mono">{hex}</span>
    <span className="text-[9px] text-ink-muted font-sans">{label}</span>
  </div>
);

/* ── Quick nav items ── */
const NAV_ITEMS = [
  { id: 'colors', label: '色彩' },
  { id: 'typography', label: '字体' },
  { id: 'buttons', label: '按钮' },
  { id: 'bubbles', label: '气泡' },
  { id: 'icons', label: '图标' },
  { id: 'streaming', label: '流式卡片' },
  { id: 'reasoning', label: '推理线程' },
  { id: 'typing', label: '打字' },
  { id: 'layout', label: '布局容器' },
  { id: 'interactive', label: '交互组件' },
  { id: 'forms', label: '表单' },
  { id: 'syntax', label: '语法高亮' },
  { id: 'animations', label: '动画' },
  { id: 'radius-shadow', label: '圆角阴影' },
];

/* ── Demo reasoning thread data ── */
const DEMO_BLOCKS: LiveBlock[] = [
  {
    id: 'b1', spec: { kind: 'thinking', content: '用户要求重构认证模块，需要先搜索现有代码结构，理解依赖关系后制定重构方案。' },
    status: 'complete', typed: '', toolStatus: 'complete', progress: 0, subStatus: [], fileStatus: [],
  },
  {
    id: 'b2', spec: { kind: 'tool', toolName: '代码搜索', icon: 'search', params: [{ key: 'query', value: 'AuthProvider' }], result: '找到 3 个匹配文件', resultCount: '3 results' },
    status: 'complete', typed: '', toolStatus: 'complete', progress: 0, subStatus: [], fileStatus: [],
  },
  {
    id: 'b3', spec: { kind: 'code', language: 'java', code: 'public class AuthResult {\n    private final String token;\n    public AuthResult(String token) {\n        this.token = token;\n    }\n}' },
    status: 'complete', typed: '', toolStatus: 'complete', progress: 0, subStatus: [], fileStatus: [],
  },
  {
    id: 'b4', spec: { kind: 'file', operations: [{ type: 'read', path: 'src/auth/AuthProvider.java', meta: '1.2 KB' }, { type: 'update', path: 'src/auth/AuthProvider.java', meta: 'Added null check' }, { type: 'create', path: 'src/auth/AuthResult.java', meta: '245 B' }] },
    status: 'complete', typed: '', toolStatus: 'complete', progress: 0, subStatus: [], fileStatus: ['complete', 'complete', 'complete'],
  },
];

const TOOL_ICONS = ['search', 'git-compare', 'play', 'wand-2', 'bug', 'file-text', 'file-plus', 'file-minus', 'file-edit', 'settings', 'book-open', 'message-square', 'shield-check', 'eye', 'heart', 'activity', 'lightbulb', 'sparkles'];

/* ── Demo history entry for journal-card ── */
const DEMO_HISTORY = { date: '2025-06-26', time: '14:30', title: '重构认证模块', desc: '搜索了 AuthProvider 相关代码，分析了依赖关系，完成了模块拆分和空值检查。', messages: '8 条消息', tools: '3 个工具' };

export default function Playground() {
  const [overlayDemo, setOverlayDemo] = useState(false);
  const [sheetDemo, setSheetDemo] = useState(false);
  const [screenDemo, setScreenDemo] = useState<'A' | 'B'>('A');

  return (
    <div className="min-h-screen" style={{ background: '#f9f1d8' }}>
      {/* Header */}
      <header className="px-8 pt-8 pb-4 border-b-2 border-ink" style={{ background: '#fdf6e3' }}>
        <div className="max-w-[960px] mx-auto flex items-center justify-between">
          <div>
            <h1 className="text-[28px] font-bold text-ink font-display">PlayGround</h1>
            <p className="text-[13px] text-ink-muted font-comic mt-1">设计系统与组件可视化基准 · 持续开发视觉参考</p>
          </div>
          <a href="#/" className="btn-light px-4 py-2 rounded-button text-xs font-comic font-bold cursor-pointer inline-block">返回主页</a>
        </div>
      </header>

      {/* Quick nav */}
      <nav className="sticky top-0 z-30 border-b-2 border-ink" style={{ background: '#fdf6e3' }}>
        <div className="max-w-[960px] mx-auto px-8 py-2 flex flex-wrap gap-1.5">
          {NAV_ITEMS.map((item) => (
            <a
              key={item.id}
              href={`#${item.id}`}
              onClick={(e) => {
                e.preventDefault();
                const element = document.getElementById(item.id);
                if (element) {
                  element.scrollIntoView({ behavior: 'smooth', block: 'start' });
                }
              }}
              className="px-2.5 py-1 rounded-pill text-[11px] font-comic font-bold text-ink-muted hover:text-ink hover:bg-cream-warm transition-colors cursor-pointer"
            >
              {item.label}
            </a>
          ))}
        </div>
      </nav>

      <main className="max-w-[960px] mx-auto px-8 py-8">

        {/* ── 1. 色彩体系 ── */}
        <Section id="colors" title="色彩体系">
          <h3 className="text-[14px] font-bold text-ink font-display mb-3">Peach 主色</h3>
          <div className="flex flex-wrap gap-4 mb-6">
            <Swatch token="peach-bg" hex="#f4b89a" label="主题基调" />
            <Swatch token="peach-light" hex="#f8c4a8" label="浅桃变体" />
            <Swatch token="peach-soft" hex="#f9d5c0" label="更浅桃底" />
            <Swatch token="peach-muted" hex="#e8a88a" label="加深变体" />
          </div>
          <h3 className="text-[14px] font-bold text-ink font-display mb-3">Cream 奶油底色</h3>
          <div className="flex flex-wrap gap-4 mb-6">
            <Swatch token="cream-bg" hex="#f9f1d8" label="页面底色" />
            <Swatch token="cream-card" hex="#fdf6e3" label="卡片背景" />
            <Swatch token="cream-surface" hex="#fffbf0" label="最亮表面" />
            <Swatch token="cream-warm" hex="#f5ecd0" label="hover 强调" />
          </div>
          <h3 className="text-[14px] font-bold text-ink font-display mb-3">Ink 墨水色</h3>
          <div className="flex flex-wrap gap-4 mb-6">
            <Swatch token="ink" hex="#3d2b1f" label="正文/边框" />
            <Swatch token="ink-muted" hex="#7a6b5f" label="次要文字" />
            <Swatch token="ink-faint" hex="rgba(61,43,31,0.4)" label="极淡墨水" />
            <Swatch token="ink-ghost" hex="rgba(61,43,31,0.15)" label="幽灵边框" />
          </div>
          <h3 className="text-[14px] font-bold text-ink font-display mb-3">Accent 强调色</h3>
          <div className="flex flex-wrap gap-4">
            <Swatch token="accent-pink" hex="#f4a8a8" label="可爱/心情" />
            <Swatch token="accent-sage" hex="#a8c4a0" label="成功/完成" />
            <Swatch token="accent-sky" hex="#a8c8e8" label="运行中/工具" />
            <Swatch token="accent-lavender" hex="#c8b8d8" label="思考/推理" />
            <Swatch token="accent-coral" hex="#e89080" label="错误/警告" />
          </div>
        </Section>

        {/* ── 2. 字体体系 ── */}
        <Section id="typography" title="字体体系">
          <div className="space-y-4">
            <div className="p-4 bg-cream-card border-2 border-ink rounded-card shadow-card">
              <span className="text-[10px] font-bold text-ink-muted uppercase tracking-wider font-sans">font-display</span>
              <p className="text-[24px] font-display text-ink mt-1">咪兔的编程日记 — Ma Shan Zheng</p>
              <p className="text-[12px] text-ink-muted font-sans mt-1">用于：标题、标签、日记感文字</p>
            </div>
            <div className="p-4 bg-cream-card border-2 border-ink rounded-card shadow-card">
              <span className="text-[10px] font-bold text-ink-muted uppercase tracking-wider font-sans">font-comic / font-hand</span>
              <p className="text-[18px] font-comic text-ink mt-1">你好，我是咪兔，想让我搜代码还是调试？</p>
              <p className="text-[12px] text-ink-muted font-sans mt-1">用于：消息气泡、工具名、按钮文字</p>
            </div>
            <div className="p-4 bg-cream-card border-2 border-ink rounded-card shadow-card">
              <span className="text-[10px] font-bold text-ink-muted uppercase tracking-wider font-sans">font-sans</span>
              <p className="text-[14px] font-sans text-ink mt-1">Inter · 系统 UI 文字、状态标签、时间戳</p>
              <p className="text-[12px] text-ink-muted font-sans mt-1">fallback: Noto Sans SC → PingFang SC → system-ui</p>
            </div>
            <div className="p-4 bg-cream-card border-2 border-ink rounded-card shadow-card">
              <span className="text-[10px] font-bold text-ink-muted uppercase tracking-wider font-sans">font-mono</span>
              <p className="text-[14px] font-mono text-ink mt-1">public class AuthProvider {'{'} ... {'}'}</p>
              <p className="text-[12px] text-ink-muted font-sans mt-1">用于：代码块、diff、搜索结果</p>
            </div>
          </div>
        </Section>

        {/* ── 3. 按钮系统 ── */}
        <Section id="buttons" title="按钮系统">
          <div className="flex flex-wrap items-center gap-4">
            <div className="flex flex-col items-center gap-2">
              <button className="btn-dark px-6 py-2.5 rounded-button text-sm font-comic font-bold cursor-pointer">发送消息</button>
              <span className="text-[10px] text-ink-muted font-sans">btn-dark 深色主按钮</span>
            </div>
            <div className="flex flex-col items-center gap-2">
              <button className="btn-light px-6 py-2.5 rounded-button text-sm font-comic font-bold cursor-pointer">演示全部</button>
              <span className="text-[10px] text-ink-muted font-sans">btn-light 浅色次按钮</span>
            </div>
            <div className="flex flex-col items-center gap-2">
              <button className="btn-dark px-6 py-2.5 rounded-button text-sm font-comic font-bold cursor-pointer opacity-45 cursor-not-allowed" disabled>禁用态</button>
              <span className="text-[10px] text-ink-muted font-sans">disabled 禁用</span>
            </div>
            <div className="flex flex-col items-center gap-2">
              <button className="w-11 h-11 btn-dark rounded-button flex items-center justify-center cursor-pointer" aria-label="示例">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fdf6e3" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <line x1="12" y1="19" x2="12" y2="5" /><polyline points="5 12 12 5 19 12" />
                </svg>
              </button>
              <span className="text-[10px] text-ink-muted font-sans">图标按钮 44x44</span>
            </div>
          </div>
        </Section>

        {/* ── 4. 消息气泡 ── */}
        <Section id="bubbles" title="消息气泡">
          <div className="space-y-4 max-w-[480px]">
            <div className="flex gap-3 justify-end">
              <div className="flex flex-col items-end">
                <div className="msg-user inline-block px-4 py-3 max-w-[85%] text-[15px] leading-relaxed">帮我重构认证模块</div>
                <span className="text-[10px] text-ink-muted mt-1 mr-1 font-comic">10:30</span>
              </div>
            </div>
            <div className="flex gap-3">
              <AIAvatar size={32} />
              <div className="flex flex-col">
                <div className="msg-ai inline-block px-4 py-3 max-w-[90%] text-[15px] leading-relaxed">好的，我来搜索认证相关代码，分析依赖关系后给你重构方案。</div>
                <span className="text-[10px] text-ink-muted mt-1 ml-1 font-comic">10:31</span>
              </div>
            </div>
          </div>
        </Section>

        {/* ── 5. 图标组件 ── */}
        <Section id="icons" title="图标组件">
          <h3 className="text-[14px] font-bold text-ink font-display mb-3">头像</h3>
          <div className="flex flex-wrap items-center gap-6 mb-6">
            <div className="flex flex-col items-center gap-1.5">
              <AIAvatar size={32} />
              <span className="text-[10px] text-ink-muted font-comic">AIAvatar 32</span>
            </div>
            <div className="flex flex-col items-center gap-1.5">
              <AIAvatar size={48} />
              <span className="text-[10px] text-ink-muted font-comic">AIAvatar 48</span>
            </div>
            <div className="flex flex-col items-center gap-1.5">
              <PetAvatar size={36} />
              <span className="text-[10px] text-ink-muted font-comic">PetAvatar 36</span>
            </div>
            <div className="flex flex-col items-center gap-1.5">
              <PetAvatar size={56} />
              <span className="text-[10px] text-ink-muted font-comic">PetAvatar 56</span>
            </div>
          </div>

          <h3 className="text-[14px] font-bold text-ink font-display mb-3">StatusIcon 状态</h3>
          <div className="flex flex-wrap items-center gap-6 mb-6">
            {(['pending', 'running', 'complete', 'error'] as const).map((s) => (
              <div key={s} className="flex flex-col items-center gap-1.5">
                <StatusIcon status={s} size={24} />
                <span className="text-[10px] text-ink-muted font-comic">{s}</span>
              </div>
            ))}
          </div>

          <h3 className="text-[14px] font-bold text-ink font-display mb-3">ToolIcon 工具图标</h3>
          <div className="flex flex-wrap gap-3">
            {TOOL_ICONS.map((icon) => (
              <div key={icon} className="flex flex-col items-center gap-1">
                <ToolIcon icon={icon} size={36} />
                <span className="text-[9px] text-ink-muted font-mono">{icon}</span>
              </div>
            ))}
          </div>
        </Section>

        {/* ── 6. 流式卡片 ── */}
        <Section id="streaming" title="流式卡片">
          <div className="space-y-6">
            <div>
              <h3 className="text-[14px] font-bold text-ink font-display mb-3">streaming-card 基础容器</h3>
              <div className="streaming-card">
                <p className="text-[13px] text-ink font-sans">这是一个 <code className="font-mono text-accent-sky">.streaming-card</code> 基础样式，所有流式块卡片的公共容器。背景 cream-card、2px ink 边框、16px 圆角、shadow-card。</p>
              </div>
            </div>
            <div>
              <h3 className="text-[14px] font-bold text-ink font-display mb-3">CodeBlock 代码块</h3>
              <div className="max-w-[480px]">
                <CodeBlock language="java" bare />
              </div>
            </div>
            <div>
              <h3 className="text-[14px] font-bold text-ink font-display mb-3">SearchResultBlock 搜索结果</h3>
              <div className="max-w-[480px]">
                <SearchResultBlock />
              </div>
            </div>
            <div>
              <h3 className="text-[14px] font-bold text-ink font-display mb-3">GitDiffBlock 代码对比</h3>
              <div className="max-w-[480px]">
                <GitDiffBlock />
              </div>
            </div>
            <div>
              <h3 className="text-[14px] font-bold text-ink font-display mb-3">FileOperationBlock 文件操作</h3>
              <div className="max-w-[480px]">
                <FileOperationBlock />
              </div>
            </div>
          </div>
        </Section>

        {/* ── 7. 推理线程 ── */}
        <Section id="reasoning" title="推理线程">
          <div className="space-y-6">
            <div>
              <h3 className="text-[14px] font-bold text-ink font-display mb-3">ReasoningThread React 组件</h3>
              <div className="max-w-[480px]">
                <ReasoningThread blocks={DEMO_BLOCKS} active={false} />
              </div>
            </div>
            <div>
              <h3 className="text-[14px] font-bold text-ink font-display mb-3">Trace CSS 推理时间线</h3>
              <p className="text-[12px] text-ink-muted font-sans mb-3">index.css 中的 .trace 系列样式，用于折叠式推理时间线</p>
              <div className="max-w-[480px]">
                <div className="trace">
                  <button className="trace-head">
                    <span className="thead-ic"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="m12 3-1.9 5.8a2 2 0 0 1-1.3 1.3L3 12l5.8 1.9a2 2 0 0 1 1.3 1.3L12 21l1.9-5.8a2 2 0 0 1 1.3-1.3L21 12l-5.8-1.9a2 2 0 0 1-1.3-1.3Z" /></svg></span>
                    <span className="thead-label font-comic">推理过程 (3 步)</span>
                    <svg className="t-chev" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="9 18 15 12 9 6" /></svg>
                  </button>
                  <div className="trace-rail">
                    <div className="t-step">
                      <div className="t-node think" />
                      <div className="t-head">
                        <span className="t-kind font-comic">思考</span>
                        <span className="t-sub">分析需求，确定重构方案</span>
                      </div>
                    </div>
                    <div className="t-step">
                      <div className="t-node tool" />
                      <div className="t-head">
                        <span className="t-kind font-comic">工具</span>
                        <span className="t-sub">搜索 AuthProvider 相关代码</span>
                      </div>
                    </div>
                    <div className="t-step is-last">
                      <div className="t-node done" />
                      <div className="t-head">
                        <span className="t-kind font-comic">完成</span>
                        <span className="t-sub">输出重构后的代码</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </Section>

        {/* ── 8. 打字指示器 ── */}
        <Section id="typing" title="打字指示器">
          <TypingIndicator />
        </Section>

        {/* ── 9. 布局容器 ── */}
        <Section id="layout" title="布局容器">
          <div className="space-y-6">
            <div>
              <h3 className="text-[14px] font-bold text-ink font-display mb-3">phone-frame 手机容器</h3>
              <p className="text-[12px] text-ink-muted font-sans mb-3">App 主页使用 390×844 手机框架渲染，响应式 &lt;420px 全屏</p>
              <div className="flex justify-center">
                <div className="phone-frame" style={{ height: '400px', width: '200px' }}>
                  <div className="flex-1 bg-cream-bg flex items-center justify-center">
                    <div className="text-center">
                      <PetAvatar size={48} />
                      <p className="text-[12px] text-ink-muted font-comic mt-2">phone-frame 预览</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div>
              <h3 className="text-[14px] font-bold text-ink font-display mb-3">journal-card 日记卡片</h3>
              <p className="text-[12px] text-ink-muted font-sans mb-3">HistoryScreen 中的核心卡片组件，hover/active 缩放效果</p>
              <div className="max-w-[400px] space-y-3">
                <button className="journal-card w-full text-left bg-cream-card border-2 border-ink rounded-card p-4 shadow-card cursor-pointer">
                  <div className="flex items-start justify-between mb-2">
                    <span className="text-[12px] font-bold text-ink-muted uppercase tracking-wide font-display">{DEMO_HISTORY.date}</span>
                    <span className="text-[11px] text-ink-muted font-comic">{DEMO_HISTORY.time}</span>
                  </div>
                  <h3 className="text-[15px] font-bold text-ink mb-1 font-display">{DEMO_HISTORY.title}</h3>
                  <p className="text-xs text-ink-muted leading-relaxed line-clamp-2 font-comic">{DEMO_HISTORY.desc}</p>
                  <div className="flex items-center gap-2 mt-3">
                    <span className="px-2 py-0.5 bg-cream-warm rounded-pill text-[10px] text-ink-muted border border-ink font-comic">{DEMO_HISTORY.messages}</span>
                    <span className="px-2 py-0.5 bg-cream-warm rounded-pill text-[10px] text-ink-muted border border-ink font-comic">{DEMO_HISTORY.tools}</span>
                  </div>
                </button>
              </div>
            </div>
            <div>
              <h3 className="text-[14px] font-bold text-ink font-display mb-3">dashed-divider 虚线分隔</h3>
              <div className="max-w-[400px] bg-cream-card border-2 border-ink rounded-card p-4 shadow-card">
                <p className="text-[13px] text-ink font-comic">上方内容区域</p>
                <div className="dashed-divider my-3" />
                <p className="text-[13px] text-ink font-comic">下方内容区域</p>
              </div>
            </div>
          </div>
        </Section>

        {/* ── 10. 交互组件 ── */}
        <Section id="interactive" title="交互组件">
          <div className="space-y-6">
            <div>
              <h3 className="text-[14px] font-bold text-ink font-display mb-3">screen 页面转场</h3>
              <p className="text-[12px] text-ink-muted font-sans mb-3">点击下方按钮切换 screen-active / screen-left / screen-right</p>
              <div className="flex gap-2 mb-3">
                <button onClick={() => setScreenDemo('A')} className={`px-4 py-2 rounded-button text-xs font-comic font-bold cursor-pointer ${screenDemo === 'A' ? 'btn-dark' : 'btn-light'}`}>Screen A</button>
                <button onClick={() => setScreenDemo('B')} className={`px-4 py-2 rounded-button text-xs font-comic font-bold cursor-pointer ${screenDemo === 'B' ? 'btn-dark' : 'btn-light'}`}>Screen B</button>
              </div>
              <div className="relative w-full h-[160px] border-2 border-ink rounded-card overflow-hidden bg-cream-surface">
                <div className={`screen bg-accent-sky/30 flex items-center justify-center ${screenDemo === 'A' ? 'screen-active' : 'screen-left'}`}>
                  <span className="text-[16px] font-bold text-ink font-display">Screen A</span>
                </div>
                <div className={`screen bg-accent-lavender/30 flex items-center justify-center ${screenDemo === 'B' ? 'screen-active' : 'screen-right'}`}>
                  <span className="text-[16px] font-bold text-ink font-display">Screen B</span>
                </div>
              </div>
            </div>
            <div>
              <h3 className="text-[14px] font-bold text-ink font-display mb-3">overlay + bottom-sheet 遮罩与底部抽屉</h3>
              <p className="text-[12px] text-ink-muted font-sans mb-3">点击按钮体验 overlay 淡入 + bottom-sheet 滑入动画</p>
              <div className="relative w-full h-[200px] border-2 border-ink rounded-card overflow-hidden bg-cream-surface">
                <div className="absolute inset-0 flex items-center justify-center">
                  <button onClick={() => { setOverlayDemo(true); setSheetDemo(true); }} className="btn-dark px-4 py-2 rounded-button text-xs font-comic font-bold cursor-pointer">打开 Sheet</button>
                </div>
                <div className={`overlay absolute inset-0 bg-ink/20 backdrop-blur-sm z-30 ${overlayDemo ? '' : 'hidden-overlay'}`} onClick={() => { setOverlayDemo(false); setSheetDemo(false); }} />
                <div className={`bottom-sheet absolute bottom-0 left-0 right-0 bg-cream-bg rounded-t-[20px] z-40 flex flex-col ${sheetDemo ? '' : 'hidden-sheet'}`}>
                  <button type="button" className="flex justify-center pt-3 pb-2 cursor-pointer w-full" onClick={() => { setOverlayDemo(false); setSheetDemo(false); }}>
                    <div className="w-9 h-1.5 rounded-full bg-ink/25 border border-ink/20" />
                  </button>
                  <div className="px-5 pb-4">
                    <h4 className="text-[15px] font-bold text-ink font-display font-comic">Bottom Sheet 内容</h4>
                    <p className="text-[12px] text-ink-muted font-comic mt-1">下拉关闭或点击遮罩层</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </Section>

        {/* ── 11. 表单元素 ── */}
        <Section id="forms" title="表单元素">
          <div className="space-y-4 max-w-[400px]">
            <div>
              <label className="text-[12px] font-bold text-ink font-display mb-1.5 block">输入框</label>
              <input type="text" placeholder="请输入内容..." className="w-full px-4 py-2.5 bg-cream-card border-2 border-ink rounded-button text-[14px] text-ink placeholder:text-ink-muted/70 input-focus font-comic" />
            </div>
            <div>
              <label className="text-[12px] font-bold text-ink font-display mb-1.5 block">文本域</label>
              <textarea rows={3} placeholder="说点什么..." className="w-full px-4 py-2.5 bg-cream-card border-2 border-ink rounded-button text-[14px] text-ink placeholder:text-ink-muted/70 resize-none input-focus leading-relaxed font-comic" />
            </div>
            <div>
              <label className="text-[12px] font-bold text-ink font-display mb-1.5 block">进度条</label>
              <div className="hand-progress"><div className="hand-progress-fill" style={{ width: '65%' }} /></div>
            </div>
            <div>
              <label className="text-[12px] font-bold text-ink font-display mb-1.5 block">Focus 态演示（点击输入框查看）</label>
              <input type="text" defaultValue="点击这里查看 focus ring" className="w-full px-4 py-2.5 bg-cream-card border-2 border-ink rounded-button text-[14px] text-ink input-focus font-comic" />
            </div>
          </div>
        </Section>

        {/* ── 12. 代码语法高亮色 ── */}
        <Section id="syntax" title="代码语法高亮色">
          <p className="text-[12px] text-ink-muted font-sans mb-3">index.css 中定义的 7 个 token-* 语法着色类</p>
          <div className="bg-[#3d2b1f] border-2 border-ink rounded-[12px] p-5 shadow-card max-w-[520px]">
            <pre className="text-[13px] font-mono leading-relaxed">
              <span className="token-keyword">public</span> <span className="token-keyword">class</span> <span className="token-function">AuthProvider</span> {'{\n'}
              {'  '}<span className="token-keyword">private</span> <span className="token-keyword">final</span> String token<span className="token-punctuation">;</span>{'\n'}
              {'  '}<span className="token-comment">{'// 初始化认证令牌'}</span>{'\n'}
              {'  '}<span className="token-keyword">public</span> <span className="token-function">AuthProvider</span><span className="token-punctuation">(</span>String token<span className="token-punctuation">)</span> {'{\n'}
              {'    '}<span className="token-keyword">this</span><span className="token-punctuation">.</span>token <span className="token-operator">=</span> token<span className="token-punctuation">;</span>{'\n'}
              {'    '}<span className="token-keyword">this</span><span className="token-punctuation">.</span>retryCount <span className="token-operator">=</span> <span className="token-number">3</span><span className="token-punctuation">;</span>{'\n'}
              {'  '}<span className="token-punctuation">{'}'}</span>{'\n'}
              {'  '}<span className="token-keyword">public</span> String <span className="token-function">getToken</span><span className="token-punctuation">()</span> {'{\n'}
              {'    '}<span className="token-keyword">return</span> <span className="token-string">"Bearer "</span> <span className="token-operator">+</span> token<span className="token-punctuation">;</span>{'\n'}
              {'  '}<span className="token-punctuation">{'}'}</span>{'\n'}
              {'}'}
            </pre>
          </div>
          <div className="flex flex-wrap gap-3 mt-4">
            {[
              { cls: 'token-keyword', label: 'keyword', color: '#f4a8a8' },
              { cls: 'token-string', label: 'string', color: '#a8c4a0' },
              { cls: 'token-comment', label: 'comment', color: '#7a6b5f' },
              { cls: 'token-function', label: 'function', color: '#a8c8e8' },
              { cls: 'token-number', label: 'number', color: '#f9d5c0' },
              { cls: 'token-operator', label: 'operator', color: '#fdf6e3' },
              { cls: 'token-punctuation', label: 'punctuation', color: '#7a6b5f' },
            ].map((t) => (
              <div key={t.cls} className="flex items-center gap-1.5">
                <div className="w-4 h-4 rounded border border-ink/30" style={{ background: t.color }} />
                <span className="text-[10px] font-mono text-ink-muted">{t.cls}</span>
              </div>
            ))}
          </div>
        </Section>

        {/* ── 13. 动画展示 ── */}
        <Section id="animations" title="动画展示">
          <h3 className="text-[14px] font-bold text-ink font-display mb-3">入场动画</h3>
          <div className="flex flex-wrap gap-4 mb-6">
            {[
              { name: 'slide-up', cls: 'animate-slide-up' },
              { name: 'slide-down', cls: 'animate-slide-down' },
              { name: 'fade-in', cls: 'animate-fade-in' },
              { name: 'pop-in', cls: 'animate-pop-in' },
            ].map((anim) => (
              <div key={anim.name} className="flex flex-col items-center gap-2">
                <div className={`w-16 h-16 bg-cream-card border-2 border-ink rounded-card shadow-card flex items-center justify-center ${anim.cls}`}>
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#3d2b1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                    <path d="m12 3-1.9 5.8a2 2 0 0 1-1.3 1.3L3 12l5.8 1.9a2 2 0 0 1 1.3 1.3L12 21l1.9-5.8a2 2 0 0 1 1.3-1.3L21 12l-5.8-1.9a2 2 0 0 1-1.3-1.3Z" />
                  </svg>
                </div>
                <span className="text-[10px] font-mono text-ink-muted">{anim.name}</span>
              </div>
            ))}
          </div>

          <h3 className="text-[14px] font-bold text-ink font-display mb-3">循环动画</h3>
          <div className="flex flex-wrap gap-4 mb-6">
            <div className="flex flex-col items-center gap-2">
              <div className="w-16 h-16 bg-cream-card border-2 border-ink rounded-card shadow-card flex items-center justify-center">
                <div className="pet-bounce">
                  <PetAvatar size={28} />
                </div>
              </div>
              <span className="text-[10px] font-mono text-ink-muted">gentle-bounce</span>
            </div>
            <div className="flex flex-col items-center gap-2">
              <div className="w-16 h-16 bg-cream-card border-2 border-ink rounded-card shadow-card flex items-center justify-center">
                <div className="flex gap-1">
                  <span className="w-2 h-2 rounded-full bg-ink-muted typing-dot" />
                  <span className="w-2 h-2 rounded-full bg-ink-muted typing-dot" />
                  <span className="w-2 h-2 rounded-full bg-ink-muted typing-dot" />
                </div>
              </div>
              <span className="text-[10px] font-mono text-ink-muted">typing</span>
            </div>
            <div className="flex flex-col items-center gap-2">
              <div className="w-16 h-16 bg-cream-card border-2 border-ink rounded-card shadow-card flex items-center justify-center">
                <div className="t-spin" style={{ width: 20, height: 20, display: 'block' }} />
              </div>
              <span className="text-[10px] font-mono text-ink-muted">spin</span>
            </div>
            <div className="flex flex-col items-center gap-2">
              <div className="w-16 h-16 bg-cream-card border-2 border-ink rounded-card shadow-card flex items-center justify-center">
                <span className="inline-block w-0.5 h-5 bg-accent-pink animate-blink" />
              </div>
              <span className="text-[10px] font-mono text-ink-muted">blink</span>
            </div>
          </div>

          <h3 className="text-[14px] font-bold text-ink font-display mb-3">特效动画</h3>
          <div className="flex flex-wrap gap-4">
            <div className="flex flex-col items-center gap-2">
              <div className="w-16 h-16 border-2 border-ink rounded-card shadow-card flex items-center justify-center overflow-hidden" style={{ background: 'linear-gradient(90deg, #f5ecd0 25%, #fdf6e3 50%, #f5ecd0 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.5s infinite' }} />
              <span className="text-[10px] font-mono text-ink-muted">shimmer</span>
            </div>
            <div className="flex flex-col items-center gap-2">
              <div className="w-16 h-16 bg-cream-card border-2 border-ink rounded-card shadow-card flex items-center justify-center relative">
                <div className="w-6 h-6 rounded-full bg-accent-pink" />
                <div className="absolute inset-0 flex items-center justify-center">
                  <div className="w-6 h-6 rounded-full border-2 border-accent-pink animate-pulse-ring" />
                </div>
              </div>
              <span className="text-[10px] font-mono text-ink-muted">pulse-ring</span>
            </div>
            <div className="flex flex-col items-center gap-2">
              <div className="w-16 h-16 border-2 border-ink rounded-card shadow-card overflow-hidden flex items-center justify-center" style={{ background: 'repeating-linear-gradient(45deg, #f5ecd0, #f5ecd0 5px, #fdf6e3 5px, #fdf6e3 10px)', animation: 'skill-stripe 0.5s linear infinite' }} />
              <span className="text-[10px] font-mono text-ink-muted">skill-stripe</span>
            </div>
          </div>
        </Section>

        {/* ── 14. 圆角与阴影 ── */}
        <Section id="radius-shadow" title="圆角与阴影参考">
          <div className="flex flex-wrap gap-6">
            <div className="flex flex-col items-center gap-2">
              <div className="w-20 h-12 bg-cream-card border-2 border-ink" style={{ borderRadius: '10px', boxShadow: '3px 3px 0 #3d2b1f' }} />
              <span className="text-[10px] font-mono text-ink-muted">rounded-button (10px)</span>
            </div>
            <div className="flex flex-col items-center gap-2">
              <div className="w-20 h-12 bg-cream-card border-2 border-ink" style={{ borderRadius: '16px', boxShadow: '3px 3px 0 #3d2b1f' }} />
              <span className="text-[10px] font-mono text-ink-muted">rounded-card (16px)</span>
            </div>
            <div className="flex flex-col items-center gap-2">
              <div className="w-20 h-12 bg-cream-card border-2 border-ink rounded-pill" style={{ boxShadow: '3px 3px 0 #3d2b1f' }} />
              <span className="text-[10px] font-mono text-ink-muted">rounded-pill (9999px)</span>
            </div>
            <div className="flex flex-col items-center gap-2">
              <div className="w-20 h-12 bg-cream-card border-2 border-ink rounded-card" style={{ boxShadow: '1px 1px 0 rgba(61,43,31,0.2)' }} />
              <span className="text-[10px] font-mono text-ink-muted">shadow-subtle</span>
            </div>
            <div className="flex flex-col items-center gap-2">
              <div className="w-20 h-12 bg-cream-card border-2 border-ink rounded-card" style={{ boxShadow: '3px 3px 0 #3d2b1f' }} />
              <span className="text-[10px] font-mono text-ink-muted">shadow-card</span>
            </div>
            <div className="flex flex-col items-center gap-2">
              <div className="w-20 h-12 bg-cream-card border-2 border-ink rounded-card" style={{ boxShadow: '4px 4px 0 #3d2b1f' }} />
              <span className="text-[10px] font-mono text-ink-muted">shadow-float</span>
            </div>
          </div>
        </Section>

      </main>

      {/* Footer */}
      <footer className="px-8 py-6 border-t-2 border-ink text-center" style={{ background: '#fdf6e3' }}>
        <p className="text-[12px] text-ink-muted font-comic">PlayGround · 新增组件后在此追加 demo · 只增不删</p>
      </footer>
    </div>
  );
}
