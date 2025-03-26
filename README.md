# SpamBlocker
Android Call/SMS blocker. (Android 10+)

<p>
  <img src="https://github.com/aj3423/SpamBlocker/assets/4710875/9d44afe7-2524-4b34-8bf3-ba285200bb5c" alt="SpamBlocker" height="90" />
  <a href="https://f-droid.org/packages/spam.blocker">
    <img src="https://github.com/user-attachments/assets/8757c78c-b0d5-4b8a-9adb-934d8a758e9e" alt="Get it on F-Droid" height="70" />
  </a>
  <a href="https://github.com/ImranR98/Obtainium"> 
    <img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="70" />
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
   * [Donate](#donate)

# Screenshot
| Call / SMS  | Report spam  | Setting  | Notification  |
|-------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| <img src="https://github.com/aj3423/SpamBlocker/assets/4710875/7f03d0a0-d12e-4e1b-a064-2412fc1cee8e" width="200"> | <img src="https://github.com/user-attachments/assets/d4ca58f9-72fd-48c3-8406-bb92ce958a79" width="200"> | <img src="https://github.com/aj3423/SpamBlocker/assets/4710875/a86fff09-d30b-428e-866c-0f07b874d479" width="200"> | <img src="https://github.com/aj3423/SpamBlocker/assets/4710875/633e0e24-5ba0-44d7-90ec-09324081d37b" width="200"> |

# How it works
It works without replacing your default call/SMS app.
 - For call: <br>
 &ensp; It's the Caller ID app 
 - For SMS: <br>
 &ensp; It takes over the SMS notifications, it only filters the notifications, the spam messages will still be present in the SMS app.
   > 💡 Please turn off the notification permission of the default SMS app in system settings, otherwise there will be double SMS notifications.

It's not necessary to leave this app running in the background, you can kill the process after it's configured, if it doesn't work, please refer to [this](https://github.com/aj3423/SpamBlocker/issues/100).

# Features:

| Filter                        | It checks                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
|-------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Contacts                      | Whether from a contact                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| Contact Group                 | Whether it's a member of some contact group                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| STIR/SHAKEN                   | STIR/SHAKEN attestation                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| Repeated                      | Whether it's been calling repeatedly                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| Dialed                        | Whether you have dialed the number                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| Call Alert                    | Allow calls after receiving SMS messages like: "[From ...] We are calling to inform ..., please feel free to answer."                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | 
| Recent Apps                   | Allow calls if some apps have been used recently.<br>Use case:<br>&emsp; You ordered Pizza online and soon they call you to refund.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| Meeting Mode                  | Decline calls during online video meetings.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| Off Time                      | A time period that always allows calls, usually no spams at night.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| Spam Database                 | If it matches any spam number in the database. Any public downloadable spam databases can be integrated, such as the [DNC](https://www.ftc.gov/policy-notices/open-government/data-sets/do-not-call-data).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| Instant Query                 | Check the incoming number online in real time, querying multiple API endpoints simultaneously, such as the [PhoneBlock](https://phoneblock.net/).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| Report Spam                   | Automatically or manually report the number to build our crowd-sourced databases, protecting others and yourself.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| Regex<br>(regular expression) | Check the [Wiki](https://github.com/aj3423/SpamBlocker/wiki/Regex-Workflow-Templates) for examples.<br><br>Some typical patterns:<br> - Any number: `.*` (the regex `.*` is equivalent to the wildcard `*` in other apps) <br> - Exact number: `12345` <br> - Start with 400: `400.*` <br> - End with 123: `.*123` <br> - 7 digits: `.{7}` <br> - Shorter than 5: `.{0,4}` <br> - Longer than 10: `.{11,}` <br> - Unknown number (it's actually empty text): `.{0}` or `^$`<br>  - Contain word "verification": `.*verification.*` <br> - Contain any of the words: `.*(police\|hospital\|verification).*` <br> - Start with 400, with leading country code 11 or not: `(?:11)?400.*` <br>- Extract verification code from SMS message: `code.*?(\d+)` |


# Limitations 
- Auto clear SMS: [No plan](https://github.com/aj3423/SpamBlocker/issues/274)
- Dual SIM support: [Waiting for Google](https://github.com/aj3423/SpamBlocker/issues/169)
- Local AI support: [Future plan, not yet ready](https://github.com/aj3423/SpamBlocker/issues/267#issuecomment-2632229803)
- RCS support: [No plan](https://github.com/aj3423/SpamBlocker/issues/308#issuecomment-2692269430)

# Permissions 

| Permission (all optional)                                                         | Why                                                                    |
|-----------------------------------------------------------------------------------|------------------------------------------------------------------------|
| INTERNET                                                                          | For database downloading / instant query / number reporting            | 
| MANAGE_EXTERNAL_STORAGE (Android 11+)<br>READ/WRITE_EXTERNAL_STORAGE (Android 10) | For file access from automated workflow                                | 
| ANSWER_PHONE_CALLS                                                                | Reject, answer and hang-up calls                                       |
| POST_NOTIFICATIONS                                                                | Show notifications                                                     |
| READ_CONTACTS                                                                     | For matching contacts                                                  |
| RECEIVE_SMS / RECEIVE_MMS                                                         | For receiving new messages                                             |
| READ_CALL_LOG<br>READ_SMS                                                         | For checking if a call is repeated                                     |
| PACKAGE_USAGE_STATS                                                               | For feature: Recent Apps (check whether an app has been used recently) |
| READ_PHONE_STATE                                                                  | For BlockMode: Answer+Hang-up (monitor ringing state)                  |

# Privacy
 - For offline features

   No data collection.

 - For online features:

   The API endpoints will see your:

     - IP address
     - TLS and TCP fingerprints (which would reveal your Android version)
     - The reported number(including the country code)

   Nothing else.

   You can also [disable the internet access](https://github.com/aj3423/SpamBlocker/issues/147) if
   you want, or download the offline apk from the release page.
 - No communication with other apps
 - [Reproducible](https://f-droid.org/docs/Reproducible_Builds/) apk
 - Apk signing signature:

    `apksigner verify --print-certs SpamBlocker.apk`
    > 7b1ce727856f3427eab1fadfad6c9730cd4e6ba201661547f009206377dffb58

Full [Privacy Policy](https://github.com/aj3423/SpamBlocker/blob/master/Docs/PRIVACY%20POLICY.md)

# Support
 - Most problems have already been discussed in the issue list, please search first.
 - There's also a [matrix channel](https://matrix.to/#/#spam-blocker:matrix.org)

# FAQ
 - [Security warning from Google Play when installing this app](https://github.com/aj3423/SpamBlocker/issues/108)
 - [How does the "Priority" work, how to always block a particular number regardless of any other rules](https://github.com/aj3423/SpamBlocker/issues/166)
 - [It stopped working after being killed](https://github.com/aj3423/SpamBlocker/issues/100)
 - [Android 9- support](https://github.com/aj3423/SpamBlocker/issues/38)

# Language support

Languages are translated using Gemini AI([golang script](https://github.com/aj3423/SpamBlocker/blob/master/auto_translate/translate.go)), fire an issue for requesting a new language support.

# Contributing
 - [Contributing Guidelines](https://github.com/aj3423/SpamBlocker/blob/master/Docs/CONTRIBUTING.md)

# Donate

:heart:  https://aj3423.github.io/donate
