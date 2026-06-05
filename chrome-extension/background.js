// ─────────────────────────────────────────────────────────────
// 트래커로 간주해 제거할 쿼리 파라미터 (전부 소문자로 비교)
// ─────────────────────────────────────────────────────────────
const TRACKER_PREFIXES = ['utm_', 'pk_', 'mtm_', 'matomo_', 'piwik_', 'hsa_'];

const TRACKER_EXACT = new Set([
  // Google
  'gclid', 'gclsrc', 'dclid', 'gbraid', 'wbraid', 'gad_source', 'gad_campaignid',
  '_gl', 'gcles', 'gbclid', 'srsltid',
  // Meta / Facebook
  'fbclid', 'fb_action_ids', 'fb_action_types', 'fb_source', 'fb_ref',
  // Microsoft / Bing
  'msclkid',
  // Twitter / X
  'twclid', 'ref_src', 'ref_url',
  // TikTok
  'ttclid',
  // Instagram
  'igshid', 'igsh',
  // Mailchimp
  'mc_cid', 'mc_eid',
  // HubSpot
  '_hsenc', '_hsmi', '__hssc', '__hstc', '__hsfp', 'hsctatracking',
  // Yandex
  'yclid', 'ysclid', '_openstat',
  // Marketo
  'mkt_tok',
  // Adobe
  's_cid', 's_kwcid', 'ef_id',
  // Klaviyo
  '_kx',
  // Drip
  '__s',
  // Olytics
  'oly_anon_id', 'oly_enc_id',
  // Vero
  'vero_id', 'vero_conv',
  // Pinterest
  'epik',
  // Snapchat
  'sc_cid',
  // Wicked Reports
  'wickedid',
  // Outbrain / Taboola
  'obclid', 'tblci',
  // MailerLite
  'ml_subscriber', 'ml_subscriber_hash',
  // Sailthru
  'spjobid', 'spreportid', 'spmailingid', 'spuserid',
  // Yahoo
  'guce_referrer', 'guccounter', 'soc_src', 'soc_trk',
  // 뉴스 / 광고 일반
  'cmpid', 'ncid', 'icid', 'wt.mc_id',
]);

function isTracker(key) {
  const k = key.toLowerCase();
  if (TRACKER_EXACT.has(k)) return true;
  return TRACKER_PREFIXES.some((p) => k.startsWith(p));
}

function cleanUrl(rawUrl) {
  try {
    const url = new URL(rawUrl);
    const keys = [...new Set(url.searchParams.keys())];
    let removed = 0;
    for (const key of keys) {
      if (isTracker(key)) {
        url.searchParams.delete(key);
        removed++;
      }
    }
    // 쿼리가 전부 사라지면 toString()이 '?'도 자동으로 떼어냄
    return { url: url.toString(), removed };
  } catch {
    return { url: rawUrl, removed: 0 };
  }
}

// ─────────────────────────────────────────────────────────────
// 따닥(더블 탭) 판정 + 복사 처리
// 단축키와 툴바 아이콘 클릭이 같은 로직을 공유한다.
// ─────────────────────────────────────────────────────────────
let lastTime = 0;
let lastTabId = -1;
const DOUBLE_TAP_MS = 500;

// ─── 디버그: 서비스 워커가 켜질 때 단축키 등록 상태를 출력 ───
console.log('[copy-url] service worker 시작', new Date().toLocaleTimeString());
chrome.commands.getAll((cmds) => {
  console.log('[copy-url] 등록된 commands:', cmds);
  const c = cmds.find((x) => x.name === 'copy-url');
  if (!c) {
    console.warn('[copy-url] ⚠️ copy-url 커맨드 자체가 없음 (manifest 로드 문제?)');
  } else if (!c.shortcut) {
    console.warn('[copy-url] ⚠️ 단축키가 비어 있음 → 충돌로 자동 등록 실패. chrome://extensions/shortcuts 에서 직접 지정 필요');
  } else {
    console.log('[copy-url] ✅ 단축키 등록됨:', c.shortcut);
  }
});

