// Synap3e — site interactions

// Reveal animations only apply when JS runs (CSS scopes hiding to html.js)
document.documentElement.classList.add('js');

// Mobile nav toggle
const navToggle = document.getElementById('navToggle');
const navLinks = document.getElementById('navLinks');

navToggle.addEventListener('click', () => {
  const open = navLinks.classList.toggle('open');
  navToggle.setAttribute('aria-expanded', String(open));
});

// Close the mobile menu after choosing a link
navLinks.addEventListener('click', (e) => {
  if (e.target.tagName === 'A') {
    navLinks.classList.remove('open');
    navToggle.setAttribute('aria-expanded', 'false');
  }
});

// Scroll reveal
const observer = new IntersectionObserver(
  (entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        entry.target.classList.add('visible');
        observer.unobserve(entry.target);
      }
    });
  },
  { threshold: 0.12 }
);
document.querySelectorAll('.reveal').forEach((el) => observer.observe(el));

// Footer year
document.getElementById('year').textContent = new Date().getFullYear();

// ------------------------------------------------------------
// Build demo: cycles idea → prototype → AI build → live,
// keeping the matching "How It Works" step card highlighted.
// ------------------------------------------------------------
(() => {
  const demo = document.getElementById('buildDemo');
  if (!demo) return;

  const scenes = demo.querySelectorAll('.build-scene');
  const urlBar = document.getElementById('buildUrl');
  const liveBadge = document.getElementById('buildLiveBadge');
  const progressBar = document.getElementById('buildProgressBar');
  const steps = document.querySelectorAll('#how .step');
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  const stages = [
    { url: 'synap3e.com/your-request', duration: 3600 },
    { url: 'synap3e.com/prototype', duration: 3600 },
    { url: 'ai-build · compiling…', duration: 4400 },
    { url: 'orders.limsbakery.com', duration: 5200 },
  ];

  // Without motion, just show the finished app.
  if (reducedMotion) {
    scenes[3].classList.add('active');
    liveBadge.classList.add('on');
    urlBar.textContent = stages[3].url;
    return;
  }

  let current = 0;
  let timer = null;
  let running = false;

  function showStage(i) {
    scenes.forEach((scene, idx) => {
      scene.classList.remove('leaving');
      if (idx === i) {
        // Re-trigger the scene's CSS animations on every loop
        scene.classList.remove('active');
        void scene.offsetWidth;
        scene.classList.add('active');
      } else if (scene.classList.contains('active')) {
        scene.classList.remove('active');
        scene.classList.add('leaving');
      }
    });
    steps.forEach((step, idx) => step.classList.toggle('step-active', idx === i));
    urlBar.textContent = stages[i].url;
    liveBadge.classList.toggle('on', i === 3);
    progressBar.classList.remove('run');
    void progressBar.offsetWidth;
    progressBar.style.animationDuration = stages[i].duration + 'ms';
    progressBar.classList.add('run');
  }

  function advance() {
    showStage(current);
    timer = setTimeout(() => {
      current = (current + 1) % stages.length;
      advance();
    }, stages[current].duration);
  }

  function start() {
    if (running) return;
    running = true;
    advance();
  }

  function stop() {
    running = false;
    clearTimeout(timer);
  }

  // Run only while the demo is on screen
  const demoObserver = new IntersectionObserver(
    (entries) => entries.forEach((e) => (e.isIntersecting ? start() : stop())),
    { threshold: 0.25 }
  );
  demoObserver.observe(demo);
})();
