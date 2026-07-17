import { useRef, useCallback, useState } from "react";

/**
 * 图片上传 hook — 管理上传状态、预览和描述文本
 */
export function useImageUpload(onError?: (msg: string) => void) {
  const [imageUploading, setImageUploading] = useState(false);
  const [imagePreview, setImagePreview] = useState<string | null>(null);
  const imageDescriptionRef = useRef<string | null>(null);

  const handleImageUpload = useCallback(async (file: File, uploadFn: (file: File) => Promise<{ imageUrl: string; description: string }>) => {
    const reader = new FileReader();
    reader.onload = (e) => {
      setImagePreview(e.target?.result as string);
    };
    reader.readAsDataURL(file);
    setImageUploading(true);
    setImagePreview(null);

    try {
      const result = await uploadFn(file);
      imageDescriptionRef.current = result.description;
      setImageUploading(false);
      setImagePreview(result.imageUrl || URL.createObjectURL(file));
    } catch {
      onError?.("图片处理失败，请重试");
      setImageUploading(false);
      setImagePreview(null);
      imageDescriptionRef.current = null;
    }
  }, [onError]);

  const clearImageUpload = useCallback(() => {
    imageDescriptionRef.current = null;
    setImagePreview(null);
    setImageUploading(false);
  }, []);

  return {
    imageUploading,
    imagePreview,
    imageDescriptionRef,
    handleImageUpload,
    clearImageUpload,
  };
}
