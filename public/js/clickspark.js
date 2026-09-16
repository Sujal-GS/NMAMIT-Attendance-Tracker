/**
 * React Bits - ClickSpark Component (@react-bits/ClickSpark)
 * Creates particle spark bursts at click position across the website.
 */

(function () {
  const config = {
    sparkColor: '#ffffff',
    sparkSize: 10,
    sparkRadius: 15,
    sparkCount: 8,
    duration: 400,
    easing: 'ease-out',
    extraScale: 1.0
  };

  let canvas = null;
  let ctx = null;
  let sparks = [];
  let animId = null;
  let dpr = 1;

  function easeFunc(t, easing) {
    switch (easing) {
      case 'linear':
        return t;
      case 'ease-in':
        return t * t;
      case 'ease-in-out':
        return t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;
      default: // 'ease-out'
        return t * (2 - t);
    }
  }

  function resizeCanvas() {
    if (!canvas || !ctx) return;
    dpr = window.devicePixelRatio || 1;
    const width = window.innerWidth;
    const height = window.innerHeight;

    canvas.width = Math.floor(width * dpr);
    canvas.height = Math.floor(height * dpr);
    canvas.style.width = width + 'px';
    canvas.style.height = height + 'px';

    ctx.resetTransform?.();
    ctx.scale(dpr, dpr);
  }

  function draw(timestamp) {
    if (!canvas || !ctx) return;

    ctx.clearRect(0, 0, window.innerWidth, window.innerHeight);

    const activeColor = config.sparkColor || '#ffffff';

    ctx.strokeStyle = activeColor;
    ctx.lineWidth = 2;
    ctx.lineCap = 'round';

    sparks = sparks.filter(spark => {
      const elapsed = timestamp - spark.startTime;
      if (elapsed >= config.duration) {
        return false;
      }

      const progress = elapsed / config.duration;
      const eased = easeFunc(progress, config.easing);

      const distance = eased * config.sparkRadius * config.extraScale;
      const lineLength = config.sparkSize * (1 - eased);

      const x1 = spark.x + distance * Math.cos(spark.angle);
      const y1 = spark.y + distance * Math.sin(spark.angle);
      const x2 = spark.x + (distance + lineLength) * Math.cos(spark.angle);
      const y2 = spark.y + (distance + lineLength) * Math.sin(spark.angle);

      ctx.beginPath();
      ctx.moveTo(x1, y1);
      ctx.lineTo(x2, y2);
      ctx.stroke();

      return true;
    });

    if (sparks.length > 0) {
      animId = requestAnimationFrame(draw);
    } else {
      ctx.clearRect(0, 0, window.innerWidth, window.innerHeight);
      animId = null;
    }
  }

  function handleClick(e) {
    // Ignore synthetic or disabled clicks if needed, but allow all interactive elements
    const x = e.clientX;
    const y = e.clientY;

    if (x === undefined || y === undefined) return;

    const now = performance.now();
    const count = config.sparkCount;

    for (let i = 0; i < count; i++) {
      sparks.push({
        x,
        y,
        angle: (2 * Math.PI * i) / count,
        startTime: now
      });
    }

    if (!animId) {
      animId = requestAnimationFrame(draw);
    }
  }

  function init() {
    if (document.getElementById('click-spark-canvas')) return;

    canvas = document.createElement('canvas');
    canvas.id = 'click-spark-canvas';
    canvas.setAttribute('aria-hidden', 'true');
    Object.assign(canvas.style, {
      position: 'fixed',
      top: '0',
      left: '0',
      width: '100vw',
      height: '100vh',
      pointerEvents: 'none',
      zIndex: '99998',
      userSelect: 'none',
      display: 'block'
    });

    document.body.appendChild(canvas);
    ctx = canvas.getContext('2d');

    resizeCanvas();
    window.addEventListener('resize', resizeCanvas);
    window.addEventListener('pointerdown', handleClick, { passive: true });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
