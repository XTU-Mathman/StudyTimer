# Bug 修复计划：科目集"+ 新增"点击无效

## 问题定位

**文件**：`TimerFragment.kt` 第59-69行 + 第148行  
**根因**：`setOnCheckedStateChangeListener` 机制与 `isCheckable = false` 冲突

### 详细分析

```
第148行：chipGroup.addView(createChip("+ 新增").apply { isCheckable = false })
                                  ↑ isCheckable = false
```

```
第59行：chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
第60行：    if (checkedIds.isNotEmpty()) {  ← 只有选中状态变化时才进
第63行：        if (name == "+ 新增") {
第65行：            showAddGroupDialog()    ← 永远不会执行到这里
```

**核心逻辑**：

1. `setOnCheckedStateChangeListener` 是 **选中状态变化** 监听器
2. "+ 新增" Chip 设置了 `isCheckable = false`（为了让它不参与选中状态）
3. 点击 `isCheckable = false` 的 Chip **不会改变任何选中状态**
4. 因此 **监听器根本不会被触发**，`showAddGroupDialog()` 永远不会执行

### 同样的问题也存在于科目新增

第72-82行的 `chipSubject.setOnCheckedStateChangeListener` 有相同的问题：
科目列表的"+ 新增"同样是 `isCheckable = false`，点击后也不会触发弹窗。

---

## 修复方案

### 核心思路

将"+ 新增"按钮的逻辑从 `setOnCheckedStateChangeListener`（选中状态监听）中分离出来，
改为使用 `setOnClickListener`（点击事件监听），这是最干净的解耦方式。

### 具体改动

#### 改动1：`refreshGroupChips()` — 给"+ 新增"添加点击监听

**文件**：`TimerFragment.kt` 第141-152行

```kotlin
// 改前
private fun refreshGroupChips() {
    chipGroup.removeAllViews()
    val names = SubjectData.getGroupNames()
    for (name in names) {
        chipGroup.addView(createChip(name))
    }
    // 新增按钮
    chipGroup.addView(createChip("+ 新增").apply { isCheckable = false })
    if (names.isNotEmpty()) {
        (chipGroup.getChildAt(0) as? Chip)?.isChecked = true
    }
}

// 改后
private fun refreshGroupChips() {
    chipGroup.removeAllViews()
    val names = SubjectData.getGroupNames()
    for (name in names) {
        chipGroup.addView(createChip(name))
    }
    // 新增按钮 — isCheckable=false 保证不参与选中，用 clickListener 响应点击
    chipGroup.addView(createChip("+ 新增").apply {
        isCheckable = false
        setOnClickListener {
            showAddGroupDialog()
        }
    })
    if (names.isNotEmpty()) {
        (chipGroup.getChildAt(0) as? Chip)?.isChecked = true
    }
}
```

#### 改动2：`chipGroup.setOnCheckedStateChangeListener` — 移除冗余判断

**文件**：`TimerFragment.kt` 第59-69行

```kotlin
// 改前
chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
    if (checkedIds.isNotEmpty()) {
        val chip = chipGroup.findViewById<Chip>(checkedIds[0])
        val name = chip?.text?.toString() ?: return@setOnCheckedStateChangeListener
        if (name == "+ 新增") {
            chipGroup.clearCheck()
            showAddGroupDialog()
        } else {
            refreshSubjectChips(name)
        }
    }
}

// 改后 — 只处理普通科目的选中，"+ 新增"已由 clickListener 独立处理
chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
    if (checkedIds.isNotEmpty()) {
        val chip = chipGroup.findViewById<Chip>(checkedIds[0])
        val name = chip?.text?.toString() ?: return@setOnCheckedStateChangeListener
        refreshSubjectChips(name)
    }
}
```

#### 改动3：`refreshSubjectChips()` — 给科目"+ 新增"添加点击监听

**文件**：`TimerFragment.kt` 第154-164行

```kotlin
// 改前
private fun refreshSubjectChips(groupName: String) {
    chipSubject.removeAllViews()
    val subjects = SubjectData.getSubjectsByGroup(groupName)
    for (sub in subjects) {
        chipSubject.addView(createChip(sub))
    }
    chipSubject.addView(createChip("+ 新增").apply { isCheckable = false })
    if (subjects.isNotEmpty()) {
        (chipSubject.getChildAt(0) as? Chip)?.isChecked = true
    }
}

// 改后
private fun refreshSubjectChips(groupName: String) {
    chipSubject.removeAllViews()
    val subjects = SubjectData.getSubjectsByGroup(groupName)
    for (sub in subjects) {
        chipSubject.addView(createChip(sub))
    }
    chipSubject.addView(createChip("+ 新增").apply {
        isCheckable = false
        setOnClickListener {
            val groupName2 = getSelectedGroup()
            showAddSubjectDialog(groupName2)
        }
    })
    if (subjects.isNotEmpty()) {
        (chipSubject.getChildAt(0) as? Chip)?.isChecked = true
    }
}
```

#### 改动4：`chipSubject.setOnCheckedStateChangeListener` — 移除冗余判断

**文件**：`TimerFragment.kt` 第72-82行

```kotlin
// 改前
chipSubject.setOnCheckedStateChangeListener { _, checkedIds ->
    if (checkedIds.isNotEmpty()) {
        val chip = chipSubject.findViewById<Chip>(checkedIds[0])
        val name = chip?.text?.toString() ?: return@setOnCheckedStateChangeListener
        if (name == "+ 新增") {
            chipSubject.clearCheck()
            val groupName = getSelectedGroup()
            showAddSubjectDialog(groupName)
        }
    }
}

// 改后 — 科目选中无额外逻辑，可直接清空监听或保留空壳
chipSubject.setOnCheckedStateChangeListener { _, _ ->
    // 科目选择不需要额外处理，选中即可开始计时
}
```

---

## 改动总结

| 文件 | 行号 | 改动类型 | 说明 |
|------|------|----------|------|
| `TimerFragment.kt` | 59-69 | 精简 | 移除 `setOnCheckedStateChangeListener` 中的"+ 新增"判断 |
| `TimerFragment.kt` | 72-82 | 精简 | 移除 `chipSubject` 监听器中的"+ 新增"判断 |
| `TimerFragment.kt` | 148 | **新增代码** | `refreshGroupChips()` 中给"+ 新增" Chip 添加 `setOnClickListener` |
| `TimerFragment.kt` | 160 | **新增代码** | `refreshSubjectChips()` 中给"+ 新增" Chip 添加 `setOnClickListener` |

**总改动量**：约15行，不涉及数据层、布局文件、或其他文件的修改。

---

## 验证方法

1. 启动 app → 进入计时页
2. 点击科目集区域的"+ 新增" → 应弹出"新增科目集"对话框
3. 输入名称点确定 → 新 Chip 应出现并自动选中
4. 选中一个科目集 → 科目区域应刷新
5. 点击科目区域的"+ 新增" → 应弹出"向xxx添加科目"对话框
6. 输入名称点确定 → 新科目 Chip 应出现
