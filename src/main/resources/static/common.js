/**
 * AI Chat 公共工具模块
 * 提供所有页面共用的 API 封装、UI 工具函数、认证管理
 */

; (function (global) {
  'use strict';

  // ======== 认证管理 (支持 localStorage 持久登录 + sessionStorage 临时会话) ========
  const Auth = {
    TOKEN_KEY: 'chat_token',
    USERNAME_KEY: 'chat_username',
    REMEMBER_KEY: 'chat_remember',

    // 判断当前是否使用 localStorage（用户勾选了"记住我"）
    _useLocal: function () {
      return localStorage.getItem(this.REMEMBER_KEY) === 'true';
    },

    // 获取当前应使用的 Storage
    _storage: function () {
      return this._useLocal() ? localStorage : sessionStorage;
    },

    getToken: function () {
      // 优先从当前模式读取，回退到另一个 Storage（兼容登录时双写）
      var token = this._storage().getItem(this.TOKEN_KEY);
      if (token) return token;
      var other = this._useLocal() ? sessionStorage : localStorage;
      return other.getItem(this.TOKEN_KEY) || '';
    },

    setToken: function (token) {
      this._storage().setItem(this.TOKEN_KEY, token);
      // 双写：保证登录后两种模式都能读到 Token
      var other = this._useLocal() ? sessionStorage : localStorage;
      other.setItem(this.TOKEN_KEY, token);
    },

    setRemember: function (remember) {
      if (remember) {
        localStorage.setItem(this.REMEMBER_KEY, 'true');
        // 将 sessionStorage 中的 Token 迁移到 localStorage
        var sessToken = sessionStorage.getItem(this.TOKEN_KEY);
        var sessUser = sessionStorage.getItem(this.USERNAME_KEY);
        if (sessToken) localStorage.setItem(this.TOKEN_KEY, sessToken);
        if (sessUser) localStorage.setItem(this.USERNAME_KEY, sessUser);
      } else {
        localStorage.removeItem(this.REMEMBER_KEY);
      }
    },

    getUsername: function () {
      var name = this._storage().getItem(this.USERNAME_KEY);
      if (name) return name;
      var other = this._useLocal() ? sessionStorage : localStorage;
      return other.getItem(this.USERNAME_KEY) || '';
    },

    setUsername: function (name) {
      this._storage().setItem(this.USERNAME_KEY, name);
      var other = this._useLocal() ? sessionStorage : localStorage;
      other.setItem(this.USERNAME_KEY, name);
    },

    clear: function () {
      // 登出时同时清理两种存储，确保无残留
      localStorage.removeItem(this.TOKEN_KEY);
      localStorage.removeItem(this.USERNAME_KEY);
      localStorage.removeItem(this.REMEMBER_KEY);
      sessionStorage.removeItem(this.TOKEN_KEY);
      sessionStorage.removeItem(this.USERNAME_KEY);
    },

    isLoggedIn: function () {
      return !!this.getToken();
    }
  };

  // ======== API 封装 ========
  /**
   * 创建 API 请求函数
   * @param {Object} options
   * @param {string} options.baseUrl - API 基础路径，默认 ''
   * @param {string} options.tokenKey - localStorage 中 token 的 key，默认 'chat_token'
   * @param {string} options.redirectOn401 - 401 时跳转地址，默认 '/'
   * @returns {Function} api(url, opts) 函数
   */
  function createApi(options) {
    options = options || {};
    var baseUrl = options.baseUrl || '';
    var tokenKey = options.tokenKey || 'chat_token';
    var redirectOn401 = options.redirectOn401 || '/';

    return function (url, opts) {
      opts = opts || {};
      // 统一通过 Auth 模块获取 Token（支持 localStorage/sessionStorage 双模式）
      var token = Auth.getToken();
      var headers = {};
      // 复制自定义 headers
      if (opts.headers) {
        for (var key in opts.headers) {
          if (opts.headers.hasOwnProperty(key)) {
            headers[key] = opts.headers[key];
          }
        }
      }
      headers['Authorization'] = 'Bearer ' + token;

      // 自动序列化 JSON body
      var body = opts.body;
      if (body && typeof body === 'object' && !(body instanceof FormData)) {
        headers['Content-Type'] = 'application/json';
        body = JSON.stringify(body);
      }

      return fetch(baseUrl + url, {
        method: opts.method,
        headers: headers,
        body: body
      }).then(function (res) {
        if (res.status === 401) {
          Auth.clear();
          window.location.href = redirectOn401;
          return null;
        }
        return res;
      });
    };
  }

  // ======== Toast 通知 ========
  var _toastTimer = null;

  /**
   * 显示 Toast 消息
   * @param {string} msg - 消息内容
   * @param {number} duration - 显示时长(ms)，默认 2000
   */
  function showToast(msg, duration) {
    duration = duration || 2000;
    var el = document.getElementById('toast');
    if (!el) return;
    if (_toastTimer) clearTimeout(_toastTimer);
    el.textContent = msg;
    el.className = 'toast show';
    _toastTimer = setTimeout(function () {
      el.className = 'toast';
    }, duration);
  }

  // ======== HTML 安全转义 ========
  /**
   * 转义 HTML 特殊字符（用于 innerHTML）
   */
  function escapeHtml(str) {
    var div = document.createElement('div');
    div.textContent = str || '';
    return div.innerHTML;
  }

  /**
   * 转义用于 HTML 属性值的字符串
   */
  function escapeAttr(str) {
    return (str || '')
      .replace(/'/g, "\\'")
      .replace(/"/g, '&quot;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
  }

  // ======== 格式化工具 ========
  /**
   * 格式化文件大小
   * @param {number} bytes
   * @returns {string}
   */
  function formatSize(bytes) {
    if (!bytes || bytes === 0) return '0 B';
    var units = ['B', 'KB', 'MB', 'GB'];
    var i = 0;
    var size = bytes;
    while (size >= 1024 && i < 3) {
      size /= 1024;
      i++;
    }
    return size.toFixed(1) + ' ' + units[i];
  }

  /**
   * 格式化相对时间
   * @param {string|number|Date} ts
   * @returns {string}
   */
  function timeLabel(ts) {
    if (!ts) return '';
    var d = new Date(ts);
    var now = new Date();
    var diff = now - d;
    var mins = Math.floor(diff / 60000);
    if (mins < 1) return '刚刚';
    if (mins < 60) return mins + '分钟前';
    var hours = Math.floor(mins / 60);
    if (hours < 24) return hours + '小时前';
    var days = Math.floor(hours / 24);
    if (days < 7) return days + '天前';
    return d.toLocaleDateString('zh-CN');
  }

  /**
   * 格式化日期时间
   * @param {string|number|Date} dt
   * @returns {string}
   */
  function formatDateTime(dt) {
    if (!dt) return '-';
    var d = new Date(dt);
    var pad = function (n) { return String(n).padStart(2, '0'); };
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) +
      ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
  }

  /**
   * 格式化日期
   * @param {string|number|Date} dt
   * @returns {string}
   */
  function formatDate(dt) {
    if (!dt) return '-';
    var d = new Date(dt);
    var pad = function (n) { return String(n).padStart(2, '0'); };
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
  }

  // ======== DOM 工具 ========
  /**
   * 显示模态框
   */
  function showModal(id) {
    var el = document.getElementById(id);
    if (el) el.classList.add('show');
  }

  /**
   * 隐藏模态框
   */
  function hideModal(id) {
    var el = document.getElementById(id);
    if (el) el.classList.remove('show');
  }

  /**
   * 为模态框背景点击绑定关闭事件
   */
  function bindModalOverlayClose(modalId) {
    var el = document.getElementById(modalId);
    if (!el) return;
    el.addEventListener('click', function (e) {
      if (e.target === el) {
        el.classList.remove('show');
      }
    });
  }

  // ======== 确认对话框 ========
  /**
   * 显示自定义确认对话框
   * @param {string} message - 提示消息
   * @param {Function} onConfirm - 确认回调
   */
  function confirmDialog(message, onConfirm) {
    if (typeof onConfirm === 'function') {
      if (window.confirm(message)) {
        onConfirm();
      }
    }
  }

  // ======== 导出到全局 ========
  var ChatCommon = {
    Auth: Auth,
    createApi: createApi,
    showToast: showToast,
    escapeHtml: escapeHtml,
    escapeAttr: escapeAttr,
    formatSize: formatSize,
    timeLabel: timeLabel,
    formatDateTime: formatDateTime,
    formatDate: formatDate,
    showModal: showModal,
    hideModal: hideModal,
    bindModalOverlayClose: bindModalOverlayClose,
    confirmDialog: confirmDialog
  };

  global.ChatCommon = ChatCommon;

})(window);
