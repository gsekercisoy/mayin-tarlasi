import React, { useEffect, useRef, useState } from 'react';
import { api } from './api.js';

const NUM_COLORS = ['', '#3b82f6', '#16a34a', '#dc2626', '#7c3aed', '#b45309', '#0891b2', '#334155', '#111827'];

export default function App() {
  return (
    <div className="app">
      <h1>💣 Mayın Tarlası</h1>
      <p className="lead">Mayın haritası tamamen sunucuda üretilir ve saklanır; istemci çözgelere erişemez (oyun bitince açılır).</p>
      <Board />
    </div>
  );
}

function Board() {
  const [view, setView] = useState(null);
  const [player, setPlayer] = useState(() => localStorage.getItem('minesPlayer') || '');
  const [difficulty, setDifficulty] = useState('EASY');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const tick = useRef(null);
  const [boardKey, setBoardKey] = useState(0);

  const startTick = () => {
    if (tick.current) clearInterval(tick.current);
    setElapsed(0);
    tick.current = setInterval(() => setElapsed(e => e + 1), 1000);
  };
  const stopTick = (fromServer) => {
    if (tick.current) { clearInterval(tick.current); tick.current = null; }
    if (fromServer && fromServer != null) setElapsed(fromServer);
  };
  useEffect(() => () => { if (tick.current) clearInterval(tick.current); }, []);

  const newGame = async () => {
    setErr('');
    setBusy(true);
    const name = player.trim() || 'MISAFIR';
    try {
      const v = await api.post('/games', { player: name, difficulty });
      localStorage.setItem('minesPlayer', name);
      setView(v);
      setBoardKey(k => k + 1);
      setBusy(false);
      startTick();
      window.dispatchEvent(new CustomEvent('mines-updated', { detail: { refresh: true } }));
    } catch (e) { setErr(e.message); setBusy(false); }
  };

  const act = async (r, c, mode) => {
    if (!view || view.status !== 'ACTIVE' || busy) return;
    setErr('');
    setBusy(true);
    try {
      const v = await api.post(`/games/${view.id}/${mode}`, { row: r, col: c });
      setView(v);
      setBoardKey(k => k + 1);
      if (v.status !== 'ACTIVE') stopTick(v.durationSec);
      if (v.status === 'WON' || v.status === 'LOST') {
        window.dispatchEvent(new CustomEvent('mines-updated', { detail: { refresh: true } }));
      }
    } catch (e) { setErr(e.message); }
    setBusy(false);
  };

  const over = view && view.status !== 'ACTIVE';

  return (
    <div className="layout">
      <div className="panel game-panel">
        <div className="setup">
          <input placeholder="Oyuncu adı" value={player} maxLength={20}
            onChange={e => setPlayer(e.target.value)} />
          <div className="diffs">
            {['EASY', 'MEDIUM', 'HARD'].map(d => (
              <button key={d} className={difficulty === d ? 'active' : ''} onClick={() => setDifficulty(d)}>
                {d}
              </button>
            ))}
          </div>
          <button className="btn primary" onClick={newGame} disabled={busy}>
            {view ? '↺ Yeni Oyun' : '▶ Başlat'}
          </button>
        </div>

        {err && <div className="err">{err}</div>}

        {view && (
          <div className="hud">
            <span>💣 {view.mineCount}</span>
            <span>🚩 {view.flagsLeft}</span>
            <span className={elapsed > 0 ? 'time' : ''}>⏱ {view.durationSec != null ? view.durationSec + 's' : elapsed + 's'}</span>
          </div>
        )}

        {view && (
          <div className={`grid ${over ? 'over' : ''}`}
            style={{ gridTemplateColumns: `repeat(${view.width}, 32px)` }}
            key={boardKey}>
            {view.board.map((row, r) =>
              row.map((cell, c) => (
                <button
                  key={`${r}-${c}`}
                  className={[
                    'cell',
                    cell.revealed ? 'open' : '',
                    cell.flag && !cell.revealed ? 'flag' : '',
                    cell.mine ? 'mine' : ''
                  ].join(' ')}
                  style={cell.revealed && cell.v ? { color: NUM_COLORS[cell.v] } : null}
                  onClick={() => act(r, c, 'reveal')}
                  onContextMenu={(e) => { e.preventDefault(); act(r, c, 'flag'); }}
                  title={`${r},${c}`}
                >
                  {cell.flag && !cell.revealed ? '🚩'
                    : cell.mine ? '💣'
                    : cell.revealed && cell.v ? cell.v
                    : ''}
                </button>
              ))
            )}
          </div>
        )}

        {!view && <div className="muted placeholder">Tahta için "Başlat" — EASY 9×9/10 💣, MEDIUM 12×12/25, HARD 16×16/45</div>}

        {view && view.status === 'WON' && (
          <div className="banner won">🎉 Kazandın {view.player}! Süre: {view.durationSec} sn — leaderboard'a bak!</div>
        )}
        {view && view.status === 'LOST' && (
          <div className="banner lost">💥 Mayına bastın! Orta tuş (sağ tık) ile bayrak koymayı dene.</div>
        )}

        <p className="small">Sol tık: aç · Sağ tık: bayrak koyma/kaldırma · İlk tıklama her zaman güvenli</p>
      </div>

      <Side view={view} over={over} />
    </div>
  );
}

function Side({ view, over }) {
  const [stats, setStats] = useState(null);
  const [ldDiff, setLdDiff] = useState('EASY');
  const [lb, setLb] = useState([]);
  const [flg, setFlg] = useState(false);

  useEffect(() => {
    api.get('/stats').then(setStats).catch(() => {});
  }, [flg, view && view.status === 'WON']);

  useEffect(() => {
    setFlg(false);
    const h = (e) => {
      if (e.detail && e.detail.refresh) setFlg(f => !f);
    };
    window.addEventListener('mines-updated', h);
    return () => window.removeEventListener('mines-updated', h);
  }, []);

  useEffect(() => {
    setLdDiff(ldDiff);
    api.get(`/leaderboard?difficulty=${ldDiff}`).then(setLb).catch(() => {});
  }, [ldDiff, flg]);

  return (
    <div className="panel side-panel">
      <h2>🏆 Rekorlar ({ldDiff})</h2>
      <div className="diffs small-diffs">
        {['EASY', 'MEDIUM', 'HARD'].map(d => (
          <button key={d} className={ldDiff === d ? 'active' : ''} onClick={() => setLdDiff(d)}>{d}</button>
        ))}
      </div>
      <table className="tbl">
        <thead><tr><th>#</th><th>Oyuncu</th><th>Süre</th></tr></thead>
        <tbody>
          {lb.map((g, i) => (
            <tr key={i}>
              <td>{i + 1}</td>
              <td>{g.player}</td>
              <td className="num">{g.durationSec} sn</td>
            </tr>
          ))}
          {!lb.length && <tr><td colSpan={3} className="muted">Bu zorlukta kazanan yok</td></tr>}
        </tbody>
      </table>

      {stats && (
        <div className="statsrow muted">
          <span><b>{stats.totalGames}</b> oyun</span>
          <span className="num">{stats.won} kazanan</span>
          <span className="num">{stats.lost} kayıp</span>
          <span>En hızlı: <b>{stats.fastestWinSec != null ? stats.fastestWinSec + ' sn' : '—'}</b></span>
        </div>
      )}
      <p className="small small2">{over ? 'Skor kaydedildi (kazandın ise)' : 'Kazanınca süre rekor tablosuna düşer'}</p>
    </div>
  );
}
