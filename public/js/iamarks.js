/**
 * IA Marks (Continuous Internal Evaluation) View Module
 * Renders official university marks in a high-fidelity dark glassmorphic table
 */

const IaMarksView = {
  rawHtml: '',
  headers: [],
  rows: [],
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
            window.App.showToast('Official IA Marks updated!', 'success');
          }
        } finally {
          refreshBtn.disabled = false;
          if (svg) setTimeout(() => svg.classList.remove('spinning'), 500);
        }
      });
    }
  },

  async load() {
    const tableContainer = document.getElementById('ia-table-container');
    if (tableContainer) {
      tableContainer.innerHTML = `
        <div class="ia-loading-state">
          <div class="btn-spinner"></div>
          <p>Fetching official university CIE table from student portal...</p>
        </div>
      `;
    }

    try {
      const res = await API.getIaMarks();
      if (res && res.success) {
        this.rawHtml = res.rawHtml || '';
        let parsed = { headers: [], rows: [] };

        if (this.rawHtml) {
          parsed = this.parseTableHtml(this.rawHtml);
        }

        // If parsed rows is empty but res.data exists as an array
        if (parsed.rows.length === 0 && Array.isArray(res.data) && res.data.length > 0) {
          parsed = this.parseStructuredData(res.data);
        }

        this.headers = parsed.headers;
        this.rows = parsed.rows;
      } else {
        this.headers = [];
        this.rows = [];
      }
    } catch (err) {
      console.warn('Failed to load IA marks:', err.message);
      this.headers = [];
      this.rows = [];
    }

    this.render();
  },

  parseTableHtml(htmlStr) {
    try {
      const parser = new DOMParser();
      const doc = parser.parseFromString(htmlStr, 'text/html');
      const table = doc.querySelector('table');
      if (!table) return { headers: [], rows: [] };

      const trs = Array.from(table.querySelectorAll('tr'));
      if (trs.length === 0) return { headers: [], rows: [] };

      // Find the row containing column headers
      let headerRowIndex = -1;
      let rawHeaders = [];

      for (let i = 0; i < trs.length; i++) {
        const ths = Array.from(trs[i].querySelectorAll('th, td'));
        const texts = ths.map(c => c.textContent.trim());
        const hasSub = texts.some(t => /sub.*code|subject/i.test(t));
        const hasMarks = texts.some(t => /mse|marks|la-|final/i.test(t));
        if (hasSub || (texts.length >= 4 && hasMarks)) {
          headerRowIndex = i;
          rawHeaders = texts;
          break;
        }
      }

      if (headerRowIndex === -1) {
        headerRowIndex = trs.length > 1 ? 1 : 0;
        rawHeaders = Array.from(trs[headerRowIndex].querySelectorAll('th, td')).map(c => c.textContent.trim());
      }

      const rows = [];
      for (let i = headerRowIndex + 1; i < trs.length; i++) {
        const tds = Array.from(trs[i].querySelectorAll('td, th'));
        if (tds.length < 3) continue;

        const cells = tds.map(c => c.textContent.trim());
        const rowObj = {
          _rawCells: cells
        };

        rawHeaders.forEach((h, colIdx) => {
          rowObj[h] = cells[colIdx] || '';
          const lower = h.toLowerCase();
          if (/sub.*code/i.test(lower)) rowObj.subCode = cells[colIdx] || '';
          else if (/sub.*name/i.test(lower)) rowObj.subName = cells[colIdx] || '';
          else if (/final.*mark|final/i.test(lower)) rowObj.finalMarks = cells[colIdx] || '';
          else if (/sem/i.test(lower)) rowObj.sem = cells[colIdx] || '';
          else if (/sl.*no|^no$/i.test(lower)) rowObj.slNo = cells[colIdx] || '';
          else if (/elig/i.test(lower)) rowObj.eligibility = cells[colIdx] || '';
        });

        // Fallbacks if not mapped
        if (!rowObj.subCode && cells[2]) rowObj.subCode = cells[2];
        if (!rowObj.subName && cells[3]) rowObj.subName = cells[3];
        if (!rowObj.finalMarks) {
          for (let c = cells.length - 1; c >= 0; c--) {
            if (/^\d+(\.\d+)?$/.test(cells[c])) {
              rowObj.finalMarks = cells[c];
              break;
            }
          }
        }

        if (rowObj.subCode || rowObj.subName) {
          rows.push(rowObj);
        }
      }

      return { headers: rawHeaders, rows };
    } catch (e) {
      console.error('parseTableHtml error:', e);
      return { headers: [], rows: [] };
    }
  },

  parseStructuredData(arr) {
    if (!Array.isArray(arr) || arr.length === 0) return { headers: [], rows: [] };
    
    // Standard NMAMIT headers
    const headers = ['Sl. No.', 'Sub. Code', 'Sub. Name', 'MSE-1', 'MSE-2', 'LA-1', 'LA-2', 'LA-3', 'RECORD', 'CONDUCTION', 'MSE(LAB)', 'Final Marks', 'Eligibility'];
    
    const rows = arr.map((item, idx) => {
      return {
        'Sl. No.': item.slNo || String(idx + 1),
        'Sub. Code': item.subcode || item.fsubcode || '',
        'Sub. Name': item.subname || item.fsubname || '',
        'MSE-1': item.mse1 ?? item.ftest1 ?? '',
        'MSE-2': item.mse2 ?? item.ftest2 ?? '',
        'LA-1': item.la1 ?? item.fevent ?? '',
        'LA-2': item.la2 ?? '',
        'LA-3': item.la3 ?? '',
        'RECORD': item.record ?? '',
        'CONDUCTION': item.conduction ?? '',
        'MSE(LAB)': item.mseLab ?? '',
        'Final Marks': item.finalMarks ?? item.total ?? '',
        'Eligibility': item.eligibility ?? 'Eligible',
        subCode: item.subcode || item.fsubcode || '',
        subName: item.subname || item.fsubname || '',
        finalMarks: item.finalMarks ?? item.total ?? ''
      };
    });

    return { headers, rows };
  },

  render() {
    const tableContainer = document.getElementById('ia-table-container');
    const countBadgeEl = document.getElementById('ia-subjects-count-badge');
    if (!tableContainer) return;

    // Filter rows by search query
    const filtered = this.rows.filter(r => {
      if (!this.searchQuery) return true;
      const code = (r.subCode || '').toLowerCase();
      const name = (r.subName || '').toLowerCase();
      return code.includes(this.searchQuery) || name.includes(this.searchQuery);
    });

    if (countBadgeEl) {
      countBadgeEl.textContent = `${filtered.length} Subject${filtered.length !== 1 ? 's' : ''}`;
    }

    this.renderHeroMetrics(filtered);

    if (filtered.length === 0) {
      tableContainer.innerHTML = `
        <div class="empty-state">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="12" y1="8" x2="12" y2="12"></line>
            <line x1="12" y1="16" x2="12.01" y2="16"></line>
          </svg>
          <p>${this.searchQuery ? `No courses match "${this.searchQuery}".` : 'No official CIE marks published on university portal yet.'}</p>
        </div>
      `;
      return;
    }

    // Determine columns to display
    // Exclude redundant columns like "Sl. No." or "Sem" if we render our own clean "#" column
    const displayHeaders = this.headers.filter(h => {
      const lower = h.toLowerCase();
      return !/^sl|^sem$/i.test(lower);
    });

    // Build Table HTML
    let tableHtml = `
      <table class="ia-modern-table">
        <thead>
          <tr>
            <th class="col-num">#</th>
            ${displayHeaders.map(h => {
              const lower = h.toLowerCase();
              let alignClass = 'col-center';
              if (/name/i.test(lower)) alignClass = 'col-left col-name';
              else if (/code/i.test(lower)) alignClass = 'col-left col-code';
              else if (/final/i.test(lower)) alignClass = 'col-center col-final';
              return `<th class="${alignClass}">${h}</th>`;
            }).join('')}
          </tr>
        </thead>
        <tbody>
    `;

    filtered.forEach((row, idx) => {
      tableHtml += `<tr>`;
      tableHtml += `<td class="col-num">${idx + 1}</td>`;

      displayHeaders.forEach(h => {
        const lower = h.toLowerCase();
        let val = row[h] != null ? String(row[h]).trim() : '';

        if (/code/i.test(lower)) {
          tableHtml += `
            <td class="col-left col-code">
              <span class="subject-code-tag">${val || row.subCode || 'SUB'}</span>
            </td>
          `;
        } else if (/name/i.test(lower)) {
          tableHtml += `
            <td class="col-left col-name">
              <span class="ia-sub-title">${val || row.subName || 'Course'}</span>
            </td>
          `;
        } else if (/final/i.test(lower)) {
          const num = parseFloat(val);
          const displayVal = !isNaN(num) ? num.toFixed(2) : (val || '—');
          tableHtml += `
            <td class="col-center col-final">
              <div class="ia-final-pill">
                <strong>${displayVal}</strong>
                <span class="ia-denom">/ 50</span>
              </div>
            </td>
          `;
        } else if (/elig/i.test(lower)) {
          const isElig = !val || /elig|yes|ok|true/i.test(val);
          tableHtml += `
            <td class="col-center">
              <span class="ia-status-pill ${isElig ? 'pill-eligible' : 'pill-warning'}">
                ${val || 'Eligible'}
              </span>
            </td>
          `;
        } else {
          // Standard score column (MSE-1, MSE-2, LA-1, etc.)
          if (!val || val === '-2' || val === '-2.00') {
            const label = (val === '-2' || val === '-2.00') ? 'AB' : '—';
            tableHtml += `<td class="col-center"><span class="score-dash ${label === 'AB' ? 'score-ab' : ''}">${label}</span></td>`;
          } else {
            tableHtml += `<td class="col-center"><span class="score-num">${val}</span></td>`;
          }
        }
      });

      tableHtml += `</tr>`;
    });

    tableHtml += `
        </tbody>
      </table>
    `;

    tableContainer.innerHTML = tableHtml;
  },

  renderHeroMetrics(rows) {
    let totalFinal = 0;
    let finalCount = 0;
    let highest = 0;

    rows.forEach(r => {
      const fStr = r.finalMarks || '';
      const fNum = parseFloat(fStr);
      if (!isNaN(fNum) && fNum > 0) {
        totalFinal += fNum;
        finalCount++;
        if (fNum > highest) highest = fNum;
      }
    });

    const avgFinal = finalCount > 0 ? (totalFinal / finalCount) : 0;
    const avgPct = (avgFinal / 50) * 100;

    const avgScoreEl = document.getElementById('ia-hero-avg-score');
    const avgPctEl = document.getElementById('ia-hero-avg-pct');
    const countEl = document.getElementById('ia-hero-courses-count');
    const highestEl = document.getElementById('ia-hero-highest-score');

    if (avgScoreEl) avgScoreEl.textContent = finalCount > 0 ? `${avgFinal.toFixed(1)} / 50` : '—';
    if (avgPctEl) avgPctEl.textContent = finalCount > 0 ? `${avgPct.toFixed(1)}% CIE Average` : 'CIE in progress';
    if (countEl) countEl.textContent = `${rows.length} Courses`;
    if (highestEl) highestEl.textContent = highest > 0 ? `${highest.toFixed(1)} / 50` : '—';
  }
};

window.IaMarksView = IaMarksView;
