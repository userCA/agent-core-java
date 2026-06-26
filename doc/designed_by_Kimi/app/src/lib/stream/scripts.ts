import type { Script } from './types';

/* ───────────────────────────────────────────────
   buildScript — map a user message to a scripted
   assistant turn (思考 → 工具/技能 → 答案).
   纯前端模拟；日后可由真实 SSE 事件替代。
   ─────────────────────────────────────────────── */

const SEARCH_RESULTS = [
  { path: 'src/auth/', filename: 'AuthProvider.java', preview: 'public class AuthProvider { private TokenManager tokenManager;', highlights: ['AuthProvider'] },
  { path: 'src/auth/', filename: 'TokenManager.java', preview: 'public class TokenManager { public String issueToken(...) {', highlights: ['TokenManager'] },
  { path: 'src/utils/', filename: 'JwtUtil.java', preview: 'public class JwtUtil { private static final String SECRET =', highlights: ['JwtUtil'] },
];

const AUTH_CODE = `public class AuthProvider {
    private final TokenManager tokenManager;

    public AuthResult authenticate(Credentials creds) {
        if (creds == null || !creds.isValid()) {
            throw new AuthException("无效的凭证");
        }
        String token = tokenManager.issueToken(creds);
        auditLog.record("AUTH", creds.getUser());
        return new AuthResult(token);
    }
}`;

const DIFF_LINES = [
  { type: 'neutral' as const, oldNum: '...', newNum: '...', content: '...' },
  { type: 'del' as const, oldNum: '145', newNum: '', content: '        String token = tokenManager.getToken(creds);' },
  { type: 'del' as const, oldNum: '146', newNum: '', content: '        return token;' },
  { type: 'add' as const, oldNum: '', newNum: '145', content: '        if (creds == null || !creds.isValid()) {' },
  { type: 'add' as const, oldNum: '', newNum: '146', content: '            throw new AuthException("无效的凭证");' },
  { type: 'add' as const, oldNum: '', newNum: '147', content: '        }' },
  { type: 'add' as const, oldNum: '', newNum: '148', content: '        String token = tokenManager.issueToken(creds);' },
  { type: 'add' as const, oldNum: '', newNum: '149', content: '        auditLog.record("AUTH", creds.getUser());' },
  { type: 'neutral' as const, oldNum: '...', newNum: '...', content: '...' },
];

const has = (t: string, ...keys: string[]) => keys.some((k) => t.includes(k));

