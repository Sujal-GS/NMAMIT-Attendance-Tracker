/**
 * Interactive Attendance Calendar & Daily Timetable Inspector
 * Styled with Lucent.AI Monospace & Bento Matrix Aesthetics
 */

const CalendarView = {
  currentYear: new Date().getFullYear(),
  currentMonth: new Date().getMonth(), // 0-11
  selectedDateStr: null,
  monthDataCache: new Map(), // key: 'YYYY-MM' -> month attendance map
  dayDataCache: new Map(),   // key: 'YYYY-MM-DD' -> array of period classes
  isLoadingMonth: false,
  isInitialized: false,

  monthNames: [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December'
  ],

  dayNames: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'],

  init() {
    if (!this.isInitialized) {
      const now = new Date();
      this.currentYear = now.getFullYear();
      this.currentMonth = now.getMonth();
      this.selectedDateStr = this.formatDate(now);
      this.isInitialized = true;
      this.bindEvents();
    }
  },

  bindEvents() {
    const prevBtn = document.getElementById('cal-prev-btn');
    const nextBtn = document.getElementById('cal-next-btn');
    const todayBtn = document.getElementById('cal-today-btn');

    if (prevBtn) {
      prevBtn.onclick = (e) => {
        e.preventDefault();
        e.stopPropagation();
        this.prevMonth();
      };
    }

    if (nextBtn) {
      nextBtn.onclick = (e) => {
        e.preventDefault();
        e.stopPropagation();
        this.nextMonth();
      };
    }

    if (todayBtn) {
      todayBtn.onclick = (e) => {
        e.preventDefault();
        e.stopPropagation();
        this.goToday();
      };
    }
  },

  prevMonth() {
    if (this.currentMonth === 0) {
      this.currentMonth = 11;
      this.currentYear--;
    } else {
      this.currentMonth--;
    }
    const monthStr = String(this.currentMonth + 1).padStart(2, '0');
    this.selectedDateStr = `${this.currentYear}-${monthStr}-01`;
    this.render();
  },

  nextMonth() {
    if (this.currentMonth === 11) {
      this.currentMonth = 0;
      this.currentYear++;
    } else {
      this.currentMonth++;
    }
    const monthStr = String(this.currentMonth + 1).padStart(2, '0');
    this.selectedDateStr = `${this.currentYear}-${monthStr}-01`;
    this.render();
  },

  goToday() {
    const now = new Date();
    this.currentYear = now.getFullYear();
    this.currentMonth = now.getMonth();
    this.selectedDateStr = this.formatDate(now);
    this.render();
    this.loadDateDetails(this.selectedDateStr);
  },

  formatDate(d) {
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  },

  async loadMonthData(year, month) {
    const cacheKey = `${year}-${String(month).padStart(2, '0')}`;
    if (this.monthDataCache.has(cacheKey)) {
      return this.monthDataCache.get(cacheKey);
    }

    // Check sessionStorage
    try {
      const sessionCached = sessionStorage.getItem(`att_month_${cacheKey}`);
      if (sessionCached) {
        const parsed = JSON.parse(sessionCached);
        if (parsed && typeof parsed === 'object') {
          this.monthDataCache.set(cacheKey, parsed);
          for (const [dStr, dInfo] of Object.entries(parsed)) {
            if (dInfo && dInfo.classes && dInfo.classes.length > 0) {
              this.dayDataCache.set(dStr, dInfo.classes);
            }
          }
          return parsed;
        }
      }
    } catch (e) {}

    try {
      this.isLoadingMonth = true;
      const res = await API.getMonthAttendance(year, month);
      if (res && res.success && res.monthData) {
        this.monthDataCache.set(cacheKey, res.monthData);
        try {
          sessionStorage.setItem(`att_month_${cacheKey}`, JSON.stringify(res.monthData));
        } catch (e) {}

        // Prepopulate day caches
        for (const [dStr, dInfo] of Object.entries(res.monthData)) {
          if (dInfo && dInfo.classes && dInfo.classes.length > 0) {
            this.dayDataCache.set(dStr, dInfo.classes);
          }
        }
        return res.monthData;
      }
    } catch (err) {
      console.warn('Failed to load month attendance batch, falling back to lazy load:', err);
    } finally {
      this.isLoadingMonth = false;
    }
    return {};
  },

  async render() {
    const year = this.currentYear;
    const month = this.currentMonth + 1; // 1-12
    const monthTitle = `${this.monthNames[this.currentMonth]} ${year}`;
    
    const titleEl = document.getElementById('calendar-month-year');
    if (titleEl) titleEl.textContent = monthTitle;

    const gridEl = document.getElementById('calendar-days-grid');
    if (!gridEl) return;

    // Show loading skeleton if fetching
    gridEl.innerHTML = '<div class="empty-state" style="grid-column: 1 / -1;"><div class="btn-spinner"></div><p>Fetching attendance records...</p></div>';

    const monthData = await this.loadMonthData(year, month);

    // Calculate calendar grid metrics
    const firstDayIndex = new Date(year, month - 1, 1).getDay(); // 0 = Sun
    const daysInMonth = new Date(year, month, 0).getDate();
    const daysInPrevMonth = new Date(year, month - 1, 0).getDate();

    const todayStr = this.formatDate(new Date());

    let gridHtml = '';
    let totalConducted = 0;
    let totalAttended = 0;

    // 1. Previous month trailing days
    for (let i = firstDayIndex - 1; i >= 0; i--) {
      const prevDayNum = daysInPrevMonth - i;
      gridHtml += `
        <div class="cal-day-cell other-month">
          <div class="cal-day-top">
            <span class="cal-day-number">${prevDayNum}</span>
          </div>
        </div>
      `;
    }

    // 2. Days of current month
    for (let d = 1; d <= daysInMonth; d++) {
      const dateStr = `${year}-${String(month).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      const dayData = monthData[dateStr] || { conducted: 0, attended: 0, classes: [] };
      
      const conducted = dayData.conducted || 0;
      const attended = dayData.attended || 0;
      const missed = conducted - attended;

      totalConducted += conducted;
      totalAttended += attended;

      const isToday = (dateStr === todayStr);
      const isSelected = (dateStr === this.selectedDateStr);

      let statusClass = 'status-off';
      let badgeHtml = '';
      let ratioText = '';

      if (conducted > 0) {
        if (attended === conducted) {
          statusClass = 'status-perfect';
          badgeHtml = `<span class="day-badge-mini text-emerald">${attended}/${conducted}</span>`;
          ratioText = `<span class="text-emerald">All ${attended} attended</span>`;
        } else if (attended > 0) {
          statusClass = 'status-partial';
          badgeHtml = `<span class="day-badge-mini text-contrast">${attended}/${conducted}</span>`;
          ratioText = `<span class="text-contrast">${attended} attended</span>`;
        } else {
          statusClass = 'status-missed';
          badgeHtml = `<span class="day-badge-mini text-rose">0/${conducted}</span>`;
          ratioText = `<span class="text-rose">Missed ${conducted}</span>`;
        }
      } else {
        // Holiday or Sunday or date with no classes
        const dayOfWeek = new Date(year, month - 1, d).getDay();
        if (dayOfWeek === 0) {
          ratioText = `<span class="text-muted">Sunday</span>`;
        } else {
          ratioText = `<span class="text-muted">No classes</span>`;
        }
      }

      const activeClass = isSelected ? 'is-selected' : '';
      const todayClass = isToday ? 'is-today' : '';

      gridHtml += `
        <div class="cal-day-cell ${statusClass} ${activeClass} ${todayClass}" 
             data-date="${dateStr}"
             tabindex="0"
             role="button"
             aria-label="Attendance for ${dateStr}">
          <div class="cal-day-top">
            <span class="cal-day-number">${d}</span>
            ${badgeHtml}
          </div>
          <div class="cal-day-bottom">
            <span class="cal-ratio-text">${ratioText}</span>
          </div>
        </div>
      `;
    }

    // 3. Next month trailing days to complete grid (up to multiple of 7)
    const totalCells = firstDayIndex + daysInMonth;
    const remainingCells = (7 - (totalCells % 7)) % 7;
    for (let n = 1; n <= remainingCells; n++) {
      gridHtml += `
        <div class="cal-day-cell other-month">
          <div class="cal-day-top">
            <span class="cal-day-number">${n}</span>
          </div>
        </div>
      `;
    }

    gridEl.innerHTML = gridHtml;

    // Attach click handlers to active month day cells
    gridEl.querySelectorAll('.cal-day-cell[data-date]').forEach(cell => {
      cell.addEventListener('click', () => {
        const dateStr = cell.getAttribute('data-date');
        this.selectDate(dateStr);
      });
      cell.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          const dateStr = cell.getAttribute('data-date');
          this.selectDate(dateStr);
        }
      });
    });

    // Update month stats strip
    const condEl = document.getElementById('mstat-conducted');
    const attEl = document.getElementById('mstat-attended');
    const missEl = document.getElementById('mstat-missed');
    const pctEl = document.getElementById('mstat-percentage');

    if (condEl) condEl.textContent = totalConducted;
    if (attEl) attEl.textContent = totalAttended;
    if (missEl) missEl.textContent = (totalConducted - totalAttended);

    const monthPct = totalConducted > 0 ? ((totalAttended / totalConducted) * 100).toFixed(1) : '0.0';
    if (pctEl) {
      pctEl.textContent = `${monthPct}%`;
      pctEl.className = 'mstat-value ' + (monthPct >= 85 ? 'text-emerald' : monthPct >= 75 ? 'text-contrast' : 'text-rose');
    }

    // Ensure currently selected date is loaded into daily panel
    if (this.selectedDateStr) {
      this.loadDateDetails(this.selectedDateStr);
    }
  },

  selectDate(dateStr) {
    this.selectedDateStr = dateStr;
    // Update active class in grid
    document.querySelectorAll('#calendar-days-grid .cal-day-cell').forEach(cell => {
      if (cell.getAttribute('data-date') === dateStr) {
        cell.classList.add('is-selected');
      } else {
        cell.classList.remove('is-selected');
      }
    });

    this.loadDateDetails(dateStr);

    // On mobile screens, smoothly scroll to timetable breakdown panel
    if (window.innerWidth <= 768) {
      const panel = document.getElementById('day-details-panel');
      if (panel) {
        setTimeout(() => {
          panel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        }, 100);
      }
    }
  },

  async loadDateDetails(dateStr) {
    const headingEl = document.getElementById('selected-date-heading');
    const subEl = document.getElementById('selected-date-dayname');
    const badgeEl = document.getElementById('day-overall-badge');
    const listEl = document.getElementById('periods-list');

    const condEl = document.getElementById('day-stat-conducted');
    const attEl = document.getElementById('day-stat-attended');
    const missEl = document.getElementById('day-stat-missed');

    const d = new Date(dateStr + 'T00:00:00');
    const dayName = this.dayNames[d.getDay()];

    if (headingEl) headingEl.textContent = `${d.getDate()} ${this.monthNames[d.getMonth()]} ${d.getFullYear()}`;
    if (subEl) subEl.textContent = dayName;

    // Check memory cache
    let classes = this.dayDataCache.get(dateStr);

    if (!classes) {
      if (listEl) {
        listEl.innerHTML = '<div class="empty-state"><div class="btn-spinner"></div><p>Loading classes for this day...</p></div>';
      }
      try {
        const res = await API.getDailyAttendance(dateStr);
        if (res.success && Array.isArray(res.data)) {
          classes = res.data;
          this.dayDataCache.set(dateStr, classes);
        } else {
          classes = [];
        }
      } catch (err) {
        console.error('Failed to load daily attendance for', dateStr, err);
        classes = [];
      }
    }

    const conductedCount = classes.reduce((sum, c) => sum + parseInt(c.fnoclass || '1', 10), 0);
    const attendedCount = classes.reduce((sum, c) => {
      const isPres = c.fpresent === '1' || c.fpresent === 1 || String(c.fpresent).trim().toUpperCase() === 'P' || String(c.fpresent).toLowerCase() === 'present';
      return sum + (isPres ? parseInt(c.fnoclass || '1', 10) : 0);
    }, 0);
    const missedCount = conductedCount - attendedCount;

    if (condEl) condEl.textContent = conductedCount;
    if (attEl) attEl.textContent = attendedCount;
    if (missEl) missEl.textContent = missedCount;

    if (badgeEl) {
      badgeEl.className = 'day-status-pill';
      if (conductedCount === 0) {
        badgeEl.textContent = 'No Classes';
      } else if (attendedCount === conductedCount) {
        badgeEl.classList.add('text-emerald');
        badgeEl.textContent = '100% Attended';
      } else if (attendedCount > 0) {
        badgeEl.classList.add('text-contrast');
        badgeEl.textContent = `${attendedCount}/${conductedCount} Attended`;
      } else {
        badgeEl.classList.add('text-rose');
        badgeEl.textContent = '0% Attended';
      }
    }

    // Render periods timetable
    if (!listEl) return;

    if (!classes || classes.length === 0) {
      listEl.innerHTML = `
        <div class="empty-state">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="12" y1="8" x2="12" y2="12"></line>
            <line x1="12" y1="16" x2="12.01" y2="16"></line>
          </svg>
          <p>No class records scheduled or logged for this date.</p>
        </div>
      `;
      return;
    }

    let periodsHtml = '';
    classes.forEach((item, idx) => {
      const isPresent = item.fpresent === '1' || item.fpresent === 1 || String(item.fpresent).trim().toUpperCase() === 'P' || String(item.fpresent).toLowerCase() === 'present';
      const periodLabel = item.fperiod ? `Period ${item.fperiod}` : `Slot ${idx + 1}`;
      const hoursCount = parseInt(item.fnoclass || '1', 10);

      periodsHtml += `
        <div class="period-item ${isPresent ? 'is-present' : 'is-absent'}">
          <div class="period-info-left">
            <div class="period-badge-number" title="Period number">${item.fperiod || (idx + 1)}</div>
            <div class="period-meta">
              <span class="period-subcode">${item.fsubcode || 'SUB'}</span>
              <span class="period-subname" title="${item.fsubname || 'Subject'}">${item.fsubname || 'Academic Class'}</span>
              <span class="period-classes-count">${periodLabel} • ${hoursCount} Hour${hoursCount > 1 ? 's' : ''}</span>
            </div>
          </div>
          <div>
            ${
              isPresent
                ? `<span class="period-status-chip present">
                     <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="20 6 9 17 4 12"></polyline></svg>
                     Present
                   </span>`
                : `<span class="period-status-chip absent">
                     <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
                     Absent
                   </span>`
            }
          </div>
        </div>
      `;
    });

    listEl.innerHTML = periodsHtml;
  }
};

window.CalendarView = CalendarView;
