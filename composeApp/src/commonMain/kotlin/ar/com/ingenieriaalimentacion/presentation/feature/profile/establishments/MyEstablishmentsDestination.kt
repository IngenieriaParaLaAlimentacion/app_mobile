package ar.com.ingenieriaalimentacion.presentation.feature.profile.establishments

sealed class MyEstablishmentsDestination {
    data object Back : MyEstablishmentsDestination()
    data object RequestEstablishment : MyEstablishmentsDestination()
}
