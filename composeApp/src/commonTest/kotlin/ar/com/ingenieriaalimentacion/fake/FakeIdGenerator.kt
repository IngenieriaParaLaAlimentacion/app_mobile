package ar.com.ingenieriaalimentacion.fake

import ar.com.ingenieriaalimentacion.domain.util.IdGenerator

/** Deterministic ids: `id-1`, `id-2`, … so a test can assert *when* a new one is minted. */
class FakeIdGenerator : IdGenerator {
    var count: Int = 0
        private set

    override fun newId(): String = "id-${++count}"
}
