import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { api, tokenStore } from '../api/client';
import type { CurrentUser, LoginResponse, UserRole } from '../api/types';

interface AuthValue {
  user: CurrentUser | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  canWrite: boolean;
  isAdmin: boolean;
}

const AuthContext = createContext<AuthValue | null>(null);

const WRITE_ROLES: UserRole[] = ['ADMIN', 'BILLER'];

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(() => tokenStore.getUser<CurrentUser>());

  const login = useCallback(async (username: string, password: string) => {
    const result = await api<LoginResponse>('/auth/login', {
      method: 'POST',
      body: { username, password },
    });
    tokenStore.set(result.token);
    const current: CurrentUser = {
      username: result.username,
      fullName: result.fullName,
      role: result.role,
    };
    tokenStore.setUser(current);
    setUser(current);
  }, []);

  const logout = useCallback(() => {
    tokenStore.clear();
    setUser(null);
  }, []);

  const value = useMemo<AuthValue>(() => ({
    user,
    login,
    logout,
    // Role gating is enforced server-side too. Hiding the button is a
    // courtesy, not the control.
    canWrite: user !== null && WRITE_ROLES.includes(user.role),
    isAdmin: user?.role === 'ADMIN',
  }), [user, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
