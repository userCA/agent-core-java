# 咪兔 - Theme Style Document
## Theme Name: "Warm Journal" (手账暖调)

---

## 1. Design Philosophy (设计理念)

**Warm Journal** 是一款以"数字手账伴侣"为核心概念的视觉主题。设计灵感源自实体手账本的温暖触感与中国风毛笔书法的优雅气质，旨在打造一个让用户感到放松、愉悦、有陪伴感的 AI 聊天界面。

### 核心特征
- **手账感**: 手绘风格的边框、虚线分隔符、缝合线效果，模拟实体手账本的纸张质感
- **温暖陪伴**: 暖桃色与奶油色的主色调营造出温馨、可信赖的氛围
- **角色共情**: 宠物伙伴「咪兔」作为情感纽带，增强产品的陪伴属性
- **中国风书法**: 马善政楷书（毛笔楷书）贯穿全局字体，端庄有力的笔画赋予界面独特的传统文化韵味
- **轻量活泼**: 弹跳动画、胶囊形按钮、方形宠物头像带来轻松愉快的交互体验

---

## 2. Color Palette (色彩系统)

### 2.1 Primary Colors (主色)

| Token | Hex | RGB | Usage |
|-------|-----|-----|-------|
| `peach/bg` | `#f4b89a` | 244, 184, 154 | App全局背景色，暖桃底色 |
| `peach/light` | `#f8c4a8` | 248, 196, 168 | 浅桃色，用于hover状态、渐变 |
| `peach/soft` | `#f9d5c0` | 249, 213, 192 | 柔和桃色，用于卡片背景、装饰元素 |
| `peach/muted` | `#e8a88a` | 232, 168, 138 | 暗桃色，用于阴影、pressed状态 |

### 2.2 Cream Colors (奶油色/中性背景)

| Token | Hex | RGB | Usage |
|-------|-----|-----|-------|
| `cream/bg` | `#f9f1d8` | 249, 241, 216 | 页面主背景、底部面板底色 |
| `cream/card` | `#fdf6e3` | 253, 246, 227 | 卡片背景、消息气泡、容器底色 |
| `cream/surface` | `#fffbf0` | 255, 251, 240 | 表面高亮色、AI消息气泡 |
| `cream/warm` | `#f5ecd0` | 245, 236, 208 | 暖奶油色、hover背景、工具图标底色 |

### 2.3 Ink Colors (墨色系/文字与线条)

| Token | Hex / Value | Usage |
|-------|-------------|-------|
| `ink/DEFAULT` | `#3d2b1f` | 主要文字、图标、边框线、按钮背景 |
| `ink/muted` | `#7a6b5f` | 次要文字、时间戳、占位符 |
| `ink/faint` | `rgba(61,43,31,0.4)` | 弱化文字、disabled状态、边框hover |
| `ink/ghost` | `rgba(61,43,31,0.15)` | placeholder文字、极弱提示 |

### 2.4 Accent Colors (强调色)

| Token | Hex | Usage |
|-------|-----|-------|
| `accent/pink` | `#f4a8a8` | 宠物心情条、爱心图标、强调高亮 |
| `accent/sage` | `#a8c4a0` | 在线状态指示器、成功提示 |
| `accent/sky` | `#a8c8e8` | 信息提示、辅助装饰 |
| `accent/lavender` | `#c8b8d8` | 辅助装饰色 |
| `accent/coral` | `#e89080` | 警告/重要提示 |

### 2.5 Semantic Colors (语义色)

| Context | Color | Usage |
|---------|-------|-------|
| Online/Active | `#a8c4a0` (sage green) | 在线状态圆点 |
| Success | `#a8c4a0` | 测试通过、操作成功 |
| Error | `#e89080` (coral) | 调试错误、删除操作 |
| Code Add | `#059669` (emerald-600) | Git diff 新增行 |
| Code Remove | `#e11d48` (rose-500) | Git diff 删除行 |

### 2.6 Border Colors (边框色)

| Token | Value | Usage |
|-------|-------|-------|
| `border/passive` | `#e8dcc8` | 分割线、表单边框默认态 |
| `border/interactive` | `rgba(61,43,31,0.4)` | 输入框focus、交互元素边框 |

---

## 3. Typography (字体系统)

### 3.1 Font Families (字体族)

| Role | Font Stack | Usage |
|------|-----------|-------|
| **Primary Sans** | `Inter`, `Noto Sans SC`, `PingFang SC`, `Microsoft YaHei`, `Hiragino Sans GB`, `system-ui`, sans-serif | 代码块、文件路径、英文内容 |
| **Display** (font-display) | `Ma Shan Zheng` (马善政楷书), `Noto Sans SC`, `PingFang SC`, cursive | 品牌标题、卡片标题、日期标签、导航栏 |
| **Handwritten** (font-hand) | `Ma Shan Zheng` (马善政楷书), `Noto Sans SC`, `PingFang SC`, cursive | 消息正文、按钮文字、状态标签 |
| **Comic** (font-comic) | `Ma Shan Zheng` (马善政楷书), `Noto Sans SC`, `PingFang SC`, cursive | 时间戳、小标签、统计数字 |

### 3.2 Type Scale (字号层级)

| Level | Size | Line Height | Font | Weight | Usage |
|-------|------|-------------|------|--------|-------|
| Display | 20px | 1.2 | Display | 700 | 统计数字、大号标题 |
| H1 | 18px | 1.3 | Display | 700 | 面板标题、页面主标题 |
| H2 | 16px | 1.3 | Display | 700 | 品牌标题、导航栏标题 |
| H3 | 15px | 1.4 | Display | 700 | 卡片标题、消息小标题 |
| Body | 15px | 1.6 | Hand | 400 | 消息内容、正文、输入框 |
| Label | 13px | 1.4 | Display | 700 | 日期标签、分类标签 |
| Caption | 12px | 1.4 | Hand | 700 | 时间戳、小标签 |
| Small | 11px | 1.4 | Hand | 400/700 | 次要信息、状态文字、统计描述 |
| Tiny | 10px | 1.4 | Hand | 400 | 元数据、计数标签 |

### 3.3 Typography Patterns (排版模式)

