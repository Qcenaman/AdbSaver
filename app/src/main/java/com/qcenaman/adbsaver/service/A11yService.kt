package com.qcenaman.adbsaver.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Reads the on-screen view tree as compact text and performs taps/gestures — the default "see the screen". */
class A11yService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    data class El(val label: String, val bounds: Rect, val clickable: Boolean, val node: AccessibilityNodeInfo)

    /** Flat list of meaningful elements: "label [l,t,r,b]". */
    fun elements(): List<El> {
        val out = ArrayList<El>()
        val windows = if (Build.VERSION.SDK_INT >= 21) windows else null
        val roots = windows?.mapNotNull { it.root } ?: listOfNotNull(rootInActiveWindow)
        for (r in roots) walk(r, out)
        return out
    }

    private fun walk(n: AccessibilityNodeInfo?, out: MutableList<El>) {
        if (n == null) return
        val label = (n.text ?: n.contentDescription ?: n.hintText)?.toString()?.trim().orEmpty()
        if (label.isNotEmpty() && n.isVisibleToUser) {
            val b = Rect(); n.getBoundsInScreen(b)
            out.add(El(label, b, n.isClickable, n))
        }
        for (i in 0 until n.childCount) walk(n.getChild(i), out)
    }

    fun treeText(max: Int = 200): String {
        val els = elements().take(max)
        return els.joinToString("\n") { e ->
            val b = e.bounds
            (if (e.clickable) "* " else "  ") + "${e.label} [${b.left},${b.top},${b.right},${b.bottom}]"
        }
    }

    fun tapLabel(label: String): Boolean {
        val e = elements().firstOrNull { it.label.equals(label, true) }
            ?: elements().firstOrNull { it.label.contains(label, true) } ?: return false
        var node: AccessibilityNodeInfo? = e.node
        while (node != null && !node.isClickable) node = node.parent
        return node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: tapAt(e.bounds.centerX(), e.bounds.centerY())
    }

    fun tapAt(x: Int, y: Int): Boolean = gesture(x.toFloat(), y.toFloat(), x.toFloat(), y.toFloat(), 60)
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, ms: Long = 250) =
        gesture(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), ms)

    private fun gesture(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long): Boolean {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, ms)).build()
        val latch = CountDownLatch(1); var ok = false
        dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { ok = true; latch.countDown() }
            override fun onCancelled(d: GestureDescription?) { latch.countDown() }
        }, null)
        latch.await(ms + 2000, TimeUnit.MILLISECONDS)
        return ok
    }

    private fun visible(text: String) = elements().any { it.label.contains(text, true) }

    /** Scroll page by page (no fling) until [text] is on screen. */
    fun scrollToText(text: String, maxPages: Int = 12): Boolean {
        if (visible(text)) return true
        repeat(maxPages) {
            val scrolled = scrollables().any { it.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) }
            if (!scrolled) {
                // No scrollable node reported: short, slow drag so the list cannot fling.
                val w = resources.displayMetrics.widthPixels; val h = resources.displayMetrics.heightPixels
                swipe(w / 2, (h * 0.62).toInt(), w / 2, (h * 0.42).toInt(), 600)
            }
            Thread.sleep(700)
            if (visible(text)) return true
        }
        return false
    }

    private fun scrollables(): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null) return
            if (n.isScrollable && n.isVisibleToUser) out.add(n)
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        (windows?.mapNotNull { it.root } ?: listOfNotNull(rootInActiveWindow)).forEach { walk(it) }
        return out
    }

    companion object {
        @Volatile var instance: A11yService? = null
            private set
    }
}
