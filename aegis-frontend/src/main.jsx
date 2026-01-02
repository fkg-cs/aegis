import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import keycloak from './keycloak';

const root = ReactDOM.createRoot(document.getElementById('root'));

// Inizializza Keycloak
// MODIFICA CRITICA: checkLoginIframe: false risolve l'errore di timeout con SSL autofirmati
keycloak.init({ 
    onLoad: 'login-required', 
    checkLoginIframe: false, // <--- QUESTA RIGA RISOLVE IL PROBLEMA "Timeout waiting for iframe"
    pkceMethod: 'S256'       // Aggiunge sicurezza extra (Proof Key for Code Exchange)
}).then((authenticated) => {
  if (authenticated) {
    console.log("✅ Login avvenuto con successo!");

    // --- LOGICA DI AUTO-REFRESH DEL TOKEN ---
    // Controlla ogni 60 secondi (60000 ms)
    setInterval(() => {
      // Chiede a Keycloak di rinnovare il token se scade entro 70 secondi
      keycloak.updateToken(70)
        .then((refreshed) => {
          if (refreshed) {
            console.log('🔄 Token rinnovato automaticamente! La sessione è stata estesa.');
          } else {
            // Il token è ancora valido per più di 70 secondi, non faccio nulla
            // console.log('Token ancora valido');
          }
        })
        .catch(() => {
          console.error('❌ Errore rinnovo token o sessione scaduta. Logout forzato.');
          keycloak.logout();
        });
    }, 60000);
    // ----------------------------------------

    // Renderizza l'app solo se autenticato
    root.render(
      <React.StrictMode>
        <App />
      </React.StrictMode>
    );
  } else {
    console.error("❌ Autenticazione fallita");
  }
}).catch((error) => {
  console.error("❌ Errore inizializzazione Keycloak", error);
});
