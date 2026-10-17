package dev.astoris.ursa.benchmarks

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = BuildConfig.TARGET_PACKAGE
private const val TARGET_MONITOR = "Monitor 1-100"
private const val WAIT_MILLIS = 30_000L

internal fun prepareFleet(monitorCount: Int) {
    require(monitorCount == 100 || monitorCount == 500 || monitorCount == 1_000)
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val clearResult = device.executeShellCommand("pm clear $TARGET_PACKAGE")
    check(clearResult.contains("Success")) { "Could not clear benchmark target: $clearResult" }
    val seedResult = device.executeShellCommand(
        "content call --uri content://$TARGET_PACKAGE.benchmark-fixtures --method seed --arg $monitorCount",
    )
    check(seedResult.contains("seeded=true") && seedResult.contains("count=$monitorCount")) {
        "Could not seed $monitorCount benchmark monitors: $seedResult"
    }
}

internal fun MacrobenchmarkScope.startHome() {
    startActivityAndWait()
    device.requireObject(By.text("Benchmark fleet"), "benchmark Home")
}

internal fun MacrobenchmarkScope.exerciseHome() {
    device.requireObject(By.text("Server health"), "server-health action").click()
    device.requireObject(By.text("Connection diagnostics"), "connection diagnostics")
    device.requireObject(By.text("Hide server health"), "hide server-health action").click()
}

internal fun MacrobenchmarkScope.exerciseInventorySearchAndDetail() {
    device.requireObject(By.text("Monitors"), "Monitors navigation").click()
    device.requireObject(By.text("Search monitors"), "monitor search")
    repeat(3) {
        device.swipe(
            device.displayWidth / 2,
            device.displayHeight * 3 / 4,
            device.displayWidth / 2,
            device.displayHeight / 3,
            12,
        )
    }

    val search = device.requireObject(By.clazz("android.widget.EditText"), "monitor search field")
    search.click()
    search.text = TARGET_MONITOR
    device.pressBack()
    device.requireObject(
        By.clickable(true).hasDescendant(By.text(TARGET_MONITOR)),
        "filtered monitor",
    ).click()
    device.requireObject(By.text("Overview"), "monitor detail")
    device.pressBack()

    val restoredSearch = device.requireObject(By.clazz("android.widget.EditText"), "restored search field")
    restoredSearch.text = ""
    device.pressBack()
    device.requireObject(By.text("Filters"), "monitor filters").click()
    device.requireObject(By.text("All"), "all-monitor filter").click()
    device.requireObject(By.text("Home"), "Home navigation").click()
    device.waitForIdle()
}

private fun UiDevice.requireObject(selector: BySelector, label: String): UiObject2 =
    wait(Until.findObject(selector), WAIT_MILLIS)
        ?: error("Timed out waiting for $label")
