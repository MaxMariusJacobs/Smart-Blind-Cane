# Smart-Blind-Cane (AI Assistance System)

An AI-powered camera system for the visually impaired. The project combines real-time object detection (YOLOv11) via Edge-AI with detailed environment analysis (Google Gemini) and navigates the user using spatial audio feedback.

**IMPORTANT HARDWARE NOTICE:**
The Android app is deeply optimized for the Tensor architecture of Google Pixel smartphones (tested on Pixel 7a). Due to specific TensorFlow-Lite GPU delegates and restrictive background audio protocols (MediaSession/Volume broadcasts), flawless execution on devices from other manufacturers is not guaranteed.

## Hardware Requirements

1. **Google Pixel Smartphone** (Android 14+)
2. **ESP32-S3 Camera Module** (e.g., Freenove ESP32-S3 WROOM) with active PSRAM.
3. High-quality USB-C data cable (to prevent voltage drops on the ESP32).

## Part 1: Flashing the ESP32 Firmware

1. **Prepare Arduino IDE:**
* Install the `esp32` board library by Espressif in the Arduino IDE.
* Select board: **ESP32S3 Dev Module**.
* Strictly enable in the tool settings: `PSRAM: "OPI PSRAM"` (or QSPI, depending on your exact board).


2. **Configure Network:**
* Open `esp32_firmware/esp32_camera.ino`.
* Enter the exact SSID and password of your smartphone hotspot in lines 13 and 14.


3. **Flash:**
* Compile the code and upload it to the board.
* Open the Serial Monitor (115200 baud). The board is now waiting for the Wi-Fi connection.



## Part 2: Compiling the Android App

1. **Open Project:**
* Clone this repository and open it in Android Studio.


2. **Set API Keys:**
* Create a file named `local.properties` in the root directory of the project (on the same level as `build.gradle.kts`).
* Add your Google Gemini API key in exactly this format:
`GEMINI_API_KEY=Your_API_Key_Here`


3. **Install App:**
* Connect the Pixel smartphone via USB (USB debugging enabled).
* Build the project and install the app (`Shift + F10`).
* On the first launch, it is mandatory to grant all requested permissions (Camera, Location, Audio, Background Services).
* Also go into the accessibility-Settings and grant the App permission there. 



## Part 3: Setup & Usage

1. **Strictly Adjust Smartphone Hotspot:**
* Go to the hotspot settings of your Pixel smartphone.
* **Critical:** Enable the toggle for **"Extend compatibility"**. This forces the 2.4 GHz band, which the ESP32 requires.
* Turn on the hotspot and completely **disable Bluetooth** (prevents TCP packet congestion caused by antenna interference).


2. **Establish Connection:**
* Connect the ESP32 to a power bank or PC.
* Open the app. The IP address of the ESP32 is displayed in the Serial Monitor of the Arduino IDE (defaults often to `[http://192.168.4.1/stream](http://192.168.4.1/stream)` or a local hotspot IP).
* Enter the IP on the start screen of the app and press "Preview & Test Mode" (foreground) or "Background Mode" (with locked display).


3. **Everyday Operation:**
* **Live Tuning:** Warning distances and walking corridors can be calibrated separately for the smartphone and ESP32 camera via the gear icon.
* **Scene Analysis (Gemini):** Press one of the **volume buttons**, followed by an immediate counter-movement (e.g., Volume Up and immediately Volume Down within 0.8 seconds). Press the **Power-Button** before, if the screen is off. Normal walking navigation will pause, the system takes a snapshot of the current camera view, and reads out a detailed description of the environment. This also works when the screen is locked and the phone is in your pocket.
