const express = require('express');
const cors = require('cors');
const path = require('path');
const crypto = require('crypto');

const app = express();
app.set('trust proxy', 1);
const PORT = process.env.PORT || 3050;
const PORTAL_BASE = 'https://studentportal.universitysolutions.in';

// -----------------------------------------------------------------------------
// 1. Production Secrets & Stateless Session Encryption (AES-256-GCM)
// -----------------------------------------------------------------------------
let SESSION_SECRET = process.env.SESSION_SECRET;
if (!SESSION_SECRET) {
  if (process.env.NODE_ENV === 'production') {
    // In production, generate an ephemeral cryptographically strong key if none provided
    SESSION_SECRET = crypto.randomBytes(32).toString('hex');
    console.warn('⚠️ [SECURITY WARNING] SESSION_SECRET environment variable is missing in production. Generated ephemeral random secret for this runtime instance.');
  } else {
    SESSION_SECRET = 'nmamit_attendance_secret_dev_2026_x9k2p_nitte';
  }
}
const ENCRYPTION_KEY = crypto.createHash('sha256').update(SESSION_SECRET).digest();
const ALGORITHM = 'aes-256-gcm';

// Token lifetimes
const SESSION_MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000; // 7 days max lifetime
const CAPTCHA_MAX_AGE_MS = 15 * 60 * 1000;           // 15 minutes max lifetime

function encryptPayload(prefix, payload) {
  try {
    const iv = crypto.randomBytes(12);
    const cipher = crypto.createCipheriv(ALGORITHM, ENCRYPTION_KEY, iv);
    const dataWithTs = JSON.stringify({
      ...payload,
      _t: Date.now()
    });
    let encrypted = cipher.update(dataWithTs, 'utf8', 'base64');
    encrypted += cipher.final('base64');
    const authTag = cipher.getAuthTag().toString('base64');
    return `${prefix}_${iv.toString('base64')}.${authTag}.${encrypted}`;
  } catch (err) {
    console.error(`[Crypto] Encryption error for ${prefix}:`, err.message);
    return null;
  }
}

function decryptPayload(prefix, token, maxAgeMs = null) {
  try {
    if (!token || typeof token !== 'string') return null;
    const tokenPrefix = `${prefix}_`;
    if (!token.startsWith(tokenPrefix)) return null;
    const raw = token.slice(tokenPrefix.length);
    const [ivB64, authTagB64, encrypted] = raw.split('.');
    if (!ivB64 || !authTagB64 || !encrypted) return null;

    const iv = Buffer.from(ivB64, 'base64');
    const authTag = Buffer.from(authTagB64, 'base64');
    const decipher = crypto.createDecipheriv(ALGORITHM, ENCRYPTION_KEY, iv);
    decipher.setAuthTag(authTag);
    let decrypted = decipher.update(encrypted, 'base64', 'utf8');
    decrypted += decipher.final('utf8');
    const parsed = JSON.parse(decrypted);

    // Enforce cryptographic token expiration
    if (maxAgeMs && parsed._t) {
      if (Date.now() - parsed._t > maxAgeMs) {
        return null; // Token expired
      }
    }
    return parsed;
  } catch (err) {
    return null;
  }
}

function encryptSession(payload) {
  return encryptPayload('sess', payload);
}

function decryptSession(token) {
  return decryptPayload('sess', token, SESSION_MAX_AGE_MS);
}

function encryptCaptchaToken(payload) {
  return encryptPayload('cap', payload);
}

function decryptCaptchaToken(token) {
  return decryptPayload('cap', token, CAPTCHA_MAX_AGE_MS);
}

// -----------------------------------------------------------------------------
// 2. HTTP Security Headers (Defense in Depth)
// -----------------------------------------------------------------------------
app.disable('x-powered-by');

app.use((req, res, next) => {
  // Prevent MIME-sniffing
  res.setHeader('X-Content-Type-Options', 'nosniff');
  // Prevent clickjacking via iframes
  res.setHeader('X-Frame-Options', 'SAMEORIGIN');
  // Referrer privacy
  res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
  // Disable unused browser hardware APIs
  res.setHeader('Permissions-Policy', 'geolocation=(), microphone=(), camera=()');
  // Legacy XSS filter protection
  res.setHeader('X-XSS-Protection', '1; mode=block');
  // Isolation policies
  res.setHeader('Cross-Origin-Opener-Policy', 'same-origin');
  res.setHeader('Cross-Origin-Resource-Policy', 'same-origin');

  // Enforce HSTS on production domains
  if (process.env.NODE_ENV === 'production' || req.headers['x-forwarded-proto'] === 'https') {
    res.setHeader('Strict-Transport-Security', 'max-age=31536000; includeSubDomains; preload');
  }

  // Strict Content-Security-Policy
  res.setHeader(
    'Content-Security-Policy',
    [
      "default-src 'self'",
      "script-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdn.jsdelivr.net https://va.vercel-scripts.com",
      "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
      "font-src 'self' https://fonts.gstatic.com data:",
      "img-src 'self' data: blob: https:",
      "connect-src 'self' https://studentportal.universitysolutions.in https://vitals.vercel-insights.com https://va.vercel-scripts.com",
      "frame-ancestors 'self'",
      "base-uri 'self'",
      "form-action 'self'"
    ].join('; ')
  );

  next();
});

