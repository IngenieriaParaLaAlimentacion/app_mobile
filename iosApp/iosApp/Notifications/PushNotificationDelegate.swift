import FirebaseMessaging
import IngenieriaAlimentacion
import UIKit
import UserNotifications

final class PushNotificationDelegate: NSObject, MessagingDelegate, UNUserNotificationCenterDelegate {
    static let shared = PushNotificationDelegate()

    func configure(application: UIApplication) {
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            guard granted else { return }
            DispatchQueue.main.async { application.registerForRemoteNotifications() }
        }
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken else { return }
        PushTokenBridge.shared.handleNewToken(token: token)
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        defer { completionHandler() }
        guard response.actionIdentifier == UNNotificationDefaultActionIdentifier else { return }
        let data = response.notification.request.content.userInfo
        guard
            data["type"] as? String == "stale_establishment",
            let establishmentId = data["establishment_id"] as? String,
            let rawDays = data["stale_days"] as? String,
            let staleDays = Int32(rawDays)
        else { return }
        PushOpenBridge.shared.handleStaleEstablishment(
            establishmentId: establishmentId,
            staleDays: staleDays
        )
    }
}
