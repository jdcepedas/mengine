# Deployment View (UML-style)

Reference for drawing a **UML Deployment Diagram** with stereotypes `<<processingNode>>`, `<<executionEnvironment>>`, `<<artifact>>`, and `<<disk>>`.  
The project is containerized (Docker); CPU/memory are not set in the repo, so use your target host or add `deploy.resources` in `docker-compose.yml` and document those values here.

---

## 1. Processing nodes (`<<processingNode>>`)

Each node is a **deployable unit**. In the current setup these map to Docker services (containers). Optionally you can model a single "Docker host" and nest containers inside it.

| Node name       | Stereotype          | Tagged values (suggested / from project) |
|-----------------|---------------------|-----------------------------------------|
| **Gateway**     | `<<processingNode>>` | `model`: 'eclipse-temurin:21-jre-alpine' (image) <br> `os`: 'Alpine Linux' <br> `cpu`: '(not set – use host or e.g. 2 cores)' <br> `memory`: '(not set – use host or e.g. 512MB–1GB)' |
| **ME Core**     | `<<processingNode>>` | Same image as Gateway. Two instances: **me-core** (port 8081), **me-core-1** (port 8082). <br> `model`: 'eclipse-temurin:21-jre-alpine' <br> `os`: 'Alpine Linux' <br> `cpu`: '(not set)' <br> `memory`: '(not set – e.g. 512MB–1GB per instance)' |
| **ME Core-1**   | `<<processingNode>>` | Same as ME Core; second partition (stream 11, port 8082). |
| **Database**     | `<<processingNode>>` | `model`: 'postgres:15-alpine' <br> `os`: 'Alpine Linux' <br> `cpu`: '(not set)' <br> `memory`: '(not set – e.g. 512MB–2GB)' |

---

## 2. Execution environments (`<<executionEnvironment>>`)

Runtime containers that host artifacts.

| Environment              | Stereotype                 | Host node   | Notes |
|--------------------------|----------------------------|------------|-------|
| **JRE 21 (Eclipse Temurin)** | `<<executionEnvironment>>` | Gateway, ME Core, ME Core-1 | Java 21; from base image `eclipse-temurin:21-jre-alpine`. |
| **PostgreSQL 15**       | `<<executionEnvironment>>` | Database   | From image `postgres:15-alpine`. |

---

## 3. Artifacts (`<<artifact>>`)

Deployable software components.

| Artifact            | Stereotype   | Contained in (node or execution env) |
|---------------------|-------------|---------------------------------------|
| **gateway.jar**     | `<<artifact>>` | Gateway node, inside JRE 21. Built by `./gradlew :gateway:bootJar`. Single executable JAR (Spring Boot). |
| **mengine-core**    | `<<artifact>>` | ME Core / ME Core-1, inside JRE 21. Built by `./gradlew :mengine-core:installDist`; deployed as `mengine-core/build/install/mengine-core/` (bin + lib). Entrypoint: `bin/mengine-core`. |
| **PostgreSQL schema / data** | `<<artifact>>` | Database node, inside PostgreSQL 15. DB name: `mengine`; schema created by application (trades, etc.). |

---

## 4. Disk / storage (`<<disk>>`)

Volumes used for persistence. In Docker these are named volumes; on bare metal they would be directories or LUNs.

| Volume / path              | Stereotype | Tagged values / notes |
|----------------------------|------------|------------------------|
| **aeron-shared**           | `<<disk>>` | Mount path: `/dev/shm/aeron` (shared across Gateway and ME Cores). Used for Aeron IPC (media driver directory). <br> `type`: 'shared memory (IPC)' |
| **journal-data**           | `<<disk>>` | Mount: `/data/journal` on **me-core**. Order journal + optional latency log. <br> `type`: 'persistent volume' |
| **journal-data-1**         | `<<disk>>` | Mount: `/data/journal` on **me-core-1**. Same as above for second partition. |
| **pgdata**                 | `<<disk>>` | Mount: `/var/lib/postgresql/data` on **Database**. PostgreSQL data. <br> `type`: 'persistent volume' |

---

## 5. Communication paths

Dashed associations between nodes (who talks to whom).

| From       | To          | Protocol / port | Purpose |
|------------|-------------|-----------------|---------|
| **Client** | **Gateway**  | HTTP TCP 8080  | POST /orders, GET /orderbook, /orders, /trades. |
| **Gateway**| **ME Core** | HTTP TCP 8081  | GET /orderbook/{symbol}, GET /orders/{id}, GET /metrics, GET /ready. |
| **Gateway**| **ME Core-1** | HTTP TCP 8082 | Same as above for second partition. |
| **Gateway**| **Database** | TCP 5432 (PostgreSQL) | GET /trades → JDBC. |
| **Gateway**| **ME Core(s)** | Aeron IPC (shared dir) | Orders published to streams 10, 11. Gateway owns media driver; ME Cores connect to same dir. |
| **ME Core** | **Database** | TCP 5432 (PostgreSQL) | Trade persistence (HikariCP). |
| **ME Core-1** | **Database** | TCP 5432 (PostgreSQL) | Same. |
| **Gateway**  | **aeron-shared** | Volume mount | Read/write Aeron driver files. |
| **ME Core / ME Core-1** | **aeron-shared** | Volume mount | Connect to same driver. |
| **ME Core**  | **journal-data**  | Volume mount | Journal + latency log. |
| **ME Core-1**| **journal-data-1** | Volume mount | Journal + latency log. |
| **Database** | **pgdata**   | Volume mount | Data files. |

---

## 6. Summary for diagram boxes

**Processing nodes (suggested tagged values if you standardize):**

- **Gateway**: `model` = eclipse-temurin:21-jre-alpine, `os` = Alpine Linux, `cpu` = 2+, `memory` = 512MB–1GB  
- **ME Core** (and **ME Core-1**): same as Gateway; `memory` = 512MB–1GB per instance  
- **Database**: `model` = postgres:15-alpine, `os` = Alpine Linux, `cpu` = 1–2, `memory` = 512MB–2GB  

**Execution environments:**  
JRE 21 (Eclipse Temurin) on Gateway and both ME Core nodes; PostgreSQL 15 on Database node.

**Artifacts:**  
`gateway.jar` (Gateway), `mengine-core` install dist (ME Core nodes), DB schema/data (Database).

**Disks:**  
`aeron-shared`, `journal-data`, `journal-data-1`, `pgdata` with types as above.

Use this to label your deployment view diagram consistently with the example (e.g. `<<processingNode>>` with model, os, cpu, memory and `<<executionEnvironment>>` / `<<artifact>>` / `<<disk>>` as in the reference).
