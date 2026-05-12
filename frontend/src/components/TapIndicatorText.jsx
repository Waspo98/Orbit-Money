export default function TapIndicatorText({ as: Component = 'p', children, className = '', ...props }) {
  return (
    <Component className={`tap-indicator-text ${className}`.trim()} {...props}>
      {children}
    </Component>
  );
}
