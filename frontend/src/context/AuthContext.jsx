import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';

const AuthContext = createContext(null);

const API = 'http://localhost:8080';

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [userRole, setUserRole] = useState('USER');
  const [userMfa, setUserMfa] = useState(false);

  // Fetch the current user's profile (role, MFA status) from backend
  const fetchProfile = useCallback(async (token) => {
    if (!token) return;
    try {
      const r = await fetch(`${API}/api/auth/profile`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (r.ok) {
        const data = await r.json();
        setUserRole(data.role || 'USER');
        setUserMfa(data.mfaEnabled || false);
        // Cache in localStorage
        localStorage.setItem('userRole', data.role || 'USER');
        localStorage.setItem('userMfa', String(data.mfaEnabled || false));
      }
    } catch {}
  }, []);

  useEffect(() => {
    const token = localStorage.getItem('token');
    const username = localStorage.getItem('username');
    const role = localStorage.getItem('userRole') || 'USER';
    const mfa = localStorage.getItem('userMfa') === 'true';
    if (token && username) {
      setUser({ token, username });
      setUserRole(role);
      setUserMfa(mfa);
      // Refresh profile from server
      fetchProfile(token);
    }
    setLoading(false);
  }, [fetchProfile]);

  const login = (token, username) => {
    localStorage.setItem('token', token);
    localStorage.setItem('username', username);
    setUser({ token, username });
    fetchProfile(token);
  };

  const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    localStorage.removeItem('userRole');
    localStorage.removeItem('userMfa');
    setUser(null);
    setUserRole('USER');
    setUserMfa(false);
  };

  const refreshProfile = () => {
    if (user?.token) fetchProfile(user.token);
  };

  return (
    <AuthContext.Provider value={{ user, login, logout, loading, userRole, userMfa, refreshProfile }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
