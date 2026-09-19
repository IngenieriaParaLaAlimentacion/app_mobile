package ar.com.ingenieriaalimentacion.data.notification.mapper

import ar.com.ingenieriaalimentacion.domain.model.notification.PushPlatform

fun PushPlatform.toWire(): String = when (this) {
    PushPlatform.Android -> "android"
    PushPlatform.Ios -> "ios"
}
