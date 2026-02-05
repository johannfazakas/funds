# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a personal financial management application built with Kotlin and microservices architecture. It handles money and investment management across multiple domains.

## Build System

The project uses Gradle with Kotlin DSL and follows a multi-module structure.

### Common Build Commands

```bash
# Build all services
./gradlew build

# Build a specific service
./gradlew :service:fund:fund-service:build

# Install service locally (build + publish to local maven + docker image)
./gradlew :service:fund:fund-service:installLocal

# Run tests
./gradlew test

# Run tests for a specific module
./gradlew :service:fund:fund-service:test
```

### Notebooks

The project includes Jupyter notebooks for data analysis:

```bash
# Build notebook client
cd client/notebook/funds-notebook-client && ./gradlew build
```

## Architecture

### Service Architecture

The system follows a microservices architecture with these core services:

- **user-service** (port 5247): User management
- **conversion-service** (port 5231): Currency and instrument conversion data
- **fund-service** (port 5253): Fund management. Handling Accounts, Funds, and Transactions. 
- **import-service** (port 5207): Data import functionality, orchestrates other services
- **reporting-service** (port 5212): Reporting and analytics

### Module Structure

#### Backend Services
Each service follows the same pattern:
- `*-api`: API models and interfaces
- `*-sdk`: Client SDK for service interaction
- `*-service`: Service implementation with Ktor server

#### Client Applications (KMP Architecture)
The project includes Kotlin Multiplatform mobile and web clients:

**Shared KMP Module** (`client/client-sdk/`):
- Kotlin Multiplatform SDK with JVM, Android, and JS targets
- `AuthenticationClient`: User authentication via user-service
- `FundClient`: Fund operations via fund-service
- Ktor HTTP clients with platform-specific engines (OkHttp for Android, JS for web, CIO for JVM)
- Uses KMP-compatible types (`com.benasher44:uuid`, not `java.util.UUID`)

**Platform-Specific Clients**:
- `client:android-client`: Native Android app with Jetpack Compose, Material 3, MVVM architecture (minSdk 26, targetSdk 35)
- `client:web-client-react`: Kotlin/JS module with integrated React TypeScript frontend

**Web Frontend** (`client/web-client-react/`):
- React 18 + TypeScript app bundled via Kotlin/JS webpack
- Consumes Kotlin/JS `FundsApi` exports (wraps AuthenticationClient/FundClient)
- Uses React Router for navigation, localStorage for session persistence
- npm dependencies managed via Gradle `npm()` and `devNpm()` functions

### Infrastructure

- **Database**: PostgreSQL (port 5438) with separate databases per service
- **Message Broker**: Kafka (ports 9092/29092) for async communication
- **Observability**: OpenTelemetry collector + Tempo + Grafana stack
- **Local Development**: Docker Compose setup in `infra/local/`

### Technology Stack

#### Backend
- **Framework**: Ktor 3.1.3 with Netty
- **Language**: Kotlin 2.1.20
- **Database**: PostgreSQL with Exposed ORM
- **Serialization**: kotlinx.serialization
- **DI**: Koin
- **Testing**: JUnit 5 + TestContainers + AssertJ + Mockito
- **Observability**: OpenTelemetry + Tempo + Grafana

#### Client (KMP)
- **Shared**: Kotlin Multiplatform (JVM, Android, JS targets)
- **HTTP Client**: Ktor 3.0.3 (OkHttp/CIO/JS engines)
- **Serialization**: kotlinx.serialization
- **Logging**: Kermit (KMP-compatible)
- **UUID**: com.benasher44:uuid (KMP-compatible)
- **Android UI**: Jetpack Compose + Material 3 + Navigation Compose
- **Web**: React 18 + TypeScript, bundled via Kotlin/JS webpack
- **State**: Kotlin Flow (KMP), ViewModels (Android), React hooks (Web)

## Development Workflow

### Local Development Setup

#### Backend Services

1. Start infrastructure:
```bash
cd infra/local && docker-compose up -d postgres kafka otel-collector tempo grafana
```

2. Build and run services:
```bash
./gradlew :service:user:user-service:installLocal
./gradlew :service:fund:fund-service:installLocal
# etc.
```

Or run services directly with Docker Compose:
```bash
cd infra/local && docker-compose up
```

#### Client Applications

**Build Client SDK:**
```bash
./gradlew :client:client-sdk:build
```

**Android App:**
```bash
# Build the SDK and APK
./gradlew :client:client-sdk:build :client:android-client:assembleDebug

# Start the Android emulator (uses first available AVD)
emulator -avd $(emulator -list-avds | head -1) &

# Install on running emulator (wait for emulator to boot first)
./gradlew :client:android-client:installDebug
```
- Or open `client/android-client` in Android Studio and run
- Note: On emulator, use `http://10.0.2.2:<port>` instead of `localhost`

**Web App:**
```bash
./gradlew :client:client-sdk:build :client:web-client-react:jsBrowserDevelopmentRun
```

### Service Communication

Services communicate via:
- HTTP REST APIs (synchronous)
- Kafka events (asynchronous)
- Each service has its own database

