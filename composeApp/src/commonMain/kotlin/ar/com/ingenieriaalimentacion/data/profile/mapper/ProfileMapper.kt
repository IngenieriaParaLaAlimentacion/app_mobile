package ar.com.ingenieriaalimentacion.data.profile.mapper

import ar.com.ingenieriaalimentacion.data.profile.dto.AvailableEstablishmentResponse
import ar.com.ingenieriaalimentacion.data.profile.dto.EstablishmentWithStatusResponse
import ar.com.ingenieriaalimentacion.data.profile.dto.PaginatedAvailableResponse
import ar.com.ingenieriaalimentacion.data.profile.dto.ProfileResponse
import ar.com.ingenieriaalimentacion.domain.model.profile.AvailableEstablishment
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.PagedEstablishments
import ar.com.ingenieriaalimentacion.domain.model.profile.Profile

internal fun ProfileResponse.toDomain(): Profile = Profile(
    id = id,
    email = email,
    name = name,
    lastName = lastName,
    role = role,
    establishments = establishments?.map { it.toDomain() },
)

internal fun EstablishmentWithStatusResponse.toDomain(): EstablishmentWithStatus = EstablishmentWithStatus(
    id = id,
    name = name,
    formattedAddress = formattedAddress,
    latitude = latitude,
    longitude = longitude,
    placeId = placeId,
    city = city,
    province = province,
    status = status.toEstablishmentStatus(),
)

internal fun AvailableEstablishmentResponse.toDomain(): AvailableEstablishment = AvailableEstablishment(
    id = id,
    name = name,
    formattedAddress = formattedAddress,
    latitude = latitude,
    longitude = longitude,
    placeId = placeId,
    city = city,
    province = province,
)

internal fun PaginatedAvailableResponse.toDomain(): PagedEstablishments = PagedEstablishments(
    items = items.map { it.toDomain() },
    page = page,
    totalPages = totalPages,
    total = total,
)

private fun String.toEstablishmentStatus(): EstablishmentStatus = when (this) {
    "approved" -> EstablishmentStatus.Approved
    "pending" -> EstablishmentStatus.Pending
    "rejected" -> EstablishmentStatus.Rejected
    else -> EstablishmentStatus.Unknown
}
