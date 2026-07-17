/**
 * AI Chat API 端点常量
 * 集中管理所有后端 API 路径，避免魔法字符串
 */
; (function (global) {
  'use strict';

  var API = {
    // 认证
    AUTH: {
      LOGIN: '/api/auth/login',
      REGISTER: '/api/auth/register',
      ME: '/api/auth/me',
      USER_INFO: '/api/auth/user/',        // + userId
      SEND_RESET_CODE: '/api/auth/send-reset-code',
      RESET_PASSWORD: '/api/auth/reset-password',
      CHANGE_PASSWORD: '/api/auth/change-password',
      UPDATE_SIGNATURE: '/api/auth/signature',
      UPLOAD_AVATAR: '/api/auth/avatar'
    },

    // 聊天
    CHAT: {
      SEND: '/api/chat/send',
      CONVERSATIONS: '/api/conversations',
      CONVERSATION: '/api/conversations/',  // + id
      MESSAGES: '/api/conversations/',       // + id/messages
      DELETE_MESSAGE: '/api/messages/'       // + messageId
    },

    // 提示词
    PROMPT: {
      LIST: '/api/prompts',
      CREATE: '/api/prompts',
      UPDATE: '/api/prompts/',              // + id
      DELETE: '/api/prompts/',              // + id
      USE: '/api/prompts/'                  // + id/use
    },

    // 提示词社区
    PROMPTS_HUB: {
      LIST: '/api/prompts-hub',
      DETAIL: '/api/prompts-hub/',          // + id
      LIKE: '/api/prompts-hub/',            // + id/like
      UPLOAD: '/api/prompts-hub/upload-with-image',
      COMMENTS: '/api/prompts-hub/',        // + id/comments
      COMMENT_LIKE: '/api/prompts-hub/comments/',  // + id/like
      COMMENT_DELETE: '/api/prompts-hub/comments/' // + id
    },

    // 记忆
    MEMORY: {
      LIST: '/api/memory/list',
      ENABLED: '/api/memory/enabled',
      ADD: '/api/memory/add',
      UPDATE: '/api/memory/',               // + id
      DELETE: '/api/memory/',               // + id
      TOGGLE: '/api/memory/',               // + id/toggle?enabled=
      SEARCH: '/api/memory/search',
      CLEAR: '/api/memory/clear'
    },

    // 知识库
    KB: {
      LIST: '/api/kb/list',
      CREATE: '/api/kb/create',
      UPDATE: '/api/kb/',                   // + id
      DELETE: '/api/kb/',                   // + id
      DOCS: '/api/kb/',                     // + id/docs
      DOC_UPLOAD: '/api/kb/',               // + id/docs/upload
      DOC_DELETE: '/api/kb/docs/',          // + docId
      DOC_REINDEX: '/api/kb/docs/'          // + docId/reindex
    },

    // 模型配置
    MODEL: {
      LIST: '/api/model-configs',
      CONFIG: '/api/model-configs/'         // + configId
    },

    // 好友
    FRIEND: {
      LIST: '/api/friends',
      SEARCH: '/api/friends/search',
      SEND_REQUEST: '/api/friends/request',
      HANDLE_REQUEST: '/api/friends/request/',  // + requestId
      REQUESTS: '/api/friends/requests',
      MESSAGES: '/api/friends/messages/',
      SEND_MESSAGE: '/api/friends/messages',
      MARK_READ: '/api/friends/messages/read/',
    },

    // 图片
    IMAGE: {
      UPLOAD: '/api/image/upload',
      DESCRIBE: '/api/image/describe'
    },

    // 通知
    NOTIFICATION: {
      LIST: '/api/notifications',
      UNREAD_COUNT: '/api/notifications/unread-count',
      MARK_READ: '/api/notifications/',    // + id/read
      MARK_ALL_READ: '/api/notifications/read-all'
    },

    // 赞助
    SPONSOR: {
      CREATE: '/api/billing/sponsor',
      HISTORY: '/api/billing/orders',
      BALANCE: '/api/billing/balance',
      USAGE: '/api/billing/usage',
      CHECKIN: '/api/billing/checkin',
      CHECKIN_STATUS: '/api/billing/checkin-status'
    },

    // 管理后台
    ADMIN: {
      LOGIN: '/api/admin/login',
      DASHBOARD: '/api/admin/dashboard',
      USERS: '/api/admin/users',
      USER_BALANCE: '/api/admin/users/',   // + userId/balance
      USER_ROLE: '/api/admin/users/',      // + userId/role
      USER_STATUS: '/api/admin/users/',    // + userId/status
      SPONSORS: '/api/admin/sponsors',
      SPONSOR_APPROVE: '/api/admin/sponsors/',  // + id/approve
      SPONSOR_REJECT: '/api/admin/sponsors/',   // + id/reject
      MODELS: '/api/admin/model-configs',
      MODEL_SAVE: '/api/admin/model-configs',
      MODEL_DELETE: '/api/admin/model-configs/',  // + id
      PROMPTS: '/api/admin/prompts-hub',
      PROMPT_TOGGLE_FEATURED: '/api/admin/prompts-hub/', // + id/featured
      PROMPT_DELETE: '/api/admin/prompts-hub/',  // + id
      USAGE: '/api/admin/usage',
      CONVERSATIONS: '/api/admin/conversations'
    }
  };

  global.ChatAPI = API;

})(window);
