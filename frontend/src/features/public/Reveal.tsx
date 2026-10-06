import { useEffect, useRef, useState } from 'react';
import Box, { type BoxProps } from '@mui/material/Box';

/**
 * Fades its children up into place the first time they scroll into view.
 * `delay` (ms) staggers siblings. Users who prefer reduced motion see the
 * content immediately.
 */
export function Reveal({ delay = 0, sx, children, ...rest }: BoxProps & { delay?: number }) {
  const ref = useRef<HTMLDivElement>(null);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const node = ref.current;
    if (!node || typeof IntersectionObserver === 'undefined') {
      setVisible(true);
      return;
    }
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setVisible(true);
          observer.disconnect();
        }
      },
      { threshold: 0.12, rootMargin: '0px 0px -40px 0px' },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  return (
    <Box
      ref={ref}
      sx={[
        {
          opacity: visible ? 1 : 0,
          transform: visible ? 'none' : 'translateY(24px)',
          transition: 'opacity 600ms ease, transform 600ms cubic-bezier(0.22, 1, 0.36, 1)',
          transitionDelay: `${delay}ms`,
          '@media (prefers-reduced-motion: reduce)': { opacity: 1, transform: 'none', transition: 'none' },
        },
        ...(Array.isArray(sx) ? sx : [sx]),
      ]}
      {...rest}
    >
      {children}
    </Box>
  );
}
