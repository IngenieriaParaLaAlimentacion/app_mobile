package ar.com.ingenieriaalimentacion.presentation.common

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Only the hour: the day is carried by the `AlDateSeparator` above the block, so repeating it on
 * every row would say the same thing once per line.
 */
fun formatMovementTime(epochMillis: Long): String {
    val value = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "${value.hour.toString().padStart(2, '0')}:" +
        value.minute.toString().padStart(2, '0')
}
