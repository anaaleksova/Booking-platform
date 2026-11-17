import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import ButtonBase from '@mui/material/ButtonBase';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import ChevronLeftRoundedIcon from '@mui/icons-material/ChevronLeftRounded';
import ChevronRightRoundedIcon from '@mui/icons-material/ChevronRightRounded';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import type { Ref } from 'react';
import type { ResourceType } from '@/types/api';
import { BackgroundControls, type BackdropView } from './BackgroundControls';
import { DECOR_KINDS } from './decorKinds';
import { ROOM_SHAPES, type FloorDraft } from './editorModel';
import type { FloorDrafts } from './useFloorDrafts';

/**
 * The side panel while nothing is selected: the floor's name and shape,
 * what is still to place, and room pieces to add.
 */
export function FloorOverview({
  type,
  floor,
  drafts,
  resourceCount,
  nameRef,
  onDeleteFloor,
  backdropView,
  onBackdropViewChange,
}: {
  type: ResourceType;
  floor: FloorDraft;
  drafts: FloorDrafts;
  resourceCount: number;
  nameRef: Ref<HTMLInputElement>;
  onDeleteFloor: (floor: FloorDraft) => void;
  backdropView: BackdropView;
  onBackdropViewChange: (view: BackdropView) => void;
}) {
  const { floors, floorIndex, aspectRatio, unplaced, elsewhere, floorOf } = drafts;
  const preset = ROOM_SHAPES.some((shape) => shape.value === aspectRatio);
  return (
    <>
      <BackgroundControls
        background={floor.background}
        view={backdropView}
        onViewChange={onBackdropViewChange}
        onChange={drafts.setBackground}
      />

      <Divider />

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
          onChange={(event) => drafts.renameFloor(event.target.value)}
          error={!floor.name.trim()}
          helperText={!floor.name.trim() ? 'Every floor needs a name' : 'Shown to customers above the map.'}
          fullWidth
        />
        <TextField
          select
          size="small"
          label="Room shape"
          value={aspectRatio}
          onChange={(event) => drafts.changeAspect(Number(event.target.value))}
          fullWidth
          sx={{ mt: 2 }}
        >
          {preset ? null : (
            <MenuItem value={aspectRatio}>{floor.background ? 'Same as the plan image' : 'Custom'}</MenuItem>
          )}
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
                  <IconButton size="small" aria-label="Move floor earlier" disabled={floorIndex === 0} onClick={() => drafts.moveFloor(-1)}>
                    <ChevronLeftRoundedIcon />
                  </IconButton>
                </span>
              </Tooltip>
              <Tooltip title="Move this floor later">
                <span>
                  <IconButton
                    size="small"
                    aria-label="Move floor later"
                    disabled={floorIndex === floors.length - 1}
                    onClick={() => drafts.moveFloor(1)}
                  >
                    <ChevronRightRoundedIcon />
                  </IconButton>
                </span>
              </Tooltip>
            </>
          ) : null}
          <Box sx={{ flex: 1 }} />
          <Button size="small" color="error" startIcon={<DeleteOutlineRoundedIcon />} onClick={() => onDeleteFloor(floor)}>
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
                <Button size="small" startIcon={<AddRoundedIcon />} onClick={() => drafts.addTable(resource)}>
                  Place
                </Button>
              </Stack>
            ))}
          </Stack>
        ) : (
          <Typography variant="body2" color="text.secondary">
            {resourceCount ? 'Everything is placed.' : `Add some ${type.name.toLowerCase()} resources first.`}
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
                  <Button size="small" color="inherit" onClick={() => drafts.moveHere(resource)}>
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
                onClick={() => drafts.addPiece(option.kind)}
                sx={{
                  flexDirection: 'column',
                  gap: 0.5,
                  py: 1.25,
                  borderRadius: 2,
                  border: 1,
                  borderColor: 'divider',
                  color: 'text.secondary',
                  '&:hover': { borderColor: 'primary.main', color: 'primary.main' },
                }}
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
  );
}
