package io.gitlab.arturbosch.detekt.core.reporting

import io.github.detekt.report.html.HtmlOutputReport
import io.github.detekt.report.sarif.SarifOutputReport
import io.github.detekt.report.txt.TxtOutputReport
import io.github.detekt.report.xml.XmlOutputReport
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.CorrectableCodeSmell
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Detektion
import io.gitlab.arturbosch.detekt.api.Finding
import io.gitlab.arturbosch.detekt.api.RuleSetId

internal fun defaultReportMapping(reportId: String) = when (reportId) {
    TxtOutputReport::class.java.simpleName -> "txt"
    XmlOutputReport::class.java.simpleName -> "xml"
    HtmlOutputReport::class.java.simpleName -> "html"
    SarifOutputReport::class.java.simpleName -> "sarif"
    else -> reportId
}

internal fun printFindings(findings: Map<String, List<Finding>>): String {
    return buildString {
        val debtList = mutableListOf<Debt>()
        findings.forEach { (key, issues) ->
            val debt = issues
                .map { it.issue.debt }
                .reduce { acc, d -> acc + d }
            debtList.add(debt)
            append("$key - $debt debt\n")
            issues.forEach {
                append("\t")
                append(it.compact().yellow())
                append("\n")
            }
        }
        val overallDebt = debtList.reduce { acc, d -> acc + d }
        append("\nOverall debt: $overallDebt\n")
        toString()
    }
}

const val BUILD = "build"
const val EXCLUDE_CORRECTABLE = "excludeCorrectable"

const val DETEKT_OUTPUT_REPORT_PATHS_KEY = "detekt.output.report.paths.key"
const val DETEKT_OUTPUT_REPORT_BASE_PATH_KEY = "detekt.output.report.base.path"

fun Config.excludeCorrectable(): Boolean = subConfig(BUILD).valueOrDefault(EXCLUDE_CORRECTABLE, false)

fun Detektion.filterEmptyIssues(config: Config): Map<RuleSetId, List<Finding>> {
    return this
        .filterAutoCorrectedIssues(config)
        .filter { it.value.isNotEmpty() }
}

fun Detektion.filterAutoCorrectedIssues(config: Config): Map<RuleSetId, List<Finding>> {
    if (!config.excludeCorrectable()) {
        return findings
    }
    val filteredFindings = HashMap<RuleSetId, List<Finding>>()
    findings.forEach { (ruleSetId, findingsList) ->
        val newFindingsList = findingsList.filter { finding ->
            val correctableCodeSmell = finding as? CorrectableCodeSmell
            correctableCodeSmell == null || !correctableCodeSmell.autoCorrectEnabled
        }
        filteredFindings[ruleSetId] = newFindingsList
    }
    return filteredFindings
}
