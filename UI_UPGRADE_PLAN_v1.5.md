# 时矩 v1.5 高级感 UI 美化方案

> **基线版本**: v1.0 (versionCode=1)
> **目标**: 在保持「雾蓝主色 `#5B9BD5` + 暖奶油底 `#F8F5F0`」设计语言的前提下，全面提升视觉品质感，对标 iOS 级别精品应用。
> **分析日期**: 2026-06-04

---

## 一、当前 UI 现状评估

### ✅ 已有优势（保留并强化）
| 维度 | 现状 |
|------|------|
| 设计语言 | 玻璃拟态 + 暖奶油底，方向正确 |
| 色彩体系 | `colors.xml` 四级文字层次 + 7 种图表色，体系完整 |
| 动态背景 | `DynamicBackgroundView` 三色暖漂移 + Android 12+ 真毛玻璃 |
| 自定义控件 | `CircularTimerView` 渐变弧环、`PieChartView` 甜甜圈图、`LineChartView` 折线图 |
| 卡片系统 | `card_glass` / `card_elevated` 双层阴影 + 顶部高光，有层次感 |
| 动画 | 进入淡入、弹性缩放、环绘制动画，覆盖关键路径 |

### ⚠️ 可提升点（美化重点）
| 问题 | 位置 | 影响 |
|------|------|------|
| **计时运行页背景硬编码** | `activity_timer_running.xml` 背景 `#FFFDFBF7` 而非动态背景 | 视觉断裂感 |
| **倒计时页同上** | `activity_countdown_running.xml` 同样硬编码 | 同上 |
| **Emoji 图标** | 待办/个人页菜单用 emoji（🖼💬✅🌧🎵🔋）代替图标 | 低质感 |
| **待办项纯代码构建** | `TodoFragment.kt` 中所有列表项 View 用纯代码手写 | 不够精致 |
| **统计行硬编码色值** | `StatsFragment.kt` 中 `#FF333333` `#FFF0F0F0` 等硬编码 | 不统一 |
| **Chip 样式简陋** | `TimerFragment.kt` Chip 背景 `#14FFFFFF`，无选中态渐变 | 缺少品质 |
| **分隔线过粗** | Profile 页 `0.5dp` divider 可进一步打磨 | 略显粗糙 |
| **底部导航无文字** | 只有图标，新用户认知成本高 | 可读性 |
| **按钮无自定义样式** | 计时运行页 `Button` 使用系统默认 Material 样式 | 不协调 |
| **图表无交互动效** | 扇形图/折线图只有展示，无 touch 反馈 | 缺少高级感 |

---

## 二、美化方案分阶段规划

### 🏗 阶段一：色彩与视觉基底升级

> **目标**: 让整体色调更柔和、更有层次感，建立高级感基础
> **预估总工时**: 3-4h

#### 1.1 色彩微调 — 更低饱和度的高级配色
| 优先级 | 变更 | 当前值 | 建议值 | 工时 | 依赖 |
|--------|------|--------|--------|------|------|
| 🔴 High | 主色饱和度降低 | `#5B9BD5` | `#6BA4D1` (降低饱和度 8%) | 30min | 无 |
| 🔴 High | 暖奶油底加微粉 | `#F8F5F0` | `#FAF6F1` (提亮 1% + 微暖) | 15min | 无 |
| 🟡 Medium | 图表色系统一到莫兰迪色 | 7 色分散 | 莫兰迪蓝灰紫绿体系 | 30min | 无 |
| 🟡 Medium | 暗色模式主色柔和化 | `#8BB8D9` | `#92BFD8` | 15min | 无 |

**具体操作**: 修改 `res/values/colors.xml`、`res/values-night/themes.xml`

#### 1.2 字体排版系统化
| 优先级 | 变更 | 说明 | 工时 | 依赖 |
|--------|------|------|------|------|
| 🔴 High | 引入 `app:fontFamily` 统一标题字重 | 页面标题 32sp 用 `sans-serif-medium`，正文 15sp 用 `sans-serif`，辅助 13sp 用 `sans-serif-light` | 1h | 无 |
| 🟡 Medium | 统一 `letterSpacing` 规范 | 标题 `0.02`、section label `0.08`、正文 `0` | 30min | 无 |

**具体操作**: 新建 `res/values/dimens.xml` 统一间距字号常量，各 layout 引用