- **品牌标题**: font-display, 16px, bold, tracking-tight
- **时间戳**: font-hand, 10px, ink-muted
- **日期分隔**: font-display, 13px, ink-muted, uppercase, tracking-wide
- **消息正文**: font-hand, 15px, leading-relaxed (1.625)
- **标签文字**: font-hand, 10-11px, 配合 pill 容器使用

---

## 4. Spacing System (间距系统)

### 4.1 Base Unit

Base unit: **4px**

| Token | Value | Usage |
|-------|-------|-------|
| `space/1` | 4px | 极小间距，图标内部 |
| `space/2` | 8px | 元素紧密排列 |
| `space/3` | 12px | 标准内联间距 |
| `space/4` | 16px | 模块内padding、卡片间距 |
| `space/5` | 20px | 面板内边距 |
| `space/6` | 24px | 区块间距 |

### 4.2 Component Spacing

| Component | Padding | Gap |
|-----------|---------|-----|
| Message bubble | px-4 py-3 (16px 12px) | - |
| Card container | p-4 (16px) | - |
| Header | px-4 pt-12 pb-3 | - |
| Input area | px-4 pb-8 pt-3 | gap-2 (8px) |
| Tool grid item | p-4 (16px) | gap-2 (8px) |
| Suggestion pills | px-3 py-2 | gap-2 (8px) |
| Bottom sheet | px-5 (20px) | - |

---

## 5. Border & Radius (边框与圆角)

### 5.1 Border Style

| Property | Value | Usage |
|----------|-------|-------|
| Standard border | `1.5px solid #3d2b1f` | 卡片、消息气泡、容器 |
| Thick border | `2px solid #3d2b1f` | 按钮、输入框、工具面板 |
| Dashed divider | `2px dashed #3d2b1f` (opacity 0.3) | 日期分隔线 |
| Passive border | `1px solid #e8dcc8` | 表单边框默认态 |

### 5.2 Radius Tokens

| Token | Value | Usage |
|-------|-------|-------|
| `radius/button` | 10px | 按钮、输入框 |
| `radius/card` | 16px | 卡片、消息气泡 |
| `radius/pill` | 9999px | 胶囊标签、圆形头像容器 |
| `radius/full` | 50% | 纯圆形元素 |
| `radius/sheet` | 20px (top only) | 底部面板顶部圆角 |
| `radius/frame` | 40px | 手机外框 |

### 5.3 Hand-drawn Effect Classes

```
.hand-drawn     → border: 1.5px solid ink, radius: 14px
.hand-drawn-sm  → border: 2px solid ink, radius: 10px
.hand-drawn-pill → border: 1.5px solid ink, radius: 9999px
.hand-drawn-circle → border: 1.5px solid ink, radius: 50%
```

---

## 6. Shadow System (阴影系统)

| Token | Value | Usage |
|-------|-------|-------|
| `shadow/card` | `3px 3px 0 #3d2b1f` | 卡片、消息气泡、面板 |
| `shadow/dark-btn` | `3px 3px 0 #3d2b1f` | 深色按钮 |
| `shadow/float` | `4px 4px 0 #3d2b1f` | 悬浮元素（宠物按钮） |
| `shadow/subtle` | `1px 1px 0 rgba(61,43,31,0.2)` | 用户消息气泡 |
| `shadow/frame` | `1px 1px 0 #3d2b1f, 0 25px 80px rgba(61,43,31,0.15)` | 手机外框 |
| `shadow/overlay` | (backdrop-blur) `bg-ink/20 backdrop-blur-sm` | 遮罩层 |

> **Design Note**: 阴影采用硬阴影（Hard Shadow）风格，无模糊值，模拟手绘卡片的堆叠感。这是本主题最具辨识度的视觉特征之一。

---

## 7. Component Styles (组件样式)

### 7.1 Message Bubbles (消息气泡)

**User Message (用户消息)**
```css
background: #3d2b1f;
color: #fdf6e3;
border: 1.5px solid #3d2b1f;
border-radius: 16px 16px 4px 16px;  /* 左上-右上-右下-左下 */
box-shadow: 1px 1px 0 rgba(61,43,31,0.2);
padding: 12px 16px;
max-width: 85%;
font-size: 15px;
line-height: 1.625;
```

**AI Message (AI消息)**
```css
background: #fffbf0;
color: #3d2b1f;
border: 1.5px solid #3d2b1f;
border-radius: 16px 16px 16px 4px;  /* 左上-右上-右下-左下 */
box-shadow: 1px 1px 0 rgba(61,43,31,0.15);
padding: 12px 16px;
max-width: 85%;
font-size: 15px;
line-height: 1.625;
```

### 7.2 Buttons (按钮)

**Dark Button (深色按钮)**
```css
background: #3d2b1f;
color: #fdf6e3;
border: 1.5px solid #3d2b1f;
border-radius: 10px;
box-shadow: 1px 1px 0 rgba(61,43,31,0.3);
transition: all 0.15s ease;
/* Active */ transform: scale(0.96);
```

**Light Button (浅色按钮)**
```css
background: #fdf6e3;
color: #3d2b1f;
border: 2px solid #3d2b1f;
border-radius: 10px;
box-shadow: 3px 3px 0 #3d2b1f;
transition: all 0.15s ease;
/* Hover */ background: #f5ecd0;
/* Active */ transform: scale(0.96); box-shadow: 1px 1px 0 #3d2b1f;
```

**Tool Pill (工具胶囊)**
```css
background: #fdf6e3;
border: 2px solid #3d2b1f;
border-radius: 10px;
box-shadow: 3px 3px 0 #3d2b1f;
white-space: nowrap;
transition: all 0.15s ease;
/* Hover */ background: #f5ecd0;
/* Active */ transform: scale(0.96);
```

### 7.3 Cards (卡片)

**Journal Card (日记卡片)**
```css
background: #fdf6e3;
border: 1.5px solid #3d2b1f;
border-radius: 16px;
box-shadow: 3px 3px 0 #3d2b1f;
padding: 16px;
transition: all 0.15s ease;
/* Active */ transform: scale(0.98); box-shadow: 1px 1px 0 #3d2b1f;
```

**Status Card (状态卡片)**
```css
background: #fdf6e3;
border: 2px solid #3d2b1f;
border-radius: 16px;
box-shadow: 3px 3px 0 #3d2b1f;
padding: 16px;
```

