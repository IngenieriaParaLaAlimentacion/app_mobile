package ar.com.ingenieriaalimentacion.presentation.feature.history

enum class HistorySegment(val label: String) {
    Subjects("Sujetos"),
    Measurements("Mediciones"),
    SurveyResponses("Encuestas"),

    /** Ingresos **and** salidas in one list — the only segment fed by two backend sources.
     *  Appended last so a saved `ordinal` from a previous install still resolves to the same tab. */
    FoodMovements("Alimentos"),
}
