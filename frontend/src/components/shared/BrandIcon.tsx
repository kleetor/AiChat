import hanaChatLogo from "/HanaChat.png";

/**
 * HanaChat 品牌图标组件
 */
interface BrandIconProps {
  /** 图标尺寸 (px) */
  size?: number;
  /** 额外 CSS class */
  className?: string;
}

const BRAND_GRADIENT = "linear-gradient(135deg, #d4839a 0%, #e8a3b5 100%)";

export default function BrandIcon({ size = 26, className = "" }: BrandIconProps) {
  return (
    <img
      src={hanaChatLogo}
      alt="HanaChat"
      className={`rounded-lg object-cover ${className}`}
      style={{ width: size, height: size }}
    />
  );
}

export { BRAND_GRADIENT };
