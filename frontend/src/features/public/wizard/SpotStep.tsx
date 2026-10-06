import { useEffect, useMemo, useState } from 'react';
import Alert from '@mui/material/Alert';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import ButtonBase from '@mui/material/ButtonBase';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import Paper from '@mui/material/Paper';
import Skeleton from '@mui/material/Skeleton';
import Stack from '@mui/material/Stack';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import { alpha } from '@mui/material/styles';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded';
import LayersOutlinedIcon from '@mui/icons-material/LayersOutlined';
import MapRoundedIcon from '@mui/icons-material/MapRounded';
import RemoveRoundedIcon from '@mui/icons-material/RemoveRounded';
import ShuffleRoundedIcon from '@mui/icons-material/ShuffleRounded';
import ViewListRoundedIcon from '@mui/icons-material/ViewListRounded';
import { errorMessage } from '@/api/client';
import { DecorShape, FloorPlanScene, TABLE_COLORS, TableShape, decorDrawOrder } from '@/features/floorplan/FloorPlanScene';
import { mediaUrl } from '@/features/media/api';
import { usePublicAvailability } from '@/features/public/api';
import type { AllocationOption, ChoosableOption, ChoosableRequirement, PublicFloorPlan, PublicService, Slot } from '@/types/api';
import { formatLocalDate } from '@/utils/format';
import { mappedRequirements } from './useWizardState';

/** 'small': too small (or too few seats left) for the group; 'bigGroups': kept for larger groups. */
type SpotStatus = 'free' | 'taken' | 'small' | 'bigGroups';

const ZOOMS = [1, 1.5, 2, 2.5];

/**
 * After the time is chosen: a map of the place with what is free at that time.
 * Free spots can be picked, taken ones are greyed out, ones too small for the
 * group are dimmed. "No preference" lets the business seat you.
 */
export function SpotStep({
  slug,
  service,
  people,
  date,
  time,
  picks,
  onPicks,
  onSlotGone,
}: {
  slug: string;
  service: PublicService;
  people: number;
  date: string;
  time: string;
  picks: Record<number, number[]>;
  onPicks: (picks: Record<number, number[]>) => void;
  onSlotGone: () => void;
}) {
  const availability = usePublicAvailability(slug, service.id, date, date, people);
  const slot = availability.data?.[0]?.slots.find((candidate) => candidate.time.slice(0, 5) === time);
  const requirements = useMemo(() => mappedRequirements(service), [service]);

  // A pick that is no longer free (someone was faster) is dropped.
  useEffect(() => {
    if (!slot) {
      return;
    }
    let changed = false;
    const next = { ...picks };
    requirements.forEach((requirement) => {
      const picked = next[requirement.requirementId];
      if (!picked) {
        return;
      }
      const stillFree = picked.filter((id) => freeResources(slot, requirement.requirementId).has(id));
      if (stillFree.length !== picked.length) {
        changed = true;
        // Half a combination would not seat the group: it goes as a whole.
        if (stillFree.length && !requirement.combinable) {
          next[requirement.requirementId] = stillFree;
        } else {
          delete next[requirement.requirementId];
        }
      }
    });
    if (changed) {
      onPicks(next);
    }
  }, [slot, picks, requirements, onPicks]);

  if (availability.isLoading) {
    return <Skeleton variant="rounded" height={360} />;
  }
  if (availability.isError) {
    return <Alert severity="warning">{errorMessage(availability.error)}</Alert>;
  }
  if (!slot) {
    return (
      <Alert
        severity="warning"
        action={
          <Button color="inherit" size="small" onClick={onSlotGone}>
            Choose another time
          </Button>
        }
      >
        {formatLocalDate(date)} at {time} is no longer available.
      </Alert>
    );
  }

  return (
    <Stack spacing={4}>
      {requirements.map((requirement) => (
        <RequirementMap
          key={requirement.requirementId}
          requirement={requirement}
          floors={requirement.floorPlans}
          slot={slot}
          people={people}
          when={`${formatLocalDate(date)} at ${time}`}
          picked={picks[requirement.requirementId] ?? []}
          onChange={(ids) => {
            const next = { ...picks };
            if (ids.length) {
              next[requirement.requirementId] = ids;
            } else {
              delete next[requirement.requirementId];
            }
            onPicks(next);
          }}
        />
      ))}
    </Stack>
  );
}

