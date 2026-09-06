package ar.com.ingenieriaalimentacion.presentation.feature.survey.survey_response_history

import ar.com.ingenieriaalimentacion.domain.model.survey.SurveyResponseSummary
import ar.com.ingenieriaalimentacion.presentation.common.DayGroup
import ar.com.ingenieriaalimentacion.presentation.common.dayOf
import ar.com.ingenieriaalimentacion.presentation.common.groupByDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Day blocks for the Encuestas tab, dated by `eventAt` — the same timestamp the repository sorts
 * on (and the same one the backend feeds into `occurred_at`), so blocks and ordering agree.
 */
internal fun List<SurveyResponseSummary>.toDayGroups(
    today: LocalDate,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): List<DayGroup<SurveyResponseSummary>> = groupByDay(today) { dayOf(it.eventAt, zone) }