// -----------------------------------------------------------------------------
// 3. CORS Configuration (Restricted Origin Whitelist)
// -----------------------------------------------------------------------------
const allowedOrigins = [
  'http://localhost:3000',
  'http://localhost:3050',
  'http://localhost:5173',
  'http://127.0.0.1:3000',
  'http://127.0.0.1:3050',
  'http://127.0.0.1:5173'
];

if (process.env.ALLOWED_ORIGINS) {
  process.env.ALLOWED_ORIGINS.split(',').forEach(o => {
    const trimmed = o.trim();
    if (trimmed) allowedOrigins.push(trimmed);
  });
}

const corsOptions = {
  origin: (origin, callback) => {
    // Allow requests with no origin (mobile apps, curl, server-to-server, same-origin)
    if (!origin) return callback(null, true);

    // Allow localhost/127.0.0.1 development
    if (/^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(origin)) {
      return callback(null, true);
    }

    // Allow Vercel preview & production deployments (*.vercel.app)
    if (/^https:\/\/.*\.vercel\.app$/.test(origin)) {
      return callback(null, true);
    }

    if (allowedOrigins.includes(origin)) {
      return callback(null, true);
    }

    return callback(new Error('Cross-Origin Request Blocked by Security Policy'));
  },
  credentials: true,
  methods: ['GET', 'POST', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'x-session-id', 'Authorization']
};

app.use(cors(corsOptions));

// -----------------------------------------------------------------------------
// 4. Rate Limiting Middleware (Sliding Window Memory-Bounded)
// -----------------------------------------------------------------------------
const rateLimits = new Map();

function checkRateLimit(key, maxRequests, windowMs) {
  const now = Date.now();
  const bucket = (rateLimits.get(key) || []).filter(t => now - t < windowMs);
  if (bucket.length >= maxRequests) {
    return false;
  }
  bucket.push(now);
  rateLimits.set(key, bucket);
  return true;
}

function getClientIp(req) {
  if (req.ip) return req.ip;
  const forwarded = req.headers['x-forwarded-for'];
  if (typeof forwarded === 'string' && forwarded.length > 0) {
    const candidate = forwarded.split(',')[0].trim();
    if (/^[a-fA-F0-9:.]+$/.test(candidate)) {
      return candidate;
    }
  }
  return req.socket.remoteAddress || '127.0.0.1';
}

// Login rate limiting (15 attempts / 15 min per IP)
function loginRateLimitMiddleware(req, res, next) {
  const ip = getClientIp(req);
  if (!checkRateLimit(`login_${ip}`, 15, 15 * 60 * 1000)) {
    return res.status(429).json({
      success: false,
      message: 'Too many login attempts. Please wait 15 minutes before trying again.'
    });
  }
  next();
}

// Captcha rate limiting (30 attempts / 5 min per IP)
function captchaRateLimitMiddleware(req, res, next) {
  const ip = getClientIp(req);
  if (!checkRateLimit(`captcha_${ip}`, 30, 5 * 60 * 1000)) {
    return res.status(429).json({
      success: false,
      message: 'Too many captcha requests. Please wait a moment.'
    });
  }
  next();
}

// General API rate limiting (120 requests / 1 min per IP)
app.use('/api/', (req, res, next) => {
  const ip = getClientIp(req);
  if (!checkRateLimit(`api_${ip}`, 120, 60 * 1000)) {
    return res.status(429).json({
      success: false,
      message: 'Rate limit exceeded. Please slow down your requests.'
    });
  }
  next();
});

// -----------------------------------------------------------------------------
// 5. Payload Limits & Safe Body Parsing
// -----------------------------------------------------------------------------
app.use(express.json({ limit: '15kb' }));
app.use(express.urlencoded({ extended: true, limit: '15kb' }));
app.use(express.static(path.join(__dirname, 'public')));

// In-memory session caches for warm containers / local dev
const sessions = new Map();
const preAuthSessions = new Map();

