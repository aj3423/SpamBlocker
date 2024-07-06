# SpamBlocker
An Android Call/SMS blocker. (Android 10+)

<img src="https://github.com/aj3423/SpamBlocker/assets/4710875/9d44afe7-2524-4b34-8bf3-ba285200bb5c" height="100">[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/spam.blocker/)

# Screenshot
| Call        | SMS         | Setting     | Notification |
| ----        | ----        | ----        | ----         |
| <img src="https://github.com/aj3423/SpamBlocker/assets/4710875/7f03d0a0-d12e-4e1b-a064-2412fc1cee8e" width="200"> | <img src="https://github.com/aj3423/SpamBlocker/assets/4710875/ff1dd6c3-56dc-4f64-96a5-e7ca379af035" width="200"> | <img src="https://github.com/aj3423/SpamBlocker/assets/4710875/a86fff09-d30b-428e-866c-0f07b874d479" width="200"> | <img src="https://github.com/aj3423/SpamBlocker/assets/4710875/633e0e24-5ba0-44d7-90ec-09324081d37b" width="200">  |


# Features
|                                                    | For Call                                                                                                                                               | For SMS                                                                                                        |
| ----                                               | ----                                                                                                                                               | ----                                                                                                       |
| What it does                                       | Block unwanted calls                                                                                                                               | Silence unwanted notificaions                                                                               |
| What it doesn't                                    | Replace the default call app                                                                                                                       | Replace the default SMS app                                                                                |
| How it works                                       | Act as [CallScreeningService](https://developer.android.com/reference/android/telecom/CallScreeningService),<br>aka the default caller ID & spam app | It takes over the notification of new messages |
| Filters supported<br>(explained below) | 1. Phone number (regex)<br>2. Contacts<br>3. STIR<br>4. Repeated call<br>5. Recent apps<br>6. Dialed                                                                     | 1. Phone number (regex)<br>2. Contacts<br>3. SMS content (regex)                                        |

# Filters:

| Filter   | It checks                                       |
| ----     | ----                                            |
| Contacts | Whether it's from a contact                     |
| STIR     | STIR attestation result                         |
| Repeated | Whether the number has been calling repeatedly  |
| Dialed   | Whether the number has been made outgoing calls |
| Recent Apps | If some specific apps have been used recently, all calls are allowed.<br><br> A typical use case:<br> &emsp;You ordered a pizza in PizzaApp, soon they call you to refund because they are closing. That call would be permitted if PizzaApp is enabled in this list. |
| Off Time  | A time period that always permits calls, usually no spams at night. |
| Regex Pattern | Some typical patterns:<br> - Any number: `.*` (the regex `.*` is identical to the wildcard `*` in many other apps) <br> - Exact number: `12345` <br> - Starts with 400: `400.*` <br> - Ends with 123: `.*123` <br> - Shorter than 5: `.{0,4}` <br> - Longer than 10: `.{11,}` <br> - Contains "verification": `.*verification.*` <br> - Contains any of the words: `.*(police\|hospital\|verification).*` <br> - Starts with 400, has country code 11 or not: `(?:11)?400.*` <br>- Extract verification code from SMS message: `code.*?(\d+)`<br><br> You can ask AI to generate or explain regex for you: <br>&emsp; "Show me regex for checking if a string starts with 400 or 200"<br> &emsp; Results in `(400\|200).*` |


# Permissions (all optional)

| Permission             | Why                                                             |
| ----                   | ----                                                            |
| ANSWER_PHONE_CALLS     | Reject, Answer and Hang-up calls                                |
| POST_NOTIFICATIONS     | Show notifications                                              |
| READ_CONTACTS          | For matching contacts                                           |
| RECEIVE_SMS            | For receiving new messages                                      |
| READ_CALL_LOG<br>READ_SMS | For feature: Repeated Call/Dialed (check if it's repeated)   |
| PACKAGE_USAGE_STATS    | For feature: Recent Apps <br>For checking whether an app has been used recently  |
| READ_PHONE_STATE       | For block mode: Answer + Hang-up (monitor ringing state)  |

# FAQ & Troubleshooting 

| Problem             | Solution                                                             |
| ----                   | ----                                                            |
| SMS notification doesn't work after app is killed    | Enable "app auto start" in battery settings:<br> &emsp; System Settings -> Battery -> ... -> Auto-start Manager -> enable SpamBlocker  |
| How to always block particular number regardless of how many times it repeats, or within OffTime, etc...  | Set a regex rule with <b>higher priority</b> than 10 |

# Languages supported

Languages are translated using Gemini AI([golang script](https://github.com/aj3423/SpamBlocker/blob/master/auto_translate/translate.go)), fire an issue for requesting a new language support.

# Donation <3

https://aj3423.github.io/donate

