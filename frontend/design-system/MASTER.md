# Agent Core Chat — 设计系统 Master

> **调性定位**：温暖的「手帐风 AI 编程伙伴」
> 将冰冷的编程工具包裹在手绘日记般的温暖外壳中，让 AI 助手「咪兔」像一个可爱的编程宠物陪伴开发者。

---

## 1. 技术栈

| 层面 | 技术 | 版本 |
|---|---|---|
| 框架 | React + TypeScript | 19.2 / TS 5.9 |
| 构建 | Vite | 7.2 |
| 样式 | Tailwind CSS | 3.4 |
| 组件库 | shadcn/ui (new-york style) | — |
| 图标 | Lucide React + 自定义 SVG | 0.562 |
| 路由 | React Router | 7.6 |
| 图表 | Recharts | 2.15 |
| 表单 | React Hook Form + Zod | 7.70 / 4.3 |
| 动画 | tailwindcss-animate + 自定义 keyframes | — |

---

## 2. 色彩体系

### 2.1 核心色

| 角色 | Tailwind Token | 色值 | 用途 |
|---|---|---|---|
| **主色 Peach** | `peach-bg` | `#f4b89a` | 外部背景、主题基调色 |
| **Peach Light** | `peach-light` | `#f8c4a8` | 浅桃变体 |
| **Peach Soft** | `peach-soft` | `#f9d5c0` | 更浅的桃色底 |
| **Peach Muted** | `peach-muted` | `#e8a88a` | 桃色加深变体 |

### 2.2 奶油底色 (Cream)

| Token | 色值 | 用途 |
|---|---|---|
| `cream-bg` | `#f9f1d8` | 手机框架/页面底色 |
| `cream-card` | `#fdf6e3` | 卡片背景、AI 消息气泡 |
| `cream-surface` | `#fffbf0` | 最亮的表面色 |
| `cream-warm` | `#f5ecd0` | hover 态、温暖强调 |

### 2.3 墨水色 (Ink) — 灵魂色

| Token | 色值 | 用途 |
|---|---|---|
| `ink` (DEFAULT) | `#3d2b1f` | 正文、边框、描边、所有线条 |
| `ink-muted` | `#7a6b5f` | 次要文字、时间戳、注释 |
| `ink-faint` | `rgba(61,43,31,0.4)` | 极淡墨水、分隔线 |
| `ink-ghost` | `rgba(61,43,31,0.15)` | 幽灵边框、滚动条 |

### 2.4 强调色 (Accent)

| Token | 色值 | 语义 | 使用场景 |
|---|---|---|---|
| `accent-pink` | `#f4a8a8` | 可爱/心情 | 宠物、心情进度条、focus ring |
| `accent-sage` | `#a8c4a0` | 成功/完成 | 在线状态、完成节点、代码字符串 |
| `accent-sky` | `#a8c8e8` | 运行中/工具 | 工具调用中、函数名高亮 |
| `accent-lavender` | `#c8b8d8` | 思考/推理 | 思考节点、推理时间线 |
| `accent-coral` | `#e89080` | 错误/警告 | 错误节点、停止按钮 |

### 2.5 shadcn CSS 变量

```css
:root {
  --background: 0 0% 100%;
  --foreground: 20 30% 18%;       /* ink 棕 */
  --card: 40 60% 95%;             /* cream */
  --primary: 20 30% 18%;          /* ink */
  --primary-foreground: 40 60% 95%; /* cream 反色 */
  --secondary: 40 30% 88%;
  --muted: 40 30% 88%;
  --muted-foreground: 25 14% 43%; /* ink-muted */
  --destructive: 10 60% 55%;      /* coral */
  --border: 35 30% 85%;
  --ring: 20 30% 18%;
  --radius: 0.625rem;
}
```

### 2.6 代码高亮色

| Token | 色值 | 语法 |
|---|---|---|
| `.token-keyword` | `#f4a8a8` (pink) | `if`, `return`, `class` |
| `.token-string` | `#a8c4a0` (sage) | 字符串 |
| `.token-comment` | `#7a6b5f` (muted) + italic | 注释 |
| `.token-function` | `#a8c8e8` (sky) | 函数名 |
| `.token-number` | `#f9d5c0` (peach-soft) | 数字 |
| `.token-operator` | `#fdf6e3` (cream-card) | 运算符 |
| `.token-punctuation` | `#7a6b5f` (muted) | 标点 |

---

## 3. 字体体系

