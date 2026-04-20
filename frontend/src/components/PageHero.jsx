import { useEffect, useRef, useState } from 'react';

export function useMorphingPageHero(initialHeight = 420, options = {}) {
  const innerRef = useRef(null);
  const titleRef = useRef(null);
  const expandedRef = useRef(initialHeight);
  const {
    collapsedHeight = 64,
    collapsedTitleTop = null
  } = options;
  const [state, setState] = useState({
    progress: 0,
    height: initialHeight,
    expandedHeight: initialHeight,
    titleX: 0,
    titleY: 0,
    titleScale: 0.62,
    collapsedWidth: 232
  });
  const frameRef = useRef(null);

  useEffect(() => {
    const collapsed = collapsedHeight;
    const titleScale = 0.62;

    function measureTitleTarget() {
      const title = titleRef.current;
      const hero = innerRef.current?.closest('.page-hero');
      if (!title || !hero) {
        return { titleX: 0, titleY: 0, titleScale, collapsedWidth: 232 };
      }

      const previousTransform = title.style.transform;
      title.style.transform = 'none';
      const heroRect = hero.getBoundingClientRect();
      const titleRect = title.getBoundingClientRect();
      title.style.transform = previousTransform;

      const edgeLeft = window.innerWidth >= 1080 ? 260 : 0;
      const availableWidth = window.innerWidth - edgeLeft;
      const preferredWidth = Math.min(Math.max(availableWidth / 3, 232), 520);
      const collapsedWidth = Math.min(
        availableWidth - 28,
        Math.max(preferredWidth, Math.ceil(titleRect.width * titleScale + 60))
      );
      const targetLeft = (collapsedWidth - titleRect.width * titleScale) / 2;
      const targetTop =
        collapsedTitleTop == null
          ? (collapsed - titleRect.height * titleScale) / 2
          : collapsedTitleTop;
      const currentLeft = titleRect.left - heroRect.left;
      const currentTop = titleRect.top - heroRect.top;

      return {
        titleX: targetLeft - currentLeft,
        titleY: targetTop - currentTop,
        titleScale,
        collapsedWidth
      };
    }

    function measureExpandedHeight() {
      const inner = innerRef.current;
      const hero = inner?.closest('.page-hero');
      if (!inner || !hero) return expandedRef.current;

      const styles = window.getComputedStyle(hero);
      const paddingTop = parseFloat(styles.paddingTop) || 0;
      const innerStyles = window.getComputedStyle(inner);
      const gap = parseFloat(innerStyles.rowGap || innerStyles.gap) || 0;
      const visibleChildren = Array.from(inner.children).filter((child) => {
        return window.getComputedStyle(child).display !== 'none';
      });
      const contentHeight = visibleChildren.reduce((total, child, index) => {
        return total + child.getBoundingClientRect().height + (index > 0 ? gap : 0);
      }, 0);
      const bottomCushion = window.innerWidth <= 560 ? 30 : 34;
      const measured = Math.ceil(paddingTop + contentHeight + bottomCushion);

      expandedRef.current = Math.max(collapsed, measured);
      return expandedRef.current;
    }

    function update() {
      if (frameRef.current) return;

      frameRef.current = window.requestAnimationFrame(() => {
        frameRef.current = null;
        const expanded = measureExpandedHeight();
        const titleTarget = measureTitleTarget();
        const collapseDistance = Math.max(1, expanded - collapsed);
        const progress = Math.min(1, Math.max(0, window.scrollY / collapseDistance));
        const height = Math.round(expanded - (expanded - collapsed) * progress);

        setState((prev) =>
          Math.abs(prev.progress - progress) > 0.01 ||
          prev.height !== height ||
          prev.expandedHeight !== expanded ||
          Math.abs(prev.titleX - titleTarget.titleX) > 0.5 ||
          Math.abs(prev.titleY - titleTarget.titleY) > 0.5 ||
          Math.abs(prev.collapsedWidth - titleTarget.collapsedWidth) > 0.5
            ? { progress, height, expandedHeight: expanded, ...titleTarget }
            : prev
        );
      });
    }

    update();
    const observer =
      'ResizeObserver' in window && innerRef.current
        ? new ResizeObserver(update)
        : null;
    if (observer && innerRef.current) observer.observe(innerRef.current);
    window.addEventListener('scroll', update, { passive: true });
    window.addEventListener('resize', update);
    return () => {
      if (observer) observer.disconnect();
      window.removeEventListener('scroll', update);
      window.removeEventListener('resize', update);
      if (frameRef.current) window.cancelAnimationFrame(frameRef.current);
    };
  }, [initialHeight, collapsedHeight, collapsedTitleTop]);

  return { ...state, innerRef, titleRef };
}

