package ar.com.ingenieriaalimentacion.presentation.feature.measurement.uploads

import ar.com.ingenieriaalimentacion.core.design_system.components.badge.AlBadgeTone
import ar.com.ingenieriaalimentacion.core.design_system.components.pill.AlPillTone
import ar.com.ingenieriaalimentacion.domain.model.measurement.ClinicalAlert
import ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementUpload
import ar.com.ingenieriaalimentacion.domain.model.subject.SyncState
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Instant

/** One clinical alert ready to render: a toned pill label plus its detail line. */
data class ClinicalAlertUi(val label: String, val detail: String, val tone: AlPillTone)

/** Display model for one measurement row: domain data plus localized badge, BMI, alerts and actions. */
data class MeasurementUploadUi(
    val localId: String,
    val title: String,                 // "Nombre Apellido"
    val subtitle: String,              // "DNI … · 70 kg · 172 cm · 21/06 18:30" (+ error on Failed)
    val badgeLabel: String,
    val badgeTone: AlBadgeTone,
    val showSpinner: Boolean,
    val bmiLabel: String?,             // "IMC 23,7" when Synced & bmi != null
    val alerts: List<ClinicalAlertUi>, // only populated on Synced
    val canRetry: Boolean,
    val canDelete: Boolean,
)

internal fun MeasurementUpload.toUi(): MeasurementUploadUi {
    val (label, tone) = when (syncState) {
        SyncState.Pending -> "Pendiente" to AlBadgeTone.Neutral
        SyncState.Syncing -> "Sincronizando" to AlBadgeTone.Neutral
        SyncState.Synced -> "Sincronizado" to AlBadgeTone.Success
        SyncState.Failed -> "Error" to AlBadgeTone.Danger
        SyncState.Merged -> "Sincronizado" to AlBadgeTone.Success // unreachable for measurements
    }
    val subtitle = buildString {
        append("DNI ")
        append(subjectDni)
        append(" · ")
        append(formatKg(weightKg))
        append(" kg")
        append(" · ")
        append(formatCm(heightCm))
        append(" cm")
        append(" · ")
        append(formatMeasuredAt(measuredAt))
        if (syncState == SyncState.Failed && !errorMessage.isNullOrBlank()) {
            append(" · ")
            append(errorMessage)
        }
    }
    val synced = syncState == SyncState.Synced
    return MeasurementUploadUi(
        localId = localId,
        title = "$subjectName $subjectLastName",
        subtitle = subtitle,
        badgeLabel = label,
        badgeTone = tone,
        showSpinner = syncState == SyncState.Syncing,
        bmiLabel = bmi?.takeIf { synced }?.let { "IMC ${formatBmi(it)}" },
        alerts = if (synced) alerts.map { it.toUi() } else emptyList(),
        canRetry = syncState == SyncState.Failed,
        canDelete = syncState == SyncState.Pending || syncState == SyncState.Failed || synced,
    )
}

private fun ClinicalAlert.toUi() = ClinicalAlertUi(
    label = "$alertType · $severity",
    detail = detail,
    tone = when (severity.trim().lowercase()) {
        "critical", "severe", "high", "alta", "critica", "crítica" -> AlPillTone.Danger
        "moderate", "medium", "warning", "media", "moderada" -> AlPillTone.Warning
        else -> AlPillTone.Neutral
    },
)

private fun formatBmi(v: Double): String = formatDecimal(v, 1)            // "23,7"
private fun formatKg(v: Double): String = formatDecimal(v, 1)
private fun formatCm(v: Double): String = formatDecimal(v, 1)

/**
 * Rounds [v] to [decimals] places, drops trailing zeros, and uses ',' as the decimal separator.
 * `String.format` is unavailable in commonMain, so build the string manually.
 */
private fun formatDecimal(v: Double, decimals: Int): String {
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = round(abs(v) * factor).toLong()
    val intPart = scaled / factor
    val fracPart = scaled % factor
    return buildString {
        if (v < 0 && scaled != 0L) append('-')
        append(intPart)
        if (fracPart != 0L) {
            val frac = fracPart.toString().padStart(decimals, '0').trimEnd('0')
            if (frac.isNotEmpty()) {
                append(',')
                append(frac)
            }
        }
    }
}

private fun formatMeasuredAt(iso: String): String = runCatching {
    val ldt = Instant.parse(iso).toLocalDateTime(TimeZone.currentSystemDefault())
    val dd = ldt.dayOfMonth.toString().padStart(2, '0')
    val mm = ldt.monthNumber.toString().padStart(2, '0')
    val hh = ldt.hour.toString().padStart(2, '0')
    val min = ldt.minute.toString().padStart(2, '0')
    "$dd/$mm $hh:$min"
}.getOrDefault(iso)
