import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { ShieldCheck } from 'lucide-react';
import keycloak from './keycloak';

/** What any component can read with `useAuth()`. */
type AuthContextValue = {
  ready: boolean;              // false while we are still talking to Keycloak at startup
  authenticated: boolean;      // true when the user is logged in
  user: { name: string; email?: string; username?: string; initials: string; roles: string[] } | null;
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
  const t = keycloak.tokenParsed as (Record<string, unknown> & {
    realm_access?: { roles?: unknown };
  }) | undefined;
  if (!t) return null;
  const name = typeof t.name === 'string' ? t.name :
    typeof t.preferred_username === 'string' ? t.preferred_username : 'Utilisateur';
  const initials = name.split(/\s+/).map(w => w[0]).join('').slice(0, 2).toUpperCase();
  const roles = Array.isArray(t.realm_access?.roles)
    ? t.realm_access.roles.filter((role): role is string => typeof role === 'string')
    : [];
  return {
    name,
    email: typeof t.email === 'string' ? t.email : undefined,
    username: typeof t.preferred_username === 'string' ? t.preferred_username : undefined,
    initials,
    roles,
  };
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

export function RequireStaff({ children }: { children: ReactNode }) {
  const { authenticated, login, user } = useAuth();
  useEffect(() => {
    if (!authenticated) login(window.location.pathname + window.location.search);
  }, [authenticated]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!authenticated) return null;
  if (!user?.roles.some(role => role === 'agent' || role === 'admin')) {
    return (
      <main className="empty-state">
        <div className="empty-icon"><ShieldCheck size={25}/></div>
        <h1>Accès réservé à l’équipe</h1>
        <p className="muted">Un rôle agent ou admin est nécessaire pour gérer les files.</p>
        <a href="/home" className="btn-dark">Retour à l’accueil</a>
      </main>
    );
  }
  return <>{children}</>;
}
