import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import keycloak from './keycloak';

/** What any component can read with `useAuth()`. */
type AuthContextValue = {
  ready: boolean;              // false while we are still talking to Keycloak at startup
  authenticated: boolean;      // true when the user is logged in
  user: { name: string; email?: string; username?: string; initials: string } | null;
  token?: string;              // access token, send it to your API as "Authorization: Bearer <token>"
  login: (redirectPath?: string) => void;
  register: () => void;
  logout: () => void;
  getValidToken: () => Promise<string | undefined>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

// keycloak.init() must only run ONCE (React may mount components twice in dev).
let initPromise: Promise<boolean> | null = null;
function initKeycloak() {
  if (!initPromise) {
    initPromise = keycloak.init({
      onLoad: 'check-sso',                 // don't force login, just check if the user already has a session
      pkceMethod: 'S256',                  // Authorization Code flow + PKCE
      checkLoginIframe: false,             // avoids third-party-cookie problems in modern browsers
      silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
    });
  }
  return initPromise;
}

function readUser() {
  const t = keycloak.tokenParsed as Record<string, string> | undefined;
  if (!t) return null;
  const name = t.name || t.preferred_username || 'Utilisateur';
  const initials = name.split(/\s+/).map(w => w[0]).join('').slice(0, 2).toUpperCase();
  return { name, email: t.email, username: t.preferred_username, initials };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);
  const [token, setToken] = useState<string | undefined>();

  useEffect(() => {
    // Refresh the access token shortly before it expires.
    keycloak.onTokenExpired = () => {
      keycloak.updateToken(30)
        .then(() => setToken(keycloak.token))
        .catch(() => { setAuthenticated(false); setToken(undefined); });
    };
    keycloak.onAuthLogout = () => { setAuthenticated(false); setToken(undefined); };

    initKeycloak()
      .then(auth => { setAuthenticated(auth); setToken(keycloak.token); })
      .catch(err => console.error('Keycloak init failed — is the server running and the client configured?', err))
      .finally(() => setReady(true));
  }, []);

  const value: AuthContextValue = {
    ready,
    authenticated,
    token,
    user: authenticated ? readUser() : null,
    // After login, Keycloak sends the user back to this URL.
    login: (redirectPath = '/home') => keycloak.login({ redirectUri: window.location.origin + redirectPath }),
    register: () => keycloak.register({ redirectUri: window.location.origin + '/home' }),
    logout: () => keycloak.logout({ redirectUri: window.location.origin + '/' }),
    // Use this before calling your backend: it refreshes the token if needed.
    getValidToken: async () => {
      if (!keycloak.authenticated) return undefined;
      await keycloak.updateToken(30);
      return keycloak.token;
    },
  };

  if (!ready) return <div style={{ display: 'grid', placeItems: 'center', minHeight: '100vh' }}>Chargement…</div>;
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}

/** Wrap a page with this to make it accessible only to logged-in users. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { authenticated, login } = useAuth();
  useEffect(() => {
    if (!authenticated) login(window.location.pathname + window.location.search);
  }, [authenticated]); // eslint-disable-line react-hooks/exhaustive-deps
  return authenticated ? <>{children}</> : null;
}
