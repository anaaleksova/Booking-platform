import { forwardRef, useId, type ReactNode, type SVGProps } from 'react';
import Box from '@mui/material/Box';
import { alpha, useTheme } from '@mui/material/styles';
import type { DecorTone, FloorPlanDecor, FloorPlanSpot, SpotShape } from '@/types/api';

/**
 * The floor plan as an illustration: walls, a plank floor, the areas and
 * furniture of the room, and each bookable table drawn with its chairs.
 *
 * Everything is stored in percentages of the plan; the drawing uses a fixed
 * 1000-unit-wide coordinate space so lines and labels keep their proportions
 * at any size.
 */
export const VIEW_WIDTH = 1000;
const WALL = 12;

/** The illustration keeps its own, picture-like colours in light and dark mode. */
const INK = {
  wall: '#dcdfe4',
  wallLine: '#9aa1ab',
  window: '#d4e8f6',
  floor: '#f4ede1',
  floorLine: '#e7dccb',
  partition: '#a7adb6',
  label: '#6b7280',
};

const TONES: Record<DecorTone, string> = {
  GREEN: '#dcefe1',
  PINK: '#f7e0e1',
  BLUE: '#dde9f7',
  SAND: '#efe4cf',
};
export const TONE_OPTIONS: { value: DecorTone; label: string; color: string }[] = [
  { value: 'GREEN', label: 'Green', color: TONES.GREEN },
  { value: 'PINK', label: 'Pink', color: TONES.PINK },
  { value: 'BLUE', label: 'Blue', color: TONES.BLUE },
  { value: 'SAND', label: 'Sand', color: TONES.SAND },
];

export type TableState = 'edit' | 'editSelected' | 'free' | 'selected' | 'taken' | 'small';

/** Fill colours per state — exported so legends match the drawing. */
export const TABLE_COLORS = {
  edit: { top: '#efe3cf', line: '#b08d5b', chair: '#a9b1ba', back: '#7e8792', text: '#3f3a33' },
  free: { top: '#e3f4e8', line: '#2f9e5c', chair: '#acdcbd', back: '#2f9e5c', text: '#1d5233' },
  // Neutral grey: booked must never be mistaken for the customer's own (brand-coloured) choice.
  taken: { top: '#e6e8eb', line: '#9aa1ab', chair: '#d3d7dc', back: '#9aa1ab', text: '#6b7280' },
  small: { top: '#efede9', line: '#b9b3a9', chair: '#e0dcd5', back: '#c6c0b6', text: '#857f75' },
};

/** A seat someone else already holds at a shared table — neutral, so it reads on any table colour. */
export const OCCUPIED_SEAT = { chair: '#cdd2d8', back: '#7d8590' };

export function viewHeight(aspectRatio: number): number {
  return VIEW_WIDTH / aspectRatio;
}

/** A box in percent → drawing units. */
export function toUnits(box: { x: number; y: number; width: number; height: number }, aspectRatio: number) {
  const height = viewHeight(aspectRatio);
  const w = (Number(box.width) / 100) * VIEW_WIDTH;
  const h = (Number(box.height) / 100) * height;
  const cx = (Number(box.x) / 100) * VIEW_WIDTH;
  const cy = (Number(box.y) / 100) * height;
  return { cx, cy, w, h, left: cx - w / 2, top: cy - h / 2 };
}

/**
 * The room. Children are {@link DecorShape}s and {@link TableShape}s; areas
 * should come first so they lie under everything else.
 */
export const FloorPlanScene = forwardRef<
  SVGSVGElement,
  {
    aspectRatio: number;
    children?: ReactNode;
    label: string;
    /** 1 = fit the width; larger zooms in and the plan scrolls. */
    zoom?: number;
    /** Below this width the plan scrolls sideways instead of shrinking further. */
    minWidth?: number;
    /**
     * Fill the parent's height as well as its width: the drawing is as large
     * as fits both, centred, and never scrolls. The parent needs a definite
     * height (e.g. a flex item in a fixed-height dialog).
     */
    fill?: boolean;
    onBackgroundPointerDown?: () => void;
  }
