/**
 * Custom Glassmorphic Select Component
 * Elevates native <select> elements into dark frosted glass dropdown menus
 */

const CustomSelect = {
  instances: new Map(), // selectEl -> wrapperEl

  init() {
    this.enhanceAll();
    this.bindGlobalEvents();
  },

  enhanceAll() {
    const selects = document.querySelectorAll('select.form-input, select[data-custom-select]');
    selects.forEach(sel => this.enhance(sel));
  },

  enhance(selectEl) {
    if (!selectEl || this.instances.has(selectEl)) return;

    // Hide native select visually while keeping accessible for forms
    selectEl.classList.add('custom-select-native-hidden');

    const wrapper = document.createElement('div');
    wrapper.className = 'custom-select-wrapper';
    if (selectEl.id) wrapper.id = `custom-select-for-${selectEl.id}`;

    // Pass along relevant sizing classes
    if (selectEl.classList.contains('sim-select')) wrapper.classList.add('sim-select-custom');
    if (selectEl.classList.contains('heatmap-filter-select')) wrapper.classList.add('heatmap-filter-custom');

    const trigger = document.createElement('button');
    trigger.type = 'button';
    trigger.className = 'custom-select-trigger';
    trigger.setAttribute('aria-haspopup', 'listbox');
    trigger.setAttribute('aria-expanded', 'false');

    const triggerLabel = document.createElement('span');
    triggerLabel.className = 'custom-select-label';

    const triggerArrow = document.createElement('span');
    triggerArrow.className = 'custom-select-arrow';
    triggerArrow.innerHTML = `
      <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" width="14" height="14">
        <path d="M5 7.5L10 12.5L15 7.5" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
    `;

    trigger.appendChild(triggerLabel);
    trigger.appendChild(triggerArrow);

    const dropdown = document.createElement('div');
    dropdown.className = 'custom-select-dropdown';
    dropdown.setAttribute('role', 'listbox');
    dropdown.tabIndex = -1;

    const scrollContainer = document.createElement('div');
    scrollContainer.className = 'custom-select-scroll';
    dropdown.appendChild(scrollContainer);

    wrapper.appendChild(trigger);
    wrapper.appendChild(dropdown);

    // Insert wrapper next to native select
    selectEl.parentNode.insertBefore(wrapper, selectEl.nextSibling);

    const instanceData = {
      selectEl,
      wrapper,
      trigger,
      triggerLabel,
      dropdown,
      scrollContainer,
      isOpen: false
    };

    this.instances.set(selectEl, instanceData);

    // Populate options
    this.refresh(selectEl);

    // Bind instance events
    trigger.addEventListener('click', (e) => {
      e.stopPropagation();
      this.toggle(selectEl);
    });

    // Native select change event listener (sync back if changed programmatically)
    selectEl.addEventListener('change', () => {
      this.syncFromNative(selectEl);
    });

    // Observe changes to native select's children (dynamic options added by API)
    const observer = new MutationObserver(() => {
      this.refresh(selectEl);
    });
    observer.observe(selectEl, { childList: true, subtree: true, attributes: true });
    instanceData.observer = observer;
  },

  refresh(selectEl) {
    const data = this.instances.get(selectEl);
    if (!data) return;

    const { triggerLabel, scrollContainer } = data;
    scrollContainer.innerHTML = '';

    const options = Array.from(selectEl.options);
    const selectedOption = selectEl.options[selectEl.selectedIndex] || options[0];

    if (selectedOption) {
      triggerLabel.textContent = selectedOption.textContent;
    } else {
      triggerLabel.textContent = 'Select...';
    }

    options.forEach(opt => {
      const optEl = document.createElement('div');
      optEl.className = 'custom-select-option';
      optEl.setAttribute('role', 'option');
      optEl.setAttribute('data-value', opt.value);

      const isSelected = (opt.value === selectEl.value);
      if (isSelected) optEl.classList.add('is-selected');

      const textSpan = document.createElement('span');
      textSpan.className = 'option-text';
      textSpan.textContent = opt.textContent;
      textSpan.title = opt.textContent;

      const checkSpan = document.createElement('span');
      checkSpan.className = 'option-check';
      checkSpan.innerHTML = `
        <svg viewBox="0 0 16 16" fill="currentColor" width="13" height="13">
          <path d="M13.78 4.22a.75.75 0 010 1.06l-7.25 7.25a.75.75 0 01-1.06 0L2.22 9.28a.75.75 0 011.06-1.06L6 10.94l6.72-6.72a.75.75 0 011.06 0z"/>
        </svg>
      `;

      optEl.appendChild(textSpan);
      optEl.appendChild(checkSpan);

      optEl.addEventListener('click', (e) => {
        e.stopPropagation();
        this.selectOption(selectEl, opt.value);
      });

      scrollContainer.appendChild(optEl);
    });
  },

  syncFromNative(selectEl) {
    const data = this.instances.get(selectEl);
    if (!data) return;

    const { triggerLabel, scrollContainer } = data;
    const selectedOption = selectEl.options[selectEl.selectedIndex];
    if (selectedOption) {
      triggerLabel.textContent = selectedOption.textContent;
    }

    scrollContainer.querySelectorAll('.custom-select-option').forEach(optEl => {
      const val = optEl.getAttribute('data-value');
      if (val === selectEl.value) {
        optEl.classList.add('is-selected');
      } else {
        optEl.classList.remove('is-selected');
      }
    });
  },

  selectOption(selectEl, value) {
    if (selectEl.value !== value) {
      selectEl.value = value;
      // Trigger native change event for listeners (Simulator, Heatmap, etc.)
      const event = new Event('change', { bubbles: true });
      selectEl.dispatchEvent(event);
    }
    this.syncFromNative(selectEl);
    this.close(selectEl);
  },

  toggle(selectEl) {
    const data = this.instances.get(selectEl);
    if (!data) return;
    if (data.isOpen) {
      this.close(selectEl);
    } else {
      this.open(selectEl);
    }
  },

  open(selectEl) {
    // Close other open instances
    this.closeAll();

    const data = this.instances.get(selectEl);
    if (!data) return;

    // Check available space below trigger
    const rect = data.trigger.getBoundingClientRect();
    const spaceBelow = window.innerHeight - rect.bottom;
    if (spaceBelow < 260 && rect.top > spaceBelow) {
      data.wrapper.classList.add('open-upward');
    } else {
      data.wrapper.classList.remove('open-upward');
    }

    data.isOpen = true;
    data.wrapper.classList.add('is-open');
    data.trigger.setAttribute('aria-expanded', 'true');

    // Scroll active item into view
    const selectedItem = data.scrollContainer.querySelector('.custom-select-option.is-selected');
    if (selectedItem) {
      selectedItem.scrollIntoView({ block: 'nearest' });
    }
  },

  close(selectEl) {
    const data = this.instances.get(selectEl);
    if (!data) return;

    data.isOpen = false;
    data.wrapper.classList.remove('is-open');
    data.trigger.setAttribute('aria-expanded', 'false');
  },

  closeAll() {
    this.instances.forEach((data, selectEl) => {
      if (data.isOpen) this.close(selectEl);
    });
  },

  bindGlobalEvents() {
    // Click outside to close
    document.addEventListener('click', (e) => {
      if (!e.target.closest('.custom-select-wrapper')) {
        this.closeAll();
      }
    });

    // Keyboard support
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') {
        this.closeAll();
      }
    });
  }
};

window.CustomSelect = CustomSelect;

// Auto initialize on DOMContentLoaded or immediately if DOM is ready
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => CustomSelect.init());
} else {
  CustomSelect.init();
}
