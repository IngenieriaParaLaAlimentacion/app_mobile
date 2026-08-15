package ar.com.ingenieriaalimentacion.domain.usecase.survey

import ar.com.ingenieriaalimentacion.domain.repository.SurveyResponseRepository

/** Discards a queued response for good. Destructive: the answers are unsynced and unrecoverable,
 *  so the caller must confirm first. Mirrors `DeleteSubjectUploadUseCase`. */
class DiscardSurveyResponseUploadUseCase(private val repository: SurveyResponseRepository) {
    suspend operator fun invoke(localId: String): Result<Unit> = repository.delete(localId)
}
