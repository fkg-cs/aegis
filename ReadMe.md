# Aegis: Secure Compartmentalized Intelligence System

> **Sistema Informativo di Intelligence Compartimentata**
> *Piattaforma framework ultra-sicura per la gestione operativa di missioni e personale in contesti statali ad alto rischio, allineata ai massimi standard di sicurezza e alla normativa NOS (Nulla Osta di Sicurezza).*

---

## 1. Panoramica del Progetto

**Aegis** è un sistema informativo web progettato per contesti governativi e parastatali, operando su un modello **"Security First"**. La piattaforma offre un framework per la gestione di missioni e personale, adottando un'architettura moderna a microservizi dockerizzati orchestrata per garantire resilienza e isolamento.

---

## 2. Filosofia e Principi di Sicurezza

Aegis è costruito secondo i principi della **Security by Design** e della **Data Minimization**. Il sistema espone solo i dati necessari e riduce la superficie d'attacco.

### Pilastri Fondamentali
* **Zero-Trust Networking:** Nessuna fiducia implicita tra i componenti; le comunicazioni sono cifrate via TLS con autenticazione mutuale.
* **Accesso Gerarchico Rigido (Bell-LaPadula):** L'accesso è basato su livelli di segretezza (*Clearance Level*); un utente non può accedere o creare missioni con classificazione superiore alla propria.
* **Minimizzazione dei Dati (Pattern DTO):** Aegis utilizza i *Data Transfer Objects* per prevenire l'esposizione accidentale di PII. Ad esempio, per un agente l'API restituisce solo il *Code Name*, oscurando nome reale e telefono (visibili solo ai supervisori).

### Matrice di Sicurezza & Defense in Depth
Aegis implementa controlli a più livelli per mitigare le minacce moderne:

| Minaccia / Requisito | Implementazione Tecnica in AEGIS |
| :--- | :--- |
| **Vulnerabilità Logiche (BOLA/BFLA)** | Controlli granulari nel Business Layer e uso di identificativi non sequenziali per prevenire accessi orizzontali non autorizzati. |
| **Enumerazione Dati (IDOR)** | Ricerca missioni esclusivamente tramite **UUID** (Universally Unique Identifier), rendendo impossibile indovinare gli ID delle risorse. |
| **Session Hijacking** | Sessioni **Stateless** basate su token JWT. Nessuna persistenza di sessione server-side. |
| **Data Leakage (Files)** | **Crittografia AES-128** a riposo per tutti gli allegati.<br> **Watermarking Dinamico** sui documenti per evitare foto o condivisioni non autorizzate (es. *"RISERVATO: [USER_ID]"*). |
| **Attacchi Volumetrici (DoS)** | **Bucket4j Rate Limiting**: Filtro attivo su tutti gli endpoint (limite 5000 req/min per IP) per neutralizzare Brute Force e HTTP Flood. |
| **Phishing & XSS** | **NoLinksValidator**: Blocca URL nelle note.<br>**Sanificazione**: Stripping preventivo dei tag HTML lato frontend. |
| **Man-in-the-Middle** | **TLS/HTTPS Forzato**: Backend su porta 8443, Database via JDBC SSL, Keycloak su HTTPS. |
| **Information Disclosure** | **Exception Masking**: Il *GlobalExceptionHandler* sopprime stack trace rivelatori, restituendo messaggi generici. |

### Protocolli crittografici utilizzati
* **Data in Transit:** Backend utilizza HTTPS (Porta 8443, Keystore PKCS12). Connessione DB via JDBC SSL (`sslmode=require`).
* **Data at Rest:** File allegati cifrati con **AES-128** previa verifica integrità (**SHA-256**).
* **Hashing Password:** Gestito da Keycloak tramite standard **PBKDF2/Argon2**.

---

## 3. Architettura del Progetto
<p align="center">
  <img src="./docs/images/schema.png" width="700" alt="Schema Architettura Aegis">
</p>

### Componenti Funzionali
* **Frontend (`aegis-frontend`):** SPA in **React + Vite**. Gestisce UI, cifratura client-side e interazioni REST.
* **Backend (`aegis-backend`):** Resource Server in **Java 21 / Spring Boot 3**. Gestisce logica business, crittografia file, accesso dati e audit.
* **Identity Provider (`aegis-idp`):** Istanza **Keycloak** per SSO, RBAC e emissione JWT.
* **Database (`aegis-db`):** **PostgreSQL 16** per dati strutturati.
* **Secrets Management (`aegis-vault`):** **HashiCorp Vault** per gestione sicura credenziali.

