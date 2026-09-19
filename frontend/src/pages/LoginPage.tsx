import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ApiError } from '../api/client';

export default function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState('biller');
  const [password, setPassword] = useState('biller123');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to="/" replace />;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await login(username, password);
      navigate('/', { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : 'Could not reach the server.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="login-shell">
      <div className="login-card">
        <div className="login-head">
          <div className="brand-mark">CM</div>
          <h1>Claim Management</h1>
          <div className="muted" style={{ fontSize: 13 }}>Healthcare billing operations</div>
        </div>

        <form className="login-body" onSubmit={submit}>
          {error && <div className="alert alert-error">{error}</div>}

          <div className="field">
            <label htmlFor="username">Username</label>
            <input id="username" value={username} autoComplete="username"
                   onChange={(e) => setUsername(e.target.value)} required />
          </div>

          <div className="field">
            <label htmlFor="password">Password</label>
            <input id="password" type="password" value={password} autoComplete="current-password"
                   onChange={(e) => setPassword(e.target.value)} required />
          </div>

          <button className="btn" type="submit" disabled={busy} style={{ width: '100%' }}>
            {busy ? <><span className="spinner" /> Signing in</> : 'Sign in'}
          </button>
        </form>

        <div className="login-hint">
          <strong>Demo accounts</strong><br />
          <code>admin / admin123</code> &mdash; full access<br />
          <code>biller / biller123</code> &mdash; create and work claims<br />
          <code>viewer / viewer123</code> &mdash; read only
        </div>
      </div>
    </div>
  );
}
