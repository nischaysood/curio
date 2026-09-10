# iosApp

The iOS shell. Three Swift files; everything else comes from `composeApp`.

## What's here and why

| File | Why it's Swift and not Kotlin |
|---|---|
| `CurioApp.swift` | App entry point, hosts `ComposeUIViewController`, configures RevenueCat, registers the bridges |
| `KeychainStore.swift` | The Security framework from Kotlin/Native is hand-built `CFDictionary` pointer work; from Swift it's legible |
| `RevenueCatBilling.swift` | `purchases-ios` is a Swift Package with no Kotlin bindings |

Kotlin declares what it needs in `IosBridge.kt` as plain interfaces. Kotlin/Native
exports those to Swift as protocols, so the Swift above is ordinary Swift with no
cinterop.

## Xcode setup

These steps have to happen in the Xcode GUI once.

### 1. Add the files to the target

Drag `Curio/*.swift` into the project navigator. **Uncheck "Copy items if needed"**
— they should stay where they are so git tracks one copy.

### 2. Build the Kotlin framework before each Swift build

Target → **Build Phases** → **+** → **New Run Script Phase**. Drag it **above**
"Compile Sources", name it `Build Kotlin framework`, and paste:

```sh
cd "$SRCROOT/.."
./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
```

Then uncheck **"Based on dependency analysis"** so it runs every time — Gradle
already does its own up-to-date checking, and Xcode's version of that check gets
this wrong.

### 3. Point the linker at the framework

Target → **Build Settings** (All / Combined):

| Setting | Value |
|---|---|
| Framework Search Paths | `$(SRCROOT)/../composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)` |
| Other Linker Flags | `$(inherited) -framework ComposeApp` |

### 4. Add RevenueCat

**File → Add Package Dependencies…** →
`https://github.com/RevenueCat/purchases-ios` → Up to Next Major → add
**RevenueCat** (not `RevenueCatUI`; the paywall is Curio's own).

### 5. Deployment target

iOS **15.0** or higher. Compose Multiplatform doesn't support anything older.

## Before submitting to the App Store

- [ ] `BillingKeys.IOS_API_KEY` set to the `appl_` key from RevenueCat
- [ ] Products created in App Store Connect and imported into RevenueCat
- [ ] Paid Applications agreement signed — StoreKit returns nothing without it
- [ ] Tested a sandbox purchase on a real device (StoreKit sandbox is unreliable
      on the simulator)