### Giustificazione Tecnologica
1. **Separazione Frontend/Backend:** Comunicazione esclusiva via REST API su HTTPS. Permette scalabilità indipendente.
2. **Containerizzazione Ibrida:** Servizi critici (DB, Keycloak, Vault) su Docker per immutabilità; App su host per sviluppo rapido.
3. **IAM Esterno (Keycloak):** Il backend non gestisce password ma valida solo token JWT. Accesso concesso solo previa verifica NOS (No Self-Registration).
4. **Gestione Segreti (Vault):** Credenziali DB iniettate dinamicamente all'avvio (*Fetch Credentials*) per evitare il *Secret Sprawl*.
5. **Storage Ibrido:** Metadati su DB, File (AES-128) su disco locale. Mantiene il DB performante evitando BLOB pesanti.

### Albero del Progetto
```text
AEGIS/
├── 📂 aegis-frontend/           # [FRONTEND] React Single Page Application (SPA)
│   ├── src/
│   │   ├── App.jsx             # Logica principale UI e Router
│   │   └── keycloak.js         # Configurazione Adapter OIDC
│   ├── public/                 # Asset statici
│   └── package.json            # Dipendenze Node.js
│
├── 📂 backend/
│   └── aegis-backend/          # [BACKEND] Spring Boot Application (Java 21)
│       ├── src/main/java/com/aegis/backend/
│       │   ├── controller/     # REST Endpoints Layer
│       │   ├── service/        # Business Logic Layer
│       │   ├── model/          # JPA Entities
│       │   ├── dto/            # Data Transfer Objects
│       │   └── security/       # Security Configuration (JWT, Filters)
│       ├── uploads/            # [LOCAL STORAGE] File cifrati (AES-128)
│       └── pom.xml             # Dipendenze Maven
│
├── 📂 docker-env/              # [INFRASTRUCTURE] Componenti Dockerizzati
│   ├── 📄 docker-compose.yml   # Orchestratore servizi
│   ├── 📂 init-db/             # Script SQL inizializzazione
│   ├── 📂 themes/              # Temi Keycloak
│   └── 📄 realm-export.json    # Configurazione Realm Keycloak
│
├── 📂 certs/                   # Certificati SSL/TLS condivisi
├── 📂 postgres-ssl/            # Chiavi Database
├── 📂 keycloak-ssl/            # Chiavi Identity Provider
└── 📄 *_SETUP.md               # Guide installazione
```

---

## 4. Stack Tecnologico

| Componente | Tecnologia | Ruolo |
| :--- | :--- | :--- |
| **Backend** | Java 21, Spring Boot 3 | Resource Server, Business Logic |
| **Frontend** | React, Vite | Interfaccia Utente SPA  |
| **Auth** | OIDC, OAuth 2.0, JWT | Standard di Protocollo |
| **IAM** | Keycloak | Identity Provider, MFA, RBAC  |
| **Database** | PostgreSQL 16 | Persistenza Dati  |
| **Security** | HashiCorp Vault | Gestione Segreti  |
| **Crittografia** | AES-128, SHA-256 | Cifratura Dati e Integrità  |

### Approfondimento: Flusso OIDC

Il flusso **OIDC (OpenID Connect)** delega l'autenticazione a Keycloak, che rilascia un token di autorizzazione all'applicazione richiedente.

1.  **Richiesta:** Il client richiede accesso.
2.  **Concessione:** L'utente si autentica (MFA) su Keycloak.
3.  **Token:** Keycloak emette un *Access Token* (JWT).
4.  **Accesso:** Il client allega il token nell'header `Authorization: Bearer`.
5.  **Verifica:** Il backend valida crittograficamente firma (JWK), scadenza (`exp`) ed emittente (`iss`) prima di servire la risorsa.

---

## 5. Modello Operativo e Ruoli

Il sistema gestisce la gerarchia e l'accesso ai dati su tre livelli di segretezza, ispirandosi alla logica **Bell-LaPadula**.

| Ruolo | Permessi e Visibilità |
| :--- | :--- |
| **Super Supervisor** | Amministrazione totale, visibilità globale, accesso ai Log di Audit.  |
| **Supervisor** | Creazione missioni, coordinamento assegnazione agenti a missioni.  |
| **Agent** | Operatività sul campo. Accesso limitato a pagina proprie missioni.  |

### Nota sulla Gestione delle Utenze
> La scelta architetturale di **inibire la registrazione autonoma degli utenti** risponde a un principio fondamentale di sicurezza nazionale. L'accesso deve essere concesso esclusivamente tramite canali istituzionali gerarchici, impedendo a chiunque di registrarsi senza una preventiva verifica della clearance e del Nulla Osta di Sicurezza.

---

## 7. Test d'uso

## 8. Test d'abuso

## 9. Guida all'Installazione (Docker Environment)

**Prerequisiti:** Docker Desktop, Java 21, Node.js 20+.

