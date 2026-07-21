import { useRef, useEffect } from "react";
import { Send, Image, Paperclip, StopCircle, Loader2, CheckCircle2, X } from "lucide-react";
import WebSearchToggle from "@/components/shared/WebSearchToggle";

interface InputBarProps {
  value: string;
  onChange: (value: string) => void;
  onSend: () => void;
  onStop?: () => void;
  onImageUpload?: (file: File) => void;
  onClearImage?: () => void;
  onFileUpload?: (file: File) => void;
  onClearFile?: () => void;
  isGenerating?: boolean;
  imageUploading?: boolean;
  imagePreview?: string | null;
  fileUploading?: boolean;
  fileName?: string | null;
  maxLength?: number;
  disabled?: boolean;
  placeholder?: string;
  webSearchEnabled?: boolean;
  onWebSearchChange?: (enabled: boolean) => void;
}

export default function InputBar({
  value,
  onChange,
  onSend,
  onStop,
  onImageUpload,
  onClearImage,
  onFileUpload,
  onClearFile,
  isGenerating = false,
  imageUploading = false,
  imagePreview = null,
  fileUploading = false,
  fileName = null,
  maxLength = 4000,
  disabled = false,
  placeholder = "向 AI 提问，或输入 / 使用指令...",
  webSearchEnabled = false,
  onWebSearchChange,
}: InputBarProps) {
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const docInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = "auto";
      textareaRef.current.style.height = Math.min(textareaRef.current.scrollHeight, 160) + "px";
    }
  }, [value]);

  const isOverLimit = value.length > maxLength;
  const hasContent = value.trim().length > 0;

  const handleImageClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileClick = () => {
    docInputRef.current?.click();
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file && onImageUpload) {
      onImageUpload(file);
    }
    if (fileInputRef.current) fileInputRef.current.value = "";
  };

  const handleDocChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file && onFileUpload) {
      onFileUpload(file);
    }
    if (docInputRef.current) docInputRef.current.value = "";
  };

  return (
    <div className="px-4 py-3 shrink-0 border-t border-border">
      {/* Image preview bar */}
      {(imagePreview || imageUploading) && (
        <div className="max-w-3xl mx-auto mb-2 flex items-center gap-2 px-3 py-2 rounded-xl bg-emerald-50 border border-emerald-200 text-sm text-emerald-800">
          {imageUploading ? (
            <Loader2 size={14} className="animate-spin shrink-0 text-emerald-600" />
          ) : (
            <CheckCircle2 size={14} className="shrink-0 text-emerald-600" />
          )}
          {imagePreview && !imageUploading && (
            <img src={imagePreview} alt="preview" className="w-8 h-8 object-cover rounded-lg border border-emerald-200 shrink-0" />
          )}
          <span className="flex-1 text-xs">
            {imageUploading ? "正在分析图片..." : "图片处理完成，识别结果将随消息发送"}
          </span>
          {onClearImage && (
            <button onClick={onClearImage} className="p-0.5 rounded hover:bg-emerald-100 transition-colors" title="清除图片">
              <X size={14} />
            </button>
          )}
        </div>
      )}

      {/* File upload bar */}
      {(fileName || fileUploading) && (
        <div className="max-w-3xl mx-auto mb-2 flex items-center gap-2 px-3 py-2 rounded-xl bg-blue-50 border border-blue-200 text-sm text-blue-800">
          {fileUploading ? (
            <Loader2 size={14} className="animate-spin shrink-0 text-blue-600" />
          ) : (
            <CheckCircle2 size={14} className="shrink-0 text-blue-600" />
          )}
          <span className="flex-1 text-xs">
            {fileUploading ? "正在上传文件..." : `文件已上传：${fileName}`}
          </span>
          {onClearFile && (
            <button onClick={onClearFile} className="p-0.5 rounded hover:bg-blue-100 transition-colors" title="清除文件">
              <X size={14} />
            </button>
          )}
        </div>
      )}

      <div className="relative max-w-3xl mx-auto rounded-2xl overflow-hidden transition-all duration-200 bg-card border shadow-[0_2px_16px_rgba(0,0,0,0.06)]">
        <textarea
          ref={textareaRef}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey && !disabled) {
              e.preventDefault();
              if (hasContent) onSend();
            }
          }}
          placeholder={disabled ? "请先登录并选择模型配置..." : placeholder}
          rows={1}
          disabled={disabled}
          className="w-full resize-none outline-none bg-transparent text-sm leading-relaxed px-4 pt-3.5 pb-2 text-foreground placeholder:text-muted-foreground/50 disabled:opacity-50"
          style={{
            minHeight: 48,
            maxHeight: 160,
            fontFamily: "inherit",
            caretColor: "hsl(var(--primary))",
          }}
        />
        <div className="flex items-center justify-between px-3 pb-2.5">
          <div className="flex items-center gap-2">
            <span
              className="text-[11px] tabular-nums"
              style={{
                color: isOverLimit ? "#e05454" : "hsl(var(--muted-foreground))",
                fontFamily: "'Geist Mono', monospace",
                opacity: value.length > 0 ? 1 : 0.4,
              }}
            >
              {value.length} / {maxLength}
            </span>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-[10px] hidden sm:block text-muted-foreground/50">
              Shift+Enter 换行
            </span>

            {/* File upload button (工具调用路径) */}
            {onFileUpload && (
              <button
                onClick={handleFileClick}
                disabled={disabled || fileUploading}
                className="flex items-center justify-center w-7 h-7 rounded-lg transition-all duration-150 text-muted-foreground hover:bg-accent hover:text-foreground disabled:opacity-40"
                title="上传文件"
              >
                <Paperclip size={14} />
              </button>
            )}
            <input
              ref={docInputRef}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={handleDocChange}
            />

            {/* Image upload button (旧路径，保留但隐藏) */}
            {onImageUpload && (
              <button
                onClick={handleImageClick}
                disabled={disabled || imageUploading}
                className="hidden"
                title="上传图片"
              >
                <Image size={14} />
              </button>
            )}
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={handleFileChange}
            />

            {/* Web search toggle */}
            {onWebSearchChange && (
              <WebSearchToggle enabled={webSearchEnabled} onChange={onWebSearchChange} />
            )}

            {/* Stop button */}
            {isGenerating && onStop && (
              <button
                onClick={onStop}
                className="flex items-center justify-center w-8 h-8 rounded-xl transition-all duration-150 active:scale-95 bg-destructive text-destructive-foreground"
                title="停止生成"
              >
                <StopCircle size={14} />
              </button>
            )}

            {/* Send button */}
            {!isGenerating && (
              <button
                className={`flex items-center justify-center w-8 h-8 rounded-xl transition-all duration-150 active:scale-95 ${
                  hasContent && !disabled
                    ? "bg-primary text-primary-foreground"
                    : "bg-accent text-muted-foreground"
                }`}
                onClick={() => {
                  if (hasContent && !disabled) onSend();
                }}
                disabled={disabled || !hasContent}
              >
                <Send size={14} />
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
