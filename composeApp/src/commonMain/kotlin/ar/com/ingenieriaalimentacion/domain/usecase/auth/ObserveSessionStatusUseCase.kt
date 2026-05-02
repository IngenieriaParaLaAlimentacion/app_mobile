package ar.com.ingenieriaalimentacion.domain.usecase.auth

import ar.com.ingenieriaalimentacion.domain.model.auth.SessionStatus
import ar.com.ingenieriaalimentacion.domain.repository.AuthRepository
import kotlinx.coroutines.flow.StateFlow

class ObserveSessionStatusUseCase(private val repository: AuthRepository) {
    operator fun invoke(): StateFlow<SessionStatus> = repository.sessionStatus
}
