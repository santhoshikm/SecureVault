import React from "react";
import { BrowserRouter, Routes, Route, Link, Navigate } from "react-router-dom";
import Login from "./pages/login";
import Register from "./pages/Register";
import Dashboard from "./pages/Dashboard";
import SecurityDashboard from "./pages/SecurityDashboard";
import ForgotPassword from "./pages/ForgotPassword";
import ResetPassword from "./pages/ResetPassword";
import { AuthProvider, useAuth } from "./context/AuthContext";
import { LockIcon, ShieldIcon, CheckIcon, ChartIcon, GoogleIcon, GitHubIcon, CloudIcon } from "./icons";
import "./App.css";

function Home() {
  return (
    <div className="app">
      {/* NAVBAR */}
      <header className="navbar">
        <Link to="/" className="logo">
          <div className="logo-icon">
            <LockIcon />
          </div>
          <div>
            <h2>SecureVault</h2>
          </div>
        </Link>

        <nav>
          <a href="#features">Features</a>
          <a href="#security">Security</a>
          <a href="#about">About</a>
        </nav>

        <div className="nav-buttons">
          <Link to="/login" className="login-button">
            Login
          </Link>
          <Link to="/register" className="start-button">
            Get Started
          </Link>
        </div>
      </header>

      {/* HERO */}
      <section className="hero">
        <div className="hero-text">
          <div className="security-pill">
            <ShieldIcon size={15} /> Enterprise-grade credential protection
          </div>
          <h1>
            Your passwords.
            <br />
            <span>Your security.</span>
          </h1>
          <p>
            SecureVault is your centralized password and credential management platform. Store, organize and protect your sensitive information with modern security technology.
          </p>

          <div className="hero-buttons">
            <Link to="/register" className="primary-button">
              Create Your Vault →
            </Link>
            <a href="#security" className="outline-button">
              Explore Security
            </a>
          </div>

          <div className="security-stats">
            <div>
              <strong>AES-256</strong>
              <span>Encryption</span>
            </div>
            <div>
              <strong>MFA</strong>
              <span>Authentication</span>
            </div>
            <div>
              <strong>JWT</strong>
              <span>Secure Sessions</span>
            </div>
          </div>
        </div>

        {/* VAULT CARD */}
        <div className="vault-container">
          <div className="vault-glow"></div>
          <div className="vault-card">
            <div className="vault-top">
              <div className="window-dots">
                <span></span>
                <span></span>
                <span></span>
              </div>
              <small>● ENCRYPTED VAULT</small>
            </div>

            <div className="vault-title">
              <div>
                <small>MY VAULT</small>
                <h3>Credential Manager</h3>
              </div>
              <div className="vault-lock">
                <LockIcon />
              </div>
            </div>

            <Credential icon={<GoogleIcon />} name="Google" type="Personal Account" />
            <Credential icon={<GitHubIcon />} name="GitHub" type="Developer Account" />
            <Credential icon={<CloudIcon />} name="Cloud Account" type="Work Account" />

            <div className="vault-bottom">
              <span>
                <LockIcon size={13} /> Protected storage
              </span>
              <span className="secure-text">● Secure</span>
            </div>
          </div>
        </div>
      </section>

      {/* SECURITY SECTION */}
      <section id="security" className="security-section">
        <div className="section-heading">
          <span>SECURITY FIRST</span>
          <h2>Built around your security</h2>
          <p>SecureVault combines multiple security layers to protect your digital credentials.</p>
        </div>

        <div className="security-grid">
          <SecurityCard icon={<LockIcon />} title="AES-256 Encryption" text="Sensitive credentials are protected using strong encryption before storage." />
          <SecurityCard icon={<ShieldIcon />} title="Secure Authentication" text="Authentication is protected using Spring Security, BCrypt and JWT." />
          <SecurityCard icon={<CheckIcon />} title="Multi-Factor Authentication" text="An additional verification layer helps prevent unauthorized account access." />
          <SecurityCard icon={<ChartIcon />} title="Security Monitoring" text="Monitor account activity and security events through the dashboard." />
        </div>
      </section>

      {/* FEATURES */}
      <section id="features" className="features-section">
        <div className="section-heading">
          <span>POWERFUL FEATURES</span>
          <h2>Everything you need in one vault</h2>
        </div>

        <div className="feature-grid">
          <Feature number="01" title="Password Vault" text="Securely store and organize your credentials." />
          <Feature number="02" title="Password Generator" text="Create strong passwords for your online accounts." />
          <Feature number="03" title="Password Health" text="Identify weak and reused passwords." />
          <Feature number="04" title="Credential Management" text="Manage your digital credentials from one place." />
        </div>
      </section>

      {/* FOOTER */}
      <footer id="about">
        <div className="footer-logo">
          <div className="logo-icon">
            <LockIcon />
          </div>
          <div>
            <strong>SecureVault</strong>
            <span>Secure. Store. Protect.</span>
          </div>
        </div>
      </footer>
    </div>
  );
}

function Credential({ icon, name, type }) {
  return (
    <div className="credential">
      <div className="credential-icon">{icon}</div>
      <div className="credential-info">
        <strong>{name}</strong>
        <span>{type}</span>
      </div>
      <div className="protected">Protected</div>
    </div>
  );
}

function SecurityCard({ icon, title, text }) {
  return (
    <div className="security-card">
      <div className="security-icon">{icon}</div>
      <h3>{title}</h3>
      <p>{text}</p>
    </div>
  );
}

function Feature({ number, title, text }) {
  return (
    <div className="feature-card">
      <span>{number}</span>
      <h3>{title}</h3>
      <p>{text}</p>
    </div>
  );
}

function ProtectedRoute({ children }) {
  const { user, loading } = useAuth();

  if (loading) {
    return <div className="auth-container"><p>Loading...</p></div>;
  }

  return user ? children : <Navigate to="/login" replace />;
}

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/forgot-password" element={<ForgotPassword />} />
          <Route path="/reset-password" element={<ResetPassword />} />
          <Route path="/dashboard" element={
            <ProtectedRoute>
              <Dashboard />
            </ProtectedRoute>
          } />
          <Route path="/security" element={
            <ProtectedRoute>
              <SecurityDashboard />
            </ProtectedRoute>
          } />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
