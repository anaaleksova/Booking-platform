import { useState } from 'react';
import Autocomplete from '@mui/material/Autocomplete';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { errorMessage } from '@/api/client';
import { useToast } from '@/components/ToastProvider';
import type { DayInterval } from '@/types/api';
import { dayName, formatInterval, pluralize } from '@/utils/format';
import { useCopySchedule } from './api';

export interface ScheduleOwner {
  /** The resource id: an employee's is its resource. */
  resourceId: number;
  name: string;
  hours: DayInterval[];
}

/** "Mon 09:00 – 13:00 · Wed 14:00 – 18:00", or that it follows the opening hours. */
function describeWeek(hours: DayInterval[]) {
  return hours.length === 0
    ? 'Follows the opening hours'
    : [...hours]
        .sort((a, b) => a.dayOfWeek - b.dayOfWeek || a.startTime.localeCompare(b.startTime))
        .map((interval) => `${dayName(interval.dayOfWeek).slice(0, 3)} ${formatInterval(interval)}`)
        .join(' · ');
}

/** Gives others of the same kind this one's weekly schedule — and optionally the services it serves. */
export function CopyScheduleDialog({
  source,
  candidates,
  noun,
  onClose,
}: {
  source: ScheduleOwner;
  /** Others of the same type. */
  candidates: ScheduleOwner[];
  /** "resource" or "employee", for the wording. */
  noun: string;
  onClose: () => void;
}) {
  const toast = useToast();
  const copy = useCopySchedule();
  const others = candidates.filter((candidate) => candidate.resourceId !== source.resourceId);
  const [targets, setTargets] = useState<ScheduleOwner[]>([]);
  const [includeServices, setIncludeServices] = useState(false);

  const submit = () =>
    copy
      .mutateAsync({ id: source.resourceId, targetIds: targets.map((t) => t.resourceId), includeServices })
      .then((changed) => {
        toast.success(`Schedule copied to ${pluralize(changed.length, noun)}`);
        onClose();
      })
      .catch((err) => toast.error(errorMessage(err)));

  return (
    <Dialog open onClose={copy.isPending ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Copy {source.name}'s hours</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Typography variant="body2" color="text.secondary">
            {describeWeek(source.hours)}
          </Typography>
          <Autocomplete
            multiple
            options={others}
            value={targets}
            onChange={(_, value) => setTargets(value)}
            getOptionLabel={(option) => option.name}
            isOptionEqualToValue={(a, b) => a.resourceId === b.resourceId}
            disableCloseOnSelect
            renderInput={(params) => <TextField {...params} label={`Copy to`} placeholder={`Choose ${noun}s`} />}
          />
          <Button
            size="small"
            sx={{ alignSelf: 'flex-start' }}
            disabled={others.length === 0}
            onClick={() => setTargets(others)}
          >
            Select all {others.length}
          </Button>
          <FormControlLabel
            control={<Checkbox checked={includeServices} onChange={(e) => setIncludeServices(e.target.checked)} />}
            label="Also copy which services it serves"
          />
          <Typography variant="caption" color="text.secondary">
            Their own hours are replaced. Existing bookings are not moved.
          </Typography>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={copy.isPending}>
          Cancel
        </Button>
        <Button variant="contained" onClick={submit} disabled={targets.length === 0 || copy.isPending}>
          Copy to {pluralize(targets.length, noun)}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
