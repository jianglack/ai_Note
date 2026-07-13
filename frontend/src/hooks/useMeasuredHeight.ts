import { useLayoutEffect, useState, type RefObject } from 'react';

export function useMeasuredHeight<T extends HTMLElement>(
  ref: RefObject<T | null>,
  fallbackHeight = 400
) {
  const [height, setHeight] = useState(fallbackHeight);

  useLayoutEffect(() => {
    const element = ref.current;
    if (!element) return;

    const updateHeight = () => {
      const nextHeight = Math.floor(element.getBoundingClientRect().height);
      if (nextHeight > 0) {
        setHeight(nextHeight);
      }
    };

    updateHeight();
    const observer = new ResizeObserver(updateHeight);
    observer.observe(element);

    return () => observer.disconnect();
  }, [fallbackHeight, ref]);

  return height;
}
