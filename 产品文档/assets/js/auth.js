/* 登录鉴权：登录成功后进入首页（家庭概览），退出后回到登录页 */
(function (global) {
  'use strict';

  var S_KEY = 'fa-auth-session';   // 本次会话登录态
  var R_KEY = 'fa-auth-remember';  // 7 天免登录
  var USERS = { admin: '123456', family: 'family123' };  // 演示账号

  var loginScreen = document.getElementById('loginScreen');
  var appShell = document.getElementById('appShell');
  var form = document.getElementById('loginForm');
  var userInput = document.getElementById('loginUser');
  var passInput = document.getElementById('loginPass');
  var toggleBtn = document.getElementById('togglePass');
  var rememberBox = document.getElementById('rememberMe');
  var errorBox = document.getElementById('loginError');
  var loginBtn = document.getElementById('loginBtn');
  var logoutBtn = document.getElementById('logoutBtn');
  var forgotLink = document.getElementById('forgotLink');

  /* ---- 登录态 ---- */
  function rememberUser() {
    try {
      var raw = localStorage.getItem(R_KEY);
      if (!raw) return null;
      var obj = JSON.parse(raw);
      if (obj && obj.user && obj.exp && Date.now() < obj.exp) return obj.user;
      localStorage.removeItem(R_KEY);
    } catch (e) { /* 解析失败按未记住处理 */ }
    return null;
  }

  function authed() {
    return sessionStorage.getItem(S_KEY) || rememberUser();
  }

  function showApp(user) {
    loginScreen.hidden = true;
    appShell.hidden = false;
    var nameEl = document.getElementById('memberName');
    if (nameEl) nameEl.textContent = user;
  }

  function showLogin() {
    loginScreen.hidden = false;
    appShell.hidden = true;
    if (userInput) userInput.focus();
  }

  function showError(msg) {
    errorBox.textContent = msg;
    errorBox.hidden = false;
  }

  function clearError() {
    errorBox.hidden = true;
    errorBox.textContent = '';
  }

  /* ---- 交互 ---- */
  if (toggleBtn) {
    toggleBtn.addEventListener('click', function () {
      var show = passInput.type === 'password';
      passInput.type = show ? 'text' : 'password';
      toggleBtn.textContent = show ? '隐藏' : '显示';
      passInput.focus();
    });
  }

  [userInput, passInput].forEach(function (input) {
    if (input) input.addEventListener('input', clearError);
  });

  if (form) {
    form.addEventListener('submit', function (e) {
      e.preventDefault();
      var user = (userInput.value || '').trim();
      var pass = passInput.value || '';

      if (!user) { showError('请输入账号'); userInput.focus(); return; }
      if (!pass) { showError('请输入密码'); passInput.focus(); return; }
      if (pass.length < 6) { showError('密码至少 6 位'); passInput.focus(); return; }

      // 登录中状态
      clearError();
      loginBtn.disabled = true;
      loginBtn.textContent = '登录中…';

      setTimeout(function () {
        if (USERS[user] && USERS[user] === pass) {
          if (rememberBox && rememberBox.checked) {
            localStorage.setItem(R_KEY, JSON.stringify({
              user: user,
              exp: Date.now() + 7 * 24 * 60 * 60 * 1000
            }));
          } else {
            sessionStorage.setItem(S_KEY, user);
          }
          loginBtn.disabled = false;
          loginBtn.textContent = '登录';
          showApp(user);
        } else {
          loginBtn.disabled = false;
          loginBtn.textContent = '登录';
          showError('账号或密码错误，请重试');
          passInput.focus();
          passInput.select();
        }
      }, 500);
    });
  }

  if (logoutBtn) {
    logoutBtn.addEventListener('click', function () {
      sessionStorage.removeItem(S_KEY);
      localStorage.removeItem(R_KEY);
      passInput.value = '';
      showLogin();
    });
  }

  if (forgotLink) {
    forgotLink.addEventListener('click', function () {
      if (global.FA && FA.toast) FA.toast('演示环境暂未开放找回密码', true);
    });
  }

  /* ---- 启动分流 ---- */
  var current = authed();
  if (current) showApp(current);
  else showLogin();
})(window);
