package ar.com.ingenieriaalimentacion.domain.model.subject

import kotlinx.serialization.Serializable

/** `@Serializable` so [ar.com.ingenieriaalimentacion.domain.model.subject.SubjectHistoryEntry] and
 *  [ar.com.ingenieriaalimentacion.domain.model.measurement.MeasurementHistoryEntry] round-trip through
 *  the `CachedActivityItem.payloadJson` column. */
@Serializable
enum class SyncState { Pending, Syncing, Synced, Merged, Failed }