>(function FloorPlanScene({ aspectRatio, children, label, zoom = 1, minWidth = 0, fill = false, onBackgroundPointerDown }, ref) {
  const theme = useTheme();
  const id = useId().replace(/:/g, '');
  const height = viewHeight(aspectRatio);
  const windows = Math.max(2, Math.floor(VIEW_WIDTH / 190));
  /** Width over height of the whole drawing, walls included. */
  const drawnRatio = (VIEW_WIDTH + 2 * WALL + 8) / (height + 2 * WALL + 8);

  return (
    <Box
      sx={{
        overflow: 'auto',
        borderRadius: 3,
        bgcolor: theme.palette.mode === 'dark' ? 'grey.900' : '#f3f4f6',
        p: { xs: 1.5, sm: 3 },
        ...(fill ? { height: '100%', containerType: 'size', display: 'flex', alignItems: 'center' } : {}),
      }}
    >
      <Box
        sx={{
          // In fill mode 100cqh is the frame's inner height: as wide as that height allows, at most the full width.
          width: fill ? `min(100%, calc(100cqh * ${drawnRatio.toFixed(4)}))` : `${zoom * 100}%`,
          minWidth: fill ? 0 : minWidth * zoom,
          mx: 'auto',
          transition: 'width 200ms ease',
        }}
      >
        <Box
          component="svg"
          ref={ref}
          viewBox={`${-WALL - 4} ${-WALL - 4} ${VIEW_WIDTH + 2 * WALL + 8} ${height + 2 * WALL + 8}`}
          role="group"
          aria-label={label}
          sx={{
            display: 'block',
            width: '100%',
            height: 'auto',
            userSelect: 'none',
            fontFamily: theme.typography.fontFamily,
            '& .fp-spot': { outline: 'none' },
            '& .fp-spot.is-interactive': { cursor: 'pointer' },
            '& .fp-ring': { opacity: 0, transition: 'opacity 120ms' },
            '& .fp-spot:focus-visible .fp-ring': { opacity: 1 },
            '& .fp-body': { transition: 'transform 160ms ease', transformBox: 'fill-box', transformOrigin: 'center' },
            '& .fp-spot.is-free:hover .fp-body': { transform: 'scale(1.04)' },
            '& .fp-shadow': { filter: 'drop-shadow(0 3px 3px rgba(15, 23, 42, 0.16))' },
          }}
        >
          <defs>
            <pattern id={`${id}-planks`} width="180" height="56" patternUnits="userSpaceOnUse">
              <rect width="180" height="56" fill={INK.floor} />
              <path d="M0 27.5H180M0 55.5H180M50 0V28M140 28V56" stroke={INK.floorLine} strokeWidth="1.5" />
            </pattern>
            <filter id={`${id}-room-shadow`} x="-10%" y="-10%" width="120%" height="125%">
              <feDropShadow dx="0" dy="8" stdDeviation="10" floodColor="#0f172a" floodOpacity="0.14" />
            </filter>
          </defs>

          {/* Walls with windows along the top, then the floor. */}
          <rect
            x={-WALL}
            y={-WALL}
            width={VIEW_WIDTH + 2 * WALL}
            height={height + 2 * WALL}
            rx={4}
            fill={INK.wall}
            stroke={INK.wallLine}
            strokeWidth={2}
            filter={`url(#${id}-room-shadow)`}
          />
          {Array.from({ length: windows }, (_, index) => {
            const span = VIEW_WIDTH / windows;
            return (
              <rect
                key={index}
                x={index * span + span * 0.25}
                y={-WALL}
                width={span * 0.5}
                height={WALL}
                fill={INK.window}
                stroke={INK.wallLine}
                strokeWidth={1.5}
              />
            );
          })}
          <rect
            width={VIEW_WIDTH}
            height={height}
            fill={`url(#${id}-planks)`}
            stroke={alpha(INK.wallLine, 0.8)}
            strokeWidth={1.5}
            onPointerDown={onBackgroundPointerDown}
          />
          {children}
        </Box>
      </Box>
    </Box>
  );
});

// --- decor -------------------------------------------------------------------

const DECOR_ORDER = { ZONE: 0, WALL: 1, COUNTER: 2, ENTRANCE: 3, PLANT: 4 } as const;

/** Areas first, so tables and furniture are drawn on top of them. */
export function decorDrawOrder<T extends Pick<FloorPlanDecor, 'kind'>>(decor: T[]): T[] {
  return [...decor].sort((a, b) => DECOR_ORDER[a.kind] - DECOR_ORDER[b.kind]);
}

