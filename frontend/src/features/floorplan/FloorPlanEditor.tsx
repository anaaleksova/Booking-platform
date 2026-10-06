import { useEffect, useMemo, useRef, useState, type KeyboardEvent, type PointerEvent, type ReactNode } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import ButtonBase from '@mui/material/ButtonBase';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Slider from '@mui/material/Slider';
import Stack from '@mui/material/Stack';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import TextField from '@mui/material/TextField';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import ChevronLeftRoundedIcon from '@mui/icons-material/ChevronLeftRounded';
import ChevronRightRoundedIcon from '@mui/icons-material/ChevronRightRounded';
import CircleOutlinedIcon from '@mui/icons-material/CircleOutlined';
import CropSquareRoundedIcon from '@mui/icons-material/CropSquareRounded';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import DoorFrontOutlinedIcon from '@mui/icons-material/DoorFrontOutlined';
import LayersOutlinedIcon from '@mui/icons-material/LayersOutlined';
import LocalBarOutlinedIcon from '@mui/icons-material/LocalBarOutlined';
import RectangleOutlinedIcon from '@mui/icons-material/RectangleOutlined';
import Rotate90DegreesCwOutlinedIcon from '@mui/icons-material/Rotate90DegreesCwOutlined';
import SpaOutlinedIcon from '@mui/icons-material/SpaOutlined';
import SquareFootOutlinedIcon from '@mui/icons-material/SquareFootOutlined';
import ViewQuiltOutlinedIcon from '@mui/icons-material/ViewQuiltOutlined';
import { errorMessage } from '@/api/client';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { LoadingState } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import { useFloorPlans, useResources, useSaveFloorPlans } from '@/features/catalog/api';
import type { DecorKind, FloorPlan, FloorPlanDecor, FloorPlanSpot, Resource, ResourceType, SpotShape } from '@/types/api';
import { DecorShape, FloorPlanScene, TONE_OPTIONS, TableShape, clamp, decorDrawOrder, evenHeight, round } from './FloorPlanScene';

const ROOM_SHAPES = [
  { label: 'Wide (16:10)', value: 1.6 },
  { label: 'Very wide (2:1)', value: 2 },
  { label: 'Landscape (4:3)', value: 1.333 },
  { label: 'Square', value: 1 },
  { label: 'Tall (3:4)', value: 0.75 },
];
const DEFAULT_WIDTH = 11;
const MAX_FLOORS = 20;

type Piece = FloorPlanDecor & { key: number };
type Selection = { type: 'table'; id: number } | { type: 'decor'; key: number } | null;

/** One floor being edited; {@code id} is absent until it has been saved. */
interface FloorDraft {
  key: number;
  id?: number;
  name: string;
  aspectRatio: number;
  items: FloorPlanSpot[];
  decor: Piece[];
}

const DECOR_KINDS: { kind: DecorKind; label: string; icon: ReactNode; hint: string }[] = [
  { kind: 'ZONE', label: 'Area', icon: <ViewQuiltOutlinedIcon />, hint: 'A tinted part of the room — terrace, kitchen, lounge' },
  { kind: 'WALL', label: 'Wall', icon: <SquareFootOutlinedIcon />, hint: 'A wall or partition' },
  { kind: 'COUNTER', label: 'Bar', icon: <LocalBarOutlinedIcon />, hint: 'A bar or reception counter' },
  { kind: 'PLANT', label: 'Plant', icon: <SpaOutlinedIcon />, hint: 'A plant' },
  { kind: 'ENTRANCE', label: 'Entrance', icon: <DoorFrontOutlinedIcon />, hint: 'Where guests come in' },
];

/**
 * Draw the place once — one plan per floor or area: lay out the room (areas,
 * walls, bar, plants, entrance) and drag each table, desk or court to where it
 * really is. Customers see these drawings, with what is free at their time,
 * when they choose.
 */
