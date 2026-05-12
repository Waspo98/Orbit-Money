export const BRAND_ASSET_VERSION = '0.76.0-20260512a';

export function brandAsset(path) {
  return `${path}?v=${BRAND_ASSET_VERSION}`;
}

export const APP_ICON_192 = brandAsset('/icon-192.png');
export const APP_ICON_512 = brandAsset('/icon-512.png');