#### 1.3 圆角体系统一
| 优先级 | 变更 | 说明 | 工时 | 依赖 |
|--------|------|------|------|------|
| 🟡 Medium | 卡片圆角统一为 `16dp` (现状 20dp) | 对标 iOS 标准卡片圆角 | 30min | 无 |
| 🟢 Low | 按钮圆角统一 `24dp` (现状 32dp) | 更紧凑的药丸感 | 15min | 无 |

---

### 🏗 阶段二：卡片与组件高级化

> **目标**: 每一个可交互元素都具备高级触感
> **预估总工时**: 6-8h

#### 2.1 计时运行页背景升级 🔴 High
| 优先级 | 变更 | 说明 | 工时 | 依赖 |
|--------|------|------|------|------|
| 🔴 High | 替换硬编码背景为 `DynamicBackgroundView` | `activity_timer_running.xml` 和 `activity_countdown_running.xml` 中 `#FFFDFBF7` → FrameLayout 包裹 DynamicBackgroundView | 1.5h | 无 |
| 🔴 High | 运行页计时环增加光晕效果 | `CircularTimerView.kt` 绘制外圈发光模糊（Aurora 效果） | 2h | 无 |

**当前问题**: 计时运行页是用户停留时间最长的页面，但背景是纯色，与主页的动态背景形成断裂感。

#### 2.2 Chip 选中态重设计 🔴 High
| 优先级 | 变更 | 说明 | 工时 | 依赖 |
|--------|------|------|------|------|
| 🔴 High | 选中态改为渐变填充背景 + 白色文字 | 当前只有描边变蓝，视觉反馈弱。改为 `btn_pill_primary` 渐变蓝填充 + white 文字 | 1h | 1.1 |

**具体修改**: `TimerFragment.kt` → `createChip()` 方法，选中态 `chipBackgroundColor` 改为雾蓝→靛紫渐变（代码构建 `GradientDrawable`）

#### 2.3 计时运行页按钮样式化 🟡 Medium
| 优先级 | 变更 | 说明 | 工时 | 依赖 |
|--------|------|------|------|------|
| 🟡 Medium | 暂停/继续按钮：玻璃描边药丸样式 | 替换系统 Button 为自定义样式 | 30min | 无 |
| 🟡 Medium | 结束按钮：`btn_pill_danger` 样式 | 红色药丸 + 涟漪 | 30min | 无 |

#### 2.4 底部导航升级 🟡 Medium
| 优先级 | 变更 | 说明 | 工时 | 依赖 |
|--------|------|------|------|------|
| 🟡 Medium | 恢复文字标签 + 缩小图标 | `labelVisibilityMode="labeled"`，`itemIconSize=20dp` | 15min | 无 |
| 🟡 Medium | 选中态增加图标颜色过渡动画 | 自定义 `BottomNavigationView` item 染色动画 | 1h | 无 |
| 🟢 Low | 导航栏增加顶部模糊边界线 | 0.5dp 渐变分割线 | 15min | 无 |

**具体修改**: `activity_main.xml`、`res/color/nav_icon_color.xml`

---

### 🏗 阶段三：列表与内容区域打磨

> **目标**: 待办列表、统计列表等信息密集区域的精致化
> **预估总工时**: 4-5h

#### 3.1 待办项样式重做 🔴 High
| 优先级 | 变更 | 说明 | 工时 | 依赖 |
|--------|------|------|------|------|
| 🔴 High | Todo 条目改为玻璃卡片 + 左滑删除 | 每项独立 `card_glass` 容器，增加分割间距 | 2h | 2.1 |
| 🔴 High | Checkbox 改为自定义圆形 + 勾选动画 | 使用自定义 drawable 带 scale 动画 | 1.5h | 无 |
| 🟡 Medium | 待办集折叠箭头动画 | 展开/收起旋转 180° `ObjectAnimator` | 30min | 无 |

**具体修改**: `TodoFragment.kt` → `createTodoItem()` 等方法，建议提取为独立 TodoItemView 自定义 View

#### 3.2 统计列表高级化 🟡 Medium
| 优先级 | 变更 | 说明 | 工时 | 依赖 |
|--------|------|------|------|------|
| 🟡 Medium | 科目统计行改用玻璃卡片 | 每行 `card_glass` 包裹，进度条改用 `progress_bar_goal` 样式 | 1h | 1.1 |
| 🟡 Medium | 消除硬编码色值 | `StatsFragment.kt` 中 `#FF333333` 等替换为 `@color/text_primary` 等资源引用 | 30min | 无 |
| 🟢 Low | 空状态插图 | 无记录时显示自定义 SVG 插图而非纯文字 | 1h | 无 |

