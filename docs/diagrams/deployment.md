---
diagram: deployment
uml-type: Deployment Diagram（部署圖）
source: docs/EDD.md §4.5.9 + docs/ARCH.md
generated: 2026-10-30T00:00:00Z
---

# Deployment Diagram — yoCat Office 後端部署架構

> 來源：EDD.md §4.5.9

```mermaid
flowchart TD
    subgraph Internet["Internet"]
        User["遊戲客戶端 / 營運"]
    end
    subgraph DMZ["DMZ / Ingress Zone"]
        Ingress["Ingress Controller<br/>Image: ingress-nginx:1.11<br/>CPU req/limit: 100m/500m<br/>Mem req/limit: 128Mi/256Mi<br/>Replicas: 2<br/>PullPolicy: Always"]
    end
    subgraph AppZone["Internal / App Zone"]
        API["yocat-office-backend-api<br/>Image: registry.sayyogames.internal/yocat-office-backend:git-sha8<br/>CPU req/limit: 500m/2000m<br/>Mem req/limit: 1Gi/2Gi<br/>Replicas: 2-8 (HPA: CPU>70%)<br/>PullPolicy: Always"]
    end
    subgraph DataZone["Data Zone"]
        PG["PostgreSQL 16<br/>Image: postgres:16-alpine<br/>CPU req/limit: 500m/2000m<br/>Mem req/limit: 1Gi/4Gi<br/>Primary + 1 Standby (Patroni)"]
        Redis["Redis 7.2 Sentinel<br/>Image: redis:7.2-alpine<br/>CPU req/limit: 100m/500m<br/>Mem req/limit: 256Mi/512Mi<br/>Replicas: 3"]
        Vol[("PersistentVolume<br/>StorageClass: local-path/gp3")]
    end

    User -->|"HTTPS:443 TLS 1.3"| Ingress
    Ingress -->|"HTTP:8080"| API
    API -->|"TCP:5432 PostgreSQL Wire"| PG
    API -->|"TCP:6379 Redis Protocol"| Redis
    PG -->|"PVC: pg-data 50Gi SSD"| Vol
```

**HPA Configuration Table：**

| Service | minReplicas | maxReplicas | Scale-Up Metric | Target | Scale-Down Cooldown |
|---------|------------|------------|----------------|--------|-------------------|
| yocat-office-backend-api | 2 | 8 | CPU Utilization | 70% | 300s |

**Storage Specification Table：**

| Service | PVC Name | Size | StorageClass | IOPS | Backup Frequency | Retention |
|---------|---------|------|------------|------|-----------------|----------|
| PostgreSQL | pg-data | 50Gi | gp3（prod）/ local-path（local，2Gi） | 3000 | hourly 增量 + daily 全量 | 30 days |

**Network Policy Table：**

| Source Zone | Dest Zone | Allowed Ports | Protocol | Notes |
|------------|---------|--------------|---------|-------|
| Internet | DMZ | 443 | HTTPS | 僅 Ingress |
| DMZ | App | 8080 | TCP | Ingress → API |
| App | Data | 5432, 6379 | TCP | API → PG/Redis |
| App | Internet | 443（Phase B 金流）| HTTPS | 預設 DENY，金流白名單例外 |
