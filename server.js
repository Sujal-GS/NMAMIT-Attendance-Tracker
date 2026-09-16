const express = require('express');
const cors = require('cors');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3050;
const PORTAL_BASE = 'https://studentportal.universitysolutions.in';

app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(express.static(path.join(__dirname, 'public')));

// In-memory session store: token -> { cookies, regno, univcode, studentInfo, isDemo, cache }
const sessions = new Map();

// Helper to make requests with proper headers and cookie handling
async function portalFetch(endpoint, options = {}, session = null) {
  const url = endpoint.startsWith('http') ? endpoint : `${PORTAL_BASE}/${endpoint.replace(/^\//, '')}`;
  const headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Origin': PORTAL_BASE,
    'Referer': `${PORTAL_BASE}/index.html`,
    'X-Requested-With': 'XMLHttpRequest',
    ...(options.headers || {})
  };

  // Attach session cookies if available
  if (session && session.cookies && Object.keys(session.cookies).length > 0) {
    const cookieStr = Object.entries(session.cookies)
      .map(([k, v]) => `${k}=${v}`)
      .join('; ');
    headers['Cookie'] = cookieStr;
  }

  const response = await fetch(url, {
    ...options,
    headers
  });

  // Extract set-cookie headers
  if (session && response.headers.getSetCookie) {
    const rawCookies = response.headers.getSetCookie();
    rawCookies.forEach(c => {
      const parts = c.split(';')[0].split('=');
      if (parts.length >= 2) {
        session.cookies[parts[0].trim()] = parts.slice(1).join('=').trim();
      }
    });
  } else if (session && response.headers.get('set-cookie')) {
    const cookieHeader = response.headers.get('set-cookie');
    const parts = cookieHeader.split(';')[0].split('=');
    if (parts.length >= 2) {
      session.cookies[parts[0].trim()] = parts.slice(1).join('=').trim();
    }
  }

  return response;
}

// Generate token
function generateSessionId() {
  return 'sess_' + Math.random().toString(36).substring(2) + Date.now().toString(36);
}

// --- Demo Data Generator ---
const DEMO_SUBJECTS = [
  { code: '21CS51', name: 'Machine Learning & Neural Networks', conducted: 42, attended: 39 },
  { code: '21CS52', name: 'Database Management Systems', conducted: 38, attended: 35 },
  { code: '21CS53', name: 'Computer Networks & Protocols', conducted: 40, attended: 32 },
  { code: '21CS54', name: 'Design & Analysis of Algorithms', conducted: 44, attended: 34 },
  { code: '21CS55', name: 'Cloud Computing & DevOps', conducted: 36, attended: 32 },
  { code: '21CSL56', name: 'Machine Learning Laboratory', conducted: 14, attended: 14 },
  { code: '21CSL57', name: 'Database Applications Laboratory', conducted: 12, attended: 11 }
];

