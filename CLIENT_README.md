# Funds Mobile & Web Clients

This document describes the Kotlin Multiplatform (KMP) architecture for Funds mobile and web clients.

## Architecture Overview

The client architecture follows a three-tier KMP design:

```
service/
├── client-shared/              # KMP shared code
│   ├── client-shared-api/      # Data models (FundTO, UserTO, etc.)
│   ├── client-shared-sdk/      # HTTP clients (FundSdk, UserSdk)
│   └── client-shared-core/     # Business logic, repositories
├── client-android/             # Android app (Jetpack Compose)
└── client-web/                 # Kotlin/JS exports for web

client/
└── web-react/                  # React web app
```

### Module Breakdown

#### 1. client-shared-api (KMP: JVM, Android, JS)
- **Purpose**: Platform-independent data models
- **Key Types**:
  - `FundTO`, `UserTO`, `ListTO`
  - `FundName` (value class)
  - KMP-compatible serializers
- **Dependencies**:
  - `kotlinx.serialization`
  - `com.benasher44:uuid` (KMP UUID library)

#### 2. client-shared-sdk (KMP: JVM, Android, JS)
- **Purpose**: Ktor HTTP clients for backend services
- **Key Classes**:
  - `FundSdk` - Fund service client
  - `UserSdk` - User service client
- **Platform Engines**:
  - JVM: CIO
  - Android: OkHttp
  - JS: Browser
- **Features**: Automatic JSON serialization, header-based auth

#### 3. client-shared-core (KMP: JVM, Android, JS)
- **Purpose**: Shared business logic and state management
- **Key Classes**:
  - `FundRepository` - Wraps FundSdk
  - `AuthRepository` - Handles login
  - `AuthState` - Auth state model
- **Dependencies**: Coroutines, Kermit logging

#### 4. client-android (Android App)
- **UI Framework**: Jetpack Compose + Material 3
- **Navigation**: Navigation Compose
- **Architecture**: MVVM with ViewModels
- **Screens**:
  - `LoginScreen` - Username-based login
  - `FundListScreen` - Display user's funds
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 35

#### 5. client-web (Kotlin/JS)
- **Purpose**: JS exports for consumption by web frameworks
- **Exported API**:
  - `FundsApi.loginWithUsername()`
  - `FundsApi.listFunds()`
- **Output**: JS module with Promise-based API

#### 6. web-react (React App)
- **Framework**: React 18 + TypeScript
- **Build Tool**: Vite
- **Routing**: React Router
- **Features**:
  - Login page with username input
  - Fund list with cards
  - Session persistence (localStorage)

## Project Structure Decisions

### Why Separate client-api Modules?
- **Chosen**: Create new KMP client-api modules
- **Rationale**:
  - Keeps server code unchanged (no java.util.UUID → KMP migration)
  - Allows independent evolution of client and server APIs
  - Cleaner separation of concerns

### Why Inside service/ Directory?
- **Chosen**: Place client modules in `service/` alongside backend
- **Rationale**:
  - Shared Gradle setup and conventions
  - Easier to iterate on API changes
  - Single repository for full-stack development

### Key Technical Choices

1. **UUID Handling**: `com.benasher44:uuid` for KMP compatibility
2. **Decimal Handling**: Currently using `String` representation (can migrate to kotlinx-decimal)
3. **Logging**: Kermit (KMP-compatible logger)
4. **Auth**: Simple header-based (FUNDS_USER_ID header)
5. **State Management**: Kotlin Flow + ViewModels (Android), useState (React)

## Building & Running

### Prerequisites

1. **Backend Services**: Ensure fund-service and user-service are running
   ```bash
   cd infra/local
   docker-compose up postgres kafka
   ```

2. **Build Shared Modules**:
   ```bash
   cd service
   ./gradlew :client-shared:client-shared-api:build
   ./gradlew :client-shared:client-shared-sdk:build
   ./gradlew :client-shared:client-shared-core:build
   ```

### Android App

1. **Open in Android Studio**:
   - Open `service/client-android` in Android Studio
   - Or use the root project and navigate to the module

2. **Run**:
   ```bash
   cd service
   ./gradlew :client-android:assembleDebug
   ./gradlew :client-android:installDebug
   ```

3. **Configure Base URL** (if needed):
   - Android emulator: Use `http://10.0.2.2:5253` instead of `localhost`
   - Update `FundSdk.kt` and `UserSdk.kt` default baseUrl

### Web App (React)

