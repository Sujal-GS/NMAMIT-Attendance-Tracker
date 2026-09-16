/**
 * "Attendance Wrapped" Story Card Generator
 * Native HTML5 Canvas Renderer for High-Resolution Instagram/WhatsApp Story Cards
 */

const Wrapped = {
  isInitialized: false,

  init() {
    if (this.isInitialized) return;
    this.isInitialized = true;
    this.bindEvents();
  },

  bindEvents() {
    const btnOpen = document.getElementById('btn-open-wrapped');
    const btnClose = document.getElementById('btn-close-wrapped');
    const backdrop = document.getElementById('wrapped-modal-backdrop');
    const btnDownload = document.getElementById('btn-download-wrapped');
    const btnCopy = document.getElementById('btn-copy-wrapped-text');

    if (btnOpen) {
      btnOpen.addEventListener('click', () => {
        this.open();
      });
    }

    const spotlightCard = document.getElementById('spotlight-wrapped-card');
    if (spotlightCard) {
      spotlightCard.addEventListener('click', () => {
        this.open();
      });
      spotlightCard.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          this.open();
        }
      });
    }

    if (btnClose) {
      btnClose.addEventListener('click', () => {
        this.close();
      });
    }

    if (backdrop) {
      backdrop.addEventListener('click', () => {
        this.close();
      });
    }

    if (btnDownload) {
      btnDownload.addEventListener('click', () => {
        this.download();
      });
    }

    if (btnCopy) {
      btnCopy.addEventListener('click', () => {
        this.copyShareText();
      });
    }

    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') {
        const modal = document.getElementById('wrapped-modal');
        if (modal && !modal.classList.contains('hidden')) {
          this.close();
        }
      }
    });
  },

  open() {
    const modal = document.getElementById('wrapped-modal');
    if (modal) modal.classList.remove('hidden');
    this.renderCanvas();
  },

  close() {
    const modal = document.getElementById('wrapped-modal');
    if (modal) modal.classList.add('hidden');
  },

  getStudentStats() {
    const subjects = window.SummaryView ? window.SummaryView.rawSubjects : [];
    let totalConducted = 0;
    let totalAttended = 0;
    let bestSubject = { name: 'None', pct: 0 };

    subjects.forEach(s => {
      const cond = parseInt(s.conducted ?? s.ftotalclass ?? 0, 10);
      const att = parseInt(s.attended ?? s.fpresentclass ?? 0, 10);
      totalConducted += cond;
      totalAttended += att;

      const pct = cond > 0 ? (att / cond) * 100 : 0;
      if (pct >= bestSubject.pct) {
        bestSubject = {
          name: s.fsubname || s.name || s.fsubcode || s.code || 'Subject',
          pct: pct
        };
      }
    });

    const overallPct = totalConducted > 0 ? (totalAttended / totalConducted) * 100 : 100;
    const nameEl = document.getElementById('profile-name');
    const usnEl = document.getElementById('profile-usn');

    const streak = window.HeatmapView ? window.HeatmapView.maxStreak : 12;
    const flawless = window.HeatmapView ? window.HeatmapView.flawlessDays : 24;

    return {
      name: nameEl ? nameEl.textContent : 'Student User',
      usn: usnEl ? usnEl.textContent : 'NNM24CS1234',
      overallPct: overallPct.toFixed(1),
      totalConducted,
      totalAttended,
      totalMissed: totalConducted - totalAttended,
      bestSubject,
      streak,
      flawless
    };
  },

  renderCanvas() {
    const canvas = document.getElementById('wrapped-canvas');
    if (!canvas) return;

    // High-DPI 9:16 canvas (540x960 logical, scaled for crisp retina)
    const ctx = canvas.getContext('2d');
    const width = 540;
    const height = 960;
    canvas.width = width;
    canvas.height = height;

    const stats = this.getStudentStats();

    // 1. Deep Tech Black Background
    ctx.fillStyle = '#050508';
    ctx.fillRect(0, 0, width, height);

    // 2. Subtle Tech Grid in Canvas Background
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.05)';
    ctx.lineWidth = 1;
    const gridSize = 36;
    for (let x = 0; x < width; x += gridSize) {
      ctx.beginPath();
      ctx.moveTo(x, 0);
      ctx.lineTo(x, height);
      ctx.stroke();
    }
    for (let y = 0; y < height; y += gridSize) {
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(width, y);
      ctx.stroke();
    }

    // 3. Ambient Glow Gradients (Emerald / Violet)
    const glow1 = ctx.createRadialGradient(width * 0.2, height * 0.25, 10, width * 0.2, height * 0.25, 280);
    glow1.addColorStop(0, 'rgba(48, 209, 88, 0.18)');
    glow1.addColorStop(1, 'transparent');
    ctx.fillStyle = glow1;
    ctx.fillRect(0, 0, width, height);

    const glow2 = ctx.createRadialGradient(width * 0.8, height * 0.7, 10, width * 0.8, height * 0.7, 300);
    glow2.addColorStop(0, 'rgba(99, 102, 241, 0.15)');
    glow2.addColorStop(1, 'transparent');
    ctx.fillStyle = glow2;
    ctx.fillRect(0, 0, width, height);

    // 4. Header Badge
    ctx.fillStyle = 'rgba(255, 255, 255, 0.08)';
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.2)';
    this.roundRect(ctx, 40, 50, width - 80, 48, 24, true, true);

    ctx.font = '700 13px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.fillStyle = '#30D158';
    ctx.textAlign = 'left';
    ctx.fillText('⚡ 2026 ATTENDANCE RECAP', 62, 79);

    ctx.fillStyle = 'rgba(255, 255, 255, 0.6)';
    ctx.textAlign = 'right';
    ctx.fillText('NMAMIT PORTAL', width - 62, 79);

    // 5. Student Profile Header
    ctx.font = '800 32px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.fillStyle = '#ffffff';
    ctx.textAlign = 'left';
    ctx.fillText(stats.name, 44, 150);

    ctx.font = '600 15px -apple-system, BlinkMacSystemFont, monospace';
    ctx.fillStyle = 'rgba(255, 255, 255, 0.6)';
    ctx.fillText(`USN: ${stats.usn} • NITTE University`, 44, 180);

    // 6. Primary Feature Card (Aggregate %)
    ctx.fillStyle = 'rgba(255, 255, 255, 0.05)';
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.18)';
    this.roundRect(ctx, 40, 215, width - 80, 220, 28, true, true);

    ctx.font = '600 13px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.fillStyle = 'rgba(255, 255, 255, 0.5)';
    ctx.fillText('OVERALL SEMESTER SCORE', 70, 255);

    // Large Percentage
    ctx.font = '900 84px -apple-system, BlinkMacSystemFont, sans-serif';
    const isGood = parseFloat(stats.overallPct) >= 85;
    ctx.fillStyle = isGood ? '#30D158' : '#FFD60A';
    ctx.fillText(`${stats.overallPct}%`, 70, 345);

    // Status Pill inside card
    const statusText = isGood ? '✓ 85% HONOR ROLL' : '⚠ BUFFER WATCH';
    ctx.fillStyle = isGood ? 'rgba(48, 209, 88, 0.18)' : 'rgba(255, 214, 10, 0.18)';
    ctx.strokeStyle = isGood ? 'rgba(48, 209, 88, 0.4)' : 'rgba(255, 214, 10, 0.4)';
    this.roundRect(ctx, 70, 375, 180, 34, 17, true, true);

    ctx.font = '700 12px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.fillStyle = isGood ? '#30D158' : '#FFD60A';
    ctx.textAlign = 'center';
    ctx.fillText(statusText, 160, 396);
    ctx.textAlign = 'left';

    // 7. Mini 2x2 Bento Metrics Grid
    const cardW = (width - 100) / 2;
    const cardH = 110;

    // Card A: Classes Attended
    ctx.fillStyle = 'rgba(255, 255, 255, 0.04)';
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.12)';
    this.roundRect(ctx, 40, 460, cardW, cardH, 20, true, true);
    ctx.font = '600 11px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.fillStyle = 'rgba(255, 255, 255, 0.5)';
    ctx.fillText('CLASSES ATTENDED', 56, 490);
    ctx.font = '800 32px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.fillStyle = '#ffffff';
    ctx.fillText(`${stats.totalAttended}`, 56, 535);
    ctx.font = '500 12px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.fillStyle = 'rgba(255, 255, 255, 0.4)';
    ctx.fillText(`out of ${stats.totalConducted} held`, 56, 555);

    // Card B: Longest Streak
    ctx.fillStyle = 'rgba(255, 255, 255, 0.04)';
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.12)';
    this.roundRect(ctx, 60 + cardW, 460, cardW, cardH, 20, true, true);
    ctx.font = '600 11px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.fillStyle = 'rgba(255, 255, 255, 0.5)';
    ctx.fillText('LONGEST STREAK', 76 + cardW, 490);
    ctx.font = '800 32px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.fillStyle = '#30D158';
    ctx.fillText(`🔥 ${stats.streak}d`, 76 + cardW, 535);
    ctx.font = '500 12px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.fillStyle = 'rgba(255, 255, 255, 0.4)';
    ctx.fillText('without bunking', 76 + cardW, 555);

    // Card C: Best Subject
    ctx.fillStyle = 'rgba(255, 255, 255, 0.04)';
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.12)';
    this.roundRect(ctx, 40, 590, width - 80, 115, 20, true, true);
    ctx.font = '600 11px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.fillStyle = 'rgba(255, 255, 255, 0.5)';
    ctx.fillText('TOP SCORING SUBJECT', 60, 620);
    ctx.font = '700 20px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.fillStyle = '#ffffff';
    const subTitle = stats.bestSubject.name.length > 26 
      ? stats.bestSubject.name.substring(0, 24) + '...' 
      : stats.bestSubject.name;
    ctx.fillText(subTitle, 60, 652);
    ctx.font = '700 16px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.fillStyle = '#30D158';
    ctx.fillText(`${stats.bestSubject.pct.toFixed(1)}% Attendance Record`, 60, 680);

    // 8. Visual Activity Matrix Dots Preview
    ctx.fillStyle = 'rgba(255, 255, 255, 0.04)';
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.12)';
    this.roundRect(ctx, 40, 725, width - 80, 110, 20, true, true);

    ctx.font = '600 11px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.fillStyle = 'rgba(255, 255, 255, 0.5)';
    ctx.fillText('ACTIVITY MATRIX PREVIEW', 60, 755);

    // Draw a neat row of GitHub green dots
    const startX = 60;
    const startY = 775;
    const dotSpacing = 16;
    const cols = 26;
    for (let c = 0; c < cols; c++) {
      for (let r = 0; r < 3; r++) {
        const x = startX + (c * dotSpacing);
        const y = startY + (r * dotSpacing);
        const hash = (c * 7 + r * 13) % 10;
        if (hash === 0) {
          ctx.fillStyle = '#FF453A'; // red
        } else if (hash <= 2) {
          ctx.fillStyle = '#FFD60A'; // yellow
        } else if (hash <= 6) {
          ctx.fillStyle = '#30D158'; // green
        } else {
          ctx.fillStyle = 'rgba(255, 255, 255, 0.08)';
        }
        this.roundRect(ctx, x, y, 11, 11, 3, true, false);
      }
    }

    // 9. Footer Branding
    ctx.font = '600 13px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.fillStyle = 'rgba(255, 255, 255, 0.4)';
    ctx.textAlign = 'center';
    ctx.fillText('Crafted with 🖤 for NMAMIT Engineering Students', width / 2, 885);
    ctx.font = '600 12px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.fillStyle = 'rgba(255, 255, 255, 0.35)';
    ctx.fillText('Sujal G S and Lakshminarayanan P S', width / 2, 906);
  },

  roundRect(ctx, x, y, width, height, radius, fill, stroke) {
    ctx.beginPath();
    ctx.moveTo(x + radius, y);
    ctx.lineTo(x + width - radius, y);
    ctx.quadraticCurveTo(x + width, y, x + width, y + radius);
    ctx.lineTo(x + width, y + height - radius);
    ctx.quadraticCurveTo(x + width, y + height, x + width - radius, y + height);
    ctx.lineTo(x + radius, y + height);
    ctx.quadraticCurveTo(x, y + height, x, y + height - radius);
    ctx.lineTo(x, y + radius);
    ctx.quadraticCurveTo(x, y, x + radius, y);
    ctx.closePath();
    if (fill) ctx.fill();
    if (stroke) ctx.stroke();
  },

  download() {
    const canvas = document.getElementById('wrapped-canvas');
    if (!canvas) return;

    const link = document.createElement('a');
    link.download = `NMAMIT_Attendance_Wrapped_${Date.now()}.png`;
    link.href = canvas.toDataURL('image/png');
    link.click();

    if (window.App) {
      App.showToast('Story card downloaded! Ready to share 🎉', 'success');
    }
  },

  copyShareText() {
    const stats = this.getStudentStats();
    const text = `📊 My NMAMIT Attendance Recap:
Overall: ${stats.overallPct}% ${parseFloat(stats.overallPct) >= 85 ? '✓ (Safe)' : '⚠️'}
Classes Attended: ${stats.totalAttended} / ${stats.totalConducted}
🔥 Longest Streak: ${stats.streak} days without bunking!
Top Subject: ${stats.bestSubject.name} (${stats.bestSubject.pct.toFixed(1)}%)

Tracked with NMAMIT Attendance Tracker 🚀`;

    if (navigator.clipboard) {
      navigator.clipboard.writeText(text).then(() => {
        if (window.App) App.showToast('Stats copied to clipboard! 📋', 'success');
      });
    }
  }
};

window.Wrapped = Wrapped;