// Periodic prune interval to prevent memory growth (every 10 minutes)
setInterval(() => {
  const now = Date.now();
  const sessionCutoff = now - 24 * 60 * 60 * 1000; // 24 hours
  const preAuthCutoff = now - 15 * 60 * 1000;      // 15 mins
  const rateLimitCutoff = now - 15 * 60 * 1000;

  for (const [k, v] of sessions.entries()) {
    if (v.createdAt && v.createdAt < sessionCutoff) sessions.delete(k);
  }
  for (const [k, v] of preAuthSessions.entries()) {
    if (v.createdAt && v.createdAt < preAuthCutoff) preAuthSessions.delete(k);
  }
  for (const [k, timestamps] of rateLimits.entries()) {
    const filtered = timestamps.filter(t => now - t < rateLimitCutoff);
    if (filtered.length === 0) rateLimits.delete(k);
    else rateLimits.set(k, filtered);
  }
}, 10 * 60 * 1000);

// Helper to extract cookies across all Node runtimes
function extractCookies(response, session) {
  if (!session || !session.cookies) return;

  if (typeof response.headers.getSetCookie === 'function') {
    const rawCookies = response.headers.getSetCookie();
    if (Array.isArray(rawCookies) && rawCookies.length > 0) {
      rawCookies.forEach(c => {
        const first = c.split(';')[0];
        const eqIdx = first.indexOf('=');
        if (eqIdx !== -1) {
          const key = first.slice(0, eqIdx).trim();
          const val = first.slice(eqIdx + 1).trim();
          if (key) session.cookies[key] = val;
        }
      });
      return;
    }
  }

  const cookieHeader = response.headers.get('set-cookie');
  if (cookieHeader) {
    cookieHeader.split(/,(?=[^;]+=[^;]+)/).forEach(c => {
      const first = c.split(';')[0];
      const eqIdx = first.indexOf('=');
      if (eqIdx !== -1) {
        const key = first.slice(0, eqIdx).trim();
        const val = first.slice(eqIdx + 1).trim();
        if (key) session.cookies[key] = val;
      }
    });
  }
}

// -----------------------------------------------------------------------------
// 6. Hardened Portal Request Bridge (SSRF-Protected)
// -----------------------------------------------------------------------------
async function portalFetch(endpoint, options = {}, session = null, timeoutMs = 8000) {
  // Ensure endpoint strictly resolves to PORTAL_BASE (SSRF Protection)
  let cleanEndpoint = endpoint.replace(/^\/+/, '');
  if (cleanEndpoint.startsWith('http://') || cleanEndpoint.startsWith('https://')) {
    const parsed = new URL(cleanEndpoint);
    if (parsed.origin !== PORTAL_BASE) {
      throw new Error('Forbidden outbound request: Origin restricted to official university portal.');
    }
    cleanEndpoint = parsed.pathname.replace(/^\/+/, '') + parsed.search;
  }

  const url = `${PORTAL_BASE}/${cleanEndpoint}`;
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

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const response = await fetch(url, {
      ...options,
      headers,
      signal: controller.signal
    });
    clearTimeout(timeoutId);
    extractCookies(response, session);
    return response;
  } catch (err) {
    clearTimeout(timeoutId);
    throw err;
  }
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

// ================= API ROUTES =================