| 角色 | Tailwind Class | 字体栈 | 用途 |
|---|---|---|---|
| **展示** | `font-display` | Ma Shan Zheng → Noto Sans SC → PingFang SC → cursive | 标题、标签、日记感文字 |
| **漫画** | `font-comic` / `font-hand` | Ma Shan Zheng → Noto Sans SC → cursive | 消息气泡、工具名、按钮文字 |
| **正文** | `font-sans` | Inter → Noto Sans SC → PingFang SC → system-ui | 系统 UI、状态标签、时间戳 |
| **等宽** | `font-mono` | SF Mono → Fira Code → Cascadia Code → monospace | 代码块、diff、搜索结果 |

**Google Fonts 引入**：
```
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Noto+Sans+SC:wght@400;500;700&family=Ma+Shan+Zheng&display=swap');
```

---

## 4. 圆角体系

| Token | 值 | 用途 |
|---|---|---|
| `rounded-button` | 10px | 按钮、输入框、小交互元素 |
| `rounded-card` | 16px | 卡片、消息气泡、流式卡片 |
| `rounded-pill` | 9999px | 标签、头像、进度条 |

---

## 5. 阴影体系

| Token | 值 | 用途 |
|---|---|---|
| `shadow-dark-btn` | `3px 3px 0 #3d2b1f` | 深色按钮 |
| `shadow-card` | `3px 3px 0 #3d2b1f` | 卡片、工具格、按钮 |
| `shadow-float` | `4px 4px 0 #3d2b1f` | 浮层、弹出元素 |
| `shadow-subtle` | `1px 1px 0 rgba(61,43,31,0.2)` | 消息气泡、轻元素 |
| `shadow-frame` | `1px 1px 0 #3d2b1f, 0 25px 80px rgba(61,43,31,0.15)` | 手机框架 |

**核心规则**：所有阴影都是**硬阴影**（无 blur），偏移量 ≥ 1px，颜色为墨水色。这是手绘风格的灵魂。

---

## 6. 边框规则

| 场景 | 样式 |
|---|---|
| 卡片/按钮/交互元素 | `border: 2px solid #3d2b1f` |
| 消息气泡 | `border: 1.5px solid #3d2b1f` |
| 分隔线 | `border: 1.5px solid #3d2b1f` 或 `border-bottom: 2px solid` |
| 虚线分隔 | `border-top: 2px dashed #3d2b1f; opacity: 0.3` |
| 被动边框 | `border-passive: #e8dcc8` |

---

## 7. 按钮系统

### `.btn-dark`（深色主按钮）
```
bg: #3d2b1f | color: #fdf6e3 | border: 1.5px solid #3d2b1f
shadow: 1px 1px 0 | active: scale(0.96)
```
- 用于：发送按钮、主要操作

### `.btn-light`（浅色次按钮）
```
bg: #fdf6e3 | color: #3d2b1f | border: 2px solid #3d2b1f
shadow: 3px 3px 0 #3d2b1f | hover: bg #f5ecd0 | active: scale(0.96) + shadow 缩小
```
- 用于：演示按钮、次要操作

---

## 8. 消息气泡

### 用户消息 `.msg-user`
```
bg: #3d2b1f (ink) | color: #fdf6e3 (cream-card)
border: 1.5px solid #3d2b1f | radius: 16px 16px 4px 16px（右下角尖角）
font: Ma Shan Zheng | shadow: subtle
```

### AI 消息 `.msg-ai`
```
bg: #fffbf0 (cream-surface) | color: #3d2b1f (ink)
border: 1.5px solid #3d2b1f | radius: 16px 16px 16px 4px（左下角尖角）
font: Ma Shan Zheng | shadow: subtle
```

---

## 9. 流式卡片 (Streaming Cards)

### 基础卡片 `.streaming-card`
```
bg: #fdf6e3 | border: 2px solid #3d2b1f | radius: 16px
padding: 14px 16px | shadow: card | max-width: 90%
```

### 代码块 `.code-block`
```
bg: #3d2b1f (ink) | border: 2px solid #3d2b1f | radius: 12px
shadow: card | 内含语法高亮
```

### 推理线程 `.trace`
```
bg: #fdf6e3 | border: 2px solid #3d2b1f | radius: 16px | shadow: card
可折叠，头部 bg: #f5ecd0，hover: #efe3c8
节点圆点按状态着色：think=lavender, tool=sky, skill=sage, error=coral
```

---

## 10. 动画系统

| 名称 | 时长 | 缓动 | 用途 |
|---|---|---|---|
| `slide-up` | 250ms | ease-out | 用户消息入场 |
| `slide-down` | 200ms | ease-out | AI 消息入场 |
| `fade-in` | 200ms | ease-out | 通用淡入 |
| `pop-in` | 200ms | ease-out | 宠物反应卡片 |
| `gentle-bounce` | 2s | ease-in-out infinite | 宠物头像呼吸 |
| `typing` | 1.2s | ease-in-out infinite | 打字指示器（三个点交错） |
| `shimmer` | 1.5s | linear infinite | 加载骨架 |
| `pulse-ring` | 1.5s | ease-out infinite | 状态脉冲 |
| `spin` | 0.8s | linear infinite | 旋转加载 |
| `blink` | 1s | step-end infinite | 光标闪烁 |

