const test = require('node:test');
const assert = require('node:assert/strict');
const s = require('../band/src/common/scoring');
test('doubles opening exception and complete service rotation', () => {
  let g = s.create('doubles', 0, 11);
  assert.equal(s.call(g.state), '0 - 0 - 2');
  g = s.rally(g, 1);
  assert.deepEqual(g.state.scores, [0, 0]);
  assert.equal(g.state.serving, 1);
  assert.equal(g.state.server, 1);
  g = s.rally(g, 1);
  assert.equal(s.call(g.state), '1 - 0 - 1');
  g = s.rally(g, 0);
  assert.equal(s.call(g.state), '1 - 0 - 2');
  g = s.rally(g, 0);
  assert.equal(s.call(g.state), '0 - 1 - 1');
});
test('announces serving side first and undo restores the full service state', () => {
  let g = s.create();
  g = s.correct(g, {mode:'doubles',scores:[4,2],serving:0,server:2,target:11});
  const before = g;
  g = s.rally(g, 1);
  assert.equal(s.call(g.state), '2 - 4 - 1');
  g = s.undo(g);
  assert.deepEqual(g.state, before.state);
  assert.equal(g.revision, before.revision + 2);
  assert.equal(s.call(g.state), '4 - 2 - 2');
});
test('singles changes serve without awarding receiving side a point', () => {
  let g = s.create('singles', 1, 15);
  g = s.rally(g, 1);
  g = s.rally(g, 0);
  assert.equal(s.call(g.state), '0 - 1');
  assert.equal(g.state.server, 1);
});
test('win by two, prevent extra rallies after game, undo reopens game', () => {
  let g = s.create();
  g = s.correct(g, {mode:'doubles',scores:[10,10],serving:0,server:1,target:11});
  g = s.rally(g, 0);
  assert.equal(s.winner(g.state), -1);
  g = s.rally(g, 0);
  assert.equal(s.winner(g.state), 0);
  assert.equal(s.rally(g, 1), g);
  assert.equal(s.winner(s.undo(g).state), -1);
});
test('corrections are atomic, undoable, and never mutate earlier state', () => {
  const g = s.create();
  const draft = {mode:'doubles',scores:[8,9],serving:1,server:2,target:11};
  const edited = s.correct(g, draft);
  draft.scores[0] = 99;
  assert.equal(edited.state.scores[0], 8);
  assert.equal(g.state.scores[0], 0);
  assert.deepEqual(s.undo(edited).state, g.state);
  assert.throws(() => s.correct(g, {...draft,scores:[-1,0]}));
});
test('saved games validate history and survive JSON round trip', () => {
  const g = s.rally(s.create(), 0);
  assert.deepEqual(s.restore(JSON.stringify(g)), g);
  assert.throws(() => s.restore({...g,history:[{}]}));
  assert.throws(() => s.restore({...g,state:{...g.state,server:3}}));
  assert.throws(() => s.restore({...g,state:{...g.state,mode:'rally'}}));
});
test('undo history is bounded, packets contain independent snapshots', () => {
  let g = s.create();
  for(let i=0;i<200;i++) g = s.rally(g, 1-g.state.serving);
  assert.equal(g.history.length, 100);
  const p = s.packet(g, 'sync', 10, 'session-1');
  g.state.scores[0] = 5;
  assert.equal(p.state.scores[0], 0);
  assert.equal(p.action, 'sync');
});
