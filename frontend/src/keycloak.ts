import Keycloak from 'keycloak-js';

// One single Keycloak instance for the whole app.
// Values come from the .env file (see .env.example).
const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL,
  realm: import.meta.env.VITE_KEYCLOAK_REALM,
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID,
});

export default keycloak;