async function handleCopy(tab, source) {
  if (!tab || tab.id == null) {
    console.warn('[copy-url] 탭 정보 없음 → 중단', { source, tab });
    return;
  }

  const now = Date.now();
  const isDouble = now - lastTime < DOUBLE_TAP_MS && tab.id === lastTabId;
  lastTime = now;
  lastTabId = tab.id;
  console.log('[copy-url] handleCopy 진입', { source, tabId: tab.id, url: tab.url, isDouble });

  const fullUrl = tab.url || '';
  let text = fullUrl;
  let label = '전체 URL 복사됨';

  if (isDouble) {
    const { url, removed } = cleanUrl(fullUrl);
    text = url;
    label = removed > 0 ? `트래커 ${removed}개 제거 후 복사됨` : '제거할 트래커 없음 · 복사됨';
  }

  try {
    await chrome.scripting.executeScript({
      target: { tabId: tab.id },
      func: copyAndToast,
      args: [text, label, isDouble],
    });
    console.log('[copy-url] ✅ executeScript 성공 → 클립보드:', text);
  } catch (e) {
    // chrome://, 크롬 웹스토어 등 스크립트 주입이 막힌 페이지
    console.error('[copy-url] ❌ executeScript 실패 (이 페이지에 주입 불가?):', e?.message, e);
  }
}

chrome.commands.onCommand.addListener(async (command) => {
  console.log('[copy-url] onCommand 수신:', command);
  if (command !== 'copy-url') return;
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  await handleCopy(tab, 'shortcut');
});

// 단축키가 다른 단축키와 충돌해 안 먹을 때를 위한 폴백:
// 툴바 아이콘 클릭 = 전체 URL 복사, 빠르게 두 번 클릭 = 트래커 제거
chrome.action.onClicked.addListener((tab) => {
  console.log('[copy-url] 아이콘 클릭');
  handleCopy(tab, 'click');
});

// ─────────────────────────────────────────────────────────────
// 아래 함수는 페이지 컨텍스트에 주입되어 실행됨
// (클로저 변수 접근 불가 — 데이터는 args로만 전달)
// ─────────────────────────────────────────────────────────────
async function copyAndToast(text, label, isDouble) {
  // 1) 클립보드 복사 (실패 시 execCommand 폴백)
  try {
    await navigator.clipboard.writeText(text);
  } catch (_) {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.style.cssText = 'position:fixed;top:0;left:0;opacity:0;pointer-events:none;';
    document.body.appendChild(ta);
    ta.select();
    try { document.execCommand('copy'); } catch (__) {}
    ta.remove();
  }

  // 2) 화면 우상단 토스트
  const ID = '__copy_url_toast__';
  document.getElementById(ID)?.remove();

  const accent = isDouble ? '#34d399' : '#60a5fa';
  const box = document.createElement('div');
  box.id = ID;
  box.style.cssText = [
    'position:fixed', 'z-index:2147483647', 'top:16px', 'right:16px',
    'max-width:420px', 'padding:12px 14px', 'border-radius:10px',
    'background:rgba(20,20,22,0.95)', 'color:#fff', 'box-sizing:border-box',
    'font:13px/1.4 -apple-system,BlinkMacSystemFont,"SF Pro",sans-serif',
    'box-shadow:0 8px 28px rgba(0,0,0,0.35)', `border-left:3px solid ${accent}`,
    'opacity:0', 'transform:translateY(-6px)',
    'transition:opacity .15s ease,transform .15s ease', 'pointer-events:none',
  ].join(';');

  const title = document.createElement('div');
  title.textContent = label;
  title.style.cssText = `color:${accent};font-weight:600;margin-bottom:4px;`;

  const body = document.createElement('div');
  body.textContent = text;
  body.style.cssText = 'word-break:break-all;opacity:0.9;';

  box.appendChild(title);
  box.appendChild(body);
  document.body.appendChild(box);

  requestAnimationFrame(() => {
    box.style.opacity = '1';
    box.style.transform = 'translateY(0)';
  });

  setTimeout(() => {
    box.style.opacity = '0';
    box.style.transform = 'translateY(-6px)';
    setTimeout(() => box.remove(), 200);
  }, 1800);
}
