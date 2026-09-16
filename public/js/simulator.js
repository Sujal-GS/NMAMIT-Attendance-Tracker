/**
 * "What-If" Bunk & Attendance Recovery Simulator
 * Real-Time Projection Sandbox for Future Classes & Leave Planning
 */

const Simulator = {
  selectedSubCode: 'AGGREGATE',
  extraAttended: 0,
  extraBunked: 0,
  targetPct: 85,
  isInitialized: false,

  init() {
    if (this.isInitialized) return;
    this.isInitialized = true;
    this.bindEvents();
  },

  bindEvents() {
    const subSelect = document.getElementById('sim-subject-select');
    if (subSelect) {
      subSelect.addEventListener('change', (e) => {
        this.selectedSubCode = e.target.value;
        this.recalculate();
      });
    }

    // Attend steppers
    const btnAttendPlus = document.getElementById('sim-btn-attend-plus');
    const btnAttendMinus = document.getElementById('sim-btn-attend-minus');
    if (btnAttendPlus) {
      btnAttendPlus.addEventListener('click', () => {
        this.extraAttended++;
        this.recalculate();
      });
    }
    if (btnAttendMinus) {
      btnAttendMinus.addEventListener('click', () => {
        if (this.extraAttended > 0) this.extraAttended--;
        this.recalculate();
      });
    }

    // Bunk steppers
    const btnBunkPlus = document.getElementById('sim-btn-bunk-plus');
    const btnBunkMinus = document.getElementById('sim-btn-bunk-minus');
    if (btnBunkPlus) {
      btnBunkPlus.addEventListener('click', () => {
        this.extraBunked++;
        this.recalculate();
      });
    }
    if (btnBunkMinus) {
      btnBunkMinus.addEventListener('click', () => {
        if (this.extraBunked > 0) this.extraBunked--;
        this.recalculate();
      });
    }

    // Reset button
    const btnReset = document.getElementById('sim-btn-reset');
    if (btnReset) {
      btnReset.addEventListener('click', () => {
        this.reset();
      });
    }

    // Quick presets
    const presets = document.querySelectorAll('.sim-preset-btn');
    presets.forEach(btn => {
      btn.addEventListener('click', () => {
        const attend = parseInt(btn.getAttribute('data-attend') || 0, 10);
        const bunk = parseInt(btn.getAttribute('data-bunk') || 0, 10);
        this.extraAttended = attend;
        this.extraBunked = bunk;
        this.recalculate();
      });
    });
  },

  reset() {
    this.extraAttended = 0;
    this.extraBunked = 0;
    this.recalculate();
  },

  populateSubjects(subjects) {
    const subSelect = document.getElementById('sim-subject-select');
    if (!subSelect) return;

    let options = '<option value="AGGREGATE">Overall College Aggregate</option>';
    if (Array.isArray(subjects)) {
      subjects.forEach(s => {
        const code = s.fsubcode || s.code;
        const name = s.fsubname || s.name || code;
        options += `<option value="${code}" ${this.selectedSubCode === code ? 'selected' : ''}>${code} - ${name}</option>`;
      });
    }
    subSelect.innerHTML = options;
    if (window.CustomSelect) window.CustomSelect.refresh(subSelect);
    this.recalculate();
  },

  recalculate() {
    const subjects = window.SummaryView ? window.SummaryView.rawSubjects : [];
    let baseConducted = 0;
    let baseAttended = 0;

    if (this.selectedSubCode === 'AGGREGATE') {
      subjects.forEach(s => {
        baseConducted += parseInt(s.conducted ?? s.ftotalclass ?? 0, 10);
        baseAttended += parseInt(s.attended ?? s.fpresentclass ?? 0, 10);
      });
    } else {
      const found = subjects.find(s => (s.fsubcode || s.code) === this.selectedSubCode);
      if (found) {
        baseConducted = parseInt(found.conducted ?? found.ftotalclass ?? 0, 10);
        baseAttended = parseInt(found.attended ?? found.fpresentclass ?? 0, 10);
      }
    }

    const currentPct = baseConducted > 0 ? (baseAttended / baseConducted) * 100 : 100;
    const projConducted = baseConducted + this.extraAttended + this.extraBunked;
    const projAttended = baseAttended + this.extraAttended;
    const projectedPct = projConducted > 0 ? (projAttended / projConducted) * 100 : 100;
    const diffPct = projectedPct - currentPct;

    // Target analysis at 85%
    const target = 0.85;
    const safeBunksRemaining = Math.max(0, Math.floor((projAttended / target) - projConducted));
    const classesNeededFor85 = Math.max(0, Math.ceil(((target * projConducted) - projAttended) / (1 - target)));

    // Update Counter Displays
    const attendCountEl = document.getElementById('sim-val-attend');
    const bunkCountEl = document.getElementById('sim-val-bunk');
    const currentPctEl = document.getElementById('sim-current-pct');
    const projectedPctEl = document.getElementById('sim-projected-pct');
    const deltaEl = document.getElementById('sim-delta-badge');
    const adviceEl = document.getElementById('sim-advice-text');
    const bufferEl = document.getElementById('sim-buffer-status');

    if (attendCountEl) attendCountEl.textContent = `+${this.extraAttended}`;
    if (bunkCountEl) bunkCountEl.textContent = `+${this.extraBunked}`;
    if (currentPctEl) currentPctEl.textContent = `${currentPct.toFixed(1)}%`;
    if (projectedPctEl) projectedPctEl.textContent = `${projectedPct.toFixed(1)}%`;

    if (deltaEl) {
      if (Math.abs(diffPct) < 0.05) {
        deltaEl.textContent = 'No Change (0.0%)';
        deltaEl.className = 'sim-delta-badge delta-neutral';
      } else if (diffPct > 0) {
        deltaEl.textContent = `+${diffPct.toFixed(1)}% 📈`;
        deltaEl.className = 'sim-delta-badge delta-positive';
      } else {
        deltaEl.textContent = `${diffPct.toFixed(1)}% 📉`;
        deltaEl.className = 'sim-delta-badge delta-negative';
      }
    }

    if (adviceEl && bufferEl) {
      if (projectedPct >= 85) {
        bufferEl.textContent = `Safe Zone • ${safeBunksRemaining} buffer class${safeBunksRemaining !== 1 ? 'es' : ''} left`;
        bufferEl.className = 'sim-buffer-status status-safe';
        adviceEl.textContent = safeBunksRemaining > 0 
          ? `You remain safely above 85%. You could still miss ${safeBunksRemaining} more class${safeBunksRemaining > 1 ? 'es' : ''}.`
          : 'Right at the 85% boundary. Further bunks will drop you into the warning zone.';
      } else if (projectedPct >= 75) {
        bufferEl.textContent = 'Warning Zone (75%–85%)';
        bufferEl.className = 'sim-buffer-status status-warning';
        adviceEl.textContent = `Eligible for exams but below 85% honor threshold. Attend next ${classesNeededFor85} consecutive classes to reach 85%.`;
      } else {
        bufferEl.textContent = 'Critical Zone (<75%)';
        bufferEl.className = 'sim-buffer-status status-critical';
        const needed75 = Math.max(1, Math.ceil(((0.75 * projConducted) - projAttended) / 0.25));
        adviceEl.textContent = `Exams barred! Must attend next ${needed75} consecutive class${needed75 > 1 ? 'es' : ''} without fail to cross 75%.`;
      }
    }
  }
};

window.Simulator = Simulator;
