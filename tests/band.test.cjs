const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const rules = require('../band/src/common/scoring');
function harness(saved = '', failWrite = false) {
  const sent = [], writes = [], timers = new Map(); let timerId = 0;
  const conn = {send: args => sent.push(args.data), getReadyState: args => args.success({status:1})};
  const context = {
    storage: {get: args => args.success(saved), set: args => { writes.push(args.value); failWrite ? args.fail() : args.success(); }},
    interconnect: {instance: () => conn}, vibrator: {vibrate: () => {}},
    require: () => rules, setInterval: () => 1, clearInterval: () => {},
    setTimeout: f => { timers.set(++timerId,f); return timerId; }, clearTimeout: id => timers.delete(id),
    result: null
  };
  let script = fs.readFileSync(require.resolve('../band/src/pages/game/game.ux'),'utf8').split('<script>')[1].split('</script>')[0];
  script = script.replace(/^import .*;$/gm,'').replace('export default','result =');
  vm.runInNewContext(script,context);
  const page = context.result;
  Object.assign(page,JSON.parse(JSON.stringify(page.private)));
  page.onInit();
  const hello = () => conn.onmessage({data:JSON.stringify({type:'hello',challenge:'test-challenge'})});
  return {page,conn,sent,writes,hello,timers};
}
test('band persists before broadcasting, correction is one atomic update', () => {
  const h=harness(); h.page.startGame(); h.hello();
  assert.equal(h.sent.at(-1).action,'sync');
  h.page.weWon();
  assert.equal(h.sent.at(-1).action,'rally');
  assert.equal(JSON.parse(h.writes.at(-1)).state.scores[0],1);
  h.page.edit(); h.page.usPlus(); h.page.switchServing();
  const count=h.sent.length;
  assert.equal(h.sent.length,count);
  h.page.saveEdit();
  assert.equal(h.sent.length,count+1);
  assert.equal(h.sent.at(-1).action,'correct');
  assert.deepEqual(Array.from(h.sent.at(-1).state.scores),[2,0]);
  assert.equal(h.sent.at(-1).state.serving,1);
});
test('storage failure leaves saved score and announcements unchanged', () => {
  const h=harness(JSON.stringify(rules.create()),true); h.hello();
  const count=h.sent.length;
  h.page.weWon();
  assert.equal(h.page.us,0);
  assert.equal(h.sent.length,count);
  assert.equal(h.page.notice,'Save failed - try again');
});
test('offline scoring reconnects with a silent snapshot, never an old rally', () => {
  const h=harness(JSON.stringify(rules.create()));
  h.page.weWon(); h.page.weWon();
  assert.equal(h.page.us,2);
  assert.ok(h.sent.every(p=>p.type==='hello'));
  h.hello();
  assert.equal(h.sent.at(-1).action,'sync');
  assert.equal(h.sent.at(-1).state.scores[0],2);
  h.page.repeat(); assert.equal(h.sent.at(-1).action,'repeat');
});
test('stale speech acknowledgments cannot confirm a newer score', () => {
  const h=harness(JSON.stringify(rules.create())); h.hello();
  h.page.weWon(); const old=h.sent.at(-1);
  h.page.weWon(); const latest=h.sent.at(-1);
  h.conn.onmessage({data:{type:'ack',session:old.session,sequence:old.sequence,status:'spoken'}});
  assert.equal(h.page.phoneStatus,'Sending score...');
  h.conn.onmessage({data:{type:'ack',session:latest.session,sequence:latest.sequence,status:'spoken'}});
  assert.equal(h.page.phoneStatus,'Score spoken');
});
