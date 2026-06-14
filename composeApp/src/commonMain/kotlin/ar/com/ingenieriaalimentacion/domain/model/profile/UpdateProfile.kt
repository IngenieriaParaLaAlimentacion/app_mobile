package ar.com.ingenieriaalimentacion.domain.model.profile

/** Patch payload: only changed fields are non-null. */
data class UpdateProfile(val name: String? = null, val lastName: String? = null) {
    val isEmpty: Boolean get() = name == null && lastName == null
}