// 1. Get Universities list
app.get('/api/universities', async (req, res) => {
  try {
    const resp = await portalFetch('getstatesanduniv.php', { method: 'POST' });
    const data = await resp.json();
    res.json({ success: true, data });
  } catch (err) {
    console.error('Error fetching universities:', err.message);
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

// 2. Fetch Captcha with synchronized session cookie (Rate-limited & Monitored)
app.get('/api/captcha', captchaRateLimitMiddleware, async (req, res) => {
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

    const captchaVal = (data && data.captcha) ? String(data.captcha).slice(0, 10) : '839201';
    const captchaToken = encryptCaptchaToken({
      cookies: sessionObj.cookies,
      captcha: captchaVal,
      createdAt: Date.now()
    }) || ('cap_' + crypto.randomBytes(16).toString('hex'));

    preAuthSessions.set(captchaToken, {
      cookies: sessionObj.cookies,
      captcha: captchaVal,
      createdAt: Date.now()
    });

    res.json({
      success: true,
      captcha: captchaVal,
      captchaToken
    });
  } catch (err) {
    console.error('[Security] Captcha fetch error:', err.message);
    const fallbackCaptcha = Math.floor(100000 + Math.random() * 900000).toString();
    res.json({ success: true, captcha: fallbackCaptcha, captchaToken: '' });
  }
});

// 3. Login Endpoint (Hardened with strict validation, Rate Limiting, and URLSearchParams)
app.post('/api/login', loginRateLimitMiddleware, async (req, res) => {
  const { regno, passwd, captcha, captchaToken, univcode = '049', isDemo } = req.body;

  // Validate Demo Login
  if (isDemo) {
    const demoRegno = typeof regno === 'string' && regno.trim() ? regno.trim().slice(0, 30) : 'NNM24CS1234';
    const cleanUniv = typeof univcode === 'string' && /^[a-zA-Z0-9_-]{1,10}$/.test(univcode.trim()) ? univcode.trim() : '049';

    const studentInfo = {
      fregno: demoRegno,
      fname: 'Rohit Shenoy',
      fdegree: 'B.Tech',
      fdescpn: 'Computer Science & Engineering',
      fexamname: 'Semester 5 Examination 2026'
    };

    const sessionPayload = {
      cookies: {},
      regno: demoRegno,
      univcode: cleanUniv,
      studentInfo,
      isDemo: true,
      createdAt: Date.now()
    };

    const sessionId = encryptSession(sessionPayload) || ('sess_' + crypto.randomBytes(16).toString('hex'));
    sessions.set(sessionId, { ...sessionPayload, cache: new Map() });

    return res.json({
      success: true,
      sessionId,
      studentInfo,
      isDemo: true
    });
  }

  // Strict Input Validation & Type Checking
  if (!regno || typeof regno !== 'string' || !passwd || typeof passwd !== 'string') {
    return res.status(400).json({
      success: false,
      message: 'Valid registered mobile number and password are required.'
    });
  }

  const cleanRegno = regno.trim();
  const cleanPasswd = passwd;
  const cleanUniv = typeof univcode === 'string' && /^[a-zA-Z0-9_-]{1,10}$/.test(univcode.trim()) ? univcode.trim() : '049';

  if (cleanRegno.length < 3 || cleanRegno.length > 30) {
    return res.status(400).json({ success: false, message: 'Invalid mobile number length.' });
  }
  if (cleanPasswd.length < 1 || cleanPasswd.length > 128) {
    return res.status(400).json({ success: false, message: 'Invalid password format.' });
  }

  try {
    const sessionObj = {
      cookies: {},
      regno: cleanRegno,
      univcode: cleanUniv,
      studentInfo: null,
      isDemo: false,
      cache: new Map()
    };

    let finalCaptcha = typeof captcha === 'string' ? captcha.trim() : '';

    // First try decrypting stateless captcha token
    if (captchaToken && typeof captchaToken === 'string') {
      const decryptedCaptcha = decryptCaptchaToken(captchaToken);
      if (decryptedCaptcha && decryptedCaptcha.cookies) {
        sessionObj.cookies = { ...decryptedCaptcha.cookies };
        if (!finalCaptcha) finalCaptcha = decryptedCaptcha.captcha;
      } else if (preAuthSessions.has(captchaToken)) {
        const preAuth = preAuthSessions.get(captchaToken);
        sessionObj.cookies = { ...preAuth.cookies };
        if (!finalCaptcha) finalCaptcha = preAuth.captcha;
        preAuthSessions.delete(captchaToken);
      }
    }

    // If session has no cookies yet, fetch fresh captcha synchronized with PHPSESSID
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

    // Safely encode POST payload via URLSearchParams (eliminates delimiter injection)
    const signinParams = new URLSearchParams();
    signinParams.append('regno', cleanRegno);
    signinParams.append('passwd', cleanPasswd);
    signinParams.append('captcha', finalCaptcha || '123456');

    const signinResp = await portalFetch('signin.php', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
      },
      body: signinParams.toString()
    }, sessionObj);

    const signinText = await signinResp.text();
    let signinData;
    try {
      signinData = JSON.parse(signinText);
    } catch {
      signinData = { error_code: -1, msg: 'Unexpected portal authentication response.' };
    }

    if (parseInt(signinData.error_code, 10) !== 0) {
      return res.status(401).json({
        success: false,
        message: (signinData && typeof signinData.msg === 'string') ? signinData.msg : 'Invalid mobile number or password!'
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
        if (profData.funivcode && /^[a-zA-Z0-9_-]{1,10}$/.test(profData.funivcode)) {
          sessionObj.univcode = profData.funivcode;
        }
      }
    } catch (profErr) {
      console.warn('Profile fetch warning:', profErr.message);
    }

    if (!sessionObj.studentInfo) {
      sessionObj.studentInfo = {
        fregno: sessionObj.regno,
        fname: 'Student User',
        fdegree: 'Engineering',
        fdescpn: 'Undergraduate Program',
        fexamname: 'Academic Year'
      };
    }

    const sessionPayload = {
      cookies: sessionObj.cookies,
      regno: sessionObj.regno,
      univcode: sessionObj.univcode,
      studentInfo: sessionObj.studentInfo,
      isDemo: false,
      createdAt: Date.now()
    };

    const sessionId = encryptSession(sessionPayload) || ('sess_' + crypto.randomBytes(16).toString('hex'));
    sessions.set(sessionId, sessionObj);

    res.json({
      success: true,
      sessionId,
      studentInfo: sessionObj.studentInfo,
      isDemo: false
    });
  } catch (err) {
    console.error('[Security] Login error:', err.message);
    res.status(500).json({ success: false, message: 'Portal connection failed. Please check your credentials and try again.' });
  }
});

// Middleware to resolve active session (Supports stateless tokens across Vercel Lambdas)
// Helper to detect expired/invalid university portal sessions
function isPortalSessionExpired(data, rawText = '') {
  if (rawText && typeof rawText === 'string') {
    const lower = rawText.toLowerCase();
    if (
      lower.includes('session expired') ||
      lower.includes('session timed out') ||
      lower.includes('session timeout') ||
      lower.includes('please login') ||
      lower.includes('invalid session') ||
      lower.includes('not logged in') ||
      lower.includes('signin.php') ||
      lower.includes('window.location="index.html"') ||
      lower.includes('window.location=\'index.html\'')
    ) {
      return true;
    }
  }

  if (data && typeof data === 'object') {
    const msg = String(data.msg || data.message || '').toLowerCase();
    // Only flag as expired if message explicitly mentions session expiry / login required
    if (
      msg.includes('session expired') ||
      msg.includes('session timed out') ||
      msg.includes('session timeout') ||
      msg.includes('please login') ||
      msg.includes('invalid session') ||
      msg.includes('not logged in') ||
      msg.includes('session destroyed') ||
      msg.includes('login again')
    ) {
      return true;
    }
  }
  return false;
}

// Middleware to resolve active session (Supports stateless tokens across Vercel Lambdas)
function authMiddleware(req, res, next) {
  const sessionId = req.headers['x-session-id'] || req.query.sessionId;
  if (!sessionId) {
    return res.status(401).json({ success: false, sessionExpired: true, message: 'Session missing. Please log in.' });
  }

  // 1. Check in-memory session
  if (sessions.has(sessionId)) {
    req.sessionId = sessionId;
    req.userSession = sessions.get(sessionId);
    return next();
  }

  // 2. Decrypt stateless session token for serverless environments
  const decrypted = decryptSession(sessionId);
  if (decrypted && decrypted.regno) {
    const sessionObj = {
      cookies: decrypted.cookies || {},
      regno: decrypted.regno,
      univcode: decrypted.univcode || '049',
      studentInfo: decrypted.studentInfo || null,
      isDemo: Boolean(decrypted.isDemo),
      createdAt: decrypted.createdAt || Date.now(),
      cache: new Map()
    };
    sessions.set(sessionId, sessionObj);
    req.sessionId = sessionId;
    req.userSession = sessionObj;
    return next();
  }

  return res.status(401).json({ success: false, sessionExpired: true, message: 'Session expired or invalid. Please log in.' });
}

// 3.5 Session Check & Liveness Verification
app.get('/api/session-check', authMiddleware, (req, res) => {
  const session = req.userSession;
  if (!session) {
    return res.status(401).json({ success: false, sessionExpired: true, message: 'Session expired. Please log in.' });
  }
  return res.json({ success: true, valid: true, isDemo: session.isDemo });
});

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
  const rawDate = req.query.date;
  const date = (typeof rawDate === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(rawDate))
    ? rawDate
    : new Date().toISOString().split('T')[0];

  if (session.isDemo) {
    const demoData = DEMO_SUBJECTS.map(s => ({
      fsubcode: s.code,
      fsubname: s.name,
      conducted: s.conducted.toString(),
      attended: s.attended.toString(),
      ftotalclass: s.conducted.toString(),
      fpresentclass: s.attended.toString()
    }));
    return res.json({
      success: true,
      error_code: 0,
      data: demoData
    });
  }

  try {
    const params = new URLSearchParams();
    params.append('date', date);

    const resp = await portalFetch(
      `app.php?a=viewAttendanceDetsummary&univcode=${encodeURIComponent(session.univcode)}`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
        },
        body: params.toString()
      },
      session,
      7000
    );

    const text = await resp.text();
    let data;
    try {
      data = JSON.parse(text);
    } catch {
      if (isPortalSessionExpired(null, text)) {
        if (req.sessionId) sessions.delete(req.sessionId);
        return res.status(401).json({ success: false, sessionExpired: true, message: 'University portal session expired. Please sign in again.' });
      }
      return res.status(502).json({ success: false, message: 'Unable to parse attendance records from student portal.' });
    }

    if (isPortalSessionExpired(data, text)) {
      if (req.sessionId) sessions.delete(req.sessionId);
      return res.status(401).json({
        success: false,
        sessionExpired: true,
        message: (data && typeof data.msg === 'string') ? data.msg : 'University portal session expired. Please sign in again.'
      });
    }

    const rawList = (data.error_code === 0 && Array.isArray(data.data)) ? data.data : (Array.isArray(data.data) ? data.data : []);
    
    // Normalize data fields so all frontend views work regardless of portal response keys
    const normalizedData = rawList.map(item => {
      const cond = String(item.conducted ?? item.ftotalclass ?? item.total ?? 0);
      const att = String(item.attended ?? item.fpresentclass ?? item.present ?? 0);
      return {
        ...item,
        fsubcode: String(item.fsubcode || item.subcode || item.code || ''),
        fsubname: String(item.fsubname || item.subname || item.name || ''),
        conducted: cond,
        attended: att,
        ftotalclass: cond,
        fpresentclass: att
      };
    });

    res.json({
      success: true,
      error_code: data.error_code || 0,
      data: normalizedData
    });
  } catch (err) {
    console.error('[Portal] Error fetching attendance summary:', err.message);
    res.status(500).json({ success: false, message: 'Failed to retrieve attendance summary from student portal.' });
  }
});

