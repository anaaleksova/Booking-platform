import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import ButtonBase from '@mui/material/ButtonBase';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { alpha } from '@mui/material/styles';
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded';
import LayersOutlinedIcon from '@mui/icons-material/LayersOutlined';
import { TABLE_COLORS } from '@/features/floorplan/FloorPlanScene';
import { mediaUrl } from '@/features/media/api';
import type { AllocationOption, ChoosableOption, PublicFloorPlan } from '@/types/api';
import type { SpotStatus } from './seatingRules';

/** The panels around the map: ready-made seating, floors, the legend and the chosen spot. */

/** The customer's choice when it is several tables put together — all of them, not just the one clicked. */
export function SelectionDetails({
  tables,
  seatsOf,
  floorName,
  people,
  onClear,
}: {
  tables: ChoosableOption[];
  /** Seats a table offers this group now (free seats for a shared one). */
  seatsOf: (option: ChoosableOption) => number | null;
  /** Shown when the place has several floors. */
  floorName?: string;
  people: number;
  onClear: () => void;
}) {
  const seats = tables.reduce((sum, option) => sum + (seatsOf(option) ?? 0), 0);
  return (
    <Paper variant="outlined" sx={{ mt: 2, p: 2, borderRadius: 3, display: 'flex', gap: 2, alignItems: 'center' }}>
      <Avatar variant="rounded" sx={{ width: 56, height: 56, borderRadius: 2, bgcolor: 'primary.main', fontWeight: 800 }}>
        {tables.length}×
      </Avatar>
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography fontWeight={700}>{tables.map((option) => option.name).join(' + ')}</Typography>
        <Typography variant="body2" color="text.secondary">
          {[floorName, `Seats ${seats} together`].filter(Boolean).join(' · ')}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          {tables.map((option) => `${option.name}${seatsOf(option) ? ` (${seatsOf(option)})` : ''}`).join(', ')} — pushed
          together for your group of {people}.
        </Typography>
      </Box>
      <Button variant="outlined" onClick={onClear} sx={{ borderRadius: 'var(--site-radius-button, 999px)', flexShrink: 0 }}>
        Selected ✓
      </Button>
    </Paper>
  );
}

export function SpotDetails({
  option,
  combined,
  status,
  seatsLeft,
  floorName,
  picked,
  people,
  onToggle,
}: {
  option: ChoosableOption;
  /** Tables are combined for larger groups. */
  combined: boolean;
  status: SpotStatus;
  seatsLeft: number | null;
  /** Shown when the place has several floors. */
  floorName?: string;
  picked: boolean;
  people: number;
  onToggle: () => void;
}) {
  const note =
    status === 'taken'
      ? 'Already booked at this time.'
      : status === 'bigGroups'
        ? `Kept for groups of ${option.minPartySize} or more.`
        : status === 'small' && combined
          ? `Not part of a free combination for ${people} at this time.`
          : status === 'small'
          ? seatsLeft !== null && option.capacity !== null && seatsLeft < option.capacity
            ? `Only ${seatsLeft} ${seatsLeft === 1 ? 'seat' : 'seats'} left — not enough for ${people}.`
            : `Too small for ${people} people.`
          : option.shared
            ? `Shared table: ${seatsLeft} of ${option.capacity} seats free. Other guests may sit at the remaining seats.`
            : null;
  return (
    <Paper variant="outlined" sx={{ mt: 2, p: 2, borderRadius: 3, display: 'flex', gap: 2, alignItems: 'center' }}>
      <Avatar variant="rounded" src={mediaUrl(option.imageUrl)} sx={{ width: 56, height: 56, borderRadius: 2, bgcolor: 'primary.main' }}>
        {option.name.charAt(0)}
      </Avatar>
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography fontWeight={700}>{option.name}</Typography>
        <Typography variant="body2" color="text.secondary">
          {[floorName, option.capacity ? `Seats ${option.capacity}` : null, option.description].filter(Boolean).join(' · ')}
        </Typography>
        {note ? (
          <Typography variant="caption" color="text.secondary">
            {note}
          </Typography>
        ) : null}
      </Box>
      {status === 'free' ? (
        <Button variant={picked ? 'outlined' : 'contained'} onClick={onToggle} sx={{ borderRadius: 'var(--site-radius-button, 999px)', flexShrink: 0 }}>
          {picked ? 'Selected ✓' : 'Choose'}
        </Button>
      ) : null}
    </Paper>
  );
}

/**
 * The best ways to seat the group, found by the server — "Table 3 + Table 4".
 * The customer never has to put tables together themselves; the other free
 * tables are on the map.
 */
