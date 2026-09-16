/**
 * GitHub-Style Attendance Activity Matrix & Streak Engine
 * Features 20-Week Heatmap, Streaks, Subject Filtering & Day Inspection
 */

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
                const subName = c.fsubname || c.name || c.fsubcode || 'Academic Class';
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

      // Fetch the last 8 months of attendance (Feb to Sep, covers entire 32-week academic span)
      const monthsToFetch = [];
      for (let i = 7; i >= 0; i--) {
        let m = currentMonth - i;
        let y = currentYear;
        if (m <= 0) {
          m += 12;
          y -= 1;
        }
        monthsToFetch.push({ year: y, month: m });
      }

      const results = await Promise.all(
        monthsToFetch.map(({ year, month }) => API.getMonthAttendance(year, month).catch(() => ({})))
      );

      this.semesterData.clear();
      results.forEach(res => {
        if (res && res.success && res.monthData) {
          for (const [dateStr, info] of Object.entries(res.monthData)) {
            this.semesterData.set(dateStr, info);
          }
        }
      });

      this.populateSubjectFilter();
      this.calculateStreaks();
      this.render();
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
      optionsHtml += `<option value="${code}" ${this.selectedSubject === code ? 'selected' : ''}>${code} - ${name}</option>`;
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
    this.renderSmartInsights();
    this.renderWeeklyPattern();
    this.renderMonthlyTrend();
    this.renderSubjectLeaderboard();
  },

  renderSmartInsights() {
    const dayStats = {
      1: { name: 'Monday', conducted: 0, attended: 0 },
      2: { name: 'Tuesday', conducted: 0, attended: 0 },
      3: { name: 'Wednesday', conducted: 0, attended: 0 },
      4: { name: 'Thursday', conducted: 0, attended: 0 },
      5: { name: 'Friday', conducted: 0, attended: 0 },
      6: { name: 'Saturday', conducted: 0, attended: 0 }
    };

    let totalConducted = 0;
    let totalAttended = 0;

    for (const [dateStr, info] of this.semesterData.entries()) {
      const [y, m, d] = dateStr.split('-').map(Number);
      const dayOfWeek = new Date(y, m - 1, d).getDay();
      if (dayStats[dayOfWeek] && info.conducted > 0) {
        dayStats[dayOfWeek].conducted += (info.conducted || 0);
        dayStats[dayOfWeek].attended += (info.attended || 0);
        totalConducted += (info.conducted || 0);
        totalAttended += (info.attended || 0);
      }
    }

    // Weakest Day
    let weakestDayName = '—';
    let weakestDayPct = null;
    let minPct = Infinity;

    for (let day = 1; day <= 6; day++) {
      const stat = dayStats[day];
      if (stat.conducted > 0) {
        const pct = Math.round((stat.attended / stat.conducted) * 100);
        if (pct < minPct) {
          minPct = pct;
          weakestDayName = stat.name;
          weakestDayPct = pct;
        }
      }
    }

    const weakestValEl = document.getElementById('insight-weakest-day');
    const weakestSubEl = document.getElementById('insight-weakest-day-sub');
    if (weakestValEl) weakestValEl.textContent = weakestDayName;
    if (weakestSubEl) weakestSubEl.textContent = weakestDayPct !== null ? `${weakestDayPct}% avg attendance` : 'No data recorded';

    // Best Subject
    let subjectsList = [];
    if (window.SummaryView && Array.isArray(SummaryView.rawSubjects) && SummaryView.rawSubjects.length > 0) {
      subjectsList = SummaryView.rawSubjects.map(s => {
        const cond = parseInt(s.ftotalclass || s.conducted || 0, 10);
        const att = parseInt(s.fpresentclass || s.attended || 0, 10);
        const pct = cond > 0 ? (att / cond) * 100 : 0;
        return {
          code: s.fsubcode || s.code || '',
          name: s.fsubname || s.name || s.fsubcode || '',
          conducted: cond,
          attended: att,
          pct
        };
      });
    } else {
      const subMap = new Map();
      for (const info of this.semesterData.values()) {
        if (Array.isArray(info.classes)) {
          info.classes.forEach(c => {
            const code = c.fsubcode || c.code;
            const name = c.fsubname || c.name || code;
            if (code) {
              if (!subMap.has(code)) subMap.set(code, { code, name, conducted: 0, attended: 0 });
              const entry = subMap.get(code);
              const cnt = parseInt(c.fnoclass || 1, 10);
              entry.conducted += cnt;
              if (c.fpresent === '1' || c.attended > 0 || String(c.fpresent).toUpperCase() === 'P') {
                entry.attended += cnt;
              }
            }
          });
        }
      }
      subjectsList = Array.from(subMap.values()).map(s => ({
        ...s,
        pct: s.conducted > 0 ? (s.attended / s.conducted) * 100 : 0
      }));
    }

    let bestSubName = '—';
    let bestSubPct = 0;
    if (subjectsList.length > 0) {
      const sortedSubs = [...subjectsList].sort((a, b) => b.pct - a.pct || b.conducted - a.conducted);
      bestSubName = sortedSubs[0].name;
      bestSubPct = sortedSubs[0].pct;
    }

    const bestValEl = document.getElementById('insight-best-subject');
    const bestSubEl = document.getElementById('insight-best-subject-sub');
    if (bestValEl) {
      bestValEl.textContent = bestSubName;
      bestValEl.title = bestSubName;
    }
    if (bestSubEl) {
      bestSubEl.textContent = bestSubPct === 100 ? '100% perfect attendance' : `${bestSubPct.toFixed(1)}% attendance`;
    }

    // Classes Missed
    const totalMissed = Math.max(0, totalConducted - totalAttended);
    const missedPct = totalConducted > 0 ? ((totalMissed / totalConducted) * 100).toFixed(1) : '0.0';

    const missedValEl = document.getElementById('insight-classes-missed');
    const missedSubEl = document.getElementById('insight-classes-missed-sub');
    if (missedValEl) missedValEl.textContent = `${totalMissed} class${totalMissed === 1 ? '' : 'es'}`;
    if (missedSubEl) missedSubEl.textContent = `${missedPct}% of all classes skipped`;

    // Longest Streak
    const streakValEl = document.getElementById('insight-longest-streak');
    const streakSubEl = document.getElementById('insight-longest-streak-sub');
    if (streakValEl) streakValEl.textContent = `${this.maxStreak} day${this.maxStreak === 1 ? '' : 's'}`;
    if (streakSubEl) streakSubEl.textContent = `Active streak: ${this.activeStreak} day${this.activeStreak === 1 ? '' : 's'}`;
  },

  renderWeeklyPattern() {
    const chartContainer = document.getElementById('weekly-pattern-chart');
    if (!chartContainer) return;

    const days = [
      { key: 1, label: 'Mon' },
      { key: 2, label: 'Tue' },
      { key: 3, label: 'Wed' },
      { key: 4, label: 'Thu' },
      { key: 5, label: 'Fri' },
      { key: 6, label: 'Sat' }
    ];

    const dayTotals = { 1: { cond: 0, att: 0 }, 2: { cond: 0, att: 0 }, 3: { cond: 0, att: 0 }, 4: { cond: 0, att: 0 }, 5: { cond: 0, att: 0 }, 6: { cond: 0, att: 0 } };

    for (const [dateStr, info] of this.semesterData.entries()) {
      const [y, m, d] = dateStr.split('-').map(Number);
      const dayOfWeek = new Date(y, m - 1, d).getDay();
      if (dayTotals[dayOfWeek] && info.conducted > 0) {
        dayTotals[dayOfWeek].cond += (info.conducted || 0);
        dayTotals[dayOfWeek].att += (info.attended || 0);
      }
    }

    let html = '';
    days.forEach(d => {
      const stat = dayTotals[d.key];
      const hasClasses = stat.cond > 0;
      const pct = hasClasses ? Math.round((stat.att / stat.cond) * 100) : null;

      let fillClass = 'fill-muted';
      if (pct !== null) {
        if (pct >= 85) fillClass = 'fill-emerald';
        else if (pct >= 75) fillClass = 'fill-amber';
        else fillClass = 'fill-rose';
      }

      const pctText = pct !== null ? `${pct}%` : '—';
      const heightVal = pct !== null ? Math.max(8, pct) : 0;

      html += `
        <div class="bar-column">
          <span class="bar-pct-label">${pctText}</span>
          <div class="bar-track" title="${d.label}: ${stat.att}/${stat.cond} classes attended (${pctText})">
            <div class="bar-fill ${fillClass}" style="height: ${heightVal}%;"></div>
          </div>
          <span class="bar-name-label">${d.label}</span>
        </div>
      `;
    });

    chartContainer.innerHTML = html;
  },

  renderMonthlyTrend() {
    const chartContainer = document.getElementById('monthly-trend-chart');
    if (!chartContainer) return;

    // Group by year-month
    const monthMap = new Map();
    for (const [dateStr, info] of this.semesterData.entries()) {
      const ym = dateStr.substring(0, 7);
      if (!monthMap.has(ym)) {
        monthMap.set(ym, { cond: 0, att: 0 });
      }
      const mEntry = monthMap.get(ym);
      mEntry.cond += (info.conducted || 0);
      mEntry.att += (info.attended || 0);
    }

    const sortedYMs = Array.from(monthMap.keys()).sort();
    const activeMonths = sortedYMs.filter(ym => monthMap.get(ym).cond > 0);
    const monthsToShow = activeMonths.length > 0 ? activeMonths.slice(-6) : sortedYMs.slice(-6);

    let html = '';
    monthsToShow.forEach(ym => {
      const [y, m] = ym.split('-').map(Number);
      const monthName = new Date(y, m - 1, 1).toLocaleDateString('en-US', { month: 'short' });
      const stat = monthMap.get(ym) || { cond: 0, att: 0 };
      const hasClasses = stat.cond > 0;
      const pct = hasClasses ? Math.round((stat.att / stat.cond) * 100) : null;

      let fillClass = 'fill-muted';
      if (pct !== null) {
        if (pct >= 85) fillClass = 'fill-emerald';
        else if (pct >= 75) fillClass = 'fill-amber';
        else fillClass = 'fill-rose';
      }

      const pctText = pct !== null ? `${pct}%` : '—';
      const heightVal = pct !== null ? Math.max(8, pct) : 0;

      html += `
        <div class="bar-column">
          <span class="bar-pct-label">${pctText}</span>
          <div class="bar-track" title="${monthName} ${y}: ${stat.att}/${stat.cond} classes attended (${pctText})">
            <div class="bar-fill ${fillClass}" style="height: ${heightVal}%;"></div>
          </div>
          <span class="bar-name-label">${monthName}</span>
        </div>
      `;
    });

    if (monthsToShow.length === 0) {
      html = '<div style="margin: auto; color: var(--foreground-muted); font-size: 0.8rem;">No monthly attendance history recorded yet</div>';
    }

    chartContainer.innerHTML = html;
  },

  renderSubjectLeaderboard() {
    const listContainer = document.getElementById('subject-leaderboard-list');
    if (!listContainer) return;

    let subjects = [];
    if (window.SummaryView && Array.isArray(SummaryView.rawSubjects) && SummaryView.rawSubjects.length > 0) {
      subjects = SummaryView.rawSubjects.map(s => {
        const cond = parseInt(s.ftotalclass || s.conducted || 0, 10);
        const att = parseInt(s.fpresentclass || s.attended || 0, 10);
        const pct = cond > 0 ? (att / cond) * 100 : 0;
        return {
          code: s.fsubcode || s.code || '',
          name: s.fsubname || s.name || s.fsubcode || '',
          conducted: cond,
          attended: att,
          pct
        };
      });
    } else {
      const subMap = new Map();
      for (const info of this.semesterData.values()) {
        if (Array.isArray(info.classes)) {
          info.classes.forEach(c => {
            const code = c.fsubcode || c.code;
            const name = c.fsubname || c.name || code;
            if (code) {
              if (!subMap.has(code)) subMap.set(code, { code, name, conducted: 0, attended: 0 });
              const entry = subMap.get(code);
              const cnt = parseInt(c.fnoclass || 1, 10);
              entry.conducted += cnt;
              if (c.fpresent === '1' || c.attended > 0 || String(c.fpresent).toUpperCase() === 'P') {
                entry.attended += cnt;
              }
            }
          });
        }
      }
      subjects = Array.from(subMap.values()).map(s => ({
        ...s,
        pct: s.conducted > 0 ? (s.attended / s.conducted) * 100 : 0
      }));
    }

    subjects.sort((a, b) => b.pct - a.pct || b.conducted - a.conducted);

    if (subjects.length === 0) {
      listContainer.innerHTML = '<div style="text-align: center; color: var(--foreground-muted); font-size: 0.8rem; padding: 20px;">No subject data found</div>';
      return;
    }

    let html = '';
    subjects.forEach((sub, idx) => {
      let rankBadge = '';
      if (idx === 0) rankBadge = '🥇';
      else if (idx === 1) rankBadge = '🥈';
      else if (idx === 2) rankBadge = '🥉';
      else rankBadge = `<span class="rank-num">#${idx + 1}</span>`;

      let progressClass = 'progress-rose';
      let textClass = 'text-rose';
      if (sub.pct >= 85) {
        progressClass = 'progress-emerald';
        textClass = 'text-emerald';
      } else if (sub.pct >= 75) {
        progressClass = 'progress-amber';
        textClass = 'text-amber';
      }

      html += `
        <div class="leaderboard-row" title="${sub.name} (${sub.code}): ${sub.attended}/${sub.conducted} classes (${sub.pct.toFixed(1)}%)">
          <div class="leaderboard-row-top">
            <div class="leaderboard-sub-info">
              <span class="leaderboard-rank-badge">${rankBadge}</span>
              <div class="leaderboard-sub-text">
                <span class="leaderboard-sub-name">${sub.name}</span>
                <span class="leaderboard-sub-code">${sub.code} • ${sub.attended}/${sub.conducted} classes attended</span>
              </div>
            </div>
            <span class="leaderboard-pct-val ${textClass}">${sub.pct.toFixed(1)}%</span>
          </div>
          <div class="leaderboard-progress-track">
            <div class="leaderboard-progress-bar ${progressClass}" style="width: ${Math.min(100, Math.max(2, sub.pct))}%;"></div>
          </div>
        </div>
      `;
    });

    listContainer.innerHTML = html;
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
