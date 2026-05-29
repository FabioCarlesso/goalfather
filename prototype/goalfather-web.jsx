// GoalFather (GF) — protótipo web jogável
// Manager de futebol estilo Elifoot. Especificação executável das regras de jogo.
// Ver ../docs/ARQUITETURA.md e ../CLAUDE.md
import { useState, useEffect, useRef, useCallback } from "react";

// ─── GAME DATA ────────────────────────────────────────────────────────────────

const FORMATIONS = {
  "4-4-2":  { name: "4-4-2",  slots: ["GK","CB","CB","CB","CB","MF","MF","MF","MF","FW","FW"] },
  "4-3-3":  { name: "4-3-3",  slots: ["GK","CB","CB","CB","CB","MF","MF","MF","FW","FW","FW"] },
  "3-5-2":  { name: "3-5-2",  slots: ["GK","CB","CB","CB","MF","MF","MF","MF","MF","FW","FW"] },
  "5-3-2":  { name: "5-3-2",  slots: ["GK","CB","CB","CB","CB","CB","MF","MF","MF","FW","FW"] },
};

const POS_LABELS = { GK: "GL", CB: "ZG", MF: "MC", FW: "AT" };

function makePlayer(id, name, pos, ovr, salary, age) {
  const base = () => Math.round(ovr + (Math.random() * 14 - 7));
  return {
    id, name, pos, age, salary,
    ovr,
    pace: base(), shot: base(), pass: base(), def: base(), stamina: 100,
    goals: 0, injured: false, star: ovr >= 82,
  };
}

const INITIAL_SQUAD = [
  makePlayer(1,  "Marcos Figueiredo",  "GK", 78, 25000, 32),
  makePlayer(2,  "Rodrigo Alves",      "CB", 75, 18000, 27),
  makePlayer(3,  "Túlio Mendes",       "CB", 72, 15000, 24),
  makePlayer(4,  "Carlos Neto",        "CB", 76, 19000, 29),
  makePlayer(5,  "Edson Vieira",       "CB", 70, 14000, 23),
  makePlayer(6,  "Felipe Costa",       "MF", 80, 28000, 26),
  makePlayer(7,  "André Lima",         "MF", 77, 22000, 28),
  makePlayer(8,  "Bruno Pires",        "MF", 74, 17000, 25),
  makePlayer(9,  "Lucas Moura",        "MF", 73, 16000, 22),
  makePlayer(10, "★ Renato Silva",     "FW", 88, 55000, 24),
  makePlayer(11, "Caio Fernandes",     "FW", 82, 38000, 26),
  makePlayer(12, "Thiago Bastos",      "FW", 75, 20000, 23),
  makePlayer(13, "Diego Rocha",        "CB", 68, 12000, 21),
  makePlayer(14, "Paulo Souza",        "MF", 71, 14000, 23),
  makePlayer(15, "★ Gabriel Cunha",   "MF", 85, 45000, 27),
];

function makeOpponent(name, ovr) {
  return {
    name, ovr,
    squad: [
      makePlayer(100, "GK Opp",  "GK", ovr - 5 + Math.round(Math.random()*10), 0, 25),
      makePlayer(101, "CB1 Opp", "CB", ovr - 3 + Math.round(Math.random()*6),  0, 25),
      makePlayer(102, "CB2 Opp", "CB", ovr - 3 + Math.round(Math.random()*6),  0, 25),
      makePlayer(103, "MF1 Opp", "MF", ovr + Math.round(Math.random()*6),      0, 25),
      makePlayer(104, "MF2 Opp", "MF", ovr + Math.round(Math.random()*6),      0, 25),
      makePlayer(105, "FW1 Opp", "FW", ovr + 3 + Math.round(Math.random()*8),  0, 25),
      makePlayer(106, "FW2 Opp", "FW", ovr + 2 + Math.round(Math.random()*8),  0, 25),
      makePlayer(107, "CB3 Opp", "CB", ovr - 4 + Math.round(Math.random()*6),  0, 25),
      makePlayer(108, "MF3 Opp", "MF", ovr + Math.round(Math.random()*6),      0, 25),
      makePlayer(109, "CB4 Opp", "CB", ovr - 5 + Math.round(Math.random()*6),  0, 25),
      makePlayer(110, "MF4 Opp", "MF", ovr - 2 + Math.round(Math.random()*6),  0, 25),
    ]
  };
}

const LEAGUE_OPPONENTS = [
  makeOpponent("Atletico Norte",   72),
  makeOpponent("Esporte Clube Sul",74),
  makeOpponent("Grêmio Central",   76),
  makeOpponent("FC Brasília",      78),
  makeOpponent("Santos FC",        80),
  makeOpponent("Fluminense Est.",  75),
  makeOpponent("CR Vascão",        73),
  makeOpponent("Botafogo Stars",   77),
  makeOpponent("Corinthios FC",    82),
  makeOpponent("Palmeiras Rei",    84),
  makeOpponent("São Paulo Tri",    85),
  makeOpponent("Flamengo Top",     87),
  makeOpponent("Cruzeiro Astral",  79),
  makeOpponent("Internacional FC", 81),
  makeOpponent("Athletico PR",     76),
  makeOpponent("Fortaleza EC",     74),
  makeOpponent("Ceará SC",         71),
  makeOpponent("Bahia FC",         72),
  makeOpponent("América MG",       70),
];

const MARKET_PLAYERS = [
  makePlayer(200, "★ Adriano Reyes",  "FW", 90, 75000, 22),
  makePlayer(201, "Leandro Faria",    "MF", 79, 30000, 25),
  makePlayer(202, "Matheus Brum",     "CB", 77, 22000, 24),
  makePlayer(203, "★ Fábio Gomes",   "GK", 86, 52000, 28),
  makePlayer(204, "Sandro Leal",      "FW", 74, 18000, 21),
  makePlayer(205, "Geraldo Pinto",    "MF", 81, 35000, 26),
  makePlayer(206, "Nilton Santos",    "CB", 73, 16000, 23),
  makePlayer(207, "★ Carlos Novo",   "FW", 84, 60000, 25),
  makePlayer(208, "Emerson Sá",       "MF", 76, 20000, 22),
  makePlayer(209, "Hélio Vaz",        "CB", 69, 13000, 20),
].map(p => ({ ...p, price: p.salary * 12 + Math.round(Math.random() * 50000) }));

// ─── SIMULATION ENGINE ────────────────────────────────────────────────────────

function calcTeamStrength(lineup) {
  if (!lineup.length) return 60;
  return lineup.reduce((s, p) => s + p.ovr, 0) / lineup.length;
}