export function DecorShape({
  decor,
  aspectRatio,
  selected = false,
  groupProps,
}: {
  decor: FloorPlanDecor;
  aspectRatio: number;
  selected?: boolean;
  groupProps?: SVGProps<SVGGElement>;
}) {
  const theme = useTheme();
  const { cx, cy, w, h, left, top } = toUnits(decor, aspectRatio);
  const label = decor.label?.trim();

  let body: ReactNode;
  switch (decor.kind) {
    case 'ZONE':
      body = (
        <>
          <rect x={left} y={top} width={w} height={h} rx={3} fill={TONES[decor.tone ?? 'GREEN']} stroke={INK.partition} strokeWidth={5} />
          {label ? (
            <text x={left + 16} y={top + 30} fontSize={15} fontWeight={700} letterSpacing={1.6} fill={INK.label}>
              {label.toUpperCase()}
            </text>
          ) : null}
        </>
      );
      break;
    case 'WALL':
      body = <rect x={left} y={top} width={w} height={h} fill={INK.wall} stroke={INK.wallLine} strokeWidth={1.5} />;
      break;
    case 'COUNTER': {
      const wide = w >= h;
      const stools = Math.max(1, Math.floor((wide ? w : h) / 44));
      body = (
        <>
          {Array.from({ length: stools }, (_, index) => {
            const along = (index + 0.5) / stools;
            return (
              <circle
                key={index}
                cx={wide ? left + along * w : left + w + 15}
                cy={wide ? top + h + 15 : top + along * h}
                r={9}
                fill="#cdc6ba"
                stroke="#a39a8d"
                strokeWidth={1.5}
              />
            );
          })}
          <g className="fp-shadow">
            <rect x={left} y={top} width={w} height={h} rx={8} fill="#ddd6cb" stroke="#a39a8d" strokeWidth={2} />
            <rect x={left + 6} y={top + 6} width={Math.max(0, w - 12)} height={Math.max(0, h - 12)} rx={5} fill="#f7f4ef" />
          </g>
          {label ? (
            <text x={cx} y={cy} textAnchor="middle" dominantBaseline="central" fontSize={Math.min(18, h * 0.42)} fontWeight={700} fill="#57534e">
              {label}
            </text>
          ) : null}
        </>
      );
      break;
    }
    case 'PLANT': {
      const r = Math.min(w, h) / 2;
      body = (
        <g className="fp-shadow">
          <circle cx={cx} cy={cy} r={r * 0.62} fill="#d9c7a8" stroke="#b49c76" strokeWidth={1.5} />
          {Array.from({ length: 8 }, (_, index) => (
            <ellipse
              key={index}
              cx={cx}
              cy={cy - r * 0.42}
              rx={r * 0.24}
              ry={r * 0.5}
              fill={index % 2 ? '#7cb66f' : '#5c9c58'}
              transform={`rotate(${index * 45} ${cx} ${cy})`}
            />
          ))}
          <circle cx={cx} cy={cy} r={r * 0.2} fill="#4a8746" />
        </g>
      );
      break;
    }
    case 'ENTRANCE': {
      // The arrow sits on the side of the nearest outer wall and points into the room.
      const height = viewHeight(aspectRatio);
      const distances = { top: cy, bottom: height - cy, left: cx, right: VIEW_WIDTH - cx };
      const side = (Object.keys(distances) as (keyof typeof distances)[]).reduce((a, b) => (distances[a] <= distances[b] ? a : b));
      const s = Math.min(w, h) * 0.45;
      const arrow = {
        bottom: `M${cx - s} ${top + h + s * 0.2 + 6} L${cx + s} ${top + h + s * 0.2 + 6} L${cx} ${top + h - s * 0.6 + 6} Z`,
        top: `M${cx - s} ${top - s * 0.2 - 6} L${cx + s} ${top - s * 0.2 - 6} L${cx} ${top + s * 0.6 - 6} Z`,
        left: `M${left - s * 0.2 - 6} ${cy - s} L${left - s * 0.2 - 6} ${cy + s} L${left + s * 0.6 - 6} ${cy} Z`,
        right: `M${left + w + s * 0.2 + 6} ${cy - s} L${left + w + s * 0.2 + 6} ${cy + s} L${left + w - s * 0.6 + 6} ${cy} Z`,
      }[side];
      body = (
        <>
          <rect x={left} y={top} width={w} height={h} rx={4} fill="#e4e0d9" stroke="#bdb6aa" strokeWidth={1.5} strokeDasharray="6 4" />
          <path d={arrow} fill="#8b929c" />
          {label ? (
            <text x={cx} y={cy} textAnchor="middle" dominantBaseline="central" fontSize={Math.min(15, h * 0.4)} fontWeight={700} fill={INK.label}>
              {label}
            </text>
          ) : null}
        </>
      );
      break;
    }
  }

  return (
    <g className="fp-spot" {...groupProps}>
      {body}
      {selected ? (
        <rect
          x={left - 6}
          y={top - 6}
          width={w + 12}
          height={h + 12}
          rx={6}
          fill="none"
          stroke={theme.palette.primary.main}
          strokeWidth={3}
          strokeDasharray="8 5"
        />
      ) : null}
    </g>
  );
}