### 7.4 Input Fields (输入框)

**Text Input / Textarea**
```css
background: #fdf6e3;
border: 2px solid #3d2b1f;
border-radius: 10px;
padding: 12px 16px;
font-size: 15px;
color: #3d2b1f;
placeholder-color: rgba(61,43,31,0.15);
/* Focus */ outline: none; border-color: #3d2b1f; box-shadow: 1px 1px 0 #3d2b1f;
/* Focus-visible */ outline: 3px solid #3d2b1f; outline-offset: 2px;
resize: none;
min-height: 44px;
max-height: 120px;
```

### 7.5 Progress Bar (进度条)

```css
.hand-progress {
    border: 2px solid #3d2b1f;
    border-radius: 9999px;
    overflow: hidden;
    background: #f5ecd0;
    height: 12px;
}
.hand-progress-fill {
    border-radius: 9999px;
    background: #f4a8a8;
    height: 100%;
}
```

### 7.6 Avatar / Icon Containers (头像/图标容器)

```css
/* AI Avatar */
width: 32px; height: 32px;
border-radius: 50%;
background: #fdf6e3;
border: 2px solid #3d2b1f;
box-shadow: 3px 3px 0 #3d2b1f;

/* Tool Icon Box */
width: 40px; height: 40px;
border-radius: 10px;
background: #f5ecd0;
border: 2px solid #3d2b1f;

/* Pet Avatar (Large) */
width: 56px; height: 56px;
border-radius: 50%;
background: #f4a8a8;
border: 2px solid #3d2b1f;
box-shadow: 3px 3px 0 #3d2b1f;
```

---

## 8. Streaming Message Formats (流式消息交互格式)

流式消息是 AI 响应过程中的中间态视觉呈现，用于展示思考过程、工具调用、Skill 执行等状态。所有流式卡片均挂载在 AI 消息气泡下方，采用统一的手账卡片容器风格。

### 8.1 Unified Streaming Card Container (流式卡片统一容器)

所有流式组件共享以下基础样式：

```css
.streaming-card {
    background: #fdf6e3;
    border: 2px solid #3d2b1f;
    border-radius: 16px;
    padding: 14px 16px;
    box-shadow: 3px 3px 0 #3d2b1f;
    margin-top: 8px;
    max-width: 90%;
    animation: slideDown 0.2s ease-out;
}
```

**结构模式**：
```
[Header Row]    → 图标(24px) + 标题 + 状态标签(右对齐)
[Divider]       → 1.5px dashed ink/15, margin 10px 0
[Content Area]  → 内容区域（因类型而异）
[Footer Row]    → 可选：时间戳、展开/收起按钮
```

### 8.2 Thinking / Reasoning Block (思考过程)

用于展示 AI 的推理链（Chain-of-Thought），支持展开/收起。

**Collapsed State (收起态)**
```css
.thinking-header {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
}
.thinking-icon {
    width: 24px; height: 24px;
    border-radius: 50%;
    background: #f9d5c0;
    border: 1.5px solid #3d2b1f;
    display: flex;
    align-items: center;
    justify-content: center;
    /* Icon: sparkle / brain / lightbulb, 14px, ink */
}
.thinking-title {
    font-family: display;
    font-size: 13px;
    font-weight: 700;
    color: #3d2b1f;
}
.thinking-hint {
    font-family: hand;
    font-size: 11px;
    color: #7a6b5f;
    margin-left: auto;
}
/* 右侧 chevron-down 图标，ink-muted，12px */
```

**Expanded State (展开态)**
```css
.thinking-content {
    margin-top: 10px;
    padding-top: 10px;
    border-top: 1.5px dashed rgba(61,43,31,0.15);
    font-size: 13px;
    line-height: 1.7;
    color: #7a6b5f;
    font-family: sans;
    max-height: 200px;
    overflow-y: auto;
}
/* 展开时 chevron 旋转 180deg，transition 0.2s */
```

**Streaming Indicator (流式接收指示)**
```css
.thinking-streaming::after {
    content: "";
    display: inline-block;
    width: 4px; height: 4px;
    border-radius: 50%;
    background: #f4a8a8;
    margin-left: 4px;
    animation: typing 1s infinite;
}
```

**Complete State (完成态)**
- 图标背景色从 `#f9d5c0` 变为 `#a8c4a0`（sage green）
- 标题右侧显示小勾图标（`check-circle`, 12px, `#a8c4a0`）
- 思考内容文字颜色从 `#7a6b5f` 变为 `#3d2b1f`

---

### 8.3 Tool Call Block (工具调用)

用于展示 AI 调用外部工具的过程，包含参数展示、执行状态、结果回显。

**States Overview**: `preparing` → `calling` → `executing` → `complete` / `error`

#### 8.3.1 Tool Call - Preparing (准备中)

```css
/* Header: wrench icon in lavender bg */
.tool-icon-preparing { background: #c8b8d8; }
.tool-status-label {
    margin-left: auto;
    font-family: hand;
    font-size: 10px;
    padding: 2px 8px;
    border-radius: 9999px;
    border: 1.5px solid #3d2b1f;
    background: #f5ecd0;
    color: #3d2b1f;
}
```

#### 8.3.2 Tool Call - Calling/Executing (执行中)

```css
/* Header: tool icon with pulsing ring */
.tool-icon-executing {
    background: #a8c8e8;
    position: relative;
}
.tool-icon-executing::before {
    content: "";
    position: absolute;
    inset: -3px;
    border-radius: 50%;
    border: 1.5px solid #a8c8e8;
    animation: pulse-ring 1.5s ease-out infinite;
}
@keyframes pulse-ring {
    0% { transform: scale(1); opacity: 0.6; }
    100% { transform: scale(1.6); opacity: 0; }
}
/* Status label bg: #a8c8e8, text: ink */
/* Content: 参数列表展示 */
```

**Parameter List Style**
```css
.param-row {
    display: flex;
    gap: 8px;
    padding: 6px 0;
    font-size: 12px;
    border-bottom: 1px dashed rgba(61,43,31,0.1);
}
.param-key {
    font-family: hand;
    font-weight: 700;
    color: #3d2b1f;
    min-width: 60px;
    flex-shrink: 0;
}
.param-value {
    font-family: sans;
    color: #7a6b5f;
    word-break: break-all;
}
```

