import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import '../App.css';
import './SecurityDashboard.css';

const API = 'http://localhost:8080';
const authHeaders = (token) => ({
  Authorization: `Bearer ${token}`,
  'Content-Type': 'application/json',
});

/* ── helpers ─────────────────────────────────────────────────────────────── */
const fmtDateTime = (d) =>
  d
    ? new Date(d).toLocaleString('en-GB', {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    : '—';

const severityColor = {
  CRITICAL: '#ef4444',
  HIGH:     '#f97316',
  MEDIUM:   '#f59e0b',
  LOW:      '#10b981',
};
const severityBg = {
  CRITICAL: 'rgba(239,68,68,0.12)',
  HIGH:     'rgba(249,115,22,0.12)',
  MEDIUM:   'rgba(245,158,11,0.12)',
  LOW:      'rgba(16,185,129,0.12)',
};

const alertTypeLabel = {
  BRUTE_FORCE_ATTACK:       'Brute Force Attack',
  MULTIPLE_FAILED_LOGINS:   'Multiple Failed Logins',
  UNUSUAL_LOCATION_LOGIN:   'Unusual Location',
  ACCOUNT_LOCKOUT:          'Account Lockout',
  RAPID_CREDENTIAL_ACCESS:  'Rapid Access',
  OFF_HOURS_ACCESS:         'Off-Hours Access',
  CONCURRENT_SESSIONS:      'Concurrent Sessions',
  PASSWORD_SPRAY_DETECTED:  'Password Spray',
  UNRECOGNIZED_DEVICE_LOGIN:'Unrecognized Device',
};

const actionIcon = {
  LOGIN_SUCCESS:          '✅',
  LOGIN_FAILURE:          '❌',
  LOGOUT:                 '🔒',
  REGISTER:               '🆕',
  PASSWORD_RESET_REQUEST: '📧',
  PASSWORD_RESET_SUCCESS: '🔑',
  CREDENTIAL_CREATE:      '➕',
  CREDENTIAL_UPDATE:      '✏️',
  CREDENTIAL_DELETE:      '🗑️',
  CREDENTIAL_VIEW:        '👁️',
  CREDENTIAL_SHARE:       '🤝',
  CREDENTIAL_SHARE_REVOKE:'🚫',
  MFA_ENABLED:            '🛡️',
  MFA_DISABLED:           '⚠️',
  ACCOUNT_LOCKED:         '🔐',
  ACCOUNT_UNLOCKED:       '🔓',
  SUSPICIOUS_ACTIVITY_DETECTED: '🚨',
};

/* ── Mini Charts ─────────────────────────────────────────────────────────── */
function MiniBarChart({ data, maxVal }) {
  if (!data || data.length === 0) return <div className="sec-empty-chart">No data available</div>;
  const max = maxVal || Math.max(...data.map((d) => d.value), 1);
  return (
    <div className="sec-bar-chart">
      {data.map((d, i) => (
        <div key={i} className="sec-bar-row">
          <span className="sec-bar-label">{d.label}</span>
          <div className="sec-bar-track">
            <div
              className="sec-bar-fill"
              style={{
                width: `${Math.min(100, Math.round((d.value / max) * 100))}%`,
                background: d.color || 'var(--indigo)',
              }}
            />
          </div>
          <span className="sec-bar-val">{d.value}</span>
        </div>
      ))}
    </div>
  );
}

function DonutChart({ segments, label, value }) {
  const total = segments.reduce((s, x) => s + x.value, 0) || 1;
  let offset = 0;
  const r = 52, cx = 60, cy = 60, circ = 2 * Math.PI * r;
  return (
    <div className="sec-donut-wrap">
      <svg width="120" height="120" viewBox="0 0 120 120">
        <circle cx={cx} cy={cy} r={r} fill="none" stroke="rgba(255,255,255,0.08)" strokeWidth="14" />
        {segments.map((seg, i) => {
          const dash = (seg.value / total) * circ;
          const gap  = circ - dash;
          const el = (
            <circle
              key={i}
              cx={cx} cy={cy} r={r}
              fill="none"
              stroke={seg.color}
              strokeWidth="14"
              strokeDasharray={`${dash} ${gap}`}
              strokeDashoffset={-offset}
              transform={`rotate(-90 ${cx} ${cy})`}
            />
          );
          offset += dash;
          return el;
        })}
        <text x={cx} y={cy - 4} textAnchor="middle" fontSize="18" fontWeight="700" fill="#f8fafc">{value}</text>
        <text x={cx} y={cy + 12} textAnchor="middle" fontSize="9" fill="#94a3b8">{label}</text>
      </svg>
      <div className="sec-donut-legend">
        {segments.map((s, i) => (
          <div key={i} className="sec-legend-item">
            <span className="sec-legend-dot" style={{ background: s.color }} />
            <span>{s.label}</span>
            <span className="sec-legend-val">{s.value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function HourlyChart({ data }) {
  if (!data || data.length === 0) return <div className="sec-empty-chart">No login data</div>;
  const max = Math.max(...data.map((d) => Number(d.count)), 1);
  const hours = Array.from({ length: 24 }, (_, i) => {
    const found = data.find((d) => Number(d.hour) === i);
    return { hour: i, count: found ? Number(found.count) : 0 };
  });
  return (
    <div className="sec-hourly-chart">
      {hours.map((h) => (
        <div key={h.hour} className="sec-hour-col">
          <div
            className="sec-hour-bar"
            style={{ height: `${Math.round((h.count / max) * 80)}%` }}
            title={`${h.hour}:00 — ${h.count} logins`}
          />
          {h.hour % 6 === 0 && (
            <span className="sec-hour-label">{h.hour}h</span>
          )}
        </div>
      ))}
    </div>
  );
}

function HealthRing({ score }) {
  const r = 46, cx = 56, cy = 56, circ = 2 * Math.PI * r;
  const dash = (score / 100) * circ;
  const color =
    score >= 80 ? '#10b981' : score >= 60 ? '#f59e0b' : '#ef4444';
  return (
    <svg width="112" height="112" viewBox="0 0 112 112">
      <circle cx={cx} cy={cy} r={r} fill="none" stroke="rgba(255,255,255,0.08)" strokeWidth="10" />
      <circle
        cx={cx} cy={cy} r={r} fill="none"
        stroke={color} strokeWidth="10"
        strokeDasharray={`${dash} ${circ - dash}`}
        strokeDashoffset={-circ * 0.25}
        strokeLinecap="round"
        style={{ transition: 'stroke-dasharray 1s ease' }}
      />
      <text x={cx} y={cy - 4} textAnchor="middle" fontSize="22" fontWeight="800" fill={color}>{score}</text>
      <text x={cx} y={cy + 13} textAnchor="middle" fontSize="9" fill="#94a3b8">SCORE</text>
    </svg>
  );
}

/* ══════════════════════════════════════════════════════════════════════════
   MAIN COMPONENT
══════════════════════════════════════════════════════════════════════════ */
export default function SecurityDashboard() {
  const { user, token, logout } = useAuth();
  const navigate = useNavigate();

  const [tab, setTab]                 = useState('overview');
  const [loading, setLoading]         = useState(false);
  const [dashData, setDashData]       = useState(null);
  const [auditLogs, setAuditLogs]     = useState([]);
  const [auditCategory, setAuditCategory] = useState('all');
  const [auditSearch, setAuditSearch] = useState('');
  const [auditReport, setAuditReport] = useState(null);
  const [alerts, setAlerts]           = useState([]);
  const [alertFilter, setAlertFilter] = useState('active');
  const [threats, setThreats]         = useState(null);
  const [devices, setDevices]         = useState([]);
  const [anomalies, setAnomalies]     = useState([]);
  const [riskData, setRiskData]       = useState(null);
  const [loginAttempts, setLoginAttempts] = useState([]);
  const [pwdReport, setPwdReport]     = useState(null);
  const [loginReport, setLoginReport] = useState(null);
  const [notification, setNotification] = useState(null);
  const [resolving, setResolving]     = useState(null);
  const refreshTimer = useRef(null);

  const notify = (msg, type = 'success') => {
    setNotification({ msg, type });
    setTimeout(() => setNotification(null), 3500);
  };

  /* ── Data Fetchers ──────────────────────────────────────────────────────── */
  const fetchDashboard = useCallback(async () => {
    try {
      const r = await fetch(`${API}/api/security/dashboard`, { headers: authHeaders(token) });
      if (r.ok) setDashData(await r.json());
    } catch {}
  }, [token]);

  const fetchAuditLogs = useCallback(async () => {
    try {
      const query = auditCategory !== 'all' ? `?category=${auditCategory}` : '';
      const r = await fetch(`${API}/api/security/audit-logs/me${query}`, { headers: authHeaders(token) });
      if (r.ok) setAuditLogs(await r.json());
    } catch {}
  }, [token, auditCategory]);

  const fetchAuditReport = useCallback(async () => {
    try {
      const r = await fetch(`${API}/api/security/audit-logs/report`, { headers: authHeaders(token) });
      if (r.ok) setAuditReport(await r.json());
    } catch {}
  }, [token]);

  const fetchAlerts = useCallback(async () => {
    try {
      const endpoint = alertFilter === 'all' ? `${API}/api/security/alerts/all` : `${API}/api/security/alerts`;
      const r = await fetch(endpoint, { headers: authHeaders(token) });
      if (r.ok) setAlerts(await r.json());
    } catch {}
  }, [token, alertFilter]);

  const fetchThreats = useCallback(async () => {
    try {
      const r = await fetch(`${API}/api/security/threats`, { headers: authHeaders(token) });
      if (r.ok) setThreats(await r.json());
    } catch {}
  }, [token]);

  const fetchDevices = useCallback(async () => {
    try {
      const r = await fetch(`${API}/api/security/devices`, { headers: authHeaders(token) });
      if (r.ok) setDevices(await r.json());
    } catch {}
  }, [token]);

  const fetchAnomalies = useCallback(async () => {
    try {
      const r = await fetch(`${API}/api/security/anomalies`, { headers: authHeaders(token) });
      if (r.ok) setAnomalies(await r.json());
    } catch {}
  }, [token]);

  const fetchRiskAnalysis = useCallback(async () => {
    try {
      const r = await fetch(`${API}/api/security/risk-analysis`, { headers: authHeaders(token) });
      if (r.ok) setRiskData(await r.json());
    } catch {}
  }, [token]);

  const fetchLoginAttempts = useCallback(async () => {
    try {
      const r = await fetch(`${API}/api/security/login-attempts?hours=48`, { headers: authHeaders(token) });
      if (r.ok) setLoginAttempts(await r.json());
    } catch {}
  }, [token]);

  const fetchPasswordReport = useCallback(async () => {
    try {
      const r = await fetch(`${API}/api/security/reports/password-health`, { headers: authHeaders(token) });
      if (r.ok) setPwdReport(await r.json());
    } catch {}
  }, [token]);

  const fetchLoginReport = useCallback(async () => {
    try {
      const r = await fetch(`${API}/api/security/reports/login-activity`, { headers: authHeaders(token) });
      if (r.ok) setLoginReport(await r.json());
    } catch {}
  }, [token]);

  const loadAll = useCallback(async () => {
    setLoading(true);
    await Promise.all([
      fetchDashboard(),
      fetchAuditLogs(),
      fetchAuditReport(),
      fetchAlerts(),
      fetchThreats(),
      fetchDevices(),
      fetchAnomalies(),
      fetchRiskAnalysis(),
      fetchLoginAttempts(),
      fetchPasswordReport(),
      fetchLoginReport(),
    ]);
    setLoading(false);
  }, [fetchDashboard, fetchAuditLogs, fetchAuditReport, fetchAlerts, fetchThreats, fetchDevices, fetchAnomalies, fetchRiskAnalysis, fetchLoginAttempts, fetchPasswordReport, fetchLoginReport]);

  useEffect(() => {
    loadAll();
    refreshTimer.current = setInterval(loadAll, 30000);
    return () => clearInterval(refreshTimer.current);
  }, [loadAll]);

  useEffect(() => {
    fetchAlerts();
  }, [alertFilter, fetchAlerts]);

  useEffect(() => {
    fetchAuditLogs();
  }, [auditCategory, fetchAuditLogs]);

  /* ── CSV Export Handler ─────────────────────────────────────────────────── */
  const handleExportCsv = async () => {
    try {
      const query = auditCategory !== 'all' ? `?category=${auditCategory}` : '';
      const r = await fetch(`${API}/api/security/audit-logs/export${query}`, { headers: authHeaders(token) });
      if (r.ok) {
        const blob = await r.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `audit-log-report${auditCategory !== 'all' ? '-' + auditCategory : ''}.csv`;
        document.body.appendChild(a);
        a.click();
        a.remove();
        notify('Audit logs exported to CSV successfully');
      } else {
        notify('Failed to export CSV report', 'error');
      }
    } catch {
      notify('Error exporting CSV', 'error');
    }
  };

  /* ── Resolve Alert ──────────────────────────────────────────────────────── */
  const resolveAlert = async (id) => {
    setResolving(id);
    try {
      const r = await fetch(`${API}/api/security/alerts/${id}/resolve`, {
        method: 'PATCH',
        headers: authHeaders(token),
      });
      if (r.ok) {
        notify('Alert resolved successfully');
        fetchAlerts();
        fetchDashboard();
        fetchThreats();
      } else {
        notify('Failed to resolve alert', 'error');
      }
    } catch {
      notify('Network error resolving alert', 'error');
    } finally {
      setResolving(null);
    }
  };

  /* ── Filtered Audit Logs ───────────────────────────────────────────────── */
  const filteredAudit = auditLogs.filter(
    (l) =>
      !auditSearch ||
      l.action?.toLowerCase().includes(auditSearch.toLowerCase()) ||
      l.details?.toLowerCase().includes(auditSearch.toLowerCase()) ||
      l.ipAddress?.includes(auditSearch) ||
      l.username?.toLowerCase().includes(auditSearch.toLowerCase())
  );

  return (
    <div className="sec-root">
      {/* ── TOAST NOTIFICATION ───────────────────────────────────────────── */}
      {notification && (
        <div className={`sec-toast sec-toast-${notification.type}`}>
          {notification.type === 'success' ? '✅' : '❌'} {notification.msg}
        </div>
      )}

      {/* ── NAVBAR ───────────────────────────────────────────────────────── */}
      <header className="sec-navbar">
        <div className="sec-nav-brand">
          <div className="sec-shield-icon">🛡️</div>
          <div>
            <h1 className="sec-nav-title">Security Center</h1>
            <p className="sec-nav-sub">Monitoring & Security Intelligence</p>
          </div>
        </div>

        <nav className="sec-nav-tabs">
          {[
            { id: 'overview',  label: '📊 Overview' },
            { id: 'alerts',    label: '🚨 Alerts & Threats' },
            { id: 'devices',   label: '💻 Devices & Anomalies' },
            { id: 'risk',      label: '⚖️ Risk Analysis' },
            { id: 'audit',     label: '📋 Audit Logging' },
            { id: 'logins',    label: '🔑 Login Monitor' },
            { id: 'reports',   label: '📈 Reports' },
          ].map((t) => (
            <button
              key={t.id}
              className={`sec-nav-tab ${tab === t.id ? 'active' : ''}`}
              onClick={() => setTab(t.id)}
            >
              {t.label}
              {t.id === 'alerts' && dashData?.activeAlerts > 0 && (
                <span className="sec-badge-count">{dashData.activeAlerts}</span>
              )}
            </button>
          ))}
        </nav>

        <div className="sec-nav-actions">
          <button className="sec-btn-icon" onClick={loadAll} title="Refresh Data">
            {loading ? '⏳' : '🔄'}
          </button>
          <Link to="/dashboard" className="sec-btn-outline">
            ← Vault
          </Link>
          <button className="sec-btn-danger" onClick={() => { logout(); navigate('/login'); }}>
            Logout
          </button>
        </div>
      </header>

      <main className="sec-main">
        {/* ══════════════════════════════════════════════════════════════
            TAB 1: OVERVIEW
        ══════════════════════════════════════════════════════════════ */}
        {tab === 'overview' && (
          <div className="sec-overview">
            <div className="sec-kpi-grid">
              <KpiCard
                emoji="✅"
                label="Successful Logins (24h)"
                value={dashData?.successfulLogins24h ?? '—'}
                sub={`${dashData?.loginSuccessRate ?? 0}% success rate`}
                color="#10b981"
              />
              <KpiCard
                emoji="❌"
                label="Failed Logins (24h)"
                value={dashData?.failedLogins24h ?? '—'}
                sub="Authentication failures"
                color="#ef4444"
              />
              <KpiCard
                emoji="🚨"
                label="Active Security Alerts"
                value={dashData?.activeAlerts ?? '—'}
                sub={`${dashData?.criticalAlerts ?? 0} critical priority`}
                color="#f97316"
                pulse={dashData?.criticalAlerts > 0}
              />
              <KpiCard
                emoji="📋"
                label="Audit Events (24h)"
                value={dashData?.totalAuditEvents24h ?? '—'}
                sub={`${dashData?.suspiciousEvents24h ?? 0} flagged suspicious`}
                color="#6366f1"
              />
            </div>

            <div className="sec-charts-row">
              <div className="sec-chart-card">
                <h3 className="sec-chart-title">Unresolved Alerts by Severity</h3>
                {dashData?.unresolvedBySeverity ? (
                  <DonutChart
                    label="Active"
                    value={dashData.activeAlerts ?? 0}
                    segments={Object.entries(dashData.unresolvedBySeverity).map(([k, v]) => ({
                      label: k,
                      value: Number(v),
                      color: severityColor[k] || '#888',
                    }))}
                  />
                ) : (
                  <div className="sec-empty-chart">Loading statistics…</div>
                )}
              </div>

              <div className="sec-chart-card">
                <h3 className="sec-chart-title">Alert Types Breakdown (7 Days)</h3>
                <MiniBarChart
                  data={dashData?.alertsByType7d
                    ? Object.entries(dashData.alertsByType7d).map(([k, v]) => ({
                        label: alertTypeLabel[k] || k,
                        value: Number(v),
                        color: '#6366f1',
                      }))
                    : []}
                />
              </div>

              <div className="sec-chart-card sec-chart-wide">
                <h3 className="sec-chart-title">Hourly Login Frequency — Last 24 Hours</h3>
                <HourlyChart data={dashData?.loginsByHour} />
              </div>
            </div>

            <div className="sec-overview-bottom">
              <div className="sec-table-card">
                <h3 className="sec-chart-title">🎯 Most Targeted Accounts (24h)</h3>
                {dashData?.topAttackedUsers?.length > 0 ? (
                  <table className="sec-table">
                    <thead>
                      <tr><th>Username</th><th>Failed Logins</th><th>Threat Status</th></tr>
                    </thead>
                    <tbody>
                      {dashData.topAttackedUsers.slice(0, 5).map((u, i) => (
                        <tr key={i}>
                          <td><strong>{u.username}</strong></td>
                          <td><span className="sec-count-badge sec-count-red">{u.failedAttempts}</span></td>
                          <td>
                            <span
                              className="sec-severity-pill"
                              style={{
                                background: u.failedAttempts >= 10 ? severityBg.CRITICAL : u.failedAttempts >= 5 ? severityBg.HIGH : severityBg.LOW,
                                color: u.failedAttempts >= 10 ? severityColor.CRITICAL : u.failedAttempts >= 5 ? severityColor.HIGH : severityColor.LOW,
                              }}
                            >
                              {u.failedAttempts >= 10 ? 'CRITICAL RISK' : u.failedAttempts >= 5 ? 'HIGH RISK' : 'LOW RISK'}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                ) : (
                  <div className="sec-empty">No targeted login attacks detected ✨</div>
                )}
              </div>

              <div className="sec-table-card">
                <h3 className="sec-chart-title">🌐 Top Suspicious Attack IPs (24h)</h3>
                {dashData?.topAttackingIps?.length > 0 ? (
                  <table className="sec-table">
                    <thead>
                      <tr><th>IP Address</th><th>Failures</th><th>Filter Status</th></tr>
                    </thead>
                    <tbody>
                      {dashData.topAttackingIps.slice(0, 5).map((ip, i) => (
                        <tr key={i}>
                          <td><code className="sec-ip">{ip.ip || 'Unknown'}</code></td>
                          <td><span className="sec-count-badge sec-count-red">{ip.failedAttempts}</span></td>
                          <td>
                            <span className="sec-severity-pill" style={{ background: severityBg.HIGH, color: severityColor.HIGH }}>
                              FLAGGED
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                ) : (
                  <div className="sec-empty">No malicious IP activity recorded ✨</div>
                )}
              </div>
            </div>
          </div>
        )}

        {/* ══════════════════════════════════════════════════════════════
            TAB 2: ALERTS & THREAT MONITORING
        ══════════════════════════════════════════════════════════════ */}
        {tab === 'alerts' && (
          <div className="sec-alerts-page">
            <div className="sec-threat-banner">
              <div className="sec-threat-status-card">
                <div className="sec-threat-level-badge" style={{
                  background: threats?.threatLevel === 'CRITICAL' ? severityBg.CRITICAL : threats?.threatLevel === 'HIGH' ? severityBg.HIGH : severityBg.LOW,
                  color: threats?.threatLevel === 'CRITICAL' ? severityColor.CRITICAL : threats?.threatLevel === 'HIGH' ? severityColor.HIGH : severityColor.LOW,
                }}>
                  THREAT LEVEL: {threats?.threatLevel || 'NORMAL'}
                </div>
                <h2>Threat Monitoring Index: {threats?.threatScore ?? 15} / 100</h2>
                <p>Real-time indicators of compromise and automated threat intelligence</p>
              </div>

              <div className="sec-ioc-card">
                <h4>🛡️ Active Indicators of Compromise (IOCs)</h4>
                <div className="sec-ioc-grid">
                  <div>
                    <span>Suspicious Source IPs:</span>
                    <strong>{threats?.suspiciousIps?.length ?? 0}</strong>
                  </div>
                  <div>
                    <span>Targeted User Accounts:</span>
                    <strong>{threats?.targetedUsers?.length ?? 0}</strong>
                  </div>
                  <div>
                    <span>Active Critical Alerts:</span>
                    <strong style={{ color: severityColor.CRITICAL }}>{threats?.criticalAlertsCount ?? 0}</strong>
                  </div>
                </div>
              </div>
            </div>

            <div className="sec-page-header">
              <div>
                <h2 className="sec-page-title">Security Alerts Queue</h2>
                <p className="sec-page-sub">Review and resolve threat notifications generated by rules</p>
              </div>
              <div className="sec-toggle-group">
                <button
                  className={`sec-toggle-btn ${alertFilter === 'active' ? 'active' : ''}`}
                  onClick={() => setAlertFilter('active')}
                >
                  Active ({dashData?.activeAlerts ?? 0})
                </button>
                <button
                  className={`sec-toggle-btn ${alertFilter === 'all' ? 'active' : ''}`}
                  onClick={() => setAlertFilter('all')}
                >
                  All Alerts
                </button>
              </div>
            </div>

            {alerts.length === 0 ? (
              <div className="sec-empty-state">
                <div className="sec-empty-icon">🛡️</div>
                <h3>No security alerts found</h3>
                <p>Your vault security status is optimal — no active threats.</p>
              </div>
            ) : (
              <div className="sec-alerts-list">
                {alerts.map((alert) => (
                  <div
                    key={alert.id}
                    className={`sec-alert-card ${alert.resolved ? 'resolved' : ''}`}
                    style={{ borderLeftColor: severityColor[alert.severity] || '#888' }}
                  >
                    <div className="sec-alert-top">
                      <div className="sec-alert-info">
                        <span
                          className="sec-severity-pill"
                          style={{
                            background: severityBg[alert.severity],
                            color: severityColor[alert.severity],
                          }}
                        >
                          {alert.severity}
                        </span>
                        <span className="sec-alert-type">
                          {alertTypeLabel[alert.alertType] || alert.alertType}
                        </span>
                        {alert.resolved && (
                          <span className="sec-resolved-tag">✅ Resolved</span>
                        )}
                      </div>
                      <span className="sec-alert-time">{fmtDateTime(alert.createdAt)}</span>
                    </div>
                    <p className="sec-alert-msg">{alert.message}</p>
                    <div className="sec-alert-footer">
                      <div className="sec-alert-meta">
                        <span>👤 User: {alert.username}</span>
                        {alert.ipAddress && <span>🌐 IP: {alert.ipAddress}</span>}
                        {alert.resolvedBy && <span>✔ Resolved by {alert.resolvedBy}</span>}
                      </div>
                      {!alert.resolved && (
                        <button
                          className="sec-btn-resolve"
                          onClick={() => resolveAlert(alert.id)}
                          disabled={resolving === alert.id}
                        >
                          {resolving === alert.id ? 'Resolving…' : 'Mark Resolved'}
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* ══════════════════════════════════════════════════════════════
            TAB 3: DEVICE TRACKING & ANOMALIES
        ══════════════════════════════════════════════════════════════ */}
        {tab === 'devices' && (
          <div className="sec-devices-page">
            <div className="sec-page-header">
              <div>
                <h2 className="sec-page-title">Device Tracking & Login Anomalies</h2>
                <p className="sec-page-sub">Recognized browser profiles, client hardware, and anomaly detections</p>
              </div>
            </div>

            <div className="sec-reports-grid">
              {/* Device Profiles */}
              <div className="sec-report-card">
                <div className="sec-report-header">
                  <h3>💻 Tracked User Devices</h3>
                  <button className="sec-btn-refresh" onClick={fetchDevices}>🔄 Refresh</button>
                </div>
                {devices.length === 0 ? (
                  <div className="sec-empty">No device history tracked yet</div>
                ) : (
                  <div className="sec-audit-table-wrap">
                    <table className="sec-audit-table">
                      <thead>
                        <tr>
                          <th>Device / OS</th>
                          <th>Browser</th>
                          <th>Category</th>
                          <th>Last Known IP</th>
                          <th>Last Seen</th>
                          <th>Status</th>
                        </tr>
                      </thead>
                      <tbody>
                        {devices.map((d, i) => (
                          <tr key={i}>
                            <td><strong>{d.os}</strong></td>
                            <td>{d.browser}</td>
                            <td>
                              <span className="sec-chip sec-chip-blue">{d.deviceType}</span>
                            </td>
                            <td><code className="sec-ip">{d.lastIpAddress || '—'}</code></td>
                            <td className="sec-td-time">{fmtDateTime(d.lastSeen)}</td>
                            <td>
                              {d.trusted ? (
                                <span className="sec-severity-pill" style={{ background: 'rgba(16,185,129,0.1)', color: '#10b981' }}>
                                  ✓ Recognized
                                </span>
                              ) : (
                                <span className="sec-severity-pill" style={{ background: severityBg.HIGH, color: severityColor.HIGH }}>
                                  ⚠ New Device
                                </span>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>

              {/* Login Anomalies */}
              <div className="sec-report-card">
                <div className="sec-report-header">
                  <h3>🚨 Real-Time Login Anomalies</h3>
                  <button className="sec-btn-refresh" onClick={fetchAnomalies}>🔄 Refresh</button>
                </div>
                {anomalies.length === 0 ? (
                  <div className="sec-empty">No login anomalies detected in recent window ✨</div>
                ) : (
                  <div className="sec-anomalies-list">
                    {anomalies.map((a, i) => (
                      <div key={i} className="sec-anomaly-card">
                        <div className="sec-anomaly-top">
                          <span className="sec-severity-pill" style={{ background: severityBg[a.riskWeight] || severityBg.MEDIUM, color: severityColor[a.riskWeight] || severityColor.MEDIUM }}>
                            {a.type}
                          </span>
                          <span className="sec-alert-time">{fmtDateTime(a.timestamp)}</span>
                        </div>
                        <p className="sec-anomaly-desc">{a.details}</p>
                        <div className="sec-alert-meta">
                          <span>👤 Account: {a.username}</span>
                          <span>🌐 IP: {a.ipAddress}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {/* ══════════════════════════════════════════════════════════════
            TAB 4: RISK ANALYSIS ENGINE
        ══════════════════════════════════════════════════════════════ */}
        {tab === 'risk' && (
          <div className="sec-risk-page">
            <div className="sec-page-header">
              <div>
                <h2 className="sec-page-title">Security Risk Analysis Engine</h2>
                <p className="sec-page-sub">Algorithmic risk evaluation across credential health and access patterns</p>
              </div>
            </div>

            {riskData ? (
              <div className="sec-risk-grid">
                <div className="sec-report-card sec-risk-score-card">
                  <h3>Vault Security Rating</h3>
                  <div className="sec-grade-box">
                    <span className="sec-grade-badge">{riskData.securityGrade}</span>
                    <div className="sec-grade-meta">
                      <h4>Risk Rating: <span style={{ color: severityColor[riskData.systemRiskRating] || '#10b981' }}>{riskData.systemRiskRating}</span></h4>
                      <p>System Risk Index: <strong>{riskData.systemRiskScore} / 100</strong> (Lower is safer)</p>
                    </div>
                  </div>

                  <h4 style={{ marginTop: '1.5rem', marginBottom: '0.6rem' }}>Penalty Factor Breakdown</h4>
                  <div className="sec-pwd-bars">
                    <MiniBarChart
                      data={[
                        { label: 'Password Weakness Penalty', value: Math.round((100 - riskData.passwordHealthScore) * 0.35), color: '#ef4444' },
                        { label: 'Unresolved Alerts Penalty', value: riskData.activeAlertsPenalty, color: '#f97316' },
                        { label: 'Failed Login Penalty', value: riskData.failedLoginPenalty, color: '#f59e0b' },
                        { label: 'Suspicious Activity Penalty', value: riskData.suspiciousActivityPenalty, color: '#6366f1' },
                      ]}
                      maxVal={40}
                    />
                  </div>
                </div>

                <div className="sec-report-card">
                  <h3>🔍 Identified Risk Factors & Remediation</h3>
                  <div className="sec-risk-factors-list">
                    {riskData.riskFactors?.map((rf, i) => (
                      <div key={i} className="sec-risk-factor-item">
                        <div className="sec-risk-factor-top">
                          <strong>{rf.factor}</strong>
                          <span
                            className="sec-severity-pill"
                            style={{
                              background: severityBg[rf.impact] || severityBg.LOW,
                              color: severityColor[rf.impact] || severityColor.LOW,
                            }}
                          >
                            {rf.impact} IMPACT
                          </span>
                        </div>
                        <p className="sec-risk-advice">💡 Action: {rf.advice}</p>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            ) : (
              <div className="sec-empty">Calculating security risk profile…</div>
            )}
          </div>
        )}

        {/* ══════════════════════════════════════════════════════════════
            TAB 5: AUDIT LOGGING MODULE
        ══════════════════════════════════════════════════════════════ */}
        {tab === 'audit' && (
          <div className="sec-audit-page">
            <div className="sec-page-header">
              <div>
                <h2 className="sec-page-title">Comprehensive Audit Trail</h2>
                <p className="sec-page-sub">Complete system-wide audit records categorized for compliance</p>
              </div>
              <div className="sec-nav-actions">
                <button className="sec-btn-outline" onClick={handleExportCsv}>
                  📥 Export CSV Report
                </button>
              </div>
            </div>

            {/* Sub-Category Tabs for Audit Logging */}
            <div className="sec-category-tabs">
              {[
                { id: 'all',      label: '🌐 All Events' },
                { id: 'login',    label: '🔑 Login Logs' },
                { id: 'vault',    label: '🔐 Vault Access Logs' },
                { id: 'sharing',  label: '🤝 Sharing Activity Logs' },
                { id: 'security', label: '🛡️ Security Logs' },
                { id: 'system',   label: '⚙️ System Activity' },
              ].map((c) => (
                <button
                  key={c.id}
                  className={`sec-cat-tab ${auditCategory === c.id ? 'active' : ''}`}
                  onClick={() => setAuditCategory(c.id)}
                >
                  {c.label}
                </button>
              ))}
            </div>

            <div className="sec-audit-controls">
              <div className="sec-search-box">
                <span>🔍</span>
                <input
                  type="text"
                  placeholder="Search audit records by action, user, details, or IP…"
                  value={auditSearch}
                  onChange={(e) => setAuditSearch(e.target.value)}
                  className="sec-search-input"
                />
              </div>
            </div>

            {/* Executive Audit Summary Card */}
            {auditReport && (
              <div className="sec-audit-summary-box">
                <div className="sec-summary-stat">
                  <span>Total Audit Events (30d)</span>
                  <strong>{auditReport.totalEvents}</strong>
                </div>
                <div className="sec-summary-stat">
                  <span>Suspicious Flagged</span>
                  <strong style={{ color: severityColor.HIGH }}>{auditReport.suspiciousEventsCount}</strong>
                </div>
                <div className="sec-summary-categories">
                  {Object.entries(auditReport.eventsByCategory || {}).map(([cat, count]) => (
                    <span key={cat} className="sec-chip sec-chip-blue">
                      {cat.toUpperCase()}: {count}
                    </span>
                  ))}
                </div>
              </div>
            )}

            {filteredAudit.length === 0 ? (
              <div className="sec-empty-state">
                <div className="sec-empty-icon">📋</div>
                <h3>No audit events recorded</h3>
                <p>Events will appear automatically as actions are taken in SecureVault.</p>
              </div>
            ) : (
              <div className="sec-audit-table-wrap">
                <table className="sec-audit-table">
                  <thead>
                    <tr>
                      <th>Time</th>
                      <th>User</th>
                      <th>Action</th>
                      <th>Details</th>
                      <th>IP Address</th>
                      <th>Security Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredAudit.map((log) => (
                      <tr key={log.id} className={log.suspicious ? 'suspicious' : ''}>
                        <td className="sec-td-time">{fmtDateTime(log.timestamp)}</td>
                        <td><strong>{log.username || 'system'}</strong></td>
                        <td>
                          <div className="sec-action-cell">
                            <span className="sec-action-icon">{actionIcon[log.action] || '🔹'}</span>
                            <span className="sec-action-name">{log.action?.replace(/_/g, ' ')}</span>
                          </div>
                        </td>
                        <td className="sec-td-details">{log.details || '—'}</td>
                        <td><code className="sec-ip">{log.ipAddress || '—'}</code></td>
                        <td>
                          {log.suspicious ? (
                            <span className="sec-severity-pill" style={{ background: severityBg.HIGH, color: severityColor.HIGH }}>
                              ⚠ Suspicious
                            </span>
                          ) : (
                            <span className="sec-severity-pill" style={{ background: 'rgba(16,185,129,0.1)', color: '#10b981' }}>
                              ✓ Verified
                            </span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {/* ══════════════════════════════════════════════════════════════
            TAB 6: LOGIN MONITORING
        ══════════════════════════════════════════════════════════════ */}
        {tab === 'logins' && (
          <div className="sec-logins-page">
            <div className="sec-page-header">
              <div>
                <h2 className="sec-page-title">Login Activity Monitor</h2>
                <p className="sec-page-sub">Full authentication attempt log across accounts (48-hour history)</p>
              </div>
              <div className="sec-stat-chips">
                <div className="sec-chip sec-chip-green">
                  ✅ {loginAttempts.filter((l) => l.success).length} Successful
                </div>
                <div className="sec-chip sec-chip-red">
                  ❌ {loginAttempts.filter((l) => !l.success).length} Failed
                </div>
              </div>
            </div>

            {loginAttempts.length === 0 ? (
              <div className="sec-empty-state">
                <div className="sec-empty-icon">🔑</div>
                <h3>No login activity recorded</h3>
                <p>Login history will populate upon authentication attempts.</p>
              </div>
            ) : (
              <div className="sec-audit-table-wrap">
                <table className="sec-audit-table">
                  <thead>
                    <tr>
                      <th>Time</th>
                      <th>Account</th>
                      <th>Status</th>
                      <th>IP Address</th>
                      <th>Device / Location Info</th>
                      <th>Failure Reason</th>
                    </tr>
                  </thead>
                  <tbody>
                    {loginAttempts.map((la) => (
                      <tr key={la.id} className={!la.success ? 'failed-row' : ''}>
                        <td className="sec-td-time">{fmtDateTime(la.attemptTime)}</td>
                        <td><strong>{la.username}</strong></td>
                        <td>
                          {la.success ? (
                            <span className="sec-severity-pill" style={{ background: 'rgba(16,185,129,0.1)', color: '#10b981' }}>
                              ✅ Success
                            </span>
                          ) : (
                            <span className="sec-severity-pill" style={{ background: severityBg.HIGH, color: severityColor.HIGH }}>
                              ❌ Failed
                            </span>
                          )}
                        </td>
                        <td><code className="sec-ip">{la.ipAddress || '—'}</code></td>
                        <td>{la.geoLocation || 'Web Client'}</td>
                        <td className="sec-td-reason">{la.failureReason || '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {/* ══════════════════════════════════════════════════════════════
            TAB 7: REPORTS
        ══════════════════════════════════════════════════════════════ */}
        {tab === 'reports' && (
          <div className="sec-reports-page">
            <div className="sec-page-header">
              <div>
                <h2 className="sec-page-title">Security & Audit Analytics Reports</h2>
                <p className="sec-page-sub">Credential health diagnostics and account login history summaries</p>
              </div>
            </div>

            <div className="sec-reports-grid">
              {/* Password Health Report */}
              <div className="sec-report-card">
                <div className="sec-report-header">
                  <div>
                    <h3>🔐 Password Health Diagnostics</h3>
                    <p>Analysis of your stored vault credentials</p>
                  </div>
                  <button className="sec-btn-refresh" onClick={fetchPasswordReport}>🔄 Refresh</button>
                </div>

                {pwdReport ? (
                  <div className="sec-report-body">
                    <div className="sec-health-top">
                      <HealthRing score={pwdReport.healthScore ?? 0} />
                      <div className="sec-health-stats">
                        <div className="sec-health-stat">
                          <span className="sec-stat-dot" style={{ background: '#10b981' }} />
                          <span>Strong Passwords</span>
                          <strong>{pwdReport.strongPasswords}</strong>
                        </div>
                        <div className="sec-health-stat">
                          <span className="sec-stat-dot" style={{ background: '#ef4444' }} />
                          <span>Weak Passwords</span>
                          <strong>{pwdReport.weakPasswords}</strong>
                        </div>
                        <div className="sec-health-stat">
                          <span className="sec-stat-dot" style={{ background: '#f97316' }} />
                          <span>Reused Passwords</span>
                          <strong>{pwdReport.reusedPasswords}</strong>
                        </div>
                        <div className="sec-health-stat">
                          <span className="sec-stat-dot" style={{ background: '#f59e0b' }} />
                          <span>Old Passwords (&gt;3mo)</span>
                          <strong>{pwdReport.oldPasswords}</strong>
                        </div>
                        <div className="sec-health-stat sec-health-total">
                          <span>Total Credentials</span>
                          <strong>{pwdReport.totalPasswords}</strong>
                        </div>
                      </div>
                    </div>

                    <div className="sec-recommendations">
                      <h4>💡 Recommendations</h4>
                      <ul>
                        {pwdReport.recommendations?.map((r, i) => (
                          <li key={i}>{r}</li>
                        ))}
                      </ul>
                    </div>

                    <div className="sec-pwd-bars">
                      <MiniBarChart
                        data={[
                          { label: 'Strong', value: pwdReport.strongPasswords, color: '#10b981' },
                          { label: 'Weak', value: pwdReport.weakPasswords, color: '#ef4444' },
                          { label: 'Reused', value: pwdReport.reusedPasswords, color: '#f97316' },
                          { label: 'Old', value: pwdReport.oldPasswords, color: '#f59e0b' },
                        ]}
                        maxVal={pwdReport.totalPasswords || 1}
                      />
                    </div>
                  </div>
                ) : (
                  <div className="sec-empty">Loading password health report…</div>
                )}
              </div>

              {/* Login Activity Report */}
              <div className="sec-report-card">
                <div className="sec-report-header">
                  <div>
                    <h3>🔑 Login Activity History</h3>
                    <p>Personal authentication history analysis</p>
                  </div>
                  <button className="sec-btn-refresh" onClick={fetchLoginReport}>🔄 Refresh</button>
                </div>

                {loginReport ? (
                  <div className="sec-report-body">
                    <div className="sec-login-kpis">
                      <div className="sec-login-kpi">
                        <span>Total Attempts</span>
                        <strong>{loginReport.totalAttempts}</strong>
                      </div>
                      <div className="sec-login-kpi sec-kpi-green">
                        <span>Successful Logins</span>
                        <strong>{loginReport.successfulLogins}</strong>
                      </div>
                      <div className="sec-login-kpi sec-kpi-red">
                        <span>Failed Logins</span>
                        <strong>{loginReport.failedLogins}</strong>
                      </div>
                      <div className="sec-login-kpi">
                        <span>Distinct IPs</span>
                        <strong>{Array.isArray(loginReport.uniqueIpAddresses) ? loginReport.uniqueIpAddresses.length : (loginReport.uniqueIpAddresses?.size ?? 1)}</strong>
                      </div>
                    </div>

                    <h4 style={{ margin: '1.2rem 0 0.6rem', fontSize: '0.85rem', color: '#94a3b8' }}>
                      Recent Login History
                    </h4>
                    <div className="sec-recent-table-wrap">
                      <table className="sec-audit-table">
                        <thead>
                          <tr>
                            <th>Time</th>
                            <th>Status</th>
                            <th>IP Address</th>
                            <th>Reason</th>
                          </tr>
                        </thead>
                        <tbody>
                          {loginReport.recentAttempts?.map((a, i) => (
                            <tr key={i}>
                              <td className="sec-td-time">{fmtDateTime(a.timestamp)}</td>
                              <td>
                                {a.success ? (
                                  <span className="sec-severity-pill" style={{ background: 'rgba(16,185,129,0.1)', color: '#10b981' }}>
                                    ✅ Success
                                  </span>
                                ) : (
                                  <span className="sec-severity-pill" style={{ background: severityBg.HIGH, color: severityColor.HIGH }}>
                                    ❌ Failed
                                  </span>
                                )}
                              </td>
                              <td><code className="sec-ip">{a.ipAddress || '—'}</code></td>
                              <td className="sec-td-reason">{a.failureReason || '—'}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                ) : (
                  <div className="sec-empty">Loading login activity report…</div>
                )}
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}

/* ── KPI Card ──────────────────────────────────────────────────────────── */
function KpiCard({ emoji, label, value, sub, color, pulse }) {
  return (
    <div className="sec-kpi-card" style={{ borderTopColor: color }}>
      <div className="sec-kpi-header">
        <div className="sec-kpi-emoji" style={{ color }}>
          {emoji}
          {pulse && <span className="sec-pulse" />}
        </div>
        <span className="sec-kpi-val" style={{ color }}>{value}</span>
      </div>
      <div className="sec-kpi-label">{label}</div>
      <div className="sec-kpi-sub">{sub}</div>
    </div>
  );
}
