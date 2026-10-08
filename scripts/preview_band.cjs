const fs=require('fs');
// Optional development tool: use an installed Playwright module, not shipped in either app.
const {chromium}=require(process.env.PICKLEBALL_PLAYWRIGHT || 'playwright');
const path=require('path');
const root=path.resolve(__dirname,'..');
const version=JSON.parse(fs.readFileSync(root+'/package.json','utf8')).version;
const source=fs.readFileSync(root+'/band/src/pages/game/game.ux','utf8');
const template=source.split('<template>')[1].split('</template>')[0];
let css=source.split('<style>')[1].split('</style>')[0];
css=css.replace(/lines:\s*(\d+);/g,(_,n)=>'--vela-lines: '+n+';'+(n==='1'?' white-space: nowrap;':''));
const base={screen:'game',pageIndex:2,musicStatus:'Start music on your phone',us:8,them:6,serveLabel:'We serve / server 2',rallyServe:'We serve',rallyDetail:'8 - 6 - 2',phoneStatus:'Phone connected',notice:'Doubles / to 11',scoreCall:'8 - 6 - 2',isDoubles:true,editUs:8,editThem:6,editServing:'Us',editServer:2,newMode:'Doubles',newFirst:'Them',newTarget:11,newWarning:'Replaces this game and its undo history.',hasGame:true,linkDetails:'Diagnosis 1001: Phone companion not found by the band. Open Pickleball on the phone.'};
const scenes=[
 {name:'Settings',state:{pageIndex:0}},
 {name:'Live score',state:{pageIndex:1}},
 {name:'Play · opens first',state:{}},
 {name:'Music',state:{pageIndex:3}},
 {name:'Correct score',state:{screen:'edit',editUs:99,editThem:99}},
 {name:'New game',state:{screen:'new'}},
 {name:'Long message',state:{screen:'connection',phoneStatus:'Phone offline - score saved',linkDetails:'Diagnosis 1001: Phone companion not found by the band. Open Pickleball on the phone. The connection details can be copied from the phone app.'}},
 {name:'Long score / offline',state:{pageIndex:1,us:99,them:98,scoreCall:'98 - 99 - 2',serveLabel:'They serve / server 2',phoneStatus:'Phone offline - score saved'}},
 {name:'Play · singles',state:{rallyServe:'They serve',rallyDetail:'6 - 8'}},
 {name:'Play · game over',state:{rallyServe:'They win!',rallyDetail:'11 - 8 - 1'}},
 {name:'Play · save failed',state:{rallyServe:'Save failed'}},
 {name:'Play · two-digit score',state:{rallyServe:'They serve',rallyDetail:'98 - 99 - 2'}}
];
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
 const page=await browser.newPage({viewport:{width:1080,height:1800},deviceScaleFactor:1});
 await page.setContent(`<html><head><style>
 *{box-sizing:border-box} body{margin:0;background:#e9ede7;font-family:Arial,sans-serif;color:#183127} h1{font-size:24px;margin:24px 24px 8px}p{font-size:13px;margin:0 24px 12px}.sheet{display:grid;grid-template-columns:repeat(4,240px);gap:20px;margin:20px 30px}.scene{width:240px}h2{font-size:14px;text-align:center;margin:0 0 10px}.device{width:212px;height:520px;margin:auto;border-radius:106px;background:#071510;overflow:hidden;box-shadow:0 0 0 6px #13251c}
 .device div{display:flex;flex-shrink:0}.device text{display:block;flex-shrink:0;overflow-wrap:anywhere}.device list{display:block;overflow-y:auto;flex-shrink:0;scrollbar-width:none}.device list-item{display:flex;flex-shrink:0;align-items:center}
 .device swiper{display:flex;flex-shrink:0;overflow:hidden}
 .device stack{display:block;position:relative;flex-shrink:0}.device stack>div{position:absolute}
 ${css}
 </style></head><body><h1>Pickleball · Band 10</h1><p>212 × 520 browser layout preview · 212 × 260 rally buttons · Settings ↔ Live score ↔ Play ↔ Music</p><div class="sheet"></div></body></html>`);
 await page.evaluate(({template,scenes,base})=>{
  const evaluate=(expression,state)=>Function('state','with(state){return ('+expression+');}')(state);
  for(const scene of scenes){
   const state={...base,...scene.state};
   const holder=document.createElement('div');holder.className='scene';
   const heading=document.createElement('h2');heading.textContent=scene.name;holder.appendChild(heading);
   const device=document.createElement('div');device.className='device';device.innerHTML=template;
   for(const el of [...device.querySelectorAll('[if]')]){if(!evaluate(el.getAttribute('if').slice(2,-2),state))el.remove();else el.removeAttribute('if');}
   for(const pager of device.querySelectorAll('swiper'))for(const [i,slide] of [...pager.children].entries())if(i!==state.pageIndex)slide.remove();
   const walker=document.createTreeWalker(device,NodeFilter.SHOW_TEXT);let node;
   while(node=walker.nextNode())node.textContent=node.textContent.replace(/{{(.*?)}}/g,(_,expr)=>evaluate(expr,state));
   holder.appendChild(device);document.querySelector('.sheet').appendChild(holder);
  }
 },{template,scenes,base});
 const issues=await page.evaluate(()=>{
  const result=[];
  for(const scene of document.querySelectorAll('.scene')){
   const screen=scene.querySelector('.page').getBoundingClientRect();
   const name=scene.querySelector('h2').textContent;
   const buttons=[...scene.querySelectorAll('.rallyButton')];
   for(const [i,button] of buttons.entries()){
    const r=button.getBoundingClientRect();
    if(r.width!==212||r.height!==260||r.top-screen.top!==i*260||r.left!==screen.left)result.push({name,error:'Rally button does not cover its half of the display'});
   }
   const badge=scene.querySelector('.serveBadge');
   if(badge){const r=badge.getBoundingClientRect();if(r.top+r.height/2!==screen.top+260)result.push({name,error:'Serve badge is not centered over the button seam'});}
   for(const el of scene.querySelectorAll('text')){
    if(el.closest('list'))continue;
    const style=getComputedStyle(el),box=el.getBoundingClientRect();
    const range=document.createRange();range.selectNodeContents(el);
    const rects=[...range.getClientRects()].filter(r=>r.width>0 && r.height>0);
    const max=Number(style.getPropertyValue('--vela-lines'));
    if(max && rects.length>max)result.push({name,text:el.textContent,error:'Too many lines',lines:rects.length,max});
    if(el.scrollWidth>el.clientWidth+1||el.scrollHeight>el.clientHeight+1)result.push({name,text:el.textContent,error:'Text exceeds its box',width:box.width,height:box.height,scroll:[el.scrollWidth,el.scrollHeight]});
    for(const r of rects){
     for(const [x,y] of [[r.left-screen.left,r.top-screen.top],[r.right-screen.left,r.top-screen.top],[r.left-screen.left,r.bottom-screen.top],[r.right-screen.left,r.bottom-screen.top]]){
      const dy=y<106?y-106:y>414?y-414:0;
      if(x<0||x>212||y<0||y>520||(dy && (x-106)**2+dy**2>106**2))result.push({name,text:el.textContent,error:'Outside conservative rounded display mask',x,y});
     }
    }
   }
  }
  return result;
 });
 fs.mkdirSync(root+'/docs',{recursive:true});
 await page.screenshot({path:root+'/docs/band-ui-'+version+'.png',fullPage:true});
 fs.mkdirSync(root+'/build',{recursive:true});
 fs.writeFileSync(root+'/build/band-ui-preview.html',await page.content());
 console.log(JSON.stringify({issues,preview:root+'/docs/band-ui-'+version+'.png'},null,2));
 await browser.close();
 if(issues.length)process.exitCode=1;
})();
