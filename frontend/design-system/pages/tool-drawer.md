# Tool Drawer — 页面设计覆盖

> 工具箱底部面板，展示可用工具的快捷入口。本文件中的规则覆盖 MASTER.md 中的同名规则。

---

## 页面定位

工具快捷面板，以网格方式展示 9 个常用 AI 工具 + 1 个演示按钮。从底部弹出，类似系统 Bottom Sheet 模式。

---

## 面板结构

```
bottom-sheet: 底部弹出 | bg: cream-bg | radius: 20px 顶部圆角
max-height: 70% | border: 1.5px solid ink (底部无 border)
过渡: 300ms cubic-bezier(0.32, 0.72, 0, 1)
```

---

## 特殊组件

### 工具网格
```
grid-cols-3 | gap-3 | 共 9 个工具
```

### 工具格子
```
flex-col items-center | gap-2 | p-4
bg: cream-card | border: 2px ink | radius: card | shadow: card
hover: bg cream-warm | active: scale(0.95) | cursor-pointer
```

内部结构：
1. 图标容器: `w-10 h-10` | `rounded-button` | `bg-cream-warm` | `border-2 ink` | 居中
2. 工具名称: 12px comic bold ink

### 演示按钮
```
btn-light | w-full | mt-4 | py-2.5 | 12px comic
内含 sparkle SVG (14px) + 文字
disabled: opacity-45 + cursor-not-allowed
```

---

## 与 Master 的差异

| 规则 | Master 默认 | Tool Drawer 覆盖 |
|---|---|---|
| 面板 border | 全描边 | `border-bottom: none` |
| 面板过渡 | 250ms | 300ms（更高面板需要更长过渡） |
| 工具格 shadow | shadow-card | shadow-card（一致） |