#### 3.3 个人页 Emoji → 矢量图标 🟡 Medium
| 优先级 | 变更 | 说明 | 工时 | 依赖 |
|--------|------|------|------|------|
| 🟡 Medium | 菜单图标替换为 SVG/Vector | 🖼💬✅🌧🎵🔋 → `ic_custom_bg.xml` `ic_motto.xml` 等矢量图标 | 2h | 无 |

**具体修改**: 新增 `res/drawable/ic_*.xml` 矢量资源，修改 `fragment_profile.xml` 中对应 TextView

---

### 🏗 阶段四：动画与微交互增强

> **目标**: 让每一个操作都有触感反馈，提升「活」的感觉
> **预估总工时**: 4-5h

#### 4.1 计时环光效与呼吸 🔴 High
| 优先级 | 变更 | 说明 | 工时 | 依赖 |
|--------|------|------|------|------|
| 🔴 High | 运行中环增加微呼吸动画 | `CircularTimerView.kt` 弧线端点微微脉冲 2.5% 透明度 | 1.5h | 2.1 |
| 🟡 Medium | 完成时环彩虹庆祝动画 | 计时结束时环颜色循环一圈 + 缩放弹跳 | 1.5h | 2.1 |

#### 4.2 页面转场动画升级 🟡 Medium
| 优先级 | 变更 | 说明 | 工时 | 依赖 |
|--------|------|------|------|------|
| 🟡 Medium | Fragment 切换增加共享元素动画 | 底部导航切换时标题共享 + 淡入淡出 | 1h | 无 |
| 🟡 Medium | 计时启动页 → 运行页 Shared Element Transition | 科目名/时间数字过渡 | 1h | 无 |

**具体修改**: `res/anim/fragment_enter.xml` 等、`MainActivity.kt`

#### 4.3 图表交互反馈 🟢 Low
| 优先级 | 变更 | 说明 | 工时 | 依赖 |
|--------|------|------|------|------|
| 🟢 Low | 扇形图 Touch 高亮 | 手指按住扇区时该区块外弹 4dp + 阴影加深 | 1.5h | 无 |
| 🟢 Low | 折线图 Tooltip | 点击数据点显示具体时长气泡 | 1.5h | 无 |

---

### 🏗 阶段五：细节打磨与暗色模式

> **目标**: 像素级细节完善，暗色模式同步优化
> **预估总工时**: 2-3h

#### 5.1 暗色模式专项优化 🟡 Medium
| 优先级 | 变更 | 说明 | 工时 | 依赖 |
|--------|------|------|------|------|
| 🟡 Medium | 暗色玻璃卡片适配 | `card_glass` 在暗色模式下使用深色半透明 + 微光描边 | 1h | 1.1 |
| 🟡 Medium | 暗色模式 DynamicBackgroundView 色彩 | 三色漂移改为冷蓝紫调 | 30min | 无 |
| 🟡 Medium | 图表暗色适配 | 文字/轴线/网格色切换 | 30min | 1.1 |

**具体修改**: 新增 `res/drawable-night/card_glass.xml`、`DynamicBackgroundView.kt` 暗色分支

#### 5.2 最终润色 🟢 Low
| 优先级 | 变更 | 说明 | 工时 | 依赖 |
|--------|------|------|------|------|
| 🟢 Low | 启动屏 Splash | 使用 `SplashScreen API` (Android 12+) + 品牌渐变背景 | 1h | 1.1 |
| 🟢 Low | 版本号升至 v1.5 | `versionName = "1.5"`, `versionCode = 2` | 5min | 所有 |
| 🟢 Low | Toast 替换为 Snackbar | Material3 Snackbar 带操作按钮 | 30min | 无 |

---

## 三、实施依赖关系图

```
阶段一（色彩基底）
  ├── 1.1 色彩微调 ──────────────────┐
  ├── 1.2 字体排版 ──────────────────┤
  └── 1.3 圆角体系统 ────────────────┤
                                     ▼
阶段二（组件高级化）
  ├── 2.1 运行页背景升级 ← 1.1 ─────┤
  ├── 2.2 Chip 渐变选中态 ← 1.1 ────┤
  ├── 2.3 按钮样式化 ────────────────┤
  └── 2.4 底部导航升级 ──────────────┤
                                     ▼
阶段三（列表打磨）
  ├── 3.1 待办项重做 ← 2.1 ─────────┤
  ├── 3.2 统计列表高级化 ← 1.1 ─────┤
  └── 3.3 Emoji → 矢量图标 ─────────┤
                                     ▼
阶段四（动画增强）
  ├── 4.1 计时环光效 ← 2.1 ─────────┤
  ├── 4.2 转场动画 ─────────────────┤
  └── 4.3 图表交互 ─────────────────┤
                                     ▼
阶段五（收尾）
  ├── 5.1 暗色模式 ← 1.1 ──────────┤
  └── 5.2 版本号 + 润色 ← ALL ─────┘
```

