import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { generatePassword, generatePassphrase, getPasswordStrength } from '../utils/passwordGenerator';
import {
  LockIcon,
  ShieldIcon,
  VaultIcon,
  StarIcon,
  GlobeIcon,
  MailIcon,
  BankIcon,
  Share2Icon,
  AppWindowIcon,
  KeyIcon,
  FileTextIcon,
  ZapIcon,
  ActivityIcon,
  UsersIcon,
  UserIcon,
  UserCheckIcon,
  SendIcon,
  CopyIcon,
  EyeIcon,
  EyeOffIcon,
  EditIcon,
  TrashIcon,
  PlusIcon,
  SearchIcon,
  ClockIcon,
  RefreshIcon,
  ExternalLinkIcon,
  CrownIcon,
  CheckIcon,
  XIcon,
} from '../icons';
import '../App.css';
import './Dashboard.css';

const API = 'http://localhost:8080';
const authHeaders = (token) => ({ Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' });

// ── Credential type metadata ──────────────────────────────────────────────────
const CRED_TYPES = [
  { key: 'WEBSITE_LOGIN',  label: 'Website Login',      icon: GlobeIcon,     color: '#6366f1' },
  { key: 'EMAIL_ACCOUNT',  label: 'Email Account',      icon: MailIcon,      color: '#06b6d4' },
  { key: 'BANKING',        label: 'Banking',            icon: BankIcon,      color: '#10b981' },
  { key: 'SOCIAL_MEDIA',   label: 'Social Media',       icon: Share2Icon,    color: '#f59e0b' },
  { key: 'APPLICATION',    label: 'Application',        icon: AppWindowIcon, color: '#8b5cf6' },
  { key: 'API_KEY',        label: 'API Key',            icon: KeyIcon,       color: '#ec4899' },
  { key: 'SECURE_NOTE',    label: 'Secure Note',        icon: FileTextIcon,  color: '#14b8a6' },
];

const typeMap = Object.fromEntries(CRED_TYPES.map(t => [t.key, t]));

const EMPTY_FORM = {
  siteName: '', siteUrl: '', accountUsername: '', accountPassword: '',
  credentialType: 'WEBSITE_LOGIN', category: '', notes: '', tags: '', favorite: false,
};

// ── Rating helpers ────────────────────────────────────────────────────────────
const ratingColor = (r) => ({
  Weak: '#f43f5e', Fair: '#f59e0b', Medium: '#f59e0b', Strong: '#10b981', 'Very Strong': '#6366f1',
  WEAK: '#f43f5e', MEDIUM: '#f59e0b', STRONG: '#10b981', 'VERY STRONG': '#6366f1',
})[r] || '#888';

const ratingBadgeClass = (r) => ({
  Weak: 'badge-rose', Fair: 'badge-amber', Medium: 'badge-amber', Strong: 'badge-emerald', 'Very Strong': 'badge-indigo',
  WEAK: 'badge-rose', MEDIUM: 'badge-amber', STRONG: 'badge-emerald', 'VERY STRONG': 'badge-indigo',
})[r] || 'badge-indigo';

const fmtDate = (d) => d ? new Date(d).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }) : '';
const fmtDateTime = (d) => d ? new Date(d).toLocaleString('en-GB', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }) : '';

