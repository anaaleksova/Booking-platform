import { useState } from 'react';
import Autocomplete from '@mui/material/Autocomplete';
import Avatar from '@mui/material/Avatar';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import Grid from '@mui/material/Grid';
import IconButton from '@mui/material/IconButton';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import EditRoundedIcon from '@mui/icons-material/EditRounded';
import GroupsRoundedIcon from '@mui/icons-material/GroupsRounded';
import { errorMessage } from '@/api/client';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { ImageUpload } from '@/components/ImageUpload';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import { WeeklyHoursEditor } from '@/components/WeeklyHoursEditor';
import {
  useDeleteEmployee,
  useEmployees,
  useSaveEmployee,
  useServices,
  useSetEmployeeActive,
} from '@/features/catalog/api';
import { mediaUrl } from '@/features/media/api';
import type { DayInterval, Employee } from '@/types/api';
import { dayName, formatInterval } from '@/utils/format';

interface Draft {
  id?: number;
  imageMediaId: string | null;
  imageUrl: string | null;
  name: string;
  email: string;
  phone: string;
  capacity: string;
  active: boolean;
  serviceIds: number[];
  ownHours: boolean;
  hours: DayInterval[];
}

/**
 * The team. Each employee is also a resource of the "Employee" type, so a
 * service needing "2 × Employee" is scheduled by the same engine as rooms.
 */