// 6. Attendance for a Single Date
app.post('/api/attendance-daily', authMiddleware, async (req, res) => {
  const session = req.userSession;
  const { date } = req.body;

  if (!date || typeof date !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(date)) {
    return res.status(400).json({ success: false, message: 'Valid date is required (YYYY-MM-DD).' });
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
      `app.php?a=viewAttendanceDet&univcode=${encodeURIComponent(session.univcode)}`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
        },
        body: params.toString()
      },
      session,
      5000
    );

    const text = await resp.text();
    let data;
    try {
      data = JSON.parse(text);
    } catch {
      if (isPortalSessionExpired(null, text)) {
        if (req.sessionId) sessions.delete(req.sessionId);
        return res.status(401).json({ success: false, sessionExpired: true, message: 'University portal session expired. Please sign in again.' });
      }
      return res.status(502).json({ success: false, message: 'Unable to parse timetable data from student portal.' });
    }

    if (isPortalSessionExpired(data, text)) {
      if (req.sessionId) sessions.delete(req.sessionId);
      return res.status(401).json({
        success: false,
        sessionExpired: true,
        message: (data && typeof data.msg === 'string') ? data.msg : 'University portal session expired. Please sign in again.'
      });
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
    console.error(`[Portal] Error fetching daily attendance for ${date}:`, err.message);
    res.status(500).json({ success: false, message: 'Failed to retrieve daily attendance records.' });
  }
});