function getDemoDailyAttendance(dateStr) {
  const d = new Date(dateStr + 'T00:00:00');
  const day = d.getDay(); // 0 = Sun, 6 = Sat
  if (day === 0) return []; // Sunday holiday

  // Saturday has 2 lab periods
  if (day === 6) {
    const isPresent = (d.getDate() % 5 !== 0);
    return [
      {
        fsubcode: '21CSL56',
        fsubname: 'Machine Learning Laboratory',
        fperiod: '1-2',
        fnoclass: '2',
        fpresent: isPresent ? '1' : '0'
      }
    ];
  }

  // Weekdays have 4-5 periods
  const scheduleByDay = [
    [], // Sun
    [ // Mon
      { period: '1', sub: DEMO_SUBJECTS[0] },
      { period: '2', sub: DEMO_SUBJECTS[1] },
      { period: '3', sub: DEMO_SUBJECTS[2] },
      { period: '4', sub: DEMO_SUBJECTS[3] }
    ],
    [ // Tue
      { period: '1', sub: DEMO_SUBJECTS[1] },
      { period: '2', sub: DEMO_SUBJECTS[2] },
      { period: '3', sub: DEMO_SUBJECTS[4] },
      { period: '4', sub: DEMO_SUBJECTS[0] }
    ],
    [ // Wed
      { period: '1', sub: DEMO_SUBJECTS[3] },
      { period: '2', sub: DEMO_SUBJECTS[4] },
      { period: '3', sub: DEMO_SUBJECTS[0] },
      { period: '4', sub: DEMO_SUBJECTS[1] },
      { period: '5', sub: DEMO_SUBJECTS[2] }
    ],
    [ // Thu
      { period: '1', sub: DEMO_SUBJECTS[0] },
      { period: '2', sub: DEMO_SUBJECTS[3] },
      { period: '3', sub: DEMO_SUBJECTS[2] },
      { period: '4', sub: DEMO_SUBJECTS[4] }
    ],
    [ // Fri
      { period: '1', sub: DEMO_SUBJECTS[4] },
      { period: '2', sub: DEMO_SUBJECTS[1] },
      { period: '3', sub: DEMO_SUBJECTS[3] },
      { period: '4-5', sub: DEMO_SUBJECTS[6] }
    ]
  ];

  const dailyClasses = scheduleByDay[day] || [];
  return dailyClasses.map((item, idx) => {
    // Generate realistic attendance (student occasionally misses 1 or 2 classes)
    const seed = (d.getDate() * 7 + idx * 13) % 17;
    const isPresent = seed > 2; // ~85% present rate
    return {
      fsubcode: item.sub.code,
      fsubname: item.sub.name,
      fperiod: item.period,
      fnoclass: item.period.includes('-') ? '2' : '1',
      fpresent: isPresent ? '1' : '0'
    };
  });
}

// Pre-auth session store for linking captcha to PHPSESSID: captchaToken -> { cookies, captcha, createdAt }
const preAuthSessions = new Map();

// ================= API ROUTES =================

// 1. Get Universities list
app.get('/api/universities', async (req, res) => {
  try {
    const resp = await portalFetch('getstatesanduniv.php', { method: 'POST' });
    const data = await resp.json();
    res.json({ success: true, data });
  } catch (err) {
    console.error('Error fetching universities:', err.message);
    // Fallback default list if portal is unreachable
    res.json({
      success: true,
      data: {
        state: [{ fstate: 'KARNATAKA' }],
        university: [
          { fstate: 'KARNATAKA', funivcode: '049', funivname: 'NITTE (DEEMED TO BE UNIVERSITY)' },
          { fstate: 'KARNATAKA', funivcode: '021', funivname: 'VISVESVARAYA TECHNOLOGICAL UNIVERSITY' }
        ]
      }
    });
  }
});

// 2. Fetch Captcha with synchronized session cookie
app.get('/api/captcha', async (req, res) => {
  try {
    const sessionObj = { cookies: {} };
    const resp = await portalFetch('get_captcha.php', { method: 'GET' }, sessionObj);
    const text = await resp.text();
    let data;
    try {
      data = JSON.parse(text);
    } catch {
      data = { captcha: Math.floor(100000 + Math.random() * 900000).toString() };
    }

    const captchaToken = 'cap_' + Math.random().toString(36).substring(2) + Date.now().toString(36);
    preAuthSessions.set(captchaToken, {
      cookies: sessionObj.cookies,
      captcha: data.captcha || '839201',
      createdAt: Date.now()
    });

    // Cleanup expired pre-auth sessions (> 15 mins)
    const expiryCutoff = Date.now() - 15 * 60 * 1000;
    for (const [k, v] of preAuthSessions.entries()) {
      if (v.createdAt < expiryCutoff) preAuthSessions.delete(k);
    }

    res.json({
      success: true,
      captcha: data.captcha || '839201',
      captchaToken
    });
  } catch (err) {
    console.error('Captcha fetch error:', err.message);
    const randomCaptcha = Math.floor(100000 + Math.random() * 900000).toString();
    res.json({ success: true, captcha: randomCaptcha, captchaToken: '' });
  }
});

