package io.gitlab.arturbosch.detekt.core

import io.github.detekt.test.utils.compileForTest
import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.test.yamlConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.kotlin.psi.KtFile
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.concurrent.CompletionException

class AnalyzerSpec : Spek({

    describe("exceptions during analyze()") {
        it("throw error explicitly when config has wrong value type in config") {
            val testFile = path.resolve("Test.kt")
            val settings = createProcessingSettings(testFile, yamlConfig("configs/config-value-type-wrong.yml"))
            val analyzer = Analyzer(settings, listOf(StyleRuleSetProvider()), emptyList())

            assertThatThrownBy {
                settings.use { analyzer.run(listOf(compileForTest(testFile))) }
            }.isInstanceOf(IllegalStateException::class.java)
        }

        it("throw error explicitly in parallel when config has wrong value in config") {
            val testFile = path.resolve("Test.kt")
            val settings = createProcessingSettings(
                inputPath = testFile,
                config = yamlConfig("configs/config-value-type-wrong.yml"),
                spec = createNullLoggingSpec {
                    execution {
                        parallelParsing = true
                        parallelAnalysis = true
                    }
                }
            )
            val analyzer = Analyzer(settings, listOf(StyleRuleSetProvider()), emptyList())

            assertThatThrownBy { settings.use { analyzer.run(listOf(compileForTest(testFile))) } }
                .isInstanceOf(CompletionException::class.java)
                .hasCauseInstanceOf(IllegalStateException::class.java)
        }
    }

    describe("analyze successfully when config has correct value type in config") {

        it("no findings") {
            val testFile = path.resolve("Test.kt")
            val settings = createProcessingSettings(testFile, yamlConfig("configs/config-value-type-correct.yml"))
            val analyzer = Analyzer(settings, listOf(StyleRuleSetProvider()), emptyList())

            assertThat(settings.use { analyzer.run(listOf(compileForTest(testFile))) }).isEmpty()
        }

        it("with findings") {
            val testFile = path.resolve("Test.kt")
            val settings = createProcessingSettings(testFile, yamlConfig("configs/config-value-type-correct.yml"))
            val analyzer = Analyzer(settings, listOf(StyleRuleSetProvider(18)), emptyList())

            assertThat(settings.use { analyzer.run(listOf(compileForTest(testFile))) }).hasSize(1)
        }

        it("with findings but ignored") {
            val testFile = path.resolve("Test.kt")
            val settings = createProcessingSettings(
                testFile,
                yamlConfig("configs/config-value-type-correct-ignore-annotated.yml")
            )
            val analyzer = Analyzer(settings, listOf(StyleRuleSetProvider(18)), emptyList())

            assertThat(settings.use { analyzer.run(listOf(compileForTest(testFile))) }).isEmpty()
        }
    }
})

private class StyleRuleSetProvider(private val threshold: Int? = null) : RuleSetProvider {
    override val ruleSetId: String = "style"
    override fun instance(config: Config) = RuleSet(ruleSetId, listOf(MaxLineLength(config, threshold)))
}

private class MaxLineLength(config: Config, threshold: Int?) : Rule(config) {
    override val issue = Issue(this::class.java.simpleName, Severity.Style, "", Debt.FIVE_MINS)
    private val lengthThreshold: Int = threshold ?: valueOrDefault("maxLineLength", 100)
    override fun visitKtFile(file: KtFile) {
        super.visitKtFile(file)
        for (line in file.text.lineSequence()) {
            if (line.length > lengthThreshold) {
                report(CodeSmell(issue, Entity.atPackageOrFirstDecl(file), issue.description))
            }
        }
    }
}