// 7. Attendance for an entire month (Calendar batch helper)
// Highly optimized for Vercel Serverless Function execution limits
app.post('/api/attendance-month', authMiddleware, async (req, res) => {
  const session = req.userSession;
  const { year, month } = req.body;

  const numYear = parseInt(year, 10);
  const numMonth = parseInt(month, 10);

  if (isNaN(numYear) || numYear < 2020 || numYear > 2035 || isNaN(numMonth) || numMonth < 1 || numMonth > 12) {
    return res.status(400).json({ success: false, message: 'Valid year (2020-2035) and month (1-12) are required.' });
  }

  const daysInMonth = new Date(numYear, numMonth, 0).getDate();
  const today = new Date();
  const todayStr = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;

  const results = {};
  const activeDatesToFetch = [];

  for (let d = 1; d <= daysInMonth; d++) {
    const dayStr = String(d).padStart(2, '0');
    const monthStr = String(numMonth).padStart(2, '0');
    const dateStr = `${numYear}-${monthStr}-${dayStr}`;

    const dayOfWeek = new Date(numYear, numMonth - 1, d).getDay(); // 0 = Sun
    const isSunday = dayOfWeek === 0;
    const isFuture = dateStr > todayStr;

    // Optimization: Skip future dates and Sundays without making network calls
    if (isSunday || isFuture) {
      results[dateStr] = { conducted: 0, attended: 0, classes: [] };
    } else {
      activeDatesToFetch.push(dateStr);
    }
  }

  if (session.isDemo) {
    for (const dateStr of activeDatesToFetch) {
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

  // Live Portal Fetch: Concurrency batch with per-request timeout guard
  let portalSessionExpired = false;
  let sessionExpiredMsg = '';

  const batchSize = 6;
  for (let i = 0; i < activeDatesToFetch.length; i += batchSize) {
    if (portalSessionExpired) break;
    const batch = activeDatesToFetch.slice(i, i + batchSize);
    await Promise.all(batch.map(async (dateStr) => {
      if (portalSessionExpired) return;

      // Check in-memory cache
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
          `app.php?a=viewAttendanceDet&univcode=${encodeURIComponent(session.univcode)}`,
          {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
            body: params.toString()
          },
          session,
          4500 // 4.5s timeout per day request
        );

        const text = await resp.text();
        let data = null;
        try { data = JSON.parse(text); } catch {}

        if (isPortalSessionExpired(data, text)) {
          portalSessionExpired = true;
          sessionExpiredMsg = (data && typeof data.msg === 'string') ? data.msg : 'University portal session expired.';
          return;
        }

        const classes = (data && data.error_code === 0 && Array.isArray(data.data)) ? data.data : [];
        if (session.cache) session.cache.set(dateStr, classes);

        const conducted = classes.reduce((sum, c) => sum + parseInt(c.fnoclass || '1', 10), 0);
        const attended = classes.reduce((sum, c) => {
          const isPres = c.fpresent === '1' || c.fpresent === 1 || String(c.fpresent).trim().toUpperCase() === 'P' || String(c.fpresent).toLowerCase() === 'present';
          return sum + (isPres ? parseInt(c.fnoclass || '1', 10) : 0);
        }, 0);
        results[dateStr] = { conducted, attended, classes };
      } catch (e) {
        // Fallback gracefully for this date
        results[dateStr] = { conducted: 0, attended: 0, classes: [], error: true };
      }
    }));
  }

  if (portalSessionExpired) {
    if (req.sessionId) sessions.delete(req.sessionId);
    return res.status(401).json({
      success: false,
      sessionExpired: true,
      message: sessionExpiredMsg || 'University portal session expired. Please sign in again.'
    });
  }

  res.json({ success: true, monthData: results });
});

