# Contribution Documentation

__Any help is greatly appreciated.__

### Table of Contents

- [For translator](#for-translator)
- [For coder/developer](#for-coderdeveloper)
- [Build from source](#build-from-source)
- [New API presets](#new-api-presets)

## For translator:

Languages are translated using AI, it's improving, but still cannot fully replicate the precision of human translation. Feel free to propose new translations, or to update any existing one. You can translate it then share it via a [pull request](https://github.com/aj3423/SpamBlocker/pulls)/[issue](https://github.com/aj3423/SpamBlocker/issues/new).

#### Files to translate:

-  All [`strings_.xml`](../app/src/main/res/values/strings_1.xml) files in the [`values`](../app/src/main/res/values) folder 
    - Make sure to insert a backslash `\` before any apostrophe `'` or quotes `"`

#### Note
* if you are unsure the locale prefix of a specific language you can find it [here](https://countrycode.org/).

#### Explanation
* AI can't handle large files, that's why the strings.xml is split into multiple small files.


## For coder/developer:
Please try to keep new codes similar to existing ones, with just a couple notes:

- Please write comments.
- Please discuss first if you want to contribute a new feature.


## Build from source

This is a small guide for how to build and run this application with Android Studio:

1. Download Android Studio from [here](https://developer.android.com/studio).
2. Clone this github repository to your local workspace: `git clone https://github.com/aj3423/SpamBlocker.git`.
3. Prepare a device emulator from the menu: Tools->Device Manager, follow the step-by-step guide.
4. Run the app. If step 3 was completed you should be able to just press the 'play' button (green triangle) at the top.
5. If everything goes well, you should be able to see the app installed in your emulator and be able to run it smoothly.

## New API presets

If you know a public database or API service that can be added to the presets, please report. 
- PRs are welcome for public services
- For proprietary services, I'll contact them, asking for the permission of the integration. It will only be added with their permission.
  - If you have contacted them and got positive reply, you can fire a PR with a preset template, I appreciate that and will be happy to merge it.
    - When contacting them, please make sure to clarify:
      - This app will not share numbers identified by APIs with others, it will only share numbers that were blocked by local rules. Because no provider would allow their numbers to be leaked to competitors.
