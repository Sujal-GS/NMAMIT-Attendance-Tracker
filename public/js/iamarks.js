/**
 * IA Marks (Continuous Internal Evaluation) View Module
 * Fetches, parses, and displays student internal marks with modern dark-mode aesthetics
 */

const IaMarksView = {
  rawSubjects: [],
  rawHtml: '',
  searchQuery: '',
  isInitialized: false,

  init() {
    if (this.isInitialized) return;
    this.isInitialized = true;
    this.bindEvents();
  },

  bindEvents() {
    const searchInput = document.getElementById('ia-search-input');
    if (searchInput) {
      searchInput.addEventListener('input', (e) => {
        this.searchQuery = e.target.value.toLowerCase().trim();
        this.render();
      });
    }

    const refreshBtn = document.getElementById('btn-refresh-ia');
    if (refreshBtn) {
      refreshBtn.addEventListener('click', async () => {
        const svg = refreshBtn.querySelector('svg');
        if (svg) svg.classList.add('spinning');
        refreshBtn.disabled = true;
        try {
          if (window.App && typeof window.App.showToast === 'function') {
            window.App.showToast('Fetching latest IA Marks from portal...', 'info');
          }
          await this.load();
          if (window.App && typeof window.App.showToast === 'function') {
            window.App.showToast('IA Marks updated!', 'success');
          }
        } finally {
          refreshBtn.disabled = false;
          if (svg) setTimeout(() => svg.classList.remove('spinning'), 500);
        }
      });
    }
  },

  async load() {
    const gridEl = document.getElementById('ia-cards-grid');
    if (gridEl) {
      gridEl.innerHTML = `
        <div class="empty-state" style="grid-column: 1 / -1;">
          <div class="btn-spinner"></div>
          <p>Fetching official IA marks from student portal...</p>
        </div>
      `;
    }

    try {
      const res = await API.getIaMarks();
      if (res && res.success) {
        this.rawHtml = res.rawHtml || '';
        let subjects = Array.isArray(res.data) ? res.data : [];

        // If backend returned rawHtml but no structured subjects, parse from HTML table
        if (subjects.length === 0 && this.rawHtml) {
          subjects = this.parseTableHtml(this.rawHtml);
        }

        this.rawSubjects = subjects;
      } else {
        this.rawSubjects = [];
        this.rawHtml = '';
      }
    } catch (err) {
      console.warn('Failed to load IA marks:', err.message);
      this.rawSubjects = [];
      this.rawHtml = '';
    }

    this.render();
  },

  parseTableHtml(htmlStr) {
    try {
      const parser = new DOMParser();
      const doc = parser.parseFromString(htmlStr, 'text/html');
      const table = doc.querySelector('table');
      if (!table) return [];

      const rows = Array.from(table.querySelectorAll('tr'));
      if (rows.length < 2) return [];

      // Extract header names
      const headerRow = rows[0];
      const headers = Array.from(headerRow.querySelectorAll('th, td')).map(c => c.textContent.trim().toLowerCase());

      const parsed = [];
      for (let i = 1; i < rows.length; i++) {
        const cells = Array.from(rows[i].querySelectorAll('td')).map(c => c.textContent.trim());
        if (cells.length < 3) continue;

        let subcode = '';
        let subname = '';
        const tests = [];
        let total = 0;
        let maxTotal = 50;

        cells.forEach((val, cIdx) => {
          const hName = headers[cIdx] || `col_${cIdx}`;
          if (hName.includes('code') || hName.includes('subcode')) {
            subcode = val;
          } else if (hName.includes('name') || hName.includes('subject')) {
            subname = val;
          } else if (hName.includes('test') || hName.includes('ia') || hName.includes('cie') || hName.includes('event') || hName.includes('quiz') || hName.includes('assessment')) {
            const numVal = parseFloat(val);
            tests.push({
              name: headers[cIdx] ? headers[cIdx].toUpperCase() : `Test ${tests.length + 1}`,
              marks: isNaN(numVal) ? val : numVal.toFixed(1),
              max: hName.includes('test') ? 30 : 20
            });
          } else if (hName.includes('total')) {
            const num = parseFloat(val);
            if (!isNaN(num)) total = num;
          }
        });

        if (subcode || subname) {
          const numTotal = total || tests.reduce((sum, t) => sum + (parseFloat(t.marks) || 0), 0);
          parsed.push({
            subcode: subcode || `SUB-${i}`,
            subname: subname || subcode || `Course ${i}`,
            tests,
            total: numTotal.toFixed(1),
            maxTotal: maxTotal,
            percentage: maxTotal > 0 ? (numTotal / maxTotal) * 100 : 0,
            status: numTotal >= 45 ? 'Outstanding' : numTotal >= 38 ? 'Good' : 'Average'
          });
        }
      }
      return parsed;
    } catch (e) {
      console.warn('[IaMarksView] HTML Table parsing warning:', e);
      return [];
    }
  },

  render() {
    const gridEl = document.getElementById('ia-cards-grid');
    const countBadgeEl = document.getElementById('ia-subjects-count-badge');
    if (!gridEl) return;

    this.renderHeroMetrics();

    // Filter by search query
    const filtered = this.rawSubjects.filter(s => {
      const code = (s.subcode || s.fsubcode || s.fsubshort || '').toLowerCase();
      const name = (s.subname || s.fsubname || '').toLowerCase();
      return code.includes(this.searchQuery) || name.includes(this.searchQuery);
    });

    if (countBadgeEl) {
      countBadgeEl.textContent = `${filtered.length} Subject${filtered.length !== 1 ? 's' : ''}`;
    }

    if (filtered.length === 0) {
      gridEl.innerHTML = `
        <div class="empty-state" style="grid-column: 1 / -1;">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="12" y1="8" x2="12" y2="12"></line>
            <line x1="12" y1="16" x2="12.01" y2="16"></line>
          </svg>
          <p>${this.searchQuery ? `No IA marks match "${this.searchQuery}".` : 'No Internal Assessment marks published by university examination division yet.'}</p>
        </div>
      `;
      return;
    }

    let cardsHtml = '';
    filtered.forEach(item => {
      const code = item.subcode || item.fsubcode || item.fsubshort || 'SUB';
      const name = item.subname || item.fsubname || 'Course Name';
      const total = parseFloat(item.total ?? item.grandtotal ?? 0);
      const maxTotal = parseFloat(item.maxTotal ?? 50);
      const pct = maxTotal > 0 ? (total / maxTotal) * 100 : 0;

      let colorVar = 'var(--emerald)';
      let textClass = 'text-emerald';
      let tierLabel = 'Outstanding';

      if (pct < 60) {
        colorVar = 'var(--rose)';
        textClass = 'text-rose';
        tierLabel = 'Needs Improvement';
      } else if (pct < 75) {
        colorVar = 'var(--amber)';
        textClass = 'text-amber';
        tierLabel = 'Average';
      } else if (pct < 90) {
        colorVar = 'var(--primary)';
        textClass = 'text-contrast';
        tierLabel = 'Good';
      }

      // Render individual component test pills
      let testsHtml = '';
      if (Array.isArray(item.tests) && item.tests.length > 0) {
        testsHtml = item.tests.map(t => {
          const tMarks = t.marks === '-2' || t.marks === '-2.00' ? 'AB' : t.marks;
          return `
            <div class="ia-test-pill">
              <span class="ia-test-name">${t.name}</span>
              <span class="ia-test-score">${tMarks}${t.max ? `<span class="ia-test-max">/${t.max}</span>` : ''}</span>
            </div>
          `;
        }).join('');
      } else {
        // Fallback fields from elementWiseMarks format (ftest1, ftest2, fevent)
        const t1 = item.ftest1 != null ? (item.ftest1 === '-2.00' ? 'AB' : item.ftest1) : null;
        const t2 = item.ftest2 != null ? (item.ftest2 === '-2.00' ? 'AB' : item.ftest2) : null;
        const event = item.fevent != null ? (item.fevent === '-2.00' ? 'AB' : item.fevent) : null;

        const pills = [];
        if (t1 != null) pills.push({ name: 'Test 1', score: t1 });
        if (t2 != null) pills.push({ name: 'Test 2', score: t2 });
        if (event != null) pills.push({ name: 'Assessment', score: event });

        testsHtml = pills.map(p => `
          <div class="ia-test-pill">
            <span class="ia-test-name">${p.name}</span>
            <span class="ia-test-score">${p.score}</span>
          </div>
        `).join('');
      }

      cardsHtml += `
        <div class="subject-card ia-card" data-subcode="${code}">
          <!-- Header -->
          <div class="subject-card-header">
            <div class="subject-identity">
              <span class="subject-code-tag">${code}</span>
              <h4 class="subject-card-title">${name}</h4>
            </div>
            <div class="subject-percentage-badge">
              <span class="subject-pct-number ${textClass}">${pct.toFixed(1)}%</span>
              <span class="subject-status-mini ${textClass}">${tierLabel}</span>
            </div>
          </div>

          <!-- Component Test Pills -->
          <div class="ia-tests-wrapper">
            ${testsHtml || '<div class="ia-tests-pending">Evaluation components in progress</div>'}
          </div>

          <!-- Total Score Line & Progress Bar -->
          <div class="progress-container" style="margin-top: 14px;">
            <div class="progress-track">
              <div class="progress-fill" style="width: ${Math.min(100, pct)}%; background-color: ${colorVar};"></div>
            </div>
            <div class="progress-stats-line">
              <span>Scored: <strong>${total.toFixed(1)}</strong> / ${maxTotal} marks</span>
              <span class="${textClass}"><strong>${pct.toFixed(1)}%</strong> overall</span>
            </div>
          </div>
        </div>
      `;
    });

    gridEl.innerHTML = cardsHtml;

    // Render raw HTML table fallback container if available
    const rawContainer = document.getElementById('ia-raw-table-container');
    const rawContent = document.getElementById('ia-raw-table-content');
    if (rawContainer && rawContent && this.rawHtml) {
      rawContent.innerHTML = this.rawHtml;
      rawContainer.classList.remove('hidden');
    }
  },

  renderHeroMetrics() {
    let totalScored = 0;
    let totalMax = 0;
    let validCount = 0;

    this.rawSubjects.forEach(s => {
      const tot = parseFloat(s.total ?? s.grandtotal ?? 0);
      const mTot = parseFloat(s.maxTotal ?? 50);
      if (!isNaN(tot) && tot > 0) {
        totalScored += tot;
        totalMax += mTot;
        validCount++;
      }
    });

    const avgPct = totalMax > 0 ? (totalScored / totalMax) * 100 : 0;

    const pctEl = document.getElementById('ia-overall-percentage');
    const gaugeEl = document.getElementById('ia-gauge-bar');
    const scoredEl = document.getElementById('ia-total-scored');
    const maxEl = document.getElementById('ia-max-possible');
    const countEl = document.getElementById('ia-courses-count');
    const statusChipEl = document.getElementById('ia-status-chip');
    const statusTextEl = document.getElementById('ia-status-text');

    if (pctEl) pctEl.textContent = validCount > 0 ? `${avgPct.toFixed(1)}%` : '--%';
    if (scoredEl) scoredEl.textContent = totalScored.toFixed(1);
    if (maxEl) maxEl.textContent = `Out of ${totalMax.toFixed(0)} total marks`;
    if (countEl) countEl.textContent = this.rawSubjects.length;

    if (gaugeEl) {
      const radius = 52;
      const circumference = 2 * Math.PI * radius;
      gaugeEl.style.strokeDasharray = `${circumference} ${circumference}`;
      const offset = circumference - (Math.min(100, avgPct) / 100) * circumference;
      gaugeEl.style.strokeDashoffset = offset;
    }

    if (statusChipEl && statusTextEl) {
      if (avgPct >= 85) {
        statusChipEl.className = 'status-chip status-chip-success';
        statusTextEl.textContent = 'Excellent Standing';
      } else if (avgPct >= 70) {
        statusChipEl.className = 'status-chip status-chip-info';
        statusTextEl.textContent = 'Good Standing';
      } else {
        statusChipEl.className = 'status-chip status-chip-warning';
        statusTextEl.textContent = 'Active Evaluation';
      }
    }
  }
};

window.IaMarksView = IaMarksView;
