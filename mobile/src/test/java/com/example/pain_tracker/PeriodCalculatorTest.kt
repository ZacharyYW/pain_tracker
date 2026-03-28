import com.example.pain_tracker.CycleSettings
import com.example.pain_tracker.PeriodCalculator
import org.junit.Test
import org.junit.Assert.assertEquals
import java.time.LocalDate

class PeriodCalculatorTest {
    @Test
    fun testPrediction() {
        val calculator = PeriodCalculator()
        val start = LocalDate.of(2026, 3, 1) // March 1st
        val settings = CycleSettings(lastPeriodDate = start, averageCycleLength = 28)

        val prediction = calculator.predictNextPeriod(settings)

        // We expect March 29th (1 + 28 days)
        assertEquals(LocalDate.of(2026, 3, 29), prediction)
    }
}