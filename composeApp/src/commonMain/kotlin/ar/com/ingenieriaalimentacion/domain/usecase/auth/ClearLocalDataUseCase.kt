package ar.com.ingenieriaalimentacion.domain.usecase.auth

import ar.com.ingenieriaalimentacion.domain.repository.DatabaseCleaner

/** Wipes all local data on explicit sign-out so a new user never sees the previous user's data. */
class ClearLocalDataUseCase(private val cleaner: DatabaseCleaner) {
    suspend operator fun invoke() = cleaner.clearAll()
}