### 1. Avvio Infrastruttura
Lanciare i servizi di supporto (DB, Keycloak, Vault).

```bash
cd docker-env
docker-compose up -d
# Attendere l'inizializzazione dei container.
```

### 2. Configurazione Backend
Il backend attende che Vault e DB siano pronti.

```bash
cd backend/aegis-backend
mvn spring-boot:run
```
*Il server si avvierà sulla porta **8443** (HTTPS).*

### 3. Avvio Frontend

```bash
cd aegis-frontend
npm install
npm run dev
```
*Accessibile a https://localhost:5173.*

Nota: Essendo un ambiente locale con certificati auto-firmati (certs/), sarà necessario accettare le eccezioni di sicurezza nel browser per localhost:8443 (Backend) e localhost:8444 (Keycloak).

**Nota per l'installazione:** Questa guida fornisce i passaggi rapidi per l'avvio tramite Docker.
> Per istruzioni dettagliate passo-passo specifiche per il tuo sistema operativo (configurazione variabili d'ambiente, prerequisiti, ecc.), consulta i file dedicati presenti nella root del progetto:
> * `WINDOWS_SETUP.md`
> * `MAC_SETUP.md`
> * `LINUX_SETUP.md`

## 10. Riferimenti Normativi e Teorici

L'architettura di sicurezza di Aegis è stata progettata in conformità con i seguenti standard governativi e modelli accademici:

* **[DPCM 6 novembre 2015](https://www.gazzettaufficiale.it/eli/id/2015/12/01/15A09048/sg)** – *"Disposizioni per la tutela amministrativa del segreto di Stato e delle informazioni classificate"*: Costituisce il riferimento normativo per la gestione del **NOS** (Nulla Osta di Sicurezza) e per i livelli di classificazione implementati nel sistema.

* **Modello Bell-LaPadula (1973)** – Modello formale per il controllo degli accessi mandatorio (**M.A.C.**): Il sistema applica rigorosamente la proprietà matematica *No Read Up* per garantire la confidenzialità dei dati tra livelli gerarchici differenti.

* **[NIST SP 800-207](https://csrc.nist.gov/publications/detail/sp/800-207/final)** – *"Zero Trust Architecture"*: Standard statunitense che guida l'approccio architetturale del progetto, basato sul principio che nessuna fiducia sia implicita (indipendentemente dalla posizione di rete) e sulla verifica continua di ogni transazione.


## 11. Risoluzione Problemi (Troubleshooting)

In caso di difficoltà durante l'avvio o l'utilizzo della piattaforma in ambiente locale, consultare la seguente tabella:

| Sintomo | Causa Probabile | Soluzione Tecnica |
| :--- | :--- | :--- |
| **Browser: "La connessione non è privata" / "Not Secure"** | Utilizzo di certificati SSL auto-firmati per `localhost` (non riconosciuti dalle CA pubbliche). | Cliccare su **"Avanzate"** -> **"Procedi su localhost (non sicuro)"**. È necessario accettare l'eccezione sia per il Frontend (`:5173`) che per il Backend/Keycloak (`:8443`, `:8444`). |
| **Backend: "Connection Refused" all'avvio** | *Race Condition* nell'orchestrazione Docker: il Backend tenta di connettersi a Vault o DB prima che siano completamente inizializzati. | Attendere 30 secondi affinché i servizi infrastrutturali siano pronti, quindi riavviare solo il backend: `docker restart aegis-backend` (o rilanciare `mvn spring-boot:run` se in locale). |
| **Login: Reindirizzamento continuo (Loop)** | Cookie di sessione obsoleti o conflitti di cache nel browser. | Provare l'accesso in una finestra di **Navigazione in Incognito** o pulire i cookie relativi a `localhost`. Verificare anche che l'orologio di sistema sia sincronizzato. |
| **Swagger UI: "Network Error" / "Failed to fetch"** | Il browser blocca le chiamate AJAX verso il backend perché il certificato SSL non è stato esplicitamente accettato. | Aprire una nuova scheda, visitare `https://localhost:8443/api/hello` (o un endpoint qualsiasi) e accettare il rischio di sicurezza. Ricaricare Swagger UI. |
| **Vault: "Sealed" status** | Il container di Vault si è riavviato e ha perso lo stato di *unseal* (se non configurato per l'auto-unseal in dev). | Eseguire lo script di ripristino o riavviare l'intero stack `docker-compose down && docker-compose up -d`. |
| **Frontend: Schermata Bianca** | Il Frontend non riesce a contattare Keycloak per scaricare la configurazione OIDC. | Verificare che Keycloak sia raggiungibile via browser a `https://localhost:8444` e che non ci siano blocchi CORS nella console sviluppatore (F12). |