**屏幕过渡**：250ms ease-out（translateX），opacity 200ms

**交互反馈**：
- hover: `transition-colors` 150-300ms
- active: `scale(0.95-0.96)` + 阴影缩小
- Bottom Sheet: 300ms `cubic-bezier(0.32, 0.72, 0, 1)`

**无障碍**：已实现 `prefers-reduced-motion` 全局禁用动画。

---

## 11. 布局系统

### 手机框架 `.phone-frame`
```
width: 390px | height: 844px | max-height: 98vh
bg: #f9f1d8 | radius: 40px | border: 1.5px solid #3d2b1f
shadow: frame | overflow: hidden | flex-col
```
移动端 ≤420px 时全屏（radius: 0, border: none）

### 页面结构
```
header (fixed, pt-12, backdrop-blur) → scrollable content → input bar (fixed bottom, pb-8)
```

### 间距
- 页面水平 padding: `px-4` (16px)
- 消息间距: `space-y-4` (16px)
- 卡片内 padding: `p-4` (16px)
- 按钮/图标: 44x44px (最小触摸目标)

---

## 12. 交互模式

| 模式 | 实现 |
|---|---|
| **屏幕切换** | Chat ↔ History 左右滑动 (translateX) |
| **Bottom Sheet** | 工具箱从底部弹出，带 overlay 遮罩 |
| **Companion Panel** | 宠物面板从底部弹出，带 overlay |
| **流式打字** | 逐字渲染，光标闪烁，支持 stop |
| **推理折叠** | 可展开/折叠的推理时间线 |
| **宠物互动** | 戳一戳/喂食/表扬，pop-in 动画反馈 |

---

## 13. 图标系统

- **主要来源**：自定义 SVG inline（Icons.tsx 中 `ToolIcon`、`StatusIcon`）
- **风格**：stroke-based, `strokeWidth: 2-2.5`, `strokeLinecap: round`, `strokeLinejoin: round`
- **颜色**：默认 `#3d2b1f` (ink)，特殊场景用 accent 色
- **尺寸**：统一 viewBox `0 0 24 24`，通过 `size` prop 控制渲染尺寸
- **禁止**：emoji 作为图标、外部 CDN 图标库

---

## 14. 设计原则

1. **手绘优先**：所有 UI 元素都要有"手工绘制"的质感，拒绝机械感的标准 UI
2. **温暖亲切**：用奶油色 + 桃色 + 墨水棕取代冰冷的灰蓝白色系
3. **趣味性**：通过宠物伙伴、弹跳动画、手绘字体增加趣味，但不幼稚
4. **清晰可读**：尽管是手绘风，代码块、状态指示、信息层级必须清晰
5. **动效克制**：动画服务于信息传达，不喧宾夺主
6. **墨水描边**：2px solid `#3d2b1f` 是统一的视觉锚点
7. **硬阴影**：所有阴影无 blur，偏移 1-4px，强化手绘质感
8. **SVG 图标**：禁止 emoji 图标，所有图标使用 stroke-based SVG

---

## 15. 反模式 (禁止清单)

| 禁止 | 原因 |
|---|---|
| emoji 作为按钮图标 | 使用 SVG icons (Lucide / 自定义) |
| 模糊阴影 (blur shadow) | 破坏手绘硬边质感 |
| 灰蓝色系 (#64748b 等) | 与温暖调性冲突 |
| 标准圆角 8px | 使用 10px (button) / 16px (card) |
| 无描边的卡片 | 所有卡片必须有 ink 描边 |
| 渐变背景 | 保持纯色温暖底色 |
| scale hover 导致布局偏移 | 仅在 active 态使用 scale |
| 标准 cursor | 所有可点击元素必须 `cursor-pointer` |
| CDN 图标依赖 | 本地引入 SVG |

---

## 16. 新增页面/组件检查清单

- [ ] 使用 `ink` 描边 (2px solid #3d2b1f)
- [ ] 使用硬阴影 (shadow-card / shadow-subtle)
- [ ] 标题使用 `font-display`，正文使用 `font-comic` 或 `font-sans`
- [ ] 背景色在 cream 色系内
- [ ] 所有可点击元素有 `cursor-pointer`
- [ ] hover/active 过渡 150-300ms
- [ ] 触摸目标 ≥ 44x44px
- [ ] 图标为 stroke-based SVG，非 emoji
- [ ] 文字对比度 ≥ 4.5:1
- [ ] 已处理 `prefers-reduced-motion`
- [ ] 响应式：375px / 768px / 1024px