---

## 四、变更影响评估

### 涉及文件清单

| 类别 | 文件 | 变更类型 |
|------|------|----------|
| **资源-颜色** | `res/values/colors.xml` | 修改（微调色值） |
| **资源-主题** | `res/values/themes.xml` | 修改（圆角/字号） |
| **资源-主题** | `res/values-night/themes.xml` | 修改（暗色适配） |
| **资源-尺寸** | `res/values/dimens.xml` | **新增**（统一尺寸常量） |
| **布局** | `activity_timer_running.xml` | 修改（添加动态背景） |
| **布局** | `activity_countdown_running.xml` | 修改（添加动态背景） |
| **布局** | `activity_main.xml` | 修改（底部导航文字） |
| **布局** | `fragment_profile.xml` | 修改（图标替换） |
| **布局** | `fragment_todo.xml` | 修改（列表样式） |
| **Drawable** | `card_glass.xml` / `card_elevated.xml` | 修改（圆角值） |
| **Drawable** | 新增 `ic_motto.xml` 等矢量图标 | **新增** |
| **Drawable** | 新增 `drawable-night/card_glass.xml` | **新增** |
| **Kotlin** | `DynamicBackgroundView.kt` | 修改（暗色模式分支） |
| **Kotlin** | `CircularTimerView.kt` | 修改（光晕+呼吸动画） |
| **Kotlin** | `TimerFragment.kt` | 修改（Chip 渐变选中态） |
| **Kotlin** | `TodoFragment.kt` | 修改（列表样式重做） |
| **Kotlin** | `StatsFragment.kt` | 修改（消除硬编码色） |
| **Kotlin** | `TimerRunningActivity.kt` | 修改（完成动画） |
| **Kotlin** | `PieChartView.kt` | 修改（Touch 高亮） |
| **Kotlin** | `LineChartView.kt` | 修改（Tooltip） |

### 风险与缓解

| 风险 | 概率 | 缓解措施 |
|------|------|----------|
| `DynamicBackgroundView` 在运行页导致性能下降 | 中 | 暂停/恢复时停止动画 `onDetachedFromWindow` 已处理；可加 `isShown` 守卫 |
| Chip 渐变背景在低端机卡顿 | 低 | `GradientDrawable` 为硬件加速友好型 |
| 暗色模式新增 drawable 文件遗漏 | 中 | 使用 `drawable-night/` 目录覆盖机制，逐项验证 |
| `versionCode` 升级后用户数据兼容 | 低 | 本次不涉及数据库 schema 变更 |

---

## 五、工时汇总

| 阶段 | 预估工时 | 可并行度 |
|------|----------|----------|
| 阶段一：色彩基底 | 3-4h | 高（3 项互相独立） |
| 阶段二：组件高级化 | 6-8h | 中（2.1↔2.2 依赖 1.1） |
| 阶段三：列表打磨 | 4-5h | 中 |
| 阶段四：动画增强 | 4-5h | 高 |
| 阶段五：收尾打磨 | 2-3h | 低（依赖全部） |
| **合计** | **19-25h** | — |

---

## 六、快速胜利清单 (Quick Wins)

> 以下变更 **15 分钟内** 可完成，**视觉提升显著**，建议优先实施：

1. ✏️ `colors.xml` 主色 `#5B9BD5` → `#6BA4D1` （降饱和度 → 高级感立即提升）
2. ✏️ `activity_timer_running.xml` 背景替换为 `@color/bg_page` + DynamicBackgroundView
3. ✏️ `activity_countdown_running.xml` 同上
4. ✏️ `activity_main.xml` BottomNavigationView 添加 `labelVisibilityMode="labeled"`
5. ✏️ `fragment_todo.xml` 标题字号 `26sp` → `32sp`，与其他页面统一
6. ✏️ `nav_icon_color.xml` 增加 `state_pressed` 态
7. ✏️ `StatsFragment.kt` 硬编码色值 → 资源引用

---

*方案完毕。建议按阶段顺序执行，每个阶段完成后进行 UI 走查确认再进入下一阶段。*
