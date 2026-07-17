/**
 * AiChat 登录/注册/重置密码页面逻辑
 * 依赖于 common.js (ChatCommon.Auth, ChatCommon.createApi) 和 api-paths.js (ChatAPI)
 */
;(function () {
  'use strict';

  // 如果已登录，直接跳转回首页
  if (ChatCommon.Auth.isLoggedIn()) {
    window.location.href = '/';
    return;
  }

  // baseUrl 为空，api-paths.js 中的路径已是完整路径
  var api = ChatCommon.createApi({ baseUrl: '', tokenKey: 'chat_token', redirectOn401: '/' });

  // ======== DOM ========
  var loginPanel = document.getElementById('loginFormPanel');
  var registerPanel = document.getElementById('registerFormPanel');
  var resetPanel = document.getElementById('resetFormPanel');

  // ======== 面板切换 ========
  function showPanel(panel) {
    loginPanel.style.display = 'none';
    registerPanel.style.display = 'none';
    resetPanel.style.display = 'none';
    panel.style.display = '';
  }

  document.getElementById('toRegisterLink').addEventListener('click', function(e) {
    e.preventDefault();
    showPanel(registerPanel);
  });
  document.getElementById('toLoginFromReg').addEventListener('click', function(e) {
    e.preventDefault();
    showPanel(loginPanel);
  });
  document.getElementById('toForgotLink').addEventListener('click', function(e) {
    e.preventDefault();
    showPanel(resetPanel);
  });
  document.getElementById('backToLogin').addEventListener('click', function(e) {
    e.preventDefault();
    showPanel(loginPanel);
  });

  // ======== 登录 ========
  document.getElementById('loginSubmitBtn').addEventListener('click', function (e) {
    e.preventDefault();
    var username = document.getElementById('loginUsername').value.trim();
    var password = document.getElementById('loginPassword').value;
    var errorEl = document.getElementById('loginError');

    errorEl.textContent = '';
    if (!username || !password) { errorEl.textContent = '请填写用户名/邮箱和密码'; return; }

    var btn = this;
    btn.disabled = true;
    btn.textContent = '登录中...';

    // 传对象（非字符串），让 createApi 自动设置 Content-Type 并序列化
    api(ChatAPI.AUTH.LOGIN, {
      method: 'POST',
      body: { username: username, password: password },
    })
      .then(function (res) {
        if (!res) throw new Error('认证失败，请刷新页面后重试');
        if (!res.ok) {
          return res.json().then(function (err) {
            throw new Error(err.message || '用户名或密码错误');
          }).catch(function () {
            throw new Error('登录失败 (' + res.status + ')');
          });
        }
        return res.json();
      })
      .then(function (data) {
        ChatCommon.Auth.setToken(data.token);
        ChatCommon.Auth.setUsername(data.username);
        window.location.href = '/';
      })
      .catch(function (err) {
        errorEl.textContent = err.message || '登录失败，请检查用户名和密码';
        btn.disabled = false;
        btn.textContent = '登录';
      });
  });

  // Enter 键登录
  document.getElementById('loginPassword').addEventListener('keydown', function(e) {
    if (e.key === 'Enter') {
      e.preventDefault();
      document.getElementById('loginSubmitBtn').click();
    }
  });
  document.getElementById('loginUsername').addEventListener('keydown', function(e) {
    if (e.key === 'Enter') {
      e.preventDefault();
      document.getElementById('loginSubmitBtn').click();
    }
  });

  // ======== 注册 ========
  document.getElementById('regSendCodeBtn').addEventListener('click', function (e) {
    e.preventDefault();
    var email = document.getElementById('regEmail').value.trim();
    var errorEl = document.getElementById('regError');
    errorEl.textContent = '';
    if (!email) { errorEl.textContent = '请填写邮箱'; return; }

    var btn = this;
    btn.disabled = true;
    var countdown = 60;
    btn.textContent = countdown + 's';

    api('/api/auth/send-code', {
      method: 'POST',
      body: { email: email },
    })
      .then(function (res) {
        if (!res || !res.ok) {
          return (res ? res.json().catch(function() { return {}; }) : Promise.resolve({}))
            .then(function (err) { throw new Error(err.message || '发送失败'); });
        }
      })
      .then(function () {
        var timer = setInterval(function () {
          countdown--;
          btn.textContent = countdown + 's';
          if (countdown <= 0) {
            clearInterval(timer);
            btn.disabled = false;
            btn.textContent = '发送验证码';
          }
        }, 1000);
      })
      .catch(function (err) {
        errorEl.textContent = err.message || '发送失败';
        btn.disabled = false;
        btn.textContent = '发送验证码';
      });
  });

  document.getElementById('regSubmitBtn').addEventListener('click', function (e) {
    e.preventDefault();
    var username = document.getElementById('regUsername').value.trim();
    var email = document.getElementById('regEmail').value.trim();
    var password = document.getElementById('regPassword').value;
    var code = document.getElementById('regCode').value.trim();
    var errorEl = document.getElementById('regError');

    errorEl.textContent = '';
    if (!username) { errorEl.textContent = '请填写用户名'; return; }
    if (!email) { errorEl.textContent = '请填写邮箱'; return; }
    if (password.length < 6) { errorEl.textContent = '密码至少6位'; return; }
    if (!code) { errorEl.textContent = '请填写验证码'; return; }

    var btn = this;
    btn.disabled = true;
    btn.textContent = '注册中...';

    api(ChatAPI.AUTH.REGISTER, {
      method: 'POST',
      body: { username: username, email: email, password: password, code: code },
    })
      .then(function (res) {
        if (!res) throw new Error('注册失败');
        if (!res.ok) {
          return res.json().then(function (err) { throw new Error(err.message || '注册失败'); });
        }
        return res.json();
      })
      .then(function (data) {
        ChatCommon.Auth.setToken(data.token);
        ChatCommon.Auth.setUsername(data.username);
        window.location.href = '/';
      })
      .catch(function (err) {
        errorEl.textContent = err.message || '注册失败';
        btn.disabled = false;
        btn.textContent = '注册';
      });
  });

  // ======== 重置密码 ========
  document.getElementById('resetSendCodeBtn').addEventListener('click', function (e) {
    e.preventDefault();
    var username = document.getElementById('resetUsername').value.trim();
    var errorEl = document.getElementById('resetError');
    errorEl.textContent = '';
    if (!username) { errorEl.textContent = '请填写用户名或邮箱'; return; }

    var btn = this;
    btn.disabled = true;
    var countdown = 60;
    btn.textContent = countdown + 's';

    api(ChatAPI.AUTH.SEND_RESET_CODE, {
      method: 'POST',
      body: { username: username },
    })
      .then(function (res) {
        if (!res || !res.ok) {
          return (res ? res.json().catch(function() { return {}; }) : Promise.resolve({}))
            .then(function (err) { throw new Error(err.message || '发送失败'); });
        }
      })
      .then(function () {
        var timer = setInterval(function () {
          countdown--;
          btn.textContent = countdown + 's';
          if (countdown <= 0) {
            clearInterval(timer);
            btn.disabled = false;
            btn.textContent = '发送验证码';
          }
        }, 1000);
      })
      .catch(function (err) {
        errorEl.textContent = err.message || '发送失败';
        btn.disabled = false;
        btn.textContent = '发送验证码';
      });
  });

  document.getElementById('resetSubmitBtn').addEventListener('click', function (e) {
    e.preventDefault();
    var username = document.getElementById('resetUsername').value.trim();
    var code = document.getElementById('resetCode').value.trim();
    var newPassword = document.getElementById('resetNewPassword').value;
    var errorEl = document.getElementById('resetError');

    errorEl.textContent = '';
    if (!username) { errorEl.textContent = '请填写用户名或邮箱'; return; }
    if (!code) { errorEl.textContent = '请填写验证码'; return; }
    if (newPassword.length < 6) { errorEl.textContent = '新密码至少6位'; return; }

    var btn = this;
    btn.disabled = true;
    btn.textContent = '重置中...';

    api(ChatAPI.AUTH.RESET_PASSWORD, {
      method: 'POST',
      body: { username: username, code: code, newPassword: newPassword },
    })
      .then(function (res) {
        if (!res) throw new Error('重置失败');
        if (!res.ok) {
          return res.json().then(function (err) { throw new Error(err.message || '重置失败'); });
        }
        return res.json();
      })
      .then(function () {
        ChatCommon.showToast ? ChatCommon.showToast('密码重置成功，请使用新密码登录') : alert('密码重置成功，请使用新密码登录');
        showPanel(loginPanel);
      })
      .catch(function (err) {
        errorEl.textContent = err.message || '重置失败';
        btn.disabled = false;
        btn.textContent = '重置密码';
      });
  });

})();
