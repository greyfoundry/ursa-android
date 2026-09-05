package dev.astoris.ursa.wear

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView

internal object WearUi {
    const val BACKGROUND = 0xFF050A07.toInt()
    const val SURFACE = 0xFF101A15.toInt()
    const val OUTLINE = 0xFF294033.toInt()
    const val PRIMARY = 0xFF5CDD8B.toInt()
    const val DOWN = 0xFFFF5364.toInt()
    const val PENDING = 0xFFFFC247.toInt()
    const val SUBTLE = 0xFFADB8B1.toInt()
    const val WHITE = Color.WHITE

    fun secure(activity: Activity) {
        if (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    fun root(activity: Activity): LinearLayout {
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(activity.dp(28), activity.dp(14), activity.dp(28), activity.dp(28))
        }
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            setBackgroundColor(BACKGROUND)
            addView(
                content,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        activity.setContentView(scroll)
        return content
    }

    fun title(context: Context, value: String): TextView = text(context, value, 22f, WHITE).apply {
        gravity = Gravity.CENTER
        setPadding(0, context.dp(4), 0, context.dp(4))
    }

    fun body(context: Context, value: String, color: Int = SUBTLE): TextView =
        text(context, value, 14f, color).apply { gravity = Gravity.CENTER }

    fun label(context: Context, value: String, color: Int = WHITE): TextView =
        text(context, value, 16f, color)

    fun button(
        context: Context,
        value: String,
        primary: Boolean = false,
        onClick: (View) -> Unit,
    ): Button = Button(context).apply {
        text = value
        isAllCaps = false
        setTextColor(if (primary) Color.BLACK else WHITE)
        textSize = 14f
        minHeight = context.dp(48)
        background = rounded(if (primary) PRIMARY else SURFACE, OUTLINE, context.dp(24).toFloat())
        setOnClickListener(onClick)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            context.dp(48),
        ).apply { topMargin = context.dp(8) }
    }

    fun card(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
        background = rounded(SURFACE, OUTLINE, context.dp(18).toFloat())
        minimumHeight = context.dp(64)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = context.dp(8) }
    }

    fun spacer(context: Context, heightDp: Int): Space = Space(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, context.dp(heightDp))
    }

    fun statusColor(status: WearMonitorStatus): Int = when (status) {
        WearMonitorStatus.UP -> PRIMARY
        WearMonitorStatus.DOWN -> DOWN
        WearMonitorStatus.PENDING -> PENDING
        WearMonitorStatus.MAINTENANCE -> PENDING
        WearMonitorStatus.UNKNOWN -> SUBTLE
    }

    private fun text(context: Context, value: String, size: Float, color: Int) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
    }

    private fun rounded(fill: Int, stroke: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(fill)
        setStroke(1, stroke)
    }
}

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

internal object WearSnapshotMemory {
    @Volatile
    var latest: WearSnapshot? = null
}