function simulateMatch(homeLineup, awayLineup) {
  const hStr = calcTeamStrength(homeLineup);
  const aStr = calcTeamStrength(awayLineup);
  const events = [];
  let hGoals = 0, aGoals = 0;

  const minutes = Array.from({ length: 90 }, (_, i) => i + 1);
  const importantMins = minutes.filter(() => Math.random() < 0.12);

  importantMins.forEach(min => {
    const hAttack = (hStr / (hStr + aStr)) + (Math.random() * 0.3 - 0.15);
    const roll = Math.random();
    if (roll < hAttack * 0.4) {
      hGoals++;
      const scorer = homeLineup.filter(p => p.pos === "FW" || p.pos === "MF")[
        Math.floor(Math.random() * homeLineup.filter(p => p.pos === "FW" || p.pos === "MF").length)
      ];
      events.push({ min, type: "goal", team: "home", player: scorer?.name || "Jogador", text: `⚽ GOL! ${scorer?.name || "Jogador"} marca para o seu time!` });
    } else if (roll > (1 - (1 - hAttack) * 0.4)) {
      aGoals++;
      events.push({ min, type: "goal", team: "away", player: "Adversário", text: `⚽ GOL do adversário!` });
    } else if (roll > 0.7) {
      const isYellow = Math.random() > 0.8;
      if (isYellow) events.push({ min, type: "card", text: `🟨 Cartão amarelo no minuto ${min}` });
      else events.push({ min, type: "miss", text: `💨 Chute para fora no minuto ${min}` });
    } else {
      events.push({ min, type: "chance", text: `🧤 Defesa difícil no minuto ${min}!` });
    }
  });

  events.sort((a, b) => a.min - b.min);
  return { hGoals, aGoals, events };
}

// ─── STYLES ──────────────────────────────────────────────────────────────────

