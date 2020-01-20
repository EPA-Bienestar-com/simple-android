![Build Status](https://github.com/simpledotorg/simple-android/workflows/CI/badge.svg)
[![pullreminders](https://pullreminders.com/badge.svg)](https://pullreminders.com?ref=badge)

# Simple

An Android app for recording blood pressure measurements.

## How to build

**Clone the project using git.**

Run the following command in a terminal.

 ```
 $ git clone git@github.com:simpledotorg/simple-android.git
 ```

**Install Android Studio**

Download and install Android Studio from [their website](https://developer.android.com/studio/).

**Import the project into Android Studio.**

When Android Studio starts up, it will prompt you to create a new project or import an existing project. Select the
option to import an existing project, navigate to the `simple-android` directory you cloned earlier, and select it.

When building for the first time, gradle will download all dependencies so it'll take a few minutes to complete.
Subsequent builds will be faster.

## Running locally

The Simple App can be run locally on an Android emulator using Android Studio. To do this,

**Install the NDK library**

The NDK library is currently required by the project to enable an SQLite extension. To install it:

* Open the SDK Manager through Tools -> SDK Manager
* Select Appearance & Behavior -> System Settings -> Android SDK in the left sidebar
* Select the SDK Tools tab in the main window
* Activate NDK (Side by Side) and click Apply

NDK will now be installed.

**Create a Run/Debug configuration**

* Open the Run/Debug configurations window through Run -> Edit Configurations ([ref](https://developer.android.com/studio/run/rundebugconfig))
* Create a new configuration using the `Android App` template
* Set the module to `app`, and finish creating the configuration

**Create a virtual device**

* Create an Android Virtual Device (AVD) using the AVD Manager, usually found in Tools -> AVD Manager. ([ref](https://developer.android.com/studio/run/managing-avds))
* Select a device and operating system
* Note: You will have to download one of the available OS options the first time you create an AVD

**Set the right build variant**

* Open the Build Variants window through View -> Tool Windows -> Build Variants, or clicking the item in the lower left
  corner of the main window
* Set the Build Variant of the app module to `qaDebug`

**Run the app**

* Click "Run", either through Run -> Run, or the green play button in the top toolbar.

## Code styles

The code styles which the project uses have been exported as an IntelliJ code style XML file and are saved as
`quality/code-style.xml`. To import them into Android Studio,

1. Open the Android Studio preferences page, and navigate to Editor -> Code Style.
1. Click on the gear/settings button next to the "Scheme" label.
1. In the drop-down menu, select "Import scheme".
1. In the file picker, navigate to  `<project>/quality/code-style.xml`.
1. Import the `Simple` scheme into the IDE and set it as the project code style.

## Build and deploy Simple Server

Simple Server is in a separate repository, and you should follow the [instructions there](https://github.com/simpledotorg/simple-server/blob/master/README.md).

## Resources

Check out the following documents for more information.

* [Quirks That You Should Probably Be Aware Of](doc/QUIRKS.md)
* [More Documentation](doc)
