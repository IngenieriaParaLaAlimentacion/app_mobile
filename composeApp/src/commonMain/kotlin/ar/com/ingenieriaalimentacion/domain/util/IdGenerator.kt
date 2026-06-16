package ar.com.ingenieriaalimentacion.domain.util

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Generates client-side identifiers. Injected so tests can supply deterministic ids. */
interface IdGenerator {
    fun newId(): String
}

@OptIn(ExperimentalUuidApi::class)
class UuidGenerator : IdGenerator {
    override fun newId(): String = Uuid.random().toString()
}