export default function PageHero({
  id,
  variant,
  kicker,
  title,
  subtitle,
  stats = [],
  statsExtra,
  initialHeight = 420,
  collapsedHeight = 64,
  collapsedTitleTop,
  hero: controlledHero,
  chrome,
  toolbar,
  collapsedContent,
  onOpenMenu,
  statLabel
}) {
  const staticInnerRef = useRef(null);
  const staticTitleRef = useRef(null);
  const hero = controlledHero || {
    progress: 0,
    height: initialHeight,
    expandedHeight: initialHeight,
    titleX: 0,
    titleY: 0,
    titleScale: 1,
    collapsedWidth: 0,
    innerRef: staticInnerRef,
    titleRef: staticTitleRef
  };
  const chromeContent = typeof chrome === 'function' ? chrome(hero) : chrome;
  const toolbarContent = typeof toolbar === 'function' ? toolbar(hero) : toolbar;
  // Archived collapsed-pill support. This is intentionally not rendered while
  // headers are normal scrolling cards, but kept nearby in case we bring the
  // pill back later.
  // const collapsedSlot =
  //   typeof collapsedContent === 'function'
  //     ? collapsedContent(hero)
  //     : collapsedContent;

  return (
    <>
      <section
        className={`page-hero page-hero-${variant}`}
        aria-labelledby={id}
        style={{
          '--hero-progress': hero.progress,
          '--hero-content-opacity': Math.max(0, 1 - hero.progress * 1.35),
          '--hero-title-x': `${hero.titleX}px`,
          '--hero-title-y': `${hero.titleY}px`,
          '--hero-title-scale': hero.titleScale,
          '--hero-collapsed-width': `${hero.collapsedWidth}px`
        }}
      >
        {onOpenMenu && (
          <div className="page-hero-pill-bar" hidden>
            <span className="page-hero-pill-title" aria-hidden="true">{title}</span>
            <button
              type="button"
              className="btn-icon page-hero-pill-menu"
              onClick={onOpenMenu}
              aria-label="Open menu"
              disabled={hero.progress < 0.72}
            >
              {'\u2630'}
            </button>
          </div>
        )}
        <div className="page-hero-inner" ref={hero.innerRef}>
          {chromeContent}
          <div className="page-hero-content">
            <div className="page-hero-topline">
              <div className="page-hero-main">
                <div className="page-kicker">{kicker}</div>
                <h2 id={id} ref={hero.titleRef}>{title}</h2>
                {/*
                  Archived collapsed search/pill slot:
                  {collapsedSlot && (
                    <div
                      className="page-hero-collapsed-slot"
                      style={{ pointerEvents: hero.progress > 0.82 ? 'auto' : 'none' }}
                    >
                      {collapsedSlot}
                    </div>
                  )}
                */}
                <p>{subtitle}</p>
              </div>

              <div className="page-hero-stats" aria-label={statLabel || `${title} summary`}>
                {stats.map((stat) => (
                  <div key={stat.label} className={`page-hero-stat ${stat.tone || ''}`}>
                    <span>{stat.label}</span>
                    <strong>{stat.value}</strong>
                  </div>
                ))}
                {statsExtra}
              </div>
            </div>
            {toolbarContent}
          </div>
        </div>
      </section>
      {/*
        Archived fixed-hero spacer. Static headers live in normal document flow.
        <div
          className={`page-hero-spacer page-hero-${variant}-spacer`}
          style={{ height: `${hero.expandedHeight}px` }}
          aria-hidden="true"
        />
      */}
    </>
  );
}
