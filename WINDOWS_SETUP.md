# 🪟 Guida Configurazione Aegis per Windows(tramite WSL2)

Questa guida spiega come configurare ed eseguire il progetto **Aegis** su Windows 10 o 11.
Consigliamo vivamente l'uso di **WSL2 (Windows Subsystem for Linux)** per evitare problemi noti con i permessi dei file Docker e terminatori di riga.

---

## Prerequisiti

### 1. Attivare WSL2 e Installare Ubuntu
Se non hai ancora WSL2:
1. Apri **PowerShell** come Amministratore.
2. Esegui: `wsl --install`
3. Riavvia il computer quando richiesto.
4. Dopo il riavvio, si aprirà una finestra di Ubuntu per creare username e password (ricordale!).

### 2. Installare Docker Desktop per Windows
1. Scarica e installa [Docker Desktop](https://www.docker.com/products/docker-desktop).
2. Durante l'installazione, assicurati che la voce "Use WSL 2 based engine" sia selezionata.
3. Apri Docker Desktop -> **Settings (Ingranaggio)** -> **Resources** -> **WSL Integration**.
4. Attiva lo switch accanto alla tua distro Ubuntu. Clicca "Apply & Restart".

### 3. Installare Java e Node.js (Dentro Ubuntu)
Tutte le operazioni andranno fatte nel **terminale di Ubuntu**, NON su PowerShell.

Apri Ubuntu ed esegui:
```bash
# Aggiorna i pacchetti
sudo apt update && sudo apt upgrade -y

# Installa prerequisiti
sudo apt install -y unzip zip curl

# Installa Java 21 (Amazon Corretto o OpenJDK)
wget https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.deb
sudo dpkg -i jdk-21_linux-x64_bin.deb

# Installa Node.js (v20 LTS consigliata)
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs
```

---

## Setup del Progetto

### 1. Clonare il Repository (Dentro Ubuntu)
È fondamentale clonare il progetto **dentro il filesystem di Linux** (`/home/tuo_nome/...`) e non su quello di Windows (`/mnt/c/...`) per evitare problemi di permessi.

```bash
cd ~
# Clona il progetto (sostituisci con l'URL reale)
git clone <URL_DEL_TUO_PROGETTO>
cd Aegis-Project
```

### 2. Risolvere il problema dei Permessi SSL
PostgreSQL richiede che il file della chiave privata sia posseduto dall'utente del database (UID 70). Questo comando è **essenziale**:

```bash
sudo chown 70:70 postgres-ssl/server.key
sudo chmod 600 postgres-ssl/server.key
```

### 3. Avviare i Container
```bash
docker compose up -d
```
Attendi qualche secondo che i servizi (Database, Vault, Keycloak) siano pronti.

---

##  Inizializzazione Security

### 1. Configurare Vault
Vault deve essere inizializzato. Esegui questi comandi nel terminale Ubuntu:

```bash
# Imposta variabili ambiente temporanee
export VAULT_ADDR='http://127.0.0.1:8200'
export VAULT_TOKEN='root_token_segreto'

# Abilita Key-Value storage
vault secrets enable -path=secret kv 2>/dev/null || true

# Inserisci le credenziali del DB in Vault
vault kv put secret/aegis-backend username=admin password=password_segreta_dev
```

### 2. Avviare il Backend
Il backend è un'applicazione Spring Boot.

```bash
cd backend/aegis-backend
# Assicurati che lo script mvnw sia eseguibile
chmod +x mvnw

# Avvia il backend
export VAULT_TOKEN='root_token_segreto'
./mvnw spring-boot:run
```
Il backend partirà su **https://localhost:8443**.

### 3. Avviare il Frontend
Apri un **nuovo terminale Ubuntu** (o tab), naviga nella cartella del progetto:

```bash
cd ~/Aegis-Project/aegis-frontend

# Installa dipendenze (solo la prima volta)
npm install

# Avvia server di sviluppo
npm run dev
```

Il frontend sarà accessibile a **https://localhost:5173**.

---

##  Troubleshooting Comuni

*   **Errore `mvnw: permission denied`**: Esegui `chmod +x mvnw` nella cartella del backend.
*   **Errore Database (Connection Refused)**: Hai eseguito `sudo chown 70:70 ...` sulla chiave SSL? Se Docker gira su Windows puro (senza WSL2), questo passaggio fallirà e il DB non partirà. Usa WSL2.
*   **Porte occupate**: Assicurati di non avere altri servizi su porte 8080, 5432 o 8200.
