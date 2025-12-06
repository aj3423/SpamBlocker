# SpamBlocker
Android Call/SMS blocker. (Android 10+)

<p>
  <img src="https://github.com/aj3423/SpamBlocker/assets/4710875/9d44afe7-2524-4b34-8bf3-ba285200bb5c" alt="SpamBlocker" height="90" />

  <a href="https://f-droid.org/packages/spam.blocker">
    <img src="https://github.com/user-attachments/assets/8757c78c-b0d5-4b8a-9adb-934d8a758e9e" alt="Get it on F-Droid" height="60" />
  </a>
  <a href="https://github.com/ImranR98/Obtainium"> 
    <img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="60" />
  </a>
  <a href="https://github.com/aj3423/SpamBlocker/releases/latest"> 
    <img src="https://github.com/user-attachments/assets/75d2f736-ba69-4173-b972-6f69a1804e85" alt="Get it on Github" height="60" />
  </a>
</p>


Table of Contents
=================
   * [Screenshot](#screenshot)
   * [How it works](#how-it-works)
   * [Features](#features)
   * [Limitations](#limitations)
   * [Permissions](#permissions)
   * [Privacy](#privacy)
   * [Support](#support)
   * [FAQ](#faq)
   * [Language Support](#language-support)
   * [Contributing](#contributing)
   * [Some ideas](#some-ideas)
   * [Donate](#donate)

# Screenshot
| History | Settings | Notification |
|----|----|----|
| <img src="https://github.com/user-attachments/assets/abfac64c-83e8-445b-b92a-8826e87156ed" width="200" height="400"> | <img src="https://github.com/user-attachments/assets/17a927a3-7d18-486b-b43e-943e22fc55b7" width="200" height="400"> | <img src="https://github.com/user-attachments/assets/70cb5537-1b29-49e8-be0e-47d362ae3ebc" width="200" height="400"> |

# How it works
It works without replacing your call/SMS app.
 - For call: <br>
 &ensp; It's the Caller ID app.
 - For SMS: <br>
 &ensp; It takes over SMS notifications but only filters them, spam messages will still appear in the SMS app.

You can kill the app after setup, it doesn’t need to stay running in the background.

# Features:

| Filter                        | It checks |
|-------------------------------|----------|
| Contacts                      | From a contact? |
| STIR/SHAKEN                   | Fails STIR/SHAKEN attestation? |
| Repeated                      | Multiple calls from the same number in a short while? |
| Dialed                        | Have you dialed the number? |
| Push Alert                    | Allow calls after receiving notifications from other apps, e.g.: "Your order has been taken by driver ...", the driver may then contact you. | 
| SMS Alert                     | Allow calls after receiving SMS messages like: "[From ...] We are calling to inform ..., please feel free to answer." | 
| Recent Apps                   | Allow calls if some apps have been used recently.<br>Use case:<br>&emsp; You ordered Pizza online and soon they call you to refund. |
| Meeting Mode                  | Decline calls during online video meetings. |
| Off Time                      | A time period that always allows calls, usually no spams at night. |
| Spam Database                 | If it exists in the spam database. Any public downloadable spam databases can be integrated, such as the [DNC](https://www.ftc.gov/policy-notices/open-government/data-sets/do-not-call-data). |
| Instant Query                 | Check the incoming number online in real time, querying multiple API endpoints simultaneously, such as the [PhoneBlock](https://phoneblock.net/). |
| Report Spam                   | Automatically or manually report the number to build our crowd-sourced databases, protecting others and yourself.  |
| Regex<br>(regular expression) | Check the [Wiki](https://github.com/aj3423/SpamBlocker/wiki/Regex-Workflow-Templates) for examples.<br><br>Some typical patterns:<br> - Any number: `.*` (the regex `.*` is equivalent to the wildcard `*` in other apps) <br> - Unknown/Private/Empty number (it's actually empty text): `.{0}` or `^$`<br> - Exact number: `12345` <br> - Start with 789: `789.*` <br> - End with 123: `.*123` <br> - 7 digits: `.{7}` <br> - Shorter than 5: `.{0,4}` <br> - Longer than 10: `.{11,}` <br> - Contain word "verification": `.*verification.*` <br> - Contain any of the words: `.*(police\|hospital\|verification).*` <br> - Start with 789, with leading country code 11 or not: `(?:11)?789.*` <br>- Extract verification code from SMS message: `code.*?(\d+)` |


# Limitations 
- Auto clear SMS: [No plan](https://github.com/aj3423/SpamBlocker/issues/274)
- Local AI support: [Future plan, not yet ready](https://github.com/aj3423/SpamBlocker/issues/267#issuecomment-2632229803)
- RCS support: [No plan](https://github.com/aj3423/SpamBlocker/issues/308#issuecomment-2692269430)

# Permissions 

| Permission (all optional)                                                         | Why                                                                    |
|-----------------------------------------------------------------------------------|------------------------------------------------------------------------|
| INTERNET                                                                          | For database downloading / instant query / number reporting            | 
| MANAGE_EXTERNAL_STORAGE (Android 11+)<br>READ/WRITE_EXTERNAL_STORAGE (Android 10) | For file access via automated workflow                                 | 
| ANSWER_PHONE_CALLS                                                                | Reject, answer and hang-up calls                                       |
| POST_NOTIFICATIONS                                                                | Show notifications                                                     |
| READ_CONTACTS                                                                     | Match contacts                                                         |
| RECEIVE_SMS / RECEIVE_MMS                                                         | For SMS notification screening                                         |
| READ_CALL_LOG<br>READ_SMS                                                         | For allowing repeated calls                                            |
| PACKAGE_USAGE_STATS                                                               | For feature: Recent Apps (check whether an app has been used recently) |
| READ_PHONE_STATE                                                                  | For BlockMode: Answer+Hang-up (monitor ringing state)                  |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS                                              | For it to keep working after being swiped and killed                   |
| NOTIFICATION_ACCESS                                                               | For feature: Push Alert (receiving notifications from other apps)      |
| WRITE_SETTINGS                                                                    | For customizing call ringtone                                          | 
| READ_LOG                                                                          | For reporting bugs with logcat messages                                |   

# Privacy
 - For offline features

   No data collection.

 - For online features:

   The API endpoints will see your:

     - IP address
     - TLS and TCP fingerprints (which would reveal your Android version)
     - The reported number(including the country code)

   Nothing else.

   You can also [disable the internet access](https://github.com/aj3423/SpamBlocker/issues/147) , or download the offline apk from the release page.
 - No communication with other apps
 - [Reproducible](https://f-droid.org/docs/Reproducible_Builds/) apk
 - Apk signing signature:

    `apksigner verify --print-certs SpamBlocker.apk`
    > 7b1ce727856f3427eab1fadfad6c9730cd4e6ba201661547f009206377dffb58

Full [Privacy Policy](https://github.com/aj3423/SpamBlocker/blob/master/Docs/PRIVACY%20POLICY.md)

# Support
 - Most problems are already covered in the issue list, please search first.
 - There's a [matrix channel](https://matrix.to/#/#spam-blocker:matrix.org).

# FAQ
 - [Security warning from Google Play when installing this app](https://github.com/aj3423/SpamBlocker/issues/108)
 - [How the "Priority" works](https://github.com/aj3423/SpamBlocker/issues/166)
 - [It stops working after being killed](https://github.com/aj3423/SpamBlocker/issues/100)
 - [Android 9- support](https://github.com/aj3423/SpamBlocker/issues/38)

# Language support

Languages are translated using Gemini AI([golang script](https://github.com/aj3423/SpamBlocker/blob/master/auto_translate/translate.go)), fire an issue for requesting a new language support.
PRs for corrections are welcome.

# Contributing
 - [Contributing Guidelines](https://github.com/aj3423/SpamBlocker/blob/master/Docs/CONTRIBUTING.md)

# Some ideas
 - [A decentralized database](https://github.com/aj3423/SpamBlocker/issues/340)

# Donate

:heart:  https://aj3423.github.io/donate
