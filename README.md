# TFC Stream Manager

An Android application for managing and displaying live scores for table football (foosball) matches, specifically designed for TFC Leipzig and MTFV (Mitteldeutscher Tischfußballverband) league matches.

## Features

- **Live Score Management**: Track and update scores for two teams in real-time
- **Network Service Discovery (NSD)**: Automatically discovers and connects to TFCStreamServer on the local network via mDNS
- **Player Selection**: Select up to two players per team from the league database
- **MTFV League Integration**: Fetches team and player data from the MTFV league API
- **Persistent Storage**: Saves scores and game state locally via SharedPreferences
- **Server Sync**: Automatically pushes score updates to the connected stream server via HTTP POST
- **Landscape Mode**: Forced landscape orientation for optimal display

## Technical Stack

- **Language**: Kotlin 2.3.10
- **Minimum SDK**: 34 (Android 14)
- **Target SDK**: 35 (Android 15)
- **Compile SDK**: 36
- **Gradle**: 9.4.0
- **Java Compatibility**: 11
- **Build Features**: ViewBinding
- **Networking**: OkHttp 5.3.2, NSD for local server discovery
- **Serialization**: Kotlinx Serialization JSON 1.10.0
- **UI**: Material Components 1.13.0, ConstraintLayout 2.2.1

## Key Components

### MainActivity
- Main score display with two large tap-sensitive score views
- Single tap increments score, double-tap decrements (minimum 0)
- Status dot indicator: green (connected) / red (disconnected)
- Four player dropdowns (two per team) using AutoCompleteTextView
- Settings FAB launching SettingsActivity

### SettingsActivity
- Load Teams button to fetch teams from the MTFV API
- Team A / Team B selection dropdowns (prevents selecting the same team twice)
- Event name configuration (default: "MTFV Landesliga 2025")
- League URL field (hidden, defaults to MTFV Landesliga)
- Reset Scores and Clear Settings buttons

### GameState
- Serializable data class holding team names, player names, and event name
- Player names stored as "Player1 / Player2" format for doubles

### ScoreManager
- SharedPreferences-based score persistence (left_score, right_score)

### SettingsManager
- Singleton managing SharedPreferences for league URL, event name, and serialized game state

### TeamDataStore
- Singleton with volatile in-memory cache for teams and player lists
- Thread-safe via ConcurrentHashMap

### NsdHelper
- Discovers `_http._tcp.` services named "TFCStreamServer" (case-insensitive)
- Auto-retries discovery every 2 seconds
- Monitors service health every 2 seconds with a 4-second timeout
- Reports connection status via callback

### ScoreUpdater
- Sends POST requests to `http://{host}:{port}/scores` with JSON payload
- Payload: `teamAScore`, `teamBScore`, `teamAName`, `teamBName`, `teamAPlayer`, `teamBPlayer`, `eventName`
- 5-second connection/read/write timeouts
- Runs on Kotlin Coroutines IO dispatcher

### PlayerDropdownHelper
- Manages two-player selection per team
- "No player" option for clearing selection
- Player 2 field disabled until Player 1 is selected

### DtfbClient
- Fetches league teams from configurable URL
- Fetches player rosters per team ID
- Returns `Result<>` for error handling
- 10-second timeouts, lenient JSON parsing

## Configuration

### API URLs
- League API (configurable, default): `https://mtfv.de/ligabetrieb/aktuelle-saison?format=json`
- Team Details API (hardcoded): `https://mtfv.de/ligabetrieb/aktuelle-saison?task=team_details&id={teamId}&format=json`

### Network Permissions Required
- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `ACCESS_WIFI_STATE`
- `CHANGE_WIFI_MULTICAST_STATE`

## Setup

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle dependencies
4. Build and run on a device/emulator (minimum API 34)

## Usage

1. Launch the app — it automatically searches for a TFCStreamServer on the network
2. Go to Settings (FAB button) and tap "Load Teams" to fetch league data
3. Select teams for each side
4. Select players for each team using the dropdown menus
5. Tap on a score to increment, double-tap to decrement
6. Scores are automatically sent to the connected server and saved locally

## Dependencies

| Dependency | Version |
|---|---|
| AndroidX Core KTX | 1.18.0 |
| AndroidX AppCompat | 1.7.1 |
| AndroidX Activity | 1.13.0 |
| AndroidX ConstraintLayout | 2.2.1 |
| Material Components | 1.13.0 |
| Kotlinx Serialization JSON | 1.10.0 |
| OkHttp | 5.3.2 |

## Known Issues & TODOs

- Team details API URL is hardcoded
- No error handling UI for network failures
- No manual server connection option
- Limited test coverage
