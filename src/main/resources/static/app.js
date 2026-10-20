const app = document.getElementById('app')

// ---------- Роутинг ----------

function route() {
    const hash = location.hash || '#/'
    const m = hash.match(/^#\/contest\/(\d+)$/)
    if (m) renderContest(m[1])
    else renderHome()
}

window.addEventListener('hashchange', route)
window.addEventListener('DOMContentLoaded', route)

// ---------- Главная ----------

function renderHome() {
    app.innerHTML = `
    <div class="home">
      <h1>CF Analysis</h1>
      <p class="muted">Аналитика контестов Codeforces: вердикты, задачи, solve rate.</p>
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

// ---------- Страница контеста ----------

async function renderContest(id) {
    app.innerHTML = `<div class="loading">Загрузка контеста ${id}...</div>`
    try {
        const res = await fetch(`/api/contests/${id}`)
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        const submissions = await res.json()
        renderContestData(id, submissions)
    } catch (e) {
        app.innerHTML = `
      <div class="contest">
        <a href="#/" class="back">← Назад</a>
        <div class="error">Ошибка: ${e.message}</div>
      </div>
    `
    }
}

function renderContestData(id, submissions) {
    // ---- агрегация ----
    const verdicts = {}
    const problems = {}
    const participants = new Set()

    for (const s of submissions) {
        const v = s.verdict || 'UNKNOWN'
        verdicts[v] = (verdicts[v] || 0) + 1

        const idx = s.problem.index
        if (!problems[idx]) {
            problems[idx] = {
                name: s.problem.name,
                rating: s.problem.rating,
                attempts: 0,
                solved: 0,
            }
        }
        problems[idx].attempts++
        if (s.verdict === 'OK') problems[idx].solved++

        for (const m of s.author?.members ?? []) participants.add(m.handle)
    }

    const accepted = verdicts['OK'] || 0
    const acceptRate = submissions.length ? (accepted / submissions.length * 100).toFixed(1) : '0.0'

    app.innerHTML = `
    <div class="contest">
      <a href="#/" class="back">← Назад</a>
      <h1>Контест ${id}</h1>

      <div class="stats">
        <div class="stat"><div class="stat-label">Посылок</div><div class="stat-value">${submissions.length}</div></div>
        <div class="stat"><div class="stat-label">Участников</div><div class="stat-value">${participants.size}</div></div>
        <div class="stat"><div class="stat-label">Принято</div><div class="stat-value">${accepted}</div></div>
        <div class="stat"><div class="stat-label">Accept rate</div><div class="stat-value">${acceptRate}%</div></div>
      </div>

      <h2>Распределение вердиктов</h2>
      <div class="chart-wrap">
        <canvas id="verdict-chart" height="140"></canvas>
      </div>

      <h2>Задачи</h2>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>#</th><th>Название</th><th>Rating</th>
              <th>Попыток</th><th>Решено</th><th>Solve rate</th>
            </tr>
          </thead>
          <tbody>
            ${Object.entries(problems)
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([idx, p]) => `
                <tr>
                  <td class="mono">${idx}</td>
                  <td>${escapeHtml(p.name)}</td>
                  <td class="muted">${p.rating ?? '—'}</td>
                  <td>${p.attempts}</td>
                  <td>${p.solved}</td>
                  <td>${(p.solved / p.attempts * 100).toFixed(1)}%</td>
                </tr>
              `).join('')}
          </tbody>
        </table>
      </div>
    </div>
  `

    // ---- график ----
    const colors = {
        OK: '#22c55e',
        WRONG_ANSWER: '#ef4444',
        TIME_LIMIT_EXCEEDED: '#f97316',
        MEMORY_LIMIT_EXCEEDED: '#eab308',
        RUNTIME_ERROR: '#a855f7',
        COMPILATION_ERROR: '#64748b',
        IDLENESS_LIMIT_EXCEEDED: '#f59e0b',
        PRESENTATION_ERROR: '#f43f5e',
        FAILED: '#dc2626',
        REJECTED: '#b91c1c',
        SKIPPED: '#94a3b8',
    }
    const labels = Object.keys(verdicts).sort((a, b) => verdicts[b] - verdicts[a])
    new Chart(document.getElementById('verdict-chart'), {
        type: 'doughnut',
        data: {
            labels,
            datasets: [{
                data: labels.map((l) => verdicts[l]),
                backgroundColor: labels.map((l) => colors[l] || '#64748b'),
                borderWidth: 0,
            }],
        },
        options: {
            plugins: {
                legend: { labels: { color: '#cbd5e1', font: { size: 12 } } },
            },
        },
    })
}

function escapeHtml(s) {
    return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]))
}