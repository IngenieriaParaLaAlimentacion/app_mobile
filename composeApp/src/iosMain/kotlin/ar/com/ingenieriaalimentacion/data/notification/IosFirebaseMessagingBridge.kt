package ar.com.ingenieriaalimentacion.data.notification

@OptIn(kotlin.experimental.ExperimentalObjCName::class)
@kotlin.native.ObjCName("IosFirebaseMessagingBridge")
interface IosFirebaseMessagingBridge {
    fun getToken(completion: (String?, String?) -> Unit)
    fun deleteToken(completion: (String?) -> Unit)
}

@OptIn(kotlin.experimental.ExperimentalObjCName::class)
@kotlin.native.ObjCName("IosFirebaseMessagingBridgeProvider")
object IosFirebaseMessagingBridgeProvider {
    private var bridge: IosFirebaseMessagingBridge? = null

    fun setBridge(bridge: IosFirebaseMessagingBridge) {
        this.bridge = bridge
    }

    fun requireBridge(): IosFirebaseMessagingBridge =
        checkNotNull(bridge) { "iOS Firebase Messaging bridge was not installed" }
}
