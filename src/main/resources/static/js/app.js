// Электронная система тестирования — интерфейсные улучшения и анимации
(function () {
    'use strict';
    var reduceMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    // Высота шапки — для закреплённой панели таймера
    var nav = document.querySelector('.topbar');
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
        if (form.hasAttribute('data-undo') && !e.defaultPrevented) {
            e.preventDefault();
            undoableDelete(form);
        }
        // защита от двойного нажатия: кнопка блокируется и показывает «Отправляем…»
        if (!e.defaultPrevented && form.method.toLowerCase() === 'post' && !form.target && !form.hasAttribute('data-no-lock')) {
            var btn = e.submitter || form.querySelector('[type=submit]');
            setTimeout(function () {
                if (!btn) return;
                btn.dataset.html = btn.innerHTML;
                btn.disabled = true;
                btn.classList.add('is-sending');
                if (btn.offsetWidth > 60) btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>Отправляем…';
            }, 0);
        }
    });
    // при возврате кнопкой «Назад» кнопки снова активны
    window.addEventListener('pageshow', function () {
        document.querySelectorAll('.is-sending').forEach(function (b) {
            b.disabled = false; b.classList.remove('is-sending'); if (b.dataset.html) b.innerHTML = b.dataset.html;
        });
    });

    // ---------- Всплывающее уведомление (с кнопкой «Отменить» при необходимости) ----------
    function toast(text, action) {
        var stack = document.querySelector('.toast-stack');
        if (!stack) return null;
        var t = document.createElement('div');
        t.className = 'toast-msg';
        t.setAttribute('role', 'status');
        t.innerHTML = '<i class="bi bi-check-circle-fill"></i><div class="flex-grow-1"></div>';
        t.querySelector('div').textContent = text;
        if (action) {
            var b = document.createElement('button');
            b.type = 'button'; b.className = 'toast-action'; b.textContent = action.label;
            b.addEventListener('click', function () { action.run(); close(); });
            t.appendChild(b);
        }
        var close = function () { t.classList.add('hide'); setTimeout(function () { t.remove(); }, 400); };
        stack.appendChild(t);
        setTimeout(close, action ? 5200 : 3200);
        return t;
    }

    // ---------- Удаление с отменой (как в Gmail): элемент прячется, удаление уходит через 5 секунд ----------
    var pendingDeletes = [];
    function sendDelete(job) {
        if (job.sent) return;
        job.sent = true;
        clearTimeout(job.timer);
        fetch(job.form.action, { method: 'POST', body: new FormData(job.form), credentials: 'same-origin', keepalive: true })
            .then(function (r) { if (!r.ok) throw new Error(r.status); job.item.remove(); })
            .catch(function () { job.item.hidden = false; toast('Не удалось удалить, попробуйте ещё раз'); });
    }
    function undoableDelete(form) {
        var item = form.closest(form.getAttribute('data-undo-target') || '*');
        var job = { form: form, item: item, sent: false };
        item.classList.add('removing');
        setTimeout(function () { item.hidden = true; item.classList.remove('removing'); }, 220);
        job.timer = setTimeout(function () { sendDelete(job); }, 5000);
        pendingDeletes.push(job);
        toast(form.getAttribute('data-undo'), { label: 'Отменить', run: function () {
            if (job.sent) return;
            clearTimeout(job.timer); job.sent = true; item.hidden = false;
        } });
    }
    // если уйти со страницы раньше, удаление всё равно выполняется
    window.addEventListener('pagehide', function () { pendingDeletes.forEach(sendDelete); });

    // ---------- Редакторы: предупреждение о несохранённых изменениях и Ctrl+S ----------
    var guarded = document.querySelector('form[data-guard]');
    if (guarded) {
        var changed = false;
        guarded.addEventListener('input', function () { changed = true; });
        guarded.addEventListener('change', function () { changed = true; });
        guarded.addEventListener('submit', function () { changed = false; });
        window.addEventListener('beforeunload', function (e) { if (changed) { e.preventDefault(); e.returnValue = ''; } });
        document.addEventListener('keydown', function (e) {
            if ((e.ctrlKey || e.metaKey) && (e.key === 's' || e.key === 'ы')) { e.preventDefault(); guarded.requestSubmit(); }
        });
    }

    // ---------- Копировать ссылку ----------
    document.addEventListener('click', function (e) {
        var b = e.target.closest('[data-copy-path]');
        if (!b) return;
        var url = location.origin + b.getAttribute('data-copy-path');
        var done = function () { toast('Ссылка скопирована: ' + url); };
        if (navigator.clipboard && window.isSecureContext) {
            navigator.clipboard.writeText(url).then(done, function () { window.prompt('Скопируйте ссылку', url); });
        } else {
            window.prompt('Скопируйте ссылку', url);
        }
    });

    // ---------- Фильтры списков: применяются сразу, «/» — перейти к поиску ----------
    document.querySelectorAll('form.filter-bar, form.search-row').forEach(function (f) {
        f.querySelectorAll('select').forEach(function (s) { s.addEventListener('change', function () { f.requestSubmit(); }); });
        var q = f.querySelector('input[name=q]');
        if (!q) return;
        var wait = null;
        q.addEventListener('input', function () {
            clearTimeout(wait);
            wait = setTimeout(function () {
                try { sessionStorage.setItem('est-refocus', location.pathname); } catch (err) { /* ничего */ }
                f.requestSubmit();
            }, 700);
        });
        try {
            if (sessionStorage.getItem('est-refocus') === location.pathname) {
                sessionStorage.removeItem('est-refocus');
                q.focus();
                q.setSelectionRange(q.value.length, q.value.length);
            }
        } catch (err) { /* ничего */ }
    });
    document.addEventListener('keydown', function (e) {
        if (e.key !== '/' || e.ctrlKey || e.metaKey || e.altKey) return;
        if (e.target.matches('input, textarea, select, [contenteditable]')) return;
        var q = document.querySelector('form.filter-bar input[name=q], form.search-row input[name=q]');
        if (q) { e.preventDefault(); q.focus(); q.select(); }
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

    // ---------- Кликабельные строки таблиц: вся строка ведёт на подробную страницу ----------
    document.addEventListener('click', function (e) {
        var row = e.target.closest('tr[data-href]');
        if (!row || e.target.closest('a, button, form, input, select, label')) return;
        location.href = row.getAttribute('data-href');
    });

    // с клавиатуры: Tab до строки, Enter — открыть
    document.querySelectorAll('tr[data-href]').forEach(function (row) { row.tabIndex = 0; });
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' && e.target.matches('tr[data-href]')) location.href = e.target.getAttribute('data-href');
    });

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

    // ---------- Прохождение теста ----------
    var timer = document.getElementById('timer');
    var form = document.getElementById('attempt-form');
    if (timer && form) {
        var leaving = false;
        var label = timer.querySelector('span') || timer;
        var left = parseInt(timer.getAttribute('data-seconds'), 10) || 0;
        // таймер: за 5 минут жёлтый, на последней минуте красный и мигает
        (function tick() {
            var m = Math.floor(left / 60), s = left % 60;
            label.textContent = (m < 10 ? '0' : '') + m + ':' + (s < 10 ? '0' : '') + s;
            timer.classList.toggle('timer-normal', left > 300);
            timer.classList.toggle('text-bg-warning', left <= 300 && left > 60);
            timer.classList.toggle('text-bg-danger', left <= 60);
            if (left <= 0) { leaving = true; form.dataset.autoSubmit = 'true'; form.submit(); return; }
            left--;
            setTimeout(tick, 1000);
        })();

        var cards = Array.prototype.slice.call(form.querySelectorAll('[data-question]'));
        var bar = document.getElementById('answered-bar');
        var counter = document.getElementById('answered-label');
        var draftUrl = form.getAttribute('data-draft-url');
        var navItem = function (card) { return document.querySelector('.qnav-item[data-q="' + card.getAttribute('data-question') + '"]'); };
        var isAnswered = function (card) { return card.querySelector('input:checked') !== null; };
        var update = function () {
            var done = 0;
            cards.forEach(function (card) {
                var answered = isAnswered(card);
                card.classList.toggle('answered', answered);
                var nav = navItem(card);
                if (nav) nav.classList.toggle('answered', answered);
                var clear = card.querySelector('[data-clear]');
                if (clear) clear.hidden = !answered;
                if (answered) done++;
            });
            if (bar) bar.style.width = (cards.length ? done * 100 / cards.length : 0) + '%';
            if (counter) counter.textContent = 'Отвечено ' + done + ' из ' + cards.length;
        };
        form.addEventListener('change', update);
        update();

        // флажок «вернуться позже» (хранится в браузере для этой попытки)
        var flagKey = 'est-flags:' + draftUrl;
        var flags = {};
        try { flags = JSON.parse(localStorage.getItem(flagKey) || '{}'); } catch (e) { flags = {}; }
        var paintFlag = function (card) {
            var on = !!flags[card.getAttribute('data-question')];
            card.classList.toggle('flagged', on);
            var btn = card.querySelector('[data-flag]');
            btn.setAttribute('aria-pressed', on ? 'true' : 'false');
            btn.querySelector('span').textContent = on ? 'Отмечен' : 'Вернуться позже';
            var nav = navItem(card);
            if (nav) nav.classList.toggle('flagged', on);
        };
        cards.forEach(function (card) {
            paintFlag(card);
            card.querySelector('[data-flag]').addEventListener('click', function () {
                var id = card.getAttribute('data-question');
                if (flags[id]) delete flags[id]; else flags[id] = 1;
                try { localStorage.setItem(flagKey, JSON.stringify(flags)); } catch (e) { /* без хранилища флажки живут до перезагрузки */ }
                paintFlag(card);
            });
            var clear = card.querySelector('[data-clear]');
            if (clear) clear.addEventListener('click', function () {
                card.querySelectorAll('input:checked').forEach(function (i) { i.checked = false; });
                form.dispatchEvent(new Event('change', { bubbles: true }));
                var first = card.querySelector('input'); if (first) first.focus();
            });
        });

        // переход по номеру вопроса: плавно и с учётом закреплённой шапки
        var goTo = function (card) {
            var top = card.getBoundingClientRect().top + window.scrollY - (document.querySelector('.sticky-timer').offsetHeight + 16);
            window.scrollTo({ top: top, behavior: reduceMotion ? 'auto' : 'smooth' });
            card.classList.remove('pulse-focus'); void card.offsetWidth; card.classList.add('pulse-focus');
        };
        document.querySelectorAll('.qnav-item').forEach(function (a) {
            a.addEventListener('click', function (e) {
                e.preventDefault();
                goTo(document.getElementById('q' + a.getAttribute('data-q')));
            });
        });

        // клавиши 1–9: вариант в текущем вопросе (первый, что виден под шапкой)
        var lastCard = null;
        form.addEventListener('focusin', function (e) { lastCard = e.target.closest('[data-question]') || lastCard; });
        form.addEventListener('click', function (e) { lastCard = e.target.closest('[data-question]') || lastCard; });
        var currentCard = function () {
            var edge = document.querySelector('.sticky-timer').getBoundingClientRect().bottom + 8;
            if (lastCard) { var r = lastCard.getBoundingClientRect(); if (r.bottom > edge && r.top < window.innerHeight) return lastCard; }
            for (var i = 0; i < cards.length; i++) if (cards[i].getBoundingClientRect().bottom > edge + 40) return cards[i];
            return null;
        };
        document.addEventListener('keydown', function (e) {
            if (e.ctrlKey || e.metaKey || e.altKey || document.querySelector('.modal.show')) return;
            if (e.target.matches('input[type=text], input[type=search], textarea, select')) return;
            if (!/^[1-9]$/.test(e.key)) return;
            var card = currentCard();
            if (!card) return;
            var input = card.querySelectorAll('input')[parseInt(e.key, 10) - 1];
            if (!input) return;
            e.preventDefault();
            input.click();
            lastCard = card;
        });

        // сводка перед отправкой вместо системного окна
        var modalEl = document.getElementById('finish-modal');
        // окно должно лежать прямо в body: иначе анимированный контейнер страницы прячет его под затемнением
        if (modalEl) document.body.appendChild(modalEl);
        var summary = document.getElementById('finish-summary');
        var links = function (list) {
            return list.map(function (c) { return '<a href="#" data-jump="' + c.getAttribute('data-question') + '">' + c.getAttribute('data-num') + '</a>'; }).join(', ');
        };
        form.addEventListener('submit', function (e) {
            if (form.dataset.confirmed || form.dataset.autoSubmit || !modalEl || !window.bootstrap) return;
            e.preventDefault();
            var open = cards.filter(function (c) { return !isAnswered(c); });
            var flagged = cards.filter(function (c) { return c.classList.contains('flagged'); });
            var html = '<p class="mb-2">Отвечено <b>' + (cards.length - open.length) + ' из ' + cards.length + '</b>.</p>';
            if (open.length) html += '<p class="mb-2 text-danger"><i class="bi bi-exclamation-circle me-1"></i>Без ответа: ' + links(open) + '</p>';
            if (flagged.length) html += '<p class="mb-2"><i class="bi bi-flag-fill text-warning me-1"></i>Отмечены, чтобы вернуться: ' + links(flagged) + '</p>';
            if (!open.length && !flagged.length) html += '<p class="mb-2 text-success"><i class="bi bi-check-circle me-1"></i>Все вопросы отвечены.</p>';
            html += '<p class="small text-muted mb-0">После отправки изменить ответы будет нельзя.</p>';
            summary.innerHTML = html;
            bootstrap.Modal.getOrCreateInstance(modalEl).show();
        });
        if (summary) summary.addEventListener('click', function (e) {
            var a = e.target.closest('[data-jump]');
            if (!a) return;
            e.preventDefault();
            bootstrap.Modal.getInstance(modalEl).hide();
            modalEl.addEventListener('hidden.bs.modal', function once() {
                modalEl.removeEventListener('hidden.bs.modal', once);
                goTo(document.getElementById('q' + a.getAttribute('data-jump')));
            });
        });
        var confirmBtn = document.getElementById('finish-confirm');
        if (confirmBtn) confirmBtn.addEventListener('click', function () {
            form.dataset.confirmed = 'true';
            leaving = true;
            confirmBtn.disabled = true;
            confirmBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>Отправляем…';
            form.requestSubmit(document.getElementById('finish-btn'));
        });

        // предупреждение, если закрыть вкладку или уйти со страницы, не завершив тест
        window.addEventListener('beforeunload', function (e) {
            if (leaving) return;
            e.preventDefault();
            e.returnValue = '';
        });

        // Автосохранение: при каждом изменении ответа форма уходит на сервер (с паузой, чтобы не слать лишнее)
        var draftUrl = form.getAttribute('data-draft-url');
        var state = document.getElementById('save-state');
        var dirty = false, busy = false, pause = null;
        var show = function (cls, icon, text) {
            if (!state) return;
            state.className = 'save-state ms-auto ' + cls;
            state.innerHTML = '<i class="bi ' + icon + '"></i>';
            state.appendChild(document.createTextNode(text));
        };
        var save = function () {
            if (!draftUrl || busy || !dirty || form.dataset.autoSubmit || form.dataset.confirmed) return;
            busy = true; dirty = false;
            show('saving', 'bi-arrow-repeat', 'Сохраняем…');
            fetch(draftUrl, { method: 'POST', body: new FormData(form), credentials: 'same-origin',
                              headers: { 'Accept': 'application/json' } })
                .then(function (r) { if (!r.ok) throw new Error(r.status); return r.json(); })
                .then(function (d) { show('saved', 'bi-cloud-check', 'Сохранено в ' + d.saved); })
                .catch(function () { dirty = true; show('error', 'bi-wifi-off', 'Не сохранено: проверьте интернет'); })
                .then(function () { busy = false; if (dirty) { clearTimeout(pause); pause = setTimeout(save, 4000); } });
        };
        form.addEventListener('change', function () { dirty = true; clearTimeout(pause); pause = setTimeout(save, 600); });
        window.addEventListener('online', save);
    }

    // ---------- Страница печати ведомости ----------
    var printBtn = document.getElementById('print-btn');
    if (printBtn) printBtn.addEventListener('click', function () { window.print(); });
    if (document.body && document.body.dataset.autoprint === 'true') {
        window.addEventListener('load', function () { setTimeout(function () { window.print(); }, 300); });
    }

    // ---------- Простые фильтры: выбор в списке сразу применяется ----------
    document.querySelectorAll('select[data-autosubmit-simple]').forEach(function (select) {
        select.addEventListener('change', function () {
            var form = select.form;
            // группа и преподаватель — взаимоисключающие
            form.querySelectorAll('select[data-autosubmit-simple]').forEach(function (other) {
                if (other !== select) other.value = '';
            });
            form.submit();
        });
    });

    // ---------- Панель ведомости: группа → дисциплина → тест, страница обновляется при выборе ----------
    var sheetForm = document.getElementById('sheet-form');
    if (sheetForm) {
        var reset = { groupId: ['subjectId', 'testId'], subjectId: ['testId'], testId: [] };
        sheetForm.querySelectorAll('select[data-autosubmit]').forEach(function (select) {
            select.addEventListener('change', function () {
                (reset[select.name] || []).forEach(function (n) {
                    var el = sheetForm.querySelector('[name="' + n + '"]');
                    if (el) el.value = '';
                });
                sheetForm.removeAttribute('target');
                sheetForm.action = sheetForm.getAttribute('data-panel-action') || sheetForm.action;
                sheetForm.submit();
            });
        });
    }
})();
