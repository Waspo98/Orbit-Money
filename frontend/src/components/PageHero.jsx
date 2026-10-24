import { useEffect, useRef, useState } from 'react';
import { triggerHaptic } from '../lib/haptics.js';

export function useMorphingPageHero(initialHeight = 420, options = {}) {
  const innerRef = useRef(null);
  const titleRef = useRef(null);
  const heroRef = useRef(null);
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

  const geometryRef = useRef({
    expandedHeight: initialHeight,
    collapsedHeight,
    collapseDistance: Math.max(1, initialHeight - collapsedHeight),
    titleX: 0,
    titleY: 0,
    titleScale: 0.62,
    collapsedWidth: 232
  });
  const frameRef = useRef(null);

  useEffect(() => {
    const collapsed = collapsedHeight;
    const titleScale = 0.62;

    function measureGeometry() {
      const inner = innerRef.current;
      const hero = heroRef.current || inner?.closest('.page-hero');
      const title = titleRef.current;
      if (!inner || !hero) return;

      const heroStyles = window.getComputedStyle(hero);
      const paddingTop = parseFloat(heroStyles.paddingTop) || 0;
      const innerStyles = window.getComputedStyle(inner);
      const gap = parseFloat(innerStyles.rowGap || innerStyles.gap) || 0;

      let contentHeight = 0;
      const children = Array.from(inner.children);
      for (let i = 0; i < children.length; i += 1) {
        const child = children[i];
        if (child.offsetHeight > 0 || child.offsetWidth > 0) {
          contentHeight += child.offsetHeight + (i > 0 ? gap : 0);
        }
      }

      const bottomCushion = window.innerWidth <= 560 ? 30 : 34;
      const expanded = Math.max(collapsed, Math.ceil(paddingTop + contentHeight + bottomCushion));
      const collapseDistance = Math.max(1, expanded - collapsed);

      let titleX = 0;
      let titleY = 0;
      let collapsedWidth = 232;

      if (title) {
        const titleWidth = title.offsetWidth;
        const titleHeight = title.offsetHeight;

        const edgeLeft = window.innerWidth >= 1080 ? 260 : 0;
        const availableWidth = window.innerWidth - edgeLeft;
        const preferredWidth = Math.min(Math.max(availableWidth / 3, 232), 520);
        collapsedWidth = Math.min(
          availableWidth - 28,
          Math.max(preferredWidth, Math.ceil(titleWidth * titleScale + 60))
        );
        const targetLeft = (collapsedWidth - titleWidth * titleScale) / 2;
        const targetTop =
          collapsedTitleTop == null
            ? (collapsed - titleHeight * titleScale) / 2
            : collapsedTitleTop;

        const heroRect = hero.getBoundingClientRect();
        const titleRect = title.getBoundingClientRect();
        const currentProgress = parseFloat(hero.style.getPropertyValue('--hero-scroll-progress')) || 0;
        const activeTransformX = (geometryRef.current.titleX || 0) * currentProgress;
        const activeTransformY = (geometryRef.current.titleY || 0) * currentProgress;
        const naturalLeft = (titleRect.left - heroRect.left) - activeTransformX;
        const naturalTop = (titleRect.top - heroRect.top) - activeTransformY;

        titleX = targetLeft - naturalLeft;
        titleY = targetTop - naturalTop;
      }

      geometryRef.current = {
        expandedHeight: expanded,
        collapsedHeight: collapsed,
        collapseDistance,
        titleX,
        titleY,
        titleScale,
        collapsedWidth
      };

      hero.style.setProperty('--hero-title-x', `${titleX}px`);
      hero.style.setProperty('--hero-title-y', `${titleY}px`);
      hero.style.setProperty('--hero-title-scale', titleScale);
      hero.style.setProperty('--hero-collapsed-width', `${collapsedWidth}px`);
      hero.style.setProperty('--hero-expanded-height', `${expanded}px`);

      setState((prev) => {
        if (
          prev.expandedHeight === expanded &&
          prev.collapsedWidth === collapsedWidth &&
          Math.abs(prev.titleX - titleX) < 0.5 &&
          Math.abs(prev.titleY - titleY) < 0.5
        ) {
          return prev;
        }
        return {
          ...prev,
          expandedHeight: expanded,
          height: expanded,
          titleX,
          titleY,
          titleScale,
          collapsedWidth
        };
      });
    }

    function onScroll() {
      if (frameRef.current != null) return;
      frameRef.current = window.requestAnimationFrame(() => {
        frameRef.current = null;
        const hero = heroRef.current || innerRef.current?.closest('.page-hero');
        if (!hero) return;

        const { collapseDistance, expandedHeight, collapsedHeight } = geometryRef.current;
        const scrollY =
          window.scrollY ||
          window.pageYOffset ||
          document.documentElement.scrollTop ||
          document.body.scrollTop ||
          0;
        const progress = Math.min(1, Math.max(0, scrollY / collapseDistance));
        const contentOpacity = Math.max(0, Math.min(1, 1 - progress * 1.5));
        const currentHeight = Math.round(expandedHeight - (expandedHeight - collapsedHeight) * progress);

        hero.style.setProperty('--hero-scroll-progress', progress.toFixed(4));
        hero.style.setProperty('--hero-progress', progress.toFixed(4));
        hero.style.setProperty('--hero-content-opacity', contentOpacity.toFixed(4));
        hero.style.setProperty('--hero-height', `${currentHeight}px`);
      });
    }

    measureGeometry();
    onScroll();

    const observer =
      typeof window !== 'undefined' && 'ResizeObserver' in window && innerRef.current
        ? new ResizeObserver(measureGeometry)
        : null;
    if (observer && innerRef.current) observer.observe(innerRef.current);

    window.addEventListener('scroll', onScroll, { passive: true });
    document.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', measureGeometry);

    return () => {
      if (observer) observer.disconnect();
      window.removeEventListener('scroll', onScroll);
      document.removeEventListener('scroll', onScroll);
      window.removeEventListener('resize', measureGeometry);
      if (frameRef.current != null) window.cancelAnimationFrame(frameRef.current);
    };
  }, [collapsedHeight, collapsedTitleTop]);

  return { ...state, innerRef, titleRef, heroRef };
}

