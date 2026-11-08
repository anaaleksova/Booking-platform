import { useEffect, useMemo, useState } from 'react';
import Alert from '@mui/material/Alert';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import ButtonBase from '@mui/material/ButtonBase';
import Chip from '@mui/material/Chip';
import Skeleton from '@mui/material/Skeleton';
import Stack from '@mui/material/Stack';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import { alpha } from '@mui/material/styles';
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded';
import MapRoundedIcon from '@mui/icons-material/MapRounded';
import ShuffleRoundedIcon from '@mui/icons-material/ShuffleRounded';
import ViewListRoundedIcon from '@mui/icons-material/ViewListRounded';
import { errorMessage } from '@/api/client';
import { usePublicAvailability } from '@/features/public/api';
import type { ChoosableOption, ChoosableRequirement, PublicFloorPlan, PublicService, Slot } from '@/types/api';
import { formatLocalDate } from '@/utils/format';
import { mappedRequirements } from './useWizardState';
import { FloorPicker, SelectionDetails, SpotDetails, Suggestions } from './spot/SpotPanels';
import { SpotList } from './spot/SpotList';
import { SpotMap } from './spot/SpotMap';
import { seatingRules, withoutTakenPicks } from './spot/seatingRules';

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
    const next = slot ? withoutTakenPicks(requirements, slot, picks) : null;
    if (next) {
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
  const rules = useMemo(() => seatingRules(requirement, slot, people), [requirement, slot, people]);
  const { combined, many, suggestions, seatsLeftOf, statusOf } = rules;
  const noun = requirement.resourceTypeName.toLowerCase();
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
  const floorOfOption = (id: number) => floors.find((floor) => floor.spots.some((spot) => spot.resourceId === id));

  const toggle = (option: ChoosableOption) => {
    if (statusOf(option) === 'taken') {
      return;
    }
    setFocusedId(option.id);
    const next = rules.afterClick(option, picked);
    if (next) {
      onChange(next);
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

      {combined && rules.recommended.length ? (
        <Suggestions
          suggestions={rules.recommended}
          people={people}
          noun={noun}
          isPicked={(allocation) => rules.isSuggestionPicked(allocation, picked)}
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
          <SpotMap plan={plan} options={options} rules={rules} picked={picked} people={people} noun={noun} onToggle={toggle} />
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
        <SpotList resources={requirement.resources} rules={rules} picked={picked} people={people} onToggle={toggle} />
      )}
    </section>
  );
}
