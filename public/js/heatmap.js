/**
 * GitHub-Style Attendance Activity Matrix & Streak Engine
 * Features 20-Week Heatmap, Streaks, Subject Filtering & Day Inspection
 */

// ── HTML escaping to prevent XSS via server-supplied data ────────────────────────
function escapeHtml(str) {
  return String(str === null || str === undefined ? '' : str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#x27;');
}

const HeatmapView = {
  isInitialized: false,
  selectedSubject: 'ALL',
  semesterData: new Map(), // 'YYYY-MM-DD' -> { conducted, attended, missed, classes: [] }
  activeStreak: 0,
  maxStreak: 0,
  flawlessDays: 0,
  consistencyPct: 100,

  formatDate(d) {
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  },

  init() {
    if (this.isInitialized) return;
    this.isInitialized = true;
    this.bindEvents();
  },

  bindEvents() {
    const filterSelect = document.getElementById('heatmap-subject-filter');
    if (filterSelect) {
      filterSelect.addEventListener('change', (e) => {
        this.selectedSubject = e.target.value;
        this.render();
      });
    }

    // Delegate tooltip and click events on heatmap grid
    const gridContainer = document.getElementById('heatmap-cells-grid');
    const tooltip = document.getElementById('heatmap-tooltip');

    if (gridContainer && tooltip) {
      gridContainer.addEventListener('mouseover', (e) => {
        const cell = e.target.closest('.heatmap-cell');
        if (!cell) {
          tooltip.classList.add('hidden');
          return;
        }

        const dateStr = cell.getAttribute('data-date');
        const dayInfo = this.getDayData(dateStr);
        if (!dayInfo) {
          tooltip.classList.add('hidden');
          return;
        }

        const [y, m, d] = dateStr.split('-').map(Number);
        const dateObj = new Date(y, m - 1, d);
        const formattedDate = dateObj.toLocaleDateString('en-US', {
          weekday: 'short',
          month: 'short',
          day: 'numeric',
          year: 'numeric'
        });

        let statusText = 'No scheduled classes';
        let statusBadge = '<span class="status-badge status-none">Off / Holiday</span>';

        if (dayInfo.conducted > 0) {
          const pct = Math.round((dayInfo.attended / dayInfo.conducted) * 100);
          if (pct === 100) {
            statusBadge = '<span class="status-badge status-perfect">100% Flawless</span>';
          } else if (pct >= 75) {
            statusBadge = `<span class="status-badge status-good">${pct}% Attended</span>`;
          } else if (dayInfo.attended > 0) {
            statusBadge = `<span class="status-badge status-partial">${pct}% Partial</span>`;
          } else {
            statusBadge = '<span class="status-badge status-missed">0% Missed All</span>';
          }
          statusText = `${dayInfo.attended} of ${dayInfo.conducted} class${dayInfo.conducted > 1 ? 'es' : ''} attended`;
        }

        let subjectsHtml = '';
        if (dayInfo.classes && dayInfo.classes.length > 0) {
          subjectsHtml = `
            <div class="tooltip-subjects">
              ${dayInfo.classes.slice(0, 5).map(c => {
                const isPres = (c.fpresent === '1' || c.attended > 0 || String(c.fpresent).toUpperCase() === 'P');
                const subName = escapeHtml(c.fsubname || c.name || c.fsubcode || 'Academic Class');
                return `<div class="tooltip-sub-item ${isPres ? 'attended' : 'missed'}">
                  <span class="sub-dot"></span>
                  <span class="sub-name" title="${subName}">${subName}</span>
                </div>`;
              }).join('')}
              ${dayInfo.classes.length > 5 ? `<div class="tooltip-more">+${dayInfo.classes.length - 5} more</div>` : ''}
            </div>
          `;
        }

        tooltip.innerHTML = `
          <div class="tooltip-header">
            <span class="tooltip-date">${formattedDate}</span>
            ${statusBadge}
          </div>
          <div class="tooltip-body">${statusText}</div>
          ${subjectsHtml}
          <div class="tooltip-hint">Click cell to view in Calendar</div>
        `;

        tooltip.classList.remove('hidden');

        // Position tooltip using fixed coordinates on the viewport
        const cellRect = cell.getBoundingClientRect();
        let left = cellRect.left + (cellRect.width / 2);
        // Clamp horizontally so tooltip never overflows mobile or desktop screen
        const halfWidth = 140;
        left = Math.max(halfWidth + 12, Math.min(window.innerWidth - halfWidth - 12, left));

        // Smart vertical positioning:
        // Default is above cell. If cell is near top of viewport (< 200px), flip BELOW the cell.
        let top = cellRect.top - 10;
        let transform = 'translate(-50%, -100%)';

        if (cellRect.top < 200) {
          top = cellRect.bottom + 10;
          transform = 'translate(-50%, 0)';
        }

        tooltip.style.left = `${left}px`;
        tooltip.style.top = `${top}px`;
        tooltip.style.transform = transform;
      });

      gridContainer.addEventListener('mouseleave', () => {
        tooltip.classList.add('hidden');
      });

      // Auto-hide tooltip on page or container scroll
      window.addEventListener('scroll', () => {
        if (!tooltip.classList.contains('hidden')) tooltip.classList.add('hidden');
      }, { passive: true });

      const scrollArea = document.querySelector('.heatmap-board-wrapper');
      if (scrollArea) {
        scrollArea.addEventListener('scroll', () => {
          if (!tooltip.classList.contains('hidden')) tooltip.classList.add('hidden');
        }, { passive: true });
      }

      gridContainer.addEventListener('click', (e) => {
        const cell = e.target.closest('.heatmap-cell');
        if (!cell) return;
        const dateStr = cell.getAttribute('data-date');
        if (!dateStr) return;

        // Switch to Calendar tab and inspect this day
        if (window.App) {
          App.switchTab('tab-calendar');
          const [y, m, d] = dateStr.split('-').map(Number);
          CalendarView.currentYear = y;
          CalendarView.currentMonth = m - 1;
          CalendarView.selectedDateStr = dateStr;
          CalendarView.render();
          CalendarView.loadDateDetails(dateStr);
        }
      });
    }
  },

  async load() {
    try {
      const today = new Date();
      const currentYear = today.getFullYear();
      const currentMonth = today.getMonth() + 1; // 1-12

      // Prioritize the recent semester months first (from current month backwards)
      const monthsToFetch = [];
      for (let i = 0; i < 8; i++) {
        let m = currentMonth - i;
        let y = currentYear;
        if (m <= 0) {
          m += 12;
          y -= 1;
        }
        monthsToFetch.push({ year: y, month: m });
      }

      this.semesterData.clear();

      // Fetch in pairs of months and render progressively as data arrives
      for (let i = 0; i < monthsToFetch.length; i += 2) {
        const chunk = monthsToFetch.slice(i, i + 2);
        const chunkRes = await Promise.all(
          chunk.map(({ year, month }) => API.getMonthAttendance(year, month).catch(() => ({})))
        );

        let hasNewData = false;
        chunkRes.forEach(res => {
          if (res && res.success && res.monthData) {
            for (const [dateStr, info] of Object.entries(res.monthData)) {
              this.semesterData.set(dateStr, info);
              hasNewData = true;
            }
          }
        });

        if (hasNewData) {
          this.populateSubjectFilter();
          this.calculateStreaks();
          this.render();
        }
      }
    } catch (err) {
      console.warn('[HeatmapView] Error loading semester data:', err);
    }
  },

  populateSubjectFilter() {
    const filterSelect = document.getElementById('heatmap-subject-filter');
    if (!filterSelect) return;

    // Collect unique subjects
    const subjectsMap = new Map();
    for (const info of this.semesterData.values()) {
      if (Array.isArray(info.classes)) {
        info.classes.forEach(c => {
          const code = c.fsubcode || c.code;
          const name = c.fsubname || c.name || code;
          if (code && !subjectsMap.has(code)) {
            subjectsMap.set(code, name);
          }
        });
      }
    }

    let optionsHtml = '<option value="ALL">All Subjects Combined</option>';
    for (const [code, name] of subjectsMap.entries()) {
      const esc = escapeHtml;
      optionsHtml += `<option value="${esc(code)}" ${this.selectedSubject === code ? 'selected' : ''}>${esc(code)} - ${esc(name)}</option>`;
    }
    filterSelect.innerHTML = optionsHtml;
    if (window.CustomSelect) window.CustomSelect.refresh(filterSelect);
  },

  getDayData(dateStr) {
    const raw = this.semesterData.get(dateStr);
    if (!raw) return { conducted: 0, attended: 0, missed: 0, classes: [] };

    if (this.selectedSubject === 'ALL') {
      return {
        conducted: raw.conducted || 0,
        attended: raw.attended || 0,
        missed: (raw.conducted || 0) - (raw.attended || 0),
        classes: raw.classes || []
      };
    }

    // Filter by specific subject code
    const filteredClasses = (raw.classes || []).filter(c => (c.fsubcode || c.code) === this.selectedSubject);
    let conducted = 0;
    let attended = 0;

    filteredClasses.forEach(c => {
      const cnt = parseInt(c.fnoclass || 1, 10);
      conducted += cnt;
      if (c.fpresent === '1' || c.attended > 0 || String(c.fpresent).toUpperCase() === 'P') {
        attended += cnt;
      }
    });

    return {
      conducted,
      attended,
      missed: conducted - attended,
      classes: filteredClasses
    };
  },

  calculateStreaks() {
    let currentStreak = 0;
    let bestStreak = 0;
    let tempStreak = 0;
    let flawlessCount = 0;
    let totalAttended = 0;
    let totalConducted = 0;

    const todayStr = this.formatDate(new Date());
    const sortedDates = Array.from(this.semesterData.keys()).sort();

    sortedDates.forEach(dateStr => {
      if (dateStr > todayStr) return; // Skip future dates
      const data = this.getDayData(dateStr);

      if (data.conducted > 0) {
        totalConducted += data.conducted;
        totalAttended += data.attended;

        const isGoodDay = (data.attended / data.conducted) >= 0.75;
        const isFlawless = data.attended === data.conducted;

        if (isFlawless) {
          flawlessCount++;
        }

        if (isGoodDay) {
          tempStreak++;
          if (tempStreak > bestStreak) bestStreak = tempStreak;
        } else {
          tempStreak = 0;
        }
      }
    });

    for (let i = sortedDates.length - 1; i >= 0; i--) {
      const dateStr = sortedDates[i];
      if (dateStr > todayStr) continue;
      const data = this.getDayData(dateStr);

      if (data.conducted > 0) {
        if ((data.attended / data.conducted) >= 0.75) {
          currentStreak++;
        } else {
          break;
        }
      }
    }

    this.activeStreak = currentStreak;
    this.maxStreak = bestStreak;
    this.flawlessDays = flawlessCount;
    this.consistencyPct = totalConducted > 0 ? Math.round((totalAttended / totalConducted) * 100) : 100;
  },

  render() {
    this.calculateStreaks();
    this.renderMetrics();
    this.renderGrid();
    this.renderAnalyticsPanels();
  },

  // ── Analytics Panels Orchestrator ─────────────────────────────────────────
  renderAnalyticsPanels() {
    this.renderInsights();
    this.renderDayOfWeekChart();
    this.renderMonthlyTrend();
    this.renderSubjectLeaderboard();
  },

  // ── 1. Smart Insights Strip ────────────────────────────────────────────────
  renderInsights() {
    const todayStr = this.formatDate(new Date());

    // Day-of-week buckets: 0=Mon … 5=Sat (skip Sun)
    const dayNames = ['Monday','Tuesday','Wednesday','Thursday','Friday','Saturday'];
    const dowBuckets = Array.from({ length: 7 }, () => ({ conducted: 0, attended: 0 }));

    // Subject buckets
    const subjectMap = new Map(); // code -> { name, conducted, attended }

    let totalConducted = 0;
    let totalMissed = 0;

    for (const [dateStr, info] of this.semesterData.entries()) {
      if (dateStr > todayStr) continue;
      const d = new Date(dateStr + 'T00:00:00');
      const dotw = (d.getDay() + 6) % 7; // Mon=0 … Sun=6

      const cond = info.conducted || 0;
      const att  = info.attended  || 0;
      if (cond > 0) {
        totalConducted += cond;
        totalMissed    += (cond - att);
        dowBuckets[dotw].conducted += cond;
        dowBuckets[dotw].attended  += att;
      }

      // Per-subject aggregation
      if (Array.isArray(info.classes)) {
        info.classes.forEach(c => {
          const code = c.fsubcode || c.code || 'UNKNOWN';
          const name = c.fsubname || c.name || code;
          const cnt  = parseInt(c.fnoclass || 1, 10);
          const pres = (c.fpresent === '1' || c.fpresent === 1 || String(c.fpresent).toUpperCase() === 'P');
          if (!subjectMap.has(code)) subjectMap.set(code, { name, conducted: 0, attended: 0 });
          const sb = subjectMap.get(code);
          sb.conducted += cnt;
          if (pres) sb.attended += cnt;
        });
      }
    }

    // Weakest day (min pct among days with ≥1 class)
    let weakDay = '—', weakDayPct = '';
    {
      let minPct = Infinity;
      dowBuckets.forEach((b, i) => {
        if (b.conducted > 0) {
          const pct = (b.attended / b.conducted) * 100;
          if (pct < minPct) { minPct = pct; weakDay = dayNames[i]; weakDayPct = `${pct.toFixed(0)}% avg attendance`; }
        }
      });
      if (weakDay === '—') { weakDay = 'N/A'; weakDayPct = 'No data yet'; }
    }

    // Best subject (max pct with ≥5 classes conducted)
    let bestSubName = '—', bestSubPct = '';
    {
      let maxPct = -1;
      for (const [, sb] of subjectMap.entries()) {
        if (sb.conducted >= 5) {
          const pct = (sb.attended / sb.conducted) * 100;
          if (pct > maxPct) { maxPct = pct; bestSubName = sb.name.length > 22 ? sb.name.substring(0, 20) + '…' : sb.name; bestSubPct = `${pct.toFixed(0)}% perfect attendance`; }
        }
      }
      if (bestSubName === '—') { bestSubName = 'N/A'; bestSubPct = 'Need more data'; }
    }

    // Missed classes
    const missedDisplay = totalMissed > 0 ? `${totalMissed} classes` : 'None!';
    const missedPct = totalConducted > 0
      ? `${((totalMissed / totalConducted) * 100).toFixed(1)}% of all classes skipped`
      : 'No data yet';

    // Best streak
    const streakDisplay = `${this.maxStreak} days`;
    const streakSub = this.maxStreak > 0 ? `Active streak: ${this.activeStreak} day${this.activeStreak !== 1 ? 's' : ''}` : 'Start attending to build a streak!';

    // Update DOM
    const set = (id, val) => { const el = document.getElementById(id); if (el) el.textContent = val; };
    set('hm-ins-weakday-val', weakDay);
    set('hm-ins-weakday-sub', weakDayPct);
    set('hm-ins-bestsub-val', bestSubName);
    set('hm-ins-bestsub-sub', bestSubPct);
    set('hm-ins-missed-val',  missedDisplay);
    set('hm-ins-missed-sub',  missedPct);
    set('hm-ins-streak-val',  streakDisplay);
    set('hm-ins-streak-sub',  streakSub);

    // Color the weakest day card rose, best subject emerald
    const weakCard = document.getElementById('hm-insight-weakday');
    const bestCard = document.getElementById('hm-insight-bestsub');
    if (weakCard) weakCard.style.borderColor = 'rgba(239,68,68,0.35)';
    if (bestCard) bestCard.style.borderColor = 'rgba(16,185,129,0.35)';
  },

  // ── 2. Day-of-Week DNA Bar Chart ───────────────────────────────────────────
  renderDayOfWeekChart() {
    const container = document.getElementById('hm-dow-chart');
    if (!container) return;

    const todayStr = this.formatDate(new Date());
    const dayLabels = ['Mon','Tue','Wed','Thu','Fri','Sat'];
    const buckets = Array.from({ length: 6 }, () => ({ conducted: 0, attended: 0 }));

    for (const [dateStr, info] of this.semesterData.entries()) {
      if (dateStr > todayStr) continue;
      const d = new Date(dateStr + 'T00:00:00');
      const dotw = (d.getDay() + 6) % 7; // Mon=0 … Sat=5
      if (dotw >= 6) continue; // skip Sunday
      const cond = info.conducted || 0;
      const att  = info.attended  || 0;
      if (cond > 0) {
        buckets[dotw].conducted += cond;
        buckets[dotw].attended  += att;
      }
    }

    const maxPct = Math.max(...buckets.map(b => b.conducted > 0 ? (b.attended / b.conducted) * 100 : 0), 1);
    const minPctIdx = buckets.reduce((minI, b, i, arr) => {
      const p = b.conducted > 0 ? b.attended / b.conducted : 1;
      return (p < (arr[minI].conducted > 0 ? arr[minI].attended / arr[minI].conducted : 1)) ? i : minI;
    }, 0);
    const maxPctIdx = buckets.reduce((maxI, b, i, arr) => {
      const p = b.conducted > 0 ? b.attended / b.conducted : 0;
      return (p > (arr[maxI].conducted > 0 ? arr[maxI].attended / arr[maxI].conducted : 0)) ? i : maxI;
    }, 0);

    container.innerHTML = buckets.map((b, i) => {
      const pct = b.conducted > 0 ? (b.attended / b.conducted) * 100 : 0;
      const heightPct = b.conducted > 0 ? Math.max(6, (pct / 100) * 100) : 4;

      let color = 'var(--emerald)';
      let opacity = '0.85';
      if (i === minPctIdx && b.conducted > 0) { color = 'var(--rose)'; opacity = '1'; }
      else if (i === maxPctIdx && b.conducted > 0) { color = 'var(--emerald)'; opacity = '1'; }
      else if (pct < 75) { color = 'var(--amber)'; opacity = '0.75'; }

      const pctLabel = b.conducted > 0 ? `${pct.toFixed(0)}%` : '—';

      return `
        <div class="hm-dow-bar-group" title="${dayLabels[i]}: ${pctLabel} (${b.attended}/${b.conducted} classes)">
          <div class="hm-dow-bar-track">
            <div class="hm-dow-bar-fill"
                 data-pct="${pctLabel}"
                 style="height:${heightPct}%; background:${color}; opacity:${opacity}; box-shadow: 0 0 8px ${color}55;">
            </div>
          </div>
          <span class="hm-dow-label">${dayLabels[i]}</span>
        </div>
      `;
    }).join('');
  },

  // ── 3. Monthly Trend Bar Chart ─────────────────────────────────────────────
  renderMonthlyTrend() {
    const container = document.getElementById('hm-monthly-chart');
    if (!container) return;

    const todayStr = this.formatDate(new Date());

    // Aggregate by month
    const monthlyMap = new Map(); // 'YYYY-MM' -> { conducted, attended }
    for (const [dateStr, info] of this.semesterData.entries()) {
      if (dateStr > todayStr) continue;
      const key = dateStr.substring(0, 7);
      if (!monthlyMap.has(key)) monthlyMap.set(key, { conducted: 0, attended: 0 });
      const mb = monthlyMap.get(key);
      mb.conducted += info.conducted || 0;
      mb.attended  += info.attended  || 0;
    }

    // Sort months chronologically, keep only those with classes
    const months = Array.from(monthlyMap.entries())
      .filter(([, b]) => b.conducted > 0)
      .sort(([a], [b]) => a.localeCompare(b));

    if (months.length === 0) {
      container.innerHTML = '<div style="color:var(--text-muted);font-size:12px;text-align:center;padding:20px;">No monthly data available yet.</div>';
      return;
    }

    const MONTH_LABELS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];

    container.innerHTML = months.map(([key, b]) => {
      const pct = (b.attended / b.conducted) * 100;
      const heightPct = Math.max(6, pct);
      const monthIdx = parseInt(key.split('-')[1], 10) - 1;
      const label = MONTH_LABELS[monthIdx];

      let color, glow;
      if (pct >= 85)      { color = 'var(--emerald)'; glow = 'rgba(16,185,129,0.4)'; }
      else if (pct >= 75) { color = 'var(--amber)';   glow = 'rgba(251,191,36,0.4)'; }
      else                { color = 'var(--rose)';    glow = 'rgba(239,68,68,0.4)'; }

      return `
        <div class="hm-month-bar-group" title="${label}: ${pct.toFixed(1)}% (${b.attended}/${b.conducted})">
          <div class="hm-month-bar-track">
            <div class="hm-month-bar-fill"
                 data-pct="${pct.toFixed(0)}%"
                 style="height:${heightPct}%; background:${color}; box-shadow: 0 0 6px ${glow};">
            </div>
          </div>
          <span class="hm-month-label">${label}</span>
        </div>
      `;
    }).join('');
  },

  // ── 4. Subject Leaderboard ─────────────────────────────────────────────────
  renderSubjectLeaderboard() {
    const container = document.getElementById('hm-leaderboard-list');
    if (!container) return;

    const todayStr = this.formatDate(new Date());
    const subjectMap = new Map();

    for (const [dateStr, info] of this.semesterData.entries()) {
      if (dateStr > todayStr) continue;
      if (!Array.isArray(info.classes)) continue;
      info.classes.forEach(c => {
        const code = c.fsubcode || c.code || 'UNKNOWN';
        const name = c.fsubname || c.name || code;
        const cnt  = parseInt(c.fnoclass || 1, 10);
        const pres = (c.fpresent === '1' || c.fpresent === 1 || String(c.fpresent).toUpperCase() === 'P');
        if (!subjectMap.has(code)) subjectMap.set(code, { name, conducted: 0, attended: 0 });
        const sb = subjectMap.get(code);
        sb.conducted += cnt;
        if (pres) sb.attended += cnt;
      });
    }

    const ranked = Array.from(subjectMap.entries())
      .filter(([, sb]) => sb.conducted > 0)
      .map(([code, sb]) => ({ code, ...sb, pct: (sb.attended / sb.conducted) * 100 }))
      .sort((a, b) => b.pct - a.pct);

    if (ranked.length === 0) {
      container.innerHTML = '<div style="color:var(--text-muted);font-size:13px;padding:16px 0;">No subject data available yet. Load the heatmap first.</div>';
      return;
    }

    const RANK_CLASSES = ['gold', 'silver', 'bronze'];
    const RANK_MEDALS  = ['🥇', '🥈', '🥉'];

    container.innerHTML = ranked.map((s, i) => {
      const rankClass = RANK_CLASSES[i] || '';
      const rankLabel = i < 3 ? RANK_MEDALS[i] : `#${i + 1}`;

      let pctColor, barColor;
      if (s.pct >= 85)      { pctColor = 'var(--emerald)'; barColor = 'var(--emerald)'; }
      else if (s.pct >= 75) { pctColor = 'var(--amber)';   barColor = 'var(--amber)'; }
      else                  { pctColor = 'var(--rose)';    barColor = 'var(--rose)'; }

      const safeCode = escapeHtml(s.code);
      const safeName = escapeHtml(s.name.length > 40 ? s.name.substring(0, 38) + '…' : s.name);

      return `
        <div class="hm-lb-item" title="${escapeHtml(s.name)}: ${s.pct.toFixed(1)}% (${s.attended}/${s.conducted} classes)">
          <div class="hm-lb-rank ${rankClass}">${rankLabel}</div>
          <div>
            <div class="hm-lb-name">${safeName}</div>
            <div class="hm-lb-code">${safeCode}</div>
          </div>
          <div class="hm-lb-pct" style="color:${pctColor};">${s.pct.toFixed(1)}%</div>
          <div style="grid-column:2/-1;margin-top:4px;">
            <div style="height:4px;background:rgba(255,255,255,0.07);border-radius:3px;overflow:hidden;">
              <div style="height:100%;width:${Math.min(100,s.pct)}%;background:${barColor};border-radius:3px;transition:width 0.8s cubic-bezier(.34,1.56,.64,1);box-shadow:0 0 6px ${barColor}55;"></div>
            </div>
          </div>
        </div>
      `;
    }).join('');
  },

  renderMetrics() {
    const streakEl = document.getElementById('heatmap-active-streak');
    const bestStreakEl = document.getElementById('heatmap-best-streak');
    const flawlessEl = document.getElementById('heatmap-flawless-count');
    const consistencyEl = document.getElementById('heatmap-consistency-pct');

    if (streakEl) streakEl.textContent = `${this.activeStreak} Days`;
    if (bestStreakEl) bestStreakEl.textContent = `${this.maxStreak} Days`;
    if (flawlessEl) flawlessEl.textContent = `${this.flawlessDays} Days`;
    if (consistencyEl) consistencyEl.textContent = `${this.consistencyPct}%`;
  },

  renderGrid() {
    const gridContainer = document.getElementById('heatmap-cells-grid');
    const monthsContainer = document.getElementById('heatmap-months-labels');
    if (!gridContainer) return;

    const today = new Date();
    const todayStr = this.formatDate(today);

    // Find upcoming Sunday (end of current week)
    const endOfWeek = new Date(today.getFullYear(), today.getMonth(), today.getDate());
    const dayOfWeek = endOfWeek.getDay(); // 0 = Sun
    const daysUntilSunday = (7 - dayOfWeek) % 7;
    endOfWeek.setDate(endOfWeek.getDate() + daysUntilSunday);

    // Build 32 weeks (224 days) for authentic GitHub-style span
    const totalWeeks = 32;
    const totalDays = totalWeeks * 7;
    const startDate = new Date(endOfWeek.getFullYear(), endOfWeek.getMonth(), endOfWeek.getDate());
    startDate.setDate(startDate.getDate() - totalDays + 1);

    const cellWidth = 16;
    const cellGap = 4;
    const colWidth = cellWidth + cellGap; // 20px per week column

    const weeks = [];
    let currentWeek = [];
    const monthLabels = [];
    let lastMonth = -1;

    let curr = new Date(startDate.getFullYear(), startDate.getMonth(), startDate.getDate());

    for (let d = 0; d < totalDays; d++) {
      const dateStr = this.formatDate(curr);
      const dayIndex = (curr.getDay() + 6) % 7; // Monday = 0, Sunday = 6

      currentWeek.push({
        dateStr,
        dayIndex,
        isFuture: dateStr > todayStr,
        dateObj: new Date(curr.getTime()),
        data: this.getDayData(dateStr)
      });

      if (currentWeek.length === 7) {
        weeks.push(currentWeek);
        currentWeek = [];
      }

      curr.setDate(curr.getDate() + 1);
    }

    if (currentWeek.length > 0) {
      weeks.push(currentWeek);
    }

    // Determine month positions based on first day of month in each column
    weeks.forEach((week, weekIndex) => {
      const firstOfMonth = week.find(day => day.dateObj.getDate() === 1);
      if (firstOfMonth) {
        const prev = monthLabels[monthLabels.length - 1];
        if (!prev || (weekIndex - prev.weekIndex) >= 3) {
          monthLabels.push({
            weekIndex,
            name: firstOfMonth.dateObj.toLocaleDateString('en-US', { month: 'short' })
          });
          lastMonth = firstOfMonth.dateObj.getMonth();
        }
      } else if (weekIndex === 0) {
        monthLabels.push({
          weekIndex: 0,
          name: week[0].dateObj.toLocaleDateString('en-US', { month: 'short' })
        });
        lastMonth = week[0].dateObj.getMonth();
      }
    });

    const totalGridWidth = totalWeeks * colWidth;

    // Render Month Headers with EXACT pixel alignment matching the column widths
    if (monthsContainer) {
      monthsContainer.style.width = `${totalGridWidth}px`;
      monthsContainer.style.minWidth = `${totalGridWidth}px`;
      monthsContainer.innerHTML = monthLabels.map(m => {
        const leftPx = m.weekIndex * colWidth;
        return `<span class="heatmap-month-tag" style="left: ${leftPx}px">${m.name}</span>`;
      }).join('');
    }

    gridContainer.style.width = `${totalGridWidth}px`;
    gridContainer.style.minWidth = `${totalGridWidth}px`;

    // Render Matrix Columns & Cells
    let gridHtml = '';
    weeks.forEach(week => {
      gridHtml += '<div class="heatmap-week-col">';
      week.forEach(day => {
        let level = 0;
        if (!day.isFuture && day.data.conducted > 0) {
          const pct = day.data.attended / day.data.conducted;
          if (pct === 1) level = 4;        // 100% Flawless Emerald
          else if (pct >= 0.75) level = 3;  // Good Mint
          else if (day.data.attended > 0) level = 2; // Partial Amber
          else level = 1;                   // Missed all Crimson
        }

        const isToday = day.dateStr === todayStr;

        gridHtml += `
          <div class="heatmap-cell level-${level} ${isToday ? 'is-today' : ''} ${day.isFuture ? 'is-future' : ''}"
               data-date="${day.dateStr}"
               data-level="${level}"
               title="${day.dateStr}">
          </div>
        `;
      });
      gridHtml += '</div>';
    });

    gridContainer.innerHTML = gridHtml;
  }
};

window.HeatmapView = HeatmapView;
