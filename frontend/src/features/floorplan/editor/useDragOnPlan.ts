import { useRef, type KeyboardEvent, type PointerEvent, type RefObject } from 'react';
import { round } from '../FloorPlanScene';
import type { Target } from './editorModel';
import type { FloorDrafts } from './useFloorDrafts';

/**
 * Moving things on the plan: drag with a pointer, or select and use the
 * arrow keys (Shift for bigger steps); Delete removes.
 *
 * Returns the props for each draggable shape.
 */
export function useDragOnPlan(svgRef: RefObject<SVGSVGElement>, drafts: FloorDrafts) {
  const drag = useRef<{ target: Target; dx: number; dy: number } | null>(null);
  const { aspectRatio, move, remove, setSelection } = drafts;

  /** The pointer's position in floor percentages. */
  const pointFrom = (event: PointerEvent) => {
    // The drawing has a wall margin around the floor; measure against the floor itself.
    const svg = svgRef.current!;
    const point = svg.createSVGPoint();
    point.x = event.clientX;
    point.y = event.clientY;
    const local = point.matrixTransform(svg.getScreenCTM()!.inverse());
    return { x: (local.x / 1000) * 100, y: ((local.y * aspectRatio) / 1000) * 100 };
  };

  const startDrag = (event: PointerEvent<SVGGElement>, target: Target, box: { x: number; y: number }) => {
    event.preventDefault();
    event.stopPropagation();
    event.currentTarget.focus();
    event.currentTarget.setPointerCapture(event.pointerId);
    const point = pointFrom(event);
    drag.current = { target, dx: point.x - box.x, dy: point.y - box.y };
    setSelection(target);
  };

  const onDragMove = (event: PointerEvent) => {
    if (!drag.current) {
      return;
    }
    const { target, dx, dy } = drag.current;
    const point = pointFrom(event);
    move(target, () => ({ x: round(point.x - dx), y: round(point.y - dy) }));
  };

  const onKey = (event: KeyboardEvent, target: Target) => {
    const step = event.shiftKey ? 5 : 0.5;
    const moves: Record<string, [number, number]> = {
      ArrowLeft: [-step, 0],
      ArrowRight: [step, 0],
      ArrowUp: [0, -step],
      ArrowDown: [0, step],
    };
    if (moves[event.key]) {
      event.preventDefault();
      const [dx, dy] = moves[event.key];
      setSelection(target);
      move(target, (box) => ({ x: round(box.x + dx), y: round(box.y + dy) }));
    } else if (event.key === 'Delete' || event.key === 'Backspace') {
      event.preventDefault();
      remove(target);
    }
  };

  const endDrag = () => {
    drag.current = null;
  };

  return (target: Target, box: { x: number; y: number }, label: string) => ({
    role: 'button',
    tabIndex: 0,
    'aria-label': `${label} — drag or use arrow keys to move`,
    style: { cursor: 'grab', touchAction: 'none' },
    onPointerDown: (event: PointerEvent<SVGGElement>) => startDrag(event, target, box),
    onPointerMove: onDragMove,
    onPointerUp: endDrag,
    onPointerCancel: endDrag,
    onKeyDown: (event: KeyboardEvent<SVGGElement>) => onKey(event, target),
  });
}
