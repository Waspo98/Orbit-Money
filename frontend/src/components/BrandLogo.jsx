import wordmarkBlack from '../assets/brand/wordmark-black.svg';
import wordmarkCharcoal from '../assets/brand/wordmark-charcoal.svg';
import wordmarkGreen from '../assets/brand/wordmark-green.svg';
import wordmarkWhite from '../assets/brand/wordmark-white.svg';

const WORDMARKS = {
  autoLight: wordmarkCharcoal,
  autoDark: wordmarkWhite,
  black: wordmarkBlack,
  charcoal: wordmarkCharcoal,
  green: wordmarkGreen,
  white: wordmarkWhite
};

export default function BrandLogo({ tone = 'auto', className = '' }) {
  const classes = ['brand-logo', `brand-logo-${tone}`, className]
    .filter(Boolean)
    .join(' ');

  if (tone === 'auto') {
    return (
      <span className={classes} role="img" aria-label="Orbit Money">
        <img className="brand-logo-img brand-logo-img-light" src={WORDMARKS.autoLight} alt="" />
        <img className="brand-logo-img brand-logo-img-dark" src={WORDMARKS.autoDark} alt="" />
      </span>
    );
  }

  return (
    <span className={classes} role="img" aria-label="Orbit Money">
      <img className="brand-logo-img" src={WORDMARKS[tone] || WORDMARKS.autoLight} alt="" />
    </span>
  );
}