#### 8.3.3 Tool Call - Complete (完成)

```css
/* Header icon bg: #a8c4a0 (sage) */
/* Status label: "Done" with check icon */
.tool-status-complete {
    background: #a8c4a0;
    color: #3d2b1f;
}
/* Content: 结果摘要或折叠的结果卡片 */
```

**Result Preview (结果预览)**
```css
.tool-result-preview {
    background: #fffbf0;
    border: 1.5px solid #3d2b1f;
    border-radius: 10px;
    padding: 10px 12px;
    font-size: 12px;
    font-family: sans;
    color: #3d2b1f;
    line-height: 1.5;
    margin-top: 8px;
}
```

#### 8.3.4 Tool Call - Error (错误)

```css
/* Header icon bg: #e89080 (coral) */
/* Border color: #e89080 */
/* Status label: "Failed" with alert-triangle icon */
.tool-status-error {
    background: #e89080;
    color: #3d2b1f;
}
/* Error message text: #e89080, 12px */
/* Retry button: 小号 dark button 样式 */
```

---

### 8.4 Skill Call Block (Skill 调用)

Skill 是组合多个工具的高阶能力调用，视觉层级高于普通 Tool Call，采用更醒目的卡片样式。

#### 8.4.1 Skill Card - Active (执行中)

```css
.skill-card {
    background: linear-gradient(135deg, #fdf6e3 0%, #f9d5c0 100%);
    border: 2.5px solid #3d2b1f;
    border-radius: 16px;
    padding: 16px;
    box-shadow: 4px 4px 0 #3d2b1f;
    position: relative;
    overflow: hidden;
}
/* 左上角装饰角标 */
.skill-card::before {
    content: "SKILL";
    position: absolute;
    top: 6px;
    right: -22px;
    transform: rotate(45deg);
    background: #f4a8a8;
    color: #3d2b1f;
    font-size: 8px;
    font-family: hand;
    font-weight: 700;
    padding: 2px 24px;
    border: 1px solid #3d2b1f;
    letter-spacing: 1px;
}
```

**Skill Header**
```css
.skill-header {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 12px;
}
.skill-icon {
    width: 32px; height: 32px;
    border-radius: 10px;
    background: #f4b89a;
    border: 2px solid #3d2b1f;
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 2px 2px 0 #3d2b1f;
    /* Icon: 20px, ink */
}
.skill-name {
    font-family: hand;
    font-size: 15px;
    font-weight: 700;
    color: #3d2b1f;
}
.skill-description {
    font-family: hand;
    font-size: 11px;
    color: #7a6b5f;
    margin-top: 2px;
}
```

**Skill Progress (执行进度)**
```css
.skill-progress-bar {
    height: 10px;
    background: #f5ecd0;
    border: 2px solid #3d2b1f;
    border-radius: 9999px;
    overflow: hidden;
    margin: 12px 0;
    position: relative;
}
.skill-progress-fill {
    height: 100%;
    background: linear-gradient(90deg, #f4b89a 0%, #f4a8a8 100%);
    border-radius: 9999px;
    transition: width 0.4s ease;
    position: relative;
}
/* 条纹动画效果 */
.skill-progress-fill::after {
    content: "";
    position: absolute;
    inset: 0;
    background: repeating-linear-gradient(
        45deg,
        transparent,
        transparent 4px,
        rgba(255,255,255,0.3) 4px,
        rgba(255,255,255,0.3) 8px
    );
    animation: skill-stripe 0.5s linear infinite;
}
@keyframes skill-stripe {
    0% { background-position: 0 0; }
    100% { background-position: 11px 0; }
}
.skill-progress-text {
    font-family: hand;
    font-size: 10px;
    color: #7a6b5f;
    text-align: center;
    margin-top: 4px;
}
```

**Sub-Tool List (子工具链)**
```css
.skill-subtools {
    margin-top: 12px;
    padding-top: 12px;
    border-top: 1.5px dashed rgba(61,43,31,0.15);
}
.subtool-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 0;
    font-size: 12px;
}
.subtool-status {
    width: 16px; height: 16px;
    border-radius: 50%;
    border: 1.5px solid #3d2b1f;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
}
/* Pending: 空心圆 */
.subtool-status.pending { background: transparent; }
/* Running: 填充 sky + 旋转loader */
.subtool-status.running {
    background: #a8c8e8;
    position: relative;
}
.subtool-status.running::after {
    content: "";
    width: 8px; height: 8px;
    border: 1.5px solid #3d2b1f;
    border-top-color: transparent;
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
}
/* Complete: 填充 sage + 对勾 */
.subtool-status.complete { background: #a8c4a0; }
/* Error: 填充 coral + 感叹号 */
.subtool-status.error { background: #e89080; }

.subtool-name {
    font-family: sans;
    color: #3d2b1f;
}
.subtool-name.completed { color: #7a6b5f; text-decoration: line-through; }
```

#### 8.4.2 Skill Card - Complete (完成态)

```css
/* 移除条纹动画 */
.skill-progress-fill::after { animation: none; }
/* 进度条 fill 变为 #a8c4a0 */
.skill-progress-fill.complete {
    background: #a8c4a0;
}
/* 角标背景变 sage */
.skill-card.complete::before { background: #a8c4a0; }
/* 添加完成印章效果 */
.skill-complete-stamp {
    position: absolute;
    bottom: 12px;
    right: 12px;
    width: 48px; height: 48px;
    border-radius: 50%;
    border: 2.5px solid #a8c4a0;
    color: #a8c4a0;
    display: flex;
    align-items: center;
    justify-content: center;
    font-family: hand;
    font-size: 10px;
    font-weight: 700;
    transform: rotate(-15deg);
    opacity: 0.6;
    pointer-events: none;
}
```

---

### 8.5 Code Block - Streaming (代码块流式输出)

代码块在流式输出时的特殊处理。

**Container**
```css
.code-block {
    background: #3d2b1f;
    border: 2px solid #3d2b1f;
    border-radius: 12px;
    overflow: hidden;
    margin-top: 8px;
    box-shadow: 3px 3px 0 #3d2b1f;
}
```