export function FloorPlanEditor({ type, onClose }: { type: ResourceType | null; onClose: () => void }) {
  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down('md'));
  const plans = useFloorPlans(type?.id);
  const resources = useResources();

  return (
    <Dialog
      open={type !== null}
      onClose={onClose}
      maxWidth="xl"
      fullWidth
      fullScreen={fullScreen}
      // Wide screens: the dialog takes the window's height and the map scales into it,
      // so nothing but the side panel ever scrolls.
      PaperProps={{ sx: { height: { md: 'calc(100% - 64px)' } } }}
    >
      <DialogTitle>Floor plans · {type?.name}</DialogTitle>
      {type && plans.isSuccess && resources.isSuccess ? (
        <EditorBody
          key={type.id}
          type={type}
          plans={plans.data}
          resources={resources.data.filter((resource) => resource.resourceTypeId === type.id)}
          onClose={onClose}
        />
      ) : (
        <DialogContent>
          <LoadingState />
        </DialogContent>
      )}
    </Dialog>
  );
}

function EditorBody({
  type,
  plans,
  resources,
  onClose,
}: {
  type: ResourceType;
  plans: FloorPlan[];
  resources: Resource[];
  onClose: () => void;
}) {
  const toast = useToast();
  const save = useSaveFloorPlans();
  const theme = useTheme();
  /** Map and panel side by side, fitted to the window; below this they stack and scroll. */
  const wide = useMediaQuery(theme.breakpoints.up('md'));
  const nextKey = useRef(0);
  const [floors, setFloors] = useState<FloorDraft[]>(() =>
    plans.length
      ? plans.map((plan) => ({
          key: nextKey.current++,
          id: plan.id,
          name: plan.name,
          aspectRatio: Number(plan.aspectRatio),
          items: plan.items.map(numericBox),
          decor: plan.decor.map((piece) => ({ ...numericBox(piece), key: nextKey.current++ })),
        }))
      : [{ key: nextKey.current++, name: 'Main floor', aspectRatio: 1.6, items: [], decor: [] }],
  );
  const [activeKey, setActiveKey] = useState(floors[0]?.key ?? 0);
  const [selection, setSelection] = useState<Selection>(null);
  const [deletingFloor, setDeletingFloor] = useState<FloorDraft | null>(null);
  const [focusName, setFocusName] = useState(false);
  const svgRef = useRef<SVGSVGElement>(null);
  const nameRef = useRef<HTMLInputElement>(null);
  const drag = useRef<{ target: NonNullable<Selection>; dx: number; dy: number } | null>(null);

  const floor = floors.find((candidate) => candidate.key === activeKey) ?? floors[0];
  const floorIndex = floor ? floors.indexOf(floor) : -1;
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

  // A new floor's name is selected so it can be typed over straight away.
  useEffect(() => {
    if (focusName && nameRef.current) {
      nameRef.current.focus();
      nameRef.current.select();
      setFocusName(false);
    }
  }, [focusName, activeKey]);

  const updateFloor = (key: number, change: (draft: FloorDraft) => Partial<FloorDraft>) =>
    setFloors((current) => current.map((draft) => (draft.key === key ? { ...draft, ...change(draft) } : draft)));
  const setItems = (change: (items: FloorPlanSpot[]) => FloorPlanSpot[]) => floor && updateFloor(floor.key, (draft) => ({ items: change(draft.items) }));
  const setDecor = (change: (decor: Piece[]) => Piece[]) => floor && updateFloor(floor.key, (draft) => ({ decor: change(draft.decor) }));

  const switchFloor = (key: number) => {
    setActiveKey(key);
    setSelection(null);
  };

  const addFloor = () => {
    const taken = new Set(floors.map((draft) => draft.name.trim().toLowerCase()));
    let number = floors.length + 1;
    while (taken.has(`floor ${number}`)) {
      number++;
    }
    const draft: FloorDraft = { key: nextKey.current++, name: `Floor ${number}`, aspectRatio, items: [], decor: [] };
    setFloors((current) => [...current, draft]);
    switchFloor(draft.key);
    setFocusName(true);
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
    setDeletingFloor(null);
  };

  const updateTable = (id: number, change: (item: FloorPlanSpot) => Partial<FloorPlanSpot>) =>
    setItems((current) => current.map((item) => (item.resourceId === id ? keepInside({ ...item, ...change(item) }, 2, 60) : item)));
  const updatePiece = (key: number, change: (piece: Piece) => Partial<Piece>) =>
    setDecor((current) => current.map((piece) => (piece.key === key ? keepInside({ ...piece, ...change(piece) }, 0.5, 100) : piece)));
  const move = (target: NonNullable<Selection>, change: (box: { x: number; y: number }) => { x: number; y: number }) =>
    target.type === 'table' ? updateTable(target.id, change) : updatePiece(target.key, change);

  const newSpot = (resource: Resource, count: number): FloorPlanSpot => {
    const offset = (count % 6) * 5;
    const width = resource.capacity && resource.capacity > 4 ? 14 : DEFAULT_WIDTH;
    const shape: SpotShape = resource.capacity && resource.capacity > 4 ? 'RECT' : 'ROUND';
    return keepInside(
      {
        resourceId: resource.id,
        x: 30 + offset,
        y: 35 + offset,
        width,
        height: shape === 'RECT' ? clamp(round(width * aspectRatio * 0.65), 2, 60) : evenHeight(width, aspectRatio),
        shape,
      },
      2,
      60,
    );
  };

  const addTable = (resource: Resource) => {
    setItems((current) => [...current, newSpot(resource, current.length)]);
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
            ? { ...draft, items: [...draft.items, keepInside({ ...spot }, 2, 60)] }
            : draft,
      ),
    );
    setSelection({ type: 'table', id: resource.id });
  };

  const addPiece = (kind: DecorKind) => {
    const defaults: Record<DecorKind, Omit<FloorPlanDecor, 'kind'>> = {
      ZONE: { label: 'Area', tone: 'GREEN', x: 50, y: 50, width: 30, height: 40 },
      WALL: { label: null, tone: null, x: 50, y: 30, width: 1, height: 30 },
      COUNTER: { label: 'Bar', tone: null, x: 50, y: 15, width: 22, height: 10 },
      PLANT: { label: null, tone: null, x: 6, y: 10, width: 4.5, height: round(4.5 * aspectRatio) },
      ENTRANCE: { label: 'Entrance', tone: null, x: 50, y: 95, width: 10, height: 7 },
    };
    const key = nextKey.current++;
    setDecor((current) => [...current, keepInside({ kind, ...defaults[kind], key }, 0.5, 100)]);
    setSelection({ type: 'decor', key });
  };

  const removeSelected = () => {
    if (selection?.type === 'table') {
      setItems((current) => current.filter((item) => item.resourceId !== selection.id));
    } else if (selection?.type === 'decor') {
      setDecor((current) => current.filter((piece) => piece.key !== selection.key));
    }
    setSelection(null);
  };

  const changeAspect = (next: number) => {
    if (!floor) {
      return;
    }
    // Round and square tables and plants keep their look.
    updateFloor(floor.key, (draft) => ({
      aspectRatio: next,
      items: draft.items.map((item) => keepInside(item.shape === 'RECT' ? item : { ...item, height: evenHeight(item.width, next) }, 2, 60)),
      decor: draft.decor.map((piece) => keepInside(piece.kind === 'PLANT' ? { ...piece, height: round(piece.width * next) } : piece, 0.5, 100)),
    }));
  };

  /** Swaps width and height while keeping the real length — a wall turned on the spot. */
  const turn = (box: { width: number; height: number }) => ({
    width: round(box.height / aspectRatio),
    height: round(box.width * aspectRatio),
  });

  const pointFrom = (event: PointerEvent) => {
    // The drawing has a wall margin around the floor; measure against the floor itself.
    const svg = svgRef.current!;
    const point = svg.createSVGPoint();
    point.x = event.clientX;
    point.y = event.clientY;
    const local = point.matrixTransform(svg.getScreenCTM()!.inverse());
    return { x: (local.x / 1000) * 100, y: ((local.y * aspectRatio) / 1000) * 100 };
  };

  const startDrag = (event: PointerEvent<SVGGElement>, target: NonNullable<Selection>, box: { x: number; y: number }) => {
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

  const onKey = (event: KeyboardEvent, target: NonNullable<Selection>) => {
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
      if (target.type === 'table') {
        setItems((current) => current.filter((item) => item.resourceId !== target.id));
      } else {
        setDecor((current) => current.filter((piece) => piece.key !== target.key));
      }
      setSelection(null);
    }
  };

  const dragProps = (target: NonNullable<Selection>, box: { x: number; y: number }, label: string) => ({
    role: 'button',
    tabIndex: 0,
    'aria-label': `${label} — drag or use arrow keys to move`,
    style: { cursor: 'grab', touchAction: 'none' },
    onPointerDown: (event: PointerEvent<SVGGElement>) => startDrag(event, target, box),
    onPointerMove: onDragMove,
    onPointerUp: () => {
      drag.current = null;
    },
    onPointerCancel: () => {
      drag.current = null;
    },
    onKeyDown: (event: KeyboardEvent<SVGGElement>) => onKey(event, target),
  });

  const submit = async () => {
    const names = floors.map((draft) => draft.name.trim());
    const blank = floors.find((draft) => !draft.name.trim());
    if (blank) {
      switchFloor(blank.key);
      setFocusName(true);
      toast.error('Give every floor a name.');
      return;
    }
    const duplicate = names.find((name, index) => names.findIndex((other) => other.toLowerCase() === name.toLowerCase()) !== index);
    if (duplicate) {
      toast.error(`Two floors are called “${duplicate}”. Give each floor its own name.`);
      return;
    }
    try {
      await save.mutateAsync({
        typeId: type.id,
        plans: floors.map((draft) => ({
          id: draft.id,
          name: draft.name.trim(),
          aspectRatio: draft.aspectRatio,
          items: draft.items,
          decor: draft.decor.map(({ key: _key, ...piece }) => piece),
        })),
      });
      toast.success(floors.length ? 'Floor plans saved' : 'Floor plans removed');
      onClose();
    } catch (error) {
      toast.error(errorMessage(error));
    }
  };

  return (
    <>
      <DialogContent dividers sx={{ display: 'flex', flexDirection: 'column', overflow: wide ? 'hidden' : 'auto' }}>
        <FloorTabs
          floors={floors}
          activeKey={floor?.key}
          onSelect={switchFloor}
          onAdd={addFloor}
          canAdd={floors.length < MAX_FLOORS}
        />

        {floor ? (
          <Stack
            direction={{ xs: 'column', md: 'row' }}
            spacing={3}
            alignItems={wide ? 'stretch' : 'flex-start'}
            sx={{ mt: 2, ...(wide ? { flex: 1, minHeight: 0 } : {}) }}
          >
            <Box
              sx={{
                flex: 1,
                minWidth: 0,
                width: '100%',
                position: 'relative',
                ...(wide ? { display: 'flex', flexDirection: 'column', minHeight: 0 } : {}),
              }}
            >
              {items.length === 0 && decor.length === 0 ? (
                <Stack
                  alignItems="center"
                  justifyContent="center"
                  spacing={1}
                  sx={{ position: 'absolute', inset: 0, bottom: 28, zIndex: 1, pointerEvents: 'none', textAlign: 'center', px: 4 }}
                >
                  <Box sx={{ bgcolor: 'background.paper', borderRadius: 3, boxShadow: 2, px: 3, py: 2, maxWidth: 380 }}>
                    <Typography fontWeight={700}>“{floor.name.trim() || 'This floor'}” is empty</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {unplaced.length
                        ? `Use “Place” on the right to put ${type.name.toLowerCase()}s here`
                        : elsewhere.length
                          ? `Use “Move here” on the right to bring ${type.name.toLowerCase()}s from another floor`
                          : `Add ${type.name.toLowerCase()} resources first, then place them here`}
                      , and add walls, a bar or plants to draw the room.
                    </Typography>
                  </Box>
                </Stack>
              ) : null}
              <Box sx={wide ? { flex: 1, minHeight: 0 } : undefined}>
              <FloorPlanScene
                ref={svgRef}
                aspectRatio={aspectRatio}
                minWidth={560}
                fill={wide}
                label={`Floor plan of ${floor.name}`}
                onBackgroundPointerDown={() => setSelection(null)}
              >
                {decorDrawOrder(decor).map((piece) => (
                  <DecorShape
                    key={piece.key}
                    decor={piece}
                    aspectRatio={aspectRatio}
                    selected={selection?.type === 'decor' && selection.key === piece.key}
                    groupProps={dragProps({ type: 'decor', key: piece.key }, piece, piece.label || DECOR_KINDS.find((d) => d.kind === piece.kind)!.label)}
                  />
                ))}
                {items.map((item) => {
                  const resource = byId.get(item.resourceId);
                  const isSelected = selection?.type === 'table' && selection.id === item.resourceId;
                  return (
                    <TableShape
                      key={item.resourceId}
                      spot={item}
                      aspectRatio={aspectRatio}
                      name={resource?.name ?? '?'}
                      caption={resource?.capacity ? `${resource.capacity} seats` : undefined}
                      seats={resource?.capacity ?? null}
                      state={isSelected ? 'editSelected' : 'edit'}
                      groupProps={dragProps({ type: 'table', id: item.resourceId }, item, resource?.name ?? 'Table')}
                    />
                  );
                })}
              </FloorPlanScene>
              </Box>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1, flexShrink: 0 }}>
                Drag anything to move it. Select it to change its shape, size or name; arrow keys nudge (Shift for bigger steps), Delete removes.
              </Typography>
            </Box>

            <Stack
              spacing={2.5}
              sx={{ width: { xs: '100%', md: 280, lg: 320 }, flexShrink: 0, ...(wide ? { overflowY: 'auto', minHeight: 0, pr: 0.5, pb: 1 } : {}) }}
            >
              {selectedTable ? (
                <TableControls
                  item={selectedTable}
                  resource={byId.get(selectedTable.resourceId)}
                  aspectRatio={aspectRatio}
                  onChange={(change) => updateTable(selectedTable.resourceId, change)}
                  onTurn={() => updateTable(selectedTable.resourceId, turn)}
                  onRemove={removeSelected}
                  onDone={() => setSelection(null)}
                />
              ) : selectedPiece ? (
                <PieceControls
                  piece={selectedPiece}
                  onChange={(change) => updatePiece(selectedPiece.key, change)}
                  onTurn={() => updatePiece(selectedPiece.key, turn)}
                  onRemove={removeSelected}
                  onDone={() => setSelection(null)}
                />
              ) : (
                <>
                  <section>
                    <Typography variant="subtitle2" fontWeight={700} gutterBottom>
                      This floor
                    </Typography>
                    <TextField
                      inputRef={nameRef}
                      size="small"
                      label="Floor name"
                      placeholder="e.g. Ground floor, Rooftop"
                      value={floor.name}
                      onChange={(event) => updateFloor(floor.key, () => ({ name: event.target.value.slice(0, 60) }))}
                      error={!floor.name.trim()}
                      helperText={!floor.name.trim() ? 'Every floor needs a name' : 'Shown to customers above the map.'}
                      fullWidth
                    />
                    <TextField
                      select
                      size="small"
                      label="Room shape"
                      value={ROOM_SHAPES.some((shape) => shape.value === aspectRatio) ? aspectRatio : ''}
                      onChange={(event) => changeAspect(Number(event.target.value))}
                      fullWidth
                      sx={{ mt: 2 }}
                    >
                      {ROOM_SHAPES.map((shape) => (
                        <MenuItem key={shape.value} value={shape.value}>
                          {shape.label}
                        </MenuItem>
                      ))}
                    </TextField>
                    <Stack direction="row" alignItems="center" spacing={0.5} sx={{ mt: 1.5 }}>
                      {floors.length > 1 ? (
                        <>
                          <Tooltip title="Move this floor earlier">
                            <span>
                              <IconButton size="small" aria-label="Move floor earlier" disabled={floorIndex === 0} onClick={() => moveFloor(-1)}>
                                <ChevronLeftRoundedIcon />
                              </IconButton>
                            </span>
                          </Tooltip>
                          <Tooltip title="Move this floor later">
                            <span>
                              <IconButton size="small" aria-label="Move floor later" disabled={floorIndex === floors.length - 1} onClick={() => moveFloor(1)}>
                                <ChevronRightRoundedIcon />
                              </IconButton>
                            </span>
                          </Tooltip>
                        </>
                      ) : null}
                      <Box sx={{ flex: 1 }} />
                      <Button
                        size="small"
                        color="error"
                        startIcon={<DeleteOutlineRoundedIcon />}
                        onClick={() => (floor.items.length || floor.decor.length ? setDeletingFloor(floor) : deleteFloor(floor))}
                      >
                        Delete floor
                      </Button>
                    </Stack>
                  </section>

                  <Divider />

                  <section>
                    <Typography variant="subtitle2" fontWeight={700} gutterBottom>
                      Not on any floor yet
                    </Typography>
                    {unplaced.length ? (
                      <Stack spacing={1}>
                        {unplaced.map((resource) => (
                          <Stack key={resource.id} direction="row" alignItems="center" spacing={1}>
                            <Typography variant="body2" sx={{ flex: 1, minWidth: 0 }} noWrap>
                              {resource.name}
                              {resource.capacity ? ` · ${resource.capacity} seats` : ''}
                              {!resource.active ? ' (inactive)' : ''}
                            </Typography>
                            <Button size="small" startIcon={<AddRoundedIcon />} onClick={() => addTable(resource)}>
                              Place
                            </Button>
                          </Stack>
                        ))}
                      </Stack>
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        {resources.length ? 'Everything is placed.' : `Add some ${type.name.toLowerCase()} resources first.`}
                      </Typography>
                    )}
                    {elsewhere.length ? (
                      <>
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2, mb: 0.5 }}>
                          On other floors
                        </Typography>
                        <Stack spacing={0.5}>
                          {elsewhere.map((resource) => (
                            <Stack key={resource.id} direction="row" alignItems="center" spacing={1}>
                              <Typography variant="body2" color="text.secondary" sx={{ flex: 1, minWidth: 0 }} noWrap>
                                {resource.name} · {floorOf.get(resource.id)?.name.trim() || 'Unnamed floor'}
                              </Typography>
                              <Button size="small" color="inherit" onClick={() => moveHere(resource)}>
                                Move here
                              </Button>
                            </Stack>
                          ))}
                        </Stack>
                      </>
                    ) : null}
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                      Anything left off the floors can still be booked — customers see it in the list view.
                    </Typography>
                  </section>

                  <Divider />

                  <section>
                    <Typography variant="subtitle2" fontWeight={700} gutterBottom>
                      Add to the room
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
                      Not bookable — they help guests find their way around.
                    </Typography>
                    <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 1 }}>
                      {DECOR_KINDS.map((option) => (
                        <Tooltip key={option.kind} title={option.hint}>
                          <ButtonBase
                            onClick={() => addPiece(option.kind)}
                            sx={{ flexDirection: 'column', gap: 0.5, py: 1.25, borderRadius: 2, border: 1, borderColor: 'divider', color: 'text.secondary', '&:hover': { borderColor: 'primary.main', color: 'primary.main' } }}
                          >
                            {option.icon}
                            <Typography variant="caption" fontWeight={600}>
                              {option.label}
                            </Typography>
                          </ButtonBase>
                        </Tooltip>
                      ))}
                    </Box>
                  </section>
                </>
              )}
            </Stack>
          </Stack>
        ) : (
          <NoFloors typeName={type.name} onAdd={addFloor} />
        )}

        {floors.length > 0 && floors.every((draft) => draft.items.length === 0) && plans.length > 0 ? (
          <Alert severity="info" sx={{ mt: 2, flexShrink: 0 }}>
            With nothing placed on any floor, customers choose from the list as before.
          </Alert>
        ) : null}
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={submit} disabled={save.isPending}>
          Save
        </Button>
      </DialogActions>

      <ConfirmDialog
        open={deletingFloor !== null}
        title={`Delete “${deletingFloor?.name.trim() || 'this floor'}”?`}
        message={
          deletingFloor?.items.length
            ? `Its ${deletingFloor.items.length} placed ${deletingFloor.items.length === 1 ? 'resource goes' : 'resources go'} back to “Not on any floor”. Nothing is deleted until you save.`
            : 'Its drawing is removed. Nothing is deleted until you save.'
        }
        confirmLabel="Delete floor"
        onConfirm={() => deletingFloor && deleteFloor(deletingFloor)}
        onClose={() => setDeletingFloor(null)}
      />
    </>
  );
}

