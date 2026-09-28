import React, { useState } from 'react';
import {
  setStorageMode,
  setServerUrl,
  getServerUrl,
  STORAGE_MODE_LOCAL,
  STORAGE_MODE_SELF_HOSTED,
  isNativeApp
} from '../serverConfig.js';
import { api } from '../api.js';

export default function OnboardingWalkthrough({ onComplete }) {
  const [currentPage, setCurrentPage] = useState(0);
  const [selectedMode, setSelectedMode] = useState(STORAGE_MODE_LOCAL);
  const [customServerUrl, setCustomServerUrl] = useState(getServerUrl() || '');
  const [serverTesting, setServerTesting] = useState(false);
  const [serverTestStatus, setServerTestStatus] = useState(null); // { ok: bool, message: string }
  const [biometricsEnabled, setBiometricsEnabled] = useState(false);

  const totalPages = 4;

  const handleNext = () => {
    if (currentPage < totalPages - 1) {
      setCurrentPage((p) => p + 1);
    } else {
      handleFinish();
    }
  };

  const handleBack = () => {
    if (currentPage > 0) {
      setCurrentPage((p) => p - 1);
    }
  };

  const handleTestServer = async () => {
    if (!customServerUrl.trim()) return;
    setServerTesting(true);
    setServerTestStatus(null);
    try {
      setServerUrl(customServerUrl);
      const res = await api.remoteGet('/api/auth/me');
      if (res) {
        setServerTestStatus({ ok: true, message: 'Connected successfully to Orbit Money server!' });
      }
    } catch (err) {
      setServerTestStatus({ ok: false, message: err.message || 'Could not reach server.' });
    } finally {
      setServerTesting(false);
    }
  };

  const handleFinish = () => {
    setStorageMode(selectedMode);
    if (selectedMode === STORAGE_MODE_SELF_HOSTED) {
      setServerUrl(customServerUrl);
    }
    if (biometricsEnabled) {
      localStorage.setItem('orbit_biometrics_enabled', 'true');
    }
    onComplete();
  };

  return (
    <div className="onboarding-container">
      {/* Top Bar */}
      <header className="onboarding-topbar">
        <div className="onboarding-logo-title">
          <span className="onboarding-brand">Orbit Money</span>
        </div>
        {currentPage < totalPages - 1 && (
          <button
            type="button"
            className="onboarding-skip-btn"
            onClick={handleFinish}
          >
            Skip Setup
          </button>
        )}
      </header>

      {/* Main Content Area / Pager */}
      <main className="onboarding-content">
        {currentPage === 0 && (
          <div className="onboarding-card-animate">
            <div className="onboarding-hero-icon">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="10" />
                <path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20" />
                <path d="M2 12h20" />
              </svg>
            </div>
            <h1 className="onboarding-title">Welcome to Orbit Money</h1>
            <p className="onboarding-subtitle">
              A private, fast, and friendly personal finance tracker built for your device.
            </p>

            <div className="onboarding-features-box">
              <div className="onboarding-feature-row">
                <div className="onboarding-feature-icon">🔒</div>
                <div className="onboarding-feature-text">
                  <div className="onboarding-feature-title">Local & Private by Default</div>
                  <div className="onboarding-feature-desc">All your accounts and transactions live securely right on this device.</div>
                </div>
              </div>
              <div className="onboarding-feature-row">
                <div className="onboarding-feature-icon">⚡</div>
                <div className="onboarding-feature-text">
                  <div className="onboarding-feature-title">Instant & Offline</div>
                  <div className="onboarding-feature-desc">Embedded SQLite provides instant load times and zero network lag.</div>
                </div>
              </div>
              <div className="onboarding-feature-row">
                <div className="onboarding-feature-icon">🏦</div>
                <div className="onboarding-feature-text">
                  <div className="onboarding-feature-title">SimpleFIN Bank Sync</div>
                  <div className="onboarding-feature-desc">Automatically sync bank and credit card accounts on your schedule.</div>
                </div>
              </div>
              <div className="onboarding-feature-row">
                <div className="onboarding-feature-icon">☁️</div>
                <div className="onboarding-feature-text">
                  <div className="onboarding-feature-title">Google Drive or Self-Hosted</div>
                  <div className="onboarding-feature-desc">Backup silently to your private Google Drive or connect a private household server.</div>
                </div>
              </div>
            </div>
          </div>
        )}

        {currentPage === 1 && (
          <div className="onboarding-card-animate">
            <div className="onboarding-hero-icon">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <ellipse cx="12" cy="5" rx="9" ry="3" />
                <path d="M3 5v14a9 3 0 0 0 18 0V5" />
                <path d="M3 12a9 3 0 0 0 18 0" />
              </svg>
            </div>
            <h1 className="onboarding-title">How will you store your data?</h1>
            <p className="onboarding-subtitle">
              Choose the mode that best fits your workflow. You can switch at any time in Settings.
            </p>

            <div className="onboarding-mode-options">
              {/* Option A: Local First */}
              <div
                className={`onboarding-mode-card ${selectedMode === STORAGE_MODE_LOCAL ? 'selected' : ''}`}
                onClick={() => setSelectedMode(STORAGE_MODE_LOCAL)}
              >
                <div className="onboarding-mode-header">
                  <div className="onboarding-mode-title-wrap">
                    <span className="onboarding-mode-radio">{selectedMode === STORAGE_MODE_LOCAL ? '●' : '○'}</span>
                    <strong className="onboarding-mode-name">Local First (Recommended)</strong>
                  </div>
                  <span className="onboarding-badge-green">Offline & Private</span>
                </div>
                <p className="onboarding-mode-desc">
                  Stored directly on this device in SQLite. No server required. Easily backup to Google Drive or download offline SQLite files.
                </p>
              </div>

              {/* Option B: Self-Hosted */}
              <div
                className={`onboarding-mode-card ${selectedMode === STORAGE_MODE_SELF_HOSTED ? 'selected' : ''}`}
                onClick={() => setSelectedMode(STORAGE_MODE_SELF_HOSTED)}
              >
                <div className="onboarding-mode-header">
                  <div className="onboarding-mode-title-wrap">
                    <span className="onboarding-mode-radio">{selectedMode === STORAGE_MODE_SELF_HOSTED ? '●' : '○'}</span>
                    <strong className="onboarding-mode-name">Self-Hosted Server</strong>
                  </div>
                  <span className="onboarding-badge-blue">Household & Multi-User</span>
                </div>
                <p className="onboarding-mode-desc">
                  Connect to your private Orbit Money Docker instance. Supports shared household budgeting, multiple users, and SSO.
                </p>

                {selectedMode === STORAGE_MODE_SELF_HOSTED && (
                  <div className="onboarding-server-input-block" onClick={(e) => e.stopPropagation()}>
                    <label className="onboarding-input-label">Server URL</label>
                    <div className="onboarding-input-row">
                      <input
                        type="url"
                        className="onboarding-text-input"
                        placeholder="https://orbit.yourdomain.com"
                        value={customServerUrl}
                        onChange={(e) => setCustomServerUrl(e.target.value)}
                      />
                      <button
                        type="button"
                        className="onboarding-action-btn"
                        onClick={handleTestServer}
                        disabled={serverTesting || !customServerUrl}
                      >
                        {serverTesting ? 'Testing...' : 'Test'}
                      </button>
                    </div>
                    {serverTestStatus && (
                      <div className={`onboarding-test-result ${serverTestStatus.ok ? 'success' : 'error'}`}>
                        {serverTestStatus.ok ? '✓ ' : '✕ '} {serverTestStatus.message}
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {currentPage === 2 && (
          <div className="onboarding-card-animate">
            <div className="onboarding-hero-icon">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z" />
              </svg>
            </div>
            <h1 className="onboarding-title">Google Drive Cloud Backup</h1>
            <p className="onboarding-subtitle">
              Never lose your financial records. Orbit Money backs up securely to your private Google Drive sandbox.
            </p>

            <div className="onboarding-features-box">
              <div className="onboarding-feature-row">
                <div className="onboarding-feature-icon">🛡️</div>
                <div className="onboarding-feature-text">
                  <div className="onboarding-feature-title">Hidden AppData Sandbox</div>
                  <div className="onboarding-feature-desc">Orbit only accesses its own hidden folder. We cannot read or alter any other files in your Google Drive.</div>
                </div>
              </div>
              <div className="onboarding-feature-row">
                <div className="onboarding-feature-icon">📲</div>
                <div className="onboarding-feature-text">
                  <div className="onboarding-feature-title">Seamless Device Restore</div>
                  <div className="onboarding-feature-desc">Upgrading phones or reinstalling? Restore your entire database with a single tap.</div>
                </div>
              </div>
              <div className="onboarding-feature-row">
                <div className="onboarding-feature-icon">💾</div>
                <div className="onboarding-feature-text">
                  <div className="onboarding-feature-title">Manual SQLite Exports</div>
                  <div className="onboarding-feature-desc">Download standard `.sqlite` database files anytime for complete offline portability.</div>
                </div>
              </div>
            </div>

            <div className="onboarding-hint-text">
              Google Drive can be connected anytime from Settings.
            </div>
          </div>
        )}

        {currentPage === 3 && (
          <div className="onboarding-card-animate">
            <div className="onboarding-hero-icon">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <rect width="18" height="11" x="3" y="11" rx="2" ry="2" />
                <path d="M7 11V7a5 5 0 0 1 10 0v4" />
              </svg>
            </div>
            <h1 className="onboarding-title">You're All Set!</h1>
            <p className="onboarding-subtitle">
              Your financial orbit is ready. Let's take control of your accounts, budgets, and savings.
            </p>

            <div className="onboarding-summary-card">
              <div className="onboarding-summary-row">
                <span>Storage Mode:</span>
                <strong>{selectedMode === STORAGE_MODE_LOCAL ? 'Local First (Device SQLite)' : 'Self-Hosted Server'}</strong>
              </div>
              {selectedMode === STORAGE_MODE_SELF_HOSTED && customServerUrl && (
                <div className="onboarding-summary-row">
                  <span>Server:</span>
                  <span className="onboarding-summary-url">{customServerUrl}</span>
                </div>
              )}
              <div className="onboarding-summary-row">
                <span>Database:</span>
                <strong>SQLite 3 with Unified Schema</strong>
              </div>
            </div>

            <div className="onboarding-finish-action">
              <button
                type="button"
                className="onboarding-launch-btn"
                onClick={handleFinish}
              >
                Launch Orbit Money 🚀
              </button>
            </div>
          </div>
        )}
      </main>

      {/* Bottom Bar with Dynamic Pill Indicators & Pager Controls */}
      <footer className="onboarding-bottombar">
        {currentPage > 0 ? (
          <button
            type="button"
            className="onboarding-nav-icon-btn"
            onClick={handleBack}
            aria-label="Back"
          >
            ←
          </button>
        ) : (
          <div className="onboarding-nav-spacer" />
        )}

        {/* Dynamic Spring Dot Indicators */}
        <div className="onboarding-dots-row">
          {Array.from({ length: totalPages }).map((_, index) => (
            <span
              key={index}
              className={`onboarding-dot ${currentPage === index ? 'active' : ''}`}
            />
          ))}
        </div>

        {currentPage < totalPages - 1 ? (
          <button
            type="button"
            className="onboarding-nav-icon-btn next"
            onClick={handleNext}
            aria-label="Next"
          >
            →
          </button>
        ) : (
          <div className="onboarding-nav-spacer" />
        )}
      </footer>
    </div>
  );
}