// 8. IA Marks (Continuous Internal Evaluation)
app.get('/api/ia-marks', authMiddleware, async (req, res) => {
  const session = req.userSession;

  if (session.isDemo) {
    const demoIa = [
      {
        slNo: '1',
        sem: 'V',
        subcode: 'CB3001-1',
        subname: 'Information and Network Security',
        mse1: '19.00',
        mse2: '20.00',
        la1: '4.00',
        la2: '4.00',
        la3: '2.00',
        record: '10.00',
        conduction: '10.00',
        mseLab: '25.00',
        finalMarks: '48.00',
        eligibility: 'Eligible'
      },
      {
        slNo: '2',
        sem: 'V',
        subcode: 'CB2004-1',
        subname: 'Principles of Database Management Systems',
        mse1: '18.50',
        mse2: '14.00',
        la1: '4.00',
        la2: '4.00',
        la3: '2.00',
        record: '10.00',
        conduction: '10.00',
        mseLab: '30.00',
        finalMarks: '46.00',
        eligibility: 'Eligible'
      },
      {
        slNo: '3',
        sem: 'V',
        subcode: 'CB3003-1',
        subname: 'Essentials of Operating Systems',
        mse1: '12.00',
        mse2: '16.00',
        la1: '4.00',
        la2: '4.00',
        la3: '2.00',
        record: '',
        conduction: '',
        mseLab: '',
        finalMarks: '38.00',
        eligibility: 'Eligible'
      },
      {
        slNo: '4',
        sem: 'V',
        subcode: 'CB3222-1',
        subname: 'Malware Analysis and Detection',
        mse1: '17.00',
        mse2: '19.50',
        la1: '4.00',
        la2: '4.00',
        la3: '2.00',
        record: '',
        conduction: '',
        mseLab: '',
        finalMarks: '47.00',
        eligibility: 'Eligible'
      },
      {
        slNo: '5',
        sem: 'V',
        subcode: 'HU1010-1',
        subname: 'Research Methodology',
        mse1: '16.00',
        mse2: '18.00',
        la1: '4.00',
        la2: '4.00',
        la3: '2.00',
        record: '',
        conduction: '',
        mseLab: '',
        finalMarks: '44.00',
        eligibility: 'Eligible'
      },
      {
        slNo: '6',
        sem: 'V',
        subcode: 'CB3603-1',
        subname: 'Secure Full Stack Web Development',
        mse1: '',
        mse2: '',
        la1: '',
        la2: '',
        la3: '',
        record: '9.00',
        conduction: '20.00',
        mseLab: '20.00',
        finalMarks: '49.00',
        eligibility: 'Eligible'
      },
      {
        slNo: '7',
        sem: 'V',
        subcode: 'HU1006-1',
        subname: 'Introduction to IPR',
        mse1: '18.00',
        mse2: '13.00',
        la1: '4.00',
        la2: '4.00',
        la3: '2.00',
        record: '',
        conduction: '',
        mseLab: '',
        finalMarks: '41.00',
        eligibility: 'Eligible'
      },
      {
        slNo: '8',
        sem: 'V',
        subcode: 'HU1007-1',
        subname: 'Social Connect & Responsibility',
        mse1: '17.00',
        mse2: '20.00',
        la1: '4.00',
        la2: '4.00',
        la3: '2.00',
        record: '',
        conduction: '',
        mseLab: '',
        finalMarks: '47.00',
        eligibility: 'Eligible'
      },
      {
        slNo: '9',
        sem: 'V',
        subcode: 'UM1003-1',
        subname: 'Employability Skill Development',
        mse1: '13.00',
        mse2: '18.00',
        la1: '2.00',
        la2: '4.00',
        la3: '2.00',
        record: '',
        conduction: '',
        mseLab: '',
        finalMarks: '39.00',
        eligibility: 'Eligible'
      }
    ];

    const demoTableHtml = `
      <table class="table table-bordered">
        <thead>
          <tr><th colspan="14">IA / CIE Details</th></tr>
          <tr>
            <th>Sl. No.</th><th>Sem</th><th>Sub. Code</th><th>Sub. Name</th>
            <th>MSE-1</th><th>MSE-2</th><th>LA-1</th><th>LA-2</th><th>LA-3</th>
            <th>RECORD</th><th>CONDUCTION</th><th>MSE(LAB)</th><th>Final Marks</th><th>Eligibility</th>
          </tr>
        </thead>
        <tbody>
          ${demoIa.map(s => `
            <tr>
              <td>${s.slNo}</td><td>${s.sem}</td><td>${s.subcode}</td><td>${s.subname}</td>
              <td>${s.mse1}</td><td>${s.mse2}</td><td>${s.la1}</td><td>${s.la2}</td><td>${s.la3}</td>
              <td>${s.record}</td><td>${s.conduction}</td><td>${s.mseLab}</td><td>${s.finalMarks}</td><td>${s.eligibility}</td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    `;

    return res.json({
      success: true,
      error_code: 0,
      data: demoIa,
      rawHtml: demoTableHtml,
      isDemo: true
    });
  }

  try {
    let rawHtml = '';
    let parsedData = null;

    // Try viewIaMarksNew first
    try {
      const resp = await portalFetch(
        `app.php?a=viewIaMarksNew&univcode=${encodeURIComponent(session.univcode)}`,
        { method: 'GET' },
        session,
        8000
      );
      const text = await resp.text();
      let json = null;
      try { json = JSON.parse(text); } catch {}

      if (json && json.data) {
        if (typeof json.data.marks === 'string') {
          rawHtml = json.data.marks;
        } else if (Array.isArray(json.data.marks)) {
          parsedData = json.data.marks;
        } else if (Array.isArray(json.data)) {
          parsedData = json.data;
        }
      }
    } catch (e1) {
      console.warn('viewIaMarksNew failed:', e1.message);
    }

    // Try elementWiseMarks as fallback if nothing was returned
    if (!rawHtml && (!parsedData || parsedData.length === 0)) {
      try {
        const resp2 = await portalFetch(
          `app.php?a=elementWiseMarks&univcode=${encodeURIComponent(session.univcode)}`,
          { method: 'GET' },
          session,
          8000
        );
        const text2 = await resp2.text();
        let json2 = null;
        try { json2 = JSON.parse(text2); } catch {}
        if (json2 && json2.data) {
          if (Array.isArray(json2.data.marks)) {
            parsedData = json2.data.marks;
          }
        }
      } catch (e2) {
        console.warn('elementWiseMarks failed:', e2.message);
      }
    }

    res.json({
      success: true,
      error_code: 0,
      rawHtml: rawHtml || '',
      data: parsedData || []
    });
  } catch (err) {
    console.error('[Security] Error fetching IA marks:', err.message);
    res.status(500).json({ success: false, message: 'Failed to retrieve IA marks from student portal.' });
  }
});

// 9. Logout
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

// Centralized Security Error Handler (Never leak stack traces to client)
app.use((err, req, res, next) => {
  console.error('[Unhandled Server Error]:', err && err.message ? err.message : err);
  if (res.headersSent) return next(err);
  res.status(err.status || 500).json({
    success: false,
    message: 'An internal server security or processing error occurred.'
  });
});

if (require.main === module) {
  app.listen(PORT, () => {
    console.log(`🚀 Attendance Tracker server running on http://localhost:${PORT}`);
  });
}

module.exports = app;
