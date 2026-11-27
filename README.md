# DNDx

A native Android app for a user-controlled Do Not Disturb mode, with call screening and a local list of contact exemptions. Current update: **v1.1.0**.

## What it does

- Declares Android's **Notification Policy Access** so DNDx appears in the system access list, enables Priority DND, and restores the previous interruption filter when turned off.
- Can grant DND policy access through a user-approved Shizuku session when Shizuku is installed and running; the normal Android settings flow remains available.
- Uses the Android **Call Screening** role (Android 10 / API 29+) to reject incoming calls while DNDx is active, except numbers in the app's exemption list.
- Lets the user choose exempt phone numbers from Contacts. The list is stored locally in app preferences.
- Includes links to Android's DND settings so users can adjust system notification exceptions.
- Uses a Material 3 interface with separate Overview, Rules, and Exemptions pages.

## Important platform limits

- A normal third-party app cannot reject an incoming carrier SMS or prevent its delivery. Android DND can silence message notifications; it does not reject the message. DNDx deliberately does not request the default-SMS role or claim otherwise.
- Android controls which calls and messages bypass Priority DND. Contacts exempted inside DNDx are call-screening exemptions; to have their notifications bypass system DND, configure Android's DND exceptions (often by allowing starred contacts and starring the contacts in the system Contacts app).
- Samsung does not expose a public API that lets third-party apps duplicate every Bixby Routine behavior. On Samsung and other manufacturers, availability depends on the device's Android version, call-screening role support, and manufacturer call/notification policies. The call-rejection feature requires Android 10 or later.
- DND policy access and the call-screening role are special user-granted settings, not dangerous runtime permissions. Contacts access is requested only when adding a contact.

## Build

Open this project in Android Studio with JDK 17 and Android Gradle Plugin 8.7.3, or build with Gradle 8.9:

```sh
gradle assembleDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk`, launch DNDx, grant DND policy access, optionally grant the call-screening role to enable call rejection, then add exemptions and enable DND. If Shizuku is running, approve DNDx's Shizuku request from the Overview screen to grant policy access without opening Android's access settings. Shizuku permission is always user-approved.