Service dependencies:
- import-service → fund-service, conversion-service
- reporting-service → fund-service, conversion-service

### Configuration

Services use YAML configuration with environment variable overrides:
- Database URLs, connection pools
- Kafka bootstrap servers
- OpenTelemetry endpoints
- Service-to-service URLs

## Data Management

### Data Sources

- `data/investment/`: Investment data CSV files by year/quarter
- `data/wallet/`: Wallet transaction CSV files by year/month
- `data/provision/`: YAML configuration files for funds, import settings

### Notebooks

Data analysis notebooks in `client/notebook/`:
- `expenses.ipynb`: Expense analysis
- `income.ipynb`: Income analysis  
- `investment.ipynb`: Investment analysis
- `provision.ipynb`: Provision analysis

## Code Conventions

### Backend Services
- Follow existing Kotlin code style and conventions
- Use dependency injection with Koin
- Implement proper error handling and logging
- Write tests using TestContainers for integration tests
- Use kotlinx.serialization for JSON handling
- Follow REST API conventions in service interfaces
- When asked to rebuild a module, trigger gradle clean installLocal tasks on the module

### Client Applications
- **KMP Modules**: Use KMP-compatible libraries only (no JVM-specific types like `java.util.UUID` or `java.math.BigDecimal`)
- **Shared Code**: Place in commonMain, platform-specific in androidMain/jsMain
- **Android**: Follow Material 3 guidelines, use Compose best practices, MVVM pattern
- **Web**: Use TypeScript, functional React components with hooks
- **API Calls**: Use header-based auth with `FUNDS_USER_ID` header
- **Gradle**: Client modules follow pattern `client:client-sdk`, `client:android-client`, or `client:web-client-react`
- **Web npm deps**: Use `npm()` and `devNpm()` in build.gradle.kts, not package.json

### Kobweb Web Client (`client:web-client-kobweb`)
- **Kobweb 0.21.1** with Silk UI components, uses Kotlin/JS target (not wasmJs)
- **JetBrains Compose 1.7.3** — required by Kobweb 0.21.1
- **`@Layout` annotation is NOT available** (requires Kobweb 0.22.0+ / Kotlin 2.1.21) — use manual layout composables instead
- **`@Page` functions must be under `{group}.pages` package** (e.g. `ro.jf.funds.pages`) — Kobweb KSP silently skips pages in wrong packages
- **Webpack output JS name** is derived from the root project group (`ro.jf.funds` → `ro-jf-funds.js`), not the module name — this must match `.kobweb/conf.yaml` dev/prod script paths
- **No changes needed to client-sdk or convention plugins** — Kobweb uses the existing JS target
- **Ktor client dependencies** must be added explicitly (`ktor-client-core` + `ktor-client-js`) since client-sdk depends on them transitively but Kobweb needs them on the compile classpath
- **Dev server**: `./gradlew :client:web-client-kobweb:kobwebStart` / `kobwebStop` (port 8080)
- **Build check**: `./gradlew :client:web-client-kobweb:jsMainClasses` for compilation only
- **`conf.yaml` changes require server restart** — the Kobweb server reads config at startup and caches it
- **Silk theming API**:
  - Palette customization: `ctx.theme.palettes.light.background`, `.color`, `.border`, `.focusOutline`, `.overlay`, `.placeholder`
  - Widget palettes: `.button.set(default, hover, focus, pressed)`, `.input.set(hoveredBorder, invalidBorder, filled, filledHover, filledFocus)`
  - Component style overrides: `ctx.theme.replaceStyle(ButtonStyle) { ... }` or `ctx.theme.modifyComponentStyle(ButtonStyle) { ... }` — but `modifyComponentStyle` may not exist in 0.21.1, use `replaceStyle` instead
  - CssStyle: `val MyStyle = CssStyle { base { Modifier... }; hover { Modifier... } }` — must be public top-level vals for auto-registration
  - CssStyle.base: requires `import com.varabyte.kobweb.silk.style.base` (extension function on companion)
  - Hover/focus/active selectors: `import com.varabyte.kobweb.silk.style.selectors.hover` etc.
  - `toModifier()` requires `import com.varabyte.kobweb.silk.style.toModifier`
- **FontAwesome icons**: package is `com.varabyte.kobweb.silk.components.icons.fa` (NOT `com.varabyte.kobwebx.silk.icons.fa`)
- **`kobweb { app { index { head.add { ... } } } }` in build.gradle.kts** uses kotlinx.html DSL, but `kotlinx.html` is NOT on the build script classpath — the `link {}` function is unavailable. Load fonts dynamically via `document.createElement("link")` in `@InitSilk` instead
- **Modifier APIs**: `letterSpacing(CSSLengthNumericValue)` takes a raw value (e.g. `0.5.px`), no `LetterSpacing.of()` wrapper. `thenIf` is in `com.varabyte.kobweb.compose.ui`
- **TextInput**: parameter is `onTextChange` (not `onTextChanged`)

## Git Workflow

- **ALWAYS** run `git add` when creating, moving, or removing files to ensure they are tracked properly in version control
- Stage new files immediately after creation to avoid losing work