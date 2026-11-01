package ar.com.ingenieriaalimentacion.domain.model.food

enum class FoodOutflowCategory(val wireValue: String, val label: String) {
    FamilyDelivery("family_delivery", "Entrega a familias"),
    Kitchen("kitchen", "Cocina"),
    Discard("discard", "Descarte"),
    Other("other", "Otro");

    companion object {
        fun fromWireValue(value: String?): FoodOutflowCategory? =
            entries.firstOrNull { it.wireValue == value }
    }
}
