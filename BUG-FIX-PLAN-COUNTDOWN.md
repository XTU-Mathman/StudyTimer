# Bug 修复计划：倒计时暂停后时间丢失

## 问题定位

**文件**：`CountdownRunningActivity.kt` 第128-143行  
**现象**：设置20秒倒计时 → 5秒时暂停 → 10秒后继续 → 倒计时直接跳到0

## 根因分析

`pausedRemaining` 被**重复扣减**了两次——暂停时扣一次，恢复时又扣一次。

### 用数字推演（20秒倒计时，5秒时暂停，10秒后恢复）

```
初始状态：pausedRemaining = 20000ms

--- T=0s 开始 ---
startTimestamp = 1000000

--- T=5s 暂停（此时 remaining=15s）---
第138行：pausedRemaining -= (1005000 - 1000000)  // -= 5000
结果：pausedRemaining = 15000 ✅ 正确

--- T=15s 恢复（暂停了10秒）---
第131行：pausedRemaining -= (1015000 - 1000000)  // -= 15000 ❌ 把暂停的10秒也扣了！
结果：pausedRemaining = 0
```

**问题代码**（恢复分支）：
```kotlin
if (isPaused) {
    isPaused = false
    pausedRemaining -= System.currentTimeMillis() - startTimestamp  // ← 这行是错的
    startTimestamp = System.currentTimeMillis()
    handler.post(refreshRunnable)
}
```

`startTimestamp` 从未在暂停时更新过，所以恢复时 `currentTime - startTimestamp` 算出的是"从上次恢复到现在的全部时间"（包含暂停期间的10秒），而不是"实际运行时间"。

## 修复方案

**恢复时不需要再扣减 `pausedRemaining`**——暂停时已经正确扣减过了，恢复只需要重置 `startTimestamp`。

```kotlin
// 改前（恢复分支）
if (isPaused) {
    isPaused = false
    pausedRemaining -= System.currentTimeMillis() - startTimestamp  // ← 删除这行
    startTimestamp = System.currentTimeMillis()
    handler.post(refreshRunnable)
    btnPauseResume.text = "暂停"
    circularTimer.pulse()
}

// 改后（恢复分支）
if (isPaused) {
    isPaused = false
    startTimestamp = System.currentTimeMillis()  // 只需重置时间戳
    handler.post(refreshRunnable)
    btnPauseResume.text = "暂停"
    circularTimer.pulse()
}
```

**注意**：暂停分支（else）不需要改动，那里扣减是正确的。

## 修复后的正确推演

```
初始：pausedRemaining = 20000

--- T=0s 开始 ---
startTimestamp = 1000000

--- T=5s 暂停 ---
pausedRemaining -= 5000 → 15000 ✅
（startTimestamp 不变）

--- T=15s 恢复 ---
startTimestamp = 1015000（只重置，不扣减）
pausedRemaining = 15000（不变）✅

--- T=18s（恢复后运行3秒）---
remaining = 15000 - (1018000 - 1015000) = 12000 ✅ 显示12秒

--- T=25s（总共倒计时10秒）---
remaining = 15000 - (1025000 - 1015000) = 5000 ✅ 显示5秒
```

## 改动范围

| 文件 | 行号 | 改动 |
|------|------|------|
| `CountdownRunningActivity.kt` | 131 | 删除一行 `pausedRemaining -= ...` |

**总改动量**：删除1行代码。

## 验证方法

1. 设置一个20秒倒计时
2. 开始后5秒按暂停 → 数字停在15秒
3. 等10秒后按继续 → 数字应从15秒继续往下减
4. 最终0秒时应刚好过去20秒实际时间
