import React, { useState } from "react";
import { Link } from "react-router-dom";
import { LockIcon, ShieldIcon } from "../icons";
import "../App.css";

export default function ForgotPassword() {
  const [email, setEmail] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!email.trim()) {
      setError("Please enter your email.");
      return;
    }

    try {
      setLoading(true);
      setError("");
      setMessage("");

      const response = await fetch("http://localhost:8080/api/auth/forgot-password", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: email.trim() }),
      });

      const data = await response.json();

      if (!response.ok) {
        throw new Error(data.error || "Failed to send reset link.");
      }

      setMessage(data.message || "Password reset email sent.");
    } catch (err) {
      setError(err.message || "Unable to connect to the server.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-card-header">
          <Link to="/" className="auth-logo-simple">
            <LockIcon size={24} />
            <strong>SecureVault</strong>
          </Link>
          <h2>Forgot Password</h2>
          <p>Enter the email address associated with your account.</p>
        </div>

        {error && <div className="auth-error"><span>{error}</span></div>}
        {message && <div style={{color: '#047857', marginBottom: '1.5rem', padding: '0.75rem 1rem', background: '#ecfdf5', borderRadius: '8px', border: '1px solid #a7f3d0', fontSize: '0.875rem', textAlign: 'center'}}>{message}</div>}

        <form onSubmit={handleSubmit} className="auth-form">
          <div className="form-group-simple">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              placeholder="Enter your email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={loading}
            />
          </div>
          <button type="submit" className="auth-btn-primary" disabled={loading}>
            {loading ? "Sending..." : "Send Reset Link"}
          </button>
        </form>

        <div className="auth-card-footer">
          <span>Remembered your password?</span>
          <Link to="/login" className="register-link">Back to Login</Link>
        </div>
      </div>
    </div>
  );
}
