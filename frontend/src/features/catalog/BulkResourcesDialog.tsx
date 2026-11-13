import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import Grid from '@mui/material/Grid';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { errorMessage } from '@/api/client';
import { useToast } from '@/components/ToastProvider';
import { WeeklyHoursEditor } from '@/components/WeeklyHoursEditor';
import type { DayInterval, ResourceType } from '@/types/api';
import { pluralize } from '@/utils/format';
import { useCreateResources } from './api';

/** The most the server creates at once. */
const MAX = 100;

/** "Table 1" … "Table 20" with the same seats and hours: setting up a whole room in one go. */
export function BulkResourcesDialog({
  types,
  initialTypeId,
  onClose,
}: {
  types: ResourceType[];
  initialTypeId?: number;
  onClose: () => void;
}) {
  const toast = useToast();
  const create = useCreateResources();
  const firstType = types.find((type) => type.id === initialTypeId) ?? types[0];
  const [typeId, setTypeId] = useState<number | ''>(firstType?.id ?? '');
  const [prefix, setPrefix] = useState(firstType?.name ?? '');
  const [first, setFirst] = useState('1');
  const [last, setLast] = useState('10');
  const [capacity, setCapacity] = useState('4');
  const [shared, setShared] = useState(false);
  const [ownHours, setOwnHours] = useState(false);
  const [hours, setHours] = useState<DayInterval[]>([]);

  const from = Number(first);
  const to = Number(last);
  const count = Number.isInteger(from) && Number.isInteger(to) && to >= from ? to - from + 1 : 0;
  const resourceTypeId = typeId === '' ? null : typeId;
  const valid = resourceTypeId !== null && prefix.trim() !== '' && count > 0 && count <= MAX;

  const submit = () => {
    if (!valid || resourceTypeId === null) {
      return;
    }
    create
      .mutateAsync({
        resourceTypeId,
        namePrefix: prefix.trim(),
        firstNumber: from,
        lastNumber: to,
        capacity: capacity === '' ? null : Math.max(1, Number(capacity)),
        sharedSeating: capacity !== '' && shared,
        minPartySize: null,
        active: true,
        serviceIds: [],
        hours: ownHours ? hours : [],
      })
      .then((result) => {
        toast.success(
          `${pluralize(result.created.length, 'resource')} added` +
            (result.skipped.length ? ` — ${result.skipped.join(', ')} already existed` : ''),
        );
        onClose();
      })
      .catch((err) => toast.error(errorMessage(err)));
  };

  return (
    <Dialog open onClose={create.isPending ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Add several resources</DialogTitle>
      <DialogContent>
        <Grid container spacing={2} sx={{ pt: 1 }}>
          <Grid item xs={12} sm={6}>
            <TextField
              select
              fullWidth
              label="Type"
              value={typeId}
              onChange={(e) => {
                const type = types.find((t) => t.id === Number(e.target.value));
                // Keep a typed prefix; follow the type while it is still the default.
                if (type && types.some((t) => t.name === prefix)) {
                  setPrefix(type.name);
                }
                setTypeId(Number(e.target.value));
              }}
            >
              {types.map((type) => (
                <MenuItem key={type.id} value={type.id}>
                  {type.name}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid item xs={12} sm={6}>
            <TextField fullWidth label="Name" value={prefix} onChange={(e) => setPrefix(e.target.value)} />
          </Grid>
          <Grid item xs={6} sm={3}>
            <TextField fullWidth type="number" label="From" value={first} onChange={(e) => setFirst(e.target.value)} />
          </Grid>
          <Grid item xs={6} sm={3}>
            <TextField fullWidth type="number" label="To" value={last} onChange={(e) => setLast(e.target.value)} />
          </Grid>
          <Grid item xs={12} sm={6}>
            <TextField
              fullWidth
              type="number"
              label="Capacity each"
              value={capacity}
              onChange={(e) => setCapacity(e.target.value)}
              helperText="Empty: not limited by group size"
            />
          </Grid>
          <Grid item xs={12}>
            {count > MAX ? (
              <Alert severity="warning">Up to {MAX} can be added at once.</Alert>
            ) : count > 0 && prefix.trim() ? (
              <Typography variant="body2" color="text.secondary">
                {pluralize(count, 'resource')}: {prefix.trim()} {from}
                {count > 1 ? ` … ${prefix.trim()} ${to}` : ''}. Names that already exist are skipped.
              </Typography>
            ) : null}
          </Grid>
          {capacity !== '' ? (
            <Grid item xs={12}>
              <FormControlLabel
                control={<Checkbox checked={shared} onChange={(e) => setShared(e.target.checked)} />}
                label="Shared: several groups can sit at each until its seats are taken"
              />
            </Grid>
          ) : null}
          <Grid item xs={12}>
            <Stack spacing={1}>
              <FormControlLabel
                control={<Switch checked={ownHours} onChange={(e) => setOwnHours(e.target.checked)} />}
                label="Their own hours (otherwise they follow the opening hours)"
              />
              {ownHours ? <WeeklyHoursEditor value={hours} onChange={setHours} /> : null}
            </Stack>
          </Grid>
        </Grid>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={create.isPending}>
          Cancel
        </Button>
        <Button variant="contained" onClick={submit} disabled={!valid || create.isPending}>
          Add {count > 0 && count <= MAX ? count : ''}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
