import type { DecorKind, FloorPlan, FloorPlanDecor, FloorPlanSpot, Resource, SpotShape } from '@/types/api';
import { clamp, evenHeight, round } from '../FloorPlanScene';

/**
 * The floor plan editor's data and the pure geometry behind it. Positions and
 * sizes are percentages of the floor, so a drawing scales to any screen.
 */

export const ROOM_SHAPES = [
  { label: 'Wide (16:10)', value: 1.6 },
  { label: 'Very wide (2:1)', value: 2 },
  { label: 'Landscape (4:3)', value: 1.333 },
  { label: 'Square', value: 1 },
  { label: 'Tall (3:4)', value: 0.75 },
];
export const DEFAULT_WIDTH = 11;
export const MAX_FLOORS = 20;

/** Size limits, in percent of the floor. */
export const TABLE_SIZE = { min: 2, max: 60 } as const;
export const PIECE_SIZE = { min: 0.5, max: 100 } as const;

/** A piece of decor with a key that stays the same while it is edited. */
export type Piece = FloorPlanDecor & { key: number };
export type Selection = { type: 'table'; id: number } | { type: 'decor'; key: number } | null;
export type Target = NonNullable<Selection>;

/** An uploaded picture of the place, shown faintly behind the floor to trace over. */
export interface Background {
  mediaId: string;
  url: string;
}

/** One floor being edited; {@code id} is absent until it has been saved. */
export interface FloorDraft {
  key: number;
  id?: number;
  name: string;
  aspectRatio: number;
  items: FloorPlanSpot[];
  decor: Piece[];
  background: Background | null;
}

/** The room shape closest to a picture's width over height, within what a floor allows. */
export function aspectOf(width: number, height: number): number {
  return Math.round(clamp(width / height, 0.25, 4) * 1000) / 1000;
}

type Box = { x: number; y: number; width: number; height: number };

/** The API sends decimals that may arrive as strings; the editor works in numbers. */
export function numericBox<T extends Box>(box: T): T {
  return { ...box, x: Number(box.x), y: Number(box.y), width: Number(box.width), height: Number(box.height) };
}

/** Keeps the whole item on the plan. */
export function keepInside<T extends Box>(box: T, minSize: number, maxSize: number): T {
  const width = round(clamp(box.width, minSize, maxSize));
  const height = round(clamp(box.height, minSize, maxSize));
  return {
    ...box,
    width,
    height,
    x: round(clamp(box.x, width / 2, 100 - width / 2)),
    y: round(clamp(box.y, height / 2, 100 - height / 2)),
  };
}

export const keepTableInside = (spot: FloorPlanSpot) => keepInside(spot, TABLE_SIZE.min, TABLE_SIZE.max);
export const keepPieceInside = (piece: Piece) => keepInside(piece, PIECE_SIZE.min, PIECE_SIZE.max);

/** The drafts for the saved plans, or one empty floor to start from. */
export function draftsFrom(plans: FloorPlan[], nextKey: () => number): FloorDraft[] {
  if (!plans.length) {
    return [{ key: nextKey(), name: 'Main floor', aspectRatio: 1.6, items: [], decor: [], background: null }];
  }
  return plans.map((plan) => ({
    key: nextKey(),
    id: plan.id,
    name: plan.name,
    aspectRatio: Number(plan.aspectRatio),
    items: plan.items.map(numericBox),
    decor: plan.decor.map((piece) => ({ ...numericBox(piece), key: nextKey() })),
    background:
      plan.backgroundMediaId && plan.backgroundUrl ? { mediaId: plan.backgroundMediaId, url: plan.backgroundUrl } : null,
  }));
}

/** "Floor 3", skipping names already taken. */
export function nextFloorName(floors: FloorDraft[]): string {
  const taken = new Set(floors.map((draft) => draft.name.trim().toLowerCase()));
  let number = floors.length + 1;
  while (taken.has(`floor ${number}`)) {
    number++;
  }
  return `Floor ${number}`;
}

/** Where a newly placed resource goes: staggered, so several new ones do not stack. Big ones are long tables. */
export function newSpot(resource: Resource, placedCount: number, aspectRatio: number): FloorPlanSpot {
  const offset = (placedCount % 6) * 5;
  const big = Boolean(resource.capacity && resource.capacity > 4);
  const width = big ? 14 : DEFAULT_WIDTH;
  const shape: SpotShape = big ? 'RECT' : 'ROUND';
  return keepTableInside({
    resourceId: resource.id,
    x: 30 + offset,
    y: 35 + offset,
    width,
    height: shape === 'RECT' ? clamp(round(width * aspectRatio * 0.65), 2, 60) : evenHeight(width, aspectRatio),
    shape,
  });
}

/** A new piece of decor of this kind, at a sensible place and size. */
export function newPiece(kind: DecorKind, key: number, aspectRatio: number): Piece {
  const defaults: Record<DecorKind, Omit<FloorPlanDecor, 'kind'>> = {
    ZONE: { label: 'Area', tone: 'GREEN', x: 50, y: 50, width: 30, height: 40 },
    WALL: { label: null, tone: null, x: 50, y: 30, width: 1, height: 30 },
    COUNTER: { label: 'Bar', tone: null, x: 50, y: 15, width: 22, height: 10 },
    PLANT: { label: null, tone: null, x: 6, y: 10, width: 4.5, height: round(4.5 * aspectRatio) },
    ENTRANCE: { label: 'Entrance', tone: null, x: 50, y: 95, width: 10, height: 7 },
  };
  return keepPieceInside({ kind, ...defaults[kind], key });
}

/** Swaps width and height while keeping the real length — a wall turned on the spot. */
export function turned(box: { width: number; height: number }, aspectRatio: number) {
  return { width: round(box.height / aspectRatio), height: round(box.width * aspectRatio) };
}

/** A new room shape. Round and square tables and plants keep their look. */
export function reshaped(draft: FloorDraft, aspectRatio: number): Partial<FloorDraft> {
  return {
    aspectRatio,
    items: draft.items.map((item) =>
      keepTableInside(item.shape === 'RECT' ? item : { ...item, height: evenHeight(item.width, aspectRatio) }),
    ),
    decor: draft.decor.map((piece) =>
      keepPieceInside(piece.kind === 'PLANT' ? { ...piece, height: round(piece.width * aspectRatio) } : piece),
    ),
  };
}

/** Why the floors cannot be saved, or null when they can. */
export function floorNamesProblem(floors: FloorDraft[]): { message: string; floorKey?: number } | null {
  const blank = floors.find((draft) => !draft.name.trim());
  if (blank) {
    return { message: 'Give every floor a name.', floorKey: blank.key };
  }
  const names = floors.map((draft) => draft.name.trim());
  const duplicate = names.find(
    (name, index) => names.findIndex((other) => other.toLowerCase() === name.toLowerCase()) !== index,
  );
  return duplicate ? { message: `Two floors are called “${duplicate}”. Give each floor its own name.` } : null;
}
