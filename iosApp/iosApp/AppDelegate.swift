import FirebaseCore
import FirebaseMessaging
import IngenieriaAlimentacion
import UIKit

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Local Debug builds can run before Firebase/APNs is provisioned.
        // The build phase requires the matching Firebase config for Release.
        if Bundle.main.url(forResource: "GoogleService-Info", withExtension: "plist") != nil {
            FirebaseApp.configure()
        }
        IosFirebaseMessagingBridgeProvider.shared.setBridge(bridge: IOSFirebaseMessagingBridge())
        InitKoinKt.doInitKoin()
        if FirebaseApp.app() != nil {
            PushNotificationDelegate.shared.configure(application: application)
        }
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        if FirebaseApp.app() != nil {
            Messaging.messaging().apnsToken = deviceToken
        }
    }
}
