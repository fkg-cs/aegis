import Keycloak from "keycloak-js";

const keycloakConfig = {
  url: "https://localhost:8444", 
  realm: "Aegis-Intel",
  clientId: "aegis-frontend",
};

const keycloak = new Keycloak(keycloakConfig);

export default keycloak;
