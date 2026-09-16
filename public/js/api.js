/**
 * API Service for University Student Portal Proxy
 */

const API = {
  sessionId: localStorage.getItem('att_session_id') || null,
  studentInfo: JSON.parse(localStorage.getItem('att_student_info') || 'null'),
  isDemo: localStorage.getItem('att_is_demo') === 'true',

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

  // Helper request builder
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

      const data = await resp.json();
      if (!resp.ok) {
        throw new Error(data.message || `Request failed with status ${resp.status}`);
      }
      return data;
    } catch (err) {
      console.error(`API Error [${endpoint}]:`, err);
      throw err;
    }
  },

  // 1. Fetch available universities
  async getUniversities() {
    return this.request('/api/universities');
  },

  captchaToken: null,

  // 2. Fetch captcha code
  async getCaptcha() {
    const res = await this.request('/api/captcha');
    if (res && res.captchaToken) {
      this.captchaToken = res.captchaToken;
    }
    return res;
  },

  // 3. Authenticate / Login
  async login({ regno, passwd, captcha, captchaToken, univcode, isDemo }) {
    const payload = {
      regno,
      passwd,
      captcha,
      captchaToken: captchaToken || this.captchaToken,
      univcode,
      isDemo
    };
    const res = await this.request('/api/login', {
      method: 'POST',
      body: JSON.stringify(payload)
    });

    if (res.success && res.sessionId) {
      this.setSession(res.sessionId, res.studentInfo, res.isDemo);
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
