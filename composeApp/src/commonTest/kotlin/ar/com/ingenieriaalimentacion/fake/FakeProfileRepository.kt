package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentAbsenceNotice
import ar.com.ingenieriaalimentacion.domain.model.profile.EstablishmentWithStatus
import ar.com.ingenieriaalimentacion.domain.model.profile.PagedEstablishments
import ar.com.ingenieriaalimentacion.domain.model.profile.Profile
import ar.com.ingenieriaalimentacion.domain.model.profile.UpdateProfile
import ar.com.ingenieriaalimentacion.domain.repository.ProfileRepository

class FakeProfileRepository : ProfileRepository {

    var meResult: Result<Profile> = Result.failure(IllegalStateException("not set"))
    var updateResult: Result<Profile> = Result.failure(IllegalStateException("not set"))
    var availableResult: Result<PagedEstablishments> = Result.success(
        PagedEstablishments(items = emptyList(), page = 1, totalPages = 1, total = 0),
    )

    /** Optional per-(search, page) responder; falls back to [availableResult] when null. */
    var availableResponder: ((search: String?, page: Int) -> Result<PagedEstablishments>)? = null

    var requestResult: Result<List<EstablishmentWithStatus>> = Result.success(emptyList())
    var myEstablishmentsResult: Result<List<EstablishmentWithStatus>> = Result.success(emptyList())

    var getMeCount: Int = 0
    var getMyEstablishmentsCount: Int = 0
    var lastUpdate: UpdateProfile? = null
    var updateCount: Int = 0

    /** Search/page tuples forwarded to the catalog, in call order. */
    val availableCalls = mutableListOf<Triple<String?, Int, Int>>()
    var lastRequestedIds: List<String>? = null

    override suspend fun getMe(): Result<Profile> {
        getMeCount++
        return meResult
    }

    override suspend fun updateMe(update: UpdateProfile): Result<Profile> {
        updateCount++
        lastUpdate = update
        return updateResult
    }

    override suspend fun getMyEstablishments(): Result<List<EstablishmentWithStatus>> {
        getMyEstablishmentsCount++
        return myEstablishmentsResult
    }

    var refreshCount: Int = 0
    var clearCacheCount: Int = 0

    override suspend fun refreshMyEstablishments(): Result<Unit> {
        refreshCount++
        return Result.success(Unit)
    }

    override suspend fun clearEstablishmentCache() {
        clearCacheCount++
    }

    var currentEstablishmentId: String? = null
    var setCurrentEstablishmentIdCount: Int = 0

    override suspend fun getCurrentEstablishmentId(): String? = currentEstablishmentId

    override suspend fun setCurrentEstablishmentId(establishmentId: String?) {
        setCurrentEstablishmentIdCount++
        currentEstablishmentId = establishmentId
    }

    override suspend fun getAvailableEstablishments(
        search: String?,
        page: Int,
        pageSize: Int,
    ): Result<PagedEstablishments> {
        availableCalls += Triple(search, page, pageSize)
        return availableResponder?.invoke(search, page) ?: availableResult
    }

    override suspend fun requestEstablishmentAccess(ids: List<String>): Result<List<EstablishmentWithStatus>> {
        lastRequestedIds = ids
        return requestResult
    }

    var withdrawResult: Result<Unit> = Result.success(Unit)
    var lastWithdrawnId: String? = null
    var withdrawCount: Int = 0

    override suspend fun withdrawFromEstablishment(establishmentId: String): Result<Unit> {
        withdrawCount++
        lastWithdrawnId = establishmentId
        return withdrawResult
    }

    var subscribeResult: Result<List<EstablishmentWithStatus>> = Result.success(emptyList())
    var lastSubscribedIds: List<String>? = null
    var subscribeCount: Int = 0

    override suspend fun subscribeToEstablishments(ids: List<String>): Result<List<EstablishmentWithStatus>> {
        subscribeCount++
        lastSubscribedIds = ids
        return subscribeResult
    }

    var unsubscribeResult: Result<Unit> = Result.success(Unit)
    var lastUnsubscribedIds: List<String>? = null
    var unsubscribeCount: Int = 0

    override suspend fun unsubscribeFromEstablishments(ids: List<String>): Result<Unit> {
        unsubscribeCount++
        lastUnsubscribedIds = ids
        return unsubscribeResult
    }

    var notifyAbsenceResult: Result<Unit> = Result.success(Unit)
    var lastAbsenceNotice: EstablishmentAbsenceNotice? = null
    var notifyAbsenceCount: Int = 0

    /** Optional responder to hold the call in flight; falls back to [notifyAbsenceResult]. */
    var notifyAbsenceResponder: (suspend (EstablishmentAbsenceNotice) -> Result<Unit>)? = null

    override suspend fun notifyEstablishmentAbsence(notice: EstablishmentAbsenceNotice): Result<Unit> {
        notifyAbsenceCount++
        lastAbsenceNotice = notice
        return notifyAbsenceResponder?.invoke(notice) ?: notifyAbsenceResult
    }
}