// 3. Login
app.post('/api/login', async (req, res) => {
  const { regno, passwd, captcha, captchaToken, univcode = '049', isDemo } = req.body;

  if (isDemo) {
    const sessionId = generateSessionId();
    sessions.set(sessionId, {
      cookies: {},
      regno: regno || 'NNM24CS1234',
      univcode: univcode || '049',
      studentInfo: {
        fregno: regno || 'NNM24CS1234',
        fname: 'Rohit Shenoy',
        fdegree: 'B.Tech',
        fdescpn: 'Computer Science & Engineering',
        fexamname: 'Semester 5 Examination 2026'
      },
      isDemo: true,
      cache: new Map()
    });

    return res.json({
      success: true,
      sessionId,
      studentInfo: sessions.get(sessionId).studentInfo,
      isDemo: true
    });
  }

  if (!regno || !passwd) {
    return res.status(400).json({ success: false, message: 'Registration number / Mobile number and password are required.' });
  }

  try {
    const sessionId = generateSessionId();
    const sessionObj = {
      cookies: {},
      regno: regno.trim(),
      univcode: univcode.trim(),
      studentInfo: null,
      isDemo: false,
      cache: new Map()
    };

    let finalCaptcha = captcha;

    // Use preserved preAuth session cookies if available
    if (captchaToken && preAuthSessions.has(captchaToken)) {
      const preAuth = preAuthSessions.get(captchaToken);
      sessionObj.cookies = { ...preAuth.cookies };
      if (!finalCaptcha) {
        finalCaptcha = preAuth.captcha;
      }
      preAuthSessions.delete(captchaToken);
    }

    // If session has no cookies yet (or captcha missing), fetch a fresh captcha synchronized with PHPSESSID
    if (!sessionObj.cookies.PHPSESSID) {
      try {
        const cResp = await portalFetch('get_captcha.php', { method: 'GET' }, sessionObj);
        const cText = await cResp.text();
        const cData = JSON.parse(cText);
        finalCaptcha = cData.captcha;
      } catch (err) {
        console.warn('Fallback captcha fetch warning:', err.message);
        if (!finalCaptcha) finalCaptcha = '123456';
      }
    }

    const cleanedRegno = regno.replace(/["'& ]/g, '');
    const cleanedPasswd = passwd.replace(/["'& ]/g, '');

    // The student portal formats parameters as: &regno=...&passwd=...&captcha=...
    const bodyString = `&regno=${encodeURIComponent(cleanedRegno)}&passwd=${encodeURIComponent(cleanedPasswd)}&captcha=${encodeURIComponent(finalCaptcha)}`;

    const signinResp = await portalFetch('signin.php', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
      },
      body: bodyString
    }, sessionObj);

    const signinText = await signinResp.text();
    let signinData;
    try {
      signinData = JSON.parse(signinText);
    } catch {
      signinData = { error_code: -1, msg: signinText || 'Invalid response from portal' };
    }

    if (parseInt(signinData.error_code, 10) !== 0) {
      return res.status(401).json({
        success: false,
        message: signinData.msg || 'Invalid mobile number or password!'
      });
    }

    // Fetch student profile using src/profile.php
    try {
      const profResp = await portalFetch('src/profile.php', { method: 'POST' }, sessionObj);
      const profText = await profResp.text();
      const profData = JSON.parse(profText);
      if (profData && (profData.status === 'success' || profData.strRegno)) {
        sessionObj.regno = profData.strRegno || sessionObj.regno;
        sessionObj.studentInfo = {
          fregno: profData.strRegno || sessionObj.regno,
          fname: profData.fname || 'Student User',
          fdegree: profData.degree || profData.fdegree || 'Engineering',
          fdescpn: profData.college || 'NMAM Institute of Technology',
          fexamname: profData.strSemester || profData.fexamname || 'Academic Year',
          photo: profData.photo || profData.photopath || ''
        };
        if (profData.funivcode) {
          sessionObj.univcode = profData.funivcode;
        }
      }
    } catch (profErr) {
      console.warn('Profile fetch warning:', profErr.message);
    }

    // Fallback if studentInfo still not populated
    if (!sessionObj.studentInfo) {
      sessionObj.studentInfo = {
        fregno: sessionObj.regno,
        fname: 'Student User',
        fdegree: 'Engineering',
        fdescpn: 'Undergraduate Program',
        fexamname: 'Academic Year'
      };
    }

    sessions.set(sessionId, sessionObj);

    res.json({
      success: true,
      sessionId,
      studentInfo: sessionObj.studentInfo,
      isDemo: false
    });
  } catch (err) {
    console.error('Login error:', err);
    res.status(500).json({ success: false, message: 'Portal connection failed: ' + err.message });
  }
});

// Middleware to resolve active session
function authMiddleware(req, res, next) {
  const sessionId = req.headers['x-session-id'] || req.query.sessionId;
  if (!sessionId || !sessions.has(sessionId)) {
    return res.status(401).json({ success: false, message: 'Session expired or invalid. Please log in.' });
  }
  req.userSession = sessions.get(sessionId);
  next();
}

// 4. Student Info
app.get('/api/student-info', authMiddleware, (req, res) => {
  res.json({
    success: true,
    data: req.userSession.studentInfo,
    univcode: req.userSession.univcode
  });
});

// 5. Attendance Summary (Subject-wise)
app.get('/api/attendance-summary', authMiddleware, async (req, res) => {
  const session = req.userSession;
  const date = req.query.date || new Date().toISOString().split('T')[0];

  if (session.isDemo) {
    return res.json({
      success: true,
      error_code: 0,
      data: DEMO_SUBJECTS.map(s => ({
        fsubcode: s.code,
        fsubname: s.name,
        conducted: s.conducted.toString(),
        attended: s.attended.toString()
      }))
    });
  }

  try {
    const params = new URLSearchParams();
    params.append('date', date);

    const resp = await portalFetch(
      `app.php?a=viewAttendanceDetsummary&univcode=${session.univcode}`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
        },
        body: params.toString()
      },
      session
    );

    const text = await resp.text();
    let data;
    try {
      data = JSON.parse(text);
    } catch {
      return res.status(502).json({ success: false, message: 'Malformed JSON from portal: ' + text });
    }

    res.json({
      success: true,
      error_code: data.error_code,
      data: data.data || []
    });
  } catch (err) {
    console.error('Error fetching attendance summary:', err);
    res.status(500).json({ success: false, message: err.message });
  }
});