/** Floors as tabs, with how much is placed on each, and "Add floor" right next to them. */
function FloorTabs({
  floors,
  activeKey,
  onSelect,
  onAdd,
  canAdd,
}: {
  floors: FloorDraft[];
  activeKey?: number;
  onSelect: (key: number) => void;
  onAdd: () => void;
  canAdd: boolean;
}) {
  return (
    <Stack direction="row" alignItems="center" spacing={1} sx={{ borderBottom: 1, borderColor: 'divider' }}>
      <LayersOutlinedIcon color="action" sx={{ display: { xs: 'none', sm: 'block' } }} />
      {floors.length ? (
        <Tabs
          value={activeKey ?? false}
          onChange={(_, key: number) => onSelect(key)}
          variant="scrollable"
          scrollButtons="auto"
          sx={{ minHeight: 48, flexShrink: 1, minWidth: 0 }}
        >
          {floors.map((draft) => (
            <Tab
              key={draft.key}
              value={draft.key}
              sx={{ textTransform: 'none', fontWeight: 600, minHeight: 48 }}
              label={
                <Stack direction="row" spacing={1} alignItems="center">
                  <span>{draft.name.trim() || 'Unnamed floor'}</span>
                  <Chip size="small" label={draft.items.length} sx={{ height: 20, fontSize: 12 }} />
                </Stack>
              }
            />
          ))}
        </Tabs>
      ) : null}
      <Tooltip title={canAdd ? 'Add another floor or area, e.g. a first floor or a rooftop' : `Up to ${MAX_FLOORS} floors`}>
        <span>
          <Button startIcon={<AddRoundedIcon />} onClick={onAdd} disabled={!canAdd} sx={{ whiteSpace: 'nowrap', flexShrink: 0 }}>
            Add floor
          </Button>
        </span>
      </Tooltip>
    </Stack>
  );
}

