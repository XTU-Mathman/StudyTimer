package com.example.studytimer

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButtonToggleGroup
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 统计页面：日/周/月 切换 → 扇形图 + 折线图 + 科目列表
 */
class StatsFragment : Fragment() {

    // ==================== 视图控件 ====================
    private lateinit var btnDay: Button
    private lateinit var btnWeek: Button
    private lateinit var btnMonth: Button
    private lateinit var btnPrev: TextView
    private lateinit var btnNext: TextView
    private lateinit var tvDateLabel: TextView
    private lateinit var tvTotalDuration: TextView
    private lateinit var layoutStatsList: LinearLayout

    // 图表容器
    private lateinit var chartPieContainer: LinearLayout
    private lateinit var chartLineContainer: LinearLayout
    private lateinit var pieChart: PieChartView
    private lateinit var lineChart: LineChartView

    // ==================== 状态 ====================
    private var currentMode = "day"  // 当前模式：day / week / month
    private var currentCalendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_stats, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ---------- 1. 绑定控件 ----------
        btnDay = view.findViewById(R.id.btn_day)
        btnWeek = view.findViewById(R.id.btn_week)
        btnMonth = view.findViewById(R.id.btn_month)
        btnPrev = view.findViewById(R.id.btn_prev)
        btnNext = view.findViewById(R.id.btn_next)
        tvDateLabel = view.findViewById(R.id.tv_date_label)
        tvTotalDuration = view.findViewById(R.id.tv_total_duration)
        layoutStatsList = view.findViewById(R.id.layout_stats_list)
        chartPieContainer = view.findViewById(R.id.chart_pie_container)
        chartLineContainer = view.findViewById(R.id.chart_line_container)

