# MCGG FAST HAND

Android companion app for configuring hero targets in Magic Chess: Go Go.

## Editions

- **Lite** (`com.mcgg.fasthand.lite`): hero roster, search, LOCK setup and local preferences. It removes the overlay permission, foreground overlay service, and Accessibility Service from the merged Android manifest. It does not display a floating control.
- **Full** (`com.mcgg.fasthand`): includes the floating overlay and foreground-app detection through Android Accessibility settings. These are sensitive Android capabilities and must be enabled deliberately by the device owner.

## Build

GitHub Actions builds two debug APK artifacts:
- `MCGG-FAST-HAND-Lite`
- `MCGG-FAST-HAND-Full`

The Lite APK has a separate application ID so it can be installed alongside the previous Full package.

## Important limitations

This is not yet a complete Auto Roll automation tool. The current release does **not** include game-screen capture, OCR/hero recognition, board or bench recognition, automatic taps/drags, or verified 3-star detection. The decision strategy is currently isolated logic and is not connected to live gameplay. Do not treat the ON control as proof that gameplay automation is operating.

The Full edition requests overlay and Accessibility capabilities for its current foreground-detection/overlay prototype. Google Play Protect may still block a sideloaded build; removing permissions from Lite reduces its requested capabilities but cannot guarantee a Play Protect verdict. Do not disable device protection to install an untrusted APK.

## Signing and distribution

CI debug APKs are for testing. Their debug signing identity is not a stable release identity across independent GitHub runners. A production release must be signed with a persistent, owner-controlled release keystore stored securely (for example, in protected GitHub Actions secrets). Never commit a keystore or passwords to this repository.

## Safety

The app does not inject into or modify the game process and does not claim to bypass anti-cheat. Third-party automation may violate game rules or risk account penalties.