function NoFloors({ typeName, onAdd }: { typeName: string; onAdd: () => void }) {
  return (
    <Stack alignItems="center" spacing={1.5} sx={{ py: 8, textAlign: 'center' }}>
      <LayersOutlinedIcon sx={{ fontSize: 48, color: 'text.disabled' }} />
      <Typography variant="h6" fontWeight={700}>
        No floor plans
      </Typography>
      <Typography color="text.secondary" sx={{ maxWidth: 420 }}>
        Customers choose their {typeName.toLowerCase()} from a list. Add a floor to let them pick on a map — one per floor or area.
      </Typography>
      <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={onAdd}>
        Add floor
      </Button>
    </Stack>
  );
}

function TableControls({
  item,
  resource,
  aspectRatio,
  onChange,
  onTurn,
  onRemove,
  onDone,
}: {
  item: FloorPlanSpot;
  resource?: Resource;
  aspectRatio: number;
  onChange: (change: (item: FloorPlanSpot) => Partial<FloorPlanSpot>) => void;
  onTurn: () => void;
  onRemove: () => void;
  onDone: () => void;
}) {
  const setShape = (shape: SpotShape) =>
    onChange((current) => ({
      shape,
      height: shape === 'RECT' ? clamp(round(current.width * aspectRatio * 0.6), 2, 60) : evenHeight(current.width, aspectRatio),
    }));

  return (
    <section>
      <Typography variant="subtitle2" fontWeight={700}>
        {resource?.name}
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {resource?.capacity ? `${resource.capacity} seats — one chair is drawn per seat` : 'No seat limit'}
      </Typography>
      <Typography variant="caption" color="text.secondary">
        Shape
      </Typography>
      <ToggleButtonGroup exclusive size="small" fullWidth value={item.shape} onChange={(_, value: SpotShape | null) => value && setShape(value)} sx={{ mb: 2, mt: 0.5 }}>
        <ToggleButton value="ROUND" aria-label="Round">
          <CircleOutlinedIcon fontSize="small" sx={{ mr: 0.5 }} /> Round
        </ToggleButton>
        <ToggleButton value="SQUARE" aria-label="Square">
          <CropSquareRoundedIcon fontSize="small" sx={{ mr: 0.5 }} /> Square
        </ToggleButton>
        <ToggleButton value="RECT" aria-label="Long">
          <RectangleOutlinedIcon fontSize="small" sx={{ mr: 0.5 }} /> Long
        </ToggleButton>
      </ToggleButtonGroup>
      <Typography variant="caption" color="text.secondary">
        {item.shape === 'RECT' ? 'Width' : 'Size'}
      </Typography>
      <Slider
        size="small"
        min={4}
        max={40}
        step={0.5}
        value={item.width}
        onChange={(_, value) =>
          onChange((current) => ({
            width: value as number,
            height: current.shape === 'RECT' ? current.height : evenHeight(value as number, aspectRatio),
          }))
        }
        aria-label="Width"
      />
      {item.shape === 'RECT' ? (
        <>
          <Typography variant="caption" color="text.secondary">
            Depth
          </Typography>
          <Slider size="small" min={4} max={60} step={0.5} value={item.height} onChange={(_, value) => onChange(() => ({ height: value as number }))} aria-label="Depth" />
        </>
      ) : null}
      <ItemActions onTurn={item.shape === 'RECT' ? onTurn : undefined} onRemove={onRemove} onDone={onDone} />
    </section>
  );
}