// --- tables ------------------------------------------------------------------

const DEFAULT_SEATS: Record<SpotShape, number> = { ROUND: 4, SQUARE: 4, RECT: 6 };

/**
 * A bookable table drawn from above with one chair per seat. The state
 * colours it: free (green), the customer's choice (brand colour), taken
 * (red), too small (faded grey), or neutral wood while editing.
 */
export function TableShape({
  spot,
  aspectRatio,
  name,
  caption,
  seats,
  takenSeats = 0,
  state,
  tooltip,
  groupProps,
}: {
  spot: FloorPlanSpot;
  aspectRatio: number;
  name: string;
  caption?: string;
  seats: number | null;
  /** On a shared table, chairs already taken by other groups — drawn in the booked colour. */
  takenSeats?: number;
  state: TableState;
  tooltip?: string;
  groupProps?: SVGProps<SVGGElement>;
}) {
  const theme = useTheme();
  const primary = theme.palette.primary;
  const colors =
    state === 'selected'
      ? { top: primary.main, line: primary.dark, chair: alpha(primary.main, 0.45), back: primary.dark, text: primary.contrastText }
      : state === 'editSelected'
        ? { ...TABLE_COLORS.edit, line: primary.main }
        : TABLE_COLORS[state];

  const { cx, cy, w, h, left, top } = toUnits(spot, aspectRatio);
  const depth = clamp(Math.min(w, h) * 0.2, 9, 20);
  const inset = depth * 0.9;
  const topW = Math.max(8, w - 2 * inset);
  const topH = Math.max(8, h - 2 * inset);
  const count = clamp(seats ?? DEFAULT_SEATS[spot.shape], 1, 14);
  const chairs = spot.shape === 'ROUND' ? roundChairs(cx, cy, topW / 2, topH / 2, depth, count) : sideChairs(cx, cy, topW, topH, depth, count);

  // Labels stay readable even on small tables; they may run past a narrow top.
  const nameSize = clamp(Math.min(topW, topH * 1.6) * 0.22, 17, 24);
  const showCaption = Boolean(caption) && topH > nameSize * 2.1;
  const dashed = state === 'small' ? '7 5' : undefined;
  const lineWidth = state === 'editSelected' || state === 'selected' ? 3.5 : 2;

  return (
    <g className={`fp-spot is-${state}${groupProps?.onClick || groupProps?.onPointerDown ? ' is-interactive' : ''}`} {...groupProps}>
      {tooltip ? <title>{tooltip}</title> : null}
      <g className="fp-body">
        <g className="fp-shadow">
          {chairs.map((chair, index) => {
            const chairColors = index < takenSeats && state !== 'taken' ? OCCUPIED_SEAT : colors;
            return (
              <g key={index} transform={`translate(${chair.x} ${chair.y}) rotate(${chair.angle})`}>
                <rect x={-chair.width / 2} y={-depth * 0.4} width={chair.width} height={depth * 0.8} rx={depth * 0.22} fill={chairColors.chair} />
                <rect x={-chair.width / 2} y={-depth * 0.4} width={chair.width} height={depth * 0.26} rx={depth * 0.13} fill={chairColors.back} />
              </g>
            );
          })}
          {spot.shape === 'ROUND' ? (
            <ellipse cx={cx} cy={cy} rx={topW / 2} ry={topH / 2} fill={colors.top} stroke={colors.line} strokeWidth={lineWidth} strokeDasharray={dashed} />
          ) : (
            <rect
              x={cx - topW / 2}
              y={cy - topH / 2}
              width={topW}
              height={topH}
              rx={Math.min(10, topW * 0.12)}
              fill={colors.top}
              stroke={colors.line}
              strokeWidth={lineWidth}
              strokeDasharray={dashed}
            />
          )}
        </g>
        <text
          x={cx}
          y={showCaption ? cy - nameSize * 0.45 : cy}
          textAnchor="middle"
          dominantBaseline="central"
          fontSize={nameSize}
          fontWeight={700}
          fill={colors.text}
        >
          {name}
        </text>
        {showCaption ? (
          <text x={cx} y={cy + nameSize * 0.7} textAnchor="middle" dominantBaseline="central" fontSize={nameSize * 0.74} fill={colors.text} opacity={0.85}>
            {caption}
          </text>
        ) : null}
        {state === 'selected' ? (
          <g transform={`translate(${cx + topW / 2 - 4} ${cy - topH / 2 + 4})`}>
            <circle r={12} fill="#fff" stroke={primary.main} strokeWidth={2.5} />
            <path d="M-5 0.5 L-1.5 4 L5.5 -3.5" fill="none" stroke={primary.main} strokeWidth={2.8} strokeLinecap="round" strokeLinejoin="round" />
          </g>
        ) : null}
      </g>
      {/* Focus ring for keyboard users. */}
      <rect
        className="fp-ring"
        x={left - 5}
        y={top - 5}
        width={w + 10}
        height={h + 10}
        rx={spot.shape === 'ROUND' ? Math.min(w, h) / 2 + 5 : 10}
        fill="none"
        stroke={primary.main}
        strokeWidth={2.5}
        strokeDasharray="7 5"
        opacity={0}
      />
    </g>
  );
}

