// Электронная система тестирования — интерфейсные улучшения и анимации
(function () {
    'use strict';
    var reduceMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    // Высота шапки — для закреплённой панели таймера
    var nav = document.querySelector('.app-navbar');
    var setNavHeight = function () {
        if (nav) document.documentElement.style.setProperty('--nav-h', nav.offsetHeight + 'px');
    };
    setNavHeight();
    window.addEventListener('resize', setNavHeight);

    // ---------- Подтверждение опасных действий (удаление, завершение теста) ----------
    document.addEventListener('submit', function (e) {
        var form = e.target;
        var message = form.getAttribute('data-confirm') ||
            (e.submitter && e.submitter.getAttribute('data-confirm'));
        if (message && !form.dataset.autoSubmit && !window.confirm(message)) {
            e.preventDefault();
        }
    });

    // ---------- Плавный уход со страницы при переходе по ссылке ----------
    if (!reduceMotion) {
        document.addEventListener('click', function (e) {
            var a = e.target.closest('a[href]');
            if (!a || e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
            if (a.target || a.hasAttribute('download') || a.origin !== location.origin) return;
            var href = a.getAttribute('href');
            if (!href || href.charAt(0) === '#' || /\.(csv|xlsx|pdf)(\?|$)/.test(a.pathname)) return;
            e.preventDefault();
            document.body.classList.add('page-leaving');
            setTimeout(function () { location.href = a.href; }, 140);
        });
        // при возврате кнопкой «Назад» страница не должна остаться прозрачной
        window.addEventListener('pageshow', function () { document.body.classList.remove('page-leaving'); });
    }

    // ---------- Счётчики: число «досчитывает» до значения ----------
    function countUp(el) {
        var target = parseFloat(el.getAttribute('data-count'));
        if (isNaN(target)) return;
        var decimals = parseInt(el.getAttribute('data-decimals') || '0', 10);
        var suffix = el.getAttribute('data-suffix') || '';
        var format = function (v) { return v.toFixed(decimals).replace('.', ',') + suffix; };
        if (reduceMotion) { el.textContent = format(target); return; }
        var start = null, duration = 900;
        function step(ts) {
            if (!start) start = ts;
            var p = Math.min((ts - start) / duration, 1);
            var eased = 1 - Math.pow(1 - p, 3);
            el.textContent = format(target * eased);
            if (p < 1) requestAnimationFrame(step);
        }
        requestAnimationFrame(step);
    }

    // ---------- Полоски прогресса: заполняются при появлении ----------
    function growBar(bar) {
        var width = bar.style.width;
        if (!width || bar.id === 'answered-bar') return;
        bar.style.width = '0';
        requestAnimationFrame(function () { requestAnimationFrame(function () { bar.style.width = width; }); });
    }

    // ---------- Кольцо процента на странице результата ----------
    function fillRing(circle) {
        var percent = parseFloat(circle.getAttribute('data-percent')) || 0;
        var length = 2 * Math.PI * parseFloat(circle.getAttribute('r'));
        circle.style.strokeDasharray = length;
        circle.style.strokeDashoffset = length;
        requestAnimationFrame(function () {
            requestAnimationFrame(function () { circle.style.strokeDashoffset = length * (1 - percent / 100); });
        });
    }

    function animateInside(root) {
        root.querySelectorAll('[data-count]').forEach(countUp);
        root.querySelectorAll('.progress-bar').forEach(growBar);
        root.querySelectorAll('.ring-fg[data-percent]').forEach(fillRing);
    }

    // ---------- Появление блоков при прокрутке ----------
    var revealed = document.querySelectorAll('.reveal');
    if ('IntersectionObserver' in window && !reduceMotion) {
        var io = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (!entry.isIntersecting) return;
                entry.target.classList.add('is-visible');
                animateInside(entry.target);
                io.unobserve(entry.target);
            });
        }, { threshold: 0.12, rootMargin: '0px 0px -40px 0px' });
        revealed.forEach(function (el) { io.observe(el); });
    } else {
        // без анимаций (или старый браузер): сразу итоговое состояние, включая кольцо результата
        revealed.forEach(function (el) { el.classList.add('is-visible'); animateInside(el); });
    }
    // элементы вне .reveal анимируются сразу
    document.querySelectorAll('[data-count], .progress-bar, .ring-fg[data-percent]').forEach(function (el) {
        if (el.closest('.reveal')) return;
        if (el.matches('[data-count]')) countUp(el);
        else if (el.matches('.progress-bar')) growBar(el);
        else fillRing(el);
    });

    // ---------- Всплывающие уведомления исчезают сами ----------
    document.querySelectorAll('.toast-msg[data-autohide]').forEach(function (t, i) {
        setTimeout(function () {
            t.classList.add('hide');
            setTimeout(function () { t.remove(); }, 400);
        }, 4200 + i * 400);
        t.addEventListener('click', function () { t.classList.add('hide'); setTimeout(function () { t.remove(); }, 400); });
    });

    // ---------- Прохождение теста: таймер и прогресс ответов ----------
    var timer = document.getElementById('timer');
    var form = document.getElementById('attempt-form');
    if (timer && form) {
        var label = timer.querySelector('span') || timer;
        var left = parseInt(timer.getAttribute('data-seconds'), 10) || 0;
        (function tick() {
            var m = Math.floor(left / 60), s = left % 60;
            label.textContent = (m < 10 ? '0' : '') + m + ':' + (s < 10 ? '0' : '') + s;
            if (left <= 60) { timer.classList.remove('text-bg-warning'); timer.classList.add('text-bg-danger'); }
            if (left <= 0) { form.dataset.autoSubmit = 'true'; form.submit(); return; }
            left--;
            setTimeout(tick, 1000);
        })();

        var cards = form.querySelectorAll('[data-question]');
        var bar = document.getElementById('answered-bar');
        var counter = document.getElementById('answered-label');
        var update = function () {
            var done = 0;
            cards.forEach(function (card) {
                var answered = card.querySelector('input:checked') !== null;
                card.classList.toggle('answered', answered);
                if (answered) done++;
            });
            if (bar) bar.style.width = (cards.length ? done * 100 / cards.length : 0) + '%';
            if (counter) counter.textContent = 'Отвечено ' + done + ' из ' + cards.length;
        };
        form.addEventListener('change', update);
        update();
    }

    // ---------- Страница печати ведомости ----------
    var printBtn = document.getElementById('print-btn');
    if (printBtn) printBtn.addEventListener('click', function () { window.print(); });
    if (document.body && document.body.dataset.autoprint === 'true') {
        window.addEventListener('load', function () { setTimeout(function () { window.print(); }, 300); });
    }

    // ---------- Панель ведомости: при смене теста поля шапки подставятся заново ----------
    var sheetForm = document.getElementById('sheet-form');
    if (sheetForm) {
        var test = sheetForm.querySelector('select[name="testId"]');
        if (test) test.addEventListener('change', function () {
            ['discipline', 'teacher', 'controlForm'].forEach(function (n) {
                var el = sheetForm.querySelector('[name="' + n + '"]');
                if (el) el.value = '';
            });
        });
    }
})();
