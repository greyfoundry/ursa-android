package dev.astoris.ursa.benchmarks

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun baselineProfile() = benchmarkStartup(
        CompilationMode.Partial(BaselineProfileMode.Require),
    )

    @Test
    fun noCompilation() = benchmarkStartup(CompilationMode.None())

    private fun benchmarkStartup(compilationMode: CompilationMode) {
        prepareFleet(1_000)
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            iterations = 7,
            startupMode = StartupMode.COLD,
            setupBlock = { pressHome() },
        ) {
            startHome()
        }
    }
}
