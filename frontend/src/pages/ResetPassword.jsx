import React, { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { LockIcon, ShieldIcon } from "../icons";
import "../App.css";

export default function ResetPassword() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token");
  const navigate = useNavigate();

  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!password || !confirmPassword) {
      setError("Please fill in both fields.");
      return;
    }

    if (password !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    try {
      setLoading(true);
      setError("");
      setMessage("");

      const response = await fetch("http://localhost:8080/api/auth/reset-password", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token, password }),
      });

      const data = await response.json();

      if (!response.ok) {
        throw new Error(data.error || "Failed to reset password.");
      }

      setMessage("Password successfully reset! You can now log in.");
      setTimeout(() => navigate("/login"), 3000);
    } catch (err) {
      setError(err.message || "Unable to connect to the server.");
    } finally {
      setLoading(false);
    }
  };

  if (!token) {
    return (
      <div className="auth-page">
        <div className="auth-card" style={{ textAlign: 'center' }}>
           <h2>Invalid Link</h2>
           <p style={{ color: 'var(--text-muted)' }}>This password reset link is invalid or has expired.</p>
           <Link to="/forgot-password" className="auth-btn-primary" style={{ marginTop: '1.5rem', display: 'inline-block', textDecoration: 'none' }}>Request a new link</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-card-header">
          <Link to="/" className="auth-logo-simple">
            <LockIcon size={24} />
            <strong>SecureVault</strong>
          </Link>
          <h2>New Password</h2>
          <p>Enter your new password below.</p>
        </div>

        {error && <div className="auth-error"><span>{error}</span></div>}
        {message && <div style={{color: '#047857', marginBottom: '1.5rem', padding: '0.75rem 1rem', background: '#ecfdf5', borderRadius: '8px', border: '1px solid #a7f3d0', fontSize: '0.875rem', textAlign: 'center'}}>{message}</div>}

        <form onSubmit={handleSubmit} className="auth-form">
          <div className="form-group-simple">
            <label htmlFor="password">New Password</label>
            <input
              id="password"
              type="password"
              placeholder="Enter new password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={loading}
            />
          </div>
          <div className="form-group-simple">
            <label htmlFor="confirmPassword">Confirm Password</label>
            <input
              id="confirmPassword"
              type="password"
              placeholder="Confirm new password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              disabled={loading}
            />
          </div>
          <button type="submit" className="auth-btn-primary" disabled={loading}>
            {loading ? "Resetting..." : "Reset Password"}
          </button>
        </form>
      </div>
    </div>
  );
}
