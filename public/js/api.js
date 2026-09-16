/**
 * Global HTML Escaper for Context-Aware XSS Prevention
 */
function escapeHtml(str) {
  if (typeof str !== 'string') return str == null ? '' : String(str);
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}
window.escapeHtml = escapeHtml;

const API = {
  sessionId: localStorage.getItem('att_session_id') || null,
  studentInfo: JSON.parse(localStorage.getItem('att_student_info') || 'null'),
  isDemo: localStorage.getItem('att_is_demo') === 'true',
  captchaToken: localStorage.getItem('att_captcha_token') || null,

  // Store session in localStorage
  setSession(sessionId, studentInfo, isDemo = false) {
    this.sessionId = sessionId;
    this.studentInfo = studentInfo;
    this.isDemo = isDemo;
    localStorage.setItem('att_session_id', sessionId);
    localStorage.setItem('att_student_info', JSON.stringify(studentInfo));
    localStorage.setItem('att_is_demo', isDemo ? 'true' : 'false');
  },

  clearSession() {
    this.sessionId = null;
    this.studentInfo = null;
    this.isDemo = false;
    localStorage.removeItem('att_session_id');
    localStorage.removeItem('att_student_info');
    localStorage.removeItem('att_is_demo');
  },

  // Helper request builder with robust non-JSON & 401 handling
  async request(endpoint, options = {}) {
    const headers = {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    };

    if (this.sessionId) {
      headers['x-session-id'] = this.sessionId;
    }

    try {
      const resp = await fetch(endpoint, {
        ...options,
        headers
      });

      const text = await resp.text();
      let data;
      try {
        data = JSON.parse(text);
      } catch (parseErr) {
        if (!resp.ok) {
          throw new Error(`Server returned error ${resp.status}: ${resp.statusText || 'Unable to complete request'}`);
        }
        throw new Error('Invalid response format from server');
      }

      if (resp.status === 401) {
        this.clearSession();
        if (window.App && typeof window.App.showAuth === 'function') {
          window.App.showAuth();
          if (typeof window.App.showToast === 'function') {
            window.App.showToast('Session expired. Please log in again.', 'error');
          }
          if (typeof window.App.refreshCaptcha === 'function') {
            window.App.refreshCaptcha();
          }
        }
        throw new Error(data.message || 'Session expired. Please log in.');
      }

      if (!resp.ok) {
        throw new Error(data.message || `Request failed with status ${resp.status}`);
      }
      return data;
    } catch (err) {
      console.error(`API Error [${endpoint}]:`, err.message || err);
      throw err;
    }
  },

  // 1. Fetch available universities
  async getUniversities() {
    return this.request('/api/universities');
  },

  // 2. Fetch captcha code
  async getCaptcha() {
    const res = await this.request('/api/captcha');
    if (res && res.captchaToken) {
      this.captchaToken = res.captchaToken;
      localStorage.setItem('att_captcha_token', res.captchaToken);
    }
    return res;
  },

  // 3. Authenticate / Login
  async login({ regno, passwd, captcha, captchaToken, univcode, isDemo }) {
    const payload = {
      regno,
      passwd,
      captcha,
      captchaToken: captchaToken || this.captchaToken || localStorage.getItem('att_captcha_token'),
      univcode: univcode || '049',
      isDemo
    };
    const res = await this.request('/api/login', {
      method: 'POST',
      body: JSON.stringify(payload)
    });

    if (res.success && res.sessionId) {
      this.setSession(res.sessionId, res.studentInfo, res.isDemo);
      localStorage.removeItem('att_captcha_token');
      this.captchaToken = null;
    }
    return res;
  },

  // 4. Logout
  async logout() {
    try {
      if (this.sessionId) {
        await this.request('/api/logout', {
          method: 'POST',
          body: JSON.stringify({ sessionId: this.sessionId })
        });
      }
    } finally {
      this.clearSession();
    }
  },

  // 5. Fetch student profile info
  async getStudentInfo() {
    return this.request('/api/student-info');
  },

  // 6. Fetch overall subject-wise summary
  async getAttendanceSummary(dateStr) {
    const query = dateStr ? `?date=${encodeURIComponent(dateStr)}` : '';
    return this.request(`/api/attendance-summary${query}`);
  },

  // 7. Fetch timetable & period attendance for a single day
  async getDailyAttendance(dateStr) {
    return this.request('/api/attendance-daily', {
      method: 'POST',
      body: JSON.stringify({ date: dateStr })
    });
  },

  // 8. Fetch month-wide aggregate data for calendar rendering
  async getMonthAttendance(year, month) {
    return this.request('/api/attendance-month', {
      method: 'POST',
      body: JSON.stringify({ year, month })
    });
  }
};

window.API = API;
