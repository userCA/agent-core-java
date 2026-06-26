# ChatScreen — 页面设计覆盖

> 主聊天页面，核心交互场景。本文件中的规则覆盖 MASTER.md 中的同名规则。

---

## 页面定位

AI 对话的主战场。用户在此发送消息、查看流式推理过程、阅读 AI 回复。
信息密度高，需要平衡「手帐感」与「可读性」。

---

## 布局差异

| 区域 | 规则 |
|---|---|
| Header | `pt-12` (安全区)，`backdrop-blur-sm`，`border-b-2 border-border-passive` |
| 消息列表 | `px-4 py-4 space-y-4`，自定义滚动条 `.custom-scroll` |
| 输入栏 | `px-4 pb-8 pt-3`，`border-t-2 border-border-passive`，固定底部 |

---

## 消息气泡特殊规则

- **用户气泡**：右对齐，`max-width: 85%`，右下角尖角 (`radius: 16px 16px 4px 16px`)
- **AI 气泡**：左对齐（紧跟头像），`max-width: 90%`，左下角尖角 (`radius: 16px 16px 16px 4px`)
- **时间戳**：10px，`font-comic`，`text-ink-muted`，与气泡间距 `mt-1`

---

## 流式推理特殊规则

- 推理线程使用 `.trace` 组件，可折叠
- 推理节点按类型着色：think=`lavender` / tool=`sky` / skill=`sage` / running=白 / done=`sage` / error=`coral`
- 代码块背景为 `ink` 纯色（深色），与 cream 底色形成对比
- 打字光标：`w-0.5 h-4 bg-accent-pink`，blink 动画

---

## 状态指示

| 状态 | 视觉 |
|---|---|
| 在线 | `w-2 h-2 rounded-full bg-accent-sage border-2 border-ink` + "在线" |
| 处理中 | `w-2.5 h-2.5 rounded-full bg-accent-pink border-2 border-ink` + "思考中..." |
| 停止按钮 | `bg-accent-coral`，内含停止方块 SVG |

---

## 动画

- 用户消息入场：`animate-slide-up` (250ms)
- AI 消息入场：`animate-slide-down` (200ms)
- 打字指示器：三点交错 `typing` 动画 (1.2s)
- 宠物头像：`pet-bounce` 呼吸动画 (2s)
