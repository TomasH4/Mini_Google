"use strict";
(() => {
const $ = id => document.getElementById(id);
const langs = ["go","java","scala"];
const names = {go:"Go",java:"Java",scala:"Scala"};
const colors = {go:"#00ADD8",java:"#F59E0B",scala:"#EF4444"};
const axes = [
  ["indexing","Indexación","indexing_time_ms","ms",false],
  ["throughput","Throughput mixto","qps_concurrent","QPS",true],
  ["p99_stability","Latencia p99","p99","µs",false],
  ["memory_efficiency","Memoria retenida","ram_retained_mb","MiB",false],
  ["code_conciseness","Concisión","loc","LOC",false]
];
const metrics = [
  ["indexing_time_ms","Indexación","ms",false],["qps_single","QPS secuencial","QPS",true],
  ["qps_concurrent","QPS mixto","QPS",true],["cold_start_ms","Arranque","ms",false]
];
const modules = {ast_parser:"Parser booleano",bm25_ranking:"Ranking BM25",concurrency:"Concurrencia"};
const profiles = {"Serverless / cloud":[7,3,4,10,2],"Alta frecuencia":[2,9,10,3,1],"Modelado funcional":[1,1,1,1,10]};
const percs = ["p50","p90","p95","p99","p999"];
const pctLabels = ["p50","p90","p95","p99","p99.9"];
let data, active = [...langs], relative = false, metric = 0, moduleKey = "ast_parser", weights = [5,5,5,5,5], profile = "";
let generation = 0, revision = 0;
const charts = new Map();
const reduced = matchMedia("(prefers-reduced-motion: reduce)").matches;
const fmt = (n, digits=2) => n.toLocaleString("es-CO",{maximumFractionDigits:digits});
const nameList = ls => ls.map(l => names[l]).join(" y ");
const quantity = (n,u) => fmt(n, u==="LOC"?0:3)+" "+u;
const css = name => getComputedStyle(document.body).getPropertyValue(name).trim();
function el(tag, cls, text) {
  const node=document.createElement(tag);
  if(cls) node.className=cls;
  if(text!==undefined) node.textContent=text;
  return node;
}
function dot(l){const d=el("i","dot"); d.style.setProperty("--color",colors[l]);return d}
function tag(l){const n=el("span","tag");n.append(dot(l),names[l]);return n}
function button(label,pressed,click){const b=el("button","",label);b.setAttribute("aria-pressed",String(pressed));b.onclick=click;return b}
function value(l,key){return percs.includes(key)?data.results[l].latency_us[key]:data.results[l][key]}
function ordered(key,higher){return [...active].sort((a,b)=>(value(a,key)-value(b,key))*(higher?-1:1))}
function factor(l,key,higher){
  const values=langs.map(x=>value(x,key));
  return higher?Math.max(...values)/value(l,key):value(l,key)/Math.min(...values);
}
function plotted(l,key,higher){return relative?factor(l,key,higher):value(l,key)}
function validate(d) {
  const fail=path=>{throw Error("Campo inválido: "+path)};
  function num(n,path,positive=false){if(!Number.isFinite(n)||(positive?n<=0:n<0))fail(path)}
  function string(v,path){if(typeof v!=="string"||!v.trim())fail(path)}
  const m=d?.metadata, p=d?.parity_check;
  if(!m?.corpus||!m.machine||!m.runtimes||typeof p?.passed!=="boolean")fail("metadata / parity_check");
  num(p.match_rate_pct,"match_rate_pct"); if(p.match_rate_pct>100)fail("match_rate_pct");
  num(p.queries_tested,"queries_tested"); string(m.machine.os,"os");string(m.machine.cpu_model,"cpu_model");
  num(m.machine.cores,"cores",true);num(m.machine.ram_total_gb,"ram_total_gb",true);
  if(!Number.isFinite(Date.parse(m.timestamp)))fail("timestamp");
  for(const k of ["documents","total_tokens","queries_per_run","iterations"])num(m.corpus[k],k,true);
  if(!p.passed)return;
  for(const l of langs){
    const r=d.results?.[l]; if(!r)fail("results."+l);
    string(m.runtimes[l],l);
    for(const k of ["indexing_time_ms","qps_single","qps_concurrent","cold_start_ms","docs_per_sec","ram_retained_mb","ram_rss_peak_mb","artifact_mb","loc"])num(r[k],l+"."+k,true);
    for(const k of percs)num(r.latency_us?.[k],l+"."+k,true);
    for(const [k] of axes){const v=r.normalized_scores?.[k];num(v,k);if(v>100.00001)fail(k)}
    for(const k of Object.keys(modules)){string(d.code_samples?.[k]?.[l],k+"."+l);string(d.code_samples[k].insight,k+".insight")}
  }
}
function summary(){
  const m=data.metadata,p=data.parity_check;
  $("subtitle").textContent=fmt(m.corpus.documents)+" documentos · "+fmt(m.corpus.queries_per_run)+" consultas por corrida · "+m.corpus.iterations+" repeticiones";
  $("parity").classList.toggle("fail",!p.passed);
  $("parity").textContent=(p.passed?"✓ Paridad verificada":"✕ Paridad fallida")+" · "+fmt(p.match_rate_pct)+" %";
  $("parity").title=p.queries_tested+" consultas doradas · "+(p.notes||"");
  $("machine").textContent=m.machine.cpu_model+" · "+m.machine.cores+" núcleos · "+fmt(m.machine.ram_total_gb,1)+" GiB";
  $("metadata").replaceChildren(...[m.machine.os,...langs.map(l=>names[l]+" · "+m.runtimes[l]),p.queries_tested+" consultas doradas",fmt(m.corpus.total_tokens)+" tokens",new Date(m.timestamp).toLocaleString("es-CO")].map(t=>el("span","",t)));
  $("iterations").textContent="Medianas de "+m.corpus.iterations+" corridas";
}
function activate(d,source){
  validate(d);data=d;summary();$("dashboard").hidden=!d.parity_check.passed;
  $("status").textContent=d.parity_check.passed?(source?"Archivo cargado · "+source:""):(d.parity_check.notes||"La validación funcional falló.");
  if(d.parity_check.passed)render();
}
function render(){
  if(!data?.parity_check.passed)return;
  revision++;
  $("filters").replaceChildren(...langs.map(l=>{
    const b=button("",active.includes(l),()=>{
      active=active.includes(l)?active.filter(x=>x!==l):langs.filter(x=>x===l||active.includes(x));render();$("filters").querySelector('[data-lang="'+l+'"]').focus()
    });b.dataset.lang=l;b.append(dot(l),names[l]);b.disabled=active.length===1&&active[0]===l;return b
  }));
  for(const b of $("mode").children)b.setAttribute("aria-pressed",String((b.dataset.mode==="relative")===relative));
  document.querySelectorAll(".legend").forEach(n=>n.replaceChildren(...active.map(tag)));
  $("metrics").replaceChildren(...metrics.map((m,i)=>button(m[1],metric===i,()=>{metric=i;render();$("metrics").children[i].focus()})));
  kpis();drawCharts();code();simulator();findings();
}
function count(node,n,digits){
  const id=revision, label=()=>node.textContent=fmt(n,digits);
  label();if(reduced||!window.IntersectionObserver)return;
  const watch=new IntersectionObserver(entries=>{
    if(!node.isConnected||id!==revision){watch.disconnect();return}
    if(!entries[0].isIntersecting)return;
    watch.disconnect();const start=performance.now();
    function frame(now){if(!node.isConnected||id!==revision)return;const t=Math.min(1,(now-start)/420);node.textContent=fmt(n*(1-(1-t)**3),digits);if(t<1)requestAnimationFrame(frame)}
    requestAnimationFrame(frame)
  });watch.observe(node)
}
function kpis(){
  const box=$("kpis");box.replaceChildren();
  for(const [axis,label,key,unit,higher] of axes){
    const order=ordered(key,higher),l=order[0],raw=value(l,key),n=plotted(l,key,higher);
    const card=el("article","kpi");card.style.setProperty("--color",colors[l]);
    const leader=order.filter(x=>value(x,key)===raw);
    const val=el("div");const number=el("strong","value");val.append(number,el("span","unit"," "+(relative?"×":unit)));
    val.title=String(raw)+" "+unit;card.append(el("h3","",label),tag(l),val);
    if(leader.length>1)card.append(el("small","unit","Empate con "+nameList(leader.slice(1))));
    if(axis==="indexing")card.append(el("div","unit",fmt(data.results[l].docs_per_sec,0)+" docs/s"));
    const gaps=order.slice(1).map(other=>names[other]+" "+fmt(higher?raw/value(other,key):value(other,key)/raw)+"×");
    card.append(el("div","comparison",gaps.join(" · ")||"Único motor visible"));box.append(card);count(number,n,relative?2:unit==="LOC"||raw>=1000?0:2);
  }
}
function note(id,text){$(id).replaceChildren(el("b","","Qué observar · "),document.createTextNode(text))}
function legendFormat(l){return {name:names[l],itemStyle:{color:colors[l]}}}
function base(){
  return {animation:!reduced,animationDuration:450,animationDurationUpdate:300,
    backgroundColor:css("--panel"),textStyle:{color:css("--muted"),fontFamily:"Segoe UI, sans-serif"},
    grid:{left:18,right:22,top:40,bottom:16,containLabel:true},
    toolbox:{right:1,top:0,iconStyle:{borderColor:css("--muted")},feature:{saveAsImage:{title:"Guardar imagen",pixelRatio:2}}},
    tooltip:{trigger:"axis",confine:true,renderMode:"richText",backgroundColor:css("--sub"),textStyle:{color:css("--ink")}}
  }
}
function axis(type="value"){return {type,axisLabel:{color:css("--muted"),fontSize:12,formatter:v=>typeof v==="number"?fmt(v,2):v},axisLine:{lineStyle:{color:css("--line")}},splitLine:{lineStyle:{color:css("--line"),type:"dashed"}},nameTextStyle:{color:css("--muted")}}}
function plot(id,option){
  if(!window.echarts)return;
  let chart=charts.get(id);
  if(!chart){chart=echarts.init($(id),null,{renderer:"canvas"});charts.set(id,chart);new ResizeObserver(()=>chart.resize()).observe($(id))}
  chart.setOption({...base(),...option},{replaceMerge:["series","xAxis","yAxis","radar","graphic"]})
}
function drawCharts(){
  const [key,label,unit,higher]=metrics[metric],order=ordered(key,higher),win=order[0];
  const log=$("scale").value==="log",u=relative?"×":unit;
  const vals=active.map(l=>plotted(l,key,higher));
  const cat={...axis("category"),data:active.map(l=>names[l]),inverse:true,axisTick:{show:false},splitLine:{show:false}};
  plot("bars",{xAxis:{...axis(log?"log":"value"),min:log?Math.pow(10,Math.floor(Math.log10(Math.min(...vals)))-1):0,name:u,nameLocation:"middle",nameGap:20},
    grid:{left:8,right:56,top:34,bottom:35,containLabel:true},yAxis:cat,
    tooltip:{...base().tooltip,valueFormatter:v=>quantity(v,u)},
    series:[{id:"bars",name:label,type:"bar",barMaxWidth:27,universalTransition:true,data:active.map((l,i)=>({...legendFormat(l),value:vals[i]})),
      label:{show:true,position:"right",color:css("--ink"),formatter:p=>fmt(p.value,2)},itemStyle:{borderRadius:[0,4,4,0]}}]});
  note("bar-note",names[win]+" lidera en "+label.toLowerCase()+" entre los motores visibles, con "+quantity(value(win,key),unit)+".");
  function jump(l){return value(l,"p999")/value(l,"p50")}
  plot("tails",{xAxis:{...axis("category"),data:pctLabels,boundaryGap:false},yAxis:{...axis(),name:relative?"Factor ×":"µs"},
    tooltip:{...base().tooltip,formatter:ps=>pctLabels[ps[0].dataIndex]+"\n"+ps.map(p=>{const l=active.find(x=>names[x]===p.seriesName);return p.seriesName+"   "+quantity(p.value,relative?"×":"µs")+"   ·   p99.9/p50 "+fmt(jump(l))+"×"}).join("\n")},
    series:active.map(l=>({...legendFormat(l),id:l,type:"line",symbolSize:7,lineStyle:{width:3},areaStyle:{opacity:.045},data:percs.map(k=>plotted(l,k,false))}))});
  const tail=[...active].sort((a,b)=>jump(b)-jump(a))[0];
  note("tail-note","En "+names[tail]+", p99.9 alcanza "+fmt(jump(tail))+"× la mediana. Los percentiles describen la cola sin identificar por sí solos su causa.");
  scatter();radar();
}
function scatter(){
  const points=active.map(l=>[plotted(l,"ram_retained_mb",false),plotted(l,"qps_concurrent",true)]);
  const mx=Math.max(...points.map(p=>p[0]))*1.24,my=Math.max(...points.map(p=>p[1]))*1.3;
  const hx=mx/2,hy=my/2;
  const areas=["RAM baja · QPS bajo","RAM alta · QPS bajo","Óptimo · RAM baja / QPS alto","RAM alta · QPS alto"];
  const ranges=[[0,0,hx,hy],[hx,0,mx,hy],[0,hy,hx,my],[hx,hy,mx,my]];
  if(relative){[ranges[0],ranges[2]]=[ranges[2],ranges[0]];[ranges[1],ranges[3]]=[ranges[3],ranges[1]]}
  plot("scatter",{grid:{left:20,right:16,top:42,bottom:38,containLabel:true},xAxis:{...axis(),max:mx,name:relative?"RAM · factor ×":"RAM retenida (MiB)",nameLocation:"middle",nameGap:24},yAxis:{...axis(),max:my,name:relative?"QPS · factor ×":"QPS mixto"},
    tooltip:{...base().tooltip,trigger:"item",formatter:p=>{const l=active[p.seriesIndex];return names[l]+"\nRAM   "+quantity(value(l,"ram_retained_mb"),"MiB")+"\nCarga mixta   "+quantity(value(l,"qps_concurrent"),"QPS")+"\np99   "+quantity(value(l,"p99"),"µs")}},
    series:active.map((l,i)=>({...legendFormat(l),id:l,type:"scatter",symbolSize:12+24*Math.sqrt(value(l,"p99")/Math.max(...langs.map(k=>value(k,"p99")))),data:[points[i]],
      ...(i===0?{markLine:{silent:true,symbol:"none",label:{show:false},lineStyle:{color:css("--muted"),type:"dashed"},data:[{xAxis:hx},{yAxis:hy}]},
      markArea:{silent:true,label:{position:"insideTopLeft",color:css("--muted"),fontSize:10},data:ranges.map(([x,y,x2,y2],j)=>[{name:areas[j],xAxis:x,yAxis:y,itemStyle:{color:j===2?"rgba(0,173,216,.08)":"transparent"}},{xAxis:x2,yAxis:y2}])}}:{})}))});
  const efficient=[...active].sort((a,b)=>value(b,"qps_concurrent")/value(b,"ram_retained_mb")-value(a,"qps_concurrent")/value(a,"ram_retained_mb"))[0];
  note("scatter-note",names[efficient]+" alcanza "+fmt(value(efficient,"qps_concurrent")/value(efficient,"ram_retained_mb"),1)+" QPS/MiB. La burbuja representa p99; las guías dividen la escala visible por la mitad.");
}
function radar(){
  const labels=["Indexación","QPS mixto","Cola p99","RAM","Concisión"];
  plot("radar",{tooltip:{...base().tooltip,trigger:"item",formatter:p=>p.name+"\n"+labels.map((label,i)=>label+"   "+fmt(p.value[i],1)).join("\n")},
    radar:{center:["50%","55%"],radius:"60%",splitNumber:4,indicator:labels.map(name=>({name,max:100})),axisName:{color:css("--muted")},splitArea:{areaStyle:{color:[css("--panel"),css("--sub")]}},splitLine:{lineStyle:{color:css("--line")}},axisLine:{lineStyle:{color:css("--line")}}},
    series:active.map(l=>({...legendFormat(l),id:l,type:"radar",symbolSize:5,areaStyle:{opacity:.09},data:[{name:names[l],value:axes.map(([k])=>data.results[l].normalized_scores[k])}]}))});
  note("radar-note","Cada eje conserva al líder de los tres motores en 100. Los filtros permiten comparar dos perfiles con la misma referencia.");
}
function code(){
  $("modules").replaceChildren(...Object.entries(modules).map(([k,v])=>button(v,k===moduleKey,()=>{moduleKey=k;code();$("modules").children[Object.keys(modules).indexOf(k)].focus()})));
  const sample=data.code_samples[moduleKey];
  $("samples").replaceChildren(...active.map(l=>{
    const text=sample[l].trimEnd(),box=el("article","snippet"),head=el("header");
    head.append(tag(l),el("small","",text.split("\n").length+" líneas"));const pre=el("pre"),block=el("code","language-"+l,text);
    pre.append(block);box.append(head,pre);if(window.hljs&&hljs.getLanguage(l))hljs.highlightElement(block);return box
  }));
  note("code-note",sample.insight+" "+(moduleKey==="concurrency"?"El mayor throughput visible es "+quantity(value(ordered("qps_concurrent",true)[0],"qps_concurrent"),"QPS")+".":moduleKey==="bm25_ranking"?"El ranking se verificó con "+data.parity_check.queries_tested+" consultas doradas.":"El parseo forma parte del tiempo de búsqueda secuencial."));
}
function simulator(){
  $("profiles").replaceChildren(...Object.entries(profiles).map(([p,ws])=>button(p,p===profile,()=>{profile=p;weights=[...ws];simulator();$("profiles").children[Object.keys(profiles).indexOf(p)].focus()})));
  $("sliders").replaceChildren(...axes.map((axis,i)=>{
    const label=el("label","slider"),out=el("output","",weights[i]),input=el("input");input.type="range";input.min=0;input.max=10;input.value=weights[i];input.setAttribute("aria-label",axis[1]);
    label.append(el("span","",axis[1]),out,input);
    input.oninput=()=>{weights[i]=+input.value;out.value=input.value;profile="";for(const b of $("profiles").children)b.setAttribute("aria-pressed","false");rank()};return label
  }));rank();
}
function rank(){
  const sum=weights.reduce((a,b)=>a+b,0);
  const rows=active.map(l=>({l,score:sum?axes.reduce((n,[k],i)=>n+weights[i]*data.results[l].normalized_scores[k],0)/sum:0})).sort((a,b)=>b.score-a.score);
  const target=$("ranking");
  for(const row of [...target.children])if(!active.includes(row.dataset.lang))row.remove();
  for(const {l,score} of rows){
    let row=target.querySelector('[data-lang="'+l+'"]');
    if(!row){row=el("div","rank");row.dataset.lang=l;const head=el("div","rankhead"),track=el("div","track");head.append(tag(l),el("small"));track.append(el("i"));row.append(head,track)}
    row.querySelector("small").textContent=sum?fmt(score,2)+" / 100":"—";
    row.querySelector("i").style.setProperty("--color",colors[l]);row.querySelector("i").style.width=score+"%";target.append(row);
  }
  if(!sum){$("reason").textContent="Asigna al menos un peso para calcular el ranking.";return}
  const winner=rows[0],ties=rows.filter(r=>Math.abs(r.score-winner.score)<1e-8).map(r=>r.l);
  if(ties.length>1){$("reason").textContent="Empate ponderado entre "+nameList(ties)+".";return}
  if(rows.length===1){$("reason").textContent="Puntuación del único motor seleccionado.";return}
  const advantages=axes.map(([k,label],i)=>({label,delta:weights[i]*(data.results[winner.l].normalized_scores[k]-data.results[rows[1].l].normalized_scores[k])/sum})).sort((a,b)=>b.delta-a.delta);
  $("reason").textContent=names[winner.l]+" supera a "+names[rows[1].l]+" por "+fmt(winner.score-rows[1].score)+" puntos. La mayor contribución a esa ventaja viene de "+advantages[0].label.toLowerCase()+" ("+fmt(advantages[0].delta)+" puntos).";
}
function findings(){
  const items=[["Capacidad bajo carga","qps_concurrent",true,"QPS"],["Latencia de cola","p99",false,"µs"],["Heap retenido","ram_retained_mb",false,"MiB"]];
  $("findings").replaceChildren(...items.map(([title,key,higher,unit])=>{
    const order=ordered(key,higher),l=order[0],other=order.at(-1),card=el("article","finding");
    card.append(el("b","",title),el("p","",names[l]+" registra "+quantity(value(l,key),unit)+(other!==l?" frente a "+quantity(value(other,key),unit)+" de "+names[other]+".":".")));return card
  }));
  const useCases=["Reindexación frecuente de catálogos","Buscador de comercio electrónico con alta demanda","Búsqueda interactiva con objetivo de latencia","Servicio con presupuesto de RAM limitado","Equipo que prioriza concisión del código"];
  $("matrix").replaceChildren(...active.map(l=>{
    const r=data.results[l],sorted=axes.map(([k,,key,unit],i)=>({i,key,unit,s:r.normalized_scores[k]})).sort((a,b)=>b.s-a.s);
    const best=sorted[0],weak=sorted.at(-1),row=el("tr"),name=el("td");
    name.append(tag(l));row.append(name,el("td","",useCases[best.i]),el("td","",axes[best.i][1]+" · "+quantity(value(l,best.key),best.unit)+". "+axes[weak.i][1]+" · "+fmt(weak.s,1)+"/100 en su eje de menor puntuación."));return row
  }));
}
const library=(url)=>new Promise((resolve,reject)=>{
  const script=document.createElement("script");script.src=url;script.onload=resolve;script.onerror=()=>reject(Error("No se pudo cargar "+url));document.head.append(script)
});
async function dependency(local,remote) {
  if(location.protocol==="file:")return library(local);
  try{await Promise.race([library(remote),new Promise((_,reject)=>setTimeout(()=>reject(Error("Timeout")),2400))])}
  catch{await library(local)}
}
async function fileLoad(file) {
  if(!file)return;const ticket=++generation;
  try{const d=JSON.parse(await file.text());if(ticket===generation)activate(d,file.name)}
  catch(err){if(ticket===generation)$("status").textContent="No se pudo importar el archivo. "+err.message}
  $("file").value="";
}
$("upload").onclick=()=>$("file").click();
$("file").onchange=e=>fileLoad(e.target.files[0]);
let depth=0;
window.addEventListener("dragenter",e=>{if(e.dataTransfer.types.includes("Files")){e.preventDefault();depth++;$("drop").hidden=false}});
window.addEventListener("dragleave",()=>{if(--depth<=0){depth=0;$("drop").hidden=true}});
window.addEventListener("dragover",e=>{if(e.dataTransfer.types.includes("Files"))e.preventDefault()});
window.addEventListener("drop",e=>{e.preventDefault();depth=0;$("drop").hidden=true;fileLoad(e.dataTransfer.files[0])});
$("mode").onclick=e=>{if(!e.target.dataset.mode)return;relative=e.target.dataset.mode==="relative";render()};
$("scale").onchange=drawCharts;
$("theme").onclick=()=>{const light=document.body.classList.toggle("light");$("theme").textContent=light?"☾ Tema oscuro":"☼ Tema claro";if(data?.parity_check.passed)drawCharts()};
window.addEventListener("beforeprint",()=>{charts.forEach(c=>c.resize())});
window.addEventListener("afterprint",()=>{charts.forEach(c=>c.resize())});
async function start(){
  const initial=generation;
  try{
    await Promise.all([
      dependency("report/vendor/echarts.min.js","https://cdn.jsdelivr.net/npm/echarts@5.6.0/dist/echarts.min.js"),
      dependency("report/vendor/highlight.min.js","https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11.11.1/build/highlight.min.js").then(()=>dependency("report/vendor/scala.min.js","https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11.11.1/build/languages/scala.min.js"))
    ]);
    let d=window.BENCHMARK_DATA;
    if(location.protocol!=="file:"){try{const res=await fetch("benchmark/results.json",{cache:"no-store"});if(res.ok)d=await res.json()}catch{/* Local wrapper remains available. */}}
    if(initial===generation)activate(d,"");else if(data)render();
    if(!reduced&&window.IntersectionObserver){
      const obs=new IntersectionObserver(entries=>entries.forEach(({isIntersecting,target})=>{if(isIntersecting){target.classList.add("reveal");obs.unobserve(target)}}));
      document.querySelectorAll(".panel").forEach(node=>obs.observe(node))
    }
  }catch(err){$("status").textContent="No se pudo abrir el reporte. "+err.message}
}
start();
})();
