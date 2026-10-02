const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const rules = require('../band/src/common/scoring');
function harness(saved = '', failWrite = false) {
  let now = 1000;
  const sent = [], requests = [], writes = [], diagnoses = [], timers = new Map(), intervals = new Map(); let timerId = 0;
  const makeConnection = () => ({
    send: args => { sent.push(args.data); requests.push(args); },
    getReadyState: args => args.success({status:1}), diagnosis: args => diagnoses.push(args)
  });
  const conn = makeConnection(); let activeConnection = conn, instanceCalls = 0;
  const context = {
    storage: {get: args => args.success(saved), set: args => { writes.push(args.value); failWrite ? args.fail() : args.success(); }},
    interconnect: {instance: () => { instanceCalls++; return activeConnection; }}, vibrator: {vibrate: () => {}},
    require: () => rules, setInterval: f => { intervals.set(++timerId,f); return timerId; }, clearInterval: id => intervals.delete(id),
    Date: class extends Date { static now() { return now; } },
    setTimeout: f => { timers.set(++timerId,f); return timerId; }, clearTimeout: id => timers.delete(id),
    result: null
  };
  let script = fs.readFileSync(require.resolve('../band/src/pages/game/game.ux'),'utf8').split('<script>')[1].split('</script>')[0];
  script = script.replace(/^import .*;$/gm,'').replace('export default','result =');
  vm.runInNewContext(script,context);
  const page = context.result;
  Object.assign(page,JSON.parse(JSON.stringify(page.private)));
  page.$element = () => ({swipeTo: ({index}) => page.pageChanged({index})});
  page.onInit();
  const hello = (mediaVersion = 1) => activeConnection.onmessage({data:JSON.stringify({type:'hello',challenge:'test-challenge',mediaVersion})});
  return {page,conn,sent,requests,writes,hello,timers,intervals,diagnoses,makeConnection,
    replaceConnection: next => { activeConnection = next; }, instanceCalls: () => instanceCalls, advance: ms => { now += ms; }};
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
test('manual reconnect keeps score and undo history and resynchronizes silently', () => {
  const h=harness(JSON.stringify(rules.create())); h.hello(); h.page.weWon();
  const saved=h.writes.at(-1), writes=h.writes.length;
  h.page.connectionSettings();
  assert.equal(h.page.screen,'connection');
  assert.equal(h.page.us,1);
  assert.equal(h.writes.length,writes);
  assert.equal(h.sent.at(-1).type,'hello');
  h.hello();
  assert.equal(h.sent.at(-1).action,'sync');
  assert.equal(h.sent.at(-1).state.scores[0],1);
  assert.equal(h.writes.at(-1),saved);
  h.page.connectionBack(); assert.equal(h.page.screen,'game');
  h.page.undo(); assert.equal(h.page.us,0);
});
test('resume reacquires connection and restarts one retry timer without resetting the game', () => {
  const h=harness(JSON.stringify(rules.create())); h.hello();
  h.page.onShow(); assert.equal(h.intervals.size,1);
  h.page.onHide(); assert.equal(h.intervals.size,0);
  const next=h.makeConnection(); h.replaceConnection(next);
  const calls=h.instanceCalls(); h.page.onShow();
  assert.equal(h.instanceCalls(),calls+1);
  assert.equal(h.conn.onmessage,null);
  assert.equal(typeof next.onmessage,'function');
  assert.equal(h.sent.at(-1).type,'hello');
  h.page.onShow(); assert.equal(h.intervals.size,1);
  h.hello(); assert.equal(h.sent.at(-1).action,'sync');
  assert.equal(h.writes.length,0);
  h.page.onDestroy(); assert.equal(h.intervals.size,0);
});
test('late errors and diagnosis from a previous retry cannot erase a new handshake', () => {
  const h=harness(JSON.stringify(rules.create())); h.page.reconnect();
  const old=h.requests.at(-1), diagnosis=h.diagnoses.at(-1), oldClose=h.conn.onclose;
  h.page.reconnect(); h.hello();
  const status=h.page.phoneStatus, detail=h.page.linkDetails;
  old.fail({code:1006,data:'old failure'}); oldClose({code:1006});
  diagnosis.success({status:1001});
  assert.equal(h.page.phoneStatus,status); assert.equal(h.page.linkDetails,detail);
  h.diagnoses.at(-1).success({status:204});
  assert.equal(h.page.linkDetails,detail);
});
test('connection diagnostics distinguish missing companion, timeout and raw error codes', () => {
  const h=harness(); h.page.connectionSettings();
  h.diagnoses.at(-1).success({status:1001});
  assert.match(h.page.linkDetails,/1001.*companion not found/);
  h.requests.at(-1).fail({code:1006,data:'offline'});
  assert.match(h.page.linkDetails,/Diagnosis 1001/);
  h.page.reconnect(); h.diagnoses.at(-1).success({status:204});
  assert.match(h.page.linkDetails,/204.*timed out/);
  h.page.reconnect(); h.diagnoses.at(-1).fail({data:'bridge error'},1000);
  assert.match(h.page.linkDetails,/Diagnosis 1000: bridge error/);
  h.conn.onerror({code:1006,data:'disconnected'});
  assert.match(h.page.linkDetails,/Link 1006: disconnected/);
  h.page.connectionBack(); assert.equal(h.page.screen,'new');
});
test('retry works without optional diagnosis support and offline heartbeat retries hello', () => {
  const h=harness(); h.conn.diagnosis=undefined;
  h.page.reconnect(); h.page.onShow();
  const count=h.sent.length;
  for (const tick of h.intervals.values()) tick();
  assert.equal(h.sent.length,count+1); assert.equal(h.sent.at(-1).type,'hello');
  h.hello();
  const connectedCount=h.sent.length;
  for (const tick of h.intervals.values()) tick();
  assert.equal(h.sent.length,connectedCount);
});
test('secondary controls remain reachable through More without changing the game', () => {
  const h=harness(JSON.stringify(rules.create())); h.hello(); h.page.weWon();
  const saved=h.writes.at(-1), writes=h.writes.length;
  h.page.more(); assert.equal(h.page.screen,'more');
  h.page.edit(); assert.equal(h.page.screen,'edit');
  h.page.back(); assert.equal(h.page.screen,'game');
  h.page.more(); h.page.connectionSettings();
  assert.equal(h.page.screen,'connection');
  h.page.connectionBack(); assert.equal(h.page.screen,'more');
  h.page.settings(); assert.equal(h.page.screen,'new');
  h.page.back(); assert.equal(h.page.screen,'game');
  assert.equal(h.page.us,1); assert.equal(h.writes.length,writes);
  assert.equal(h.writes.at(-1),saved);
});
test('music controls and their acknowledgments do not mutate or acknowledge the score', () => {
  const h=harness(JSON.stringify(rules.create())); h.hello();
  h.page.weWon(); const score=h.sent.at(-1), writes=h.writes.length;
  h.page.showMusic(); h.page.musicToggle(); const music=h.sent.at(-1);
  assert.equal(music.type,'media'); assert.equal(music.action,'toggle');
  assert.equal(music.challenge,'test-challenge');
  assert.equal(h.page.musicBusy,true); const count=h.sent.length;
  h.page.musicToggle(); assert.equal(h.sent.length,count);
  h.conn.onmessage({data:{type:'mediaAck',session:music.session,sequence:music.sequence,status:'sent',message:'Play / pause sent'}});
  assert.equal(h.page.musicBusy,false); assert.equal(h.page.musicStatus,'Play / pause sent');
  assert.equal(h.page.phoneStatus,'Sending score...');
  h.conn.onmessage({data:{type:'ack',session:score.session,sequence:score.sequence,status:'spoken'}});
  assert.equal(h.page.phoneStatus,'Score spoken');
  h.page.showScore(); assert.equal(h.page.pageIndex,0);
  assert.equal(h.page.us,1); assert.equal(h.writes.length,writes);
});
test('swiping across a rally button cannot score, undo or speak', () => {
  const h=harness(JSON.stringify(rules.create())); h.hello();
  const count=h.sent.length;
  h.page.pageTouchStart({touches:[{clientX:150,clientY:200}]});
  h.page.pageTouchMove({touches:[{clientX:60,clientY:202}]});
  h.page.pageTouchEnd();
  h.page.weWon(); h.page.theyWon(); h.page.undo(); h.page.repeat();
  assert.equal(h.writes.length,0); assert.equal(h.sent.length,count);
  h.page.pageChanged({index:1}); h.advance(400);
  h.page.pageTouchStart({touches:[{clientX:90,clientY:200}]});
  h.page.weWon(); assert.equal(h.writes.length,0);
  h.page.showScore(); h.advance(400);
  h.page.pageTouchStart({touches:[{clientX:90,clientY:200}]});
  h.page.weWon(); assert.equal(h.page.us,1);
});
test('music is never queued offline or retried after timeout, and old phones get an upgrade hint', () => {
  const h=harness(JSON.stringify(rules.create())); h.page.showMusic(); h.page.musicNext();
  assert.ok(h.sent.every(p=>p.type==='hello'));
  h.hello(0); h.page.musicNext();
  assert.equal(h.page.musicStatus,'Update phone app for music');
  assert.ok(h.sent.every(p=>p.type!=='media'));
  h.hello(); h.page.musicNext();
  assert.equal(h.sent.at(-1).action,'next');
  const count=h.sent.length;
  for(const f of [...h.timers.values()]) f();
  assert.equal(h.page.musicBusy,false); assert.equal(h.page.musicStatus,'No reply - check phone');
  assert.equal(h.sent.length,count);
  h.page.onShow(); h.hello();
  assert.equal(h.sent.at(-1).action,'sync');
  assert.equal(h.sent.filter(p=>p.type==='media').length,1);
});
test('late music replies and send failures cannot replace a newer command result', () => {
  const h=harness(JSON.stringify(rules.create())); h.hello(); h.page.showMusic(); h.page.musicPrevious();
  const old=h.sent.at(-1), oldSend=h.requests.at(-1);
  const ack=(packet,message)=>h.conn.onmessage({data:{type:'mediaAck',session:packet.session,sequence:packet.sequence,status:'sent',message}});
  ack(old,'Previous track sent'); h.page.musicLouder(); const next=h.sent.at(-1);
  assert.equal(next.action,'volumeUp');
  ack(old,'Old reply'); oldSend.fail();
  assert.equal(h.page.musicStatus,'Sending control...');
  ack(next,'Volume 5 / 15'); assert.equal(h.page.musicStatus,'Volume 5 / 15');
  h.page.musicQuieter(); assert.equal(h.sent.at(-1).action,'volumeDown');
  h.page.onDestroy(); assert.equal(h.timers.size,0);
});
