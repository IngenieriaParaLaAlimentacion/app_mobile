package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationStatus
import ar.com.ingenieriaalimentacion.domain.model.establishment.EstablishmentCharacterization
import ar.com.ingenieriaalimentacion.domain.repository.EstablishmentCharacterizationRepository

class FakeEstablishmentCharacterizationRepository : EstablishmentCharacterizationRepository {

    var getResult: Result<EstablishmentCharacterization> = Result.success(EstablishmentCharacterization.EMPTY)
    var replaceResult: Result<CharacterizationStatus> = Result.success(CharacterizationStatus.Complete)

    var getCount: Int = 0
    var replaceCount: Int = 0
    var lastGetId: String? = null
    var lastReplaceId: String? = null
    var lastReplaced: EstablishmentCharacterization? = null

    override suspend fun get(establishmentId: String): Result<EstablishmentCharacterization> {
        getCount++
        lastGetId = establishmentId
        return getResult
    }

    override suspend fun replace(
        establishmentId: String,
        characterization: EstablishmentCharacterization,
    ): Result<CharacterizationStatus> {
        replaceCount++
        lastReplaceId = establishmentId
        lastReplaced = characterization
        return replaceResult
    }
}