**Code Header**
```css
.code-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 12px;
    background: rgba(255,255,255,0.08);
    border-bottom: 1.5px solid rgba(255,255,255,0.1);
}
.code-lang-label {
    font-family: hand;
    font-size: 10px;
    color: #f9d5c0;
    text-transform: uppercase;
    letter-spacing: 1px;
}
.code-actions {
    display: flex;
    gap: 6px;
}
.code-action-btn {
    width: 24px; height: 24px;
    border-radius: 6px;
    background: transparent;
    border: 1px solid rgba(255,255,255,0.15);
    color: #f9d5c0;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: all 0.15s ease;
}
.code-action-btn:hover {
    background: rgba(255,255,255,0.1);
    border-color: rgba(255,255,255,0.3);
}
```

**Code Content - Streaming**
```css
.code-content {
    padding: 12px 14px;
    font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', monospace;
    font-size: 12.5px;
    line-height: 1.7;
    color: #fdf6e3;
    overflow-x: auto;
    white-space: pre;
    tab-size: 4;
}
/* 流式输出时的光标 */
.code-cursor {
    display: inline-block;
    width: 2px;
    height: 1.2em;
    background: #f4a8a8;
    vertical-align: text-bottom;
    margin-left: 1px;
    animation: blink 1s step-end infinite;
}
@keyframes blink {
    0%, 100% { opacity: 1; }
    50% { opacity: 0; }
}
```

**Syntax Highlighting (Tokens)**
```css
.token-keyword     { color: #f4a8a8; font-weight: 600; }
.token-string      { color: #a8c4a0; }
.token-comment     { color: #7a6b5f; font-style: italic; }
.token-function    { color: #a8c8e8; }
.token-number      { color: #f9d5c0; }
.token-operator    { color: #fdf6e3; }
.token-punctuation { color: #7a6b5f; }
```

---

### 8.6 Search / Retrieval Block (搜索/检索结果)

用于展示代码搜索、知识库检索等结果。

**Container**
```css
.search-result-card {
    background: #fdf6e3;
    border: 2px solid #3d2b1f;
    border-radius: 16px;
    padding: 14px 16px;
    box-shadow: 3px 3px 0 #3d2b1f;
    margin-top: 8px;
}
```

**Result Header**
```css
.search-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 10px;
}
.search-icon {
    width: 24px; height: 24px;
    border-radius: 6px;
    background: #f5ecd0;
    border: 1.5px solid #3d2b1f;
    display: flex;
    align-items: center;
    justify-content: center;
}
.search-title {
    font-family: display;
    font-size: 13px;
    font-weight: 700;
    color: #3d2b1f;
}
.search-count {
    margin-left: auto;
    font-family: hand;
    font-size: 10px;
    color: #7a6b5f;
    padding: 2px 8px;
    background: #f5ecd0;
    border: 1px solid #3d2b1f;
    border-radius: 9999px;
}
```

**Result Item**
```css
.result-item {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 10px 12px;
    background: #fffbf0;
    border: 1.5px solid #3d2b1f;
    border-radius: 10px;
    margin-bottom: 6px;
    transition: all 0.15s ease;
    cursor: pointer;
}
.result-item:hover {
    background: #f5ecd0;
    transform: translateX(2px);
}
.result-path {
    font-family: sans;
    font-size: 10px;
    color: #7a6b5f;
}
.result-filename {
    font-family: sans;
    font-size: 12px;
    font-weight: 600;
    color: #3d2b1f;
}
.result-preview {
    font-family: monospace;
    font-size: 11px;
    color: #7a6b5f;
    line-height: 1.5;
    margin-top: 4px;
    padding: 6px 8px;
    background: rgba(61,43,31,0.04);
    border-radius: 6px;
    border-left: 2px solid #f4a8a8;
}
/* 高亮匹配关键词 */
.result-highlight {
    background: rgba(244, 184, 154, 0.4);
    padding: 0 2px;
    border-radius: 2px;
    font-weight: 600;
}
```

---

### 8.7 Git Diff Block (代码差异)

用于展示 Git diff 结果，采用分栏色彩区分。

**Container**
```css
.git-diff-card {
    background: #fdf6e3;
    border: 2px solid #3d2b1f;
    border-radius: 16px;
    overflow: hidden;
    box-shadow: 3px 3px 0 #3d2b1f;
    margin-top: 8px;
}
```

**Diff Header**
```css
.diff-header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 14px;
    background: #f5ecd0;
    border-bottom: 1.5px solid #3d2b1f;
}
.diff-file-name {
    font-family: sans;
    font-size: 12px;
    font-weight: 600;
    color: #3d2b1f;
}
.diff-stats {
    margin-left: auto;
    display: flex;
    gap: 8px;
    font-family: hand;
    font-size: 10px;
}
.diff-stat-add { color: #059669; }
.diff-stat-del { color: #e11d48; }
```

**Diff Line**
```css
.diff-content {
    font-family: 'SF Mono', monospace;
    font-size: 11.5px;
    line-height: 1.6;
    overflow-x: auto;
}
.diff-line {
    display: flex;
    padding: 1px 0;
    min-width: 100%;
}
.diff-line-num {
    width: 36px;
    text-align: right;
    padding-right: 12px;
    color: #7a6b5f;
    font-size: 10px;
    flex-shrink: 0;
    user-select: none;
}
.diff-line-add {
    background: rgba(5, 150, 105, 0.08);
}
.diff-line-add .diff-line-prefix { color: #059669; }
.diff-line-del {
    background: rgba(225, 29, 72, 0.08);
}
.diff-line-del .diff-line-prefix { color: #e11d48; }
.diff-line-neutral {
    background: transparent;
}
.diff-line-prefix {
    width: 16px;
    flex-shrink: 0;
    text-align: center;
}
.diff-line-code {
    color: #3d2b1f;
    white-space: pre;
    padding-right: 14px;
}
```

---

### 8.8 File Operation Block (文件操作)

用于展示文件读写、创建、删除等操作。