export default function Dashboard() {
  const [credentials, setCredentials]         = useState([]);
  const [sharedWithMe, setSharedWithMe]       = useState([]);
  const [myShares, setMyShares]               = useState([]);
  const [teamVaults, setTeamVaults]           = useState([]);
  const [selectedVault, setSelectedVault]     = useState(null);
  const [vaultCreds, setVaultCreds]           = useState([]);
  
  // Section: all | favorites | type:X | generator | health | shared | outgoing | teams | user-dash | team-dash | admin-dash | reports
  const [activeSection, setActiveSection]     = useState('all');
  const [searchQuery, setSearchQuery]         = useState('');
  const [filterCategory, setFilterCategory]   = useState('');
  const [sortBy, setSortBy]                   = useState('newest');
  
  // Add / Edit Modal
  const [showModal, setShowModal]             = useState(false);
  const [editTarget, setEditTarget]           = useState(null);
  const [formData, setFormData]               = useState(EMPTY_FORM);
  const [formErrors, setFormErrors]           = useState({});
  const [showPassMap, setShowPassMap]         = useState({});

  // Share Modal
  const [shareModal, setShareModal]           = useState(null);
  const [shareType, setShareType]             = useState('USER');
  const [shareTarget, setShareTarget]         = useState('');
  const [shareRole, setShareRole]             = useState('TEAM_MEMBER');
  const [shareTeamVaultId, setShareTeamVaultId] = useState('');
  const [sharePermission, setSharePermission] = useState('VIEW_ONLY');
  const [shareDuration, setShareDuration]     = useState('0');
  const [customHours, setCustomHours]         = useState('12');
  const [shareStatus, setShareStatus]         = useState({ text: '', isError: false });

  // Edit Shared Credential Modal
  const [editSharedModal, setEditSharedModal] = useState(null);
  const [editSharedForm, setEditSharedForm]   = useState({ siteName: '', accountUsername: '', accountPassword: '', siteUrl: '', notes: '' });

  // Create Team Vault Modal
  const [showTeamModal, setShowTeamModal]     = useState(false);
  const [teamName, setTeamName]               = useState('');
  const [teamDesc, setTeamDesc]               = useState('');
  const [teamMembersInput, setTeamMembersInput] = useState('');
  const [addMemberInput, setAddMemberInput]   = useState('');

  // Password Health Tool
  const [healthInput, setHealthInput]         = useState('');
  const [healthResult, setHealthResult]       = useState(null);

  // Standalone Generator Tool State
  const [genMode, setGenMode]                 = useState('password');
  const [genLength, setGenLength]             = useState(18);
  const [genOptions, setGenOptions]           = useState({
    uppercase: true, lowercase: true, numbers: true, symbols: true,
    excludeAmbiguous: false, customSymbols: '', prefix: '',
  });
  const [genPassphraseWords, setGenPassphraseWords] = useState(4);
  const [genPassphraseSep, setGenPassphraseSep]     = useState('-');
  const [genPassphraseCap, setGenPassphraseCap]     = useState(true);
  const [genPassphraseNum, setGenPassphraseNum]     = useState(true);
  const [genPassword, setGenPassword]               = useState('');
  const [bulkPasswords, setBulkPasswords]           = useState([]);

  // Toast & Notifications State
  const [toast, setToast]                     = useState({ msg: '', type: 'success' });
  const [error, setError]                     = useState('');
  const [sidebarOpen, setSidebarOpen]         = useState(true);
  const [notifications, setNotifications]     = useState([]);
  const [unreadCount, setUnreadCount]         = useState(0);
  const [showNotifDropdown, setShowNotifDropdown] = useState(false);

  // Admin & Analytics State
  const [adminUsers, setAdminUsers]           = useState([]);
  const [systemMetrics, setSystemMetrics]     = useState(null);
  const [complianceReport, setComplianceReport] = useState(null);
  const [reportType, setReportType]           = useState('security');
  const [reportAnalytics, setReportAnalytics] = useState(null);
  const [secDashboard, setSecDashboard]       = useState(null);

  const searchRef                             = useRef(null);
  const notifRef                              = useRef(null);
  const navigate                              = useNavigate();
  const { user, logout, userRole, userMfa, refreshProfile } = useAuth();
  const token                                 = user?.token;
  const username                              = user?.username;
  const isAdmin                               = userRole === 'ADMIN';

  // MFA toggle
  const [mfaLoading, setMfaLoading]           = useState(false);

  // ── Data fetching ─────────────────────────────────────────────────────────
  const fetchCredentials = useCallback(async () => {
    try {
      const r = await fetch(`${API}/api/vault/credentials`, { headers: authHeaders(token) });
      if (r.ok) setCredentials(await r.json());
      else if (r.status === 401) handleLogout();
    } catch {
      setError('Unable to connect to vault service.');
    }
  }, [token]);

  const fetchSharedWithMe = useCallback(async () => {
    try {
      const r = await fetch(`${API}/api/vault/share/shared-with-me`, { headers: authHeaders(token) });
      if (r.ok) setSharedWithMe(await r.json());
    } catch {}
  }, [token]);

  const fetchMyShares = useCallback(async () => {
    try {
      const r = await fetch(`${API}/api/vault/share/my-shares`, { headers: authHeaders(token) });
      if (r.ok) setMyShares(await r.json());
    } catch {}
  }, [token]);

  const fetchTeamVaults = useCallback(async () => {
    try {
      const r = await fetch(`${API}/api/vault/share/teams`, { headers: authHeaders(token) });
      if (r.ok) setTeamVaults(await r.json());
    } catch {}
  }, [token]);

  const fetchVaultCreds = useCallback(async (vaultId) => {
    try {
      const r = await fetch(`${API}/api/vault/share/teams/${vaultId}/credentials`, { headers: authHeaders(token) });
      if (r.ok) setVaultCreds(await r.json());
    } catch {}
  }, [token]);

  const fetchNotifications = useCallback(async () => {
    try {
      const r = await fetch(`${API}/api/notifications`, { headers: authHeaders(token) });
      if (r.ok) setNotifications(await r.json());
      const c = await fetch(`${API}/api/notifications/unread-count`, { headers: authHeaders(token) });
      if (c.ok) {
        const data = await c.json();
        setUnreadCount(data.unreadCount || 0);
      }
    } catch {}
  }, [token]);

  const fetchAdminUsers = useCallback(async () => {
    try {
      const r = await fetch(`${API}/api/admin/users`, { headers: authHeaders(token) });
      if (r.ok) setAdminUsers(await r.json());
    } catch {}
  }, [token]);

  const fetchSystemMetrics = useCallback(async () => {
    try {
      const r = await fetch(`${API}/api/admin/system-metrics`, { headers: authHeaders(token) });
      if (r.ok) setSystemMetrics(await r.json());
    } catch {}
  }, [token]);

  const fetchComplianceReport = useCallback(async () => {
    try {
      const r = await fetch(`${API}/api/admin/compliance-report`, { headers: authHeaders(token) });
      if (r.ok) setComplianceReport(await r.json());
    } catch {}
  }, [token]);

  const fetchReportAnalytics = useCallback(async (typeStr) => {
    try {
      const targetType = typeStr || reportType;
      const r = await fetch(`${API}/api/reports/analytics?type=${targetType}`, { headers: authHeaders(token) });
      if (r.ok) setReportAnalytics(await r.json());
    } catch {}
  }, [token, reportType]);

  const fetchSecDashboard = useCallback(async () => {
    try {
      const r = await fetch(`${API}/api/security/dashboard`, { headers: authHeaders(token) });
      if (r.ok) setSecDashboard(await r.json());
    } catch {}
  }, [token]);

  useEffect(() => {
    if (!token) { navigate('/login'); return; }
    fetchCredentials();
    fetchSharedWithMe();
    fetchMyShares();
    fetchTeamVaults();
    fetchNotifications();
    fetchSecDashboard();
    refreshGenerator();
  }, [token]);

  useEffect(() => {
    if (activeSection === 'admin-dash') {
      fetchAdminUsers();
      fetchSystemMetrics();
      fetchComplianceReport();
    } else if (activeSection === 'reports') {
      fetchReportAnalytics();
    }
  }, [activeSection, fetchAdminUsers, fetchSystemMetrics, fetchComplianceReport, fetchReportAnalytics]);

  // ── Toast & Helpers ──────────────────────────────────────────────────────
  const showToast = (msg, type = 'success') => {
    setToast({ msg, type });
    setTimeout(() => setToast({ msg: '', type: 'success' }), 3000);
  };

  const handleLogout = () => { logout(); navigate('/login'); };

  const markNotifRead = async (id) => {
    try {
      await fetch(`${API}/api/notifications/${id}/read`, { method: 'PATCH', headers: authHeaders(token) });
      fetchNotifications();
    } catch {}
  };

  const markAllNotifsRead = async () => {
    try {
      await fetch(`${API}/api/notifications/read-all`, { method: 'PATCH', headers: authHeaders(token) });
      fetchNotifications();
    } catch {}
  };

  const testDispatchNotif = async (channel) => {
    try {
      const r = await fetch(`${API}/api/notifications/test`, {
        method: 'POST',
        headers: authHeaders(token),
        body: JSON.stringify({ channel }),
      });
      if (r.ok) {
        showToast(`Test notification dispatched via ${channel}!`);
        fetchNotifications();
      }
    } catch {
      showToast('Error dispatching test notification', 'error');
    }
  };

  const updateUserRole = async (userId, newRole) => {
    try {
      const r = await fetch(`${API}/api/admin/users/${userId}/role`, {
        method: 'PATCH',
        headers: authHeaders(token),
        body: JSON.stringify({ role: newRole }),
      });
      if (r.ok) {
        showToast('User role updated successfully');
        fetchAdminUsers();
      } else {
        showToast('Failed to update role', 'error');
      }
    } catch {
      showToast('Error updating role', 'error');
    }
  };

  const handleExportExcel = async () => {
    showToast(`Exporting CSV file (${reportType})...`);
    try {
      const r = await fetch(`${API}/api/reports/export/excel?type=${reportType}`, { headers: authHeaders(token) });
      if (!r.ok) throw new Error('Export failed');
      const blob = await r.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `report-${reportType}.csv`;  // ← correct extension
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
    } catch {
      showToast('Error exporting file', 'error');
    }
  };

  const handleExportPdf = async () => {
    showToast(`Generating printable report (${reportType})...`);
    try {
      const r = await fetch(`${API}/api/reports/export/pdf?type=${reportType}`, { headers: authHeaders(token) });
      if (!r.ok) throw new Error('Export failed');
      const html = await r.text();
      // Open HTML in new tab (browser print dialog auto-fires)
      const win = window.open('', '_blank');
      win.document.write(html);
      win.document.close();
    } catch {
      showToast('Error generating report', 'error');
    }
  };

  const handleToggleMfa = async () => {
    setMfaLoading(true);
    try {
      const r = await fetch(`${API}/api/auth/mfa/toggle`, {
        method: 'PATCH', headers: authHeaders(token),
      });
      if (r.ok) {
        const data = await r.json();
        showToast(data.message || 'MFA status updated');
        refreshProfile();
      } else {
        showToast('Failed to toggle MFA', 'error');
      }
    } catch {
      showToast('Network error', 'error');
    } finally {
      setMfaLoading(false);
    }
  };

  // ── Generator Logic ───────────────────────────────────────────────────────
  const refreshGenerator = () => {
    if (genMode === 'password') {
      const pwd = generatePassword(genLength, genOptions);
      setGenPassword(pwd);
    } else if (genMode === 'passphrase') {
      const phrase = generatePassphrase(genPassphraseWords, genPassphraseSep, genPassphraseCap, genPassphraseNum);
      setGenPassword(phrase);
    } else {
      const list = Array.from({ length: 5 }, () => generatePassword(genLength, genOptions));
      setBulkPasswords(list);
    }
  };

  // ── Modals & Actions ──────────────────────────────────────────────────────
  const openAddModal = () => {
    setEditTarget(null);
    // Pre-fill credentialType from the currently active sidebar section
    const preType = activeSection.startsWith('type:')
      ? activeSection.split(':')[1]
      : 'WEBSITE_LOGIN';
    setFormData({ ...EMPTY_FORM, credentialType: preType });
    setFormErrors({});
    setShowModal(true);
  };

  const openEditModal = (cred) => {
    setEditTarget(cred);
    setFormData({
      siteName: cred.siteName || '',
      siteUrl: cred.siteUrl || '',
      accountUsername: cred.accountUsername || '',
      accountPassword: cred.accountPassword || '',
      credentialType: cred.credentialType || 'WEBSITE_LOGIN',
      category: cred.category || '',
      notes: cred.notes || '',
      tags: cred.tags || '',
      favorite: cred.favorite || false,
    });
    setFormErrors({});
    setShowModal(true);
  };

  const handleSaveCredential = async (e) => {
    e.preventDefault();
    if (!formData.siteName.trim()) { setFormErrors({ siteName: 'Name is required' }); return; }

    const url = editTarget ? `${API}/api/vault/credentials/${editTarget.id}` : `${API}/api/vault/credentials`;
    const method = editTarget ? 'PUT' : 'POST';

    try {
      const r = await fetch(url, {
        method, headers: authHeaders(token), body: JSON.stringify(formData),
      });
      if (r.ok) {
        showToast(editTarget ? 'Credential updated' : 'Credential saved to vault');
        setShowModal(false);
        fetchCredentials();
      } else {
        const err = await r.json();
        showToast(err.error || 'Failed to save credential', 'error');
      }
    } catch {
      showToast('Network error saving credential', 'error');
    }
  };

  const handleDeleteCredential = async (id) => {
    if (!window.confirm('Delete this credential permanently?')) return;
    try {
      const r = await fetch(`${API}/api/vault/credentials/${id}`, { method: 'DELETE', headers: authHeaders(token) });
      if (r.ok) {
        showToast('Credential deleted');
        fetchCredentials();
      } else {
        showToast('Failed to delete credential', 'error');
      }
    } catch { showToast('Network error deleting credential', 'error'); }
  };

  const handleToggleFavorite = async (id) => {
    try {
      const r = await fetch(`${API}/api/vault/credentials/${id}/favorite`, { method: 'PATCH', headers: authHeaders(token) });
      if (r.ok) fetchCredentials();
    } catch {}
  };

  const handleShareSubmit = async (e) => {
    e.preventDefault();
    if (!shareModal) return;
    setShareStatus({ text: 'Sharing…', isError: false });

    let hours = null;
    if (shareDuration === '1') hours = 1;
    else if (shareDuration === '24') hours = 24;
    else if (shareDuration === '168') hours = 168;
    else if (shareDuration === 'custom') hours = parseInt(customHours, 10) || 12;

    const payload = {
      credentialId: shareModal.id,
      permissionLevel: sharePermission,
      durationHours: hours,
    };

    if (shareType === 'USER') payload.targetUser = shareTarget;
    else if (shareType === 'ROLE') payload.targetRole = shareRole;
    else if (shareType === 'TEAM') payload.teamVaultId = shareTeamVaultId;

    try {
      const r = await fetch(`${API}/api/vault/share`, {
        method: 'POST', headers: authHeaders(token), body: JSON.stringify(payload),
      });
      if (r.ok) {
        setShareStatus({ text: 'Share successfully configured! ✅', isError: false });
        fetchMyShares();
        setTimeout(() => { setShareModal(null); setShareStatus({ text: '', isError: false }); }, 1500);
      } else {
        const err = await r.json();
        setShareStatus({ text: err.error || 'Failed to share credential', isError: true });
      }
    } catch {
      setShareStatus({ text: 'Network error sharing credential', isError: true });
    }
  };

  const handleRevokeShare = async (shareId) => {
    if (!window.confirm('Revoke share access for this credential?')) return;
    try {
      const r = await fetch(`${API}/api/vault/share/${shareId}`, { method: 'DELETE', headers: authHeaders(token) });
      if (r.ok) {
        showToast('Share access revoked');
        fetchMyShares();
      }
    } catch {}
  };

  const handleCreateTeamVault = async (e) => {
    e.preventDefault();
    if (!teamName.trim()) return;
    const members = teamMembersInput.split(',').map(s => s.trim()).filter(Boolean);
    try {
      const r = await fetch(`${API}/api/vault/share/teams`, {
        method: 'POST', headers: authHeaders(token),
        body: JSON.stringify({ name: teamName, description: teamDesc, members }),
      });
      if (r.ok) {
        showToast('Team Vault created successfully!');
        setShowTeamModal(false);
        setTeamName(''); setTeamDesc(''); setTeamMembersInput('');
        fetchTeamVaults();
      } else {
        const err = await r.json();
        showToast(err.error || 'Failed to create team vault', 'error');
      }
    } catch {
      showToast('Network error — could not create team vault', 'error');
    }
  };

  const handleAddMember = async (vaultId) => {
    if (!addMemberInput.trim()) return;
    try {
      const r = await fetch(`${API}/api/vault/share/teams/${vaultId}/members`, {
        method: 'POST', headers: authHeaders(token),
        body: JSON.stringify({ user: addMemberInput.trim() }),
      });
      if (r.ok) {
        showToast(`Member '${addMemberInput.trim()}' added to vault`);
        setAddMemberInput('');
        fetchTeamVaults();
      } else {
        const err = await r.json();
        showToast(err.error || 'User not found or could not be added', 'error');
      }
    } catch {
      showToast('Network error — could not add member', 'error');
    }
  };

  const handleRemoveMember = async (vaultId, memberId) => {
    try {
      const r = await fetch(`${API}/api/vault/share/teams/${vaultId}/members/${memberId}`, {
        method: 'DELETE', headers: authHeaders(token),
      });
      if (r.ok) {
        showToast('Member removed');
        fetchTeamVaults();
      }
    } catch {}
  };

  const copyToClipboard = (text) => {
    navigator.clipboard.writeText(text);
    showToast('Copied to clipboard');
  };

  // ── Filtered list for vault items ─────────────────────────────────────────
  const visibleCredentials = (() => {
    let list = [...credentials];
    if (activeSection === 'favorites') list = list.filter(c => c.favorite);
    else if (activeSection.startsWith('type:')) {
      const t = activeSection.split(':')[1];
      list = list.filter(c => c.credentialType === t);
    }
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      list = list.filter(c =>
        (c.siteName || '').toLowerCase().includes(q) ||
        (c.accountUsername || '').toLowerCase().includes(q) ||
        (c.category || '').toLowerCase().includes(q) ||
        (c.tags || '').toLowerCase().includes(q)
      );
    }
    if (filterCategory.trim()) {
      list = list.filter(c => (c.category || '').toLowerCase() === filterCategory.toLowerCase());
    }
    if (sortBy === 'alpha') list.sort((a, b) => (a.siteName || '').localeCompare(b.siteName || ''));
    else if (sortBy === 'oldest') list.sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
    else list.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
    return list;
  })();

  const categories = [...new Set(credentials.map(c => c.category).filter(Boolean))];
  const typeCount = (key) => credentials.filter(c => c.credentialType === key).length;
  const favoriteCount = credentials.filter(c => c.favorite).length;

  // ── Sidebar items ─────────────────────────────────────────────────────────
  const sidebarItems = [
    { key: 'all',             icon: VaultIcon,     label: 'All Items',         count: credentials.length },
    { key: 'favorites',       icon: StarIcon,      label: 'Favorites',         count: favoriteCount },
    { key: 'divider1', isDivider: true, label: 'CREDENTIAL TYPES' },
    ...CRED_TYPES.map(t => ({ key: `type:${t.key}`, icon: t.icon, label: t.label, count: typeCount(t.key), color: t.color })),
    { key: 'divider2', isDivider: true, label: 'SECURITY TOOLS' },
    { key: 'generator',       icon: ZapIcon,       label: 'Password Generator', count: null },
    { key: 'health',          icon: ActivityIcon,  label: 'Password Health',    count: null },
    { key: 'divider3', isDivider: true, label: 'COLLABORATION' },
    { key: 'shared',          icon: UserCheckIcon, label: 'Shared With Me',     count: sharedWithMe.length },
    { key: 'outgoing',        icon: SendIcon,      label: 'My Shared Items',    count: myShares.length },
    { key: 'teams',           icon: UsersIcon,     label: 'Team Vaults',        count: teamVaults.length },
    { key: 'divider4', isDivider: true, label: 'ANALYTICS & DASHBOARDS' },
    { key: 'user-dash',       icon: ActivityIcon,  label: 'User Dashboard',     count: null },
    { key: 'team-dash',       icon: UsersIcon,     label: 'Team Dashboard',     count: null },
    // Admin Dashboard — only shown to ADMIN role
    ...(isAdmin ? [{ key: 'admin-dash', icon: CrownIcon, label: 'Admin Dashboard', count: null }] : []),
    { key: 'reports',         icon: FileTextIcon,  label: 'Reports & Export',   count: null },
    { key: 'divider5', isDivider: true, label: 'ACCOUNT' },
    { key: 'profile',         icon: UserIcon,      label: 'My Profile / MFA',   count: null },
  ];

  return (
    <div className="app db-root">
      <div className="page-bg" />

      {/* ── Toast ── */}
      {toast.msg && (
        <div className={`toast ${toast.type === 'error' ? 'toast-error' : ''}`}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <CheckIcon size={16} />
            <span>{toast.msg}</span>
          </div>
        </div>
      )}

      {/* ── Navbar ── */}
      <header className="navbar db-navbar">
        <div className="logo">
          <button className="sidebar-toggle" onClick={() => setSidebarOpen(o => !o)} title="Toggle sidebar">
            <span /><span /><span />
          </button>
          <div className="logo-icon"><LockIcon size={18} /></div>
          <h2>SecureVault</h2>
          <span className="tag" style={{ marginLeft: '0.5rem', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
            <ShieldIcon size={12} /> AES-256
          </span>
        </div>

        <div className="db-search-wrap">
          <div className="search-input-box">
            <SearchIcon size={15} className="search-icon" />
            <input
              ref={searchRef}
              className="db-search-input"
              type="text"
              placeholder="Search vault items, categories, tags…"
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              id="vault-search"
            />
          </div>
        </div>

        <div className="nav-buttons">
          {/* Notification Bell Dropdown */}
          <div className="notif-bell-wrap" ref={notifRef}>
            <button
              className="notif-bell-btn"
              onClick={() => setShowNotifDropdown(o => !o)}
              title="Notifications Center"
            >
              🔔
              {unreadCount > 0 && <span className="notif-badge">{unreadCount}</span>}
            </button>

            {showNotifDropdown && (
              <div className="notif-dropdown">
                <div className="notif-header">
                  <h4>🔔 Notifications ({notifications.length})</h4>
                  <div className="notif-actions">
                    <button className="btn btn-outline btn-xs" onClick={markAllNotifsRead}>Mark All Read</button>
                  </div>
                </div>

                <div className="notif-list">
                  {notifications.length === 0 ? (
                    <div style={{ padding: '1.5rem', textAlign: 'center', color: '#64748b', fontSize: '0.82rem' }}>
                      No notifications yet
                    </div>
                  ) : (
                    notifications.map(n => (
                      <div
                        key={n.id}
                        className={`notif-item ${!n.read ? 'notif-item-unread' : ''}`}
                        onClick={() => markNotifRead(n.id)}
                      >
                        <div className="notif-item-top">
                          <span className="notif-title">{n.title}</span>
                          <span className="notif-time">{fmtDateTime(n.createdAt)}</span>
                        </div>
                        <p className="notif-msg">{n.message}</p>
                      </div>
                    ))
                  )}
                </div>

                <div className="notif-footer">
                  <span style={{ fontSize: '0.72rem', color: '#64748b' }}>Dispatch Test:</span>
                  <div style={{ display: 'flex', gap: '0.35rem' }}>
                    <button className="btn btn-outline btn-xs" onClick={() => testDispatchNotif('EMAIL')}>Email</button>
                    <button className="btn btn-outline btn-xs" onClick={() => testDispatchNotif('PUSH')}>Push</button>
                  </div>
                </div>
              </div>
            )}
          </div>

          <div className="user-profile-badge" title={`Role: ${userRole}`}>
            <UserIcon size={14} />
            <span>{username}</span>
            <span style={{ fontSize: '0.65rem', background: userRole === 'ADMIN' ? '#6366f1' : '#10b981', color: '#fff', borderRadius: '4px', padding: '1px 5px', marginLeft: '4px' }}>
              {userRole}
            </span>
          </div>

          <Link
            to="/security"
            className="btn btn-outline btn-sm"
            style={{ display: 'inline-flex', alignItems: 'center', gap: '5px', textDecoration: 'none' }}
            title="Security Monitoring & Analytics"
          >
            <ShieldIcon size={13} /> Security
          </Link>
          <button className="btn btn-primary btn-sm" onClick={() => openAddModal()} id="add-credential-btn">
            <PlusIcon size={14} /> Add Item
          </button>
          <button className="btn btn-outline btn-sm" onClick={handleLogout}>Logout</button>
        </div>
      </header>

      <div className="db-body">
        {/* ── Sidebar ── */}
        <aside className={`db-sidebar ${sidebarOpen ? 'open' : 'closed'}`}>
          {sidebarItems.map(item => {
            if (item.isDivider) return (
              <div key={item.key} className="sidebar-divider">{item.label}</div>
            );
            const IconComponent = item.icon;
            return (
              <button
                key={item.key}
                className={`sidebar-item ${activeSection === item.key ? 'active' : ''}`}
                onClick={() => { setActiveSection(item.key); setSelectedVault(null); }}
                title={item.label}
                id={`sidebar-${item.key}`}
              >
                <span className="sidebar-icon">
                  <IconComponent size={16} />
                </span>
                <span className="sidebar-label">{item.label}</span>
                {item.count !== null && item.count > 0 && (
                  <span className="sidebar-count">{item.count}</span>
                )}
              </button>
            );
          })}
        </aside>

        {/* ── Main Content ── */}
        <main className="db-main">
          {error && (
            <div className="db-error-banner">
              <span>{error}</span>
              <button onClick={() => setError('')}><XIcon size={14} /></button>
            </div>
          )}

          {/* ═══ CREDENTIAL VAULT VIEWS ═════════════════════════════════════ */}
          {(activeSection === 'all' || activeSection === 'favorites' || activeSection.startsWith('type:')) && (
            <div className="anim-fadein">
              <div className="db-toolbar">
                <div className="db-toolbar-left">
                  <h3 className="db-section-title">
                    {activeSection === 'all' && (
                      <>
                        <VaultIcon size={18} />
                        <span>All Credentials</span>
                      </>
                    )}
                    {activeSection === 'favorites' && (
                      <>
                        <StarIcon size={18} filled />
                        <span>Favorites</span>
                      </>
                    )}
                    {activeSection.startsWith('type:') && (() => {
                      const t = typeMap[activeSection.split(':')[1]];
                      const Icon = t?.icon || GlobeIcon;
                      return (
                        <>
                          <Icon size={18} />
                          <span>{t?.label}</span>
                        </>
                      );
                    })()}
                    <span className="badge badge-indigo" style={{ marginLeft: '0.5rem', fontSize: '0.8rem' }}>
                      {visibleCredentials.length}
                    </span>
                  </h3>
                </div>

                <div className="db-toolbar-right">
                  {categories.length > 0 && (
                    <select
                      className="db-filter-select"
                      value={filterCategory}
                      onChange={e => setFilterCategory(e.target.value)}
                      id="category-filter"
                    >
                      <option value="">All Categories</option>
                      {categories.map(c => <option key={c} value={c}>{c}</option>)}
                    </select>
                  )}

                  <select
                    className="db-filter-select"
                    value={sortBy}
                    onChange={e => setSortBy(e.target.value)}
                    id="sort-select"
                  >
                    <option value="newest">Newest first</option>
                    <option value="oldest">Oldest first</option>
                    <option value="alpha">Alphabetical</option>
                  </select>

                  <button className="btn btn-primary btn-sm" onClick={() => openAddModal()} id="toolbar-add-btn">
                    <PlusIcon size={14} /> Add Item
                  </button>
                </div>
              </div>

              {visibleCredentials.length === 0 ? (
                <div className="db-empty">
                  <div className="db-empty-icon-wrap">
                    <VaultIcon size={36} />
                  </div>
                  <h3>No credentials found</h3>
                  <p>Store logins, API keys, and encrypted secure notes protected with AES-256 GCM.</p>
                  <button className="btn btn-primary" style={{ marginTop: '1.25rem' }} onClick={() => openAddModal()}>
                    <PlusIcon size={14} /> Add Credential
                  </button>
                </div>
              ) : (
                <div className="db-cred-grid">
                  {visibleCredentials.map(cred => {
                    const meta = typeMap[cred.credentialType] || typeMap.WEBSITE_LOGIN;
                    const IconComponent = meta.icon;
                    const isNote = cred.credentialType === 'SECURE_NOTE';
                    return (
                      <div key={cred.id} className="cred-card anim-scalein" id={`cred-card-${cred.id}`}>
                        <div className="cred-card-header">
                          <div className="cred-icon" style={{ background: `${meta.color}15`, border: `1px solid ${meta.color}30`, color: meta.color }}>
                            <IconComponent size={20} />
                          </div>
                          <div className="cred-meta">
                            <div className="cred-title" title={cred.siteName}>{cred.siteName}</div>
                            {cred.category && (
                              <span className="cred-category" style={{ borderColor: `${meta.color}40`, color: meta.color, background: `${meta.color}10` }}>
                                {cred.category}
                              </span>
                            )}
                          </div>
                          <button
                            className={`cred-fav-btn ${cred.favorite ? 'active' : ''}`}
                            onClick={() => handleToggleFavorite(cred.id)}
                            title={cred.favorite ? 'Remove from favorites' : 'Add to favorites'}
                            id={`fav-btn-${cred.id}`}
                          >
                            <StarIcon size={16} filled={cred.favorite} />
                          </button>
                        </div>

                        {/* Fields */}
                        {isNote ? (
                          <div className="cred-note-content">{cred.notes || '(Empty note)'}</div>
                        ) : (
                          <>
                            {cred.credentialType !== 'API_KEY' && cred.accountUsername && (
                              <div className="cred-field">
                                <span className="cred-field-label">Username / Account</span>
                                <div className="cred-field-row">
                                  <span className="cred-field-value">{cred.accountUsername}</span>
                                  <button className="cred-copy-btn" onClick={() => copyToClipboard(cred.accountUsername)} title="Copy username">
                                    <CopyIcon size={13} />
                                  </button>
                                </div>
                              </div>
                            )}

                            <div className="cred-field">
                              <span className="cred-field-label">{cred.credentialType === 'API_KEY' ? 'API Key' : 'Password'}</span>
                              <div className="cred-field-row">
                                <span className="cred-field-value mono">
                                  {showPassMap[cred.id] ? cred.accountPassword : '••••••••••••'}
                                </span>
                                <button className="cred-copy-btn" onClick={() => setShowPassMap(p => ({ ...p, [cred.id]: !p[cred.id] }))} title="Toggle visibility">
                                  {showPassMap[cred.id] ? <EyeOffIcon size={13} /> : <EyeIcon size={13} />}
                                </button>
                                <button className="cred-copy-btn" onClick={() => copyToClipboard(cred.accountPassword)} title="Copy password">
                                  <CopyIcon size={13} />
                                </button>
                              </div>
                            </div>
                          </>
                        )}

                        {cred.siteUrl && (
                          <div className="cred-url">
                            <a href={cred.siteUrl.startsWith('http') ? cred.siteUrl : `https://${cred.siteUrl}`} target="_blank" rel="noreferrer">
                              <ExternalLinkIcon size={12} /> {cred.siteUrl}
                            </a>
                          </div>
                        )}

                        <div className="cred-card-footer">
                          <span className="cred-date">Updated {fmtDate(cred.updatedAt || cred.createdAt)}</span>
                          <div className="cred-actions">
                            <button className="cred-action-btn" onClick={() => { setShareModal(cred); setShareStatus({ text: '', isError: false }); }} title="Share credential">
                              <Share2Icon size={14} />
                            </button>
                            <button className="cred-action-btn" onClick={() => openEditModal(cred)} title="Edit credential">
                              <EditIcon size={14} />
                            </button>
                            <button className="cred-action-btn cred-delete-btn" onClick={() => handleDeleteCredential(cred.id)} title="Delete credential">
                              <TrashIcon size={14} />
                            </button>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}

          {/* ═══ USER DASHBOARD ══════════════════════════════════════════════ */}
          {activeSection === 'user-dash' && (
            <div className="anim-fadein">
              {/* ── Header ── */}
              <div className="db-toolbar">
                <div>
                  <h3 className="db-section-title" style={{ marginBottom: '0.2rem' }}>
                    <ActivityIcon size={18} />
                    <span>User Dashboard</span>
                  </h3>
                  <p style={{ margin: 0, fontSize: '0.82rem', color: 'var(--text-muted)' }}>
                    Welcome back, <strong style={{ color: 'var(--text)' }}>{username}</strong> &nbsp;·&nbsp;
                    <span className="badge badge-indigo" style={{ fontSize: '0.68rem' }}>{userRole}</span>
                  </p>
                </div>
                <button className="btn btn-outline btn-sm" onClick={() => { fetchSecDashboard(); fetchCredentials(); fetchTeamVaults(); }}>
                  <RefreshIcon size={13} /> Refresh
                </button>
              </div>

              {/* ── KPI Hero Cards ── */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '1rem', marginBottom: '1.25rem' }}>
                {[
                  { label: 'Total Credentials', value: credentials.length, sub: `${favoriteCount} favorited`, icon: VaultIcon },
                  { label: 'Active Alerts', value: secDashboard?.activeAlerts ?? 0, sub: (secDashboard?.activeAlerts ?? 0) === 0 ? 'All clear ✓' : 'Needs attention', icon: ShieldIcon },
                  { label: 'Shared With Me', value: sharedWithMe.length, sub: `${myShares.length} outgoing`, icon: UserCheckIcon },
                  { label: 'Team Vaults', value: teamVaults.length, sub: 'Collaborative', icon: UsersIcon },
                ].map(({ label, value, sub, icon: Icon }) => (
                  <div key={label} style={{
                    background: 'var(--bg2)', border: '1px solid var(--border)', borderRadius: '14px',
                    padding: '1.25rem', position: 'relative', overflow: 'hidden',
                  }}>
                    <div style={{ position: 'absolute', top: '1rem', right: '1rem', width: '36px', height: '36px', borderRadius: '10px', background: 'var(--bg3)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--indigo)' }}>
                      <Icon size={18} />
                    </div>
                    <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: '0.5rem' }}>{label}</div>
                    <div style={{ fontSize: '2.2rem', fontWeight: 800, color: 'var(--text)', lineHeight: 1, marginBottom: '0.3rem' }}>{value}</div>
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{sub}</div>
                  </div>
                ))}
              </div>

              {/* ── Middle Row: Breakdown + Score ── */}
              <div style={{ display: 'grid', gridTemplateColumns: '1.6fr 1fr', gap: '1.25rem', marginBottom: '1.25rem' }}>

                {/* Vault Composition */}
                <div className="dash-card">
                  <h3 style={{ marginTop: 0, marginBottom: '1.1rem', fontSize: '0.9rem', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '0.5rem', color: 'var(--text)' }}>
                    <VaultIcon size={15} style={{ color: 'var(--text-muted)' }} /> Vault Composition
                  </h3>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.9rem' }}>
                    {CRED_TYPES.map(t => {
                      const count = typeCount(t.key);
                      const pct = credentials.length ? Math.round((count / credentials.length) * 100) : 0;
                      const Icon = t.icon;
                      return (
                        <div key={t.key} style={{ cursor: 'pointer' }} onClick={() => setActiveSection(`type:${t.key}`)}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.35rem' }}>
                            <div style={{ width: '24px', height: '24px', borderRadius: '7px', background: 'var(--bg3)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-muted)', flexShrink: 0 }}>
                              <Icon size={12} />
                            </div>
                            <span style={{ fontSize: '0.81rem', color: 'var(--text)', flex: 1 }}>{t.label}</span>
                            <strong style={{ fontSize: '0.81rem', color: 'var(--text)' }}>{count}</strong>
                            <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', minWidth: '32px', textAlign: 'right' }}>{pct}%</span>
                          </div>
                          <div style={{ height: '7px', background: 'var(--bg3)', borderRadius: '99px', overflow: 'hidden' }}>
                            <div style={{
                              height: '100%', borderRadius: '99px',
                              background: 'var(--indigo)',
                              width: `${pct}%`, transition: 'width 1s cubic-bezier(0.4,0,0.2,1)',
                            }} />
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>

                {/* Security Score Ring */}
                {(() => {
                  const mfaScore = userMfa ? 30 : 0;
                  const credScore = Math.min(credentials.length * 5, 40);
                  const alertPenalty = Math.min((secDashboard?.activeAlerts ?? 0) * 10, 30);
                  const score = Math.max(0, Math.min(100, mfaScore + credScore - alertPenalty));
                  const scoreLabel = score >= 70 ? 'Strong' : score >= 40 ? 'Fair' : 'At Risk';
                  const r = 52; const circ = 2 * Math.PI * r;
                  return (
                    <div className="dash-card" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.9rem' }}>
                      <h3 style={{ marginTop: 0, marginBottom: 0, fontSize: '0.9rem', fontWeight: 600, alignSelf: 'flex-start', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                        <ShieldIcon size={15} style={{ color: 'var(--text-muted)' }} /> Security Score
                      </h3>
                      <div style={{ position: 'relative', width: '148px', height: '148px' }}>
                        <svg width="148" height="148" viewBox="0 0 130 130">
                          <circle cx="65" cy="65" r={r} fill="none" stroke="var(--bg3)" strokeWidth="11" />
                          <circle cx="65" cy="65" r={r} fill="none"
                            stroke="var(--indigo)" strokeWidth="11"
                            strokeDasharray={circ} strokeDashoffset={circ - (score / 100) * circ}
                            strokeLinecap="round" transform="rotate(-90 65 65)"
                            style={{ transition: 'stroke-dashoffset 1.2s cubic-bezier(0.4,0,0.2,1)' }}
                          />
                        </svg>
                        <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
                          <span style={{ fontSize: '2.1rem', fontWeight: 800, color: 'var(--text)', lineHeight: 1 }}>{score}</span>
                          <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>/ 100</span>
                        </div>
                      </div>
                      <span className="badge badge-indigo" style={{ fontSize: '0.8rem', padding: '4px 14px', background: 'var(--bg3)', color: 'var(--text)' }}>{scoreLabel}</span>
                      <div style={{ width: '100%', display: 'flex', flexDirection: 'column', gap: '0.45rem', fontSize: '0.78rem' }}>
                        {[
                          { label: 'MFA Protection', val: `${userMfa ? '+30' : '0'} pts` },
                          { label: 'Vault Coverage', val: `+${Math.min(credentials.length * 5, 40)} pts` },
                          ...((secDashboard?.activeAlerts ?? 0) > 0 ? [{ label: 'Alert Penalty', val: `−${Math.min((secDashboard?.activeAlerts ?? 0) * 10, 30)} pts` }] : []),
                        ].map(({ label, val }) => (
                          <div key={label} style={{ display: 'flex', justifyContent: 'space-between', padding: '0.35rem 0.6rem', background: 'var(--bg3)', borderRadius: '8px' }}>
                            <span style={{ color: 'var(--text-muted)' }}>{label}</span>
                            <span style={{ color: 'var(--text)', fontWeight: 600 }}>{val}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  );
                })()}
              </div>

              {/* ── Bottom Row: Actions + Checklist ── */}
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.25rem' }}>
                {/* Quick Actions */}
                <div className="dash-card">
                  <h3 style={{ marginTop: 0, fontSize: '0.9rem', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
                    <ZapIcon size={15} style={{ color: 'var(--text-muted)' }} /> Quick Actions
                  </h3>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                    {[
                      { label: 'Add New Credential', desc: 'Store a login securely', icon: PlusIcon, action: () => openAddModal() },
                      { label: 'Generate Password', desc: 'Create strong passwords', icon: ZapIcon, action: () => setActiveSection('generator') },
                      { label: 'Password Health Check', desc: 'Analyze strength & leaks', icon: ActivityIcon, action: () => setActiveSection('health') },
                      { label: userMfa ? 'MFA Enabled ✓' : 'Enable MFA', desc: userMfa ? 'Account is protected' : 'Add extra security layer', icon: ShieldIcon, action: () => setActiveSection('profile') },
                    ].map(({ label, desc, icon: Icon, action }) => (
                      <button key={label} onClick={action} style={{
                        display: 'flex', alignItems: 'center', gap: '0.75rem',
                        padding: '0.65rem 0.85rem', borderRadius: '11px',
                        border: '1px solid var(--border)', background: 'transparent',
                        cursor: 'pointer', textAlign: 'left', width: '100%',
                        transition: 'all 0.18s',
                      }}
                        onMouseEnter={e => { e.currentTarget.style.background = 'var(--bg3)'; e.currentTarget.style.transform = 'translateX(3px)'; }}
                        onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.transform = 'none'; }}
                      >
                        <div style={{ width: '34px', height: '34px', borderRadius: '9px', background: 'var(--bg3)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-muted)', flexShrink: 0 }}>
                          <Icon size={16} />
                        </div>
                        <div style={{ flex: 1 }}>
                          <div style={{ fontSize: '0.84rem', fontWeight: 600, color: 'var(--text)' }}>{label}</div>
                          <div style={{ fontSize: '0.74rem', color: 'var(--text-muted)' }}>{desc}</div>
                        </div>
                        <span style={{ color: 'var(--text-muted)', fontSize: '1.1rem', lineHeight: 1 }}>›</span>
                      </button>
                    ))}
                  </div>
                </div>

                {/* Security Checklist */}
                <div className="dash-card">
                  <h3 style={{ marginTop: 0, fontSize: '0.9rem', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
                    <CheckIcon size={15} style={{ color: 'var(--text-muted)' }} /> Security Checklist
                  </h3>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.55rem' }}>
                    {[
                      { done: userMfa, text: 'Enable Multi-Factor Authentication', fix: 'profile' },
                      { done: credentials.length >= 1, text: 'Store at least one credential', fix: null },
                      { done: favoriteCount > 0, text: 'Mark important credentials as favorites', fix: null },
                      { done: (secDashboard?.activeAlerts ?? 0) === 0, text: 'No active security alerts', fix: null },
                      { done: teamVaults.length > 0 || sharedWithMe.length > 0, text: 'Collaborate via Team Vaults', fix: 'teams' },
                    ].map(({ done, text, fix }) => (
                      <div key={text} style={{
                        display: 'flex', alignItems: 'center', gap: '0.65rem',
                        padding: '0.6rem 0.8rem', borderRadius: '10px',
                        background: 'transparent',
                        border: '1px solid var(--border)',
                      }}>
                        <div style={{
                          width: '22px', height: '22px', borderRadius: '50%', flexShrink: 0,
                          background: done ? 'var(--indigo)' : 'transparent',
                          border: done ? 'none' : '2px solid var(--border)',
                          display: 'flex', alignItems: 'center', justifyContent: 'center',
                          fontSize: '0.68rem', color: '#fff', fontWeight: 700,
                        }}>
                          {done ? '✓' : ''}
                        </div>
                        <span style={{ flex: 1, fontSize: '0.82rem', color: done ? 'var(--text-muted)' : 'var(--text)', textDecoration: done ? 'line-through' : 'none', opacity: done ? 0.65 : 1 }}>
                          {text}
                        </span>
                        {!done && fix && (
                          <button onClick={() => setActiveSection(fix)} style={{ fontSize: '0.72rem', color: 'var(--text)', background: 'var(--bg3)', border: '1px solid var(--border)', cursor: 'pointer', padding: '2px 8px', borderRadius: '6px', fontWeight: 600, whiteSpace: 'nowrap' }}>
                            Fix →
                          </button>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          )}


          {/* ═══ TEAM DASHBOARD (MODULE 9) ═══════════════════════════════════ */}
          {activeSection === 'team-dash' && (
            <div className="anim-fadein">
              <div className="db-toolbar">
                <h3 className="db-section-title">
                  <UsersIcon size={18} />
                  <span>Team Collaboration Dashboard</span>
                </h3>
              </div>

              <div className="dash-kpi-grid">
                <div className="dash-card">
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>TEAM VAULTS</span>
                  <h2 style={{ fontSize: '1.8rem', margin: '0.4rem 0' }}>{teamVaults.length}</h2>
                </div>
                <div className="dash-card">
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>MY OUTGOING SHARES</span>
                  <h2 style={{ fontSize: '1.8rem', margin: '0.4rem 0' }}>{myShares.length}</h2>
                </div>
                <div className="dash-card">
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>INCOMING SHARES</span>
                  <h2 style={{ fontSize: '1.8rem', margin: '0.4rem 0' }}>{sharedWithMe.length}</h2>
                </div>
              </div>

              <div className="dash-card">
                <h3 style={{ marginTop: 0, marginBottom: '1.25rem', fontSize: '1rem', display: 'flex', alignItems: 'center', gap: '0.5rem', color: 'var(--text)' }}>
                  <Share2Icon size={18} style={{ color: 'var(--text-muted)' }} /> Shared Credentials Inventory
                </h3>
                <div className="admin-table-wrap">
                  <table className="admin-table">
                    <thead>
                      <tr><th>Credential</th><th>Owner</th><th>Permission Level</th><th>Expires</th></tr>
                    </thead>
                    <tbody>
                      {sharedWithMe.map(s => (
                        <tr key={s.id}>
                          <td><strong>{s.credential?.siteName}</strong></td>
                          <td>{s.owner?.username}</td>
                          <td><span className="badge badge-indigo">{s.permissionLevel}</span></td>
                          <td>{s.expiresAt ? fmtDateTime(s.expiresAt) : 'Permanent'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {/* ═══ ADMIN DASHBOARD (MODULE 9) ══════════════════════════════════ */}
          {activeSection === 'admin-dash' && (
            <div className="anim-fadein">
              <div className="db-toolbar">
                <h3 className="db-section-title">
                  <CrownIcon size={18} />
                  <span>Admin Management & System Monitoring</span>
                </h3>
                <button className="btn btn-outline btn-sm" onClick={() => { fetchAdminUsers(); fetchSystemMetrics(); fetchComplianceReport(); }}>
                  <RefreshIcon size={13} /> Refresh
                </button>
              </div>

              <div className="dash-kpi-grid">
                <div className="dash-card">
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>REGISTERED USERS</span>
                  <h2 style={{ fontSize: '1.8rem', margin: '0.4rem 0' }}>{adminUsers.length}</h2>
                </div>
                <div className="dash-card">
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>SYSTEM MEMORY</span>
                  <h2 style={{ fontSize: '1.8rem', margin: '0.4rem 0', color: '#10b981' }}>{systemMetrics?.memoryUsagePercentage ?? 0}%</h2>
                </div>
                <div className="dash-card">
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>API HEALTH</span>
                  <h2 style={{ fontSize: '1.8rem', margin: '0.4rem 0', color: '#10b981' }}>{systemMetrics?.apiHealthStatus ?? 'OK'}</h2>
                </div>
                <div className="dash-card">
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>COMPLIANCE SCORE</span>
                  <h2 style={{ fontSize: '1.8rem', margin: '0.4rem 0', color: '#6366f1' }}>{complianceReport?.overallComplianceScore ?? 95} / 100</h2>
                </div>
              </div>

              <div className="dash-card">
                <h3>👥 Registered User Directory</h3>
                <div className="admin-table-wrap">
                  <table className="admin-table">
                    <thead>
                      <tr><th>ID</th><th>Username</th><th>Email</th><th>Role</th><th>MFA</th><th>Provider</th></tr>
                    </thead>
                    <tbody>
                      {adminUsers.map(u => (
                        <tr key={u.id}>
                          <td>#{u.id}</td>
                          <td><strong>{u.username}</strong></td>
                          <td>{u.email}</td>
                          <td>
                            <select
                              className="role-select"
                              value={u.role}
                              onChange={e => updateUserRole(u.id, e.target.value)}
                            >
                              <option value="USER">USER</option>
                              <option value="TEAM_MEMBER">TEAM_MEMBER</option>
                              <option value="ADMIN">ADMIN</option>
                            </select>
                          </td>
                          <td>{u.mfaEnabled ? '✅ Enabled' : '❌ Disabled'}</td>
                          <td><span className="badge badge-indigo">{u.authProvider}</span></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              <div className="dash-card">
                <h3>📋 Compliance Checklist (SOX / ISO 27001 / HIPAA)</h3>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
                  {complianceReport?.checklist?.map((item, i) => (
                    <div key={i} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0.6rem 0.8rem', background: 'var(--bg3)', borderRadius: '8px' }}>
                      <div>
                        <strong style={{ fontSize: '0.85rem', color: 'var(--text)' }}>{item.requirement}</strong>
                        <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{item.details}</div>
                      </div>
                      <span className={`badge ${item.status === 'PASS' ? 'badge-emerald' : 'badge-amber'}`}>{item.status}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

          {/* ═══ REPORTS & EXPORT MODULE (MODULE 10) ═════════════════════════ */}
          {activeSection === 'reports' && (
            <div className="anim-fadein">
              <div className="db-toolbar">
                <div className="db-toolbar-left">
                  <h3 className="db-section-title">
                    <FileTextIcon size={18} />
                    <span>Reports & Export Center</span>
                  </h3>
                </div>

                <div className="db-toolbar-right">
                  <button className="btn btn-outline btn-sm" onClick={handleExportExcel} id="export-excel-btn">
                    📥 Export Excel / CSV
                  </button>
                  <button className="btn btn-primary btn-sm" onClick={handleExportPdf} id="export-pdf-btn">
                    📄 Export Printable PDF
                  </button>
                </div>
              </div>

              <div className="report-selector-bar">
                {[
                  { id: 'security',          label: '🛡️ Security Report' },
                  { id: 'password-health',   label: '🔐 Password Health Report' },
                  { id: 'audit',             label: '📋 Audit Trail Report' },
                  { id: 'user-activity',     label: '🔑 User Activity Report' },
                  { id: 'threat-monitoring', label: '🚨 Threat Monitoring Report' },
                ].map(r => (
                  <button
                    key={r.id}
                    className={`report-btn ${reportType === r.id ? 'active' : ''}`}
                    onClick={() => { setReportType(r.id); fetchReportAnalytics(r.id); }}
                  >
                    {r.label}
                  </button>
                ))}
              </div>

              <div className="dash-card">
                <h3>Report Preview — {reportType.toUpperCase().replace('-', ' ')}</h3>
                {reportAnalytics ? (
                  <div style={{ fontSize: '0.85rem', color: 'var(--text-dim)' }}>
                    <p>Generated for user <strong>{username}</strong> at {fmtDateTime(reportAnalytics.generatedAt)}</p>
                    <pre style={{ background: 'var(--bg3)', padding: '1rem', borderRadius: '8px', overflowX: 'auto', fontSize: '0.8rem', color: '#a5b4fc' }}>
                      {JSON.stringify(reportAnalytics, null, 2)}
                    </pre>
                  </div>
                ) : (
                  <div className="db-empty">Loading report preview…</div>
                )}
              </div>
            </div>
          )}

          {/* ═══ PASSWORD GENERATOR VIEW ═════════════════════════════════════ */}
          {activeSection === 'generator' && (
            <div className="anim-fadein">
              <div className="db-toolbar">
                <h3 className="db-section-title">
                  <ZapIcon size={18} />
                  <span>Password Generator</span>
                </h3>
              </div>

              <div className="dash-card" style={{ maxWidth: '640px' }}>
                <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1.25rem' }}>
                  <button className={`btn btn-sm ${genMode === 'password' ? 'btn-primary' : 'btn-outline'}`} onClick={() => { setGenMode('password'); refreshGenerator(); }}>Password</button>
                  <button className={`btn btn-sm ${genMode === 'passphrase' ? 'btn-primary' : 'btn-outline'}`} onClick={() => { setGenMode('passphrase'); refreshGenerator(); }}>Passphrase</button>
                  <button className={`btn btn-sm ${genMode === 'bulk' ? 'btn-primary' : 'btn-outline'}`} onClick={() => { setGenMode('bulk'); refreshGenerator(); }}>Bulk Generator</button>
                </div>

                {genMode === 'bulk' ? (
                  <div>
                    {bulkPasswords.map((p, i) => (
                      <div key={i} className="cred-field-row" style={{ marginBottom: '0.5rem' }}>
                        <span className="cred-field-value mono">{p}</span>
                        <button className="cred-copy-btn" onClick={() => copyToClipboard(p)}><CopyIcon size={13} /></button>
                      </div>
                    ))}
                    <button className="btn btn-outline btn-sm" onClick={refreshGenerator} style={{ marginTop: '0.75rem' }}><RefreshIcon size={13} /> Regenerate Bulk</button>
                  </div>
                ) : (
                  <div>
                    <div className="cred-field-row" style={{ marginBottom: '1.25rem', padding: '0.75rem', background: 'var(--bg3)', borderRadius: '8px' }}>
                      <span className="cred-field-value mono" style={{ fontSize: '1.1rem' }}>{genPassword}</span>
                      <button className="btn btn-primary btn-sm" onClick={() => copyToClipboard(genPassword)}><CopyIcon size={14} /> Copy</button>
                      <button className="btn btn-outline btn-sm" onClick={refreshGenerator}><RefreshIcon size={14} /></button>
                    </div>

                    {genMode === 'password' ? (
                      <div>
                        <label className="cred-field-label">Length: {genLength}</label>
                        <input type="range" min="8" max="64" value={genLength} onChange={e => { setGenLength(parseInt(e.target.value)); refreshGenerator(); }} style={{ width: '100%', marginBottom: '1rem' }} />
                      </div>
                    ) : (
                      <div>
                        <label className="cred-field-label">Words: {genPassphraseWords}</label>
                        <input type="range" min="3" max="8" value={genPassphraseWords} onChange={e => { setGenPassphraseWords(parseInt(e.target.value)); refreshGenerator(); }} style={{ width: '100%', marginBottom: '1rem' }} />
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* ═══ PASSWORD HEALTH VIEW ════════════════════════════════════════ */}
          {activeSection === 'health' && (
            <div className="anim-fadein">
              <div className="db-toolbar">
                <h3 className="db-section-title">
                  <ActivityIcon size={18} />
                  <span>Password Health Checker</span>
                </h3>
              </div>

              <div className="dash-card" style={{ maxWidth: '640px' }}>
                <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Check password strength and time to crack offline.</p>
                <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.75rem' }}>
                  <input
                    className="input"
                    type="password"
                    placeholder="Enter password to test strength…"
                    value={healthInput}
                    onChange={e => setHealthInput(e.target.value)}
                  />
                  <button className="btn btn-primary" onClick={checkPasswordHealth}>Check</button>
                </div>

                {healthResult && (
                  <div style={{ marginTop: '1.25rem', padding: '1rem', background: 'var(--bg3)', borderRadius: '8px' }}>
                    <h4>Strength Score: {healthResult.score} / 4 ({healthResult.rating})</h4>
                    <p style={{ fontSize: '0.83rem', color: 'var(--text-muted)' }}>Estimated Crack Time: <strong>{healthResult.crackTime}</strong></p>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* ═══ SHARED WITH ME VIEW ═════════════════════════════════════════ */}
          {activeSection === 'shared' && (
            <div className="anim-fadein">
              <div className="db-toolbar">
                <h3 className="db-section-title">
                  <UserCheckIcon size={18} />
                  <span>Shared With Me</span>
                  <span className="badge badge-indigo" style={{ marginLeft: '0.75rem', fontSize: '0.8rem' }}>{sharedWithMe.length}</span>
                </h3>
              </div>

              {sharedWithMe.length === 0 ? (
                <div className="db-empty">
                  <h3>No shared items</h3>
                  <p>Credentials shared directly with you or your role will appear here.</p>
                </div>
              ) : (
                <div className="db-cred-grid">
                  {sharedWithMe.map(share => {
                    const c = share.credential || {};
                    return (
                      <div key={share.id} className="cred-card anim-scalein">
                        <div className="cred-card-header">
                          <div className="cred-icon" style={{ background: '#6366f115', color: 'var(--indigo)' }}>
                            <GlobeIcon size={18} />
                          </div>
                          <div className="cred-meta">
                            <div className="cred-title">{c.siteName}</div>
                            <div className="cred-type-label" style={{ color: 'var(--indigo)' }}>Shared by {share.owner?.username}</div>
                          </div>
                          <span className="badge badge-indigo" style={{ fontSize: '0.72rem' }}>{share.permissionLevel}</span>
                        </div>
                        {c.accountUsername && (
                          <div className="cred-field">
                            <span className="cred-field-label">Username</span>
                            <div className="cred-field-row"><span className="cred-field-value">{c.accountUsername}</span></div>
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}

          {/* ═══ MY SHARED ITEMS VIEW ════════════════════════════════════════ */}
          {activeSection === 'outgoing' && (
            <div className="anim-fadein">
              <div className="db-toolbar">
                <h3 className="db-section-title">
                  <SendIcon size={18} />
                  <span>My Outgoing Shares</span>
                  <span className="badge badge-indigo" style={{ marginLeft: '0.75rem', fontSize: '0.8rem' }}>{myShares.length}</span>
                </h3>
              </div>

              {myShares.length === 0 ? (
                <div className="db-empty">
                  <h3>No outgoing shares</h3>
                  <p>Credentials shared with others will be listed here.</p>
                </div>
              ) : (
                <div className="db-cred-grid">
                  {myShares.map(share => (
                    <div key={share.id} className="cred-card anim-scalein">
                      <div className="cred-card-header">
                        <div className="cred-meta">
                          <div className="cred-title">{share.credential?.siteName}</div>
                        </div>
                        <button className="cred-delete-btn btn-sm" onClick={() => handleRevokeShare(share.id)}>Revoke</button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* ═══ TEAM VAULTS VIEW ════════════════════════════════════════════ */}
          {activeSection === 'teams' && (
            <div className="anim-fadein">
              {/* ── Vault items panel (shown when a vault is selected) ── */}
              {selectedVault ? (
                <div>
                  <div className="db-toolbar">
                    <h3 className="db-section-title">
                      <UsersIcon size={18} />
                      <span>{selectedVault.name}</span>
                      <span className="badge badge-indigo" style={{ marginLeft: '0.75rem', fontSize: '0.8rem' }}>
                        {vaultCreds.length} items
                      </span>
                    </h3>
                    <div style={{ display: 'flex', gap: '0.5rem' }}>
                      <button className="btn btn-outline btn-sm" onClick={() => fetchVaultCreds(selectedVault.id)}>
                        <RefreshIcon size={13} /> Refresh
                      </button>
                      <button className="btn btn-outline btn-sm" onClick={() => { setSelectedVault(null); setVaultCreds([]); }}>
                        ← Back to Vaults
                      </button>
                    </div>
                  </div>

                  {selectedVault.description && (
                    <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '1.25rem' }}>
                      {selectedVault.description}
                    </p>
                  )}

                  {vaultCreds.length === 0 ? (
                    <div className="db-empty">
                      <h3>No credentials in this vault yet</h3>
                      <p>Share credentials into this team vault using the Share button on any credential card.</p>
                    </div>
                  ) : (
                    <div className="db-cred-grid">
                      {vaultCreds.map(share => {
                        const c = share.credential || {};
                        const meta = typeMap[c.credentialType] || typeMap.WEBSITE_LOGIN;
                        const IconComponent = meta.icon;
                        return (
                          <div key={share.id} className="cred-card anim-scalein">
                            <div className="cred-card-header">
                              <div className="cred-icon" style={{ background: `${meta.color}15`, border: `1px solid ${meta.color}30`, color: meta.color }}>
                                <IconComponent size={18} />
                              </div>
                              <div className="cred-meta">
                                <div className="cred-title">{c.siteName}</div>
                                <div className="cred-type-label" style={{ color: meta.color }}>{meta.label}</div>
                              </div>
                              <span className="badge badge-indigo" style={{ fontSize: '0.72rem' }}>{share.permissionLevel}</span>
                            </div>
                            {c.accountUsername && (
                              <div className="cred-field">
                                <span className="cred-field-label">Username</span>
                                <div className="cred-field-row">
                                  <span className="cred-field-value">{c.accountUsername}</span>
                                  <button className="cred-copy-btn" onClick={() => copyToClipboard(c.accountUsername)} title="Copy username">
                                    <CopyIcon size={13} />
                                  </button>
                                </div>
                              </div>
                            )}
                            {c.accountPassword && (
                              <div className="cred-field">
                                <span className="cred-field-label">Password</span>
                                <div className="cred-field-row">
                                  <span className="cred-field-value mono">
                                    {showPassMap[`tv-${share.id}`] ? c.accountPassword : '••••••••••••'}
                                  </span>
                                  <button className="cred-copy-btn" onClick={() => setShowPassMap(p => ({ ...p, [`tv-${share.id}`]: !p[`tv-${share.id}`] }))} title="Toggle">
                                    {showPassMap[`tv-${share.id}`] ? <EyeOffIcon size={13} /> : <EyeIcon size={13} />}
                                  </button>
                                  <button className="cred-copy-btn" onClick={() => copyToClipboard(c.accountPassword)} title="Copy password">
                                    <CopyIcon size={13} />
                                  </button>
                                </div>
                              </div>
                            )}
                            {c.siteUrl && (
                              <div className="cred-url">
                                <a href={c.siteUrl.startsWith('http') ? c.siteUrl : `https://${c.siteUrl}`} target="_blank" rel="noreferrer">
                                  <ExternalLinkIcon size={12} /> {c.siteUrl}
                                </a>
                              </div>
                            )}
                            <div className="cred-card-footer">
                              <span className="cred-date">Shared by {share.owner?.username}</span>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              ) : (
                /* ── Vault list ── */
                <div>
                  <div className="db-toolbar">
                    <h3 className="db-section-title">
                      <UsersIcon size={18} />
                      <span>Team Vaults</span>
                      <span className="badge badge-indigo" style={{ marginLeft: '0.75rem', fontSize: '0.8rem' }}>{teamVaults.length}</span>
                    </h3>
                    <div style={{ display: 'flex', gap: '0.5rem' }}>
                      <button className="btn btn-outline btn-sm" onClick={fetchTeamVaults}>
                        <RefreshIcon size={13} /> Refresh
                      </button>
                      <button className="btn btn-primary btn-sm" onClick={() => setShowTeamModal(true)}>
                        <PlusIcon size={14} /> Create Team Vault
                      </button>
                    </div>
                  </div>

                  {teamVaults.length === 0 ? (
                    <div className="db-empty">
                      <div className="db-empty-icon-wrap"><UsersIcon size={36} /></div>
                      <h3>No team vaults yet</h3>
                      <p>Create a team vault to collaborate and share credentials with your team securely.</p>
                      <button className="btn btn-primary" style={{ marginTop: '1.25rem' }} onClick={() => setShowTeamModal(true)}>
                        <PlusIcon size={14} /> Create Team Vault
                      </button>
                    </div>
                  ) : (
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(380px, 1fr))', gap: '1.25rem' }}>
                      {teamVaults.map(vault => (
                        <div key={vault.id} className="team-card anim-scalein">
                          {/* Vault header */}
                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.75rem' }}>
                            <div>
                              <h4 style={{ margin: '0 0 0.25rem', fontSize: '1rem' }}>{vault.name}</h4>
                              {vault.description && (
                                <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', margin: 0 }}>{vault.description}</p>
                              )}
                              <div style={{ marginTop: '0.4rem' }}>
                                <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>Owner: </span>
                                <span className="badge badge-indigo" style={{ fontSize: '0.7rem' }}>{vault.owner?.username}</span>
                              </div>
                            </div>
                            <button
                              className="btn btn-primary btn-sm"
                              onClick={() => { setSelectedVault(vault); fetchVaultCreds(vault.id); }}
                            >
                              View Items
                            </button>
                          </div>

                          {/* Members list */}
                          {vault.members && vault.members.length > 0 && (
                            <div style={{ marginBottom: '0.75rem' }}>
                              <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginBottom: '0.4rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                                Members ({vault.members.length})
                              </div>
                              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.35rem' }}>
                                {vault.members.map(m => (
                                  <div key={m.id} style={{ display: 'flex', alignItems: 'center', gap: '0.3rem', background: 'var(--bg3)', borderRadius: '20px', padding: '2px 8px', fontSize: '0.78rem' }}>
                                    <UserIcon size={11} />
                                    <span>{m.username}</span>
                                    {/* Only vault owner can remove members */}
                                    {vault.owner?.username === username && m.username !== username && (
                                      <button
                                        onClick={() => handleRemoveMember(vault.id, m.id)}
                                        style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#ef4444', padding: '0 2px', lineHeight: 1, display: 'flex', alignItems: 'center' }}
                                        title={`Remove ${m.username}`}
                                      >
                                        <XIcon size={10} />
                                      </button>
                                    )}
                                  </div>
                                ))}
                              </div>
                            </div>
                          )}

                          {/* Add member — only shown to vault owner */}
                          {vault.owner?.username === username && (
                            <div style={{ display: 'flex', gap: '0.4rem' }}>
                              <input
                                className="input"
                                style={{ fontSize: '0.82rem', padding: '0.35rem 0.6rem', flex: 1 }}
                                placeholder="Add member by username…"
                                value={addMemberInput}
                                onChange={e => setAddMemberInput(e.target.value)}
                                onKeyDown={e => { if (e.key === 'Enter') { handleAddMember(vault.id); } }}
                              />
                              <button className="btn btn-primary btn-sm" onClick={() => handleAddMember(vault.id)}>
                                <PlusIcon size={13} /> Add
                              </button>
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          )}


          {/* ═══ MY PROFILE / MFA SECTION ════════════════════════════════════ */}
          {activeSection === 'profile' && (
            <div className="anim-fadein">
              <div className="db-toolbar">
                <h3 className="db-section-title">
                  <UserIcon size={18} />
                  <span>My Profile &amp; Security Settings</span>
                </h3>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(360px, 1fr))', gap: '1.25rem' }}>
                {/* Profile info card */}
                <div className="dash-card">
                  <h3 style={{ marginTop: 0 }}>👤 Account Info</h3>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', fontSize: '0.9rem' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0.5rem 0', borderBottom: '1px solid var(--border)' }}>
                      <span style={{ color: 'var(--text-muted)' }}>Username</span>
                      <strong>{username}</strong>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0.5rem 0', borderBottom: '1px solid var(--border)' }}>
                      <span style={{ color: 'var(--text-muted)' }}>Role</span>
                      <span className={`badge ${userRole === 'ADMIN' ? 'badge-indigo' : 'badge-emerald'}`}>{userRole}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0.5rem 0', borderBottom: '1px solid var(--border)' }}>
                      <span style={{ color: 'var(--text-muted)' }}>Total Credentials</span>
                      <strong>{credentials.length}</strong>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0.5rem 0' }}>
                      <span style={{ color: 'var(--text-muted)' }}>Favorites</span>
                      <strong>{favoriteCount}</strong>
                    </div>
                  </div>
                </div>

                {/* MFA card */}
                <div className="dash-card">
                  <h3 style={{ marginTop: 0 }}>🛡️ Multi-Factor Authentication</h3>
                  <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', lineHeight: 1.6 }}>
                    MFA adds an extra verification layer to protect your account.
                    When enabled, login events are flagged and monitored more strictly in the Security Dashboard.
                  </p>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginTop: '1.25rem', padding: '1rem', background: 'var(--bg3)', borderRadius: '10px' }}>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontWeight: 600, fontSize: '0.9rem' }}>MFA Status</div>
                      <div style={{ fontSize: '0.8rem', color: userMfa ? '#10b981' : '#f59e0b', marginTop: '0.2rem' }}>
                        {userMfa ? '✅ Enabled — Account is protected' : '⚠️ Disabled — Enable for extra security'}
                      </div>
                    </div>
                    <button
                      className={`btn btn-sm ${userMfa ? 'btn-outline' : 'btn-primary'}`}
                      onClick={handleToggleMfa}
                      disabled={mfaLoading}
                      id="mfa-toggle-btn"
                    >
                      {mfaLoading ? 'Updating…' : userMfa ? 'Disable MFA' : 'Enable MFA'}
                    </button>
                  </div>
                  <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.75rem' }}>
                    Note: This project uses a simulated MFA flag. In production, this would initiate TOTP setup (e.g., Google Authenticator).
                  </p>
                </div>

                {/* Password health snapshot */}
                <div className="dash-card">
                  <h3 style={{ marginTop: 0 }}>🔐 Password Health Snapshot</h3>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem', fontSize: '0.85rem' }}>
                    {CRED_TYPES.map(t => (
                      <div key={t.key}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--text-muted)', marginBottom: '0.2rem' }}>
                          <span>{t.label}</span>
                          <strong style={{ color: 'var(--text)' }}>{typeCount(t.key)}</strong>
                        </div>
                        <div className="meter-track">
                          <div className="meter-fill" style={{ width: `${(typeCount(t.key) / (credentials.length || 1)) * 100}%`, background: t.color }} />
                        </div>
                      </div>
                    ))}
                    {credentials.length === 0 && <p style={{ color: 'var(--text-muted)' }}>No credentials yet — add some to see stats.</p>}
                  </div>
                </div>
              </div>
            </div>
          )}
        </main>
      </div>

      {/* ── Add / Edit Modal ── */}
      {showModal && (
        <div className="modal-overlay">
          <div className="modal-card" style={{ maxWidth: '560px', maxHeight: '90vh', overflowY: 'auto' }}>
            <h3 style={{ marginTop: 0 }}>{editTarget ? '✏️ Edit Credential' : '➕ Add New Credential'}</h3>
            <form onSubmit={handleSaveCredential}>

              {/* Credential Type */}
              <div style={{ marginBottom: '1rem' }}>
                <label className="cred-field-label">Credential Type *</label>
                <select
                  className="input"
                  value={formData.credentialType}
                  onChange={e => setFormData({ ...formData, credentialType: e.target.value })}
                  id="modal-cred-type"
                >
                  {CRED_TYPES.map(t => (
                    <option key={t.key} value={t.key}>{t.label}</option>
                  ))}
                </select>
              </div>

              {/* Site Name */}
              <div style={{ marginBottom: '1rem' }}>
                <label className="cred-field-label">Site / App Name *</label>
                <input
                  className={`input ${formErrors.siteName ? 'input-error' : ''}`}
                  value={formData.siteName}
                  onChange={e => setFormData({ ...formData, siteName: e.target.value })}
                  placeholder="e.g. Google, GitHub, My Bank"
                  required
                  id="modal-site-name"
                />
                {formErrors.siteName && <span style={{ color: '#ef4444', fontSize: '0.78rem' }}>{formErrors.siteName}</span>}
              </div>

              {/* Site URL */}
              <div style={{ marginBottom: '1rem' }}>
                <label className="cred-field-label">Site URL</label>
                <input
                  className="input"
                  value={formData.siteUrl}
                  onChange={e => setFormData({ ...formData, siteUrl: e.target.value })}
                  placeholder="https://example.com"
                  id="modal-site-url"
                />
              </div>

              {/* Username / Account */}
              {formData.credentialType !== 'SECURE_NOTE' && (
                <div style={{ marginBottom: '1rem' }}>
                  <label className="cred-field-label">
                    {formData.credentialType === 'API_KEY' ? 'API Key Label / Owner' : 'Username / Account'}
                  </label>
                  <input
                    className="input"
                    value={formData.accountUsername}
                    onChange={e => setFormData({ ...formData, accountUsername: e.target.value })}
                    placeholder={formData.credentialType === 'API_KEY' ? 'e.g. OpenAI Production Key' : 'e.g. user@email.com'}
                    id="modal-account-username"
                  />
                </div>
              )}

              {/* Password / API Key */}
              {formData.credentialType !== 'SECURE_NOTE' && (
                <div style={{ marginBottom: '1rem' }}>
                  <label className="cred-field-label">
                    {formData.credentialType === 'API_KEY' ? 'API Key / Secret' : 'Password'}
                  </label>
                  <input
                    className="input"
                    type="password"
                    value={formData.accountPassword}
                    onChange={e => setFormData({ ...formData, accountPassword: e.target.value })}
                    placeholder={formData.credentialType === 'API_KEY' ? 'sk-…' : '••••••••'}
                    id="modal-account-password"
                  />
                </div>
              )}

              {/* Category */}
              <div style={{ marginBottom: '1rem' }}>
                <label className="cred-field-label">Category</label>
                <input
                  className="input"
                  value={formData.category}
                  onChange={e => setFormData({ ...formData, category: e.target.value })}
                  placeholder="e.g. Work, Personal, Finance"
                  id="modal-category"
                />
              </div>

              {/* Tags */}
              <div style={{ marginBottom: '1rem' }}>
                <label className="cred-field-label">Tags <span style={{ color: 'var(--text-muted)', fontWeight: 400 }}>(comma separated)</span></label>
                <input
                  className="input"
                  value={formData.tags}
                  onChange={e => setFormData({ ...formData, tags: e.target.value })}
                  placeholder="e.g. important, 2fa, work"
                  id="modal-tags"
                />
              </div>

              {/* Notes */}
              <div style={{ marginBottom: '1rem' }}>
                <label className="cred-field-label">
                  {formData.credentialType === 'SECURE_NOTE' ? 'Secure Note Content *' : 'Notes'}
                </label>
                <textarea
                  className="input"
                  style={{ minHeight: '80px', resize: 'vertical' }}
                  value={formData.notes}
                  onChange={e => setFormData({ ...formData, notes: e.target.value })}
                  placeholder={formData.credentialType === 'SECURE_NOTE' ? 'Type your encrypted note here…' : 'Optional notes…'}
                  id="modal-notes"
                />
              </div>

              {/* Favorite toggle */}
              <div style={{ marginBottom: '1.25rem', display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer', fontSize: '0.9rem', color: 'var(--text)' }}>
                  <input
                    type="checkbox"
                    checked={formData.favorite}
                    onChange={e => setFormData({ ...formData, favorite: e.target.checked })}
                    id="modal-favorite"
                    style={{ width: '16px', height: '16px', accentColor: '#f59e0b' }}
                  />
                  <StarIcon size={15} filled={formData.favorite} />
                  Mark as Favorite
                </label>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem' }}>
                <button type="button" className="btn btn-outline" onClick={() => setShowModal(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary" id="modal-save-btn">Save Credential</button>
              </div>
            </form>
          </div>
        </div>
      )}
      {/* ═══ CREATE TEAM VAULT MODAL ═══════════════════════════════════════ */}
      {showTeamModal && (
        <div className="modal-overlay" onClick={() => setShowTeamModal(false)}>
          <div className="modal-box" style={{ maxWidth: '480px' }} onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h3 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <UsersIcon size={18} /> Create Team Vault
              </h3>
              <button className="modal-close" onClick={() => setShowTeamModal(false)}>
                <XIcon size={16} />
              </button>
            </div>

            <form onSubmit={handleCreateTeamVault} style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div>
                <label className="cred-field-label">Team Vault Name *</label>
                <input
                  className="input"
                  value={teamName}
                  onChange={e => setTeamName(e.target.value)}
                  placeholder="e.g. Engineering Team, Finance Dept"
                  required
                  id="team-vault-name"
                  autoFocus
                />
              </div>

              <div>
                <label className="cred-field-label">Description</label>
                <textarea
                  className="input"
                  style={{ minHeight: '72px' }}
                  value={teamDesc}
                  onChange={e => setTeamDesc(e.target.value)}
                  placeholder="Optional description of this team vault…"
                  id="team-vault-desc"
                />
              </div>

              <div>
                <label className="cred-field-label">
                  Initial Members <span style={{ color: 'var(--text-muted)', fontWeight: 400 }}>(comma-separated usernames)</span>
                </label>
                <input
                  className="input"
                  value={teamMembersInput}
                  onChange={e => setTeamMembersInput(e.target.value)}
                  placeholder="e.g. alice, bob, charlie"
                  id="team-vault-members"
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem', marginTop: '0.5rem' }}>
                <button type="button" className="btn btn-outline" onClick={() => { setShowTeamModal(false); setTeamName(''); setTeamDesc(''); setTeamMembersInput(''); }}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary" id="team-vault-create-btn">
                  <UsersIcon size={14} /> Create Vault
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
