package ar.com.ingenieriaalimentacion.data.registration.mapper

import ar.com.ingenieriaalimentacion.data.registration.dto.RegisterManagerRequest
import ar.com.ingenieriaalimentacion.domain.model.registration.ManagerRegistration

fun ManagerRegistration.toRequest() = RegisterManagerRequest(
    email = email,
    password = password,
    name = name,
    lastName = lastName,
    establishments = establishmentIds,
)