1. **Install Dependencies**:
   ```bash
   cd client/web-react
   npm install
   ```

2. **Run Development Server**:
   ```bash
   npm run dev
   ```
   - Access at: http://localhost:3000

3. **Build for Production**:
   ```bash
   npm run build
   npm run preview
   ```

### Kotlin/JS Module (Optional)

If you want to use the KMP JS exports:

```bash
cd service
./gradlew :client-web:jsBrowserDevelopmentWebpack
```

Output: `service/client-web/build/dist/js/developmentExecutable/`

## Initial Feature: Fund List

### User Flow

1. **Login Screen**
   - User enters username
   - App calls `UserSdk.findUserByUsername()`
   - On success, stores userId and navigates to fund list

2. **Fund List Screen**
   - Automatically loads funds for logged-in user
   - Calls `FundSdk.listFunds(userId)`
   - Displays fund name and ID in cards/list items

### API Endpoints Used

- **Login**: `GET http://localhost:5247/funds-api/user/v1/users/username/{username}`
- **List Funds**: `GET http://localhost:5253/funds-api/fund/v1/funds`
  - Header: `FUNDS_USER_ID: <uuid>`

## Testing the Clients

1. **Start Backend Services**:
   ```bash
   cd infra/local
   docker-compose up -d postgres kafka
   cd ../../service
   ./gradlew :fund:fund-service:installLocal
   ./gradlew :user:user-service:installLocal
   cd ../infra/local
   docker-compose up fund-service user-service
   ```

2. **Create Test User** (if needed):
   ```bash
   curl -X POST http://localhost:5247/funds-api/user/v1/users \
     -H "Content-Type: application/json" \
     -d '{"username":"testuser"}'
   ```

3. **Create Test Fund**:
   ```bash
   # Get user ID from previous response
   USER_ID="<uuid-from-create-user>"

   curl -X POST http://localhost:5253/funds-api/fund/v1/funds \
     -H "Content-Type: application/json" \
     -H "FUNDS_USER_ID: $USER_ID" \
     -d '{"name":"My Investment Fund"}'
   ```

4. **Login to Client**:
   - Android/Web: Enter "testuser"
   - Should see "My Investment Fund" in the list

## Next Steps

### Enhancements
- [ ] Add proper authentication (JWT tokens)
- [ ] Implement fund details screen
- [ ] Add transaction management
- [ ] Support account operations
- [ ] Add offline support (local database)
- [ ] Implement pull-to-refresh
- [ ] Add error retry logic with exponential backoff
- [ ] Theme support (dark mode)

### Technical Improvements
- [ ] Migrate BigDecimal handling to kotlinx-decimal
- [ ] Add KMP-compatible OpenTelemetry tracing
- [ ] Implement proper dependency injection (Koin KMP)
- [ ] Add comprehensive unit tests
- [ ] Setup CI/CD for mobile releases
- [ ] Add screenshot testing

### Platform Extensions
- [ ] iOS app (KMP + SwiftUI or Compose Multiplatform)
- [ ] Desktop app (Compose Multiplatform Desktop)
- [ ] Progressive Web App (PWA) features

## Troubleshooting

### Android Emulator Cannot Reach localhost

Use `10.0.2.2` instead of `localhost`:
```kotlin
private const val LOCALHOST_BASE_URL = "http://10.0.2.2:5253"
```

### CORS Issues on Web

Add CORS headers to backend services (already configured in Ktor services).

### Build Errors

```bash
cd service
./gradlew clean
./gradlew buildSrc:clean build
./gradlew build
```

### Android Build Issues

Ensure Android SDK is installed:
- compileSdk: 35
- minSdk: 26
- Android Studio: Hedgehog or later

## Architecture Diagrams

### Data Flow

```
┌─────────────┐
│  UI Layer   │ ← Android (Compose) / Web (React)
└─────────────┘
       ↓
┌─────────────┐
│ Repository  │ ← client-shared-core (KMP)
└─────────────┘
       ↓
┌─────────────┐
│  SDK Layer  │ ← client-shared-sdk (KMP Ktor Clients)
└─────────────┘
       ↓
┌─────────────┐
│   Backend   │ ← fund-service, user-service
│  Services   │
└─────────────┘
```

### Module Dependencies

```
client-android ──┐
                 ├──> client-shared-core
client-web ──────┤         ↓
                 └──> client-shared-sdk
                           ↓
                      client-shared-api
```

## License

Same as parent project.
