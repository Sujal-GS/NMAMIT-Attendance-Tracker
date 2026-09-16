/**
 * Subject Summary & 85% Attendance Bunk Planner
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

    // Subject search input
    const searchInput = document.getElementById('subject-search-input');
    if (searchInput) {
      searchInput.addEventListener('input', (e) => {
        this.searchQuery = e.target.value.toLowerCase().trim();
        this.render();
      });
    }

    // Delegated event for simulator buttons (avoids XSS via inline onclick)
    const subjectsGrid = document.getElementById('subjects-grid');
    if (subjectsGrid) {
      subjectsGrid.addEventListener('click', (e) => {
        const btn = e.target.closest('[data-sim-action]');
        if (!btn) return;
        const action = btn.dataset.simAction;
        const code = btn.dataset.simCode;
        if (!code) return;
        if (action === 'attend') this.simulate(code, 1, 1);
        else if (action === 'miss') this.simulate(code, 1, 0);
        else if (action === 'reset') this.resetSimulation(code);
      });
    }
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
    this.renderDangerRadar();
    this.renderAggregateHero();
    this.renderSubjectsGrid();
    if (window.Simulator && typeof window.Simulator.populateSubjects === 'function') {
      window.Simulator.populateSubjects(this.rawSubjects);
    }
  },

  renderDangerRadar() {
    const radarContainer = document.getElementById('danger-radar-container');
    if (!radarContainer) return;

    const criticalList = [];
    const warningList = [];

    this.rawSubjects.forEach(s => {
      const cond = parseInt(s.conducted || 0, 10);
      const att = parseInt(s.attended || 0, 10);
      const pct = cond > 0 ? (att / cond) * 100 : 100;
      const subName = s.fsubname || s.name || s.fsubcode || 'Subject';

      if (pct < 75) {
        const needed75 = Math.max(1, Math.ceil(((0.75 * cond) - att) / 0.25));
        criticalList.push({ ...s, subName, pct, needed75 });
      } else if (pct < 85) {
        const needed85 = Math.max(1, Math.ceil(((0.85 * cond) - att) / 0.15));
        warningList.push({ ...s, subName, pct, needed85 });
      }
    });

    if (criticalList.length === 0 && warningList.length === 0) {
      radarContainer.innerHTML = '';
      radarContainer.classList.add('hidden');
      return;
    }

    radarContainer.classList.remove('hidden');
    const isCritical = criticalList.length > 0;

    let itemsHtml = '';
    criticalList.forEach(item => {
      itemsHtml += `
        <div class="radar-item radar-item-critical">
          <div class="radar-item-top">
            <span class="radar-chip chip-critical">EXAM BARRED (&lt;75%)</span>
            <span class="radar-pct text-rose">${item.pct.toFixed(1)}%</span>
          </div>
          <div class="radar-item-body">
            <div class="radar-subname">${item.subName}</div>
          </div>
          <div class="radar-item-footer">
            <div class="radar-prescription radar-pres-critical">
              <span class="radar-pres-icon">⚡</span>
              <span>Attend next <strong>${item.needed75}</strong> consecutive class${item.needed75 > 1 ? 'es' : ''} to reach 75%</span>
            </div>
          </div>
        </div>
      `;
    });

    warningList.forEach(item => {
      itemsHtml += `
        <div class="radar-item radar-item-warning">
          <div class="radar-item-top">
            <span class="radar-chip chip-warning">BUFFER ALERT (&lt;85%)</span>
            <span class="radar-pct text-amber">${item.pct.toFixed(1)}%</span>
          </div>
          <div class="radar-item-body">
            <div class="radar-subname">${item.subName}</div>
          </div>
          <div class="radar-item-footer">
            <div class="radar-prescription radar-pres-warning">
              <span class="radar-pres-icon">⚠️</span>
              <span>Attend next <strong>${item.needed85}</strong> class${item.needed85 > 1 ? 'es' : ''} to regain 85% threshold</span>
            </div>
          </div>
        </div>
      `;
    });

    radarContainer.innerHTML = `
      <div class="danger-radar-card ${isCritical ? 'radar-danger-theme' : 'radar-warning-theme'}">
        <div class="radar-header">
          <div class="radar-header-left">
            <span class="radar-pulse-icon">${isCritical ? '🚨' : '⚠️'}</span>
            <div>
              <div class="radar-title">Low-Attendance Warning Radar</div>
              <div class="radar-subtitle">
                ${criticalList.length > 0 ? `${criticalList.length} subject${criticalList.length > 1 ? 's' : ''} in critical zone (&lt;75%)` : `${warningList.length} subject${warningList.length > 1 ? 's' : ''} below 85% honor threshold`}
              </div>
            </div>
          </div>
          <button type="button" id="btn-toggle-danger-filter" class="btn-radar-filter ${this.showOnlyDanger ? 'active' : ''}">
            ${this.showOnlyDanger ? 'Show All Subjects' : 'Filter At-Risk Only'}
          </button>
        </div>
        <div class="radar-list">
          ${itemsHtml}
        </div>
      </div>
    `;

    const filterBtn = document.getElementById('btn-toggle-danger-filter');
    if (filterBtn) {
      filterBtn.addEventListener('click', () => {
        this.showOnlyDanger = !this.showOnlyDanger;
        this.renderSubjectsGrid();
        this.renderDangerRadar();
      });
    }
  },

  renderAggregateHero() {
    let aggConducted = 0;
    let aggAttended = 0;

    this.rawSubjects.forEach(s => {
      const delta = this.simulatedDeltas.get(s.fsubcode) || { extraConducted: 0, extraAttended: 0 };
      const cond = parseInt(s.conducted || 0, 10) + delta.extraConducted;
      const att = parseInt(s.attended || 0, 10) + delta.extraAttended;
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
      const cond = parseInt(s.conducted || 0, 10);
      const att = parseInt(s.attended || 0, 10);
      const pct = cond > 0 ? (att / cond) * 100 : 100;

      if (this.showOnlyDanger && pct >= 85) {
        return false;
      }

      if (!this.searchQuery) return true;
      const code = (s.fsubcode || '').toLowerCase();
      const name = (s.fsubname || '').toLowerCase();
      return code.includes(this.searchQuery) || name.includes(this.searchQuery);
    });

    if (countBadgeEl) {
      countBadgeEl.textContent = this.showOnlyDanger 
        ? `${filtered.length} At-Risk Subject${filtered.length !== 1 ? 's' : ''}`
        : `${filtered.length} Subject${filtered.length !== 1 ? 's' : ''}`;
    }

    if (filtered.length === 0) {
      gridEl.innerHTML = `
        <div class="empty-state" style="grid-column: 1 / -1;">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="11" cy="11" r="8"></circle>
            <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
          </svg>
          <p>No subjects match your search "${escapeHtml(this.searchQuery)}".</p>
        </div>
      `;
      return;
    }

    let cardsHtml = '';
    filtered.forEach(s => {
      const code = s.fsubcode || 'N/A';
      const name = s.fsubname || 'Course Name';
      const escapedCode = escapeHtml(code);
      const escapedName = escapeHtml(name);

      const sim = this.simulatedDeltas.get(code) || { extraConducted: 0, extraAttended: 0 };
      const baseCond = parseInt(s.conducted || 0, 10);
      const baseAtt = parseInt(s.attended || 0, 10);

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
        <div class="subject-card" data-subcode="${escapedCode}">
          <!-- Header -->
          <div class="subject-card-header">
            <div class="subject-identity">
              <span class="subject-code-tag">${escapedCode}</span>
              <h4 class="subject-card-title">${escapedName}</h4>
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
              <span>Attended: <strong>${attended}</strong> / ${conducted}</span>
              <span>Missed: <strong>${missed}</strong> classes</span>
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
              <button class="sim-btn" data-sim-action="attend" data-sim-code="${escapedCode}" title="Simulate attending next class">+1 Attend</button>
              <button class="sim-btn sim-btn-danger" data-sim-action="miss" data-sim-code="${escapedCode}" title="Simulate skipping next class">+1 Miss</button>
              ${hasSimulation ? `<button class="sim-reset-btn" data-sim-action="reset" data-sim-code="${escapedCode}">Reset</button>` : ''}
            </div>
          </div>
        </div>
      `;
    });

    gridEl.innerHTML = cardsHtml;
  },

  simulate(subCode, addConducted, addAttended) {
    const current = this.simulatedDeltas.get(subCode) || { extraConducted: 0, extraAttended: 0 };
    current.extraConducted += addConducted;
    current.extraAttended += addAttended;
    this.simulatedDeltas.set(subCode, current);
    this.render();
  },

  resetSimulation(subCode) {
    this.simulatedDeltas.delete(subCode);
    this.render();
  }
};

window.SummaryView = SummaryView;
