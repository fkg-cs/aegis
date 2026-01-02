#  Guida Configurazione Aegis per Linux (Ubuntu/Debian)

Questa guida spiega come configurare ed eseguire il progetto **Aegis** su un ambiente nativo Linux (Ubuntu 22.04+ o Debian 12+).

---

##  Prerequisiti

### 1. Installare Docker & Docker Compose
Se non hai ancora Docker installato:

```bash
# Rimuovi versioni vecchie
sudo apt-get remove docker docker-engine docker.io containerd runc

# Setup repository
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# Installazione
echo \
  "deb [arch="$(dpkg --print-architecture)" signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  "$(. /etc/os-release && echo "$VERSION_CODENAME")" stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Abilita Docker senza sudo (opzionale ma consigliato per dev)
sudo usermod -aG docker $USER
newgrp docker
```

### 2. Installare Java, Node.js e Vault CLI
Esegui nel terminale:

```bash
# Aggiorna i pacchetti
sudo apt update && sudo apt upgrade -y

# Installa prerequisiti
sudo apt install -y unzip zip curl wget gpg

# Installa Java 21 (Amazon Corretto o OpenJDK)
wget https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.deb
sudo dpkg -i jdk-21_linux-x64_bin.deb

# Installa Node.js (v20 LTS)
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs

# Installa Vault CLI (per inizializzazione manuale)
wget -O- https://apt.releases.hashicorp.com/gpg | sudo gpg --dearmor -o /usr/share/keyrings/hashicorp-archive-keyring.gpg
echo "deb [signed-by=/usr/share/keyrings/hashicorp-archive-keyring.gpg] https://apt.releases.hashicorp.com $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/hashicorp.list
sudo apt update && sudo apt install vault
```

---

## Setup del Progetto

### 1. Clonare il Repository

```bash
cd ~
# Clona il progetto (sostituisci con l'URL reale)
git clone <URL_DEL_TUO_PROGETTO>
cd Aegis-Project
```

### 2. Risolvere i Permessi SSL
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
Vault deve essere inizializzato con le credenziali del DB. Esegui nel terminale:

```bash
# Imposta variabili ambiente temporanee
export VAULT_ADDR='http://127.0.0.1:8200'
export VAULT_TOKEN='root_token_segreto'

# Abilita Key-Value storage
vault secrets enable -path=secret kv 2>/dev/null || true

# Inserisci le credenziali del DB in Vault
# NOTA: Queste devono coincidere con quelle in docker-compose.yml
vault kv put secret/aegis-backend username=admin password=password_segreta_dev
```

### 2. Avviare il Backend (Spring Boot)
Apri un terminale nella cartella del backend:

```bash
cd backend/aegis-backend
# Assicurati che lo script mvnw siano eseguibile
chmod +x mvnw

# Avvia il backend
export VAULT_TOKEN='root_token_segreto'
./mvnw spring-boot:run
```
Il backend partirà su **https://localhost:8443**.

### 3. Avviare il Frontend (React)
Apri un **nuovo terminale**, naviga nella cartella del frontend:

```bash
cd ~/Aegis-Project/aegis-frontend

# Installa dipendenze (solo la prima volta)
npm install

# Avvia server di sviluppo
npm run dev
```

Il frontend sarà accessibile a **https://localhost:5173**.

---

##  Troubleshooting

*   **Errore `mvnw: permission denied`**: Esegui `chmod +x mvnw` nella cartella del backend.
*   **Errore Database (Connection Refused)**: Verifica che i container siano su (`docker compose ps`). Se non partono, controlla i log di postgres (`docker logs aegis-db`) per errori sui permessi dei file SSL.
*   **Porte occupate**: Assicurati di non avere altri servizi (es. un altro Postgres o Tomcat locale) su porte 8080, 5432 o 8200.