```css
.file-op-card {
    background: #fdf6e3;
    border: 2px solid #3d2b1f;
    border-radius: 16px;
    padding: 14px 16px;
    box-shadow: 3px 3px 0 #3d2b1f;
    margin-top: 8px;
}
.file-op-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 0;
    border-bottom: 1px dashed rgba(61,43,31,0.1);
}
.file-op-item:last-child { border-bottom: none; }

.file-op-icon {
    width: 28px; height: 28px;
    border-radius: 8px;
    border: 1.5px solid #3d2b1f;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
}
/* 创建: bg #a8c4a0, icon: file-plus */
/* 读取: bg #a8c8e8, icon: file-text */
/* 修改: bg #f9d5c0, icon: file-edit */
/* 删除: bg #e89080, icon: file-minus */

.file-op-details {
    flex: 1;
    min-width: 0;
}
.file-op-path {
    font-family: sans;
    font-size: 12px;
    color: #3d2b1f;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}
.file-op-meta {
    font-family: hand;
    font-size: 10px;
    color: #7a6b5f;
    margin-top: 2px;
}
.file-op-status {
    font-family: hand;
    font-size: 10px;
    padding: 2px 8px;
    border-radius: 9999px;
    border: 1px solid #3d2b1f;
    flex-shrink: 0;
}
/* 状态样式 */
.file-op-status.success { background: #a8c4a0; }
.file-op-status.pending { background: #f5ecd0; }
.file-op-status.error { background: #e89080; }
```

---

### 8.9 Streaming Message Lifecycle (流式消息生命周期)

流式消息在聊天流中的状态流转规范：

```
[User Message]
     ↓
[Typing Indicator] (显示 0.5-1.5s)
     ↓
[Thinking Block] (可选，若模型支持 reasoning)
     ↓
[Tool Call / Skill Call Block] (若触发工具)
     ↓
[AI Message Bubble] (内容开始流式输出)
     ↓
[Streaming Complete] (全部内容接收完毕)
```

**Transition Rules (过渡规则)**

| 过渡 | 动画 | 说明 |
|------|------|------|
| Typing → Thinking | fadeIn 0.15s | 打字指示器淡出，思考卡片淡入 |
| Thinking → Tool | slideDown 0.2s | 工具卡片从上方滑入 |
| Tool → Message | slideDown 0.2s | 消息气泡出现，工具卡片保持可见 |
| Message 流式接收 | 逐字 fadeIn | 每个 token 以 0.03s fadeIn 出现 |
| 全部完成 | 工具图标状态切换 | pending → complete 颜色过渡 0.3s |

**Stacking Rules (堆叠规则)**

多个流式卡片同时存在时的排列：

```css
.streaming-stack {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin-top: 8px;
}
/* 多个卡片按时间顺序从上到下排列 */
/* 最新卡片 slideDown 入场 */
/* 已完成卡片可保持显示或自动折叠 */
```

---

### 8.10 Animation Keyframes for Streaming (流式动画关键帧)

```css
/* 卡片入场 */
@keyframes streamCardEnter {
    from {
        transform: translateY(-6px) scale(0.98);
        opacity: 0;
    }
    to {
        transform: translateY(0) scale(1);
        opacity: 1;
    }
}

/* 状态切换闪烁 */
@keyframes statusFlash {
    0% { box-shadow: 0 0 0 0 rgba(168, 196, 160, 0.4); }
    70% { box-shadow: 0 0 0 6px rgba(168, 196, 160, 0); }
    100% { box-shadow: 0 0 0 0 rgba(168, 196, 160, 0); }
}

/* 终端光标 */
@keyframes terminalCursor {
    0%, 100% { opacity: 1; }
    50% { opacity: 0; }
}

/* 旋转加载 */
@keyframes spin {
    from { transform: rotate(0deg); }
    to { transform: rotate(360deg); }
}

/* 脉冲光环 */
@keyframes pulse-ring {
    0% { transform: scale(1); opacity: 0.5; }
    100% { transform: scale(1.8); opacity: 0; }
}

/* Skill 条纹滑动 */
@keyframes skill-stripe {
    0% { background-position: 0 0; }
    100% { background-position: 11px 0; }
}
```

---

## 9. Animation System (动画系统)

### 9.1 Entrance Animations (入场动画)

| Animation | Duration | Easing | Usage |
|-----------|----------|--------|-------|
| slideUp | 0.25s | ease-out | 用户消息入场、底部面板 |
| slideDown | 0.20s | ease-out | AI消息入场、顶部元素 |
| fadeIn | 0.20s | ease-out | 通用淡入 |
| popIn | 0.20s | ease-out | 弹窗、Toast、提示 |

### 9.2 Continuous Animations (持续动画)

| Animation | Duration | Easing | Usage |
|-----------|----------|--------|-------|
| gentleBounce | 2s | ease-in-out infinite | 咪兔头像呼吸动效 |
| typing | 1.2s | ease-in-out infinite | 输入指示器三个圆点 |

### 9.3 Transition Patterns (过渡模式)

| Element | Property | Duration | Easing |
|---------|----------|----------|--------|
| Buttons | all | 0.15s | ease |
| Cards | all | 0.15s | ease |
| Companion Panel | transform | 0.25s | cubic-bezier(0.32, 0.72, 0, 1) |
| Bottom Sheet | transform | 0.30s | cubic-bezier(0.32, 0.72, 0, 1) |
| Overlay | opacity | 0.20s | ease |
| Screen Transition | transform + opacity | 0.25s + 0.20s | ease-out |
| 咪兔头像 | all | 0.20s | ease |

### 9.4 Interaction Feedback (交互反馈)

| Action | Feedback |
|--------|----------|
| Tap button | scale(0.96), shadow 缩小 |
| Tap card | scale(0.98), shadow 缩小 |
| Focus input | border 变色, shadow 出现 |
| Focus-visible | 3px outline, 2px offset |

### 9.5 Accessibility (可访问性)

```css
@media (prefers-reduced-motion: reduce) {
    .anim-slide-up, .anim-slide-down, .anim-fade-in, .anim-pop-in,
    .pet-bounce, .typing-dot {
        animation: none;
    }
}
```

---

## 10. Decorative Elements (装饰元素)

### 10.1 Dashed Divider (虚线分隔符)
```css
border-top: 2px dashed #3d2b1f;
opacity: 0.3;
```
用于：日期分隔、内容区块分隔

