# 计划书：待办页面升级（待办集 + 内置学习目标）

## 一、数据模型变更

### 1.1 TodoItem 扩展

```kotlin
data class TodoItem(
    val id: Long,
    val content: String,
    val isDone: Boolean = false,
    val date: String = "",
    val isDaily: Boolean = false,
    val dueDate: String = "",
    // ---- 新增字段 ----
    val groupId: Long = 0,           // 所属待办集ID，0=不属于任何集
    val isBuiltIn: Boolean = false,  // 是否内置项（不可删除）
    val targetHours: Float = 0f,     // 内置学习目标：目标小时数（仅 groupId==SELF 时有效）
    val subjectTargets: List<SubjectTarget> = emptyList()  // 子科目目标
)

// 科目目标
data class SubjectTarget(
    val groupName: String,    // 科目集名，如"数学"
    val subjectName: String,  // 科目名，如"高数"（空=整个科目集）
    val targetMinutes: Int    // 目标分钟数
)
```

### 1.2 待办集的设计

待办集本身也是一条 TodoItem，通过特殊字段标识：
- `id < 0` 或新增 `itemType: "group"` 字段区分待办集与普通待办
- 待办集的 `isDone` 由其下属所有待办的完成状态自动决定（不存储，实时计算）

建议方案：**待办集用正数 id，普通待办的 `groupId` 指向所属集的 id**

```
TodoItem(id=100, content="考研冲刺", itemType="group", isDaily=true, dueDate="2026-12-25")
TodoItem(id=101, content="背单词", groupId=100, isDaily=true)
TodoItem(id=102, content="做真题", groupId=100, dueDate="2026-06-15")
TodoItem(id=103, content="买菜", groupId=0)  // 不属于任何集
```

### 1.3 内置学习目标

```kotlin
data class DailyStudyGoal(
    val targetHours: Float,              // 总目标小时数
    val subjectTargets: List<SubjectTarget>,  // 科目目标（可选）
    val isEnabled: Boolean = true
)
```

存储在 SharedPreferences（`study_timer_profile`），独立于 TodoItem。

---

## 二、TodoStorage 改造

### 2.1 新增方法

```kotlin
// ---- 待办集 ----
fun addGroup(context: Context, item: TodoItem)          // 新增待办集
fun getAllGroups(context: Context): List<TodoItem>       // 获取所有待办集
fun getTodosByGroup(context: Context, groupId: Long): List<TodoItem>  // 获取集内待办
fun isGroupDone(context: Context, groupId: Long): Boolean // 集是否全部完成

// ---- 内置学习目标 ----
fun getStudyGoal(context: Context): DailyStudyGoal?
fun saveStudyGoal(context: Context, goal: DailyStudyGoal)
fun checkStudyGoalCompletion(context: Context, records: List<TimerRecord>): Boolean
fun getStudyGoalProgress(context: Context, records: List<TimerRecord>): StudyProgress

// ---- 自动完成 ----
fun markGroupDoneIfNeeded(context: Context, groupId: Long)
fun checkAndUpdateBuiltInGoals(context: Context)  // 检查所有内置目标
```

### 2.2 getAll() 改造

现有的 `getAll()` 按 `isDone` 排序。改造后：
- 返回扁平列表，但按"待办集 → 集内待办 → 独立待办"分组排列
- 待办集显示在前面，展开显示子项
- 已完成的待办集（所有子项完成）排在后面

---

## 三、TodoFragment UI 改造

### 3.1 FAB 弹出菜单

点击 FAB 后弹出 BottomSheet 或 AlertDialog，两个选项：
```
➕ 新增待办
📁 新增待办集
```

### 3.2 新增待办集对话框

```
标题：新增待办集
[输入框：待办集名称]
☐ 每日重复
📅 设置截止日期
[确定] [取消]
```

### 3.3 新增待办对话框（改造）

在现有对话框基础上新增：
```
标题：添加待办
[输入框：待办内容]
📁 所属待办集：[下拉选择 / 无]
☐ 每日重复
📅 设置截止日期
[确定] [取消]
```

### 3.4 待办集的显示

待办集在列表中的显示样式：
```
┌─────────────────────────────────┐
│ ▼ 考研冲刺          截止2026-12-25 │  ← 待办集标题（可折叠）
│   ┌─────────────────────────┐    │
│   │ ☐ 背单词    每日          │    │  ← 子待办（缩进显示）
│   │ ☑ 做真题    截止06-15     │    │
│   └─────────────────────────┘    │
└─────────────────────────────────┘
```

完成状态：
- 待办集标题右侧显示进度：`2/5 已完成`
- 全部完成时，待办集整体显示完成样式（灰色 + 删除线）

### 3.5 内置学习目标的显示

