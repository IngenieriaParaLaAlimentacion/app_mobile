import FirebaseCore
import FirebaseMessaging
import IngenieriaAlimentacion

final class IOSFirebaseMessagingBridge: IosFirebaseMessagingBridge {
    func getToken(completion: @escaping (String?, String?) -> Void) {
        guard FirebaseApp.app() != nil else {
            completion(nil, "Firebase Messaging is not configured for this local build")
            return
        }
        Messaging.messaging().token { token, error in
            completion(token, error?.localizedDescription)
        }
    }

    func deleteToken(completion: @escaping (String?) -> Void) {
        guard FirebaseApp.app() != nil else {
            completion(nil)
            return
        }
        Messaging.messaging().deleteToken { error in
            completion(error?.localizedDescription)
        }
    }
}