export function EmployeesPage() {
  const toast = useToast();
  const { data: employees, isLoading, isError, error, refetch } = useEmployees();
  const { data: services } = useServices();
  const save = useSaveEmployee();
  const setActive = useSetEmployeeActive();
  const remove = useDeleteEmployee();
  const [draft, setDraft] = useState<Draft | null>(null);
  const [deleting, setDeleting] = useState<Employee | null>(null);

  const open = (employee?: Employee) =>
    setDraft(
      employee
        ? {
            id: employee.id,
            imageMediaId: employee.imageMediaId ?? null,
            imageUrl: employee.imageUrl ?? null,
            name: employee.name,
            email: employee.email ?? '',
            phone: employee.phone ?? '',
            capacity: employee.capacity === null ? '' : String(employee.capacity),
            active: employee.active,
            serviceIds: employee.serviceIds,
            ownHours: employee.workingHours.length > 0,
            hours: employee.workingHours,
          }
        : { imageMediaId: null, imageUrl: null, name: '', email: '', phone: '', capacity: '1', active: true, serviceIds: [], ownHours: false, hours: [] },
    );

  const submit = async () => {
    if (!draft?.name.trim()) {
      return;
    }
    try {
      await save.mutateAsync({
        id: draft.id,
        input: {
          name: draft.name.trim(),
          email: draft.email.trim() || undefined,
          phone: draft.phone.trim() || undefined,
          imageMediaId: draft.imageMediaId,
          capacity: draft.capacity === '' ? null : Math.max(1, Number(draft.capacity)),
          active: draft.active,
          serviceIds: draft.serviceIds,
        },
        hours: draft.ownHours ? draft.hours : [],
      });
      toast.success(draft.id ? 'Employee saved' : 'Employee added');
      setDraft(null);
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  return (
    <>
      <PageHeader
        title="Employees"
        description="Who works here, which services they perform and when they work."
        action={
          <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={() => open()}>
            New employee
          </Button>
        }
      />
      {isError ? <ErrorState error={error} onRetry={() => refetch()} /> : null}
      {isLoading ? (
        <LoadingState />
      ) : employees?.length === 0 ? (
        <EmptyState
          icon={<GroupsRoundedIcon />}
          title="No employees yet"
          description="Add the people who deliver your services. Services that need an employee use them automatically."
        />
      ) : (
        <Grid container spacing={2}>
          {employees?.map((employee) => (
            <Grid item xs={12} sm={6} lg={4} key={employee.id}>
              <Paper variant="outlined" sx={{ p: 2.5, height: '100%', opacity: employee.active ? 1 : 0.6 }}>
                <Stack direction="row" spacing={1.5} alignItems="center">
                  <Avatar src={mediaUrl(employee.imageUrl)} sx={{ bgcolor: 'primary.main', width: 48, height: 48 }}>
                    {employee.name.charAt(0).toUpperCase()}
                  </Avatar>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <Typography variant="subtitle1" fontWeight={700} noWrap>
                      {employee.name}
                    </Typography>
                    <Typography variant="caption" color="text.secondary" noWrap display="block">
                      {[employee.email, employee.phone].filter(Boolean).join(' · ') || 'No contact details'}
                    </Typography>
                  </div>
                  <Switch
                    checked={employee.active}
                    inputProps={{ 'aria-label': `${employee.name} active` }}
                    onChange={(e) =>
                      setActive
                        .mutateAsync({ id: employee.id, active: e.target.checked })
                        .catch((err) => toast.error(errorMessage(err)))
                    }
                  />
                </Stack>
                <Typography variant="caption" color="text.secondary" fontWeight={600} display="block" sx={{ mt: 2 }}>
                  Performs
                </Typography>
                <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap" sx={{ mt: 0.5 }}>
                  {employee.serviceIds.length === 0 ? (
                    <Chip size="small" label="All services" variant="outlined" />
                  ) : (
                    employee.serviceIds.map((id) => (
                      <Chip key={id} size="small" variant="outlined" label={services?.find((s) => s.id === id)?.name ?? id} />
                    ))
                  )}
                </Stack>
                <Typography variant="caption" color="text.secondary" fontWeight={600} display="block" sx={{ mt: 1.5 }}>
                  Works
                </Typography>
                <Typography variant="body2">
                  {employee.workingHours.length === 0
                    ? 'Whenever the business is open'
                    : summarize(employee.workingHours)}
                </Typography>
                <Stack direction="row" justifyContent="flex-end" sx={{ mt: 1 }}>
                  <Tooltip title="Edit">
                    <IconButton onClick={() => open(employee)}>
                      <EditRoundedIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="Delete">
                    <IconButton onClick={() => setDeleting(employee)}>
                      <DeleteOutlineRoundedIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </Stack>
              </Paper>
            </Grid>
          ))}
        </Grid>
      )}

      <Dialog open={draft !== null} onClose={() => setDraft(null)} maxWidth="sm" fullWidth>
        <DialogTitle>{draft?.id ? 'Edit employee' : 'New employee'}</DialogTitle>
        {draft ? (
          <DialogContent>
            <Stack spacing={2} sx={{ mt: 1 }}>
              <ImageUpload
                label="Photo"
                hint="Customers see it when choosing who serves them."
                url={draft.imageUrl}
                aspectRatio="1 / 1"
                width={112}
                round
                onChange={(media) => setDraft({ ...draft, imageMediaId: media?.id ?? null, imageUrl: media?.url ?? null })}
              />
              <TextField autoFocus label="Name" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} fullWidth />
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
                <TextField label="Email" value={draft.email} onChange={(e) => setDraft({ ...draft, email: e.target.value })} fullWidth />
                <TextField label="Phone" value={draft.phone} onChange={(e) => setDraft({ ...draft, phone: e.target.value })} fullWidth />
              </Stack>
              <TextField
                label="Capacity"
                type="number"
                value={draft.capacity}
                onChange={(e) => setDraft({ ...draft, capacity: e.target.value })}
                inputProps={{ min: 1 }}
                helperText="Participants they can serve at once — 1 for one-to-one work, more for a group instructor."
                fullWidth
              />
              <Autocomplete
                multiple
                options={services ?? []}
                getOptionLabel={(option) => option.name}
                value={(services ?? []).filter((service) => draft.serviceIds.includes(service.id))}
                onChange={(_, value) => setDraft({ ...draft, serviceIds: value.map((service) => service.id) })}
                renderInput={(params) => (
                  <TextField {...params} label="Performs only" helperText="Empty = every service that needs an employee" />
                )}
              />
              <FormControlLabel control={<Switch checked={draft.active} onChange={(e) => setDraft({ ...draft, active: e.target.checked })} />} label="Active" />
              <FormControlLabel
                control={<Switch checked={draft.ownHours} onChange={(e) => setDraft({ ...draft, ownHours: e.target.checked })} />}
                label="Own working hours (otherwise works whenever the business is open)"
              />
              {draft.ownHours ? <WeeklyHoursEditor value={draft.hours} onChange={(hours) => setDraft({ ...draft, hours })} /> : null}
            </Stack>
          </DialogContent>
        ) : null}
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setDraft(null)}>Cancel</Button>
          <Button variant="contained" onClick={submit} disabled={!draft?.name.trim() || save.isPending}>
            Save
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={deleting !== null}
        title={`Delete ${deleting?.name ?? 'employee'}?`}
        message="An employee with reservations cannot be deleted — deactivate them instead."
        confirmLabel="Delete"
        busy={remove.isPending}
        onConfirm={() =>
          deleting &&
          remove
            .mutateAsync(deleting.id)
            .then(() => toast.success('Employee deleted'))
            .catch((err) => toast.error(errorMessage(err)))
            .finally(() => setDeleting(null))
        }
        onClose={() => setDeleting(null)}
      />
    </>
  );
}

function summarize(hours: DayInterval[]): string {
  return hours.map((interval) => `${dayName(interval.dayOfWeek).slice(0, 3)} ${formatInterval(interval)}`).join(', ');
}
