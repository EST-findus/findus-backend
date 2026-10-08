"use strict";

// 인증 값은 메모리에서만 관리합니다. 브라우저 저장소에는 보관하지 않습니다.
const state = { access: null, loggedOutAccess: null, busy: false, email: null, registeredEmail: null };
const $ = id => document.getElementById(id);
const actions = {
  signup: { label: "회원가입", path: "/api/members", method: "POST", expected: 201 },
  login: { label: "로그인", path: "/api/auth/login", method: "POST", expected: 200, csrf: true },
  me: { label: "내 정보 조회", path: "/api/members/me", method: "GET", expected: 200 },
  refresh: { label: "토큰 재발급", path: "/api/auth/refresh", method: "POST", expected: 200, csrf: true },
  logout: { label: "로그아웃", path: "/api/auth/logout", method: "POST", expected: 204, csrf: true },
  blocked: { label: "로그아웃 후 접근 확인", path: "/api/members/me", method: "GET", expected: 401 },
  duplicate: { label: "중복 가입 확인", path: "/api/members", method: "POST", expected: 409 },
  invalid: { label: "짧은 비밀번호 확인", path: "/api/members", method: "POST", expected: 400 },
  wrongPassword: { label: "틀린 비밀번호 확인", path: "/api/auth/login", method: "POST", expected: 401, csrf: true },
  csrfMissing: { label: "보안 토큰 누락 확인", path: "/api/auth/refresh", method: "POST", expected: 403 }
};

function account() {
  return { email: $("email").value, password: $("password").value, name: $("name").value, nickname: $("nickname").value };
}

function newAccount() {
  state.registeredEmail = null;
  $("email").value = `test-${crypto.randomUUID()}@example.com`;
  $("password").value = "Local-test123!";
  $("name").value = "테스트회원";
  $("nickname").value = "찾음이";
  updateButtons();
}

function updateButtons() {
  document.querySelectorAll("[data-action]").forEach(button => {
    const action = button.dataset.action;
    button.disabled = state.busy || (["me", "refresh", "logout"].includes(action) && !state.access)
      || (action === "blocked" && (!state.loggedOutAccess || Boolean(state.access)))
      || (["duplicate", "wrongPassword"].includes(action) && state.registeredEmail !== $("email").value.trim().toLowerCase());
  });
  $("new-account").disabled = state.busy || Boolean(state.access);
  $("new-account").title = state.access ? "로그아웃한 뒤 새 계정을 준비해 주세요." : "새로운 이메일을 준비합니다.";
  document.querySelectorAll("input").forEach(input => { input.disabled = state.busy; });
  $("session-status").textContent = state.access ? "🟢 로그인 중" : "⚪ 로그인 전";
  $("session-status").title = state.email || "로그인하면 다음 버튼이 활성화됩니다.";
}

async function csrfHeaders() {
  // 로그인·재발급·로그아웃마다 최신 쿠키와 검증 토큰을 준비합니다.
  const response = await fetch("/api/auth/csrf", { credentials: "same-origin", cache: "no-store" });
  if (!response.ok) throw new Error("요청 검증 토큰을 준비하지 못했습니다.");
  const body = await response.json();
  return { [body.headerName]: body.token };
}

function requestBody(action) {
  const data = account();
  if (["signup", "duplicate"].includes(action)) return data;
  if (action === "invalid") return { ...data, password: "short" };
  if (action === "login") return { email: data.email, password: data.password };
  if (action === "wrongPassword") return { email: data.email, password: crypto.randomUUID() };
  return undefined;
}

function showResult(action, status, body) {
  const config = actions[action];
  const passed = status === config.expected;
  $("http-status").textContent = `HTTP ${status}`;
  $("http-status").className = "http-status " + (passed ? "success" : "error");
  $("result-summary").textContent = passed
    ? (config.expected >= 400 ? `✅ ${config.label}: 예상대로 요청이 차단됐어요.` : `✅ ${config.label}: 성공했어요.`)
    : `⚠️ ${config.label}: 예상한 결과와 달라요. 아래 응답을 확인해 주세요.`;
  $("request-path").textContent = `${config.method} ${config.path} · 예상 HTTP ${config.expected}`;
  const visibleBody = body === null ? { 안내: "응답 본문 없음 · 정상 처리됐습니다." } : { ...body };
  if (visibleBody.accessToken) visibleBody.accessToken = "[발급 완료 · 화면 내부에서 자동 관리]";
  // 서버에서 받은 값은 HTML로 해석하지 않고 텍스트로 표시합니다.
  $("response-json").textContent = JSON.stringify(visibleBody, null, 2);
  const history = $("history");
  history.querySelector(".empty-history")?.remove();
  const row = document.createElement("li");
  const label = document.createElement("span");
  label.textContent = `${passed ? "✅" : "⚠️"} ${config.label} · ${status}`;
  const time = document.createElement("time");
  time.textContent = new Date().toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" });
  row.append(label, time);
  history.prepend(row);
  while (history.children.length > 8) history.lastElementChild.remove();
}

async function run(action) {
  if (state.busy) return;
  state.busy = true;
  updateButtons();
  $("result-summary").textContent = "⏳ 서버에 요청하고 있어요…";
  const config = actions[action];
  try {
    const headers = config.csrf ? await csrfHeaders() : {};
    const body = requestBody(action);
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (action === "me") headers.Authorization = `Bearer ${state.access}`;
    if (action === "blocked") headers.Authorization = `Bearer ${state.loggedOutAccess}`;
    const response = await fetch(config.path, {
      method: config.method, credentials: "same-origin", cache: "no-store", headers,
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    const text = await response.text();
    const result = text ? JSON.parse(text) : null;
    if (action === "signup" && [201, 409].includes(response.status)) state.registeredEmail = account().email.trim().toLowerCase();
    if (response.ok && ["login", "refresh"].includes(action)) {
      state.access = result.accessToken;
      if (action === "login") { state.email = account().email.trim().toLowerCase(); state.registeredEmail = state.email; state.loggedOutAccess = null; }
    }
    if (response.ok && action === "logout") {
      state.loggedOutAccess = state.access;
      state.access = null;
      state.email = null;
    }
    if (response.status === 401 && ["me", "refresh"].includes(action)) {
      state.access = null;
      state.email = null;
    }
    showResult(action, response.status, result);
  } catch (error) {
    $("http-status").textContent = "연결 확인 필요";
    $("http-status").className = "http-status error";
    $("result-summary").textContent = "⚠️ 요청을 완료하지 못했어요. 서버 실행 상태를 확인해 주세요.";
    $("response-json").textContent = JSON.stringify({ 안내: error.message }, null, 2);
  } finally {
    state.busy = false;
    updateButtons();
  }
}

document.querySelectorAll("[data-action]").forEach(button => {
  button.addEventListener("click", () => run(button.dataset.action));
});
$("new-account").addEventListener("click", newAccount);
$("email").addEventListener("input", updateButtons);
$("clear-history").addEventListener("click", () => {
  $("history").replaceChildren();
  const empty = document.createElement("li");
  empty.className = "empty-history";
  empty.textContent = "기록을 지웠어요. 회원 데이터와 로그인 상태는 유지됩니다.";
  $("history").append(empty);
});
newAccount();
updateButtons();