function RequirementMap({
  requirement,
  floors,
  slot,
  people,
  when,
  picked,
  onChange,
}: {
  requirement: ChoosableRequirement;
  floors: PublicFloorPlan[];
  slot: Slot;
  people: number;
  when: string;
  picked: number[];
  onChange: (ids: number[]) => void;
}) {
  const [view, setView] = useState<'map' | 'list'>('map');
  const [focusedId, setFocusedId] = useState<number | null>(picked[0] ?? null);
  const combined = requirement.combinable;
  const many = requirement.quantity > 1 && !combined;
  const noun = requirement.resourceTypeName.toLowerCase();

  const free = useMemo(() => freeResources(slot, requirement.requirementId), [slot, requirement.requirementId]);
  /** The server's ways to seat the group at this time, best first (combinable requirements only). */
  const suggestions = useMemo(
    () => slot.requirements.find((candidate) => candidate.requirementId === requirement.requirementId)?.allocations ?? [],
    [slot, requirement.requirementId],
  );
  const suggestionWith = (id: number) => suggestions.find((allocation) => allocation.resources.some((resource) => resource.id === id));
  const isPicked = (allocation: AllocationOption) =>
    allocation.resources.length === picked.length && allocation.resources.every((resource) => picked.includes(resource.id));
  /** Seats still free on a shared table at this time; null for private ones. */
  const seatsLeftOf = (option: ChoosableOption): number | null =>
    option.shared ? (free.has(option.id) ? (free.get(option.id) ?? option.capacity) : 0) : null;
  const statusOf = (option: ChoosableOption): SpotStatus => {
    if (combined) {
      // A small table is still useful when the server can combine it with others.
      if (!option.shared && option.minPartySize && people < option.minPartySize) {
        return 'bigGroups';
      }
      if (!free.has(option.id)) {
        return 'taken';
      }
      const left = seatsLeftOf(option) ?? option.capacity;
      const fitsAlone = left === null || left >= people;
      return (fitsAlone && requirement.quantity <= 1) || suggestionWith(option.id) ? 'free' : 'small';
    }
    // On its own a small spot can never seat the group; with several spots combined it might.
    if (!many && !option.shared && option.minPartySize && people < option.minPartySize) {
      return 'bigGroups';
    }
    if (!many && option.capacity !== null && option.capacity < people) {
      return 'small';
    }
    if (!free.has(option.id)) {
      return 'taken';
    }
    const left = seatsLeftOf(option);
    return !many && left !== null && left < people ? 'small' : 'free';
  };
  const options = new Map(requirement.resources.map((option) => [option.id, option]));
  const onMap = new Set(floors.flatMap((floor) => floor.spots.map((spot) => spot.resourceId)));
  const offMap = requirement.resources.filter((option) => !onMap.has(option.id) && statusOf(option) === 'free');
  const freeCount = requirement.resources.filter((option) => statusOf(option) === 'free').length;
  const freeOn = (floor: PublicFloorPlan) =>
    floor.spots.filter((spot) => {
      const option = options.get(spot.resourceId);
      return option !== undefined && statusOf(option) === 'free';
    }).length;

  // Open on the floor of the customer's pick, else the first floor with something free.
  const [floorId, setFloorId] = useState<number>(() => {
    const withPick = floors.find((floor) => floor.spots.some((spot) => picked.includes(spot.resourceId)));
    return (withPick ?? floors.find((floor) => freeOn(floor) > 0) ?? floors[0]).id;
  });
  const plan = floors.find((floor) => floor.id === floorId) ?? floors[0];
  const counts: Record<SpotStatus, number> = { free: 0, taken: 0, small: 0, bigGroups: 0 };
  plan.spots.forEach((spot) => {
    const option = options.get(spot.resourceId);
    if (option) {
      counts[statusOf(option)]++;
    }
  });
  const aspect = Number(plan.aspectRatio);
  const [zoom, setZoom] = useState(1);
  const floorOfOption = (id: number) => floors.find((floor) => floor.spots.some((spot) => spot.resourceId === id));

  const toggle = (option: ChoosableOption) => {
    if (statusOf(option) === 'taken') {
      return;
    }
    setFocusedId(option.id);
    if (statusOf(option) !== 'free') {
      return;
    }
    if (combined) {
      // Tables are never assembled by hand: a click picks the best way to seat
      // the group that uses this table — the table alone when it is enough.
      if (picked.includes(option.id)) {
        onChange([]);
        return;
      }
      const left = seatsLeftOf(option) ?? option.capacity;
      const fitsAlone = (left === null || left >= people) && requirement.quantity <= 1;
      const suggestion = suggestionWith(option.id);
      onChange(fitsAlone ? [option.id] : suggestion ? suggestion.resources.map((resource) => resource.id) : []);
      return;
    }
    if (picked.includes(option.id)) {
      onChange(picked.filter((id) => id !== option.id));
    } else if (!many) {
      onChange([option.id]);
    } else {
      onChange(picked.length >= requirement.quantity ? [...picked.slice(1), option.id] : [...picked, option.id]);
    }
  };
  const focused = focusedId !== null ? options.get(focusedId) : undefined;

  return (
    <section>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} justifyContent="space-between" alignItems={{ sm: 'flex-end' }} sx={{ mb: 2 }}>
        <div>
          <Typography variant="h6" fontWeight={800}>
            {many ? `Choose ${requirement.quantity} ${noun}s` : combined && suggestions.some((s) => s.resources.length > 1) ? `Choose your seating` : `Choose your ${noun}`}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {freeCount} free {when}
            {many ? ` · ${picked.length} of ${requirement.quantity} selected` : ''}. Optional — with no preference we seat you at the best free one.
          </Typography>
        </div>
        <ToggleButtonGroup size="small" exclusive value={view} onChange={(_, value) => value && setView(value)} aria-label="View">
          <ToggleButton value="map" aria-label="Map view">
            <MapRoundedIcon fontSize="small" sx={{ mr: 0.5 }} /> Map
          </ToggleButton>
          <ToggleButton value="list" aria-label="List view">
            <ViewListRoundedIcon fontSize="small" sx={{ mr: 0.5 }} /> List
          </ToggleButton>
        </ToggleButtonGroup>
      </Stack>

      <ButtonBase
        onClick={() => onChange([])}
        aria-pressed={picked.length === 0}
        sx={{
          width: '100%',
          justifyContent: 'flex-start',
          gap: 1.5,
          p: 1.5,
          mb: 2,
          borderRadius: 3,
          border: 2,
          borderColor: picked.length === 0 ? 'primary.main' : 'divider',
          bgcolor: picked.length === 0 ? (theme) => alpha(theme.palette.primary.main, 0.05) : 'background.paper',
          textAlign: 'left',
        }}
      >
        <Avatar sx={{ bgcolor: 'action.selected', color: 'text.primary' }}>
          <ShuffleRoundedIcon />
        </Avatar>
        <Box sx={{ flex: 1 }}>
          <Typography fontWeight={700}>No preference</Typography>
          <Typography variant="caption" color="text.secondary">
            We pick the best free {noun} for your group
          </Typography>
        </Box>
        {picked.length === 0 ? <CheckCircleRoundedIcon color="primary" /> : null}
      </ButtonBase>

      {combined && suggestions.length ? (
        <Suggestions
          suggestions={suggestions}
          people={people}
          noun={noun}
          isPicked={isPicked}
          onPick={(allocation) => {
            setFocusedId(allocation.resources[0].id);
            onChange(allocation.resources.map((resource) => resource.id));
          }}
        />
      ) : null}

      {view === 'map' ? (
        <>
          {floors.length > 1 ? (
            <FloorPicker floors={floors} activeId={plan.id} freeOn={freeOn} onSelect={setFloorId} />
          ) : null}
          <Box sx={{ position: 'relative' }}>
            <StatusSummary counts={counts} people={people} />
            <FloorPlanScene aspectRatio={aspect} zoom={zoom} minWidth={620} label={`Map of ${noun}s`}>
              {decorDrawOrder(plan.decor ?? []).map((piece, index) => (
                <DecorShape key={index} decor={piece} aspectRatio={aspect} groupProps={{ 'aria-hidden': true, style: { pointerEvents: 'none' } }} />
              ))}
              {plan.spots.map((spot) => {
                const option = options.get(spot.resourceId);
                if (!option) {
                  return null;
                }
                const status = statusOf(option);
                const isPicked = picked.includes(option.id);
                const left = seatsLeftOf(option);
                const description = `${option.name}${option.capacity ? `, ${option.capacity} seats` : ''}${option.shared ? ', shared' : ''} — ${statusLabel(status, people, option, left)}`;
                return (
                  <TableShape
                    key={spot.resourceId}
                    spot={spot}
                    aspectRatio={aspect}
                    name={option.name}
                    caption={captionOf(option, status, left)}
                    seats={option.capacity}
                    takenSeats={option.shared && option.capacity !== null && left !== null ? option.capacity - left : 0}
                    state={isPicked ? 'selected' : status === 'bigGroups' ? 'small' : status}
                    tooltip={description}
                    groupProps={
                      status === 'taken'
                        ? // Booked: shown, but cannot be selected or focused.
                          { 'aria-label': description, 'aria-disabled': true, style: { cursor: 'not-allowed' } }
                        : {
                            role: 'button',
                            tabIndex: 0,
                            'aria-label': description,
                            'aria-pressed': status === 'free' ? isPicked : undefined,
                            'aria-disabled': status !== 'free' || undefined,
                            onClick: () => toggle(option),
                            onKeyDown: (event) => {
                              if (event.key === 'Enter' || event.key === ' ') {
                                event.preventDefault();
                                toggle(option);
                              }
                            },
                          }
                    }
                  />
                );
              })}
            </FloorPlanScene>
            <Stack
              sx={{ position: 'absolute', right: 12, bottom: 12, bgcolor: 'background.paper', borderRadius: 2, boxShadow: 2, overflow: 'hidden' }}
            >
              <IconButton size="small" aria-label="Zoom in" disabled={zoom >= ZOOMS[ZOOMS.length - 1]} onClick={() => setZoom(ZOOMS[ZOOMS.indexOf(zoom) + 1])}>
                <AddRoundedIcon fontSize="small" />
              </IconButton>
              <Divider />
              <IconButton size="small" aria-label="Zoom out" disabled={zoom <= ZOOMS[0]} onClick={() => setZoom(ZOOMS[ZOOMS.indexOf(zoom) - 1])}>
                <RemoveRoundedIcon fontSize="small" />
              </IconButton>
            </Stack>
          </Box>
          <Typography variant="caption" color="text.secondary" sx={{ display: { xs: 'block', sm: 'none' }, mt: 1 }}>
            Swipe the map to see the whole room.
          </Typography>

          {focused && picked.length > 1 && picked.includes(focused.id) ? (
            <SelectionDetails
              tables={picked.map((id) => options.get(id)).filter((option): option is ChoosableOption => option !== undefined)}
              seatsOf={(option) => seatsLeftOf(option) ?? option.capacity}
              floorName={floors.length > 1 ? floorOfOption(focused.id)?.name : undefined}
              people={people}
              onClear={() => onChange([])}
            />
          ) : focused ? (
            <SpotDetails
              option={focused}
              combined={combined}
              status={statusOf(focused)}
              seatsLeft={seatsLeftOf(focused)}
              floorName={floors.length > 1 ? floorOfOption(focused.id)?.name : undefined}
              picked={picked.includes(focused.id)}
              people={people}
              onToggle={() => toggle(focused)}
            />
          ) : null}

          {offMap.length ? (
            <Box sx={{ mt: 2 }}>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                Also free, not shown on the map:
              </Typography>
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                {offMap.map((option) => (
                  <Chip
                    key={option.id}
                    label={`${option.name}${option.capacity ? ` · ${option.capacity} seats` : ''}`}
                    color={picked.includes(option.id) ? 'primary' : 'default'}
                    variant={picked.includes(option.id) ? 'filled' : 'outlined'}
                    onClick={() => toggle(option)}
                  />
                ))}
              </Stack>
            </Box>
          ) : null}
        </>
      ) : (
        <Stack spacing={1}>
          {requirement.resources.map((option) => {
            const status = statusOf(option);
            const isPicked = picked.includes(option.id);
            return (
              <ButtonBase
                key={option.id}
                onClick={() => toggle(option)}
                disabled={status !== 'free'}
                aria-pressed={isPicked}
                sx={{
                  justifyContent: 'flex-start',
                  gap: 1.5,
                  p: 1.5,
                  borderRadius: 3,
                  border: 2,
                  borderColor: isPicked ? 'primary.main' : 'divider',
                  textAlign: 'left',
                  opacity: status === 'free' ? 1 : 0.55,
                }}
              >
                <Avatar src={mediaUrl(option.imageUrl)} sx={{ bgcolor: 'primary.main' }}>
                  {option.name.charAt(0)}
                </Avatar>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Typography fontWeight={700}>{option.name}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    {[option.capacity ? `${option.capacity} seats` : null, option.description].filter(Boolean).join(' · ')}
                  </Typography>
                </Box>
                {isPicked ? <CheckCircleRoundedIcon color="primary" /> : status !== 'free' ? <Chip size="small" label={statusLabel(status, people, option, seatsLeftOf(option))} /> : null}
              </ButtonBase>
            );
          })}
        </Stack>
      )}
    </section>
  );
}

