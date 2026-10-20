const app = document.getElementById('app')
console.log('[CF] app element:', app)

const REFRESH_INTERVAL_MS = 10_000

let refreshTimer = null
let currentAbort = null
let currentChart = null
let currentLanguageChart = null
let currentContestId = null
let lastSubmissionCount = null

function destroyCharts() {
    if (currentChart)         { try { currentChart.destroy() }         catch (_) {} currentChart = null }
    if (currentLanguageChart) { try { currentLanguageChart.destroy() } catch (_) {} currentLanguageChart = null }
}

function cleanup() {
    if (refreshTimer) { clearTimeout(refreshTimer); refreshTimer = null }
    if (currentAbort) { try { currentAbort.abort() } catch (_) {} currentAbort = null }
    destroyCharts()
    currentContestId = null
    lastSubmissionCount = null
}

function route() {
    cleanup()
    const hash = location.hash || '#/'
    const m = hash.match(/^#\/contest\/(\d+)$/)
    if (m) {
        currentContestId = m[1]
        loadContest(m[1], false)
    } else {
        renderHome()
    }
}
window.addEventListener('hashchange', route)
window.addEventListener('DOMContentLoaded', route)

function renderHome() {
    app.innerHTML = `
    <div class="home">
      <h1>CF Analysis</h1>
      <p class="muted">Аналитика контестов Codeforces: вердикты, языки, участники.</p>
      <form id="search-form" class="search-form">
        <input type="text" inputmode="numeric" id="contest-id"
               placeholder="ID контеста, например 551173" required>
        <button type="submit">Открыть</button>
      </form>
    </div>
  `
    document.getElementById('search-form').addEventListener('submit', (e) => {
        e.preventDefault()
        const id = document.getElementById('contest-id').value.trim()
        if (/^\d+$/.test(id)) location.hash = `#/contest/${id}`
    })
}

async function loadContest(id, silent) {
    if (currentAbort) { try { currentAbort.abort() } catch (_) {} }
    currentAbort = new AbortController()

    if (!silent) app.innerHTML = `<div class="loading">Загрузка контеста ${id}...</div>`

    try {
        console.log(`[CF] fetch /api/contests/${id} silent=${silent}`)
        const res = await fetch(`/api/contests/${id}`, { signal: currentAbort.signal })
        console.log(`[CF] response ${res.status}`)
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        const submissions = await res.json()
        console.log(`[CF] parsed JSON: len=${submissions?.length}, isArray=${Array.isArray(submissions)}`)

        if (currentContestId !== id) { console.log('[CF] contest changed, skip render'); return }
        if (silent && lastSubmissionCount === submissions.length) { console.log('[CF] silent: no changes'); return }

        console.log('[CF] calling renderContestData')
        renderContestData(id, submissions, silent)
        console.log('[CF] renderContestData done')
    } catch (e) {
        if (e.name === 'AbortError') return
        console.error('[CF] load failed:', e)
        if (!silent) {
            app.innerHTML = `<div class="contest"><a href="#/" class="back">← Назад</a>
        <div class="error">Ошибка загрузки: ${e.message}</div></div>`
        }
    } finally {
        currentAbort = null
        if (currentContestId === id) {
            refreshTimer = setTimeout(() => loadContest(id, true), REFRESH_INTERVAL_MS)
        }
    }
}

// ---------- Агрегации ----------

function aggregateVerdicts(subs) {
    const m = {}
    for (const s of subs) {
        const v = s.verdict || 'UNKNOWN'
        m[v] = (m[v] || 0) + 1
    }
    return m
}

function aggregateProblems(subs) {
    const m = {}
    for (const s of subs) {
        if (!s.problem || !s.problem.index) continue
        const idx = s.problem.index
        if (!m[idx]) m[idx] = { name: s.problem.name || '—', rating: s.problem.rating, attempts: 0, solved: 0 }
        m[idx].attempts++
        if (s.verdict === 'OK') m[idx].solved++
    }
    return m
}

/**
 * Расширенная агрегация по языкам:
 * для каждого языка собираем общее число посылок, распределение по вердиктам,
 * среднее время работы и среднее потребление памяти.
 */
function aggregateLanguages(subs) {
    const map = {}
    for (const s of subs) {
        const lang = s.programmingLanguage || 'Неизвестно'
        if (!map[lang]) {
            map[lang] = {
                total: 0,
                verdicts: {},
                timeSum: 0, timeCount: 0,
                memSum: 0,  memCount: 0,
                solved: new Set(),
            }
        }
        const e = map[lang]
        e.total++
        const v = s.verdict || 'UNKNOWN'
        e.verdicts[v] = (e.verdicts[v] || 0) + 1
        if (typeof s.timeConsumedMillis   === 'number') { e.timeSum += s.timeConsumedMillis; e.timeCount++ }
        if (typeof s.memoryConsumedBytes  === 'number') { e.memSum  += s.memoryConsumedBytes; e.memCount++ }
        if (s.verdict === 'OK' && s.problem?.index) e.solved.add(s.problem.index)
    }
    return Object.entries(map)
        .map(([name, e]) => {
            const v = e.verdicts
            const ok  = v['OK']                     || 0
            const wa  = v['WRONG_ANSWER']           || 0
            const tle = v['TIME_LIMIT_EXCEEDED']    || 0
            const re  = v['RUNTIME_ERROR']          || 0
            const ce  = v['COMPILATION_ERROR']      || 0
            const other = e.total - ok - wa - tle - re - ce
            return {
                name,
                total: e.total,
                ok, wa, tle, re, ce, other,
                acceptRate: e.total > 0 ? ok / e.total : 0,
                avgTimeMs: e.timeCount > 0 ? e.timeSum / e.timeCount : null,
                avgMemKb:  e.memCount  > 0 ? e.memSum  / e.memCount / 1024 : null,
                solvedCount: e.solved.size,
            }
        })
        .sort((a, b) => b.total - a.total)
}

function aggregateParticipants(subs) {
    const map = new Map()
    for (const s of subs) {
        for (const m of (s.author?.members ?? [])) {
            if (!m || !m.handle) continue
            if (!map.has(m.handle)) map.set(m.handle, { solved: new Set(), attempts: 0 })
            const e = map.get(m.handle)
            e.attempts++
            if (s.verdict === 'OK' && s.problem?.index) e.solved.add(s.problem.index)
        }
    }
    return Array.from(map.entries())
        .map(([handle, v]) => ({
            handle, solved: v.solved.size, attempts: v.attempts,
            rate: v.attempts > 0 ? v.solved.size / v.attempts : 0,
        }))
        .sort((a, b) => b.solved - a.solved || a.attempts - b.attempts)
}

function rateClass(rate) {
    if (rate >= 0.7) return 'rate-high'
    if (rate >= 0.4) return 'rate-mid'
    return 'rate-low'
}

// ---------- Рендер ----------

function renderContestData(id, submissions, silent) {
    console.log('[CF] renderContestData start')
    const scrollY = silent ? window.scrollY : 0
    lastSubmissionCount = submissions.length
    destroyCharts()

    let verdicts, problems, langs, participants
    try {
        verdicts     = aggregateVerdicts(submissions)
        problems     = aggregateProblems(submissions)
        langs        = aggregateLanguages(submissions)
        participants = aggregateParticipants(submissions)
        console.log('[CF] aggregations OK')
    } catch (e) {
        console.error('[CF] aggregation failed:', e)
        app.innerHTML = `<div class="error">Ошибка агрегации: ${e.message}</div>`
        return
    }

    const accepted = verdicts['OK'] || 0
    const acceptRate = submissions.length ? ((accepted / submissions.length) * 100).toFixed(1) : '0.0'
    const topLangs = langs.slice(0, 15)
    const topParticipants = participants.slice(0, 15)
    const chartAvailable = typeof Chart !== 'undefined'
    const chartWarn = chartAvailable ? '' : '<div class="error" style="margin-bottom:1rem">Chart.js не загружен — графики недоступны</div>'

    console.log('[CF] Chart available:', chartAvailable)

    try {
        app.innerHTML = `
      <div class="contest">
        <a href="#/" class="back">← Назад</a>
        <h1>Контест ${id}</h1>
        ${chartWarn}
        <div class="stats">
          <div class="stat"><div class="stat-label">Посылок</div><div class="stat-value">${submissions.length}</div></div>
          <div class="stat"><div class="stat-label">Участников</div><div class="stat-value">${participants.length}</div></div>
          <div class="stat"><div class="stat-label">Принято</div><div class="stat-value">${accepted}</div></div>
          <div class="stat"><div class="stat-label">Accept rate</div><div class="stat-value">${acceptRate}%</div></div>
        </div>

        ${chartAvailable ? `
        <section>
          <h2>Распределение вердиктов</h2>
          <div class="chart-wrap"><canvas id="verdict-chart" height="200"></canvas></div>
        </section>

        <section>
          <h2>Языки программирования — популярность и качество</h2>
          <div class="chart-wrap chart-wide chart-language"><canvas id="language-chart"></canvas></div>
        </section>
        ` : ''}

        <section>
          <h2>Детально по языкам <span class="muted small">— всего ${langs.length}</span></h2>
          <div class="table-wrap">
            <table class="lang-table">
              <thead>
                <tr>
                  <th>Язык</th>
                  <th class="num">Всего</th>
                  <th class="num">OK</th>
                  <th class="num">AC rate</th>
                  <th class="num" title="Wrong Answer">WA</th>
                  <th class="num" title="Time Limit Exceeded">TLE</th>
                  <th class="num" title="Runtime Error">RE</th>
                  <th class="num" title="Compilation Error">CE</th>
                  <th class="num" title="Прочие (MLE, PE, …)">Др</th>
                  <th class="num">Ср. время</th>
                  <th class="num">Ср. память</th>
                </tr>
              </thead>
              <tbody>
                ${langs.map(l => `
                  <tr>
                    <td class="lang-name">${escapeHtml(l.name)}</td>
                    <td class="num">${l.total}</td>
                    <td class="num">${l.ok}</td>
                    <td class="num ${rateClass(l.acceptRate)}">${(l.acceptRate * 100).toFixed(1)}%</td>
                    <td class="num">${l.wa   || '—'}</td>
                    <td class="num">${l.tle  || '—'}</td>
                    <td class="num">${l.re   || '—'}</td>
                    <td class="num">${l.ce   || '—'}</td>
                    <td class="num">${l.other || '—'}</td>
                    <td class="num">${l.avgTimeMs !== null ? Math.round(l.avgTimeMs) + ' мс' : '—'}</td>
                    <td class="num">${l.avgMemKb  !== null ? Math.round(l.avgMemKb)  + ' КБ' : '—'}</td>
                  </tr>
                `).join('')}
              </tbody>
            </table>
          </div>
        </section>

        <section>
          <h2>Топ участников</h2>
          <div class="table-wrap">
            <table>
              <thead><tr><th>#</th><th>Handle</th><th>Решено</th><th>Попыток</th><th>AC rate</th></tr></thead>
              <tbody>
                ${topParticipants.map((p, i) => `
                  <tr><td class="muted">${i + 1}</td><td class="mono">${escapeHtml(p.handle)}</td>
                  <td>${p.solved}</td><td>${p.attempts}</td><td>${(p.rate * 100).toFixed(0)}%</td></tr>
                `).join('')}
              </tbody>
            </table>
          </div>
        </section>

        <section>
          <h2>Задачи</h2>
          <div class="table-wrap">
            <table>
              <thead><tr><th>#</th><th>Название</th><th>Rating</th><th>Попыток</th><th>Решено</th><th>Solve rate</th></tr></thead>
              <tbody>
                ${Object.entries(problems).sort(([a], [b]) => a.localeCompare(b)).map(([idx, p]) => `
                  <tr><td class="mono">${idx}</td><td>${escapeHtml(p.name)}</td>
                  <td class="muted">${p.rating ?? '—'}</td><td>${p.attempts}</td>
                  <td>${p.solved}</td><td>${(p.attempts ? p.solved / p.attempts * 100 : 0).toFixed(1)}%</td></tr>
                `).join('')}
              </tbody>
            </table>
          </div>
        </section>
      </div>
    `
        console.log('[CF] innerHTML set, DOM updated')
    } catch (e) {
        console.error('[CF] innerHTML failed:', e)
        app.innerHTML = `<div class="error">Ошибка рендера HTML: ${e.message}</div>`
        return
    }

    if (!chartAvailable) {
        if (silent) window.scrollTo(0, scrollY)
        return
    }

    const anim = silent ? { animation: false } : {}

    try {
        const verdictColors = {
            OK: '#22c55e', WRONG_ANSWER: '#ef4444', TIME_LIMIT_EXCEEDED: '#f97316',
            MEMORY_LIMIT_EXCEEDED: '#eab308', RUNTIME_ERROR: '#a855f7',
            COMPILATION_ERROR: '#64748b', IDLENESS_LIMIT_EXCEEDED: '#f59e0b',
            PRESENTATION_ERROR: '#f43f5e', FAILED: '#dc2626',
            REJECTED: '#b91c1c', SKIPPED: '#94a3b8',
        }
        const verdictLabels = Object.keys(verdicts).sort((a, b) => verdicts[b] - verdicts[a])

        currentChart = new Chart(document.getElementById('verdict-chart'), {
            type: 'doughnut',
            data: { labels: verdictLabels, datasets: [{
                    data: verdictLabels.map((l) => verdicts[l]),
                    backgroundColor: verdictLabels.map((l) => verdictColors[l] || '#64748b'),
                    borderWidth: 0 }]},
            options: { ...anim, plugins: { legend: { labels: { color: '#cbd5e1', font: { size: 11 } } } } },
        })
        console.log('[CF] verdict chart OK')
    } catch (e) { console.error('[CF] verdict chart failed:', e) }

    try {
        // Стековая гистограмма: для каждого языка — сколько OK/WA/TLE/...
        currentLanguageChart = new Chart(document.getElementById('language-chart'), {
            type: 'bar',
            data: {
                labels: topLangs.map(l => l.name),
                datasets: [
                    { label: 'OK',  data: topLangs.map(l => l.ok),   backgroundColor: '#22c55e' },
                    { label: 'WA',  data: topLangs.map(l => l.wa),   backgroundColor: '#ef4444' },
                    { label: 'TLE', data: topLangs.map(l => l.tle),  backgroundColor: '#f97316' },
                    { label: 'RE',  data: topLangs.map(l => l.re),   backgroundColor: '#a855f7' },
                    { label: 'CE',  data: topLangs.map(l => l.ce),   backgroundColor: '#64748b' },
                    { label: 'Др',  data: topLangs.map(l => l.other), backgroundColor: '#94a3b8' },
                ],
            },
            options: {
                indexAxis: 'y',
                ...anim,
                plugins: {
                    legend: { position: 'bottom', labels: { color: '#cbd5e1', font: { size: 11 } } },
                    tooltip: {
                        backgroundColor: '#1e293b', borderColor: '#334155', borderWidth: 1,
                        titleColor: '#e2e8f0', bodyColor: '#e2e8f0',
                    },
                },
                scales: {
                    x: { stacked: true, ticks: { color: '#94a3b8', precision: 0 }, grid: { color: '#334155' } },
                    y: { stacked: true, ticks: { color: '#cbd5e1', font: { size: 11 } }, grid: { display: false } },
                },
            },
        })
        console.log('[CF] language chart OK')
    } catch (e) { console.error('[CF] language chart failed:', e) }

    if (silent) window.scrollTo(0, scrollY)
}

function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, (c) => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
    }[c]))
}