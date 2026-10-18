package ar.com.ingenieriaalimentacion.domain.usecase.establishment

import ar.com.ingenieriaalimentacion.domain.model.establishment.Bathroom
import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationError
import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationFailure
import ar.com.ingenieriaalimentacion.domain.model.establishment.CharacterizationStatus
import ar.com.ingenieriaalimentacion.domain.model.establishment.EstablishmentCharacterization
import ar.com.ingenieriaalimentacion.domain.model.establishment.Meal
import ar.com.ingenieriaalimentacion.domain.model.establishment.Premises
import ar.com.ingenieriaalimentacion.fake.FakeEstablishmentCharacterizationRepository
import ar.com.ingenieriaalimentacion.fake.FakeProfileRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaveEstablishmentCharacterizationUseCaseTest {

    private val characterizations = FakeEstablishmentCharacterizationRepository()
    private val profile = FakeProfileRepository()
    private val save = SaveEstablishmentCharacterizationUseCase(characterizations, profile)

    @Test
    fun `trims text, reads blanks as unanswered and drops Other text without Other`() = runTest {
        save(
            "e1",
            EstablishmentCharacterization(
                district = "  La Matanza ",
                neighborhood = "   ",
                premises = Premises.Street,
                premisesOther = "Un galpón",
                bathroom = Bathroom.Other,
                bathroomOther = " Letrina ",
            ),
        ).getOrThrow()

        val sent = characterizations.lastReplaced!!
        assertEquals("e1", characterizations.lastReplaceId)
        assertEquals("La Matanza", sent.district)
        assertEquals(null, sent.neighborhood)
        assertEquals(null, sent.premisesOther)
        assertEquals("Letrina", sent.bathroomOther)
    }

    @Test
    fun `refreshes my establishments after a successful save`() = runTest {
        val status = save("e1", EstablishmentCharacterization(meals = setOf(Meal.Lunch))).getOrThrow()

        assertEquals(CharacterizationStatus.Complete, status)
        assertEquals(1, profile.refreshCount)
    }

    @Test
    fun `does not refresh when the save fails`() = runTest {
        characterizations.replaceResult =
            Result.failure(CharacterizationFailure(CharacterizationError.Forbidden, null))

        val failure = save("e1", EstablishmentCharacterization.EMPTY).exceptionOrNull()

        assertTrue(failure is CharacterizationFailure)
        assertEquals(0, profile.refreshCount)
    }
}
