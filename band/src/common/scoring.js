// Pure state machine: shared by the wearable and the desktop preview/tests.
// Traditional side-out scoring only. Teams are stable: 0 = us, 1 = them.
function copy(value) { return JSON.parse(JSON.stringify(value)); }
function integer(n, lo, hi) { return Number.isInteger(n) && n >= lo && n <= hi; }
function validState(s) {
  return !!s && (s.mode === 'doubles' || s.mode === 'singles') &&
    Array.isArray(s.scores) && s.scores.length === 2 && s.scores.every(n => integer(n, 0, 99)) &&
    integer(s.serving, 0, 1) && integer(s.server, 1, 2) &&
    (s.mode !== 'singles' || s.server === 1) && [11, 15, 21].indexOf(s.target) !== -1;
}
function winner(s) {
  if (Math.max(s.scores[0], s.scores[1]) >= s.target && Math.abs(s.scores[0] - s.scores[1]) >= 2) {
    return s.scores[0] > s.scores[1] ? 0 : 1;
  }
  return -1;
}
function create(mode, serving, target) {
  const state = { mode: mode || 'doubles', scores: [0, 0], serving: serving || 0,
    server: mode === 'singles' ? 1 : 2, target: target || 11 };
  if (!validState(state)) throw new Error('Invalid game settings');
  return { version: 1, id: Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10),
    revision: 0, state: state, history: [] };
}
function validGame(g) {
  return !!g && g.version === 1 && typeof g.id === 'string' && /^[a-z0-9-]{5,80}$/.test(g.id) &&
    integer(g.revision, 0, 1000000000) && validState(g.state) && Array.isArray(g.history) &&
    g.history.length <= 100 && g.history.every(validState);
}
function restore(value) {
  const g = typeof value === 'string' ? JSON.parse(value) : value;
  if (!validGame(g)) throw new Error('Invalid saved game');
  return copy(g);
}
function commit(g, state) {
  if (!validState(state)) throw new Error('Invalid score');
  const next = copy(g);
  next.history.push(copy(g.state));
  next.history = next.history.slice(-100);
  next.state = copy(state);
  next.revision++;
  return next;
}
function rally(g, team) {
  if (!integer(team, 0, 1)) throw new Error('Invalid team');
  if (winner(g.state) !== -1) return g;
  const s = copy(g.state);
  if (team === s.serving) {
    if (s.scores[team] === 99) return g;
    s.scores[team]++;
  } else if (s.mode === 'doubles' && s.server === 1) {
    s.server = 2;
  } else {
    s.serving = 1 - s.serving;
    s.server = 1;
  }
  return commit(g, s);
}
function undo(g) {
  if (!g.history.length) return g;
  const next = copy(g);
  next.state = next.history.pop();
  next.revision++;
  return next;
}
function correct(g, draft) { return commit(g, draft); }
function call(s) {
  const nums = [s.scores[s.serving], s.scores[1 - s.serving]];
  if (s.mode === 'doubles') nums.push(s.server);
  return nums.join(' - ');
}
function packet(g, action, sequence, session) {
  return { protocol: 1, type: 'score', session: session, sequence: sequence, gameId: g.id,
    revision: g.revision, action: action, state: copy(g.state) };
}
module.exports = { create, restore, validState, validGame, winner, rally, undo, correct, call, packet };
