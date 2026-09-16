/**
 * Main Application Orchestrator & View Controller
 * Styled with Lucent.AI Technical Monospace & Bento Aesthetics
 */

const App = {
  currentTab: 'tab-calendar',

  init() {
    this.initTheme();
    this.initPWA();
    this.initNetworkStatus();
    this.bindAuthEvents();
    this.bindDashboardEvents();
    this.checkExistingSession();
  },

  // --------------------------------------------------------------------------
  // Theme Management (Pitch Black Mode Permanent)
  // --------------------------------------------------------------------------
  initTheme() {
    document.documentElement.classList.add('dark');
    document.body.classList.add('dark');
    localStorage.setItem('att_theme', 'dark');
  },

  setTheme(theme = 'dark') {
    document.documentElement.classList.add('dark');
    document.body.classList.add('dark');
    localStorage.setItem('att_theme', 'dark');
  },

  toggleTheme() {
    this.setTheme('dark');
  },

  // --------------------------------------------------------------------------
  // Toast Notification System
  // --------------------------------------------------------------------------
  showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;

    let iconSvg = '';
    if (type === 'success') {
      iconSvg = '<svg class="toast-icon text-emerald" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg>';
    } else if (type === 'error') {
      iconSvg = '<svg class="toast-icon text-rose" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><line x1="15" y1="9" x2="9" y2="15"></line><line x1="9" y1="9" x2="15" y2="15"></line></svg>';
    } else {
      iconSvg = '<svg class="toast-icon text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg>';
    }

    toast.innerHTML = `
      ${iconSvg}
      <span class="toast-message">${message}</span>
    `;

    container.appendChild(toast);

    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateX(40px)';
      setTimeout(() => toast.remove(), 300);
    }, 3500);
  },

  // --------------------------------------------------------------------------
  // Session & Authentication Flow
  // --------------------------------------------------------------------------
  async checkExistingSession() {
    if (API.sessionId) {
      try {
        this.showDashboard();
        return;
      } catch (err) {
        console.warn('Existing session invalid:', err);
        API.clearSession();
      }
    }

    this.showAuth();
    this.refreshCaptcha();
  },

  showAuth() {
    const authView = document.getElementById('auth-view');
    const dashView = document.getElementById('dashboard-view');
    if (authView) authView.classList.remove('hidden');
    if (dashView) dashView.classList.add('hidden');
  },

  showDashboard() {
    const authView = document.getElementById('auth-view');
    const dashView = document.getElementById('dashboard-view');
    if (authView) authView.classList.add('hidden');
    if (dashView) dashView.classList.remove('hidden');

    this.renderProfile();
    CalendarView.init();
    SummaryView.init();
    if (window.HeatmapView) HeatmapView.init();
    if (window.Simulator) Simulator.init();
    if (window.Wrapped) Wrapped.init();

    // Initial render: prioritize instant load of Summary and Calendar first
    SummaryView.load();
    CalendarView.render();
    
    // Background-load historical semester heatmap
    setTimeout(() => {
      if (window.HeatmapView) HeatmapView.load();
    }, 150);
  },

  renderProfile() {
    const info = API.studentInfo || {};
    const nameEl = document.getElementById('profile-name');
    const usnEl = document.getElementById('profile-usn');
    const degreeEl = document.getElementById('profile-degree');
    const deptEl = document.getElementById('profile-dept');
    const avatarEl = document.getElementById('student-avatar-initials');
    const demoBadgeEl = document.getElementById('demo-indicator-pill');

    const name = info.fname || 'Student User';
    const usn = info.fregno || 'NNM24CS1234';
    const degree = info.fdegree || 'B.Tech';
    const dept = info.fdescpn || 'Computer Science & Eng';

    // Format degree/branch cleanly for responsive UI
    let cleanDegree = degree;
    if (cleanDegree.includes('(')) {
      const match = cleanDegree.match(/\((.*?)\)/);
      if (match) {
        cleanDegree = match[1]
          .replace(/Computer Science & Engineering\s*\[(.*?)\]/i, '$1')
          .replace(/Computer Science & Engineering/i, 'CSE')
          .replace(/Information Science & Engineering/i, 'ISE')
          .replace(/Electronics & Communication/i, 'ECE');
      }
    }
    let cleanDept = dept;
    if (cleanDept.includes('NMAM')) cleanDept = 'NMAMIT';
    else if (cleanDept.includes('[')) cleanDept = cleanDept.split('[')[0].trim();

    if (nameEl) nameEl.textContent = name;
    if (usnEl) usnEl.textContent = usn;
    if (degreeEl) {
      degreeEl.textContent = cleanDegree;
      degreeEl.title = degree;
    }
    if (deptEl) {
      deptEl.textContent = cleanDept;
      deptEl.title = dept;
    }

    if (avatarEl) {
      const parts = name.trim().split(' ');
      const initials = parts.length > 1 ? (parts[0][0] + parts[1][0]).toUpperCase() : name.substring(0, 2).toUpperCase();
      avatarEl.textContent = initials;
    }

    if (demoBadgeEl) {
      if (API.isDemo) {
        demoBadgeEl.classList.remove('hidden');
      } else {
        demoBadgeEl.classList.add('hidden');
      }
    }
  },

  // Auth screen handlers
  bindAuthEvents() {
    const loginForm = document.getElementById('login-form');
    const btnTogglePwd = document.getElementById('btn-toggle-pwd');
    const pwdInput = document.getElementById('login-password');
    const refreshCaptchaBtn = document.getElementById('btn-refresh-captcha');
    const captchaBox = document.getElementById('captcha-box');
    const demoBtn = document.getElementById('btn-demo-login');

    // Toggle password
    if (btnTogglePwd && pwdInput) {
      btnTogglePwd.addEventListener('click', () => {
        const isPassword = pwdInput.type === 'password';
        pwdInput.type = isPassword ? 'text' : 'password';
      });
    }

    // Refresh captcha
    if (refreshCaptchaBtn) {
      refreshCaptchaBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        this.refreshCaptcha();
      });
    }
    if (captchaBox) {
      captchaBox.addEventListener('click', () => this.refreshCaptcha());
    }

    // Demo Mode Button
    if (demoBtn) {
      demoBtn.addEventListener('click', async () => {
        await this.handleDemoLogin();
      });
    }

    // Developer Credits Modal
    const announcementBtn = document.getElementById('btn-announcement');
    const creditsModal = document.getElementById('dev-credits-modal');
    const closeCreditsBtn = document.getElementById('btn-close-credits');
    const creditsBackdrop = document.getElementById('credits-modal-backdrop');

    const openCredits = () => {
      if (creditsModal) creditsModal.classList.remove('hidden');
    };
    const closeCredits = () => {
      if (creditsModal) creditsModal.classList.add('hidden');
    };

    if (announcementBtn) {
      announcementBtn.addEventListener('click', (e) => {
        e.preventDefault();
        openCredits();
      });
    }

    if (closeCreditsBtn) {
      closeCreditsBtn.addEventListener('click', (e) => {
        e.preventDefault();
        closeCredits();
      });
    }

    if (creditsBackdrop) {
      creditsBackdrop.addEventListener('click', closeCredits);
    }

    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && creditsModal && !creditsModal.classList.contains('hidden')) {
        closeCredits();
      }
    });

    // Login submit
    if (loginForm) {
      loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        await this.handleFormLogin();
      });
    }
  },

  async refreshCaptcha() {
    const codeEl = document.getElementById('captcha-code');
    const inputEl = document.getElementById('login-captcha');
    if (codeEl) codeEl.textContent = '...';

    try {
      const res = await API.getCaptcha();
      if (res.success && res.captcha) {
        if (codeEl) codeEl.textContent = res.captcha;
        if (inputEl) inputEl.value = res.captcha; // autofill for convenient portal testing
      }
    } catch (err) {
      const fallback = Math.floor(100000 + Math.random() * 900000).toString();
      if (codeEl) codeEl.textContent = fallback;
      if (inputEl) inputEl.value = fallback;
    }
  },

  async handleFormLogin() {
    const regno = document.getElementById('login-regno').value.trim();
    const passwd = document.getElementById('login-password').value;
    const captcha = document.getElementById('login-captcha').value.trim();
    const univcode = document.getElementById('login-univ') ? document.getElementById('login-univ').value : '049';

    const btn = document.getElementById('btn-login-submit');
    const btnText = btn ? btn.querySelector('.btn-text') : null;
    const btnSpinner = btn ? btn.querySelector('.btn-spinner') : null;

    if (btn) btn.disabled = true;
    if (btnText) btnText.classList.add('hidden');
    if (btnSpinner) btnSpinner.classList.remove('hidden');

    try {
      const res = await API.login({
        regno,
        passwd,
        captcha,
        captchaToken: API.captchaToken,
        univcode,
        isDemo: false
      });
      if (res.success) {
        this.showToast('Login verified! Fetching academic logs...', 'success');
        this.showDashboard();
      }
    } catch (err) {
      this.showToast(err.message || 'Login failed. Please check credentials.', 'error');
      this.refreshCaptcha();
    } finally {
      if (btn) btn.disabled = false;
      if (btnText) btnText.classList.remove('hidden');
      if (btnSpinner) btnSpinner.classList.add('hidden');
    }
  },

  async handleDemoLogin() {
    try {
      this.showToast('Loading Demo Mode with calibrated semester attendance...', 'info');
      const res = await API.login({
        regno: 'NNM24CS1234',
        passwd: 'demo',
        captcha: '123456',
        univcode: '049',
        isDemo: true
      });
      if (res.success) {
        this.showToast('Welcome to Demo Mode (NNM24CS1234)!', 'success');
        this.showDashboard();
      }
    } catch (err) {
      this.showToast('Failed to enter Demo Mode: ' + err.message, 'error');
    }
  },

  // --------------------------------------------------------------------------
  // --------------------------------------------------------------------------
  // Dashboard Handlers & Tab Switching
  // --------------------------------------------------------------------------
  bindDashboardEvents() {
    // Tab switching (Calendar, Subject Planner, Activity Matrix)
    const tabCalBtn = document.getElementById('tab-btn-calendar');
    const tabSumBtn = document.getElementById('tab-btn-summary');
    const tabHeatBtn = document.getElementById('tab-btn-heatmap');

    if (tabCalBtn) {
      tabCalBtn.addEventListener('click', () => this.switchTab('tab-calendar'));
    }
    if (tabSumBtn) {
      tabSumBtn.addEventListener('click', () => this.switchTab('tab-summary'));
    }
    if (tabHeatBtn) {
      tabHeatBtn.addEventListener('click', () => this.switchTab('tab-heatmap'));
    }

    // Refresh sync button
    const refreshBtn = document.getElementById('btn-refresh-data');
    if (refreshBtn) {
      refreshBtn.addEventListener('click', async () => {
        this.showToast('Syncing attendance data from portal...', 'info');
        CalendarView.monthDataCache.clear();
        CalendarView.dayDataCache.clear();
        SummaryView.simulatedDeltas.clear();
        await Promise.all([
          CalendarView.render(),
          SummaryView.load(),
          window.HeatmapView ? HeatmapView.load() : Promise.resolve()
        ]);
        this.showToast('Attendance data synchronized!', 'success');
      });
    }

    // Logout button
    const logoutBtn = document.getElementById('btn-logout');
    if (logoutBtn) {
      logoutBtn.addEventListener('click', async () => {
        await API.logout();
        this.showToast('Signed out successfully.', 'info');
        this.showAuth();
        this.refreshCaptcha();
      });
    }

    // PWA Install Prompt Button & Spotlight Card
    const pwaInstallBtn = document.getElementById('btn-pwa-install');
    if (pwaInstallBtn) {
      pwaInstallBtn.addEventListener('click', () => this.handlePwaInstall());
    }

    const spotlightPwaCard = document.getElementById('spotlight-pwa-card');
    if (spotlightPwaCard) {
      spotlightPwaCard.addEventListener('click', () => this.handlePwaInstall());
      spotlightPwaCard.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          this.handlePwaInstall();
        }
      });
    }

    // PWA Guidance Modal Close & Action Handlers
    const closePwaBtn = document.getElementById('btn-close-pwa-modal');
    const pwaBackdrop = document.getElementById('pwa-modal-backdrop');
    const pwaDirectInstallBtn = document.getElementById('btn-pwa-direct-install');

    if (closePwaBtn) {
      closePwaBtn.addEventListener('click', () => this.closePwaModal());
    }
    if (pwaBackdrop) {
      pwaBackdrop.addEventListener('click', () => this.closePwaModal());
    }
    if (pwaDirectInstallBtn) {
      pwaDirectInstallBtn.addEventListener('click', async () => {
        if (this.deferredPrompt) {
          try {
            await this.deferredPrompt.prompt();
            const choice = await this.deferredPrompt.userChoice;
            if (choice && choice.outcome === 'accepted') {
              this.showToast('NMAMIT Attendance installed! 🚀', 'success');
              this.closePwaModal();
            }
            this.deferredPrompt = null;
          } catch (e) {
            console.warn('Native prompt failed:', e);
            this.showToast('Follow the steps above: Safari (Share → Add to Home Screen) or Chrome (⋮ → Install App)!', 'info');
          }
        } else {
          this.showToast('Follow the steps above: Safari (Share → Add to Home Screen) or Chrome (⋮ → Install App)!', 'info');
        }
      });
    }

    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') {
        this.closePwaModal();
      }
    });
  },

  handlePwaInstall() {
    this.openPwaModal();
  },

  openPwaModal() {
    const modal = document.getElementById('pwa-install-modal');
    if (modal) modal.classList.remove('hidden');
  },

  closePwaModal() {
    const modal = document.getElementById('pwa-install-modal');
    if (modal) modal.classList.add('hidden');
  },

  switchTab(tabId) {
    this.currentTab = tabId;
    const tabs = ['tab-calendar', 'tab-summary', 'tab-heatmap'];
    
    tabs.forEach(tId => {
      const tabEl = document.getElementById(tId);
      const btnEl = document.getElementById(`tab-btn-${tId.replace('tab-', '')}`);
      
      if (tId === tabId) {
        if (tabEl) tabEl.classList.add('active');
        if (btnEl) btnEl.classList.add('active');
      } else {
        if (tabEl) tabEl.classList.remove('active');
        if (btnEl) btnEl.classList.remove('active');
      }
    });

    if (tabId === 'tab-summary') {
      SummaryView.render();
    } else if (tabId === 'tab-heatmap' && window.HeatmapView) {
      HeatmapView.render();
    } else if (tabId === 'tab-calendar') {
      CalendarView.render();
    }
  },

  // --------------------------------------------------------------------------
  // PWA & Offline Network Status Engine
  // --------------------------------------------------------------------------
  deferredPrompt: null,

  initPWA() {
    if ('serviceWorker' in navigator) {
      window.addEventListener('load', () => {
        navigator.serviceWorker.register('/service-worker.js')
          .then(reg => {
            console.log('[PWA] ServiceWorker registered with scope:', reg.scope);
          })
          .catch(err => {
            console.warn('[PWA] ServiceWorker registration failed:', err);
          });
      });
    }

    window.addEventListener('beforeinstallprompt', (e) => {
      e.preventDefault();
      this.deferredPrompt = e;
      const installBtn = document.getElementById('btn-pwa-install');
      if (installBtn) installBtn.classList.remove('hidden');
    });

    window.addEventListener('appinstalled', () => {
      this.deferredPrompt = null;
      const installBtn = document.getElementById('btn-pwa-install');
      if (installBtn) installBtn.classList.add('hidden');
      this.showToast('App installed to your device home screen!', 'success');
    });
  },

  initNetworkStatus() {
    const offlineBadge = document.getElementById('offline-indicator-badge');

    const updateStatus = () => {
      if (navigator.onLine) {
        if (offlineBadge) offlineBadge.classList.add('hidden');
      } else {
        if (offlineBadge) offlineBadge.classList.remove('hidden');
        this.showToast('You are currently offline. Viewing cached classroom records.', 'info');
      }
    };

    window.addEventListener('online', updateStatus);
    window.addEventListener('offline', updateStatus);
    updateStatus();
  }
};

// Start application when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
  App.init();
});

window.App = App;
