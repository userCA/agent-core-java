# HistoryScreen — 页面设计覆盖

> 日记列表页面，历史对话的索引与入口。本文件中的规则覆盖 MASTER.md 中的同名规则。

---

## 页面定位

以「手帐日记本」为隐喻，展示历史对话记录。视觉上强调时间线和条目卡片，给用户翻阅日记的感觉。

---

## 布局差异

| 区域 | 规则 |
|---|---|
| Header | 与 ChatScreen 一致：`pt-12`, `backdrop-blur-sm`, `border-b-2 border-border-passive` |
| 内容区 | `px-4 py-4 space-y-3`，自定义滚动条 |
| 无底部栏 | 与 ChatScreen 不同，History 没有固定底部输入区 |

---

## 特殊组件

### 搜索框
```
bg: cream-card | border: 2px solid ink | radius: button
左侧搜索图标绝对定位 (pl-10)
placeholder: ink-faint | font: comic
```

### 月份分隔线
```
font-display | 13px | uppercase | tracking-wider | ink-muted
右侧跟随 dashed-divider（虚线）
```

### 日记卡片 `.journal-card`
```
bg: cream-card | border: 2px solid ink | radius: card
padding: p-4 | shadow: card
active: scale(0.98) + shadow 缩小至 1px 1px 0
```

卡片内部层级：
1. 日期 (12px, display, uppercase, muted) + 时间 (11px, comic, muted)
2. 标题 (15px, display, bold, ink)
3. 描述 (12px, comic, muted, line-clamp-2)
4. 标签药丸：`bg-cream-warm` + `rounded-pill` + `border border-ink` + 10px comic muted

---

## 交互

- 整张卡片可点击 (`cursor-pointer`)
- hover: 无明显变化（依赖 active 态反馈）
- active: `scale(0.98)` + 阴影收缩 → 物理按压感
- 从 Chat 页面左滑进入（`screen-right → screen-active`）

---

## 与 Master 的差异

| 规则 | Master 默认 | History 覆盖 |
|---|---|---|
| 卡片 active | scale(0.96) | scale(0.98)（更柔和） |
| 分隔线 | 水平虚线居中 | 左对齐标签 + 右侧虚线 |
| 底部区域 | 无 | 无固定底部栏 |
