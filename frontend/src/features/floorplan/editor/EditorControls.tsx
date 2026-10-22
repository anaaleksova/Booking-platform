import AddRoundedIcon from '@mui/icons-material/AddRounded';
import CircleOutlinedIcon from '@mui/icons-material/CircleOutlined';
import CropSquareRoundedIcon from '@mui/icons-material/CropSquareRounded';
import LayersOutlinedIcon from '@mui/icons-material/LayersOutlined';
import RectangleOutlinedIcon from '@mui/icons-material/RectangleOutlined';
import Rotate90DegreesCwOutlinedIcon from '@mui/icons-material/Rotate90DegreesCwOutlined';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import ButtonBase from '@mui/material/ButtonBase';
import Chip from '@mui/material/Chip';
import Slider from '@mui/material/Slider';
import Stack from '@mui/material/Stack';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import TextField from '@mui/material/TextField';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import type { FloorPlanSpot, Resource, SpotShape } from '@/types/api';
import { TONE_OPTIONS, clamp, evenHeight, round } from '../FloorPlanScene';
import { decorKind } from './decorKinds';
import { MAX_FLOORS, type FloorDraft, type Piece } from './editorModel';

/** The editor's smaller parts: floor tabs, and the panels for a selected table or piece. */

/** Floors as tabs, with how much is placed on each, and "Add floor" right next to them. */
export function FloorTabs({
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

export function NoFloors({ typeName, onAdd }: { typeName: string; onAdd: () => void }) {
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

export function TableControls({
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

export function PieceControls({
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
  const kind = decorKind(piece.kind);
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
