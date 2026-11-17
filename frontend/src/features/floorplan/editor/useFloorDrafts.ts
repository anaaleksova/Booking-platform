import { useMemo, useRef, useState } from 'react';
import type { DecorKind, FloorPlan, FloorPlanSpot, Resource } from '@/types/api';
import {
  draftsFrom,
  keepPieceInside,
  keepTableInside,
  newPiece,
  newSpot,
  nextFloorName,
  reshaped,
  turned,
  type Background,
  type FloorDraft,
  type Piece,
  type Selection,
  type Target,
} from './editorModel';

/**
 * Every floor being edited, which one is shown, what is selected, and the
 * changes the editor makes to them. Nothing is saved until the owner saves.
 */
export function useFloorDrafts(plans: FloorPlan[], resources: Resource[]) {
  const nextKey = useRef(0);
  const newKey = () => nextKey.current++;
  const [floors, setFloors] = useState<FloorDraft[]>(() => draftsFrom(plans, newKey));
  const [activeKey, setActiveKey] = useState(floors[0]?.key ?? 0);
  const [selection, setSelection] = useState<Selection>(null);

  const floor = floors.find((candidate) => candidate.key === activeKey) ?? floors[0];
  const aspectRatio = floor?.aspectRatio ?? 1.6;
  const items = floor?.items ?? [];
  const decor = floor?.decor ?? [];

  const byId = useMemo(() => new Map(resources.map((resource) => [resource.id, resource])), [resources]);
  /** Which floor each placed resource stands on. */
  const floorOf = new Map<number, FloorDraft>();
  floors.forEach((candidate) => candidate.items.forEach((item) => floorOf.set(item.resourceId, candidate)));
  const unplaced = resources.filter((resource) => !floorOf.has(resource.id));
  const elsewhere = resources.filter((resource) => floorOf.has(resource.id) && floorOf.get(resource.id) !== floor);
  const selectedTable = selection?.type === 'table' ? items.find((item) => item.resourceId === selection.id) : undefined;
  const selectedPiece = selection?.type === 'decor' ? decor.find((piece) => piece.key === selection.key) : undefined;

  const updateFloor = (key: number, change: (draft: FloorDraft) => Partial<FloorDraft>) =>
    setFloors((current) => current.map((draft) => (draft.key === key ? { ...draft, ...change(draft) } : draft)));
  const setItems = (change: (items: FloorPlanSpot[]) => FloorPlanSpot[]) =>
    floor && updateFloor(floor.key, (draft) => ({ items: change(draft.items) }));
  const setDecor = (change: (decor: Piece[]) => Piece[]) =>
    floor && updateFloor(floor.key, (draft) => ({ decor: change(draft.decor) }));

  const switchFloor = (key: number) => {
    setActiveKey(key);
    setSelection(null);
  };

  /** Adds a floor and shows it; returns its key. */
  const addFloor = () => {
    const draft: FloorDraft = { key: newKey(), name: nextFloorName(floors), aspectRatio, items: [], decor: [], background: null };
    setFloors((current) => [...current, draft]);
    switchFloor(draft.key);
    return draft.key;
  };

  const moveFloor = (offset: -1 | 1) => {
    if (!floor) {
      return;
    }
    setFloors((current) => {
      const next = [...current];
      const from = next.indexOf(floor);
      const to = from + offset;
      if (to < 0 || to >= next.length) {
        return current;
      }
      [next[from], next[to]] = [next[to], next[from]];
      return next;
    });
  };

  const deleteFloor = (target: FloorDraft) => {
    const remaining = floors.filter((draft) => draft.key !== target.key);
    setFloors(remaining);
    if (target.key === activeKey) {
      const index = floors.indexOf(target);
      switchFloor(remaining[Math.max(0, index - 1)]?.key ?? 0);
    }
  };

  const renameFloor = (name: string) => floor && updateFloor(floor.key, () => ({ name: name.slice(0, 60) }));
  const changeAspect = (next: number) => floor && updateFloor(floor.key, (draft) => reshaped(draft, next));
  /**
   * Puts a picture behind this floor, or takes it away. With {@code aspectRatio}
   * the room takes the picture's shape, so the picture is not stretched.
   */
  const setBackground = (background: Background | null, aspectRatio?: number) =>
    floor &&
    updateFloor(floor.key, (draft) => ({
      background,
      ...(aspectRatio !== undefined && aspectRatio !== draft.aspectRatio ? reshaped(draft, aspectRatio) : {}),
    }));

  const updateTable = (id: number, change: (item: FloorPlanSpot) => Partial<FloorPlanSpot>) =>
    setItems((current) => current.map((item) => (item.resourceId === id ? keepTableInside({ ...item, ...change(item) }) : item)));
  const updatePiece = (key: number, change: (piece: Piece) => Partial<Piece>) =>
    setDecor((current) => current.map((piece) => (piece.key === key ? keepPieceInside({ ...piece, ...change(piece) }) : piece)));
  const move = (target: Target, change: (box: { x: number; y: number }) => { x: number; y: number }) =>
    target.type === 'table' ? updateTable(target.id, change) : updatePiece(target.key, change);
  const turn = (target: Target) =>
    target.type === 'table'
      ? updateTable(target.id, (item) => turned(item, aspectRatio))
      : updatePiece(target.key, (piece) => turned(piece, aspectRatio));

  const addTable = (resource: Resource) => {
    setItems((current) => [...current, newSpot(resource, current.length, aspectRatio)]);
    setSelection({ type: 'table', id: resource.id });
  };

  /** Takes a resource off the floor it is on and places it on this one. */
  const moveHere = (resource: Resource) => {
    const from = floorOf.get(resource.id);
    if (!floor || !from) {
      return;
    }
    const spot = from.items.find((item) => item.resourceId === resource.id)!;
    setFloors((current) =>
      current.map((draft) =>
        draft.key === from.key
          ? { ...draft, items: draft.items.filter((item) => item.resourceId !== resource.id) }
          : draft.key === floor.key
            ? { ...draft, items: [...draft.items, keepTableInside({ ...spot })] }
            : draft,
      ),
    );
    setSelection({ type: 'table', id: resource.id });
  };

  const addPiece = (kind: DecorKind) => {
    const piece = newPiece(kind, newKey(), aspectRatio);
    setDecor((current) => [...current, piece]);
    setSelection({ type: 'decor', key: piece.key });
  };

  const remove = (target: Selection) => {
    if (target?.type === 'table') {
      setItems((current) => current.filter((item) => item.resourceId !== target.id));
    } else if (target?.type === 'decor') {
      setDecor((current) => current.filter((piece) => piece.key !== target.key));
    }
    setSelection(null);
  };

  return {
    floors,
    floor,
    floorIndex: floor ? floors.indexOf(floor) : -1,
    aspectRatio,
    items,
    decor,
    byId,
    floorOf,
    unplaced,
    elsewhere,
    selection,
    setSelection,
    selectedTable,
    selectedPiece,
    switchFloor,
    addFloor,
    moveFloor,
    deleteFloor,
    renameFloor,
    changeAspect,
    setBackground,
    updateTable,
    updatePiece,
    move,
    turn,
    addTable,
    moveHere,
    addPiece,
    remove,
  };
}

export type FloorDrafts = ReturnType<typeof useFloorDrafts>;
