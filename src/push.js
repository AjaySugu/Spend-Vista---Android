import { PushNotifications } from '@capacitor/push-notifications';

export const requestPushPermission = async () => {
    const permission = await PushNotifications.requestPermissions();

    if (permission.receive === 'granted') {
        PushNotifications.register();
    } else {
        console.log('Notification permission denied');
        return;
    }

    PushNotifications.addListener('registration', async (token) => {
        console.log('Device token:', token.value);
        // send to Laravel backend
        await fetch('https://your-laravel-domain.com/api/save-device-token', {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ token: token.value }),
            credentials: "include" // send cookie for auth
        });
    });

    PushNotifications.addListener('registrationError', (err) => {
        console.error("Registration error:", err);
    });

    PushNotifications.addListener('pushNotificationReceived', (notif) => {
        console.log('Push received:', notif);
    });
};
