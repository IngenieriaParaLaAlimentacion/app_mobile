import FirebaseCore
import FirebaseMessaging
import IngenieriaAlimentacion
import UIKit

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseApp.configure()
        IosFirebaseMessagingBridgeProvider.shared.setBridge(bridge: IOSFirebaseMessagingBridge())
        InitKoinKt.doInitKoin()
        PushNotificationDelegate.shared.configure(application: application)
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        Messaging.messaging().apnsToken = deviceToken
    }
}
