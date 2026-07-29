【充值/赞助功能开发计划 V1.0】
===================================

一、需求概述
-----------------------------------
对接 tokenPlan 计费系统，将现有的"模拟充值"改造为"赞助+人工审核"模式。

核心需求：
1. 将前端"充值"按钮替换为"赞助"按钮
2. 点击"赞助"按钮弹出模态框：
   - 左侧展示个人微信收款码（uploads/Storepic/weixinPic.png）
   - 右侧展示上传截图按钮，用户可将赞助凭证上传到 uploads/upStorepic/
3. 实际 token 发放由人工审核（管理员后台审核后手动增加余额）
4. 后续在后台管理页面实现审核发放功能

二、与 tokenPlan 的对接关系
-----------------------------------
【已有功能（保持不动）】
- User.balance：余额字段 ✓
- TokenUsage：消费记录 ✓
- RechargeOrder：充值订单（订单号生成逻辑保留） ✓
- GET /api/billing/balance：查询余额 ✓
- GET /api/billing/usage-records：消费记录 ✓
- POST /api/billing/recharge：模拟充值接口 ⚠️ 需关闭/移除

【需要改造的点】
- 前端充值模态框 → 赞助模态框
- 取消直接到账的模拟充值，改为"赞助凭证上传"
- 新增赞助凭证上传接口

三、后端接口设计
-----------------------------------
【3.1 关闭模拟充值接口】
将 BillingController 中的 POST /api/billing/recharge 移除或标记为 @Deprecated，
因为不再允许前端直接到账，所有充值需经过人工审核。

【3.2 新增赞助凭证上传接口】
POST /api/billing/sponsor-upload
  请求：multipart/form-data
    - image: MultipartFile（赞助截图，支持 png/jpg/jpeg/gif，最大 5MB）
  返回：{ "success": true, "filePath": "/uploads/upStorepic/xxx.png", "message": "上传成功，请等待审核" }
  权限：已登录用户
  说明：
    - 文件保存到 ./uploads/upStorepic/ 目录
    - 使用 UUID 重命名避免文件名冲突
    - 前端上传后显示"上传成功，请等待管理员审核"提示

四、前端页面改造
-----------------------------------
【4.1 按钮替换】
位置：src/main/resources/templates/index.html → 钱包 tab
  原：<button id="btnRecharge" class="btn-primary recharge-btn">💳 充值</button>
  改：<button id="btnSponsor" class="btn-primary sponsor-btn">💝 赞助</button>

【4.2 赞助模态框】
替换原充值模态框（id="rechargeModal"），新建赞助模态框（id="sponsorModal"）：

<div class="modal-overlay" id="sponsorModal">
  <div class="modal" style="max-width: 600px;">
    <h2>💝 赞助支持</h2>
    <p style="color:#6b7280; font-size:13px; text-align:center; margin-bottom:16px;">
      请扫描下方收款码进行赞助，完成后上传赞助截图，管理员审核后将为您发放对应 Token。
    </p>
    <div class="sponsor-layout">
      <!-- 左侧：收款码 -->
      <div class="sponsor-left">
        <h4>📱 微信收款码</h4>
        <img src="/uploads/Storepic/weixinPic.png" alt="收款码" class="sponsor-qrcode">
        <p style="font-size:12px; color:#9ca3af; margin-top:8px;">请使用微信扫描二维码</p>
      </div>
      <!-- 右侧：上传截图 -->
      <div class="sponsor-right">
        <h4>📎 上传赞助截图</h4>
        <div class="upload-area" id="sponsorUploadArea">
          <div class="upload-placeholder" id="sponsorUploadPlaceholder">
            <span style="font-size:36px;">📷</span>
            <p>点击或拖拽上传赞助截图</p>
            <p style="font-size:12px; color:#9ca3af;">支持 PNG / JPG / GIF，最大 5MB</p>
          </div>
          <input type="file" id="sponsorFileInput" accept="image/png,image/jpeg,image/jpg,image/gif" style="display:none;">
          <img id="sponsorPreview" style="display:none; max-width:100%; max-height:200px; border-radius:8px;">
        </div>
        <button class="btn-primary" id="btnSponsorUpload" style="margin-top:12px;">📤 确认上传</button>
        <div id="sponsorError" class="error-msg"></div>
        <div id="sponsorSuccess" class="success-msg" style="display:none;"></div>
      </div>
    </div>
    <div style="text-align:center; margin-top:16px;">
      <a id="closeSponsorModalBtn" style="color:#6b7280; cursor:pointer; font-size:14px;">关闭</a>
    </div>
  </div>
