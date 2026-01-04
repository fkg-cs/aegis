package com.aegis.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KeycloakAdminService {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri; // e.g., https://localhost:8444/realms/Aegis-Intel

    // CREDENZIALI ADMIN (Master Realm)
    private final String adminUsername = "admin";
    private final String adminPassword = "admin";
    private final String adminClientId = "admin-cli";
    private final String adminRealm = "master"; // L'admin globale vive qui

    // RestTemplate customizzato per ignorare errori SSL (Self-Signed) in DEV
    private final RestTemplate restTemplate = createInsecureRestTemplate();

    /**
     * Aggiorna il livello di clearance di un utente su Keycloak.
     * Logica: GET User -> Update Attributes -> PUT User
     */
    /**
     * Aggiorna il livello di clearance di un utente su Keycloak.
     * Logica: Find UUID by Username -> GET User -> Update Attributes -> PUT User
     */
    public void updateUserClearance(String username, int newLevel) {
        try {
            String token = getAdminAccessToken();
            String adminUrlBase = issuerUri.replace("/realms/", "/admin/realms/");

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 0. TROVA UUID DA USERNAME
            // Endpoint: /users?username=kikko
            String searchUrl = adminUrlBase + "/users?username=" + username;
            HttpEntity<Void> searchEntity = new HttpEntity<>(headers);
            ResponseEntity<List<Map<String, Object>>> searchResponse = restTemplate.exchange(
                searchUrl, 
                HttpMethod.GET, 
                searchEntity, 
                new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            List<Map<String, Object>> users = searchResponse.getBody();
            if (users == null || users.isEmpty()) {
                 throw new RuntimeException("Utente con username '" + username + "' non trovato su Keycloak.");
            }
            
            // Keycloak search è "fuzzy" a volte, o "exact" a seconda della versione. 
            // Filtriamo per sicurezza match esatto (case insensitive)
            String userId = users.stream()
                .filter(u -> username.equalsIgnoreCase((String) u.get("username")))
                .findFirst()
                .map(u -> (String) u.get("id"))
                .orElseThrow(() -> new RuntimeException("Nessun match esatto per username '" + username + "'"));

            // 1. GET User Representation (by UUID)
            String userUrl = adminUrlBase + "/users/" + userId;
            ResponseEntity<Map> getResponse = restTemplate.exchange(userUrl, HttpMethod.GET, searchEntity, Map.class);
            Map<String, Object> userRep = getResponse.getBody();

            if (userRep == null) {
                throw new RuntimeException("Utente UUID " + userId + " non trovato (dopo averlo cercato??)");
            }

            // 2. Modifica Attributi
            Map<String, Object> attributes = (Map<String, Object>) userRep.get("attributes");
            if (attributes == null) {
                attributes = new HashMap<>();
            }
            attributes.put("clearance_level", Collections.singletonList(String.valueOf(newLevel)));
            userRep.put("attributes", attributes);

            // 3. PUT User Representation (Update Completo)
            HttpEntity<Map<String, Object>> putEntity = new HttpEntity<>(userRep, headers);
            restTemplate.exchange(userUrl, HttpMethod.PUT, putEntity, Void.class);

            System.out.println(">>> KEYCLOAK SYNC SUCCESS: Clearance utente " + username + " (" + userId + ") aggiornata a " + newLevel);

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String errorMsg = "ERRORE: HTTP KEYCLOAK (" + e.getStatusCode() + "): " + e.getResponseBodyAsString();
            System.err.println(">>> " + errorMsg);
            throw new RuntimeException(errorMsg, e);
        } catch (Exception e) {
            String errorMsg = "ERRORE: CRITICO SYNC KEYCLOAK: " + e.getMessage();
            System.err.println(">>> " + errorMsg);
            throw new RuntimeException(errorMsg, e);
        }
    }

    // TEST CONNESSIONE AVVIO
    @jakarta.annotation.PostConstruct
    public void testConnection() {
        System.out.println(">>> KEYCLOAK ADMIN SERVICE: Test connessione in corso...");
        try {
            String token = getAdminAccessToken();
            System.out.println(">>> KEYCLOAK ADMIN SERVICE: Connessione riuscita! Token ottenuto.");
        } catch (Exception e) {
            System.err.println(">>> KEYCLOAK ADMIN SERVICE: Test FALLITO. Impossibile contattare Keycloak o credenziali errate.");
            e.printStackTrace();
        }
    }

    private String getAdminAccessToken() {
        // L'admin si autentica sul realm MASTER, non su Aegis-Intel
        // Estraiamo la base URL (https://localhost:8444)
        String baseUrl = issuerUri.substring(0, issuerUri.indexOf("/realms/"));
        String tokenUrl = baseUrl + "/realms/" + adminRealm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("grant_type", "password");
        map.add("client_id", adminClientId);
        map.add("username", adminUsername);
        map.add("password", adminPassword);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(map, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, entity, Map.class);
            return (String) response.getBody().get("access_token");
        } catch (Exception e) {
            throw new RuntimeException("Impossibile ottenere token Admin da Keycloak (Master Realm): " + e.getMessage(), e);
        }
    }

    // SSL BYPASS (SOLO PER SVILUPPO) - Aggiornato per HttpClient 5 (Spring Boot 3)
    private RestTemplate createInsecureRestTemplate() {
        try {
            javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[]{
                new javax.net.ssl.X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                }
            };

            javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());

            // HttpClient 5 Implementation
            org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory csf = org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder.create()
                    .setSslContext(sc)
                    .setHostnameVerifier(org.apache.hc.client5.http.ssl.NoopHostnameVerifier.INSTANCE)
                    .build();

            org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager cm = org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(csf)
                    .build();

            org.apache.hc.client5.http.impl.classic.CloseableHttpClient httpClient = org.apache.hc.client5.http.impl.classic.HttpClients.custom()
                    .setConnectionManager(cm)
                    .build();

            org.springframework.http.client.HttpComponentsClientHttpRequestFactory requestFactory =
                    new org.springframework.http.client.HttpComponentsClientHttpRequestFactory();
            requestFactory.setHttpClient(httpClient);

            return new RestTemplate(requestFactory);
        } catch (Exception e) {
            throw new RuntimeException("Errore configurazione SSL Context Insecure", e);
        }
    }
}
