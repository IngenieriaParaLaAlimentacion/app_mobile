package ar.com.ingenieriaalimentacion.domain.model.food

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class FoodIntakeOrigin(val wireValue: String, val label: String) {
    @SerialName("national_government") NationalGovernment("national_government", "Gobierno nacional"),
    @SerialName("provincial_government") ProvincialGovernment("provincial_government", "Gobierno provincial"),
    @SerialName("municipal_government") MunicipalGovernment("municipal_government", "Gobierno municipal"),
    @SerialName("private_company") PrivateCompany("private_company", "Empresa privada"),
    @SerialName("foundation") Foundation("foundation", "Fundación"),
    @SerialName("social_organization") SocialOrganization("social_organization", "Organización social"),
    @SerialName("church") Church("church", "Iglesia"),
    @SerialName("other") Other("other", "Otro"),
    ;

    companion object {
        fun fromWire(value: String?): FoodIntakeOrigin? = entries.firstOrNull { it.wireValue == value }
    }
}

fun Boolean?.contributionLabel(): String = when (this) {
    true -> "Recurrente"
    false -> "Único"
    null -> "Sin datos"
}
