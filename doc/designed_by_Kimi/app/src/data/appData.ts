/* ───────────────────────────────────────────────
   App-level static data: tools, history, initial message.
   Keep constants out of components so App.tsx stays focused
   on state and coordination.
   ─────────────────────────────────────────────── */

import type { AgentTurn } from '../lib/stream/types';

export interface UserMsg {
  id: string;
  role: 'user';
  content: string;
  time: string;
}

export interface AiMsg {
  id: string;
  role: 'ai';
  turn: AgentTurn;
  time: string;
}

export type Msg = UserMsg | AiMsg;

export const now = () =>
  new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false });

export const INITIAL: Msg[] = [
  {
    id: 'init',
    role: 'ai',
    time: '8:30',
    turn: {
      phase: 'done',
      answerDone: true,
      blocks: [],
      answer: '你好，我是咪兔。想让我搜代码、重构、调试还是跑测试？下方也可以点开工具箱挑一个。',
    },
  },
];

export interface HistoryEntry {
  date: string;
  time: string;
  title: string;
  desc: string;
  messages: string;
  tools: string;
}

export const HISTORY_ENTRIES: HistoryEntry[] = [
  { date: '今天', time: '8:33', title: '认证模块重构', desc: '搜索了认证相关代码，找到 AuthProvider.java 和 TokenManager.java，讨论了重构策略。', messages: '6 条消息', tools: '2 个工具' },
  { date: '昨天', time: '16:15', title: '数据库表结构评审', desc: '评审了新的用户表结构，建议为 email 和 created_at 字段添加索引，验证了迁移脚本。', messages: '12 条消息', tools: '3 个工具' },
  { date: '6月22日', time: '10:42', title: 'API 接口设计', desc: '设计了新计费模块的 REST 接口，讨论了分页策略和错误处理模式。', messages: '8 条消息', tools: '1 个工具' },
  { date: '5月30日', time: '14:20', title: '性能优化', desc: '分析了数据看板中的慢查询，建议添加缓存层并重构查询语句。', messages: '15 条消息', tools: '4 个工具' },
  { date: '5月28日', time: '9:00', title: '代码评审', desc: '评审了 PR #247，发现异步处理器中存在潜在的竞态条件并提出了修复建议。', messages: '20 条消息', tools: '2 个工具' },
];

export interface ToolItem {
  icon: string;
  name: string;
}

export const TOOLS: ToolItem[] = [
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
