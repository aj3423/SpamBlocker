### Table of Contents

- [Introduction](##Introduction)
- [Compliance with data regulations](##compliance-with-data-regulations)
- [Third party cloud service dependencies](##third-party-cloud-service-dependencies)
- [Data possibly processed by third party services](####data-possibly-processed-by-third-party-services)
- [Android permissions requested by the application](##android-permissions-requested-by-the-application)
- [License](##license)

## Introduction
This privacy policy covers the use of the 'SpamBlocker' (https://github.com/aj3423/SpamBlocker) Android application.

It may not be applicable to other software produced or released by aj3423 (https://github.com/aj3423)

## Compliance with data regulations

SpamBlocker is [GDPR](https://commission.europa.eu/law/law-topic/data-protection_en?), [HIPAA](https://www.hhs.gov/hipaa/index.html) and [CCPA](https://oag.ca.gov/privacy/ccpa/regs) privacy regulations compliant.

SpamBlocker when running does not use, collect, store or share any statistics, personal information or analytics from its users, is devices or their use of these, other than Android operating system built in mechanisms that are present for all the mobile applications.

SpamBlocker does not contain any advertising sdk, nor tracker of the user, his device or their use of these.

Cookies are not used, stored or shared, at any point.

As indirect identification data,
SpamBlocker only stores external API keys, only upon user action and stored only on the user's device.

All external interactions require user action (pressing a button at least) unless explicitly configured (by the user) to automatically do so, which is always disabled by default.

## Third party cloud service dependencies

Note that SpamBlocker:

* Relies on The "Do Not Call" (DNC) Database (https://www.ftc.gov/policy-notices/open-government/data-sets/do-not-call-data) to retrieve information usable (by American users) to perform blocking call numbers reported to the Federal Trade Commission. Only if the user accepts it explicitly. Used directly on the user's device, For this purpose only. This service may store user information(s) and data(s) allowing identification. Please refer to the [ftc's privacy policy](https://www.ftc.gov/privacy) for detailed information on how they handle user data.

* Allows online database(s) downloading, upon user activation and is set for, relying on any external database service. Database(s) downloaded are stored and used locally on the user’s device. Optionally, this service(s) may store user information(s) and data(s) allowing identification. Please refer to the service's privacy policy for detailed information on how they handle user data.

* Allows online caller phone number verification, validation or reporting, upon user configure and activate it, relying on external(s) cloud service(s).
User credentials (API key) of all service(s) are stored locally on the user’s device and are only used for authentication with the official endpoints.
Percase this service(s) may store user information(s) and data(s) allowing identification. Please refer to the service's privacy policy for detailed information on how they handle user data.

#### Data possibly processed by third party services

__No personal data is sent to or otherwise shared with anyone. Data collected by third party services is by the operation of the device running SpamBlocker and without support or participation from 'SpamBlocker'.__

The only known possible data leaks _(to the third-party servers)_ are the following:
1. User's credentials _(API key)_.
2. User's device IP address
3. Phone number verified and/or validated.
4. Country codes _(either auto-detected or set manually)_.
5. date and time stamp, time difference to GMT.
6. Access status/HTTP status code.
7. Browser, operating system, interface, language, version of the browser software, user Agent and all the information possibly available on the http header.
 
Third party services do not necessarily collect all of this data _(always refer to the service's privacy policy)_.

 <!-- SpamBlocker specific licenses of libraries used in the application can be accessed from About section. - Not useful actually -->

## Android permissions requested by the application
Note that SpamBlocker application __optionally__ requires the following android platform permissions:

* “INTERNET" android permission in order to be able to perform status retrieval, parsing or checking, downloading or updating database, process instant query or number reporting, Only at the explicit request of the user or automatically if configured to do so.

* "MANAGE_EXTERNAL_STORAGE" _(Android 11+)_ or "READ/WRITE_EXTERNAL_STORAGE" _(Android 10)_ android permission in order to be able to perform file access from automated workflow. Only at the explicit request of the user or automatically if configured to do so.

* "ANSWER_PHONE_CALLS" android permission in order to be able to perform Reject, Answer and Hang-up calls.

* "POST_NOTIFICATIONS" android permission in order to be able to Show notifications.

* "READ_CONTACTS" android permission in order to be able to perform matching contacts.

* "RECEIVE_SMS" android permission in order to be able to perform receiving new messages.

* "READ_CALL_LOG" and "READ_SMS" android permission in order to be able to perform checking if a call is repeated.

* "PACKAGE_USAGE_STATS" android permission in order to be able to perform feature: Recent Apps, only for checking whether an app has been used recently.

* "READ_PHONE_STATE" android permission in order to be able to perform block mode: Answer + Hang-up (monitor ringing state).

## License
[MIT License](https://mit-license.org/)

Copyright (c) 2024 aj3423
