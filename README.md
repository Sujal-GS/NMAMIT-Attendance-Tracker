# NMAMIT Attendance Tracker 🎓

> Intelligent, beautiful attendance tracker for NMAMIT / NITTE University students — powered by the University Solutions student portal.

![Node.js](https://img.shields.io/badge/Node.js-Express-green?logo=node.js)
![License](https://img.shields.io/badge/License-MIT-blue)

## ✨ Features

- 📅 **Interactive Calendar** — Day-by-day class timetable with attendance status
- 📊 **Subject Planner** — 85% attendance calculator with bunk budget per subject
- 🟩 **GitHub-style Heatmap** — 32-week attendance activity matrix with streaks
- 📈 **Analytics Panels** — Day-of-week DNA, monthly trend, subject leaderboard, smart insights
- 🎯 **What-If Simulator** — Real-time projection sandbox for future classes
- 🚨 **Danger Radar** — Low-attendance warning for subjects below 75% / 85%
- 🏆 **Attendance Wrapped** — Shareable semester recap story card
- 📱 **PWA** — Installable on Android & iOS, works offline

## 🚀 Local Development

```bash
npm install
npm run dev
# Visit http://localhost:3050
```

## ☁️ Deploy to Vercel

See [DEPLOYMENT.md](DEPLOYMENT.md) or follow the steps in the project wiki.

## 🔒 Security

- Cryptographically-secure session IDs (Node.js `crypto`)
- Login rate limiting (15 attempts / 15 min per IP)
- Security headers: `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`, etc.
- XSS-safe rendering with HTML escaping on all server-supplied data
- Restricted CORS (same-origin only)

## 👨‍💻 Built by

**Sujal G S** & **Lakshminarayanan P S** — NMAMIT, Dept. of Computer Science & Engineering
