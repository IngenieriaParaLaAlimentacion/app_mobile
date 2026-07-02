# ADR-011 — Parse the discriminated union as a flat DTO, no `JsonContentPolymorphicSerializer`

- **Status:** Accepted (grilling session 2026-07-01)

## Decision (proposed)

`ActivityItemResponse` is modeled as **one flat `@Serializable` DTO** with nullable `subject`/
`measurement` fields, matching the wire shape exactly:

```kotlin
@Serializable
data class ActivityItemResponse(
    val type: String,                 // "subject" | "measurement"
    @SerialName("occurred_at") val occurredAt: String,
    val subject: ActivitySubjectResponse? = null,
    val measurement: ActivityMeasurementResponse? = null,
)
```

A mapper (`toDomain()`) branches on `type` to produce a sealed domain type
(`ActivityItem.SubjectRegistered` / `ActivityItem.MeasurementTaken`), same layering already used
elsewhere (`data/*/mapper/toDomain.kt` files map flat DTOs to richer domain models throughout this
codebase). No custom `KSerializer`/`JsonContentPolymorphicSerializer` is needed — kotlinx.serialization
already deserializes "field present vs. absent" into "non-null vs. null" for free.

## Rationale

There's no existing polymorphic-JSON precedent in this codebase (confirmed by search) — the closest
analog, `SubjectUploadOutcome`/`MeasurementUploadOutcome`, branches on **HTTP status code**, not a
JSON discriminator field, so it doesn't transfer directly. `JsonContentPolymorphicSerializer` is
the "correct" general-purpose tool for a discriminated union, but it's unnecessary machinery here:
the two variants share one envelope with mutually-exclusive nullable fields, which flat
`@Serializable` classes handle natively. Simpler is better; introduce the polymorphic serializer
machinery only if a future endpoint has variants with genuinely incompatible shapes (e.g. same
field name, different types across variants) — not the case here.

## Consequences

- `data/activity/dto/ActivityResponse.kt` (new file) holds the flat DTOs + `PaginatedActivityResponse`
  (identical envelope shape to `PaginatedSubjectsResponse`).
- Mapper enforces the invariant `type == "subject" xor subject != null` (and same for measurement)
  at the boundary — if the backend ever violates it, fail loudly (per the coding-style rule:
  validate at system boundaries) rather than silently rendering a blank row.
