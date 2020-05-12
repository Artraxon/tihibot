import de.rtrx.a.replaceAll
import org.junit.jupiter.api.Test

class UtilityTests {
    @Test
    fun testReplacements(){
        val replacings = mapOf(
                "var1" to { "5" },
                "varOr" to { "or"}
        ).mapKeys {(key, _ )-> "%{$key}" }

        val testString = "Testing %{var1} replacement %{varOr} more"
        val expectedResult = "Testing 5 replacement or more"
        val replaced = testString.replaceAll(replacings)
        assert(replaced == expectedResult)
    }
}