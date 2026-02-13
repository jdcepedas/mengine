# Matching Engine (ME)

High-performance matching engine for trading assets (event-driven, containerized).

## Modules

- **mengine-shared** – Order, Trade, DTOs, JSON codecs for Aeron
- **mengine-core** – Matching Engine Core: Aeron subscriber → Input Disruptor (Matcher + Journaler) → Output Disruptor (DB writer + Notification), Query API
- **gateway** – HTTP API: POST /orders → Aeron; GET /orderbook, /orders, /trades → ME Core + DB

## Requirements

- Java 21
- Gradle 8.5+ (wrapper included)
- Optional: Docker, PostgreSQL (for trade persistence)

## Build

```bash
./gradlew build
./gradlew :mengine-core:installDist
./gradlew :gateway:installDist
```

## Run locally (two processes)

**1. Start Gateway** (runs Media Driver, HTTP on 8080). Note the printed `Aeron Media Driver directory: ...`:

```bash
./gradlew :gateway:run
```

**2. Start ME Core** (connects to Gateway’s driver, Query API on 8081, subscribes for orders). Use the exact path from step 1:

```bash
export ME_AERON_DIR=/path/Gateway/printed   # use the "Aeron Media Driver directory" from step 1
./gradlew :mengine-core:run
```

Gateway owns the Media Driver; ME Core connects to it and subscribes on the same channel/stream so POST /orders is delivered to the matcher.

## Run with Docker

```bash
./gradlew :mengine-core:installDist :gateway:installDist
docker-compose build
docker-compose up
```

- Gateway: http://localhost:8080  
- ME Core Query API: http://localhost:8081  
- PostgreSQL: localhost:5432 (user/pass: mengine/mengine)

## REST API (Gateway)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /orders | Submit order (body: symbol, type, price, quantity) |
| GET | /orders/{id} | Order status (from ME Core) |
| GET | /orderbook/{symbol} | Order book snapshot (from ME Core) |
| GET | /trades/{symbol} | Recent trades (from DB) |

### Order request (POST /orders)

```json
{
  "symbol": "AAPL",
  "type": "BUY",
  "price": 100.50,
  "quantity": 10
}
```

## Configuration

**ME Core** – env / `mengine.properties`:

- `ME_QUERY_PORT` (default 8081)
- `ME_AERON_CHANNEL` (default `aeron:ipc`)
- `ME_AERON_STREAM_ID` (default 10)
- `ME_AERON_DIR` – media driver directory (for shared driver with Gateway)
- `ME_JOURNAL_DIR` – order journal directory (default `journal`)
- `ME_DB_URL`, `ME_DB_USER`, `ME_DB_PASSWORD` – trades DB (empty = in-memory)
- `ME_BUFFER_SIZE`, `ME_MATCHING_TIMEOUT_MS`, `ME_STANDARD_DELAY_MS`

**Gateway** – env / `gateway.properties`:

- `GW_HTTP_PORT` (default 8080)
- `GW_AERON_CHANNEL`, `GW_AERON_STREAM_ID`
- `GW_AERON_DIR` – connect to existing driver (no launch)
- `GW_ME_CORE_URL` (default http://localhost:8081)
- `GW_DB_URL`, `GW_DB_USER`, `GW_DB_PASSWORD` – for GET /trades

## Architecture

```
Gateway (HTTP) → Aeron (orders) → ME Core
  ↓                    ↓
  GET /orderbook,      Aeron poller → Input Disruptor
  GET /orders    ←         ↓
  (ME Core)           Matcher + Journaler (orders to disk)
  GET /trades              ↓
  (DB)               Output Disruptor → DB writer + Notification
```