const css = `
  @import url('https://fonts.googleapis.com/css2?family=Share+Tech+Mono&family=Orbitron:wght@400;700;900&display=swap');

  * { box-sizing: border-box; margin: 0; padding: 0; }

  :root {
    --green: #00ff88;
    --green-dim: #00cc66;
    --green-dark: #003322;
    --green-glow: 0 0 8px #00ff8866;
    --yellow: #ffdd00;
    --red: #ff4444;
    --bg: #050f0a;
    --bg2: #0a1a10;
    --bg3: #0f2015;
    --border: #00ff4422;
    --text: #b8ffda;
    --text-dim: #558866;
  }

  body { background: var(--bg); color: var(--text); font-family: 'Share Tech Mono', monospace; }

  .app {
    min-height: 100vh;
    background:
      radial-gradient(ellipse at 20% 20%, #003311 0%, transparent 60%),
      radial-gradient(ellipse at 80% 80%, #001a0a 0%, transparent 60%),
      var(--bg);
  }

  /* SCANLINES */
  .app::before {
    content: '';
    position: fixed; top: 0; left: 0; right: 0; bottom: 0;
    background: repeating-linear-gradient(0deg, transparent, transparent 2px, rgba(0,0,0,0.08) 2px, rgba(0,0,0,0.08) 4px);
    pointer-events: none; z-index: 9999;
  }

  .header {
    border-bottom: 1px solid var(--border);
    background: linear-gradient(90deg, var(--bg2), var(--bg3), var(--bg2));
    padding: 12px 24px;
    display: flex; align-items: center; justify-content: space-between;
  }

  .logo {
    font-family: 'Orbitron', monospace;
    font-size: 22px; font-weight: 900;
    color: var(--green);
    text-shadow: var(--green-glow), 0 0 30px #00ff8833;
    letter-spacing: 3px;
  }

  .logo span { color: var(--yellow); text-shadow: 0 0 10px #ffdd0088; }

  .header-info {
    display: flex; gap: 24px; align-items: center; font-size: 11px; color: var(--text-dim);
  }

  .hval { color: var(--green); font-size: 13px; }
  .hval.money { color: var(--yellow); }
  .hval.bad { color: var(--red); }

  .nav {
    display: flex; gap: 2px;
    background: var(--bg2);
    border-bottom: 1px solid var(--border);
    padding: 0 12px;
  }

  .nav-btn {
    background: none; border: none; cursor: pointer;
    font-family: 'Share Tech Mono', monospace; font-size: 11px;
    color: var(--text-dim);
    padding: 10px 16px;
    letter-spacing: 1px;
    transition: all 0.15s;
    border-bottom: 2px solid transparent;
    position: relative;
    text-transform: uppercase;
  }

  .nav-btn:hover { color: var(--green); }
  .nav-btn.active {
    color: var(--green);
    border-bottom-color: var(--green);
    background: linear-gradient(0deg, #00ff1108, transparent);
    text-shadow: var(--green-glow);
  }

  .page { padding: 20px 24px; max-width: 1100px; margin: 0 auto; }

  .section-title {
    font-family: 'Orbitron', monospace;
    font-size: 13px; letter-spacing: 2px;
    color: var(--green-dim);
    text-transform: uppercase;
    margin-bottom: 14px;
    padding-bottom: 6px;
    border-bottom: 1px solid var(--border);
  }

  .card {
    background: var(--bg2);
    border: 1px solid var(--border);
    border-radius: 4px;
    padding: 16px;
    margin-bottom: 16px;
  }

  .card:hover { border-color: #00ff4433; }

  .grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
  .grid3 { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 16px; }

  /* TABLE */
  table { width: 100%; border-collapse: collapse; font-size: 12px; }
  th {
    text-align: left; padding: 6px 10px;
    color: var(--text-dim); font-weight: normal; letter-spacing: 1px;
    border-bottom: 1px solid var(--border); font-size: 10px; text-transform: uppercase;
  }
  td { padding: 6px 10px; border-bottom: 1px solid #ffffff08; }
  tr:hover td { background: #00ff1106; }

  .badge {
    display: inline-block; padding: 1px 6px; border-radius: 2px;
    font-size: 10px; font-weight: bold;
  }
  .badge-pos { background: #003322; color: var(--green-dim); border: 1px solid #00443322; }
  .badge-ovr {
    background: #001a08; color: var(--green);
    border: 1px solid #00ff4422;
    font-family: 'Orbitron', monospace;
    font-size: 11px; min-width: 30px; text-align: center;
  }
  .badge-ovr.elite { color: var(--yellow); border-color: #ffdd0044; background: #1a1000; }

  .star { color: var(--yellow); }

  /* BUTTONS */
  .btn {
    font-family: 'Share Tech Mono', monospace;
    background: none;
    border: 1px solid var(--green-dim);
    color: var(--green);
    padding: 7px 16px; font-size: 11px;
    cursor: pointer; letter-spacing: 1px;
    transition: all 0.15s; text-transform: uppercase;
    border-radius: 2px;
  }
  .btn:hover { background: #00ff1114; box-shadow: var(--green-glow); }
  .btn:active { transform: scale(0.98); }
  .btn-primary {
    border-color: var(--green);
    background: #00ff1110;
    text-shadow: var(--green-glow);
  }
  .btn-danger { border-color: var(--red); color: var(--red); }
  .btn-danger:hover { background: #ff444414; }
  .btn-yellow { border-color: var(--yellow); color: var(--yellow); }
  .btn-yellow:hover { background: #ffdd0014; }
  .btn:disabled { opacity: 0.3; cursor: default; }

  /* STAT BARS */
  .stat-bar-wrap { display: flex; align-items: center; gap: 8px; font-size: 10px; }
  .stat-bar-bg { flex: 1; height: 4px; background: #002210; border-radius: 2px; }
  .stat-bar-fill { height: 100%; border-radius: 2px; background: var(--green); transition: width 0.3s; }
  .stat-label { width: 32px; color: var(--text-dim); }
  .stat-val { width: 24px; text-align: right; color: var(--green); font-size: 10px; }

  /* MATCH */
  .scoreboard {
    text-align: center;
    padding: 24px;
    background: linear-gradient(135deg, var(--bg2), var(--bg3));
    border: 1px solid var(--border);
    border-radius: 4px;
    margin-bottom: 16px;
  }

  .score-teams { display: flex; align-items: center; justify-content: center; gap: 32px; }
  .score-team { font-size: 12px; color: var(--text-dim); text-align: center; min-width: 140px; }
  .score-team-name { font-size: 14px; color: var(--text); margin-bottom: 4px; }
  .score-val {
    font-family: 'Orbitron', monospace;
    font-size: 52px; font-weight: 900;
    color: var(--green);
    text-shadow: var(--green-glow), 0 0 40px #00ff8844;
    line-height: 1;
  }
  .score-sep { font-family: 'Orbitron', monospace; font-size: 32px; color: var(--text-dim); }

  .match-log {
    height: 220px; overflow-y: auto;
    background: var(--bg);
    border: 1px solid var(--border);
    border-radius: 4px;
    padding: 10px;
    font-size: 11px;
  }
  .match-log::-webkit-scrollbar { width: 4px; }
  .match-log::-webkit-scrollbar-track { background: var(--bg); }
  .match-log::-webkit-scrollbar-thumb { background: var(--green-dark); border-radius: 2px; }

  .log-entry {
    padding: 3px 0;
    border-bottom: 1px solid #ffffff05;
    animation: fadeIn 0.3s ease;
  }
  .log-entry .min { color: var(--text-dim); width: 32px; display: inline-block; }
  .log-entry.goal { color: var(--green); }
  .log-entry.goal-away { color: var(--red); }

  @keyframes fadeIn { from { opacity: 0; transform: translateX(-4px); } to { opacity: 1; transform: none; } }

  /* MARKET */
  .player-card {
    background: var(--bg2);
    border: 1px solid var(--border);
    border-radius: 4px;
    padding: 14px;
    transition: all 0.15s;
  }
  .player-card:hover { border-color: #00ff4433; background: var(--bg3); }
  .player-card-name { font-size: 13px; margin-bottom: 8px; }
  .player-card-attrs { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 10px; }
  .attr { font-size: 10px; padding: 2px 5px; background: var(--bg); border: 1px solid var(--border); color: var(--text-dim); border-radius: 2px; }

  /* LINEUP */
  .pitch {
    background:
      linear-gradient(180deg, #0d2a12 0%, #0a2010 50%, #0d2a12 100%);
    border: 2px solid #1a4a22;
    border-radius: 6px;
    position: relative;
    width: 100%; padding-top: 130%;
    overflow: hidden;
  }

  .pitch-inner { position: absolute; top: 0; left: 0; right: 0; bottom: 0; }

  .pitch-line {
    position: absolute;
    border: 1px solid rgba(255,255,255,0.1);
  }

  .player-token {
    position: absolute;
    transform: translate(-50%, -50%);
    text-align: center;
    cursor: pointer;
    transition: all 0.2s;
  }

  .player-token-circle {
    width: 34px; height: 34px; border-radius: 50%;
    background: var(--bg3);
    border: 2px solid var(--green-dim);
    display: flex; align-items: center; justify-content: center;
    font-size: 10px; color: var(--green);
    margin: 0 auto 3px;
    transition: all 0.2s;
    font-family: 'Orbitron', monospace; font-weight: 700;
  }

  .player-token:hover .player-token-circle {
    border-color: var(--green);
    box-shadow: var(--green-glow);
    background: #003322;
  }

  .player-token-name {
    font-size: 9px; color: var(--text-dim);
    white-space: nowrap; max-width: 60px;
    overflow: hidden; text-overflow: ellipsis;
    background: rgba(0,0,0,0.6);
    padding: 1px 3px; border-radius: 2px;
  }

  .player-token.empty .player-token-circle {
    border-color: #334433; border-style: dashed; color: #334433;
  }

  /* LEAGUE TABLE */
  .my-row td { color: var(--green) !important; background: #00ff1108 !important; }
  .pos-num {
    font-family: 'Orbitron', monospace;
    font-size: 11px; color: var(--text-dim);
    width: 24px; text-align: center;
  }
  .pos-num.top3 { color: var(--yellow); }
  .pos-num.zone { color: var(--red); }

  /* TOAST */
  .toast {
    position: fixed; bottom: 24px; right: 24px;
    background: var(--bg3); border: 1px solid var(--green);
    color: var(--green); padding: 10px 18px; border-radius: 4px;
    font-size: 12px; z-index: 10000;
    box-shadow: var(--green-glow);
    animation: slideIn 0.3s ease;
  }
  .toast.err { border-color: var(--red); color: var(--red); }
  @keyframes slideIn { from { opacity:0; transform: translateY(10px); } to { opacity:1; transform:none; } }

  /* MINI STAT */
  .stat-grid { display: grid; grid-template-columns: repeat(4,1fr); gap: 12px; }
  .stat-tile {
    background: var(--bg2);
    border: 1px solid var(--border);
    border-radius: 4px;
    padding: 14px 12px;
    text-align: center;
  }
  .stat-tile-val {
    font-family: 'Orbitron', monospace;
    font-size: 22px; font-weight: 700;
    color: var(--green); margin-bottom: 4px;
  }
  .stat-tile-val.yellow { color: var(--yellow); }
  .stat-tile-val.red { color: var(--red); }
  .stat-tile-label { font-size: 10px; color: var(--text-dim); text-transform: uppercase; letter-spacing: 1px; }

  .formation-sel {
    display: flex; gap: 8px; margin-bottom: 14px; flex-wrap: wrap;
  }

  .chip {
    background: var(--bg2); border: 1px solid var(--border);
    color: var(--text-dim); padding: 4px 12px; font-size: 11px;
    cursor: pointer; border-radius: 2px; font-family: 'Share Tech Mono', monospace;
    transition: all 0.15s;
  }
  .chip:hover { border-color: var(--green-dim); color: var(--green); }
  .chip.active { border-color: var(--green); color: var(--green); background: #00ff1110; }

  .sub-panel {
    background: var(--bg); border: 1px solid var(--border);
    border-radius: 4px; padding: 12px; max-height: 280px; overflow-y: auto;
  }
  .sub-row {
    display: flex; align-items: center; justify-content: space-between;
    padding: 6px 4px; border-bottom: 1px solid #ffffff06; font-size: 11px;
    cursor: pointer; transition: background 0.1s;
  }
  .sub-row:hover { background: #00ff1108; }

  .phase-indicator {
    text-align: center; padding: 8px;
    font-family: 'Orbitron', monospace; font-size: 11px;
    letter-spacing: 2px; color: var(--text-dim);
    text-transform: uppercase;
  }

  .injury { color: var(--red) !important; }
  .result-w { color: var(--green); }
  .result-d { color: var(--yellow); }
  .result-l { color: var(--red); }

  select {
    background: var(--bg2); border: 1px solid var(--border);
    color: var(--text); font-family: 'Share Tech Mono', monospace;
    padding: 6px 10px; font-size: 11px; border-radius: 2px;
    outline: none; cursor: pointer;
  }
  select:focus { border-color: var(--green-dim); }

  .blinking { animation: blink 1s infinite; }
  @keyframes blink { 0%,100%{ opacity:1; } 50%{ opacity:0.3; } }
`;

