# Companion Panel — 页面设计覆盖

> 宠物伙伴面板（咪兔），从底部弹出的情感化交互面板。本文件中的规则覆盖 MASTER.md 中的同名规则。

---

## 页面定位

非核心功能面板，承担情感连接和轻量状态展示。是「编程伙伴」概念的核心载体，需要传递温暖、可爱、有趣的感觉。

---

## 面板结构

```
companion-panel: 底部弹出 | bg: cream-bg | border-t: 2px solid ink
max-height: 60% | radius: 20px 顶部圆角
过渡: 250ms cubic-bezier(0.32, 0.72, 0, 1)
```

---

## 特殊组件

### 拖拽手柄
```
w-9 h-1.5 rounded-full | bg: ink/25 | border: 1px ink/20
居中于面板顶部
```

### 宠物头像区
```
头像: w-14 h-14 rounded-full | bg: accent-pink | border: 2px ink | shadow: card
内含 PetAvatar SVG (size=36)
名称: 18px display bold ink
副标题: 12px comic ink-muted ("你的编程小助手")
```

### 宠物反应气泡
```
animate: pop-in (200ms) | bg: rgba(244,168,168,0.3)
border: 2px solid ink | radius: card | padding: px-4 py-3
文字: 12px comic bold ink
自动消失: 2000ms setTimeout
```

### 心情卡片
```
bg: cream-card | border: 2px ink | radius: card | padding: p-4 | shadow: card
标题: 13px display bold uppercase + 爱心 SVG (accent-pink fill)
进度条: hand-progress (height: 12px, border: 2px ink, bg: cream-warm)
         fill: accent-pink | rounded-pill
状态文字: 12px comic bold ink
```

### 今日统计卡片
```
bg: cream-card | border: 2px ink | radius: card | padding: p-4 | shadow: card
标题: 13px display bold uppercase + 活动 SVG (accent-sage stroke)
数字: 20px display bold ink
标签: 10px comic muted
三列等分 grid (grid-cols-3)
```

### 互动按钮组
```
三按钮横排 (flex gap-2)
每个: flex-1 | py-2.5 | bg: cream-card | border: 2px ink | radius: button
font: 12px comic bold ink | shadow: card
hover: bg cream-warm | active: scale(0.95)
```

---

## 动画

| 元素 | 动画 |
|---|---|
| 面板弹出 | `translateY(100%) → translateY(0)`, 250ms cubic-bezier |
| 宠物反应 | `pop-in` (scale 0.8→1 + fade), 200ms |
| 反应消失 | 无动画，直接移除（setTimeout 2s） |

---

## 与 Master 的差异

| 规则 | Master 默认 | Companion 覆盖 |
|---|---|---|
| 面板 border | 全描边 | 仅 `border-t-2`（顶部） |
| 圆角 | card=16px | 面板顶部 20px |
| 进度条 | 无标准定义 | 自定义 `.hand-progress` 组件 |
| 按钮 active | scale(0.96) | scale(0.95)（更活泼） |