export function buildScript(text: string): Script {
  const t = (text || '').toLowerCase();

  if (has(t, 'search', '搜索', '查找', 'auth', '认证')) {
    return {
      steps: [
        { kind: 'thinking', content: '用户想定位认证相关代码。先在 src 下检索 token / login 关键字，再读候选文件确认重构边界。' },
        {
          kind: 'tool', toolName: '代码搜索', icon: 'search', resultCount: '3 个结果',
          params: [{ key: '关键词', value: '认证模块' }, { key: '语言', value: 'java' }],
          result: '找到 3 个文件：AuthProvider.java、TokenManager.java、JwtUtil.java',
        },
        { kind: 'search', title: '代码搜索', count: '3 个结果', results: SEARCH_RESULTS },
      ],
      answer: '在代码库里找到 3 个相关文件，需要我展开某个文件看具体内容吗？',
    };
  }

  if (has(t, 'refactor', '重构')) {
    return {
      steps: [
        { kind: 'thinking', content: '目标是同时提升可读性与可测试性。先看现有实现的耦合点：校验与取 token 混在一起、缺少判空。再决定抽取边界。' },
        {
          kind: 'tool', toolName: '代码分析', icon: 'wand-2', resultCount: '3 个问题',
          params: [{ key: '目标', value: 'AuthProvider.java' }],
          result: '发现问题：第 147 行 NPE、缺少参数校验、异常处理不完善。',
        },
        {
          kind: 'skill', skillName: '全栈重构', description: '多步代码分析与重构',
          subTools: [{ name: '搜索代码库' }, { name: '分析依赖关系' }, { name: '生成重构补丁' }],
        },
        { kind: 'code', language: 'java', code: AUTH_CODE },
        { kind: 'file', operations: [
          { type: 'read', path: 'src/auth/AuthProvider.java', meta: '1.2 KB' },
          { type: 'update', path: 'src/auth/AuthProvider.java', meta: '新增空值检查' },
          { type: 'create', path: 'src/auth/AuthResult.java', meta: '245 B' },
        ] },
      ],
      answer: '重构完成：抽出了校验逻辑、补上了空安全判断、并新增了审计日志。共修改 1 个文件、新增 1 个文件。',
    };
  }

  if (has(t, 'debug', '调试', 'error', 'bug', '报错')) {
    return {
      steps: [
        { kind: 'thinking', content: '从堆栈看问题出在 token 使用环节。先搜可疑调用，再读上下文确认是否缺少判空。' },
        {
          kind: 'tool', toolName: '代码搜索', icon: 'search', resultCount: '1 处',
          params: [{ key: '模式', value: 'token.' }, { key: '文件', value: 'AuthProvider.java' }],
          result: '第 147 行：return token.getUserId(); — 使用前未判空。',
        },
      ],
      answer: '找到问题：AuthProvider.java 第 147 行在使用前没有判空。加一个守卫判断即可修复。',
    };
  }

  if (has(t, 'test', '测试')) {
    return {
      steps: [
        { kind: 'thinking', content: '改动集中在 auth 模块，先只跑相关单元测试，节省时间。' },
        {
          kind: 'tool', toolName: '运行测试', icon: 'play', resultCount: '24 通过',
          params: [{ key: '命令', value: 'mvn test -Dtest=Auth*' }],
          result: '24 个测试全部通过，用时 3.2s，覆盖率 87%。',
        },
      ],
      answer: '24 个测试全部通过，用时 3.2s，覆盖率 87%。',
    };
  }

  if (has(t, 'git', 'diff', '对比', '改动')) {
    return {
      steps: [
        { kind: 'thinking', content: '看看最近一次改动落在哪些文件、增删了多少行。' },
        { kind: 'diff', filename: 'src/auth/AuthProvider.java', stats: { add: 42, del: 8 }, lines: DIFF_LINES },
      ],
      answer: '最新改动：新增 42 行、删除 8 行，主要集中在 auth 模块。',
    };
  }

  if (has(t, 'doc', '文档')) {
    return {
      steps: [
        { kind: 'thinking', content: '按公共 API、配置项、用法示例三部分组织文档结构。' },
        {
          kind: 'skill', skillName: '文档生成', description: '为模块生成 Markdown 文档',
          subTools: [{ name: '提取公共接口' }, { name: '生成示例' }, { name: '汇总成文' }],
        },
      ],
      answer: '我为 auth 模块生成了文档，涵盖公共 API、配置项和用法示例。还要补充什么吗？',
    };
  }

  if (has(t, 'review', '评审')) {
    return {
      steps: [
        { kind: 'thinking', content: '重点看输入校验与边界条件，再通读 diff 找潜在问题。' },
        {
          kind: 'tool', toolName: '代码评审', icon: 'eye', resultCount: '2 处',
          params: [{ key: '范围', value: '本次提交' }],
          result: '第 89 行缺少输入校验；第 203 行存在魔法数字。',
        },
      ],
      answer: '评审完成，发现 2 个小问题：第 89 行缺少输入校验；第 203 行的魔法数字建议提取为常量。整体结构良好。',
    };
  }

  // default
  return {
    steps: [
      { kind: 'thinking', content: '先确认你想达成的目标，再决定要不要调用工具来帮你。' },
    ],
    answer: '明白了，告诉我更多细节，我来帮你～',
  };
}

/* A showcase script that exercises every streaming block. Reached via the
   tool drawer's 演示 entry; it runs through the real engine so it animates. */
export const DEMO_SCRIPT: Script = {
  steps: [
    { kind: 'thinking', content: '这是一段思考过程演示：先分析问题，再决定调用哪些工具与技能。' },
    {
      kind: 'tool',
      toolName: '代码搜索',
      icon: 'search',
      resultCount: '3 个结果',
      params: [{ key: '关键词', value: 'auth' }],
      result: '找到 3 个文件。',
    },
    {
      kind: 'skill',
      skillName: '全栈重构',
      description: '多步代码分析与重构',
      subTools: [{ name: '搜索代码库' }, { name: '分析依赖' }, { name: '生成补丁' }],
    },
    {
      kind: 'code',
      language: 'java',
      code: 'public AuthResult authenticate(Credentials creds) {\n    if (creds == null) throw new AuthException("无效");\n    return new AuthResult(tokenManager.issueToken(creds));\n}',
    },
    {
      kind: 'search',
      title: '代码搜索',
      count: '3 个结果',
      results: [
        { path: 'src/auth/', filename: 'AuthProvider.java', preview: 'public class AuthProvider {', highlights: ['AuthProvider'] },
        { path: 'src/auth/', filename: 'TokenManager.java', preview: 'public class TokenManager {', highlights: ['TokenManager'] },
      ],
    },
    {
      kind: 'diff',
      filename: 'src/auth/AuthProvider.java',
      stats: { add: 6, del: 2 },
      lines: [
        { type: 'del', oldNum: '12', newNum: '', content: '  String t = mgr.getToken(c);' },
        { type: 'add', oldNum: '', newNum: '12', content: '  if (c == null) throw new AuthException();' },
        { type: 'add', oldNum: '', newNum: '13', content: '  String t = mgr.issueToken(c);' },
      ],
    },
    {
      kind: 'file',
      operations: [
        { type: 'update', path: 'src/auth/AuthProvider.java', meta: '新增判空' },
        { type: 'create', path: 'src/auth/AuthResult.java', meta: '245 B' },
      ],
    },
  ],
  answer: '以上演示覆盖了思考、工具、技能、代码、搜索、对比、文件操作七种流式卡片。',
};
