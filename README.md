# 🚌 Bus Tracker Rio — Android App

Aplicativo Android nativo para rastreamento de ônibus em tempo real na cidade do Rio de Janeiro, com mapa interativo e busca por linhas.

## ✨ Funcionalidades

- 🗺️ **Mapa em tempo real** — Posições de ônibus atualizadas a cada 30 segundos
- 🔍 **Busca por linha** — Pesquise e filtre por número da linha
- 🏷️ **Chips de linhas ativas** — Visualização rápida das linhas selecionadas
- 📍 **Localização do usuário** — Veja sua posição no mapa
- 🌙 **Modo escuro** — Tema dark automático com estilização dourada
- 📊 **Detalhes do ônibus** — Card com informações de velocidade e direção
- 🎨 **Ícones personalizados** — Marcadores em formato de gota com rotação direcional

## 🛠️ Stack Técnica

| Tecnologia | Uso |
|---|---|
| **Kotlin** | Linguagem principal |
| **Jetpack Compose** | UI declarativa moderna |
| **Google Maps SDK** | Renderização do mapa |
| **Coroutines + Flow** | Programação assíncrona |
| **ViewModel + StateFlow** | Arquitetura MVVM |
| **Retrofit/OkHttp** | Comunicação com a API |
| **DataStore** | Persistência de preferências |

## 📁 Estrutura

```
app/src/main/java/com/example/bustrackernativo/
├── MainActivity.kt              # Activity principal (Google Maps)
├── data/
│   ├── BusModels.kt             # Data classes
│   ├── BusRepository.kt        # Repositório de dados
│   ├── BusService.kt           # Cliente HTTP
│   ├── NetworkHelper.kt        # Utilitários de rede
│   └── SettingsDataStore.kt    # Preferências do usuário
└── ui/
    ├── map/
    │   ├── BusViewModel.kt      # ViewModel principal
    │   ├── MapViewModel.kt      # Estado do mapa
    │   ├── SearchBar.kt         # Barra de pesquisa
    │   ├── ActiveLineChips.kt   # Chips de linhas ativas
    │   └── BusDetailCard.kt     # Card de detalhes
    └── theme/                   # Material Design 3
```

## 🚀 Build

### Pré-requisitos

- Android Studio Hedgehog (2023.1.1)+
- JDK 17
- Google Maps API Key

### Configuração

1. Crie o arquivo `local.properties` na raiz do projeto:

```properties
sdk.dir=C:\\Users\\SEU_USUARIO\\AppData\\Local\\Android\\Sdk
MAPS_API_KEY=SUA_CHAVE_GOOGLE_MAPS
```

1. Obtenha uma API Key em [Google Cloud Console](https://console.cloud.google.com/) → APIs → Maps SDK for Android

2. Build:

```bash
./gradlew assembleDebug
```

## 🏗️ Arquitetura

```
┌─────────────┐     ┌──────────────────┐     ┌──────────────┐
│  Android App │ ──► │ Cloudflare Worker │ ──► │ API Rio (GPS)│
│  (Kotlin)    │ ◄── │ (Cache + Proxy)   │ ◄── │  31MB → 5MB  │
└─────────────┘     └──────────────────┘     └──────────────┘
```

O app **não** se conecta diretamente à API pública. Ele usa um [Cloudflare Worker](https://github.com/SEU_USUARIO/bustracker-api-worker) como proxy para reduzir latência e consumo de dados.
