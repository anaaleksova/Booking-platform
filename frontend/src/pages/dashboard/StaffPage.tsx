import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Avatar from '@mui/material/Avatar';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Divider from '@mui/material/Divider';
import FormControlLabel from '@mui/material/FormControlLabel';
import Grid from '@mui/material/Grid';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import EditRoundedIcon from '@mui/icons-material/EditRounded';
import GroupsRoundedIcon from '@mui/icons-material/GroupsRounded';
import ScheduleRoundedIcon from '@mui/icons-material/ScheduleRounded';
import { PageHeader } from '@/components/PageHeader';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { EmptyState, ErrorState, LoadingState } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import { WeeklyHoursEditor } from '@/features/availability/WeeklyHoursEditor';
import { useServices } from '@/features/services/useServices';
import {
  useCreateStaff,
  useDeleteStaff,
  useReplaceStaffWorkingHours,
  useStaff,
  useUpdateStaff,
} from '@/features/staff/useStaff';
import { errorMessage } from '@/api/client';
import { dayName, formatInterval } from '@/utils/format';
import type { DayInterval, Staff } from '@/types/api';

export function StaffPage() {
  const toast = useToast();
  const { data: staff = [], isLoading, isError, error, refetch } = useStaff();
  const { data: services = [] } = useServices();
  const deleteStaff = useDeleteStaff();

  const [editing, setEditing] = useState<Staff | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [hoursFor, setHoursFor] = useState<Staff | null>(null);
  const [pendingDelete, setPendingDelete] = useState<Staff | null>(null);

  const handleDelete = async () => {
    if (!pendingDelete) return;
    try {
      await deleteStaff.mutateAsync(pendingDelete.id);
      toast.success('Staff member removed.');
    } catch (deleteError) {
      toast.error(errorMessage(deleteError));
    } finally {
      setPendingDelete(null);
    }
  };

  return (
    <>
      <PageHeader
        title="Staff"
        description="Who works here, what they do and when. A staff member does not need a login account."
        action={
          <Button
            variant="contained"
            startIcon={<AddRoundedIcon />}
            onClick={() => {
              setEditing(null);
              setDialogOpen(true);
            }}
          >
            Add staff
          </Button>
        }
      />

      {isError ? <ErrorState error={error} onRetry={() => void refetch()} /> : null}
      {isLoading ? <LoadingState /> : null}

      {!isLoading && staff.length === 0 ? (
        <EmptyState
          title="No staff yet"
          description="Add the people who deliver your services. You can assign each of them a subset of services."
          icon={<GroupsRoundedIcon />}
          action={
            <Button
              variant="contained"
              onClick={() => {
                setEditing(null);
                setDialogOpen(true);
              }}
            >
              Add your first staff member
            </Button>
          }
        />
      ) : (
        <Grid container spacing={2.5}>
          {staff.map((member) => (
            <Grid item xs={12} md={6} lg={4} key={member.id}>
              <Card sx={{ height: '100%' }}>
                <CardContent>
                  <Stack direction="row" spacing={2} alignItems="flex-start">
                    <Avatar src={member.photoUrl ?? undefined} sx={{ width: 48, height: 48 }}>
                      {member.name.charAt(0)}
                    </Avatar>
                    <Stack sx={{ flex: 1, minWidth: 0 }}>
                      <Stack direction="row" alignItems="center" spacing={1}>
                        <Typography variant="subtitle1" fontWeight={700} noWrap>
                          {member.name}
                        </Typography>
                        {!member.active ? <Chip size="small" label="Inactive" /> : null}
                      </Stack>
                      <Typography variant="body2" color="text.secondary" noWrap>
                        {member.email ?? member.phone ?? 'No contact details'}
                      </Typography>
                      {member.hasLoginAccount ? (
                        <Typography variant="caption" color="text.secondary">
                          Has a login account
                        </Typography>
                      ) : null}
                    </Stack>
                    <Stack direction="row">
                      <Tooltip title="Working hours">
                        <IconButton size="small" onClick={() => setHoursFor(member)}>
                          <ScheduleRoundedIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Edit">
                        <IconButton
                          size="small"
                          onClick={() => {
                            setEditing(member);
                            setDialogOpen(true);
                          }}
                        >
                          <EditRoundedIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Remove">
                        <IconButton size="small" onClick={() => setPendingDelete(member)}>
                          <DeleteOutlineRoundedIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </Stack>
                  </Stack>

                  <Divider sx={{ my: 2 }} />

                  <Typography variant="caption" color="text.secondary" display="block" gutterBottom>
                    Services
                  </Typography>
                  <Stack direction="row" flexWrap="wrap" gap={0.5} sx={{ mb: 2 }}>
                    {member.serviceIds.length === 0 ? (
                      <Chip size="small" variant="outlined" label="All services" />
                    ) : (
                      member.serviceIds.map((id) => (
                        <Chip
                          key={id}
                          size="small"
                          variant="outlined"
                          label={services.find((service) => service.id === id)?.name ?? `#${id}`}
                        />
                      ))
                    )}
                  </Stack>

                  <Typography variant="caption" color="text.secondary" display="block" gutterBottom>
                    Working hours
                  </Typography>
                  {member.workingHours.length === 0 ? (
                    <Typography variant="body2" color="text.secondary">
                      Follows business hours
                    </Typography>
                  ) : (
                    <Stack spacing={0.25}>
                      {member.workingHours.map((interval, index) => (
                        <Typography key={index} variant="body2">
                          {dayName(interval.dayOfWeek).slice(0, 3)} · {formatInterval(interval)}
                        </Typography>
                      ))}
                    </Stack>
                  )}
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      )}

      <StaffDialog
        open={dialogOpen}
        staff={editing}
        services={services.map((service) => ({ id: service.id, name: service.name }))}
        onClose={() => setDialogOpen(false)}
      />

      <StaffHoursDialog staff={hoursFor} onClose={() => setHoursFor(null)} />

      <ConfirmDialog
        open={pendingDelete !== null}
        title={`Remove ${pendingDelete?.name ?? ''}?`}
        message="Staff with existing bookings cannot be removed — deactivate them instead so their history is kept."
        confirmLabel="Remove"
        busy={deleteStaff.isPending}
        onClose={() => setPendingDelete(null)}
        onConfirm={() => void handleDelete()}
      />
    </>
  );
}

function StaffDialog({
  open,
  staff,
  services,
  onClose,
}: {
  open: boolean;
  staff: Staff | null;
  services: { id: number; name: string }[];
  onClose: () => void;
}) {
  const toast = useToast();
  const createStaff = useCreateStaff();
  const updateStaff = useUpdateStaff();

  const [form, setForm] = useState({ name: '', email: '', phone: '', photoUrl: '', active: true });
  const [serviceIds, setServiceIds] = useState<number[]>([]);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setSubmitError(null);
    setForm({
      name: staff?.name ?? '',
      email: staff?.email ?? '',
      phone: staff?.phone ?? '',
      photoUrl: staff?.photoUrl ?? '',
      active: staff?.active ?? true,
    });
    setServiceIds(staff?.serviceIds ?? []);
  }, [open, staff]);

  const submit = async () => {
    setSubmitError(null);
    const input = {
      name: form.name.trim(),
      email: form.email.trim() || undefined,
      phone: form.phone.trim() || undefined,
      photoUrl: form.photoUrl.trim() || undefined,
      active: form.active,
      serviceIds,
    };
    try {
      if (staff) {
        await updateStaff.mutateAsync({ id: staff.id, input });
        toast.success('Staff member updated.');
      } else {
        await createStaff.mutateAsync(input);
        toast.success('Staff member added.');
      }
      onClose();
    } catch (error) {
      setSubmitError(errorMessage(error));
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{staff ? 'Edit staff member' : 'New staff member'}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2.5}>
          <TextField
            label="Name"
            required
            autoFocus
            fullWidth
            value={form.name}
            onChange={(event) => setForm({ ...form, name: event.target.value })}
          />
          <TextField
            label="Email"
            type="email"
            fullWidth
            value={form.email}
            onChange={(event) => setForm({ ...form, email: event.target.value })}
          />
          <TextField
            label="Phone"
            fullWidth
            value={form.phone}
            onChange={(event) => setForm({ ...form, phone: event.target.value })}
          />
          <TextField
            label="Photo URL"
            fullWidth
            value={form.photoUrl}
            onChange={(event) => setForm({ ...form, photoUrl: event.target.value })}
            helperText="Shown on the public booking page"
          />

          <Divider />

          <div>
            <Typography variant="subtitle2">Services</Typography>
            <Typography variant="caption" color="text.secondary">
              Select none to let this person deliver everything the business offers.
            </Typography>
            <Stack sx={{ mt: 1 }}>
              {services.map((service) => (
                <FormControlLabel
                  key={service.id}
                  control={
                    <Checkbox
                      size="small"
                      checked={serviceIds.includes(service.id)}
                      onChange={(event) =>
                        setServiceIds(
                          event.target.checked
                            ? [...serviceIds, service.id]
                            : serviceIds.filter((id) => id !== service.id),
                        )
                      }
                    />
                  }
                  label={service.name}
                />
              ))}
            </Stack>
          </div>

          <FormControlLabel
            control={
              <Switch
                checked={form.active}
                onChange={(event) => setForm({ ...form, active: event.target.checked })}
              />
            }
            label="Active"
          />

          {submitError ? <Alert severity="error">{submitError}</Alert> : null}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          onClick={() => void submit()}
          disabled={!form.name.trim() || createStaff.isPending || updateStaff.isPending}
        >
          {staff ? 'Save changes' : 'Add staff member'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function StaffHoursDialog({ staff, onClose }: { staff: Staff | null; onClose: () => void }) {
  const toast = useToast();
  const replaceHours = useReplaceStaffWorkingHours();
  const [intervals, setIntervals] = useState<DayInterval[]>([]);

  useEffect(() => {
    setIntervals(staff?.workingHours ?? []);
  }, [staff]);

  const save = async () => {
    if (!staff) return;
    try {
      await replaceHours.mutateAsync({ id: staff.id, intervals });
      toast.success('Working hours saved.');
      onClose();
    } catch (error) {
      toast.error(errorMessage(error));
    }
  };

  return (
    <Dialog open={staff !== null} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{staff?.name} · working hours</DialogTitle>
      <DialogContent dividers>
        <Alert severity="info" sx={{ mb: 2 }}>
          Leave this empty for someone who simply works whenever the business is open. Personal hours are
          always narrowed by the business hours — they cannot extend them.
        </Alert>
        <WeeklyHoursEditor value={intervals} onChange={setIntervals} />
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={() => void save()} disabled={replaceHours.isPending}>
          Save hours
        </Button>
      </DialogActions>
    </Dialog>
  );
}
