/**
 * Subject Summary & 85% Attendance Bunk Planner
 */

const SummaryView = {
  rawSubjects: [],
  simulatedDeltas: new Map(), // subCode -> { extraConducted: 0, extraAttended: 0 }
  currentTargetPct: 85, // 85% standard
  searchQuery: '',
  isInitialized: false,

  init() {
    if (this.isInitialized) return;
    this.isInitialized = true;
    this.bindEvents();
  },

  bindEvents() {
    // Target pill selectors (75, 80, 85, 90)
    const pills = document.querySelectorAll('.target-pill-btn');
    pills.forEach(pill => {
      pill.addEventListener('click', () => {
        pills.forEach(p => p.classList.remove('active'));
        pill.classList.add('active');
        this.currentTargetPct = parseInt(pill.getAttribute('data-target'), 10) || 85;
        this.render();
      });
    });

    // Absent modal close buttons and backdrop
    const closeAbsentBtn = document.getElementById('btn-close-absent');
    const absentBackdrop = document.getElementById('absent-modal-backdrop');
    if (closeAbsentBtn) {
      closeAbsentBtn.addEventListener('click', () => this.closeAbsentModal());
    }
    if (absentBackdrop) {
      absentBackdrop.addEventListener('click', () => this.closeAbsentModal());
    }
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') {
        const modal = document.getElementById('absent-dates-modal');
        if (modal && !modal.classList.contains('hidden')) {
          this.closeAbsentModal();
        }
      }
    });
  },

  async load() {
    const gridEl = document.getElementById('subjects-grid');
    if (gridEl) {
      gridEl.innerHTML = '<div class="empty-state" style="grid-column: 1 / -1;"><div class="btn-spinner"></div><p>Calculating subject attendance summary...</p></div>';
    }

    try {
      const todayStr = new Date().toISOString().split('T')[0];
      const res = await API.getAttendanceSummary(todayStr);

      if (res.success && Array.isArray(res.data)) {
        this.rawSubjects = res.data;
      } else {
        this.rawSubjects = [];
      }
    } catch (err) {
      console.error('Failed to load attendance summary:', err);
      this.rawSubjects = [];
    }

    this.render();
  },

  // Compute 85% advice
  calculateBunkMetrics(conducted, attended, targetPct = 85) {
    const T = targetPct / 100;
    const currentPct = conducted > 0 ? (attended / conducted) * 100 : 100;

    if (conducted === 0) {
      return {
        percentage: 100,
        canBunk: 0,
        mustAttend: 0,
        status: 'safe',
        msgMain: 'No classes conducted yet',
        msgSub: 'Attendance will start calculating after first class'
      };
    }

    if (currentPct >= targetPct) {
      // Safe to bunk: floor( (Attended / T) - Conducted )
      const maxCanBunk = Math.floor((attended / T) - conducted);
      if (maxCanBunk > 0) {
        return {
          percentage: currentPct,
          canBunk: maxCanBunk,
          mustAttend: 0,
          status: 'safe',
          msgMain: `You can safely bunk ${maxCanBunk} class${maxCanBunk > 1 ? 'es' : ''}`,
          msgSub: `Maintains attendance at or above ${targetPct}%`
        };
      } else {
        return {
          percentage: currentPct,
          canBunk: 0,
          mustAttend: 0,
          status: 'safe-border',
          msgMain: `On the edge (${currentPct.toFixed(1)}%)`,
          msgSub: `Cannot miss any class without dropping below ${targetPct}%`
        };
      }
    } else {
      // Below target: ceil( (T * Conducted - Attended) / (1 - T) )
      const needed = Math.ceil(((T * conducted) - attended) / (1 - T));
      return {
        percentage: currentPct,
        canBunk: 0,
        mustAttend: Math.max(1, needed),
        status: 'critical',
        msgMain: `Must attend next ${needed} class${needed > 1 ? 'es' : ''}`,
        msgSub: `Required consecutive attendances to reach ${targetPct}%`
      };
    }
  },

  showOnlyDanger: false,

  render() {
    this.renderAggregateHero();
    this.renderSubjectsGrid();
    if (window.Simulator && typeof window.Simulator.populateSubjects === 'function') {
      window.Simulator.populateSubjects(this.rawSubjects);
    }
  },

  renderDangerRadar() {
    // Disabled as requested: Alert system removed from top of Subject Planner
    const radarContainer = document.getElementById('danger-radar-container');
    if (radarContainer) {
      radarContainer.innerHTML = '';
      radarContainer.classList.add('hidden');
    }
  },

  renderAggregateHero() {
    let aggConducted = 0;
    let aggAttended = 0;

    this.rawSubjects.forEach(s => {
      const subCode = s.fsubcode || s.code || '';
      const delta = this.simulatedDeltas.get(subCode) || { extraConducted: 0, extraAttended: 0 };
      const cond = parseInt(s.conducted ?? s.ftotalclass ?? 0, 10) + delta.extraConducted;
      const att = parseInt(s.attended ?? s.fpresentclass ?? 0, 10) + delta.extraAttended;
      aggConducted += cond;
      aggAttended += att;
    });

    const aggPct = aggConducted > 0 ? (aggAttended / aggConducted) * 100 : 100;
    const aggMetrics = this.calculateBunkMetrics(aggConducted, aggAttended, this.currentTargetPct);

    // Update gauge & labels
    const pctEl = document.getElementById('overall-percentage');
    const barEl = document.getElementById('aggregate-gauge-bar');
    const chipEl = document.getElementById('overall-status-chip');
    const statusTextEl = document.getElementById('overall-status-text');
    const headlineEl = document.getElementById('overall-headline');
    const subtextEl = document.getElementById('overall-subtext');

    const totalCondEl = document.getElementById('total-conducted');
    const totalAttEl = document.getElementById('total-attended');
    const totalBunksEl = document.getElementById('total-bunks-available');

    if (totalCondEl) totalCondEl.textContent = aggConducted;
    if (totalAttEl) totalAttEl.textContent = aggAttended;

    if (pctEl) pctEl.textContent = `${aggPct.toFixed(1)}%`;

    // Radial Gauge SVG stroke calculation (Circumference = 2 * PI * 52 = 326.72)
    if (barEl) {
      const radius = 52;
      const circumference = 2 * Math.PI * radius;
      const offset = circumference - (Math.min(100, Math.max(0, aggPct)) / 100) * circumference;
      barEl.style.strokeDasharray = `${circumference}`;
      barEl.style.strokeDashoffset = `${offset}`;

      if (aggPct >= this.currentTargetPct) {
        barEl.style.stroke = 'var(--emerald)';
      } else if (aggPct >= this.currentTargetPct - 10) {
        barEl.style.stroke = 'var(--amber)';
      } else {
        barEl.style.stroke = 'var(--rose)';
      }
    }

    if (chipEl && statusTextEl) {
      chipEl.className = 'status-chip';
      if (aggPct >= this.currentTargetPct) {
        chipEl.classList.add('safe');
        statusTextEl.textContent = `Above ${this.currentTargetPct}% Target`;
      } else if (aggPct >= this.currentTargetPct - 10) {
        chipEl.classList.add('warning');
        statusTextEl.textContent = `Close to ${this.currentTargetPct}% Target`;
      } else {
        chipEl.classList.add('danger');
        statusTextEl.textContent = `Below ${this.currentTargetPct}% Target`;
      }
    }

    if (headlineEl) {
      if (aggMetrics.canBunk > 0) {
        headlineEl.textContent = `${aggMetrics.canBunk} Safe Skip Hours Available`;
      } else if (aggMetrics.mustAttend > 0) {
        headlineEl.textContent = `Need ${aggMetrics.mustAttend} Classes to Recover Target`;
      } else {
        headlineEl.textContent = `Safe Margin Maintained`;
      }
    }

    if (subtextEl) {
      subtextEl.textContent = `${aggMetrics.msgMain} across all subjects to hold ${this.currentTargetPct}% attendance.`;
    }

    if (totalBunksEl) {
      if (aggMetrics.canBunk > 0) {
        totalBunksEl.className = 'amb-val text-emerald';
        totalBunksEl.textContent = `+${aggMetrics.canBunk}`;
      } else if (aggMetrics.mustAttend > 0) {
        totalBunksEl.className = 'amb-val text-rose';
        totalBunksEl.textContent = `-${aggMetrics.mustAttend}`;
      } else {
        totalBunksEl.className = 'amb-val text-contrast';
        totalBunksEl.textContent = `0`;
      }
    }
  },

  renderSubjectsGrid() {
    const gridEl = document.getElementById('subjects-grid');
    const countBadgeEl = document.getElementById('subject-count-badge');
    if (!gridEl) return;

    const filtered = this.rawSubjects.filter(s => {
      const cond = parseInt(s.conducted ?? s.ftotalclass ?? 0, 10);
      const att = parseInt(s.attended ?? s.fpresentclass ?? 0, 10);
      const pct = cond > 0 ? (att / cond) * 100 : 100;

      if (this.showOnlyDanger && pct >= 85) {
        return false;
      }

      if (!this.searchQuery) return true;
      const code = (s.fsubcode || s.code || '').toLowerCase();
      const name = (s.fsubname || s.name || '').toLowerCase();
      return code.includes(this.searchQuery) || name.includes(this.searchQuery);
    });

    if (countBadgeEl) {
      countBadgeEl.textContent = this.showOnlyDanger 
        ? `${filtered.length} At-Risk Subject${filtered.length !== 1 ? 's' : ''}`
        : `${filtered.length} Subject${filtered.length !== 1 ? 's' : ''}`;
    }

    const escape = (str) => (window.escapeHtml ? window.escapeHtml(str) : String(str || ''));

    if (filtered.length === 0) {
      gridEl.innerHTML = `
        <div class="empty-state" style="grid-column: 1 / -1;">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="11" cy="11" r="8"></circle>
            <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
          </svg>
          <p>No subjects match your search "${escape(this.searchQuery)}".</p>
        </div>
      `;
      return;
    }

    let cardsHtml = '';
    filtered.forEach(s => {
      const code = s.fsubcode || s.code || 'N/A';
      const name = s.fsubname || s.name || 'Course Name';

      const safeCode = escape(code);
      const safeName = escape(name);
      const encCode = encodeURIComponent(code);
      const encName = encodeURIComponent(name);

      const sim = this.simulatedDeltas.get(code) || { extraConducted: 0, extraAttended: 0 };
      const baseCond = parseInt(s.conducted ?? s.ftotalclass ?? 0, 10);
      const baseAtt = parseInt(s.attended ?? s.fpresentclass ?? 0, 10);

      const conducted = baseCond + sim.extraConducted;
      const attended = baseAtt + sim.extraAttended;
      const missed = conducted - attended;

      const pct = conducted > 0 ? (attended / conducted) * 100 : 100;
      const advice = this.calculateBunkMetrics(conducted, attended, this.currentTargetPct);

      let colorVar = 'var(--emerald)';
      let textClass = 'text-emerald';
      let statusLabel = 'On Track';

      if (pct < this.currentTargetPct) {
        if (pct >= this.currentTargetPct - 10) {
          colorVar = 'var(--amber)';
          textClass = 'text-amber';
          statusLabel = 'Warning';
        } else {
          colorVar = 'var(--rose)';
          textClass = 'text-rose';
          statusLabel = 'Critical Shortage';
        }
      }

      // Icon for advice box
      let adviceIconSvg = '';
      if (advice.status === 'safe') {
        adviceIconSvg = `
          <svg class="bunk-icon text-emerald" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
            <polyline points="22 4 12 14.01 9 11.01"></polyline>
          </svg>
        `;
      } else if (advice.status === 'safe-border') {
        adviceIconSvg = `
          <svg class="bunk-icon text-amber" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="12" y1="8" x2="12" y2="12"></line>
            <line x1="12" y1="16" x2="12.01" y2="16"></line>
          </svg>
        `;
      } else {
        adviceIconSvg = `
          <svg class="bunk-icon text-rose" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polygon points="7.86 2 16.14 2 22 7.86 22 16.14 16.14 22 7.86 22 2 16.14 2 7.86 7.86 2"></polygon>
            <line x1="12" y1="8" x2="12" y2="12"></line>
            <line x1="12" y1="16" x2="12.01" y2="16"></line>
          </svg>
        `;
      }

      const hasSimulation = sim.extraConducted > 0;

      cardsHtml += `
        <div class="subject-card" data-subcode="${safeCode}">
          <!-- Header -->
          <div class="subject-card-header">
            <div class="subject-identity">
              <span class="subject-code-tag">${safeCode}</span>
              <h4 class="subject-card-title">${safeName}</h4>
            </div>
            <div class="subject-percentage-badge">
              <span class="subject-pct-number ${textClass}">${pct.toFixed(1)}%</span>
              <span class="subject-status-mini ${textClass}">${statusLabel}</span>
            </div>
          </div>

          <!-- Progress Bar -->
          <div class="progress-container">
            <div class="progress-track">
              <div class="progress-marker-85" style="left: ${this.currentTargetPct}%;" title="Target ${this.currentTargetPct}% Marker"></div>
              <div class="progress-fill" style="width: ${Math.min(100, pct)}%; background-color: ${colorVar};"></div>
            </div>
            <div class="progress-stats-line">
              <span class="stat-pill-attended">Attended: <strong>${attended}</strong> / ${conducted}</span>
              <span class="${missed > 0 ? 'text-missed-link' : ''}" 
                ${missed > 0 ? `onclick="SummaryView.showAbsentDates('${encCode}', '${encName}', ${missed})" title="Click to view absent dates"` : ''}>
                Missed: <strong>${missed}</strong> class${missed !== 1 ? 'es' : ''}
              </span>
            </div>
          </div>

          <!-- 85% Bunk Advice Box -->
          <div class="bunk-advice-box ${advice.status}">
            ${adviceIconSvg}
            <div class="bunk-text">
              <span class="bunk-main-msg">${advice.msgMain}</span>
              <span class="bunk-sub-msg">${advice.msgSub}</span>
            </div>
          </div>

          <!-- What-If Simulator Strip -->
          <div class="simulator-strip">
            <span class="sim-label">
              ${hasSimulation ? `Sim: +${sim.extraAttended} att / +${sim.extraConducted} total` : 'What-If Simulator'}
            </span>
            <div class="sim-actions">
              ${missed > 0 ? `
              <button type="button" class="sim-btn sim-btn-absent" 
                onclick="SummaryView.showAbsentDates('${encCode}', '${encName}', ${missed})" 
                title="View all ${missed} absent date${missed > 1 ? 's' : ''} for this course">
                <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
                  <line x1="16" y1="2" x2="16" y2="6"></line>
                  <line x1="8" y1="2" x2="8" y2="6"></line>
                  <line x1="3" y1="10" x2="21" y2="10"></line>
                </svg>
                <span>Dates (${missed})</span>
              </button>
              ` : ''}
              <button class="sim-btn" onclick="SummaryView.simulate('${encCode}', 1, 1)" title="Simulate attending next class">+1 Attend</button>
              <button class="sim-btn sim-btn-danger" onclick="SummaryView.simulate('${encCode}', 1, 0)" title="Simulate skipping next class">+1 Miss</button>
              ${hasSimulation ? `<button class="sim-reset-btn" onclick="SummaryView.resetSimulation('${encCode}')">Reset</button>` : ''}
            </div>
          </div>
        </div>
      `;
    });

    gridEl.innerHTML = cardsHtml;
  },

  simulate(subCodeEncoded, addConducted, addAttended) {
    const subCode = decodeURIComponent(subCodeEncoded);
    const current = this.simulatedDeltas.get(subCode) || { extraConducted: 0, extraAttended: 0 };
    current.extraConducted += addConducted;
    current.extraAttended += addAttended;
    this.simulatedDeltas.set(subCode, current);
    this.render();
  },

  resetSimulation(subCodeEncoded) {
    const subCode = decodeURIComponent(subCodeEncoded);
    this.simulatedDeltas.delete(subCode);
    this.render();
  },

  showAbsentDates(subCodeEncoded, subNameEncoded, missedCount) {
    const subCode = decodeURIComponent(subCodeEncoded);
    const subName = decodeURIComponent(subNameEncoded);
    const modal = document.getElementById('absent-dates-modal');
    if (!modal) return;

    const subCodeEl = document.getElementById('absent-modal-subcode');
    const subNameEl = document.getElementById('absent-modal-subname');
    const titleEl = document.getElementById('absent-modal-title');
    const listContainer = document.getElementById('absent-modal-list');

    if (subCodeEl) subCodeEl.textContent = `// ${subCode}`;
    if (titleEl) titleEl.textContent = 'Absent Classes Log';
    if (subNameEl) subNameEl.textContent = `${subName} • ${missedCount} Missed Class${missedCount !== 1 ? 'es' : ''}`;

    if (listContainer) {
      listContainer.innerHTML = '<div class="empty-state"><div class="btn-spinner"></div><p>Scanning semester attendance records...</p></div>';
    }
    modal.classList.remove('hidden');

    setTimeout(() => {
      this.populateAbsentDates(subCode, subName, missedCount);
    }, 80);
  },

  populateAbsentDates(subCode, subName, missedCount) {
    const listContainer = document.getElementById('absent-modal-list');
    if (!listContainer) return;

    if (missedCount === 0) {
      listContainer.innerHTML = `
        <div class="absent-empty-perfect">
          <div class="perfect-trophy-icon">🏆</div>
          <h4 class="perfect-headline">Zero Absences!</h4>
          <p class="perfect-subtext">You have attended 100% of the conducted classes for <strong>${window.escapeHtml ? window.escapeHtml(subName) : subName}</strong>. Keep up the flawless streak!</p>
        </div>
      `;
      return;
    }

    const absentRecords = [];
    const allDaysMap = new Map();

    // 1. HeatmapView semester data
    if (window.HeatmapView && window.HeatmapView.semesterData) {
      for (const [dateStr, info] of window.HeatmapView.semesterData.entries()) {
        allDaysMap.set(dateStr, info);
      }
    }

    // 2. CalendarView month data
    if (window.CalendarView && window.CalendarView.monthDataCache) {
      for (const monthObj of window.CalendarView.monthDataCache.values()) {
        if (monthObj && typeof monthObj === 'object') {
          for (const [dateStr, info] of Object.entries(monthObj)) {
            if (!allDaysMap.has(dateStr)) {
              allDaysMap.set(dateStr, info);
            }
          }
        }
      }
    }

    // 3. sessionStorage
    try {
      for (let i = 0; i < sessionStorage.length; i++) {
        const k = sessionStorage.key(i);
        if (k && k.startsWith('att_month_')) {
          const parsed = JSON.parse(sessionStorage.getItem(k));
          if (parsed && typeof parsed === 'object') {
            for (const [dateStr, info] of Object.entries(parsed)) {
              if (!allDaysMap.has(dateStr)) {
                allDaysMap.set(dateStr, info);
              }
            }
          }
        }
      }
    } catch (e) {}

    // Match code
    const cleanTargetCode = subCode.replace(/[^a-zA-Z0-9]/g, '').toLowerCase();

    for (const [dateStr, dayInfo] of allDaysMap.entries()) {
      if (dayInfo && Array.isArray(dayInfo.classes)) {
        dayInfo.classes.forEach(c => {
          const cCode = (c.fsubcode || c.code || '').replace(/[^a-zA-Z0-9]/g, '').toLowerCase();
          const isMatch = cCode === cleanTargetCode || (c.fsubcode && c.fsubcode.trim() === subCode.trim());
          if (isMatch) {
            const isPres = c.fpresent === '1' || c.fpresent === 1 || String(c.fpresent).trim().toUpperCase() === 'P' || String(c.fpresent).toLowerCase() === 'present';
            if (!isPres) {
              const dObj = new Date(dateStr + 'T00:00:00');
              absentRecords.push({
                dateStr,
                dateObj: dObj,
                period: c.fperiod || '1',
                hours: parseInt(c.fnoclass || '1', 10),
                teacher: c.fteachname || ''
              });
            }
          }
        });
      }
    }

    // Sort descending (most recent first)
    absentRecords.sort((a, b) => b.dateStr.localeCompare(a.dateStr));

    if (absentRecords.length === 0) {
      listContainer.innerHTML = `
        <div class="empty-state">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="12" cy="12" r="10"></circle>
            <polyline points="12 6 12 12 14 14"></polyline>
          </svg>
          <p>You have missed <strong>${missedCount} class${missedCount > 1 ? 'es' : ''}</strong> in this subject. Detailed timetable dates are still loading from the portal.</p>
          <button class="btn-primary" style="margin-top: 10px;" onclick="CalendarView.render(); App.switchTab('tab-calendar'); SummaryView.closeAbsentModal();">Inspect in Calendar</button>
        </div>
      `;
      return;
    }

    let itemsHtml = '';
    absentRecords.forEach(rec => {
      const formattedDate = rec.dateObj.toLocaleDateString('en-US', {
        weekday: 'short',
        month: 'short',
        day: 'numeric',
        year: 'numeric'
      });

      itemsHtml += `
        <div class="absent-date-card">
          <div class="absent-card-left">
            <div class="absent-calendar-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
                <line x1="16" y1="2" x2="16" y2="6"></line>
                <line x1="8" y1="2" x2="8" y2="6"></line>
                <line x1="3" y1="10" x2="21" y2="10"></line>
              </svg>
            </div>
            <div class="absent-details">
              <span class="absent-date-text">${formattedDate}</span>
              <span class="absent-period-meta">Period ${rec.period} • ${rec.hours} Hour${rec.hours > 1 ? 's' : ''}${rec.teacher ? ` • Prof. ${window.escapeHtml ? window.escapeHtml(rec.teacher) : rec.teacher}` : ''}</span>
            </div>
          </div>
          <div class="absent-card-right">
            <span class="absent-badge-pill">Absent</span>
            <button type="button" class="btn-jump-calendar" onclick="SummaryView.jumpToDate('${rec.dateStr}')" title="Inspect this date in Calendar">
              <span>View Day</span>
              <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"></polyline></svg>
            </button>
          </div>
        </div>
      `;
    });

    listContainer.innerHTML = `
      <div class="absent-records-summary">
        <span>Recorded <strong>${absentRecords.length}</strong> missed class${absentRecords.length > 1 ? 'es' : ''}</span>
      </div>
      <div class="absent-records-list">
        ${itemsHtml}
      </div>
    `;
  },

  jumpToDate(dateStr) {
    this.closeAbsentModal();
    if (window.App) {
      App.switchTab('tab-calendar');
      const [y, m, d] = dateStr.split('-').map(Number);
      CalendarView.currentYear = y;
      CalendarView.currentMonth = m - 1;
      CalendarView.selectedDateStr = dateStr;
      CalendarView.render();
      CalendarView.loadDateDetails(dateStr);
    }
  },

  closeAbsentModal() {
    const modal = document.getElementById('absent-dates-modal');
    if (modal) modal.classList.add('hidden');
  }
};

window.SummaryView = SummaryView;
