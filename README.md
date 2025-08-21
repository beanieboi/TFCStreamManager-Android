# TFC Stream Manager

An Android application for managing and displaying live scores for table football (foosball) matches, specifically designed for TFC Leipzig and MTFV (Mitteldeutscher Tischfußballverband) league matches.

## Features

- **Live Score Management**: Track and update scores for two teams in real-time
- **Network Service Discovery (NSD)**: Automatically discovers and connects to TFC Stream Server on the local network
- **Player Selection**: Select players for each team from the league database
- **MTFV League Integration**: Fetches team and player data from MTFV league API
- **Persistent Score Storage**: Saves scores locally to prevent data loss
- **Landscape Mode**: Optimized for landscape orientation display

## Technical Stack

- **Language**: Kotlin
- **Minimum SDK**: 22 (Android 5.1)
- **Target SDK**: 35 (Android 15)
- **Architecture**: Single Activity with Settings
- **Networking**: OkHttp for API calls, NSD for local server discovery
- **Serialization**: Kotlinx Serialization

## Key Components

### MainActivity
- Main score display and control interface
- Handles score increment/decrement via tap/double-tap gestures
- Manages player selection dropdowns
- Displays connection status indicator

### NsdHelper
- Discovers "TFCStream" service on local network using mDNS
- Monitors connection status and handles reconnection
- Provides server host and port for score updates

### ScoreUpdater
- Sends score updates to the discovered TFC Stream Server
- Posts JSON payload with scores, team names, player names, and event info

### DtfbClient
- Fetches league data from MTFV API
- Retrieves team listings and player rosters
- Handles JSON parsing of league responses

### SettingsActivity
- Allows selection of teams from the league
- Configures event name
- Option to change league URL (defaults to MTFV Landesliga)

## Configuration

### Hardcoded URLs (to be refactored)
- MTFV League API: `https://mtfv.de/ligabetrieb/aktuelle-saison?format=json`
- Team Details API: `https://mtfv.de/ligabetrieb/aktuelle-saison?task=team_details&id={teamId}&format=json`

### Network Permissions Required
- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `ACCESS_WIFI_STATE`
- `CHANGE_WIFI_MULTICAST_STATE`

## Setup Instructions

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle dependencies
4. Build and run on a device/emulator (minimum API 22)

## Usage

1. Launch the app - it will automatically search for a TFC Stream Server on the network
2. Go to Settings (floating action button) to select teams
3. Select players for each team using the dropdown menus
4. Tap on a score to increment, double-tap to decrement
5. Scores are automatically sent to the connected server and saved locally

## Known Issues & TODOs

- Hardcoded URLs should be moved to configuration
- No error handling UI for network failures
- Missing unit and integration tests
- Player selection UI could be improved
- No manual server connection option
- Settings are partially persisted (team names only, not players)

## Dependencies

- AndroidX Core KTX
- Material Components
- Kotlinx Serialization JSON
- OkHttp 4.12.0

## Contributing

This project was initially created using Cursor and needs refinement. Contributions are welcome to:
- Add proper configuration management
- Improve error handling
- Add comprehensive testing
- Enhance UI/UX
- Document the server protocol
