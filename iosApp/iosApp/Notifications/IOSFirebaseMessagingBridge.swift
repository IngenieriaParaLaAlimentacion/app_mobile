import FirebaseMessaging
import IngenieriaAlimentacion

final class IOSFirebaseMessagingBridge: IosFirebaseMessagingBridge {
    func getToken(completion: @escaping (String?, String?) -> Void) {
        Messaging.messaging().token { token, error in
            completion(token, error?.localizedDescription)
        }
    }

    func deleteToken(completion: @escaping (String?) -> Void) {
        Messaging.messaging().deleteToken { error in
            completion(error?.localizedDescription)
        }
    }
}
