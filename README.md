# ESP32 OLED AirCanvas Android App

This is a complete native Android application designed to control an ESP32-powered OLED drawing system.

## Features
- **Real-time Drawing**: Low-latency WebSocket communication.
- **OLED Mapping**: Precise 128x64 coordinate mapping with 2:1 aspect ratio.
- **Full Toolset**: Pen, Eraser, Line, Rectangle, Circle, and Clear functions.
- **Customizable Sizes**: Independent thickness settings for brush and eraser.
- **Local Preview**: Live visual representation of the OLED state.
- **Automatic Reconnection**: Handles WebSocket drops gracefully.

## Communication Protocol
The app connects to the ESP32 Access Point:
- **SSID**: `OLED_AirCanvas`
- **IP**: `192.168.4.1`
- **WebSocket Port**: `81`

**Command Format**: `CMD,x1,y1,x2,y2,size`
- `W`: Pen
- `B`: Eraser
- `L`: Line
- `R`: Rectangle
- `C_S`: Circle
- `C`: Clear
- `FC`: Force Clear
- `D_S`: Demo Start
- `D_E`: Demo End

## Build Instructions

### Prerequisites
- Android Studio Flamingo or newer.
- JDK 17.

### Steps to Build
1. Open Android Studio.
2. Select **Open Project** and navigate to the `AirCanvasAndroid` folder.
3. Wait for Gradle to sync.
4. Connect your Android device via USB (Enable USB Debugging).
5. Click **Run 'app'** (Green Play button).

### Generating APK
1. Go to `Build` -> `Build Bundle(s) / APK(s)` -> `Build APK(s)`.
2. Once complete, click the notification to locate the `.apk` file.
3. Transfer the APK to your device and install.

## Testing Checklist
- [ ] Connect to `OLED_AirCanvas` Wi-Fi.
- [ ] Open app and click **Connect**.
- [ ] Draw with Pen; verify lines appear on physical OLED.
- [ ] Use Eraser; verify lines are removed.
- [ ] Test coordinates at the corners (0,0 and 127,63).
- [ ] Test "Clear" button.
- [ ] Test "Demo" button.
- [ ] Rotate device and verify layout responsiveness.