export function Suggestions({
  suggestions,
  people,
  noun,
  isPicked,
  onPick,
}: {
  suggestions: AllocationOption[];
  people: number;
  noun: string;
  isPicked: (allocation: AllocationOption) => boolean;
  onPick: (allocation: AllocationOption) => void;
}) {
  return (
    <Box sx={{ mb: 2 }}>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
        Best fit for {people} {people === 1 ? 'person' : 'people'} — or pick any free {noun} below:
      </Typography>
      <Box sx={{ display: 'grid', gap: 1, gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))' } }}>
        {suggestions.map((allocation) => {
          const picked = isPicked(allocation);
          const several = allocation.resources.length > 1;
          return (
            <ButtonBase
              key={allocation.resources.map((resource) => resource.id).join('-')}
              onClick={() => onPick(allocation)}
              aria-pressed={picked}
              sx={{
                justifyContent: 'flex-start',
                gap: 1.5,
                p: 1.5,
                borderRadius: 3,
                border: 2,
                borderColor: picked ? 'primary.main' : 'divider',
                bgcolor: picked ? (theme) => alpha(theme.palette.primary.main, 0.05) : 'background.paper',
                textAlign: 'left',
              }}
            >
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <Typography fontWeight={700} noWrap>
                  {allocation.resources.map((resource) => resource.name).join(' + ')}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {[
                    allocation.capacity !== null ? `Fits up to ${allocation.capacity}` : null,
                    several ? `${allocation.resources.length} ${noun}s together` : null,
                    allocation.area,
                  ]
                    .filter(Boolean)
                    .join(' · ')}
                </Typography>
              </Box>
              {picked ? <CheckCircleRoundedIcon color="primary" /> : null}
            </ButtonBase>
          );
        })}
      </Box>
    </Box>
  );
}

/** One button per floor, each saying how much is free there — like a lift panel. */
export function FloorPicker({
  floors,
  activeId,
  freeOn,
  onSelect,
}: {
  floors: PublicFloorPlan[];
  activeId: number;
  freeOn: (floor: PublicFloorPlan) => number;
  onSelect: (id: number) => void;
}) {
  return (
    <Box role="tablist" aria-label="Floors" sx={{ display: 'flex', gap: 1, overflowX: 'auto', pb: 0.5, mb: 1.5, scrollbarWidth: 'none' }}>
      {floors.map((floor) => {
        const active = floor.id === activeId;
        const free = freeOn(floor);
        return (
          <ButtonBase
            key={floor.id}
            role="tab"
            aria-selected={active}
            onClick={() => onSelect(floor.id)}
            sx={{
              flexShrink: 0,
              gap: 1,
              px: 1.75,
              py: 1,
              borderRadius: 999,
              border: 1.5,
              borderColor: active ? 'text.primary' : 'divider',
              bgcolor: active ? 'text.primary' : 'background.paper',
              color: active ? 'background.paper' : 'text.primary',
              transition: 'background-color 150ms, color 150ms',
            }}
          >
            <LayersOutlinedIcon sx={{ fontSize: 18, opacity: 0.8 }} />
            <Typography variant="body2" fontWeight={700} noWrap>
              {floor.name}
            </Typography>
            <Box
              component="span"
              sx={{
                px: 0.9,
                py: 0.1,
                borderRadius: 999,
                fontSize: 12,
                fontWeight: 700,
                bgcolor: free ? TABLE_COLORS.free.top : 'action.hover',
                color: free ? TABLE_COLORS.free.text : 'text.secondary',
              }}
            >
              {free ? `${free} free` : 'full'}
            </Box>
          </ButtonBase>
        );
      })}
    </Box>
  );
}

/** Live counts with the drawing's colours — also the legend. */
export function StatusSummary({ counts, people }: { counts: Record<SpotStatus, number>; people: number }) {
  const items = [
    { label: 'Available', count: counts.free, color: TABLE_COLORS.free.line },
    { label: 'Booked', count: counts.taken, color: TABLE_COLORS.taken.line },
    { label: `Too small for ${people}`, count: counts.small, color: TABLE_COLORS.small.line },
    { label: 'For bigger groups', count: counts.bigGroups, color: TABLE_COLORS.small.line },
  ].filter((item) => item.label === 'Available' || item.count > 0);
  return (
    <Stack
      direction="row"
      spacing={2}
      useFlexGap
      flexWrap="wrap"
      sx={{ mb: 1.5, ml: { sm: 'auto' }, px: 1.5, py: 0.75, borderRadius: 999, border: 1, borderColor: 'divider', width: 'fit-content' }}
    >
      {items.map((item) => (
        <Stack key={item.label} direction="row" spacing={0.75} alignItems="center">
          <Box sx={{ width: 9, height: 9, borderRadius: '50%', bgcolor: item.color }} />
          <Typography variant="caption" fontWeight={600}>
            {item.label}
          </Typography>
          <Typography variant="caption" fontWeight={800}>
            {item.count}
          </Typography>
        </Stack>
      ))}
    </Stack>
  );
}
