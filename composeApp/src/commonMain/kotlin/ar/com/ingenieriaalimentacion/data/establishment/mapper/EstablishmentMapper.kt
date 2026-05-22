package ar.com.ingenieriaalimentacion.data.establishment.mapper

import ar.com.ingenieriaalimentacion.data.establishment.dto.EstablishmentResponse
import ar.com.ingenieriaalimentacion.domain.model.establishment.Establishment

fun EstablishmentResponse.toDomain() = Establishment(
    id = id,
    name = name,
    address = address,
)
