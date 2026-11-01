package ar.com.ingenieriaalimentacion.presentation.feature.history

enum class HistorySegment(val label: String) {
    Subjects("Sujetos"),
    Measurements("Mediciones"),
    SurveyResponses("Encuestas"),

    /** Ingresos **and** salidas in one list — the only segment that filters the activity feed to
     *  more than one type.
     *  Appended last so a saved `ordinal` from a previous install still resolves to the same tab. */
    FoodMovements("Alimentos"),
}
