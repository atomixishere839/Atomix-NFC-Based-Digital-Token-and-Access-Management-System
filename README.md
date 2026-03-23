# Atomix - NFC Digital Token & Access Management System

An Android application built with Kotlin that implements an NFC-based digital token and access management system. Atomix allows users to scan NFC cards, identify card types, read/write data, and use cards as digital coin wallets, gate passes, and access control systems.

## Features

### 1. NFC Detection & Card Identification
- Detects NFC tags using ISO 14443A
- Displays UID in HEX format
- Identifies card types:
  - MIFARE Classic 1K / 4K
  - MIFARE Ultralight
  - Unsupported tags (shows proper message)
- Shows card tech list on screen

### 2. Card Reading
- Reads stored data from NFC card blocks (for MIFARE Classic)
- Authenticates using default keys (Key A / Key B)
- Displays stored values:
  - USER_ID
  - BALANCE
  - CARD_ROLE (USER / ADMIN)

### 3. Card Writing
- Writes structured data to card:
  - Format: `USER|BALANCE|YEAR`
- Prevents overwrite unless ADMIN role is authenticated
- Shows success/failure messages clearly

### 4. Digital Coin System
- Each card holds dummy coins (default: 500 Atomix Coins)
- Coins can be:
  - Added by ADMIN
  - Spent by USER
- Updates balance both on card and server

### 5. Roles
- **USER role:**
  - Can view balance
  - Can spend coins
  
- **ADMIN role:**
  - Can issue cards
  - Can recharge coins
  - Can reset cards

### 6. Server Integration (Basic)
- Simple backend (REST API)
- Stores:
  - UID
  - BALANCE
  - LAST_SCAN_TIME
- Syncs card data with server
- Offline-first: card works without internet, syncs when online

### 7. UI/UX
- Clean dark theme
- Single activity
- Status text displays:
  - "Scan NFC Card"
  - "Card Detected"
  - "Reading Data..."
  - "Writing Successful"
- Buttons:
  - Read Card
  - Write Card
  - Add Coins (Admin only)
  - Spend Coins (User only)

### 8. NFC Handling
- Uses Foreground Dispatch (no auto app switching when closed)
- App responds only when opened
- Shows error if NFC is disabled

## Project Structure

```
atom/
├── app/
│   ├── build.gradle.kts          # App-level Gradle configuration
│   ├── src/
│   │   └── main/
│   │       ├── AndroidManifest.xml
│   │       ├── java/com/atomix/app/
│   │       │   ├── MainActivity.kt       # Main activity with NFC handling
│   │       │   ├── CardType.kt           # Card type enum
│   │       │   ├── UserRole.kt           # User role enum
│   │       │   ├── CardData.kt           # Card data model
│   │       │   ├── ServerCardData.kt     # Server data model
│   │       │   ├── NfcCardHandler.kt     # NFC read/write operations
│   │       │   └── ApiService.kt         # REST API service
│   │       └── res/
│   │           ├── layout/
│   │           │   └── activity_main.xml # Main UI layout
│   │           ├── values/
│   │           │   ├── strings.xml       # String resources
│   │           │   ├── colors.xml        # Color definitions
│   │           │   └── themes.xml        # App theme
│   │           └── xml/
│   │               └── nfc_tech_filter.xml # NFC tech filter
│   └── proguard-rules.pro
├── build.gradle.kts               # Root-level Gradle configuration
├── settings.gradle.kts            # Project settings
├── gradle.properties              # Gradle properties
└── README.md                      # This file
```

## Requirements

- Android SDK: Minimum 24 (Android 7.0), Target 34 (Android 14)
- NFC hardware support
- Kotlin 1.9.20+
- Android Gradle Plugin 8.2.0+

## Setup Instructions

1. **Clone or extract the project** to your desired location

2. **Open in Android Studio:**
   - File → Open → Select the `atom` folder
   - Wait for Gradle sync to complete

3. **Configure Server URL (Optional):**
   - Edit `app/src/main/java/com/atomix/app/ApiService.kt`
   - Update `BASE_URL` constant with your server endpoint:
     ```kotlin
     private const val BASE_URL = "http://your-server.com/api"
     ```

4. **Build and Run:**
   - Connect an NFC-enabled Android device
   - Run the app from Android Studio
   - Ensure NFC is enabled on your device

## Usage

### Reading a Card
1. Tap an NFC card to your device
2. The app will detect and display card information
3. Tap "Read Card" to read stored data
4. Card data (User ID, Balance, Role) will be displayed

### Writing a Card
1. Ensure a card is detected
2. Tap "Write Card"
3. Enter User ID when prompted
4. Card will be initialized with:
   - Default balance: 500 Atoms
   - Role: USER
   - Current year

### Adding Coins (Admin Only)
1. Read a card with ADMIN role
2. Tap "Add Coins" button
3. Enter the amount to add
4. Balance will be updated on card and server

### Spending Coins (User Only)
1. Read a card with USER role
2. Tap "Spend Coins" button
3. Enter the amount to spend
4. Balance will be deducted if sufficient funds are available

## Data Format

Card data is stored in the format: `USER|BALANCE|YEAR`

Example: `user123|500|2024`

## Default MIFARE Keys

The app uses default MIFARE keys for authentication:
- **Key A:** `FF FF FF FF FF FF`
- **Key B:** `FF FF FF FF FF FF`

⚠️ **Note:** These are default keys. For production use, implement proper key management.

## Security Considerations

- The app uses block authentication to prevent unauthorized writes
- ADMIN role is required to write/overwrite existing cards
- Card data is validated before reading/writing
- Offline-first design ensures cards work without server connection

## API Endpoints

The app expects the following REST API endpoints:

- `POST /api/cards` - Sync card data
- `GET /api/cards/{uid}` - Get card data by UID
- `PUT /api/cards/{uid}/balance` - Update card balance

## Troubleshooting

### NFC Not Detected
- Ensure NFC is enabled in device settings
- Check that the device supports NFC
- Ensure the card is compatible (MIFARE Classic or Ultralight)

### Read/Write Failed
- Verify card authentication keys are correct
- Ensure card has sufficient memory
- Try holding the card closer to the device

### Server Sync Failed
- Check internet connection
- Verify server URL is correct
- Check server logs for errors

## License

This project is for educational purposes (college project).

## Author

Built for demonstrating NFC-based digital currency, access control, and card management in a unified system.