function PieceControls({
  piece,
  onChange,
  onTurn,
  onRemove,
  onDone,
}: {
  piece: Piece;
  onChange: (change: (piece: Piece) => Partial<Piece>) => void;
  onTurn: () => void;
  onRemove: () => void;
  onDone: () => void;
}) {
  const kind = DECOR_KINDS.find((option) => option.kind === piece.kind)!;
  const labelled = piece.kind === 'ZONE' || piece.kind === 'COUNTER' || piece.kind === 'ENTRANCE';
  return (
    <section>
      <Typography variant="subtitle2" fontWeight={700} gutterBottom>
        {kind.label}
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {kind.hint}
      </Typography>
      {labelled ? (
        <TextField
          size="small"
          label="Name"
          value={piece.label ?? ''}
          onChange={(event) => onChange(() => ({ label: event.target.value.slice(0, 40) }))}
          fullWidth
          sx={{ mb: 2 }}
        />
      ) : null}
      {piece.kind === 'ZONE' ? (
        <>
          <Typography variant="caption" color="text.secondary">
            Floor colour
          </Typography>
          <Stack direction="row" spacing={1} sx={{ mt: 0.5, mb: 2 }}>
            {TONE_OPTIONS.map((tone) => (
              <Tooltip key={tone.value} title={tone.label}>
                <ButtonBase
                  aria-label={tone.label}
                  aria-pressed={piece.tone === tone.value}
                  onClick={() => onChange(() => ({ tone: tone.value }))}
                  sx={{
                    width: 36,
                    height: 36,
                    borderRadius: '50%',
                    bgcolor: tone.color,
                    border: 2,
                    borderColor: piece.tone === tone.value ? 'primary.main' : 'divider',
                    boxShadow: piece.tone === tone.value ? 2 : 0,
                  }}
                />
              </Tooltip>
            ))}
          </Stack>
        </>
      ) : null}
      {piece.kind === 'PLANT' ? (
        <>
          <Typography variant="caption" color="text.secondary">
            Size
          </Typography>
          <Slider
            size="small"
            min={2}
            max={15}
            step={0.5}
            value={piece.width}
            onChange={(_, value) => onChange(() => ({ width: value as number, height: round((value as number) * (piece.height / piece.width)) }))}
            aria-label="Size"
          />
        </>
      ) : (
        <>
          <Typography variant="caption" color="text.secondary">
            Width
          </Typography>
          <Slider size="small" min={0.5} max={100} step={0.5} value={piece.width} onChange={(_, value) => onChange(() => ({ width: value as number }))} aria-label="Width" />
          <Typography variant="caption" color="text.secondary">
            Height
          </Typography>
          <Slider size="small" min={0.5} max={100} step={0.5} value={piece.height} onChange={(_, value) => onChange(() => ({ height: value as number }))} aria-label="Height" />
        </>
      )}
      <ItemActions onTurn={piece.kind === 'PLANT' ? undefined : onTurn} onRemove={onRemove} onDone={onDone} />
    </section>
  );
}

function ItemActions({ onTurn, onRemove, onDone }: { onTurn?: () => void; onRemove: () => void; onDone: () => void }) {
  return (
    <Stack direction="row" spacing={1} sx={{ mt: 1 }} alignItems="center">
      {onTurn ? (
        <Button size="small" startIcon={<Rotate90DegreesCwOutlinedIcon />} onClick={onTurn}>
          Turn
        </Button>
      ) : null}
      <Button size="small" color="error" onClick={onRemove}>
        Remove
      </Button>
      <Box sx={{ flex: 1 }} />
      <Button size="small" variant="outlined" onClick={onDone}>
        Done
      </Button>
    </Stack>
  );
}

/** The API sends decimals that may arrive as strings; the editor works in numbers. */
function numericBox<T extends { x: number; y: number; width: number; height: number }>(box: T): T {
  return { ...box, x: Number(box.x), y: Number(box.y), width: Number(box.width), height: Number(box.height) };
}

/** Keeps the whole item on the plan. */
function keepInside<T extends { x: number; y: number; width: number; height: number }>(box: T, minSize: number, maxSize: number): T {
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
