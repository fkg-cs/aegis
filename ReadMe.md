# 🛡️ Aegis - Compartmented Intelligence System

> **Progetto Universitario di Sicurezza Applicativa (AppSec)**
> *Un'architettura Zero-Trust per la gestione di intelligence compartimentata.*

**Aegis** è una piattaforma web ad alta sicurezza progettata per operare in contesti critici (Governativo/Intelligence). Il sistema mitiga i rischi di esfiltrazione dati e movimenti laterali adottando un **Positive Security Model**: ogni interazione è negata di default a meno che non sia esplicitamente autorizzata da policy basate su identità, ruolo e contesto.

---

## 🏗️ Architettura Tecnica

Il sistema è basato su un'architettura a microservizi containerizzata:

* **Frontend:** React + Vite (Single Page Application).
* **Backend:** Java 21 + Spring Boot 3 (Security, Web, Data JPA).
* **Identity Provider (IdP):** Keycloak (OAuth2 / OpenID Connect).
* **Secrets Management:** HashiCorp Vault (Gestione dinamica credenziali DB).
* **Database:** PostgreSQL 16 (Containerizzato).
* **Infrastruttura:** Docker & Docker Compose con Resource Limits (Anti-DoS).

---

## 🔒 Security Implementation (OWASP Mitigation)

Aegis implementa difese in profondità ("Defense in Depth") mappate sulle vulnerabilità **OWASP API Security Top 10**:

| Area di Rischio | Vulnerabilità (OWASP) | Soluzione Implementata in Aegis | Tecnologia |
| :--- | :--- | :--- | :--- |
| **Access Control** | **BOLA** (Broken Object Level Auth) | Controllo granulare (`@PreAuthorize`) su ogni UUID. Verifica incrociata tra il livello di classificazione del dossier (es. TOP SECRET) e le clearance dell'utente. | Spring Security EL |
| **Access Control** | **BFLA** (Broken Function Level Auth) | Segregazione endpoint: solo `ADMIN` può listare tutto, solo `ANALYST` può caricare file. | RBAC (Role-Based Access Control) |
| **Availability** | **DoS** (Denial of Service) | Algoritmo **Token Bucket** per limitare le richieste per IP (5 req/min) e Limiti risorse Docker (CPU/RAM). | Bucket4j & Docker Deploy Limits |
| **Data Integrity** | **Malicious File Upload** | Analisi dei **Magic Bytes** (firma esadecimale) per prevenire upload di malware rinominati (es. `.exe` in `.pdf`). Whitelist rigorosa. | Apache Tika |
| **Confidentiality** | **Security Misconfiguration** | Nessuna credenziale hardcodata. Le password del DB sono iniettate a runtime tramite un tunnel sicuro. | HashiCorp Vault |
| **Confidentiality** | **Insufficient Transport Layer** | Traffico cifrato End-to-End tramite **TLS 1.3 (HTTPS)** con certificato keystore locale. | Java Keytool (Self-Signed) |
| **Audit** | **Insufficient Logging** | Logging strutturato degli eventi di sicurezza (Access Denied/Granted) per garantire la Non-Ripudiabilità. | SLF4J / Logback |
| **Session** | **Session Hijacking** | Gestione Stateless (JWT), Auto-refresh del token lato client, Header di sicurezza CSP (Content Security Policy) e X-Frame-Options. | Spring Security Headers |

---

## 🚀 Guida all'Installazione e Avvio

### Prerequisiti
* Docker & Docker Compose
* Java 21 (JDK)
* Node.js & npm

### 1. Avvio Infrastruttura (Docker)
Dalla root del progetto:

```bash
# Avvia Database, Vault e Keycloak
sudo docker-compose up -d
