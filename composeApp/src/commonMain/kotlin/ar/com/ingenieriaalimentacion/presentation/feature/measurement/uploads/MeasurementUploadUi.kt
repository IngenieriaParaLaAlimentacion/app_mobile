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
    val title: String,            // "Nombre Apellido"
    val identity: String,         // "DNI 42"
    val measurements: String,     // "70 kg · 172 cm"
    val measuredAtLabel: String,  // "21/06 18:30"
    val errorMessage: String?,    // shown only on Failed
    val badgeLabel: String,
    val badgeTone: AlBadgeTone,
    val isSynced: Boolean,        // drives quiet-status vs loud-badge in the UI
    val showSpinner: Boolean,
    val bmiValue: String?,        // "23,7" when Synced & bmi != null
    val bmiCategory: String?,     // primary alert's Spanish label, else "Normal"
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
    val synced = syncState == SyncState.Synced
    val bmiValue = bmi?.takeIf { synced }?.let { formatBmi(it) }
    return MeasurementUploadUi(
        localId = localId,
        title = "$subjectName $subjectLastName",
        identity = "DNI $subjectDni",
        measurements = "${formatKg(weightKg)} kg · ${formatCm(heightCm)} cm",
        measuredAtLabel = formatMeasuredAt(measuredAt),
        errorMessage = errorMessage.takeIf { syncState == SyncState.Failed && !it.isNullOrBlank() },
        badgeLabel = label,
        badgeTone = tone,
        isSynced = synced,
        showSpinner = syncState == SyncState.Syncing,
        bmiValue = bmiValue,
        // "Normal" is derived when the server flags no alert on a present BMI — a reassuring
        // positive state for the field worker; documented as derived, not server-sent.
        bmiCategory = if (bmiValue != null) {
            alerts.firstOrNull()?.let { alertTypeLabel(it.alertType) } ?: "Normal"
        } else {
            null
        },
        alerts = if (synced) alerts.map { it.toUi() } else emptyList(),
        canRetry = syncState == SyncState.Failed,
        canDelete = syncState == SyncState.Pending || syncState == SyncState.Failed || synced,
    )
}

private fun ClinicalAlert.toUi() = ClinicalAlertUi(
    label = alertTypeLabel(alertType),
    detail = detail,
    tone = severityTone(severity),
)

/** Canonical backend `alert_type` (lowercase enum .value) → Spanish clinical label. */
internal fun alertTypeLabel(raw: String): String = when (raw.trim().lowercase()) {
    "severely_wasted" -> "Emaciación grave"
    "wasted" -> "Emaciación"
    "underweight" -> "Bajo peso"
    "severe_underweight" -> "Bajo peso grave"
    "overweight" -> "Sobrepeso"
    "obese" -> "Obesidad"
    // Defensive: future/unknown codes render readably instead of leaking raw enums.
    else -> raw.trim().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/** Canonical backend `severity` (critical|high|medium) → pill tone; tone encodes severity. */
private fun severityTone(raw: String): AlPillTone = when (raw.trim().lowercase()) {
    "critical", "high" -> AlPillTone.Danger
    "medium" -> AlPillTone.Warning
    else -> AlPillTone.Neutral
}

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
