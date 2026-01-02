# 🏗️ Architettura del Progetto AEGIS

## 🌳 Albero del Progetto
Di seguito la struttura delle directory principali e dei componenti chiave:

```text
AEGIS/
├── 📂 aegis-frontend/           # [FRONTEND] React Single Page Application (SPA)
│   ├── src/
│   │   ├── App.jsx             # Logica principale UI e Router
│   │   └── keycloak.js         # Configurazione Adapter OIDC
│   ├── public/                 # Asset statici
│   └── package.json            # Dipendenze Node.js (Vite, Axios, Keycloak-js)
│
├── 📂 backend/
│   └── aegis-backend/          # [BACKEND] Spring Boot Application
│       ├── src/main/java/      # Codice sorgente Java
│       │   └── com/aegis/backend/
│       │       ├── controller/ # REST Endpoints Layer
│       │       ├── service/    # Business Logic Layer
│       │       ├── model/      # JPA Entities (DB Mapping)
│       │       ├── dto/        # Data Transfer Objects
│       │       └── security/   # Security Configuration (JWT, Filters)
│       ├── uploads/            # [LOCAL STORAGE] Cartella file cifrati
│       └── pom.xml             # Dipendenze Maven
│
├── 📂 docker-env/              # [INFRASTRUCTURE] Componenti Dockerizzati
│   ├── 📄 docker-compose.yml   # Orchestratore servizi (DB, Vault, Keycloak)
│   ├── 📂 init-db/             # Script SQL inizializzazione DB
│   ├── 📂 themes/              # Temi personalizzati per Keycloak
│   └── 📄 realm-export.json    # Configurazione Realm Keycloak (Utenti/Ruoli)
│
├── 📂 certs/                   # Certificati SSL/TLS condivisi
├── 📂 postgres-ssl/            # Chiavi specifiche per Database
├── 📂 keycloak-ssl/            # Chiavi specifiche per Identity Provider
│
└── 📄 *_SETUP.md               # Guide di installazione per vari OS
```

---

## 🏛️ Giustificazione dell'Architettura

La struttura di **AEGIS** segue un approccio **Microservices-Ready** ma implementato inizialmente come **Monolite Modulare** per ridurre la complessità operativa mantenendo alta la sicurezza.

### 1. Separazione Frontend/Backend (Decoupling)
*   **Perché**: Il frontend (React) e il backend (Spring Boot) sono completamente disaccoppiati e comunicano esclusivamente via **REST API** sicure.
*   **Vantaggio**: Permette di scalare indipendentemente le due parti (es. 10 istanze frontend su CDN e 2 backend server) e consente di aggiornare la UI senza toccare la logica core (o viceversa).

### 2. Containerizzazione Ibrida
*   **Perché**: I servizi infrastrutturali (Database, Identity Provider, Vault) girano su **Docker**, mentre le applicazioni di business (Backend, Frontend) girano native sull'host.
*   **Vantaggio**: Garantisce che infrastruttura critica sia immutabile e isolata, mentre permette agli sviluppatori di iterare velocemente sul codice (Hot-Reload) senza dover ricostruire immagini Docker ad ogni modifica.

### 3. Identity & Access Management (IAM) Esterno
*   **Scelta**: **Keycloak**.
*   **Giustificazione**: Invece di reinventare l'autenticazione nel backend (rischio sicurezza), deleghiamo tutto a Keycloak. Il backend non gestisce password, ma valida solo **Token JWT** firmati. Questo garantisce standard industriali (OAuth2/OIDC) e funzionalità avanzate (MFA, Social Login, Session Management) "gratis".

### 4. Gestione Segreti Centralizzata
*   **Scelta**: **HashiCorp Vault**.
*   **Giustificazione**: Le credenziali del database non sono scritte nel codice (`application.yml`), ma iniettate dinamicamente all'avvio da Vault. Questo previene il "Secret Sprawl" e permette la rotazione delle password senza ri-deployare l'applicazione.

### 5. Storage Ibrido (DB + File System Cifrato)
*   **Struttura**: Metadati su PostgreSQL, File binari su Disco (cartella `uploads`).
*   **Giustificazione**: Salvare BLOB (file PDF) nel database degrada le performance. La soluzione ibrida mantiene il DB leggero e veloce, mentre i file su disco sono protetti da **Encryption AES-128 a riposo** gestita dal livello applicativo, garantendo confidenzialità anche in caso di furto fisico del disco.
