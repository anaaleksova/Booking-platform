import { useState } from 'react';
import Box from '@mui/material/Box';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import RemoveRoundedIcon from '@mui/icons-material/RemoveRounded';
import { DecorShape, FloorPlanScene, TableShape, decorDrawOrder } from '@/features/floorplan/FloorPlanScene';
import type { ChoosableOption, PublicFloorPlan } from '@/types/api';
import { StatusSummary } from './SpotPanels';
import { captionOf, statusLabel, type SeatingRules, type SpotStatus } from './seatingRules';

const ZOOMS = [1, 1.5, 2, 2.5];

/**
 * One floor's drawing with what is free at the chosen time: free spots can be
 * picked, booked ones are shown but inert, ones that cannot seat the group are
 * dimmed. Live counts above double as the legend.
 */
export function SpotMap({
  plan,
  options,
  rules,
  picked,
  people,
  noun,
  onToggle,
}: {
  plan: PublicFloorPlan;
  options: Map<number, ChoosableOption>;
  rules: SeatingRules;
  picked: number[];
  people: number;
  noun: string;
  onToggle: (option: ChoosableOption) => void;
}) {
  const [zoom, setZoom] = useState(1);
  const aspect = Number(plan.aspectRatio);
  const counts: Record<SpotStatus, number> = { free: 0, taken: 0, small: 0, bigGroups: 0 };
  plan.spots.forEach((spot) => {
    const option = options.get(spot.resourceId);
    if (option) {
      counts[rules.statusOf(option)]++;
    }
  });

  return (
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
          const status = rules.statusOf(option);
          const isPicked = picked.includes(option.id);
          const left = rules.seatsLeftOf(option);
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
                      onClick: () => onToggle(option),
                      onKeyDown: (event) => {
                        if (event.key === 'Enter' || event.key === ' ') {
                          event.preventDefault();
                          onToggle(option);
                        }
                      },
                    }
              }
            />
          );
        })}
      </FloorPlanScene>
      <Stack sx={{ position: 'absolute', right: 12, bottom: 12, bgcolor: 'background.paper', borderRadius: 2, boxShadow: 2, overflow: 'hidden' }}>
        <IconButton size="small" aria-label="Zoom in" disabled={zoom >= ZOOMS[ZOOMS.length - 1]} onClick={() => setZoom(ZOOMS[ZOOMS.indexOf(zoom) + 1])}>
          <AddRoundedIcon fontSize="small" />
        </IconButton>
        <Divider />
        <IconButton size="small" aria-label="Zoom out" disabled={zoom <= ZOOMS[0]} onClick={() => setZoom(ZOOMS[ZOOMS.indexOf(zoom) - 1])}>
          <RemoveRoundedIcon fontSize="small" />
        </IconButton>
      </Stack>
    </Box>
  );
}