### 10.2 Stitched Effect (缝合线效果)
```css
.stitched {
    background-image: repeating-linear-gradient(
        90deg,
        transparent, transparent 6px,
        #3d2b1f 6px, #3d2b1f 8px,
        transparent 8px, transparent 14px
    );
    background-size: 100% 1.5px;
    background-repeat: repeat-x;
    opacity: 0.25;
}
```
用于：卡片顶部装饰、手账本装订线效果

### 10.3 Hand-drawn Heart (手绘爱心)
```css
fill: #f4a8a8;
stroke: #3d2b1f;
stroke-width: 2;
```

### 10.4 Bottom Sheet Handle (面板把手)
```css
width: 36px; height: 6px;
border-radius: 9999px;
background: rgba(61,43,31,0.25);
border: 1px solid rgba(61,43,31,0.2);
```

---

## 11. Layout Patterns (布局模式)

### 11.1 Phone Frame (手机框架)
```css
width: 390px; height: 844px;
max-height: 98vh;
background: #f9f1d8;
border-radius: 40px;
border: 1.5px solid #3d2b1f;
box-shadow: 1px 1px 0 #3d2b1f, 0 25px 80px rgba(61,43,31,0.15);
overflow: hidden;

/* Mobile Override */
@media (max-width: 420px) {
    width: 100vw; height: 100vh;
    border-radius: 0;
    box-shadow: none;
    border: none;
}
```

### 11.2 Screen Structure (屏幕结构)
```
[Header]    → fixed, backdrop-blur, z-20, border-bottom
[Content]   → flex-1, overflow-y-auto, custom-scroll
[Input]     → fixed bottom, border-top
```

### 11.3 Message Layout (消息布局)
```
AI Message:  [Avatar 32px] [Bubble max-85%] [Time]
User Message:              [Bubble max-85%] [Time] (right-aligned)
```

### 11.4 Z-Index Hierarchy (层级系统)

| Layer | Z-Index | Element |
|-------|---------|---------|
| Content | 0-10 | 消息、卡片 |
| Header | 20 | 顶部导航 |
| Overlay | 30 | 遮罩层 |
| Bottom Sheet | 40 | 底部面板 |

---

## 12. Scrollbar Styling (滚动条样式)

```css
.custom-scroll::-webkit-scrollbar { width: 4px; }
.custom-scroll::-webkit-scrollbar-track { background: transparent; }
.custom-scroll::-webkit-scrollbar-thumb {
    background: rgba(61,43,31,0.15);
    border-radius: 4px;
}
```

---

## 13. Expanded Theme - Extended Pages (扩展页面规范)

基于现有风格，以下为可扩展的页面模块规范：

### 13.1 Settings Screen (设置页)

**Layout Pattern**
- 顶部导航栏：返回按钮 + "设置" 标题
- 分组列表：手账风格卡片分组
- 每个设置项：左侧图标 + 标题 + 右侧箭头/开关

**Setting Row Style**
```css
background: #fdf6e3;
border: 1.5px solid #3d2b1f;
border-radius: 16px;
padding: 16px;
box-shadow: 3px 3px 0 #3d2b1f;
/* Active */ transform: scale(0.98);
```

**Toggle Switch Style**
```css
/* Track */
width: 44px; height: 24px;
border-radius: 9999px;
border: 2px solid #3d2b1f;
background: #f5ecd0;

/* Thumb */
width: 18px; height: 18px;
border-radius: 50%;
background: #3d2b1f;

/* Checked Track */
background: #a8c4a0;
```

### 13.2 Profile Screen (个人资料页)

**Layout Pattern**
- 顶部：大尺寸宠物头像（80px）+ 用户名
- 中部：统计卡片网格（连续天数、总消息数、工具使用次数）
- 底部：成就徽章横向滚动列表

**Stat Card Style**
```css
background: #fdf6e3;
border: 2px solid #3d2b1f;
border-radius: 16px;
padding: 16px;
box-shadow: 3px 3px 0 #3d2b1f;
text-align: center;
```

**Badge Style**
```css
width: 64px; height: 64px;
border-radius: 50%;
background: cream-warm;
border: 2px solid #3d2b1f;
box-shadow: 2px 2px 0 #3d2b1f;
/* Locked */ opacity: 0.4; border-style: dashed;
```

### 13.3 Onboarding Screen (引导页)

**Layout Pattern**
- 全屏奶油色背景
- 中央：大尺寸宠物插画（120px）+ 欢迎文字
- 底部：分页指示器 + 开始按钮

**Page Indicator**
```css
/* Active */
width: 20px; height: 8px;
border-radius: 9999px;
background: #3d2b1f;
border: 2px solid #3d2b1f;

/* Inactive */
width: 8px; height: 8px;
border-radius: 50%;
background: transparent;
border: 2px solid #3d2b1f;
```

### 13.4 Empty State (空状态)

```css
background: transparent;
text-align: center;
padding: 48px 24px;

/* Icon */
width: 80px; height: 80px;
border-radius: 50%;
background: #f9d5c0;
border: 2px solid #3d2b1f;
box-shadow: 3px 3px 0 #3d2b1f;

/* Text */
color: #7a6b5f;
font-size: 14px;
font-family: comic;
```

### 13.5 Loading State (加载状态)

```css
/* Skeleton Card */
background: linear-gradient(
    90deg,
    #fdf6e3 0%,
    #f5ecd0 50%,
    #fdf6e3 100%
);
background-size: 200% 100%;
animation: shimmer 1.5s infinite;
border-radius: 16px;
border: 1.5px solid #3d2b1f;

@keyframes shimmer {
    0% { background-position: -200% 0; }
    100% { background-position: 200% 0; }
}
```

### 13.6 Toast / Notification (轻提示)

```css
background: #3d2b1f;
color: #fdf6e3;
border-radius: 16px;
padding: 12px 20px;
font-size: 13px;
font-family: comic;
box-shadow: 3px 3px 0 rgba(61,43,31,0.3);
position: fixed;
bottom: 100px;
left: 50%;
transform: translateX(-50%);
animation: slideUp 0.25s ease-out;
```

---

## 14. Tailwind Config Reference (Tailwind 配置参考)

