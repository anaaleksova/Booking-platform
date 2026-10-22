import { describe, expect, it } from 'vitest';
import type { Resource } from '@/types/api';
import { draftsFrom, floorNamesProblem, keepInside, newSpot, nextFloorName, reshaped, turned, type FloorDraft } from './editorModel';

const resource = (id: number, capacity: number | null) => ({ id, capacity }) as Resource;
const draft = (key: number, name: string): FloorDraft => ({ key, name, aspectRatio: 1.6, items: [], decor: [] });

describe('keepInside', () => {
  it('keeps the whole box on the plan', () => {
    expect(keepInside({ x: 99, y: 1, width: 10, height: 10 }, 2, 60)).toEqual({ x: 95, y: 5, width: 10, height: 10 });
  });

  it('clamps the size', () => {
    expect(keepInside({ x: 50, y: 50, width: 100, height: 1 }, 2, 60)).toMatchObject({ width: 60, height: 2 });
  });
});

describe('newSpot', () => {
  it('a small table is round, a big one long', () => {
    expect(newSpot(resource(1, 2), 0, 1.6).shape).toBe('ROUND');
    expect(newSpot(resource(2, 8), 0, 1.6).shape).toBe('RECT');
  });

  it('staggers new tables so they do not stack', () => {
    const first = newSpot(resource(1, 2), 0, 1.6);
    const second = newSpot(resource(2, 2), 1, 1.6);
    expect([second.x - first.x, second.y - first.y]).toEqual([5, 5]);
  });
});

describe('turned', () => {
  it('keeps the real length of a wall on a non-square floor', () => {
    expect(turned({ width: 1, height: 32 }, 1.6)).toEqual({ width: 20, height: 1.6 });
  });
});

describe('reshaped', () => {
  it('round tables stay round on a new room shape, long tables keep their size', () => {
    const floor: FloorDraft = {
      ...draft(0, 'Main'),
      items: [
        { resourceId: 1, x: 50, y: 50, width: 10, height: 16, shape: 'ROUND' },
        { resourceId: 2, x: 50, y: 50, width: 20, height: 10, shape: 'RECT' },
      ],
    };
    const [round, long] = reshaped(floor, 1).items!;
    expect(round.height).toBe(10);
    expect(long).toMatchObject({ width: 20, height: 10 });
  });
});

describe('floors', () => {
  it('a business without plans starts with one empty floor', () => {
    let key = 0;
    expect(draftsFrom([], () => key++)).toEqual([{ key: 0, name: 'Main floor', aspectRatio: 1.6, items: [], decor: [] }]);
  });

  it('names a new floor after the next free number', () => {
    expect(nextFloorName([draft(0, 'Main'), draft(1, 'Floor 3')])).toBe('Floor 4');
  });

  it('every floor needs its own name', () => {
    expect(floorNamesProblem([draft(0, 'Main'), draft(1, ' ')])).toEqual({ message: 'Give every floor a name.', floorKey: 1 });
    expect(floorNamesProblem([draft(0, 'Main'), draft(1, 'MAIN')])?.message).toContain('Two floors are called');
    expect(floorNamesProblem([draft(0, 'Main'), draft(1, 'Rooftop')])).toBeNull();
  });
});