interface Chair {
  x: number;
  y: number;
  angle: number;
  width: number;
}

function roundChairs(cx: number, cy: number, rx: number, ry: number, depth: number, count: number): Chair[] {
  const perimeter = Math.PI * (rx + ry);
  const width = Math.min(depth * 1.5, (perimeter / count) * 0.7);
  return Array.from({ length: count }, (_, index) => {
    const theta = -Math.PI / 2 + (2 * Math.PI * index) / count;
    return {
      x: cx + (rx + depth * 0.5) * Math.cos(theta),
      y: cy + (ry + depth * 0.5) * Math.sin(theta),
      angle: (theta * 180) / Math.PI + 90,
      width,
    };
  });
}

/** Seats spread over the four sides in proportion to their length, long sides first. */
function sideChairs(cx: number, cy: number, w: number, h: number, depth: number, count: number): Chair[] {
  let horizontal = Math.round((count * w) / (w + h));
  if (count >= 2 && horizontal === 1 && w >= h) horizontal = 2;
  if (count >= 2 && horizontal === count - 1 && h > w) horizontal = count - 2;
  horizontal = clamp(horizontal, 0, count);
  const vertical = count - horizontal;
  const sides = [
    { n: Math.ceil(horizontal / 2), length: w, place: (t: number) => ({ x: cx - w / 2 + t * w, y: cy - h / 2 - depth * 0.5, angle: 0 }) },
    { n: Math.floor(horizontal / 2), length: w, place: (t: number) => ({ x: cx - w / 2 + t * w, y: cy + h / 2 + depth * 0.5, angle: 180 }) },
    { n: Math.ceil(vertical / 2), length: h, place: (t: number) => ({ x: cx - w / 2 - depth * 0.5, y: cy - h / 2 + t * h, angle: -90 }) },
    { n: Math.floor(vertical / 2), length: h, place: (t: number) => ({ x: cx + w / 2 + depth * 0.5, y: cy - h / 2 + t * h, angle: 90 }) },
  ];
  return sides.flatMap((side) =>
    Array.from({ length: side.n }, (_, index) => ({
      ...side.place((index + 0.5) / side.n),
      width: Math.min(depth * 1.5, (side.length / side.n) * 0.72),
    })),
  );
}

// --- geometry helpers --------------------------------------------------------

/**
 * Height (in % of the plan's height) that makes a spot of the given width look
 * as tall as it is wide — round tables stay circles on wide plans.
 */
export function evenHeight(width: number, aspectRatio: number): number {
  return clamp(round(width * aspectRatio), 2, 60);
}

export function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

export function round(value: number): number {
  return Math.round(value * 10) / 10;
}
