// Подтверждение опасных действий (удаление, завершение теста)
document.addEventListener('submit', function (e) {
    var form = e.target;
    var message = form.getAttribute('data-confirm') ||
        (e.submitter && e.submitter.getAttribute('data-confirm'));
    if (message && !form.dataset.autoSubmit && !window.confirm(message)) {
        e.preventDefault();
    }
});

// Таймер прохождения теста: по истечении времени ответы отправляются автоматически
(function () {
    var timer = document.getElementById('timer');
    var form = document.getElementById('attempt-form');
    if (!timer || !form) return;
    var left = parseInt(timer.getAttribute('data-seconds'), 10) || 0;
    function tick() {
        var m = Math.floor(left / 60), s = left % 60;
        timer.textContent = (m < 10 ? '0' : '') + m + ':' + (s < 10 ? '0' : '') + s;
        if (left <= 60) timer.className = 'timer badge text-bg-danger fs-6';
        if (left <= 0) {
            form.dataset.autoSubmit = 'true';
            form.submit();
            return;
        }
        left--;
        setTimeout(tick, 1000);
    }
    tick();
})();

// Страница печати ведомости: кнопка печати и автоматический вызов диалога
(function () {
    var btn = document.getElementById('print-btn');
    if (btn) btn.addEventListener('click', function () { window.print(); });
    if (document.body && document.body.dataset.autoprint === 'true') {
        window.addEventListener('load', function () { setTimeout(function () { window.print(); }, 300); });
    }
})();

// Панель ведомости: при смене теста дисциплина и преподаватель подставятся заново
(function () {
    var form = document.getElementById('sheet-form');
    if (!form) return;
    var test = form.querySelector('select[name="testId"]');
    if (test) test.addEventListener('change', function () {
        ['discipline', 'teacher', 'controlForm'].forEach(function (n) {
            var el = form.querySelector('[name="' + n + '"]');
            if (el) el.value = '';
        });
    });
})();