// ─── PITCH LAYOUT ─────────────────────────────────────────────────────────────

function getPitchPositions(formation) {
  const layouts = {
    "4-4-2": [
      { x: 50, y: 92 },
      { x: 15, y: 72 }, { x: 38, y: 72 }, { x: 62, y: 72 }, { x: 85, y: 72 },
      { x: 15, y: 48 }, { x: 38, y: 48 }, { x: 62, y: 48 }, { x: 85, y: 48 },
      { x: 33, y: 18 }, { x: 67, y: 18 },
    ],
    "4-3-3": [
      { x: 50, y: 92 },
      { x: 15, y: 72 }, { x: 38, y: 72 }, { x: 62, y: 72 }, { x: 85, y: 72 },
      { x: 25, y: 48 }, { x: 50, y: 48 }, { x: 75, y: 48 },
      { x: 15, y: 18 }, { x: 50, y: 18 }, { x: 85, y: 18 },
    ],
    "3-5-2": [
      { x: 50, y: 92 },
      { x: 25, y: 72 }, { x: 50, y: 72 }, { x: 75, y: 72 },
      { x: 10, y: 50 }, { x: 30, y: 50 }, { x: 50, y: 50 }, { x: 70, y: 50 }, { x: 90, y: 50 },
      { x: 33, y: 18 }, { x: 67, y: 18 },
    ],
    "5-3-2": [
      { x: 50, y: 92 },
      { x: 10, y: 72 }, { x: 28, y: 72 }, { x: 50, y: 72 }, { x: 72, y: 72 }, { x: 90, y: 72 },
      { x: 25, y: 48 }, { x: 50, y: 48 }, { x: 75, y: 48 },
      { x: 33, y: 18 }, { x: 67, y: 18 },
    ],
  };
  return layouts[formation] || layouts["4-4-2"];
}

// ─── COMPONENTS ───────────────────────────────────────────────────────────────

function StatBar({ label, value }) {
  const pct = Math.min(100, value);
  const color = value >= 80 ? "#ffdd00" : value >= 70 ? "#00ff88" : "#558866";
  return (
    <div className="stat-bar-wrap">
      <span className="stat-label">{label}</span>
      <div className="stat-bar-bg">
        <div className="stat-bar-fill" style={{ width: `${pct}%`, background: color }} />
      </div>
      <span className="stat-val">{value}</span>
    </div>
  );
}

function PlayerRow({ player, actions }) {
  const isElite = player.ovr >= 82;
  return (
    <tr>
      <td>
        <span style={{ color: player.star ? "var(--yellow)" : "inherit" }}>
          {player.star ? "★ " : ""}{player.name.replace("★ ", "")}
        </span>
        {player.injured && <span className="injury"> [LESIONADO]</span>}
      </td>
      <td><span className="badge badge-pos">{POS_LABELS[player.pos] || player.pos}</span></td>
      <td><span className={`badge badge-ovr ${isElite ? "elite" : ""}`}>{player.ovr}</span></td>
      <td style={{ color: "var(--text-dim)", fontSize: 11 }}>{player.age}</td>
      <td style={{ color: "var(--yellow)", fontSize: 11 }}>R$ {player.salary.toLocaleString()}</td>
      <td style={{ color: "var(--green-dim)", fontSize: 11 }}>{player.goals}</td>
      {actions}
    </tr>
  );
}

// ─── MAIN APP ─────────────────────────────────────────────────────────────────

