# ⏱ 时矩 — StudyTimer

一款轻量级 Android 学习计时与自律管理应用。

**零第三方依赖** · 纯 Kotlin · API 26+ · SharedPreferences 本地存储

## 功能

### 🕐 计时
- 正计时 & 倒计时
- 全屏专注模式
- 时间戳差值计算，切后台不中断
- 完成后自动保存记录

### 📊 统计
- 日 / 周 / 月 学习时长统计
- 扇形图 & 折线图可视化
- 学习排行榜
- 日历区间选择

### ✅ 待办
- 添加 / 完成 / 删除待办事项
- 设置截止日期，自动计算剩余天数
- 每日重复待办，跨日自动重置

### 👤 我的
- 自定义背景图
- 格言管理（内置 20 条 + 增删 + 轮换显示）
- 自律打卡（起床 / 睡觉 / 自定义习惯 + 打卡历史）

### 📚 科目管理
- 科目集内增删科目
- Spinner 快速切换

## 截图

> （待补充）

## 项目结构

```
app/
├── src/main/java/com/example/studytimer/
│   ├── MainActivity.kt           # 主界面 + 底部导航
│   ├── TimerFragment.kt           # 计时页面
│   ├── TimerRunningActivity.kt    # 正计时全屏
│   ├── CountdownRunningActivity.kt# 倒计时全屏
│   ├── StatsFragment.kt           # 统计页面
│   ├── TodoFragment.kt            # 待办页面
│   ├── ProfileFragment.kt         # 我的页面
│   ├── PieChartView.kt            # 扇形图自定义 View
│   ├── LineChartView.kt           # 折线图自定义 View
│   ├── SubjectData.kt             # 科目数据管理
│   ├── StorageHelper.kt           # SharedPreferences 工具
│   ├── TodoStorage.kt             # 待办持久化
│   ├── MottoStorage.kt            # 格言持久化
│   ├── ProfileStorage.kt          # 个人设置持久化
│   ├── CheckInStorage.kt          # 打卡记录持久化
│   ├── WhiteNoiseEngine.kt        # 白噪音引擎
│   └── WhiteNoiseStorage.kt       # 白噪音持久化
└── src/main/res/                  # 布局 & 资源
```

## 构建

用 Android Studio 打开项目根目录即可：

```bash
./gradlew assembleDebug
```

APK 输出位置：`app/build/outputs/apk/debug/`

## 技术栈

| 项目 | 说明 |
|------|------|
| 语言 | Kotlin |
| 最低 API | 26 (Android 8.0) |
| 第三方库 | 无（零依赖） |
| 本地存储 | SharedPreferences |
| 图表 | 自定义 Canvas 绘制 |
| IDE | Android Studio |
| 构建 | Gradle KTS |

## 许可证

MIT License
