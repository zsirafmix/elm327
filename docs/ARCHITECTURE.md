# Architecture — OBD Master Intelligence Tester AI

## Overview

Clean Architecture + MVVM + Repository, Kotlin, Jetpack Compose, Hilt DI, Room persistence, Coroutines/Flow.

```mermaid
flowchart TB
  UI[Compose UI + ViewModels]
  DOM[Domain: models, repository interfaces, use cases]
  DATA[Data: Room, OkHttp, Transport, Repository impl]
  OBD[OBD engine: ELM, Modes, Protocol, ISO-TP, UDS, SafetyGate]
  AI[AI providers + EncryptedSharedPreferences]
  PDF[PdfDocument report generator]

  UI --> DOM
  DOM --> DATA
  DATA --> OBD
  DATA --> AI
  DATA --> PDF
  OBD --> Safety[SafetyGate READ ONLY]
```

## Layers

| Layer | Package | Responsibility |
|-------|---------|----------------|
| Presentation | `ui.*` | Compose screens, `MainViewModel`, navigation drawer |
| Domain | `domain.*` | Models, repository contracts — no Android deps |
| Data | `data.*` | Room, remote stub, transport, repository implementations |
| OBD | `obd.*` | Command builders/parsers, protocol discovery, safety |
| Cross-cutting | `ai`, `pdf`, `scoring`, `vehicle`, `knowledge`, `di` | Features + Hilt module |

## MVVM data flow

```mermaid
sequenceDiagram
  participant Screen
  participant VM as MainViewModel
  participant Repo as DiagnosticRepository
  participant ELM as Elm327CommandLayer
  participant Gate as SafetyGate
  participant T as ObdTransport

  Screen->>VM: runFullDemo()
  VM->>Repo: runFullDemoTest()
  Repo->>T: connect(mock)
  Repo->>ELM: send(AT/PID)
  ELM->>Gate: check(command)
  alt blocked
    Gate-->>ELM: Blocked
    ELM-->>Repo: SafetyBlockedException
  else allowed
    ELM->>T: transact
    T-->>ELM: response
    ELM-->>Repo: parsed / raw
  end
  Repo-->>VM: StateFlow updates
  VM-->>Screen: collectAsState
```

## Package map

```
com.obdmaster.intelligence
├── di/AppModule.kt
├── ui/ (MainActivity, MainViewModel, screens/*, components, theme, navigation)
├── domain/model + repository
├── data/local (Room) + remote + transport + repository
├── obd/modes, protocol, adapter, elm, safety, isotp, uds
├── ai/, pdf/, scoring/, vehicle/, knowledge/
```

## Dependency rule

UI → Domain ← Data. OBD/AI/PDF are injected into Data/Repository via Hilt `@Singleton` providers. Domain never imports Android framework types (except `java.io.File` on report contract).
