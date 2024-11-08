
## Introduction
This privacy policy covers the use of the 'SpamBlocker' (https://github.com/aj3423/SpamBlocker) Android application.

It may not be applicable to other software produced or released by aj3423 (https://github.com/aj3423)

SpamBlocker when running does not collect any statistics, personal information, or analytics from its users, is devices or their use of these; other than Android operating system built in mechanisms that are present for all the mobile applications.

SpamBlocker does not contain any advertising sdk, nor tracker of the user or his device.

Cookies are not stored, at any point.

SpamBlocker has the ability, at the user's request, to retrieve the Do Not Call (DNC) database, or any others (online or not) database user added. This is done without storing or transmitting any identifiable user nor device informations, the data is only stored locally on the user's device.

All external interactions require user action (pressing a button at least) unless explicitly configured (by the user) to automatically do so, which is always disabled by default.

## Third party cloud service dependencies

Note that SpamBlocker:

* Relies on The "Do Not Call" (DNC) Database (https://www.ftc.gov/policy-notices/open-government/data-sets/do-not-call-data) to retrieve information usable (by American users) to perform blocking call numbers reported to the Federal Trade Commission. only if the user accepts it explicitly. Used directly on the user's device. For this purpose only, processed without sending any data related to the user, their device or their use of these;
* allows online database downloading, upon user activation and is set for, relying on any external database service. Optionally, this services may store user information and data allowing identification; Please refer to the website's database used privacy policy for details on how they handle user data.

 <!-- SpamBlocker specific licenses of libraries used in the application can be accessed from About section. - Not useful actually -->

## Android permissions requested by the application
Note that SpamBlocker application optionally requires the following android platform permissions:

* “INTERNET" android permission in order to be able to perform status retrieval, parsing or checking, downloading or updating the database. Only at the explicit request of the user or automatically if configured to do so.

* "MANAGE_EXTERNAL_STORAGE" _(Android 11+)_ or "READ/WRITE_EXTERNAL_STORAGE" _(Android 10)_ android permission in order to be able to perform file access from automated workflow. Only at the explicit request of the user or automatically if configured to do so.

* "ANSWER_PHONE_CALLS" android permission in order to be able to perform Reject, Answer and Hang-up calls.

* "POST_NOTIFICATIONS" android permission in order to be able to Show notifications.

* "READ_CONTACTS" android permission in order to be able to perform matching contacts.

* "RECEIVE_SMS" android permission in order to be able to perform receiving new messages.

* "READ_CALL_LOG" and "READ_SMS" android permission in order to be able to perform checking if a call is repeated.

* "PACKAGE_USAGE_STATS" android permission in order to be able to perform feature: Recent Apps, only for checking whether an app has been used recently.

* "READ_PHONE_STATE" android permission in order to be able to perform block mode: Answer + Hang-up (monitor ringing state).
