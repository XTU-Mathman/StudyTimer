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
import com.example.studytimer.CalendarDetailView
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
    private lateinit var chartHeatmapContainer: LinearLayout
    private lateinit var chartCompareContainer: LinearLayout
    private lateinit var chartCalendarContainer: LinearLayout
    private lateinit var tvCalendarDetail: TextView
    private lateinit var pieChart: PieChartView
    private lateinit var lineChart: LineChartView
    private var heatmapView: HeatmapCalendarView? = null
    private var compareView: CompareBarView? = null
    private var calendarDetailView: CalendarDetailView? = null

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
        chartHeatmapContainer = view.findViewById(R.id.chart_heatmap_container)
        chartCompareContainer = view.findViewById(R.id.chart_compare_container)
        chartCalendarContainer = view.findViewById(R.id.chart_calendar_container)
        tvCalendarDetail = view.findViewById(R.id.tv_calendar_detail)

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
                    R.id.btn_year -> switchMode("year")
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
        chartLineContainer.visibility = if (mode == "week" || mode == "month") View.VISIBLE else View.GONE
        // 扇形图：日/周/月模式显示
        chartPieContainer.visibility = if (mode != "year") View.VISIBLE else View.GONE
        // 热力图：仅年模式显示
        chartHeatmapContainer.visibility = if (mode == "year") View.VISIBLE else View.GONE
        // 日历视图：仅年模式显示
        chartCalendarContainer.visibility = if (mode == "year") View.VISIBLE else View.GONE
        // 对比图：周/月模式显示
        chartCompareContainer.visibility = if (mode == "week" || mode == "month") View.VISIBLE else View.GONE
        // 日期导航：年模式隐藏
        val dateNav = view?.findViewById<View>(R.id.btn_prev)?.parent as? View
        dateNav?.visibility = if (mode == "year") View.GONE else View.VISIBLE

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
        if (currentMode != "year") {
            tvDateLabel.text = when (currentMode) {
                "day" -> dateFormat.format(currentCalendar.time)
                "week" -> {
                    val start = getWeekStart()
                    val end = getWeekEnd()
                    "${dateFormat.format(start.time)} ~ ${dateFormat.format(end.time)}"
                }
                "month" -> {
                    val monthFormat = SimpleDateFormat("yyyy年MM月", Locale.getDefault())
                    monthFormat.format(currentCalendar.time)
                }
                else -> ""
            }
        } else {
            tvDateLabel.text = "最近一年"
        }

        // 3. 筛选属于当前范围的记录
        val filteredRecords = if (currentMode == "year") allRecords else filterRecords(allRecords)

        // 年模式：热力图 + 总结，不显示图表和列表
        if (currentMode == "year") {
            val dailyMinutes = aggregateByDay(allRecords)
            if (heatmapView == null) {
                heatmapView = HeatmapCalendarView(requireContext())
                chartHeatmapContainer.addView(heatmapView)
            }
            heatmapView!!.setData(dailyMinutes)

            // 交互式日历（年模式）
            chartCalendarContainer.removeAllViews()
            calendarDetailView = CalendarDetailView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setData(dailyMinutes)
                setMonth(Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH))
                onDateSelected = { date, minutes ->
                    tvCalendarDetail.visibility = View.VISIBLE
                    tvCalendarDetail.text = "$date  学习 ${minutes} 分钟（${formatDuration(minutes * 60L)}）"
                }
            }
            chartCalendarContainer.addView(calendarDetailView)

            val totalYearSeconds = allRecords.sumOf { it.durationSeconds }
            tvTotalDuration.text = "年度总计：${formatDuration(totalYearSeconds)}"

            // 年模式列表：按月汇总
            layoutStatsList.removeAllViews()
            val monthlyData = aggregateByMonth(allRecords)
            if (monthlyData.isEmpty()) {
                layoutStatsList.addView(TextView(requireContext()).apply {
                    text = "暂无计时记录"
                    textSize = 14f
                    setTextColor(resources.getColor(R.color.text_tertiary, null))
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, 48, 0, 0)
                })
            } else {
                val maxMonthSeconds = monthlyData.values.maxOrNull()?.coerceAtLeast(1) ?: 1L
                for ((month, seconds) in monthlyData) {
                    layoutStatsList.addView(createStatRow(month, seconds, maxMonthSeconds))
                }
            }
            return
        }

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
        pieChart.subjectColors = SubjectData.getGroupColorMap()
        pieChart.setData(pieData)

        // 11. 更新折线图：仅在周/月模式有数据
        if (currentMode != "day") {
            val lineData = prepareLineChartData(allRecords)
            lineChart.setData(lineData)

            // 更新对比图
            updateCompareData(allRecords)
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
     */
    private fun createStatRow(name: String, seconds: Long, maxSeconds: Long): View {
        val dp = resources.displayMetrics.density
        // 从科目名提取科目集名（格式："科目集 - 科目"）
        val groupName = name.split(" - ").firstOrNull()?.trim() ?: ""
        val subjectColor = if (groupName.isNotEmpty()) SubjectData.getGroupColor(groupName)
            else resources.getColor(R.color.blue_primary, null)

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

        // 颜色圆点
        headerRow.addView(View(requireContext()).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(subjectColor)
            }
            layoutParams = LinearLayout.LayoutParams((10 * dp).toInt(), (10 * dp).toInt()).apply {
                marginEnd = (8 * dp).toInt()
            }
        })

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
            setTextColor(subjectColor)
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
                colors = intArrayOf(subjectColor, (subjectColor and 0x00FFFFFF) or 0x88000000.toInt())
                cornerRadius = 3f * dp
            }
            background = barBg
            layoutParams = LinearLayout.LayoutParams(barWidth, (6 * dp).toInt())
        }
        barContainer.addView(bar)
        row.addView(barContainer)

        // 点击整行 → 显示该科目每日详情
        row.isClickable = true
        row.isFocusable = true
        row.setOnClickListener {
            showSubjectDetailDialog(name, seconds)
        }

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

    /**
     * 按天汇总 → Map<yyyy-MM-dd, 分钟数>（热力图用）
     */
    private fun aggregateByDay(records: List<TimerRecord>): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        for (r in records) {
            result[r.date] = (result[r.date] ?: 0) + (r.durationSeconds / 60).toInt()
        }
        return result
    }

    /**
     * 生成对比数据：当前周期 vs 上一周期
     */
    private fun updateCompareData(allRecords: List<TimerRecord>) {
        // 获取上一周期的时间范围
        val prevCal = currentCalendar.clone() as Calendar
        when (currentMode) {
            "week" -> prevCal.add(Calendar.WEEK_OF_YEAR, -1)
            "month" -> prevCal.add(Calendar.MONTH, -1)
        }
        val savedCal = currentCalendar.clone() as Calendar
        currentCalendar = prevCal
        val prevRecords = filterRecords(allRecords)
        currentCalendar = savedCal as Calendar

        val currentRecords = filterRecords(allRecords)

        // 按科目集聚合
        val currentMap = mutableMapOf<String, Long>()
        for (r in currentRecords) {
            currentMap[r.subjectGroup] = (currentMap[r.subjectGroup] ?: 0) + r.durationSeconds
        }
        val prevMap = mutableMapOf<String, Long>()
        for (r in prevRecords) {
            prevMap[r.subjectGroup] = (prevMap[r.subjectGroup] ?: 0) + r.durationSeconds
        }

        val allGroups = (currentMap.keys + prevMap.keys).sorted()
        val colorMap = SubjectData.getGroupColorMap()

        val compareItems = allGroups.map { group ->
            CompareBarView.CompareItem(
                name = group,
                currentSeconds = currentMap[group] ?: 0,
                previousSeconds = prevMap[group] ?: 0,
                color = colorMap[group] ?: resources.getColor(R.color.blue_primary, null)
            )
        }

        if (compareView == null) {
            compareView = CompareBarView(requireContext())
            chartCompareContainer.addView(compareView)
        }
        compareView!!.setData(compareItems)
    }

    /**
     * 按月汇总 → 按时间倒序的 Map<yyyy年MM月, 秒数>（年列表用）
     */
    private fun aggregateByMonth(records: List<TimerRecord>): LinkedHashMap<String, Long> {
        val map = mutableMapOf<String, Long>()
        for (r in records) {
            val monthKey = r.date.substring(0, 7)  // yyyy-MM
            map[monthKey] = (map[monthKey] ?: 0) + r.durationSeconds
        }
        // 转为中文标签并倒序排列
        val result = LinkedHashMap<String, Long>()
        for ((key, value) in map.toSortedMap(compareByDescending { it })) {
            val parts = key.split("-")
            result["${parts[0]}年${parts[1].toInt()}月"] = value
        }
        return result
    }

    /**
     * 显示科目每日详情弹窗（点击统计行触发）
     */
    private fun showSubjectDetailDialog(name: String, seconds: Long) {
        val allRecords = StorageHelper.getAllRecords(requireContext())
        val filteredRecords = if (currentMode == "year") allRecords else filterRecords(allRecords)

        // 按日期筛选该科目的记录
        val subjectRecords = filteredRecords.filter { record ->
            "${record.subjectGroup} - ${record.subject}" == name
        }

        val dayMap = mutableMapOf<String, Long>()  // date -> seconds
        for (r in subjectRecords) {
            dayMap[r.date] = (dayMap[r.date] ?: 0) + r.durationSeconds
        }
        val sortedDays = dayMap.entries.sortedBy { it.key }

        val sb = StringBuilder()
        sb.appendLine("📖 $name")
        sb.appendLine("总计：${formatDuration(seconds)}")
        sb.appendLine("————————————")
        if (sortedDays.isEmpty()) {
            sb.appendLine("暂无每日记录")
        } else {
            for ((date, secs) in sortedDays) {
                sb.appendLine("$date  ${formatDuration(secs)}")
            }
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("科目详情")
            .setMessage(sb.toString())
            .setPositiveButton("关闭", null)
            .show()
    }
}
