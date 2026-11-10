import { createRoot } from 'react-dom/client';

import App from './App';
import { AuthProvider } from './auth';
import { ErrorBoundary } from '@/components/error-boundary';

import './index.css';

createRoot(document.getElementById('root')!, {
  // Keeps caught errors off reportError(), which would raise the dev overlay.
  onCaughtError: (error, errorInfo) => {
    console.error(error, errorInfo.componentStack);
  },
}).render(
  <ErrorBoundary>
    {/* AuthProvider runs keycloak.init() (Authorization Code + PKCE) once, then renders the app. */}
    <AuthProvider>
      <App />
    </AuthProvider>
  </ErrorBoundary>,
);
