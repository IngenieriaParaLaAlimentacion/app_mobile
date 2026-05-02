package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.auth.UserSession

interface SessionManager {
    suspend fun saveSession(session: UserSession)
    suspend fun loadSessionOrNull(): UserSession?
    suspend fun deleteSession()
}