export default function GoalFatherWeb() {
  const [tab, setTab] = useState("clube");
  const [squad, setSquad] = useState(INITIAL_SQUAD);
  const [lineup, setLineup] = useState(INITIAL_SQUAD.slice(0, 11).map(p => p.id));
  const [formation, setFormation] = useState("4-4-2");
  const [finances, setFinances] = useState({ cash: 800000, stadiumCap: 15000 });
  const [market, setMarket] = useState(MARKET_PLAYERS);
  const [toast, setToast] = useState(null);
  const [matchState, setMatchState] = useState(null);
  const [matchRunning, setMatchRunning] = useState(false);
  const [season, setSeason] = useState(1);
  const [round, setRound] = useState(0);
  const [selectedSlot, setSelectedSlot] = useState(null);
  const logRef = useRef(null);

  const [table, setTable] = useState(() =>
    ["Meu Time", ...LEAGUE_OPPONENTS.map(o => o.name)].map((name, i) => ({
      name, pts: 0, w: 0, d: 0, l: 0, gf: 0, ga: 0, gd: 0, played: 0, isMe: i === 0
    }))
  );

  const showToast = useCallback((msg, err = false) => {
    setToast({ msg, err });
    setTimeout(() => setToast(null), 3000);
  }, []);

  const lineupPlayers = squad.filter(p => lineup.includes(p.id));
  const benchPlayers = squad.filter(p => !lineup.includes(p.id));
  const weeklyWage = squad.reduce((s, p) => s + p.salary, 0);
  const teamOvr = Math.round(calcTeamStrength(lineupPlayers));

  // ── Scroll log
  useEffect(() => {
    if (logRef.current) logRef.current.scrollTop = logRef.current.scrollHeight;
  }, [matchState?.log]);

  // ── Auto-assign lineup if empty
  useEffect(() => {
    if (lineup.length === 0 && squad.length >= 11) {
      setLineup(squad.slice(0, 11).map(p => p.id));
    }
  }, []);

  function toggleLineup(pid) {
    if (lineup.includes(pid)) {
      setLineup(l => l.filter(id => id !== pid));
    } else if (lineup.length < 11) {
      setLineup(l => [...l, pid]);
      setSelectedSlot(null);
    } else {
      showToast("Escalação completa! Remova um jogador primeiro.", true);
    }
  }

  function swapInSlot(slotIdx, pid) {
    const slots = FORMATIONS[formation].slots;
    const newLineup = [...lineup];
    if (newLineup[slotIdx] !== undefined) {
      // swap
      const oldId = newLineup[slotIdx];
      const pidIdx = newLineup.indexOf(pid);
      if (pidIdx !== -1) { newLineup[pidIdx] = oldId; }
      newLineup[slotIdx] = pid;
    } else {
      newLineup[slotIdx] = pid;
    }
    setLineup(newLineup.slice(0, 11));
    setSelectedSlot(null);
  }

  async function playMatch() {
    if (round >= LEAGUE_OPPONENTS.length) {
      showToast("Temporada encerrada! Inicie uma nova.", true);
      return;
    }
    if (lineup.length < 11) {
      showToast("Escale 11 jogadores antes de jogar!", true);
      return;
    }

    const opp = LEAGUE_OPPONENTS[round];
    setMatchRunning(true);
    setMatchState({ log: [], hGoals: 0, aGoals: 0, phase: "AQUECENDO...", opp: opp.name });

    const result = simulateMatch(lineupPlayers, opp.squad.slice(0, 11));
    const allEvents = [
      { min: 1, type: "kick", text: "⚡ Bola rolando! Início de jogo." },
      ...result.events,
      { min: 45, type: "half", text: "🔔 Intervalo!" },
      { min: 90, type: "end", text: "🏁 Fim de jogo!" },
    ].sort((a, b) => a.min - b.min);

    let runningH = 0, runningA = 0;
    for (const ev of allEvents) {
      await new Promise(r => setTimeout(r, 280 + Math.random() * 200));
      if (ev.type === "goal" && ev.team === "home") runningH++;
      if (ev.type === "goal" && ev.team === "away") runningA++;
      setMatchState(s => ({
        ...s,
        hGoals: runningH,
        aGoals: runningA,
        phase: ev.min >= 90 ? "FIM DE JOGO" : ev.min >= 45 ? "2º TEMPO" : "1º TEMPO",
        log: [...(s?.log || []), { ...ev, running_h: runningH, running_a: runningA }],
      }));
    }

    // Update goals for scorers
    const newSquad = squad.map(p => {
      const matchGoals = result.events.filter(e => e.team === "home" && e.player === p.name).length;
      const staminaLoss = lineup.includes(p.id) ? Math.round(10 + Math.random() * 15) : 0;
      return { ...p, goals: p.goals + matchGoals, stamina: Math.max(40, p.stamina - staminaLoss) };
    });
    setSquad(newSquad);

    // Update table
    const h = result.hGoals, a = result.aGoals;
    const meW = h > a, meD = h === a, meL = h < a;
    const oppW = a > h, oppD = a === h, oppL = a < h;

    setTable(prev => prev.map(row => {
      if (row.isMe) return {
        ...row,
        played: row.played + 1,
        pts: row.pts + (meW ? 3 : meD ? 1 : 0),
        w: row.w + (meW ? 1 : 0), d: row.d + (meD ? 1 : 0), l: row.l + (meL ? 1 : 0),
        gf: row.gf + h, ga: row.ga + a, gd: row.gd + (h - a),
      };
      if (row.name === opp.name) return {
        ...row,
        played: row.played + 1,
        pts: row.pts + (oppW ? 3 : oppD ? 1 : 0),
        w: row.w + (oppW ? 1 : 0), d: row.d + (oppD ? 1 : 0), l: row.l + (oppL ? 1 : 0),
        gf: row.gf + a, ga: row.ga + h, gd: row.gd + (a - h),
      };
      // simulate other matches
      const r1 = Math.random(), r2 = Math.random();
      const g1 = Math.round(r1 * 3), g2 = Math.round(r2 * 3);
      return {
        ...row,
        played: row.played + 1,
        pts: row.pts + (g1 > g2 ? 3 : g1 === g2 ? 1 : 0),
        w: row.w + (g1 > g2 ? 1 : 0), d: row.d + (g1 === g2 ? 1 : 0), l: row.l + (g1 < g2 ? 1 : 0),
        gf: row.gf + g1, ga: row.ga + g2, gd: row.gd + (g1 - g2),
      };
    }));

    // Finances: ticket revenue
    const attendance = Math.round(finances.stadiumCap * (0.5 + Math.random() * 0.5));
    const revenue = attendance * 30;
    setFinances(f => ({ ...f, cash: f.cash + revenue - weeklyWage }));

    setRound(r => r + 1);
    setMatchRunning(false);

    const resultStr = meW ? "VITÓRIA! ⚽" : meD ? "EMPATE" : "DERROTA 😔";
    showToast(`${resultStr} ${h} × ${a} contra ${opp.name}`);
  }

  function buyPlayer(player) {
    if (finances.cash < player.price) {
      showToast("Saldo insuficiente!", true);
      return;
    }
    if (squad.length >= 22) {
      showToast("Elenco cheio! Venda um jogador primeiro.", true);
      return;
    }
    setSquad(s => [...s, { ...player, price: undefined }]);
    setFinances(f => ({ ...f, cash: f.cash - player.price }));
    setMarket(m => m.filter(p => p.id !== player.id));
    showToast(`${player.name} contratado!`);
  }

  function sellPlayer(player) {
    if (lineup.includes(player.id)) {
      setLineup(l => l.filter(id => id !== player.id));
    }
    const sellPrice = player.salary * 8 + Math.round(Math.random() * 30000);
    setSquad(s => s.filter(p => p.id !== player.id));
    setFinances(f => ({ ...f, cash: f.cash + sellPrice }));
    showToast(`${player.name} vendido por R$ ${sellPrice.toLocaleString()}`);
  }

  function expandStadium() {
    const cost = 150000;
    if (finances.cash < cost) { showToast("Saldo insuficiente!", true); return; }
    setFinances(f => ({ ...f, cash: f.cash - cost, stadiumCap: f.stadiumCap + 3000 }));
    showToast("Estádio ampliado! +3.000 lugares");
  }

  function recoverPlayers() {
    const cost = 30000;
    if (finances.cash < cost) { showToast("Saldo insuficiente!", true); return; }
    setSquad(s => s.map(p => ({ ...p, stamina: Math.min(100, p.stamina + 30) })));
    setFinances(f => ({ ...f, cash: f.cash - cost }));
    showToast("Treino de recuperação realizado!");
  }

  const sortedTable = [...table].sort((a, b) => b.pts - a.pts || b.gd - a.gd || b.gf - a.gf);
  const myPos = sortedTable.findIndex(r => r.isMe) + 1;

  // ─── PITCH TAB ───────────────────────────────────────────────────────────────
  function PitchTab() {
    const positions = getPitchPositions(formation);
    const slots = FORMATIONS[formation].slots;

    return (
      <div className="page">
        <div className="section-title">Escalação & Formação</div>
        <div className="grid2" style={{ gap: 20 }}>
          <div>
            <div className="formation-sel">
              {Object.keys(FORMATIONS).map(f => (
                <button key={f} className={`chip ${formation === f ? "active" : ""}`}
                  onClick={() => setFormation(f)}>{f}</button>
              ))}
            </div>
            <div className="pitch">
              <div className="pitch-inner">
                {/* field markings */}
                <div className="pitch-line" style={{ top: "50%", left: 0, right: 0, height: 1 }} />
                <div className="pitch-line" style={{ top: "50%", left: "30%", width: "40%", height: 1, borderRadius: "50%" }} />
                <div className="pitch-line" style={{ top: "10%", left: "20%", width: "60%", height: "20%" }} />
                <div className="pitch-line" style={{ top: "70%", left: "20%", width: "60%", height: "20%" }} />

                {positions.map((pos, i) => {
                  const pid = lineup[i];
                  const player = pid ? squad.find(p => p.id === pid) : null;
                  const isSelected = selectedSlot === i;

                  return (
                    <div
                      key={i}
                      className={`player-token ${!player ? "empty" : ""}`}
                      style={{ left: `${pos.x}%`, top: `${pos.y}%` }}
                      onClick={() => {
                        if (player) {
                          setSelectedSlot(isSelected ? null : i);
                        } else {
                          setSelectedSlot(i);
                        }
                      }}
                    >
                      <div className="player-token-circle"
                        style={{
                          borderColor: isSelected ? "var(--yellow)" : player?.star ? "var(--yellow)" : undefined,
                          boxShadow: isSelected ? "0 0 10px var(--yellow)" : undefined,
                        }}>
                        {player ? player.ovr : slots[i]}
                      </div>
                      <div className="player-token-name">
                        {player ? player.name.replace("★ ", "").split(" ")[0] : "─"}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          </div>

          <div>
            <div className="section-title" style={{ marginBottom: 10 }}>
              {selectedSlot !== null
                ? `Selecionar para posição ${selectedSlot + 1} (${slots[selectedSlot]})`
                : "Reservas — clique em uma posição no campo"}
            </div>
            <div className="sub-panel">
              {squad.map(p => {
                const inLineup = lineup.includes(p.id);
                return (
                  <div key={p.id} className="sub-row"
                    onClick={() => {
                      if (selectedSlot !== null) {
                        swapInSlot(selectedSlot, p.id);
                      } else {
                        toggleLineup(p.id);
                      }
                    }}
                    style={{ opacity: p.injured ? 0.5 : 1 }}
                  >
                    <span style={{ color: p.star ? "var(--yellow)" : inLineup ? "var(--green)" : "var(--text)" }}>
                      {p.star ? "★ " : ""}{p.name.replace("★ ", "")}
                    </span>
                    <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                      <span className="badge badge-pos">{POS_LABELS[p.pos]}</span>
                      <span className={`badge badge-ovr ${p.ovr >= 82 ? "elite" : ""}`}>{p.ovr}</span>
                      <span style={{ fontSize: 10, color: p.stamina < 50 ? "var(--red)" : "var(--text-dim)" }}>
                        {p.stamina}%
                      </span>
                      {inLineup && <span style={{ color: "var(--green)", fontSize: 10 }}>TITULAR</span>}
                    </div>
                  </div>
                );
              })}
            </div>
            <div style={{ marginTop: 12, fontSize: 11, color: "var(--text-dim)" }}>
              Titulares: <span style={{ color: "var(--green)" }}>{lineup.length}/11</span>
              &nbsp;&nbsp;OVR médio: <span style={{ color: "var(--yellow)" }}>{teamOvr}</span>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // ─── MATCH TAB ───────────────────────────────────────────────────────────────
  function MatchTab() {
    const nextOpp = LEAGUE_OPPONENTS[round];
    return (
      <div className="page">
        <div className="section-title">Partida — Rodada {round + 1}/{LEAGUE_OPPONENTS.length}</div>

        {matchState && (
          <>
            <div className="scoreboard">
              <div className="phase-indicator"
                style={{ color: matchState.phase === "FIM DE JOGO" ? "var(--yellow)" : "var(--green)" }}
              >
                {matchState.running ? <span className="blinking">{matchState.phase}</span> : matchState.phase}
              </div>
              <div className="score-teams" style={{ marginTop: 12 }}>
                <div className="score-team">
                  <div className="score-team-name">MEU TIME</div>
                  <div style={{ fontSize: 10, color: "var(--text-dim)" }}>OVR {teamOvr}</div>
                </div>
                <div className="score-val">{matchState.hGoals}</div>
                <div className="score-sep">×</div>
                <div className="score-val">{matchState.aGoals}</div>
                <div className="score-team">
                  <div className="score-team-name">{matchState.opp}</div>
                  <div style={{ fontSize: 10, color: "var(--text-dim)" }}>OVR {LEAGUE_OPPONENTS.find(o => o.name === matchState.opp)?.ovr || "?"}</div>
                </div>
              </div>
            </div>

            <div className="match-log" ref={logRef}>
              {matchState.log.map((ev, i) => (
                <div key={i} className={`log-entry ${ev.type === "goal" && ev.team === "home" ? "goal" : ev.type === "goal" && ev.team === "away" ? "goal-away" : ""}`}>
                  <span className="min">{ev.min}'</span> {ev.text}
                </div>
              ))}
            </div>
          </>
        )}

        {!matchState && nextOpp && (
          <div className="card" style={{ textAlign: "center", padding: 32 }}>
            <div style={{ fontSize: 12, color: "var(--text-dim)", marginBottom: 8 }}>PRÓXIMA PARTIDA</div>
            <div style={{ fontFamily: "Orbitron, monospace", fontSize: 20, color: "var(--green)", marginBottom: 20 }}>
              Meu Time <span style={{ color: "var(--text-dim)" }}>vs</span> {nextOpp.name}
            </div>
            <div style={{ fontSize: 11, color: "var(--text-dim)", marginBottom: 24 }}>
              Seu OVR: <span style={{ color: "var(--yellow)" }}>{teamOvr}</span>
              &nbsp;&nbsp;|&nbsp;&nbsp;
              Adversário OVR: <span style={{ color: "var(--yellow)" }}>{nextOpp.ovr}</span>
            </div>
          </div>
        )}

        {round >= LEAGUE_OPPONENTS.length ? (
          <div style={{ textAlign: "center" }}>
            <div style={{ fontFamily: "Orbitron, monospace", fontSize: 18, color: "var(--yellow)", margin: "24px 0" }}>
              TEMPORADA {season} ENCERRADA!
            </div>
            <div style={{ color: "var(--text-dim)", fontSize: 12, marginBottom: 20 }}>
              Posição final: #{myPos}
            </div>
            <button className="btn btn-primary" onClick={() => {
              setSeason(s => s + 1);
              setRound(0);
              setMatchState(null);
              setTable(prev => prev.map(r => ({ ...r, pts: 0, w: 0, d: 0, l: 0, gf: 0, ga: 0, gd: 0, played: 0 })));
              setSquad(s => s.map(p => ({ ...p, stamina: 100 })));
              showToast(`Temporada ${season + 1} iniciada!`);
            }}>▶ NOVA TEMPORADA</button>
          </div>
        ) : (
          <div style={{ display: "flex", gap: 12, marginTop: 16 }}>
            <button
              className="btn btn-primary"
              onClick={playMatch}
              disabled={matchRunning || lineup.length < 11}
            >
              {matchRunning ? <span className="blinking">SIMULANDO...</span> : "▶ JOGAR PARTIDA"}
            </button>
            {matchState && !matchRunning && nextOpp && (
              <button className="btn" onClick={() => setMatchState(null)}>
                ↺ LIMPAR
              </button>
            )}
          </div>
        )}
      </div>
    );
  }

  // ─── TABS ─────────────────────────────────────────────────────────────────────

  function ClubeTab() {
    const results = sortedTable.find(r => r.isMe);
    return (
      <div className="page">
        <div className="section-title">Painel do Clube — Temporada {season}</div>
        <div className="stat-grid" style={{ marginBottom: 20 }}>
          <div className="stat-tile">
            <div className="stat-tile-val yellow">R$ {(finances.cash / 1000).toFixed(0)}k</div>
            <div className="stat-tile-label">Caixa</div>
          </div>
          <div className="stat-tile">
            <div className={`stat-tile-val ${myPos <= 4 ? "" : myPos >= 16 ? "red" : "yellow"}`}>#{myPos}</div>
            <div className="stat-tile-label">Posição</div>
          </div>
          <div className="stat-tile">
            <div className="stat-tile-val">{results?.pts || 0}</div>
            <div className="stat-tile-label">Pontos</div>
          </div>
          <div className="stat-tile">
            <div className="stat-tile-val">{teamOvr}</div>
            <div className="stat-tile-label">OVR Médio</div>
          </div>
        </div>

        <div className="grid2">
          <div className="card">
            <div className="section-title">Resultados</div>
            {results && (
              <table>
                <tbody>
                  {[["Jogos", results.played],["Vitórias", results.w, "var(--green)"],
                    ["Empates", results.d, "var(--yellow)"],["Derrotas", results.l, "var(--red)"],
                    ["Gols Feitos", results.gf],["Gols Sofridos", results.ga],
                    ["Saldo", results.gd, results.gd >= 0 ? "var(--green)" : "var(--red)"]
                  ].map(([l, v, c]) => (
                    <tr key={l}>
                      <td style={{ color: "var(--text-dim)" }}>{l}</td>
                      <td style={{ color: c || "var(--text)", fontFamily: "Orbitron, monospace" }}>{v}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          <div className="card">
            <div className="section-title">Gestão do Clube</div>
            <table style={{ marginBottom: 14 }}>
              <tbody>
                <tr><td style={{ color: "var(--text-dim)" }}>Estádio</td>
                  <td style={{ color: "var(--yellow)" }}>{finances.stadiumCap.toLocaleString()} lugares</td></tr>
                <tr><td style={{ color: "var(--text-dim)" }}>Folha semanal</td>
                  <td style={{ color: "var(--red)" }}>R$ {weeklyWage.toLocaleString()}</td></tr>
                <tr><td style={{ color: "var(--text-dim)" }}>Jogadores</td>
                  <td>{squad.length} / 22</td></tr>
                <tr><td style={{ color: "var(--text-dim)" }}>Rodada</td>
                  <td>{round} / {LEAGUE_OPPONENTS.length}</td></tr>
              </tbody>
            </table>
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
              <button className="btn btn-yellow" onClick={expandStadium}>
                🏟 Ampliar Estádio (R$ 150k)
              </button>
              <button className="btn" onClick={recoverPlayers}>
                💪 Recuperar Elenco (R$ 30k)
              </button>
            </div>
          </div>
        </div>

        <div className="card">
          <div className="section-title">Artilheiros</div>
          <table>
            <thead><tr>
              <th>Jogador</th><th>Pos</th><th>OVR</th><th>Gols</th>
            </tr></thead>
            <tbody>
              {[...squad].sort((a, b) => b.goals - a.goals).slice(0, 5).map(p => (
                <tr key={p.id}>
                  <td style={{ color: p.star ? "var(--yellow)" : "inherit" }}>{p.star ? "★ " : ""}{p.name.replace("★ ", "")}</td>
                  <td><span className="badge badge-pos">{POS_LABELS[p.pos]}</span></td>
                  <td><span className={`badge badge-ovr ${p.ovr >= 82 ? "elite" : ""}`}>{p.ovr}</span></td>
                  <td style={{ color: "var(--green)", fontFamily: "Orbitron, monospace" }}>{p.goals}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    );
  }

  function ElencoTab() {
    return (
      <div className="page">
        <div className="section-title">Elenco — {squad.length} jogadores</div>
        <div className="card" style={{ padding: 0 }}>
          <table>
            <thead><tr>
              <th>Nome</th><th>Pos</th><th>OVR</th><th>Idade</th><th>Salário</th><th>Gols</th><th>Ação</th>
            </tr></thead>
            <tbody>
              {[...squad].sort((a, b) => b.ovr - a.ovr).map(p => (
                <PlayerRow key={p.id} player={p} actions={
                  <td>
                    <button className="btn btn-danger" style={{ fontSize: 9, padding: "2px 8px" }}
                      onClick={() => sellPlayer(p)}>VENDER</button>
                  </td>
                } />
              ))}
            </tbody>
          </table>
        </div>
      </div>
    );
  }

  function MercadoTab() {
    return (
      <div className="page">
        <div className="section-title">Mercado de Transferências</div>
        <div style={{ marginBottom: 12, fontSize: 11, color: "var(--text-dim)" }}>
          Saldo disponível: <span style={{ color: "var(--yellow)", fontSize: 13 }}>
            R$ {finances.cash.toLocaleString()}
          </span>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(240px, 1fr))", gap: 12 }}>
          {market.map(p => (
            <div key={p.id} className="player-card">
              <div className="player-card-name">
                <span style={{ color: p.star ? "var(--yellow)" : "inherit" }}>
                  {p.star ? "★ " : ""}{p.name.replace("★ ", "")}
                </span>
              </div>
              <div className="player-card-attrs">
                <span className="attr">{POS_LABELS[p.pos]}</span>
                <span className="attr">Idade {p.age}</span>
                <span className={`badge badge-ovr ${p.ovr >= 82 ? "elite" : ""}`}>{p.ovr}</span>
              </div>
              <div style={{ marginBottom: 10 }}>
                <StatBar label="FIN" value={p.shot} />
                <StatBar label="PAS" value={p.pass} />
                <StatBar label="DEF" value={p.def} />
                <StatBar label="VEL" value={p.pace} />
              </div>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div>
                  <div style={{ fontSize: 10, color: "var(--text-dim)" }}>Salário</div>
                  <div style={{ fontSize: 12, color: "var(--red)" }}>R$ {p.salary.toLocaleString()}/sem</div>
                </div>
                <div style={{ textAlign: "right" }}>
                  <div style={{ fontSize: 10, color: "var(--text-dim)" }}>Preço</div>
                  <div style={{ fontSize: 12, color: "var(--yellow)" }}>R$ {p.price.toLocaleString()}</div>
                </div>
              </div>
              <button
                className="btn btn-primary"
                style={{ width: "100%", marginTop: 10 }}
                onClick={() => buyPlayer(p)}
                disabled={finances.cash < p.price}
              >
                {finances.cash >= p.price ? "CONTRATAR" : "SEM SALDO"}
              </button>
            </div>
          ))}
          {market.length === 0 && (
            <div style={{ color: "var(--text-dim)", padding: 20, fontSize: 12 }}>
              Mercado vazio. Todos os jogadores foram contratados.
            </div>
          )}
        </div>
      </div>
    );
  }

  function TabelaTab() {
    return (
      <div className="page">
        <div className="section-title">Tabela de Classificação — Temporada {season}</div>
        <div className="card" style={{ padding: 0 }}>
          <table>
            <thead><tr>
              <th>#</th><th>Time</th><th>J</th><th>V</th><th>E</th><th>D</th><th>GP</th><th>GC</th><th>SG</th><th>PTS</th>
            </tr></thead>
            <tbody>
              {sortedTable.map((row, i) => {
                const pos = i + 1;
                const posClass = pos <= 4 ? "top3" : pos >= sortedTable.length - 3 ? "zone" : "";
                return (
                  <tr key={row.name} className={row.isMe ? "my-row" : ""}>
                    <td><span className={`pos-num ${posClass}`}>{pos}</span></td>
                    <td style={{ fontWeight: row.isMe ? "bold" : "normal" }}>
                      {row.isMe ? "▶ Meu Time" : row.name}
                    </td>
                    <td>{row.played}</td>
                    <td style={{ color: "var(--green)" }}>{row.w}</td>
                    <td style={{ color: "var(--yellow)" }}>{row.d}</td>
                    <td style={{ color: "var(--red)" }}>{row.l}</td>
                    <td>{row.gf}</td>
                    <td>{row.ga}</td>
                    <td style={{ color: row.gd >= 0 ? "var(--green)" : "var(--red)" }}>
                      {row.gd > 0 ? "+" : ""}{row.gd}
                    </td>
                    <td>
                      <span style={{ fontFamily: "Orbitron, monospace", color: "var(--yellow)", fontWeight: "bold" }}>
                        {row.pts}
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        <div style={{ fontSize: 10, color: "var(--text-dim)", marginTop: 8 }}>
          <span style={{ color: "var(--yellow)" }}>■</span> Classificação | &nbsp;
          <span style={{ color: "var(--red)" }}>■</span> Rebaixamento
        </div>
      </div>
    );
  }

  const tabs = [
    { id: "clube", label: "Clube" },
    { id: "escalacao", label: "Escalação" },
    { id: "partida", label: "Partida" },
    { id: "elenco", label: "Elenco" },
    { id: "mercado", label: "Mercado" },
    { id: "tabela", label: "Tabela" },
  ];

  return (
    <>
      <style>{css}</style>
      <div className="app">
        <header className="header">
          <div className="logo">GOAL<span>FATHER</span></div>
          <div className="header-info">
            <div>
              <div style={{ fontSize: 9 }}>CAIXA</div>
              <div className={`hval money ${finances.cash < 100000 ? "bad" : ""}`}>
                R$ {(finances.cash / 1000).toFixed(0)}k
              </div>
            </div>
            <div>
              <div style={{ fontSize: 9 }}>POSIÇÃO</div>
              <div className="hval">#{myPos}</div>
            </div>
            <div>
              <div style={{ fontSize: 9 }}>TEMPORADA</div>
              <div className="hval">{season}</div>
            </div>
            <div>
              <div style={{ fontSize: 9 }}>RODADA</div>
              <div className="hval">{round}/{LEAGUE_OPPONENTS.length}</div>
            </div>
            <div>
              <div style={{ fontSize: 9 }}>OVR</div>
              <div className="hval">{teamOvr}</div>
            </div>
          </div>
        </header>

        <nav className="nav">
          {tabs.map(t => (
            <button key={t.id} className={`nav-btn ${tab === t.id ? "active" : ""}`}
              onClick={() => setTab(t.id)}>
              {t.label}
            </button>
          ))}
        </nav>

        {tab === "clube"     && <ClubeTab />}
        {tab === "escalacao" && <PitchTab />}
        {tab === "partida"   && <MatchTab />}
        {tab === "elenco"    && <ElencoTab />}
        {tab === "mercado"   && <MercadoTab />}
        {tab === "tabela"    && <TabelaTab />}

        {toast && (
          <div className={`toast ${toast.err ? "err" : ""}`}>{toast.msg}</div>
        )}
      </div>
    </>
  );
}