```javascript
tailwind.config = {
    theme: {
        extend: {
            colors: {
                peach: {
                    bg: '#f4b89a',
                    light: '#f8c4a8',
                    soft: '#f9d5c0',
                    muted: '#e8a88a',
                },
                cream: {
                    bg: '#f9f1d8',
                    card: '#fdf6e3',
                    surface: '#fffbf0',
                    warm: '#f5ecd0',
                },
                ink: {
                    DEFAULT: '#3d2b1f',
                    muted: '#7a6b5f',
                    faint: 'rgba(61,43,31,0.4)',
                    ghost: 'rgba(61,43,31,0.15)',
                },
                border: {
                    passive: '#e8dcc8',
                    interactive: 'rgba(61,43,31,0.4)',
                },
                accent: {
                    pink: '#f4a8a8',
                    sage: '#a8c4a0',
                    sky: '#a8c8e8',
                    lavender: '#c8b8d8',
                    coral: '#e89080',
                }
            },
            fontFamily: {
                sans: ['Inter', 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', 'Hiragino Sans GB', 'system-ui', 'sans-serif'],
                display: ['Ma Shan Zheng', 'ZCOOL KuaiLe', 'Noto Sans SC', 'PingFang SC', 'cursive'],
                hand: ['Ma Shan Zheng', 'ZCOOL KuaiLe', 'Noto Sans SC', 'PingFang SC', 'cursive'],
                comic: ['Ma Shan Zheng', 'ZCOOL KuaiLe', 'Noto Sans SC', 'PingFang SC', 'cursive'],
            },
            borderRadius: {
                button: '10px',
                card: '16px',
                pill: '9999px',
            },
            boxShadow: {
                'dark-btn': '3px 3px 0 #3d2b1f',
                card: '3px 3px 0 #3d2b1f',
                float: '4px 4px 0 #3d2b1f',
            }
        }
    }
}
```

---

## 15. CSS Custom Properties (CSS 变量参考)

```css
:root {
    /* Primary */
    --color-peach-bg: #f4b89a;
    --color-peach-light: #f8c4a8;
    --color-peach-soft: #f9d5c0;
    --color-peach-muted: #e8a88a;

    /* Cream */
    --color-cream-bg: #f9f1d8;
    --color-cream-card: #fdf6e3;
    --color-cream-surface: #fffbf0;
    --color-cream-warm: #f5ecd0;

    /* Ink */
    --color-ink: #3d2b1f;
    --color-ink-muted: #7a6b5f;
    --color-ink-faint: rgba(61,43,31,0.4);
    --color-ink-ghost: rgba(61,43,31,0.15);

    /* Accent */
    --color-accent-pink: #f4a8a8;
    --color-accent-sage: #a8c4a0;
    --color-accent-sky: #a8c8e8;
    --color-accent-lavender: #c8b8d8;
    --color-accent-coral: #e89080;

    /* Border */
    --color-border-passive: #e8dcc8;
    --color-border-interactive: rgba(61,43,31,0.4);

    /* Shadows */
    --shadow-card: 3px 3px 0 #3d2b1f;
    --shadow-float: 4px 4px 0 #3d2b1f;
    --shadow-subtle: 1px 1px 0 rgba(61,43,31,0.2);
    --shadow-frame: 1px 1px 0 #3d2b1f, 0 25px 80px rgba(61,43,31,0.15);

    /* Radius */
    --radius-button: 10px;
    --radius-card: 16px;
    --radius-pill: 9999px;

    /* Animation */
    --transition-fast: 0.15s ease;
    --transition-base: 0.25s ease-out;
    --transition-panel: 0.3s cubic-bezier(0.32, 0.72, 0, 1);
}
```

---

## 16. Usage Guidelines (使用规范)

### 16.1 Do's (推荐做法)
- 保持所有边框使用 `ink` 色（#3d2b1f），维持手账线条的一致性
- 阴影统一使用无模糊的硬阴影风格
- 标题类文字使用 `font-display`（马善政楷书），正文消息使用 `font-hand`（马善政楷书），代码和英文使用 `font-sans`
- 交互元素需有 `active:scale-0.96` 的按压反馈
- 所有图片/图标容器保持手绘圆角风格

### 16.2 Don'ts (避免做法)
- 不要使用圆角过大的按钮（保持 10px）
- 不要使用带模糊值的 box-shadow
- 不要使用纯黑色（#000000），统一使用 ink 色
- 避免在书法字体中使用过小字号（最小 12px）
- 不要省略 focus-visible 的可访问性样式

### 16.3 Responsive Rules (响应式规则)
- 手机框架在 ≤420px 时全屏显示，取消圆角和阴影
- 输入框最大高度限制为 120px
- 消息气泡最大宽度为 85%
- 底部面板最大高度为 60-70%

---

## 17. Asset Specifications (素材规范)

### 17.1 Icons
- 使用 **Lucide Icons** 图标库
- 默认尺寸：20px（导航）、16px（内联）、14px（小图标）
- 线宽：2px（标准）、2.5px（强调）
- 颜色：ink（#3d2b1f）或 ink-muted（#7a6b5f）

### 17.2 Pet Avatar (咪兔 / Mitu)
- **设计概念**：方形小兔子，参考可爱方形动物 doodle 风格
- **身体**：粉色圆角方形脸 (`#f4a8a8`)，奶油色圆形底 (`#f9d5c0`)
- **耳朵**：两只椭圆形长耳朵（蜜桃色 `#f4b89a`），带内耳高光
- **面部特征**：
  - 圆点大眼 + 白色高光（灵动可爱）
  - 两侧淡珊瑚色腮红 (`#e89080`，50% 透明度)
  - 简约微笑弧线嘴巴
  - 两侧细线胡须（40% 透明度）
- **描边**：统一 `#3d2b1f` 墨水色粗描边（stroke-width: `size * 0.06`）
- **动画**：gentleBounce 呼吸动效
- **尺寸**：小 24px / 中 32px / 大 56px / 超大 80px
- **实现**：纯 SVG 绘制，响应式尺寸（所有坐标基于 `size` 动态计算）

### 17.3 AI Avatar
- 月亮/星星 SVG 图标
- 奶油色背景 + 墨水边框
- 统一尺寸：32px

---

*Document Version: 1.2*
*Last Updated: 2026-06-25*
*Theme: Warm Journal (手账暖调) · 咪兔*
