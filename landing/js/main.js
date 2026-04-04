// ─── Fade-in on scroll (IntersectionObserver) ─────────────────
const observer = new IntersectionObserver(
  (entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        entry.target.classList.add('visible');
        observer.unobserve(entry.target);
      }
    });
  },
  { threshold: 0.1, rootMargin: '0px 0px -40px 0px' }
);

document.querySelectorAll('.fade-in').forEach((el) => observer.observe(el));

// ─── Nav: añadir clase al hacer scroll ────────────────────────
const nav = document.getElementById('nav');
window.addEventListener('scroll', () => {
  nav.classList.toggle('scrolled', window.scrollY > 20);
}, { passive: true });

// ─── Menú hamburguesa (móvil) ─────────────────────────────────
const hamburger = document.getElementById('hamburger');
const navLinks  = document.getElementById('navLinks');

hamburger.addEventListener('click', () => {
  const open = navLinks.classList.toggle('open');
  hamburger.setAttribute('aria-expanded', String(open));
});

navLinks.querySelectorAll('a').forEach((link) => {
  link.addEventListener('click', () => {
    navLinks.classList.remove('open');
    hamburger.setAttribute('aria-expanded', 'false');
  });
});

// Cerrar menú si se hace click fuera
document.addEventListener('click', (e) => {
  if (!nav.contains(e.target)) {
    navLinks.classList.remove('open');
    hamburger.setAttribute('aria-expanded', 'false');
  }
});

// ─── Scroll suave para anclas ─────────────────────────────────
document.querySelectorAll('a[href^="#"]').forEach((anchor) => {
  anchor.addEventListener('click', (e) => {
    const selector = anchor.getAttribute('href');
    if (selector === '#') return;
    const target = document.querySelector(selector);
    if (target) {
      e.preventDefault();
      const offset = 72; // altura del nav
      const top = target.getBoundingClientRect().top + window.scrollY - offset;
      window.scrollTo({ top, behavior: 'smooth' });
    }
  });
});

// ─── Roadmap: expandir items adicionales ──────────────────────
const roadmapToggle = document.getElementById('roadmapToggle');
const roadmapExtra  = document.getElementById('roadmapExtra');
if (roadmapToggle && roadmapExtra) {
  roadmapToggle.addEventListener('click', () => {
    const expanded = roadmapToggle.getAttribute('aria-expanded') === 'true';
    roadmapToggle.setAttribute('aria-expanded', String(!expanded));
    roadmapExtra.setAttribute('aria-hidden', String(expanded));
    roadmapExtra.classList.toggle('open', !expanded);
    roadmapToggle.querySelector('.roadmap-toggle__label').textContent = expanded ? 'Ver 11 más' : 'Ver menos';
  });
}

// ─── Botones "Copiar" en bloques de código ────────────────────
document.querySelectorAll('[data-clipboard]').forEach((btn) => {
  btn.addEventListener('click', () => {
    const code = btn.closest('.code-block').querySelector('code').textContent;
    navigator.clipboard.writeText(code).then(() => {
      btn.textContent = '¡Copiado!';
      btn.classList.add('copied');
      setTimeout(() => {
        btn.textContent = 'Copiar';
        btn.classList.remove('copied');
      }, 2000);
    }).catch(() => {
      // Fallback para navegadores sin permisos de portapapeles
      const ta = document.createElement('textarea');
      ta.value = code;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      document.body.removeChild(ta);
      btn.textContent = '¡Copiado!';
      btn.classList.add('copied');
      setTimeout(() => {
        btn.textContent = 'Copiar';
        btn.classList.remove('copied');
      }, 2000);
    });
  });
});