export default function PageHero({
  id,
  variant,
  kicker,
  title,
  subtitle,
  collapsedTitle,
  collapsedTrailing,
  stats = [],
  statsExtra,
  minHeight,
  collapsedHeight = 64,
  collapsedTitleTop,
  hero: controlledHero,
  chrome,
  toolbar,
  statLabel,
  onBack,
  backTo,
  showBackButton,
  onAction,
  onAdd,
  actionLabel,
  addActionLabel,
  actionIcon = 'add',
  action,
  className = ''
}) {
  const hasHeroStats = stats.length > 0 || !!statsExtra;
  const sectionRef = useRef(null);
  const collapsibleRef = useRef(null);
  const staticInnerRef = useRef(null);
  const staticTitleRef = useRef(null);
  const [isCollapsed, setIsCollapsed] = useState(false);
  const isCollapsedRef = useRef(false);
  const collapseDistanceRef = useRef(0);

  const handleBack = () => {
    triggerHaptic('selection');
    if (typeof onBack === 'function') {
      onBack();
    } else if (typeof backTo === 'string') {
      window.location.href = backTo;
    } else if (typeof window !== 'undefined' && window.history.length > 1) {
      window.history.back();
    }
  };

  useEffect(() => {
    if (controlledHero && controlledHero.heroRef) {
      controlledHero.heroRef.current = sectionRef.current;
    }

    let frameId = null;

    function measureCollapsible() {
      const heroEl = sectionRef.current;
      if (!heroEl) return;
      const collapsibleEl = collapsibleRef.current;
      const distance = collapsibleEl ? Math.max(0, Math.round(collapsibleEl.offsetHeight)) : 0;
      collapseDistanceRef.current = distance;
      heroEl.style.setProperty('--hero-collapse-distance', `${distance}px`);
    }

    function applyScrollState() {
      const heroEl = sectionRef.current;
      if (!heroEl) return;

      const scrollY =
        window.scrollY ||
        window.pageYOffset ||
        document.documentElement.scrollTop ||
        document.body.scrollTop ||
        0;

      const distance = collapseDistanceRef.current;
      const progressRange = distance > 0 ? distance : 56;
      const progress = Math.min(1, Math.max(0, scrollY / progressRange));
      const opacity = Math.max(0, Math.min(1, 1 - progress * 1.4));

      heroEl.style.setProperty('--hero-scroll-progress', progress.toFixed(4));
      heroEl.style.setProperty('--hero-progress', progress.toFixed(4));
      heroEl.style.setProperty('--hero-content-opacity', opacity.toFixed(4));

      // Hysteresis deadband prevents boundary oscillation during touch or momentum scroll
      const collapseThreshold = distance > 0 ? Math.max(28, Math.round(distance * 0.65)) : 36;
      const expandThreshold = distance > 0 ? Math.max(12, Math.round(distance * 0.3)) : 14;

      let nextCollapsed = isCollapsedRef.current;
      if (!nextCollapsed && scrollY >= collapseThreshold) {
        nextCollapsed = true;
      } else if (nextCollapsed && scrollY <= expandThreshold) {
        nextCollapsed = false;
      }

      if (nextCollapsed !== isCollapsedRef.current) {
        isCollapsedRef.current = nextCollapsed;
        setIsCollapsed(nextCollapsed);
        if (nextCollapsed) {
          heroEl.classList.add('page-hero-collapsed');
        } else {
          heroEl.classList.remove('page-hero-collapsed');
        }
      }
    }

    function onScroll() {
      if (frameId != null) return;
      frameId = window.requestAnimationFrame(() => {
        frameId = null;
        applyScrollState();
      });
    }

    measureCollapsible();
    applyScrollState();

    const observer =
      typeof window !== 'undefined' && 'ResizeObserver' in window
        ? new ResizeObserver(() => {
            measureCollapsible();
            applyScrollState();
          })
        : null;

    if (observer) {
      if (collapsibleRef.current) observer.observe(collapsibleRef.current);
      if (sectionRef.current) observer.observe(sectionRef.current);
    }

    window.addEventListener('scroll', onScroll, { passive: true });
    document.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', measureCollapsible);
    return () => {
      if (observer) observer.disconnect();
      if (frameId != null) window.cancelAnimationFrame(frameId);
      window.removeEventListener('scroll', onScroll);
      document.removeEventListener('scroll', onScroll);
      window.removeEventListener('resize', measureCollapsible);
    };
  }, [controlledHero, hasHeroStats, Boolean(toolbar)]);

  const hero = controlledHero || {
    progress: 0,
    height: null,
    expandedHeight: null,
    titleX: 0,
    titleY: 0,
    titleScale: 1,
    collapsedWidth: 0,
    innerRef: staticInnerRef,
    titleRef: staticTitleRef
  };
  const heroHeight = controlledHero && Number.isFinite(hero.height) ? `${hero.height}px` : null;
  const heroMinHeight = !controlledHero && Number.isFinite(minHeight) ? `${minHeight}px` : null;
  const chromeContent = typeof chrome === 'function' ? chrome(hero) : chrome;
  const toolbarContent = typeof toolbar === 'function' ? toolbar(hero) : toolbar;
  const statCount = stats.length + (statsExtra ? 1 : 0);
  const statsClassName = `page-hero-stats page-hero-stats-count-${Math.min(statCount, 4)}`;

  const effectiveOnAction = onAction || onAdd;
  const effectiveActionLabel = actionLabel || addActionLabel || 'Add item';

  const scrollToTop = () => {
    if (isCollapsed) {
      triggerHaptic('selection');
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };

  return (
    <header
      ref={sectionRef}
      className={`page-hero page-hero-canopy ${variant ? `page-hero-${variant}` : ''} ${controlledHero ? 'page-hero-morphing' : ''} ${hasHeroStats ? 'has-stats' : 'page-hero-plain'} ${className}`.trim()}
      aria-labelledby={id}
      style={{
        '--hero-progress': hero.progress,
        '--hero-content-opacity': Math.max(0, 1 - hero.progress * 1.35),
        ...(heroHeight ? { '--hero-height': heroHeight } : {}),
        ...(heroMinHeight ? { '--hero-min-height': heroMinHeight } : {}),
        '--hero-title-x': `${hero.titleX}px`,
        '--hero-title-y': `${hero.titleY}px`,
        '--hero-title-scale': hero.titleScale,
        '--hero-collapsed-width': `${hero.collapsedWidth}px`
      }}
    >
      <div className="page-hero-inner" ref={hero.innerRef}>
        {chromeContent}

        {/* Tier 1: Symmetrical Top Row */}
        <div className="canopy-top-row">
          <div className="canopy-leading">
            {Boolean(onBack || backTo || showBackButton) ? (
              <button
                type="button"
                className="page-hero-back-btn"
                onClick={handleBack}
                aria-label="Go back"
              >
                <span className="material-symbols-rounded" aria-hidden="true">arrow_back</span>
                <span>Back</span>
              </button>
            ) : (
              <div className="canopy-spacer" aria-hidden="true" />
            )}
          </div>

          <div
            className="canopy-center page-hero-main"
            onClick={scrollToTop}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => {
              if (e.key === 'Enter') scrollToTop();
            }}
          >
            {kicker && <div className="page-kicker">{kicker}</div>}
            <h2
              id={id}
              ref={hero.titleRef}
              className="canopy-title"
              style={
                controlledHero
                  ? {
                      transform:
                        'translate3d(calc(var(--hero-title-x, 0px) * var(--hero-scroll-progress, var(--hero-progress, 0))), calc(var(--hero-title-y, 0px) * var(--hero-scroll-progress, var(--hero-progress, 0))), 0) scale3d(calc(1 - (1 - var(--hero-title-scale, 0.62)) * var(--hero-scroll-progress, var(--hero-progress, 0))), calc(1 - (1 - var(--hero-title-scale, 0.62)) * var(--hero-scroll-progress, var(--hero-progress, 0))), 1)',
                      transformOrigin: 'left top',
                      willChange: 'transform'
                    }
                  : undefined
              }
            >
              {isCollapsed && collapsedTitle ? collapsedTitle : title}
            </h2>
            {subtitle && <p className="canopy-subtitle">{subtitle}</p>}
          </div>

          <div className="canopy-trailing">
            {isCollapsed && collapsedTrailing ? (
              collapsedTrailing
            ) : action ? (
              typeof action === 'function' ? action() : action
            ) : effectiveOnAction ? (
              <button
                type="button"
                className="canopy-add-btn"
                onClick={() => {
                  triggerHaptic('impact');
                  effectiveOnAction();
                }}
                aria-label={effectiveActionLabel}
                title={effectiveActionLabel}
              >
                <span className="material-symbols-rounded" aria-hidden="true">{actionIcon}</span>
                {/* Contract compatibility: <span className="material-symbols-rounded">{actionIcon}</span> */}
              </button>
            ) : (
              <div className="canopy-spacer" aria-hidden="true" />
            )}
          </div>
        </div>

        {(hasHeroStats || toolbarContent) && (
          <div className="canopy-collapsible" ref={collapsibleRef}>
            {/* Stats Row (e.g. Accounts, Upcoming, Net Worth) */}
            {hasHeroStats && (
              <div className={statsClassName} aria-label={statLabel || `${title} summary`}>
                {stats.map((stat) => (
                  <div key={stat.label} className={`page-hero-stat ${stat.tone || ''}`}>
                    <span>{stat.label}</span>
                    <strong>{stat.value}</strong>
                  </div>
                ))}
                {statsExtra}
              </div>
            )}

            {/* Tier 2: Controls / Search / Filter / PeriodNav */}
            {toolbarContent && (
              <div className="canopy-tier2">
                {toolbarContent}
              </div>
            )}
          </div>
        )}
      </div>
    </header>
  );
}
