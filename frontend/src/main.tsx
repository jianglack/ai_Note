import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import App from './App';
import LoginPage from './pages/LoginPage';
import { useAuthStore } from './stores/authStore';
import { shouldShowErrorStack } from './security/contentSafety';
import './tailwind.css';
import './styles.css';

class ErrorBoundary extends React.Component<{ children: React.ReactNode }, { error: Error | null }> {
  state = { error: null as Error | null };
  static getDerivedStateFromError(error: Error) { return { error }; }
  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error('React ErrorBoundary caught:', error, info.componentStack);
  }
  render() {
    if (this.state.error) {
      return (
        <div style={{ padding: 40, fontFamily: 'monospace' }}>
          <h2 style={{ color: '#b8452e' }}>页面崩溃了</h2>
          <pre style={{ whiteSpace: 'pre-wrap', fontSize: 13, background: '#f5f5f5', padding: 16, borderRadius: 8 }}>
            {shouldShowErrorStack(import.meta.env.MODE)
              ? `${this.state.error.message}\n\n${this.state.error.stack ?? ''}`
              : '页面发生错误。请重新加载后再试。'}
          </pre>
          <button onClick={() => { this.setState({ error: null }); window.location.reload(); }}
            style={{ marginTop: 12, padding: '8px 16px', background: '#b8452e', color: '#fff', border: 'none', borderRadius: 6, cursor: 'pointer' }}>
            重新加载
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" replace />;
}

function PublicRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  return isAuthenticated ? <Navigate to="/" replace /> : <>{children}</>;
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ErrorBoundary>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<PublicRoute><LoginPage /></PublicRoute>} />
          <Route path="/*" element={<PrivateRoute><App /></PrivateRoute>} />
        </Routes>
      </BrowserRouter>
    </ErrorBoundary>
  </React.StrictMode>
);

