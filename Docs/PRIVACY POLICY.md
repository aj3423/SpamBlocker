### Table of Contents

## Introduction
This privacy policy covers the use of the 'SpamBlocker' (https://github.com/aj3423/SpamBlocker) Android application.

## Compliance with data regulations

SpamBlocker is [GDPR](https://commission.europa.eu/law/law-topic/data-protection_en?), [HIPAA](https://www.hhs.gov/hipaa/index.html) and [CCPA](https://oag.ca.gov/privacy/ccpa/regs) privacy regulations compliant.

SpamBlocker does not collect  or share any personal data or usage analytics.

SpamBlocker does not contain any advertising sdk, nor tracker .

As indirect identification data,
SpamBlocker only stores external API keys, only upon user action and stored only on the user's device.

All external interactions require user action (pressing a button at least) unless explicitly configured (by the user) to automatically do so, which is always disabled by default.

## Third party cloud service dependencies

Note that SpamBlocker:

* Allows online database(s) downloading, upon user activation and is set for, relying on any external database service. Database(s) downloaded are stored and used locally on the user’s device. Optionally, this service(s) may store user information(s) and data(s) allowing identification. Please refer to the service's privacy policy for detailed information on how they handle user data.

* Allows online caller phone number verification, validation or reporting, upon user configure and activate it, relying on external(s) cloud service(s).
User credentials (API key) of all service(s) are stored locally on the user’s device and are only used for authentication with the official endpoints.
Percase this service(s) may store user information(s) and data(s) allowing identification. Please refer to the service's privacy policy for detailed information on how they handle user data.

#### Data possibly processed by third party services

No personal data is sent to or otherwise shared with anyone. The only known possible data leaks _(to the third-party servers)_ are the following:
1. User's credentials _(API key)_.
2. User's IP address
3. Phone number verified and/or validated.
4. Country codes _(either auto-detected or set manually)_.
5. Operating system (can be detected from the TCP/TLS fingerprint).
 
Third party services do not necessarily collect all of this data _(always refer to the service's privacy policy)_.

 <!-- SpamBlocker specific licenses of libraries used in the application can be accessed from About section. - Not useful actually -->

## Android permissions requested by the application
SpamBlocker __optionally__ requires the following permissions:

* "INTERNET" - in order to download, query or report numbers.
* "ANSWER_PHONE_CALLS" - in order to hang-up calls.
* "POST_NOTIFICATIONS" - in order to show notifications.
* "READ_CONTACTS" - in order to match contacts.
* "RECEIVE_SMS" and "RECEIVE_MMS" - in order to be able to receive new SMS/MMS messages.
* "SEND_SMS" - in order to reply to contacts after their calls get blocked.
* "READ_CALL_LOG" and "READ_SMS" - in order to check if a call is repeated.
* "READ_CALENDAR" - in order to dynamically adjust rules based on calendar events.
* "READ_PHONE_STATE" - in order to monitor ringing state.
* "NOTIFICATION_ACCESS" - in order to monitor notifications from other apps.
* "WRITE_SETTINGS" - in order to change the ringtone.
* "READ_LOG" - in order to report bugs with adb log.
* "SYSTEM_ALERT_WINDOW" - in order to be able to show a floating caller ID window.
* "SCHEDULE_EXACT_ALARM" - in order to repeat notifications for important messages.
* "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" - in order to be able to work in the background.
