package ar.com.ingenieriaalimentacion.data.registration.mapper

import ar.com.ingenieriaalimentacion.data.registration.dto.RegisterFieldWorkerRequest
import ar.com.ingenieriaalimentacion.domain.model.registration.FieldWorkerRegistration

fun FieldWorkerRegistration.toRequest() = RegisterFieldWorkerRequest(
    email = email,
    password = password,
    name = name,
    lastName = lastName,
    establishments = establishmentIds,
    locality = locality,
    description = description,
    contactPhone = contactPhone,
)