特殊样式，与普通待办集区分：
```
┌─────────────────────────────────┐
│ 📚 今日学习 3 小时     1h30m/3h │  ← 进度条
│   数学·高数    ████░░░  45min/2h │  ← 科目进度
│   英语·单词    ██░░░░░  30min/1h │
│   (自动完成，无需手动勾选)        │
└─────────────────────────────────┘
```

---

## 四、Timer ↔ Todo 桥接

### 4.1 桥接逻辑

在 `TimerRunningActivity.saveAndFinish()` 和 `CountdownRunningActivity.saveAndFinish()` 中，保存记录后调用：

```kotlin
// 保存计时记录后
StorageHelper.saveRecord(this, record)

// 桥接：检查内置学习目标
TodoStorage.checkAndUpdateBuiltInGoals(this)
```

### 4.2 checkAndUpdateBuiltInGoals() 逻辑

```kotlin
fun checkAndUpdateBuiltInGoals(context: Context) {
    val goal = getStudyGoal(context) ?: return
    if (!goal.isEnabled) return

    // 获取今日所有计时记录
    val today = SimpleDateFormat("yyyy-MM-dd").format(Date())
    val todayRecords = StorageHelper.getAllRecords(context).filter { it.date == today }

    // 检查总时长
    val totalMinutes = todayRecords.sumOf { it.durationSeconds } / 60
    val totalMet = totalMinutes >= goal.targetHours * 60

    // 检查各科目目标
    val subjectsMet = goal.subjectTargets.all { target ->
        val matched = todayRecords.filter { record ->
            record.subjectGroup == target.groupName &&
            (target.subjectName.isEmpty() || record.subject == target.subjectName)
        }
        matched.sumOf { it.durationSeconds } / 60 >= target.targetMinutes
    }

    // 全部满足 → 标记今日学习目标完成
    if (totalMet && subjectsMet) {
        markStudyGoalDone(context, today)
    }
}
```

### 4.3 进度数据结构

```kotlin
data class StudyProgress(
    val totalMinutes: Long,        // 今日已学总分钟
    val targetMinutes: Long,       // 目标总分钟
    val subjectProgress: List<SubjectProgress>  // 各科目进度
)

data class SubjectProgress(
    val groupName: String,
    val subjectName: String,
    val currentMinutes: Long,
    val targetMinutes: Long,
    val isMet: Boolean
)
```

---

## 五、每日重置

扩展现有 `resetDailyIfNeeded()`：

```kotlin
fun resetDailyIfNeeded(context: Context) {
    val today = SimpleDateFormat("yyyy-MM-dd").format(Date())
    val all = getAll(context).toMutableList()
    var changed = false

    for (i in all.indices) {
        val item = all[i]
        // 普通每日待办重置
        if (item.isDaily && item.isDone && item.date != today && !item.isBuiltIn) {
            all[i] = item.copy(isDone = false)
            changed = true
        }
    }

    // 内置学习目标：每日重置完成状态（保留配置）
    resetBuiltInStudyGoalIfNeeded(context, today)

    if (changed) saveAll(context, all)
}
```

---

## 六、实现顺序

| 阶段 | 内容 | 涉及文件 |
|------|------|----------|
| P1 | TodoItem 数据模型扩展 + TodoStorage 序列化改造 | `TodoStorage.kt`, `TodoItem` |
| P2 | 待办集 CRUD（新增/删除/获取子项/完成判定） | `TodoStorage.kt` |
| P3 | FAB 弹出菜单 + 新增待办集对话框 + 待办归属选择 | `TodoFragment.kt`, `fragment_todo.xml` |
| P4 | 待办集 UI 渲染（折叠/展开/进度/完成样式） | `TodoFragment.kt` |
| P5 | 内置学习目标数据存储 + 配置对话框 | `TodoStorage.kt`, `TodoFragment.kt` |
| P6 | Timer → Todo 桥接（计时保存后自动检查） | `TimerRunningActivity.kt`, `CountdownRunningActivity.kt`, `TodoStorage.kt` |
| P7 | 内置学习目标 UI（进度条/自动完成动画） | `TodoFragment.kt` |
| P8 | 每日重置扩展 | `TodoStorage.kt` |

---

## 七、风险点

1. **性能**：`getAll()` 现在每次都要计算待办集完成状态，数据量大时可能卡。建议缓存或懒加载。
2. **数据迁移**：旧版 TodoItem 没有 `groupId` 字段，需要兼容（默认 0 = 不属于任何集）。
3. **自动完成的及时性**：只有在计时结束时触发检查，如果用户在计时运行中打开待办页面，进度不会实时更新。需要额外做定时刷新或在 onResume 时检查。
4. **内置目标的科目选择 UI**：需要从 `SubjectData` 获取科目列表，UI 交互较复杂（多选 + 设置时长）。