        // ---------- 2. 创建图表控件并添加到容器 ----------
        // 扇形图（所有模式都显示）
        pieChart = PieChartView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                640  // 像素高度，约 250dp
            )
        }
        chartPieContainer.addView(pieChart)

        // 折线图（日模式隐藏，周/月模式显示）
        lineChart = LineChartView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                460  // 像素高度，约 180dp
            )
        }
        chartLineContainer.addView(lineChart)

        // ---------- 3. 模式切换（分段控件） ----------
        val togglePeriod = view.findViewById<MaterialButtonToggleGroup>(R.id.toggle_period)
        togglePeriod.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_day -> switchMode("day")
                    R.id.btn_week -> switchMode("week")
                    R.id.btn_month -> switchMode("month")
                }
            }
        }

        // ---------- 4. 日期导航 ----------
        btnPrev.setOnClickListener { navigateDay(-1) }
        btnNext.setOnClickListener { navigateDay(1) }

        // 点击日期标签 → 弹出日历选择器（每日模式更有用）
        tvDateLabel.setOnClickListener { showDatePicker() }

        // ---------- 5. 首次加载 ----------
        togglePeriod.check(R.id.btn_day)
        switchMode("day")
    }

    /**
     * 切换模式（日/周/月）
     */
    private fun switchMode(mode: String) {
        currentMode = mode
        currentCalendar = Calendar.getInstance()  // 重置为今天

        // 折线图：仅在周/月模式显示
        chartLineContainer.visibility = if (mode == "day") View.GONE else View.VISIBLE

        refreshData()  // 刷新统计
    }

    /**
     * 弹出系统日历选择器，快速定位到任意日期（仅每日模式）
     */
    private fun showDatePicker() {
        val cal = currentCalendar
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                // 用户选定日期 → 更新并刷新
                currentCalendar = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                refreshData()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    /**
     * 日期导航：前进或后退一天/一周/一月
     */
    private fun navigateDay(delta: Int) {
        when (currentMode) {
            "day" -> currentCalendar.add(Calendar.DAY_OF_YEAR, delta)
            "week" -> currentCalendar.add(Calendar.WEEK_OF_YEAR, delta)
            "month" -> currentCalendar.add(Calendar.MONTH, delta)
        }
        refreshData()  // 刷新统计
    }

    /**
     * 刷新统计数据（核心方法）
     */
    private fun refreshData() {
        // 1. 从存储中获取所有计时记录
        val allRecords = StorageHelper.getAllRecords(requireContext())

        // 2. 根据模式更新日期标签文字
        tvDateLabel.text = when (currentMode) {
            "day" -> dateFormat.format(currentCalendar.time)  // 显示日期
            "week" -> {
                // 显示本周范围：周一 ~ 周日
                val start = getWeekStart()
                val end = getWeekEnd()
                "${dateFormat.format(start.time)} ~ ${dateFormat.format(end.time)}"
            }
            "month" -> {
                // 显示月份
                val monthFormat = SimpleDateFormat("yyyy年MM月", Locale.getDefault())
                monthFormat.format(currentCalendar.time)
            }
            else -> ""
        }

        // 3. 筛选属于当前范围的记录
        val filteredRecords = filterRecords(allRecords)

        // 4. 按科目聚合时长
        //    结构：Map<"科目集 - 科目", 总秒数>
        val aggregated = LinkedHashMap<String, Long>()
        for (record in filteredRecords) {
            val key = "${record.subjectGroup} - ${record.subject}"
            aggregated[key] = (aggregated[key] ?: 0) + record.durationSeconds
        }

        // 5. 按时长降序排列
        val sortedEntries = aggregated.entries.sortedByDescending { it.value }

        // 6. 更新总时长
        val totalSeconds = sortedEntries.sumOf { it.value }
        tvTotalDuration.text = "总计：${formatDuration(totalSeconds)}"

        // 7. 清空旧列表，重新填充
        layoutStatsList.removeAllViews()
        // 7. 清空旧列表，重新填充
        layoutStatsList.removeAllViews()

        if (sortedEntries.isEmpty()) {
            // 没有记录时显示空状态
            val emptyView = TextView(requireContext()).apply {
                text = "暂无计时记录"
                textSize = 14f
                setTextColor(resources.getColor(R.color.text_tertiary, null))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 48, 0, 0)
            }
            layoutStatsList.addView(emptyView)

            // 清空图表
            pieChart.setData(emptyList())
            lineChart.setData(emptyList())
            return
        }

        // 8. 找到最大时长，用于计算进度条比例
        val maxSeconds = sortedEntries.first().value.coerceAtLeast(1)

        // 9. 为每个科目创建一行统计
        for ((name, seconds) in sortedEntries) {
            layoutStatsList.addView(createStatRow(name, seconds, maxSeconds))
        }

        // 10. 更新扇形图：传入科目名->秒数列表
        val pieData = sortedEntries.map { (name, seconds) -> name to seconds }
        pieChart.setData(pieData)

        // 11. 更新折线图：仅在周/月模式有数据
        if (currentMode != "day") {
            val lineData = prepareLineChartData(allRecords)
            lineChart.setData(lineData)
        }
    }

    /**
     * 根据当前模式和日期，筛选记录
     */
    private fun filterRecords(allRecords: List<TimerRecord>): List<TimerRecord> {
        return when (currentMode) {
            "day" -> {
                val targetDate = dateFormat.format(currentCalendar.time)
                allRecords.filter { it.date == targetDate }
            }
            "week" -> {
                val start = getWeekStart()
                val end = getWeekEnd()
                allRecords.filter { record ->
                    val recordDate = dateFormat.parse(record.date)
                    recordDate != null &&
                        !recordDate.before(start.time) &&
                        !recordDate.after(end.time)
                }
            }
            "month" -> {
                val monthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                    .format(currentCalendar.time)
                allRecords.filter { it.date.startsWith(monthStr) }
            }
            else -> emptyList()
        }
    }

    /**
     * 准备折线图数据：按天汇总总时长
     * 每周模式 → 周一~周日；每月模式 → 1日~末
     */
    private fun prepareLineChartData(allRecords: List<TimerRecord>): List<Pair<String, Long>> {
        val dayFormat = SimpleDateFormat("MM/dd", Locale.getDefault())

        return when (currentMode) {
            "week" -> {
                // 遍历周一到周日
                val start = getWeekStart()
                val result = mutableListOf<Pair<String, Long>>()
                val cal = start.clone() as Calendar

                for (i in 0..6) {
                    val dateStr = dateFormat.format(cal.time)     // yyyy-MM-dd
                    val label = dayFormat.format(cal.time)        // MM/dd（用于显示）
                    // 汇总当天所有记录的总秒数
                    val dayTotal = allRecords
                        .filter { it.date == dateStr }
                        .sumOf { it.durationSeconds }
                    result.add(label to dayTotal)
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                result
            }
            "month" -> {
                // 获取当月天数
                val maxDay = currentCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                val monthStart = currentCalendar.clone() as Calendar
                monthStart.set(Calendar.DAY_OF_MONTH, 1)
                monthStart.set(Calendar.HOUR_OF_DAY, 0)

                val result = mutableListOf<Pair<String, Long>>()
                for (day in 1..maxDay) {
                    monthStart.set(Calendar.DAY_OF_MONTH, day)
                    val dateStr = dateFormat.format(monthStart.time)
                    val label = "${day}日"
                    val dayTotal = allRecords
                        .filter { it.date == dateStr }
                        .sumOf { it.durationSeconds }
                    result.add(label to dayTotal)
                }
                result
            }
            else -> emptyList()
        }
    }

    /**
     * 获取本周一（周起始日）
     */
    private fun getWeekStart(): Calendar {
        val cal = currentCalendar.clone() as Calendar
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        return cal
    }

    /**
     * 获取本周日（周结束日）
     */
    private fun getWeekEnd(): Calendar {
        val cal = getWeekStart()
        cal.add(Calendar.DAY_OF_YEAR, 6)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        return cal
    }

    /**
     * 创建一行科目统计视图
     * @param name 科目名称（如"数学 - 高数"）
     * @param seconds 该科目的计时秒数
     * @param maxSeconds 所有科目中的最大秒数（用于计算进度条比例）
     */
    private fun createStatRow(name: String, seconds: Long, maxSeconds: Long): View {
        val dp = resources.displayMetrics.density

        // 外层容器 — 玻璃卡片
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.card_glass)
            elevation = 1f * dp
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * dp).toInt() }
        }

        // 第一行：科目名 + 时长
        val headerRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val tvName = TextView(requireContext()).apply {
            text = name
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(tvName)

        val tvDuration = TextView(requireContext()).apply {
            text = formatDuration(seconds)
            textSize = 14f
            setTextColor(resources.getColor(R.color.blue_primary, null))
            paint.isFakeBoldText = true
        }
        headerRow.addView(tvDuration)
        row.addView(headerRow)

        // 第二行：渐变进度条
        val barContainer = LinearLayout(requireContext()).apply {
            setBackgroundColor(0x0D000000)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (6 * dp).toInt()
            ).apply { topMargin = (8 * dp).toInt() }
        }

        val percent = (seconds.toFloat() / maxSeconds.toFloat()).coerceIn(0f, 1f)
        val screenWidth = resources.displayMetrics.widthPixels - (48 * dp).toInt()
        val barWidth = (screenWidth * percent).toInt().coerceAtLeast((4 * dp).toInt())

        val bar = View(requireContext()).apply {
            val barBg = android.graphics.drawable.GradientDrawable().apply {
                orientation = android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
                colors = intArrayOf(
                    resources.getColor(R.color.blue_primary, null),
                    resources.getColor(R.color.chart_indigo, null)
                )
                cornerRadius = 3f * dp
            }
            background = barBg
            layoutParams = LinearLayout.LayoutParams(barWidth, (6 * dp).toInt())
        }
        barContainer.addView(bar)
        row.addView(barContainer)

        return row
    }

    /**
     * 把秒数格式化为人类可读的时长
     * 例如：3661 → "1小时1分"， 125 → "2分5秒"
     */
    private fun formatDuration(totalSeconds: Long): String {
        if (totalSeconds == 0L) return "0秒"
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return buildString {
            if (hours > 0) append("${hours}小时")
            if (minutes > 0) append("${minutes}分")
            if (seconds > 0 || isEmpty()) append("${seconds}秒")
        }
    }
}
