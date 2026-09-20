package ar.com.ingenieriaalimentacion.presentation.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Route {
    @Serializable
    @SerialName("get_started")
    data object GetStarted : Route()

    @Serializable
    @SerialName("home")
    data object Home : Route()

    @Serializable
    @SerialName("register")
    data object Register : Route()

    @Serializable
    @SerialName("register_success")
    data object RegisterSuccess : Route()

    /**
     * [prefillDni] seeds the DNI field when the screen is opened from the subject picker after a
     * fruitless search; [returnsResult] makes a successful save hand the new subject back to the
     * measurement form instead of staying put with a confirmation banner.
     */
    @Serializable
    @SerialName("create_subject")
    data class CreateSubject(
        val prefillDni: String = "",
        val returnsResult: Boolean = false,
    ) : Route()

    @Serializable
    @SerialName("food_overview")
    data object FoodOverview : Route()

    @Serializable
    @SerialName("create_food_intake")
    data object CreateFoodIntake : Route()

    @Serializable
    @SerialName("create_food_outflow")
    data object CreateFoodOutflow : Route()

    /** Stock of the *currently selected* establishment — the id is resolved at screen entry from
     *  `CurrentEstablishmentLocalStore`, never passed as a route argument, so a deep link or a
     *  restored back stack can never pin a stale establishment. */
    @Serializable
    @SerialName("food_stock")
    data object FoodStock : Route()

    @Serializable
    @SerialName("establishment_food_movement_history")
    data object EstablishmentFoodMovementHistory : Route()

    @Serializable
    @SerialName("establishment_food_movement_detail")
    data class EstablishmentFoodMovementDetail(
        val establishmentId: String,
        val movementId: String,
    ) : Route()

    @Serializable
    @SerialName("history")
    data object History : Route()

    @Serializable
    @SerialName("create_measurement")
    data object CreateMeasurement : Route()

    /** [singleSelect] turns the multi-select registration picker into a one-tap chooser: the first
     *  tap confirms with a one-element list and the *Listo* button is hidden. The result key is
     *  unchanged, so registration is untouched. */
    @Serializable
    @SerialName("select_establishment")
    data class SelectEstablishment(
        val preselectedJson: String = "[]",
        val singleSelect: Boolean = false,
    ) : Route()

    @Serializable
    @SerialName("select_subject")
    data object SelectSubject : Route()

    @Serializable
    @SerialName("reset_password")
    data class ResetPassword(val token: String) : Route()

    @Serializable
    @SerialName("reset_password_success")
    data object ResetPasswordSuccess : Route()

    @Serializable
    @SerialName("forgot_password")
    data object ForgotPassword : Route()

    @Serializable
    @SerialName("login")
    data object Login : Route()

    @Serializable
    @SerialName("profile")
    data object Profile : Route()

    @Serializable
    @SerialName("edit_profile")
    data class EditProfile(
        val name: String,
        val lastName: String,
        val email: String,
        val role: String,
    ) : Route()

    @Serializable
    @SerialName("request_establishment")
    data object RequestEstablishment : Route()

    @Serializable
    @SerialName("notify_establishment")
    data object NotifyEstablishment : Route()

    @Serializable
    @SerialName("my_establishments")
    data object MyEstablishments : Route()

    @Serializable
    @SerialName("authed_shell")
    data object AuthedShell : Route()

    @Serializable
    @SerialName("survey_list")
    data object SurveyList : Route()

    /**
     * The *kind* of target is deliberately not a route argument — the form reads it from
     * `SurveyDetail.responseTarget`, which it already loads and which is cached offline. One
     * source of truth. [targetId]/[targetLabel] are null for an anonymous survey, and for an
     * establishment survey entered without a pick (the Home default then pre-fills it).
     */
    @Serializable
    @SerialName("survey_response_form")
    data class SurveyResponseForm(
        val surveyId: String,
        val targetId: String? = null,
        val targetLabel: String? = null,
    ) : Route()

    /** [surveyId] is a path segment of every response endpoint, so it travels with the response id. */
    @Serializable
    @SerialName("survey_response_detail")
    data class SurveyResponseDetail(val surveyId: String, val responseId: String) : Route()

    @Serializable
    @SerialName("survey_response_edit")
    data class SurveyResponseEdit(val surveyId: String, val responseId: String) : Route()

    @Serializable
    @SerialName("survey_response_number_fix")
    data class SurveyResponseNumberFix(val localId: String, val surveyName: String) : Route()
}
