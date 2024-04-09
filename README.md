# SpamBlocker
An Android app for filtering spamming Call/Sms.

![logo](https://github.com/aj3423/SpamBlocker/assets/4710875/20930282-db38-4c21-a0db-4720ad666151)


|                                                    | For Call                                                                                                                                               | For Sms                                                                                                        |
| ----                                               | ----                                                                                                                                               | ----                                                                                                       |
| What it does                                       | Block unwanted calls                                                                                                                               | Silence unwanted notificaion                                                                               |
| What it doesn't                                    | Replace the default call app                                                                                                                       | Replace the default sms app                                                                                |
| How it works                                       | Act as [CallScreeningService](https://developer.android.com/reference/android/telecom/CallScreeningService),<br>aka the default caller ID & spam app | Turn off the notification of the default sms app(note: you need to turn it off manually in settings), this app takes over the notification of incoming message |
| Filters supported<br>([explained below](#Filters)) | 1. Phone number (regex)<br>2. In Contacts<br>3. Repeated call<br>4. Recent apps                                                                     | 1. Phone number (regex)<br>2. In Contacts<br>3. Sms content (regex)                                        |



# Filters:
## 1. Phone number and Sms content

Regex is used, ask AI if you don't know how to write one, eg: 
"Show me regex for checking if a string starts with 400 and has a length of 10", which results in `^400.{7}$`

Some typical patterns:
- Any number: `.*`
- Starts with 400: `^400.*`
- Longer than 10: `.{11,}`
- Content contain "verification": `.*verification.*`

## 2. In Contacts
Checks whether the phone number belongs to a contact.

## 3. Repeated Call
It will be allowed if the number has been calling you multiple times whin 5 minutes.

## 4. Recent Apps
Any call would be permitted if any of these apps has been used within 5 minutes.

- A typical use case: 

You ordered a pizza in PizzaApp, soon they call you to refund because they are closing. That call would be permitted if PizzaApp is enabled in Recent Apps list.


# Permissions required

| Permission          | Why                                                                          |
| ----                | ----                                                                         |
| READ_CALL_LOG       | For feature: Recent call (checking if a number is repeated within 5 min)     |
| ANSWER_PHONE_CALLS  | Reject spamming call                                                         |
| POST_NOTIFICATIONS  | Show notifications                                                           |
| READ_CONTACTS       | For matching contact number and showing contact avatar                       |
| RECEIVE_SMS         | For receiving new incoming message                                           |
| PACKAGE_USAGE_STATS | For feature: Recent Apps (checking whether an app has been used within 5 min) |
| QUERY_ALL_PACKAGES  | For feature: Recent Apps (listing all apps for choosing)                      |

