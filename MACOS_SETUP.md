#  Guida Configurazione Aegis per macOS (Intel & Apple Silicon)

Questa guida spiega come configurare ed eseguire il progetto **Aegis** su macOS.
I comandi sono compatibili sia con processori Intel che Apple Silicon (M1/M2/M3).

---

## Prerequisiti

### 1. Installare Homebrew
Se non hai [Homebrew](https://brew.sh/) installato, apri il terminale ed esegui:

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

### 2. Installare Docker Desktop per Mac
1. Scarica e installa [Docker Desktop per Mac (Apple Chip o Intel Chip)](https://www.docker.com/products/docker-desktop).
2. Avvialo e attendi che l'icona della balena nella barra dei menu smetta di animarsi.

### 3. Installare Java, Node.js e Vault
Usa Homebrew per installare tutto il necessario:

```bash
# Aggiorna brew
brew update

# Installa Java 21 (OpenJDK)
brew install openjdk@21
# Collega java al sistema (segui eventuali istruzioni a video di brew, solitamente:)
sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk

# Installa Node.js (v20 LTS)
brew install node@20
brew link node@20

# Installa Vault CLI
brew tap hashicorp/tap
brew install hashicorp/tap/vault
```

Verifica le installazioni:
```bash
java -version
node -v
vault -version
```

---

##  Setup del Progetto

### 1. Clonare il Repository

```bash
cd ~
# Clona il progetto
git clone <URL_DEL_TUO_PROGETTO>
cd Aegis-Project
```

### 2. Risolvere i Permessi SSL
PostgreSQL richiede permessi stretti per la chiave privata (UID 70, che è l'utente postgres nel container). Anche su Mac, questo comando è **essenziale** per far funzionare il bind mount:

```bash
sudo chown 70:70 postgres-ssl/server.key
sudo chmod 600 postgres-ssl/server.key
```

### 3. Avviare i Container
```bash
docker compose up -d
```
Attendi qualche secondo che i servizi siano pronti.

---

## Inizializzazione Security

### 1. Configurare Vault
Vault deve essere inizializzato. Esegui nel terminale:

```bash
# Imposta variabili ambiente temporanee
export VAULT_ADDR='http://127.0.0.1:8200'
export VAULT_TOKEN='root_token_segreto'

# Abilita Key-Value storage
vault secrets enable -path=secret kv 2>/dev/null || true

# Inserisci le credenziali del DB in Vault
vault kv put secret/aegis-backend username=admin password=password_segreta_dev
```

### 2. Avviare il Backend (Spring Boot)
Apri un tab del terminale nella cartella del backend:

```bash
cd backend/aegis-backend
# Assicurati che lo script mvnw sia eseguibile
chmod +x mvnw

# Avvia il backend
export VAULT_TOKEN='root_token_segreto'
./mvnw spring-boot:run
```
Il backend partirà su **https://localhost:8443**.

### 3. Avviare il Frontend (React)
Apri un **nuovo tab del terminale**, naviga nella cartella del frontend:

```bash
cd ~/Aegis-Project/aegis-frontend

# Installa dipendenze (solo la prima volta)
npm install

# Avvia server di sviluppo
npm run dev
```

Il frontend sarà accessibile a **https://localhost:5173**.

---

##  Note Specifiche per macOS

*   **Avviso Sicurezza "Sviluppatore non verificato"**: Se macOS blocca l'esecuzione di Java o Docker, vai in **Impostazioni di Sistema** -> **Privacy e Sicurezza** -> **Consenti comunque**.
*   **Porta 5000/7000**: macOS usa la porta 5000 o 7000 per AirPlay Receiver. Se hai conflitti, disabilitalo in Impostazioni -> Condivisione -> Ricevitore AirPlay. (Aegis usa porte standard 8080/8443/5432/8200 quindi non dovresti avere problemi).