</div>

【4.3 前端 JS 改造】
文件：src/main/resources/static/app.js

新增/修改函数：
- 移除：rechargeModal、btnRechargeConfirm 相关逻辑
- 新增：sponsorModal 相关变量和事件绑定
- 新增：showSponsorModal() → 展示赞助模态框
- 新增：赞助截图预览功能（file input change 事件）
- 新增：doSponsorUpload() → POST /api/billing/sponsor-upload
- 修改：btnRecharge 监听 → btnSponsor 监听

五、后端实现细节
-----------------------------------
【5.1 BillingController 新增方法】
@PostMapping("/sponsor-upload")
public ResponseEntity<Map<String, Object>> sponsorUpload(
        @RequestParam("image") MultipartFile image,
        Authentication authentication)

实现：
- 校验文件类型（Content-Type 检查）
- 限制文件大小（利用 Spring 默认 max-file-size=5MB）
- 生成唯一文件名（UUID + 原始扩展名）
- 保存到 uploads/upStorepic/ 目录
- 返回文件路径和成功信息

【5.2 SecurityConfig 路径放行】
/uploads/** 已放行，上传目录无需额外配置。

【5.3 application.properties 补充】
无需额外配置，现有 upload.dir 和 multipart 配置已满足需求。

六、样式设计
-----------------------------------
文件：src/main/resources/static/app.css

新增样式：
.sponsor-layout：双栏 Flex 布局
.sponsor-left / .sponsor-right：左右各占 50%
.sponsor-qrcode：收款码图片（最大宽度 200px，居中，圆角阴影）
.upload-area：上传区域（虚线边框，拖拽提示）
.upload-placeholder：上传占位图
.success-msg：成功提示（绿色背景）

七、开发步骤
-----------------------------------
Step 1. 后端：BillingController 新增 /api/billing/sponsor-upload 接口
Step 2. 前端 HTML：替换充值按钮 + 新建赞助模态框
Step 3. 前端 JS：替换充值逻辑 + 新增赞助上传逻辑
Step 4. 前端 CSS：新增赞助相关样式
Step 5. 测试：上传赞助截图 → 验证文件保存到 uploads/upStorepic/

八、与未来后台管理系统的对接预留
-----------------------------------
- 管理员后台审核赞助凭证 → 确认后通过 BillingService.recharge() 发放 Token
- RechargeOrder 实体中 payChannel 字段已有 "MANUAL" 枚举值预留
- 上传的截图文件路径可在 RechargeOrder 中新增字段关联（或单独建表 SponsorRecord）
- 可在 RechargeOrder 增加字段：
    - String sponsorImagePath  // 赞助截图路径
    - String reviewStatus       // 审核状态：PENDING / APPROVED / REJECTED
    - String reviewComment      // 审核备注

九、关键注意事项
-----------------------------------
【9.1 安全防护】
- 上传文件必须校验 MIME 类型，防止恶意文件
- 文件名使用 UUID 重命名，防止路径遍历攻击
- uploads/ 目录配置为仅静态资源访问，不执行脚本

【9.2 用户体验】
- 上传后立即显示缩略图预览
- 上传成功后显示明确提示"请等待管理员审核"
- 收款码图片不存在时显示占位提示
- 模态框关闭后清除已选文件/预览状态

【9.3 文件存储】
- 确保 uploads/upStorepic/ 目录存在（通过 File.mkdirs() 自动创建）
- 定期清理未审核的过期凭证（后续优化）
