// Erkundung: Wie lädt ordonline.de/koscherliste ihre Produkte?
import { chromium } from 'playwright';

const URL = 'https://www.ordonline.de/koscherliste/';
const log = (...a) => console.log(...a);

async function tryJson(path) {
  try {
    const r = await fetch('https://www.ordonline.de' + path);
    const t = await r.text();
    log(`\n### GET ${path} -> ${r.status} ${r.headers.get('content-type')}`);
    log(t.slice(0, 3000));
  } catch (e) { log(`### GET ${path} failed`, e.message); }
}

const browser = await chromium.launch();
const page = await browser.newPage();
const seen = [];
page.on('requestfinished', async (req) => {
  const u = req.url();
  if (!u.includes('ordonline.de')) return;
  if (/\.(css|js|woff2?|png|jpe?g|webp|svg|gif)(\?|$)/.test(u)) return;
  const res = await req.response();
  let body = '';
  try { body = (await res.text()).slice(0, 2500); } catch {}
  seen.push({ method: req.method(), url: u, post: (req.postData() || '').slice(0, 2000), status: res.status(), ct: res.headers()['content-type'], body });
});

await page.goto(URL, { waitUntil: 'networkidle', timeout: 90000 });
try { await page.getByRole('button', { name: /akzept|zustimm|alle/i }).first().click({ timeout: 3000 }); } catch {}

const info = await page.evaluate(() => {
  const h3 = [...document.querySelectorAll('h3')].filter(h => h.textContent.trim().length);
  const first = h3[0];
  let item = first;
  for (let i = 0; i < 6 && item && item.parentElement; i++) item = item.parentElement;
  const pag = [...document.querySelectorAll('a,button')].filter(e => /weiter|mehr|next|nächste|load|›|»|^\d+$/i.test(e.textContent.trim()) || /pag|load-more|more/i.test(e.className));
  return {
    h3count: h3.length,
    itemHtml: item ? item.outerHTML.slice(0, 4000) : null,
    pagination: pag.slice(0, 30).map(e => ({ tag: e.tagName, text: e.textContent.trim().slice(0, 40), cls: e.className, href: e.getAttribute('href'), data: Object.fromEntries([...e.attributes].filter(a => a.name.startsWith('data-')).map(a => [a.name, a.value.slice(0, 300)])) })),
    scripts: [...document.querySelectorAll('script:not([src])')].map(s => s.textContent).filter(t => /jet|filter|ajax|nonce|query/i.test(t)).map(t => t.slice(0, 1500)).slice(0, 8),
    filterAttrs: [...document.querySelectorAll('[data-query-id],[data-content-provider],[data-filter-type],[data-smart-filter]')].slice(0, 10).map(e => e.outerHTML.slice(0, 600)),
  };
});
log('H3 count:', info.h3count);
log('\n=== ITEM HTML ===\n', info.itemHtml);
log('\n=== PAGINATION CANDIDATES ===\n', JSON.stringify(info.pagination, null, 1));
log('\n=== INLINE SCRIPTS ===\n', info.scripts.join('\n-----\n'));
log('\n=== FILTER ELEMENTS ===\n', info.filterAttrs.join('\n-----\n'));

// Seitenwechsel/Nachladen auslösen
const before = seen.length;
for (const sel of ['.jet-filters-pagination__item:not(.jet-filters-pagination__current) >> nth=0', 'text=/^2$/', '.page-numbers.next', 'text=/mehr laden|weitere|load more/i']) {
  try { await page.locator(sel).first().click({ timeout: 4000 }); await page.waitForTimeout(4000); log('clicked', sel); break; } catch {}
}
try { await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight)); await page.waitForTimeout(4000); } catch {}
log('\nRequests nach Klick/Scroll:', seen.length - before);

log('\n=== XHR/DOC REQUESTS ===');
for (const s of seen) {
  log(`\n--- ${s.method} ${s.status} ${s.url} [${s.ct}]`);
  if (s.post) log('POST:', s.post);
  if (s.method !== 'GET' || !s.url.endsWith('/koscherliste/')) log('BODY:', s.body);
}
await browser.close();

for (const p of ['/wp-json/', '/wp-json/wp/v2/types', '/wp-json/wp/v2/taxonomies', '/wp-json/wp/v2/koscherliste?per_page=3', '/wp-json/jet-cct/', '/koscherliste/feed/', '/wp-sitemap.xml', '/sitemap_index.xml']) await tryJson(p);
