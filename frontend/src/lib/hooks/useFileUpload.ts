import { useRef, useCallback, useState } from "react";

/**
 * 文件上传 hook —— 工具调用路径专用。
 * 仅上传到 S3 并保存 URL，不做识别。
 */
export function useFileUpload(onError?: (msg: string) => void) {
  const [fileUploading, setFileUploading] = useState(false);
  const [fileName, setFileName] = useState<string | null>(null);
  const fileUrlRef = useRef<string | null>(null);

  const handleFileUpload = useCallback(async (
    file: File,
    uploadFn: (file: File) => Promise<{ fileUrl: string; fileName: string }>
  ) => {
    setFileUploading(true);
    setFileName(file.name);

    try {
      const result = await uploadFn(file);
      fileUrlRef.current = result.fileUrl;
      setFileName(result.fileName || file.name);
      setFileUploading(false);
    } catch {
      onError?.("文件上传失败，请重试");
      setFileUploading(false);
      setFileName(null);
      fileUrlRef.current = null;
    }
  }, [onError]);

  const clearFileUpload = useCallback(() => {
    fileUrlRef.current = null;
    setFileName(null);
    setFileUploading(false);
  }, []);

  return {
    fileUploading,
    fileName,
    fileUrlRef,
    handleFileUpload,
    clearFileUpload,
  };
}