// 6. Attendance for a Single Date
app.post('/api/attendance-daily', authMiddleware, async (req, res) => {
  const session = req.userSession;
  const { date } = req.body;

  if (!date) {
    return res.status(400).json({ success: false, message: 'Date is required (YYYY-MM-DD).' });
  }

  if (session.isDemo) {
    const demoClasses = getDemoDailyAttendance(date);
    return res.json({
      success: true,
      error_code: 0,
      date,
      data: demoClasses
    });
  }

  // Check cache
  if (session.cache && session.cache.has(date)) {
    return res.json({
      success: true,
      error_code: 0,
      date,
      data: session.cache.get(date),
      cached: true
    });
  }

  try {
    const params = new URLSearchParams();
    params.append('date', date);
    params.append('regno', session.regno);

    const resp = await portalFetch(
      `app.php?a=viewAttendanceDet&univcode=${session.univcode}`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
        },
        body: params.toString()
      },
      session
    );

    const text = await resp.text();
    let data;
    try {
      data = JSON.parse(text);
    } catch {
      return res.status(502).json({ success: false, message: 'Malformed JSON from portal: ' + text });
    }

    const classesList = (data.error_code === 0 && Array.isArray(data.data)) ? data.data : [];
    if (session.cache) {
      session.cache.set(date, classesList);
    }

    res.json({
      success: true,
      error_code: data.error_code || 0,
      date,
      data: classesList
    });
  } catch (err) {
    console.error(`Error fetching daily attendance for ${date}:`, err);
    res.status(500).json({ success: false, message: err.message });
  }
});

