# TFC Stream Manager

An Android application for managing and displaying live scores for table football (foosball) matches, specifically designed for TFC Leipzig and MTFV (Mitteldeutscher Tischfußballverband) league matches.

## Features

- **Live Score Management**: Track and update scores for two teams in real-time
- **Network Service Discovery (NSD)**: Automatically discovers TFCStreamServer on the local network via mDNS and follows its `state` TXT record (started / running)
- **Player Selection**: Select up to two players per team from the league database
- **MTFV League Integration**: Fetches team and player data from the MTFV league API
- **Persistent Storage**: Saves scores and game state locally via SharedPreferences
- **Server Sync**: Pushes score updates to the stream server via HTTP POST while the server is running
- **Landscape Mode**: Forced landscape orientation for optimal display

## Technical Stack

- **Language**: Kotlin
- **Minimum SDK**: 34 (Android 14)
- **Target SDK**: 35 (Android 15)
- **Java Compatibility**: 11
- **Build Features**: ViewBinding
- **Networking**: OkHttp, NSD for local server discovery
- **Serialization**: Kotlinx Serialization JSON
- **UI**: Material Components, ConstraintLayout

Versions live in `gradle/libs.versions.toml` and `app/build.gradle.kts`.

## Key Components

### MainActivity
- Two score halves that fill the screen below a top strip, with no gap and nothing on top of them
- A goal counts the moment a finger lands (touch-down), with no tap or double-tap detection to wait for. A second touch on the same side within 150 ms is ignored (finger bounce, palm)
- Every goal vibrates and flashes the scored half green
- **Undo** in the top strip takes back the most recent goal, whichever side it was for (flashes red)
- Full-screen immersive mode and keep-screen-on while the score screen is open
- Status dot indicator:
  - red: no server found
  - yellow: server app open, web server not started (`state=started`)
  - green: server running (`state=running`)
- "not sent" under the status dot while the latest score has not reached the server
- Top strip: four player dropdowns (two per team), Undo, status dot, settings button

### SettingsActivity
- Load Teams button to fetch teams from the MTFV API
- Team A / Team B selection dropdowns (prevents selecting the same team twice)
- Event name configuration (default: "MTFV Landesliga 2025")
- League URL field (hidden, defaults to MTFV Landesliga)
- Reset Scores and Clear Settings buttons

### GameState
- Serializable data class holding team names, player names, and event name
- Player names stored as "Player1 / Player2" format for doubles

### ScoreBoard
- Both scores plus the order of goals, for undo. Pure Kotlin, unit tested together with `GoalTouchFilter`

### ScoreManager
- SharedPreferences-based score persistence (left_score, right_score)

### SettingsManager
- Singleton managing SharedPreferences for league URL, event name, and serialized game state

### TeamDataStore
- Singleton with volatile in-memory cache for teams and player lists
- Thread-safe via ConcurrentHashMap

### NsdHelper
- Discovers `_http._tcp.` services named "TFCStreamServer" (case-insensitive)
- Keeps a `ServiceInfoCallback` registered on the resolved service, so changes of the `state` TXT record arrive without rediscovery
- Maps the record to `ServerStatus`: `started` → `STARTED`, `running` → `RUNNING`, unknown values → `STARTED`. A missing record counts as `RUNNING` (older servers only advertised while running)
- Exposes host and port only while `RUNNING`, so scores are not sent to a server that is not accepting requests
- Auto-retries discovery every 2 seconds and monitors service health
- Reports `ServerStatus` changes via callback

### ScoreUpdater
- Sends POST requests to `http://{host}:{port}/scores` with JSON payload
- Payload: `teamAScore`, `teamBScore`, `teamAName`, `teamBName`, `teamAPlayer`, `teamBPlayer`, `eventName`
- One request at a time, newest wins: scores submitted while a request is in flight collapse into the latest, so the server never gets an older score after a newer one
- Failed sends retry with backoff (0.5 s up to 5 s) until they succeed or a newer score replaces them
- Holds the latest score while the server is not running and sends it once it is; a server that switches to running also gets the current score
- 2-second connect timeout, 4-second call timeout

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

1. Launch the app — it automatically searches for a TFCStreamServer on the network. Wait for a green dot; yellow means the server app is open but Start Server has not been pressed
2. Go to Settings (FAB button) and tap "Load Teams" to fetch league data
3. Select teams for each side
4. Select players for each team using the dropdown menus
5. Touch a score to add a goal. Use Undo to take back the last goal
6. Scores are saved locally and sent to the server while it is running. "not sent" under the dot means the server has not got the latest score yet

## Where this is heading

The app is being reshaped from "the tablet knows everything, the server displays it" into a thin input device for a server that owns the competition. The decisions are ADRs 0002–0009 in `architecture-decisions/tfc-stream-manager`. Only ADR 0009 (started/running over mDNS) is implemented so far.

What changes for this app:

- **The server is authoritative (0002).** Competition source, match day, lineups and score live on the server. `DtfbClient`, `TeamDataStore`, `SettingsManager`, `GameState` and the team selection in `SettingsActivity` leave the app. The app no longer talks to mtfv.de and persists no domain state.
- **Lineups are entered on the server (0004).** The tablet shows the current unit's lineup and names as the server provides them; player dropdowns go away.
- **Scoring rules run on the server (0005).** Sets (Verbandsliga, Bezirksliga, Kickertool) and Race to 42 (Landesliga) are handled there. The tablet renders the derived values it receives (shown score, sets won, unit label such as "Satz 2" or "Segment 3", target) and is the same for every format.
- **Intents instead of scores (0006).** The tablet sends goal home/away, undo and next game, each with the state version it last saw. A stale version is rejected with a conflict and the app refetches. State comes in over Server-Sent Events. `POST /scores` and `ScoreUpdater`'s snapshot payload are removed.
- **One table (0007).** One tablet, one match, no table parameter.

What stays: mDNS discovery and the red/yellow/green status dot (0009). The `state` record may grow later, for example to say whether a match day is loaded.

## Known Issues & TODOs

- Team details API URL is hardcoded
- No error handling UI for network failures
- No manual server connection option
- Score is still tablet-authoritative until ADR 0002/0006 land on the server and here
- Limited test coverage
