package ar.com.ingenieriaalimentacion.presentation.feature.home

import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectSummary

data class HomeState(
    val destination: HomeDestination? = null,
    val title: String = "Ingeniería en Alimentación",
    val subtitle: String = "Hola, mundo",
    val pickedSubject: SubjectSummary? = null,
)