// 7. Attendance for an entire month (Calendar batch helper)
app.post('/api/attendance-month', authMiddleware, async (req, res) => {
  const session = req.userSession;
  const { year, month } = req.body; // month is 1-12

  if (!year || !month) {
    return res.status(400).json({ success: false, message: 'Year and month are required.' });
  }

  const daysInMonth = new Date(year, month, 0).getDate();
  const dateList = [];
  for (let d = 1; d <= daysInMonth; d++) {
    const dayStr = String(d).padStart(2, '0');
    const monthStr = String(month).padStart(2, '0');
    dateList.push(`${year}-${monthStr}-${dayStr}`);
  }

  const results = {};

  if (session.isDemo) {
    for (const dateStr of dateList) {
      const classes = getDemoDailyAttendance(dateStr);
      const conducted = classes.reduce((sum, c) => sum + parseInt(c.fnoclass || '1', 10), 0);
      const attended = classes.reduce((sum, c) => {
        const isPres = c.fpresent === '1' || c.fpresent === 1 || String(c.fpresent).toLowerCase() === 'present';
        return sum + (isPres ? parseInt(c.fnoclass || '1', 10) : 0);
      }, 0);
      results[dateStr] = {
        conducted,
        attended,
        classes
      };
    }
    return res.json({ success: true, monthData: results });
  }

  // For live portal: fetch concurrently in small chunks to avoid overload
  const chunkSize = 5;
  for (let i = 0; i < dateList.length; i += chunkSize) {
    const chunk = dateList.slice(i, i + chunkSize);
    await Promise.all(chunk.map(async (dateStr) => {
      // Check cache first
      if (session.cache && session.cache.has(dateStr)) {
        const classes = session.cache.get(dateStr);
        const conducted = classes.reduce((sum, c) => sum + parseInt(c.fnoclass || '1', 10), 0);
        const attended = classes.reduce((sum, c) => {
          const isPres = c.fpresent === '1' || c.fpresent === 1 || String(c.fpresent).trim().toUpperCase() === 'P' || String(c.fpresent).toLowerCase() === 'present';
          return sum + (isPres ? parseInt(c.fnoclass || '1', 10) : 0);
        }, 0);
        results[dateStr] = { conducted, attended, classes };
        return;
      }

      try {
        const params = new URLSearchParams();
        params.append('date', dateStr);
        params.append('regno', session.regno);

        const resp = await portalFetch(
          `app.php?a=viewAttendanceDet&univcode=${session.univcode}`,
          {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
            body: params.toString()
          },
          session
        );
        const text = await resp.text();
        const data = JSON.parse(text);
        const classes = (data.error_code === 0 && Array.isArray(data.data)) ? data.data : [];
        if (session.cache) session.cache.set(dateStr, classes);

        const conducted = classes.reduce((sum, c) => sum + parseInt(c.fnoclass || '1', 10), 0);
        const attended = classes.reduce((sum, c) => {
          const isPres = c.fpresent === '1' || c.fpresent === 1 || String(c.fpresent).trim().toUpperCase() === 'P' || String(c.fpresent).toLowerCase() === 'present';
          return sum + (isPres ? parseInt(c.fnoclass || '1', 10) : 0);
        }, 0);
        results[dateStr] = { conducted, attended, classes };
      } catch (e) {
        results[dateStr] = { conducted: 0, attended: 0, classes: [], error: true };
      }
    }));
  }

  res.json({ success: true, monthData: results });
});

// 8. Logout
app.post('/api/logout', (req, res) => {
  const sessionId = req.headers['x-session-id'] || req.body.sessionId;
  if (sessionId) {
    sessions.delete(sessionId);
  }
  res.json({ success: true, message: 'Logged out successfully.' });
});

// Catch-all: serve index.html for SPA
app.get('*', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

app.listen(PORT, () => {
  console.log(`🚀 Attendance Tracker server running on http://localhost:${PORT}`);
});
