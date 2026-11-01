package ar.com.ingenieriaalimentacion.domain.model.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserRoleTest {

    @Test
    fun `only admins and superadmins subscribe directly`() {
        assertEquals(EstablishmentLinkMode.Subscribe, establishmentLinkMode("admin"))
        assertEquals(EstablishmentLinkMode.Subscribe, establishmentLinkMode("superadmin"))
        assertEquals(EstablishmentLinkMode.RequestAccess, establishmentLinkMode("analyst"))
        assertEquals(EstablishmentLinkMode.RequestAccess, establishmentLinkMode("field_worker"))
    }

    @Test
    fun `field workers, admins, superadmins and analysts can link establishments`() {
        assertTrue(canLinkEstablishments("field_worker"))
        assertTrue(canLinkEstablishments("admin"))
        assertTrue(canLinkEstablishments("analyst"))
        assertTrue(canLinkEstablishments("superadmin"))
        assertFalse(canLinkEstablishments("unknown"))
    }

    @Test
    fun `admins, superadmins and analysts need an approved establishment for field-worker actions`() {
        assertTrue(requiresApprovedEstablishment("admin"))
        assertTrue(requiresApprovedEstablishment("analyst"))
        assertFalse(requiresApprovedEstablishment("field_worker"))
        assertTrue(requiresApprovedEstablishment("superadmin"))
    }

    @Test
    fun `analysts can now respond to surveys`() {
        assertTrue(canRespondToSurveys("analyst"))
        assertTrue(canRespondToSurveys("field_worker"))
        assertFalse(canRespondToSurveys("unknown"))
    }
}