/** The customer's choice when it is several tables put together — all of them, not just the one clicked. */
function SelectionDetails({
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
      <Button variant="outlined" onClick={onClear} sx={{ borderRadius: 999, flexShrink: 0 }}>
        Selected ✓
      </Button>
    </Paper>
  );
}

function SpotDetails({
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
        <Button variant={picked ? 'outlined' : 'contained'} onClick={onToggle} sx={{ borderRadius: 999, flexShrink: 0 }}>
          {picked ? 'Selected ✓' : 'Choose'}
        </Button>
      ) : null}
    </Paper>
  );
}

/**
 * Ready-made ways to seat the group, found by the server — "Table 3 + Table 4".
 * The customer never has to put tables together themselves.
 */
function Suggestions({
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
        Available seating for {people}:
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
function FloorPicker({
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
function StatusSummary({ counts, people }: { counts: Record<SpotStatus, number>; people: number }) {
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

function statusLabel(status: SpotStatus, people: number, option: ChoosableOption, seatsLeft: number | null): string {
  switch (status) {
    case 'free':
      return seatsLeft !== null ? `${seatsLeft} seats free` : 'available';
    case 'taken':
      return 'booked';
    case 'bigGroups':
      return `for groups of ${option.minPartySize}+`;
    default:
      return seatsLeft !== null && option.capacity !== null && seatsLeft < option.capacity
        ? `only ${seatsLeft} seats left`
        : `too small for ${people}`;
  }
}

/** The second line on a table in the drawing. */
function captionOf(option: ChoosableOption, status: SpotStatus, seatsLeft: number | null): string | undefined {
  if (status === 'taken') {
    return 'Booked';
  }
  if (status === 'bigGroups') {
    return `Groups of ${option.minPartySize}+`;
  }
  if (seatsLeft !== null && option.capacity !== null) {
    return seatsLeft < option.capacity ? `${seatsLeft} of ${option.capacity} free` : `${option.capacity} seats · shared`;
  }
  return option.capacity ? `${option.capacity} seats` : undefined;
}

/** Free resources at this time → seats left (shared resources) or null (private ones). */
function freeResources(slot: Slot, requirementId: number): Map<number, number | null> {
  const requirement = slot.requirements.find((candidate) => candidate.requirementId === requirementId);
  return new Map((requirement?.resources ?? []).map((resource) => [resource.id, resource.seatsLeft ?? null]));
}
