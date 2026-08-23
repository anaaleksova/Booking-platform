import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import Grid from '@mui/material/Grid';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import DesignServicesRoundedIcon from '@mui/icons-material/DesignServicesRounded';
import EditRoundedIcon from '@mui/icons-material/EditRounded';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { PageHeader } from '@/components/PageHeader';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { EmptyState, ErrorState, TableLoadingRows } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import {
  useCreateService,
  useDeleteService,
  useServices,
  useSetServiceActive,
  useUpdateService,
  type ServiceInput,
} from '@/features/services/useServices';
import { errorMessage } from '@/api/client';
import { formatDuration, formatMoney } from '@/utils/format';
import type { Service } from '@/types/api';

const schema = z.object({
  name: z.string().min(1, 'Name is required').max(160),
  description: z.string().max(4000).optional(),
  durationMinutes: z.coerce.number().int().min(1, 'Must be greater than 0').max(1440),
  price: z.coerce.number().min(0, 'Cannot be negative').optional().or(z.literal('')),
  currency: z
    .string()
    .regex(/^[A-Z]{3}$/, 'Use a 3-letter code such as EUR or MKD')
    .default('EUR'),
  bufferBeforeMinutes: z.coerce.number().int().min(0).max(480),
  bufferAfterMinutes: z.coerce.number().int().min(0).max(480),
  requiresStaff: z.boolean(),
  requiresResource: z.boolean(),
  active: z.boolean(),
});

type FormValues = z.input<typeof schema>;

const DEFAULTS: FormValues = {
  name: '',
  description: '',
  durationMinutes: 60,
  price: '',
  currency: 'EUR',
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  requiresStaff: true,
  requiresResource: false,
  active: true,
};

export function ServicesPage() {
  const toast = useToast();
  const { data: services = [], isLoading, isError, error, refetch } = useServices();
  const createService = useCreateService();
  const updateService = useUpdateService();
  const setActive = useSetServiceActive();
  const deleteService = useDeleteService();

  const [editing, setEditing] = useState<Service | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<Service | null>(null);

  const openCreate = () => {
    setEditing(null);
    setDialogOpen(true);
  };

  const openEdit = (service: Service) => {
    setEditing(service);
    setDialogOpen(true);
  };

  const handleDelete = async () => {
    if (!pendingDelete) return;
    try {
      await deleteService.mutateAsync(pendingDelete.id);
      toast.success('Service deleted.');
    } catch (deleteError) {
      // A service with history cannot be removed — offer the safe alternative.
      toast.error(errorMessage(deleteError));
    } finally {
      setPendingDelete(null);
    }
  };

  return (
    <>
      <PageHeader
        title="Services"
        description="What customers can book, how long it takes and what it costs."
        action={
          <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={openCreate}>
            Add service
          </Button>
        }
      />

      {isError ? <ErrorState error={error} onRetry={() => void refetch()} /> : null}

      <Card>
        {isLoading ? (
          <TableLoadingRows />
        ) : services.length === 0 ? (
          <EmptyState
            title="No services yet"
            description="A service is anything a customer can book — a haircut, a class, an hour in a room."
            icon={<DesignServicesRoundedIcon />}
            action={
              <Button variant="contained" onClick={openCreate}>
                Add your first service
              </Button>
            }
          />
        ) : (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Name</TableCell>
                  <TableCell>Duration</TableCell>
                  <TableCell>Buffers</TableCell>
                  <TableCell>Price</TableCell>
                  <TableCell>Requires</TableCell>
                  <TableCell>Bookable</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {services.map((service) => (
                  <TableRow key={service.id} hover>
                    <TableCell>
                      <Typography variant="body2" fontWeight={600}>
                        {service.name}
                      </Typography>
                      {service.description ? (
                        <Typography variant="caption" color="text.secondary">
                          {service.description}
                        </Typography>
                      ) : null}
                    </TableCell>
                    <TableCell>{formatDuration(service.durationMinutes)}</TableCell>
                    <TableCell>
                      {service.bufferBeforeMinutes || service.bufferAfterMinutes
                        ? `${service.bufferBeforeMinutes} / ${service.bufferAfterMinutes} min`
                        : '—'}
                    </TableCell>
                    <TableCell>{formatMoney(service.price, service.currency)}</TableCell>
                    <TableCell>
                      <Stack direction="row" spacing={0.5}>
                        {service.requiresStaff ? <Chip size="small" label="Staff" /> : null}
                        {service.requiresResource ? <Chip size="small" label="Resource" /> : null}
                        {!service.requiresStaff && !service.requiresResource ? '—' : null}
                      </Stack>
                    </TableCell>
                    <TableCell>
                      <Switch
                        size="small"
                        checked={service.active}
                        onChange={(event) =>
                          setActive.mutate({ id: service.id, active: event.target.checked })
                        }
                      />
                    </TableCell>
                    <TableCell align="right">
                      <Tooltip title="Edit">
                        <IconButton size="small" onClick={() => openEdit(service)}>
                          <EditRoundedIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Delete">
                        <IconButton size="small" onClick={() => setPendingDelete(service)}>
                          <DeleteOutlineRoundedIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>

      <ServiceDialog
        open={dialogOpen}
        service={editing}
        busy={createService.isPending || updateService.isPending}
        onClose={() => setDialogOpen(false)}
        onSubmit={async (values) => {
          const input: ServiceInput = {
            ...values,
            description: values.description?.trim() || undefined,
            price: values.price === '' || values.price === undefined ? null : Number(values.price),
            durationMinutes: Number(values.durationMinutes),
            bufferBeforeMinutes: Number(values.bufferBeforeMinutes),
            bufferAfterMinutes: Number(values.bufferAfterMinutes),
            currency: values.currency ?? 'EUR',
          };
          if (editing) {
            await updateService.mutateAsync({ id: editing.id, input });
            toast.success('Service updated.');
          } else {
            await createService.mutateAsync(input);
            toast.success('Service created.');
          }
          setDialogOpen(false);
        }}
      />

      <ConfirmDialog
        open={pendingDelete !== null}
        title={`Delete ${pendingDelete?.name ?? ''}?`}
        message="If this service already has bookings it cannot be deleted — turn it off instead so history stays intact."
        confirmLabel="Delete"
        busy={deleteService.isPending}
        onClose={() => setPendingDelete(null)}
        onConfirm={() => void handleDelete()}
      />
    </>
  );
}

function ServiceDialog({
  open,
  service,
  busy,
  onClose,
  onSubmit,
}: {
  open: boolean;
  service: Service | null;
  busy: boolean;
  onClose: () => void;
  onSubmit: (values: FormValues) => Promise<void>;
}) {
  const [submitError, setSubmitError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    reset,
    watch,
    setValue,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: DEFAULTS });

  useEffect(() => {
    if (!open) return;
    setSubmitError(null);
    reset(
      service
        ? {
            name: service.name,
            description: service.description ?? '',
            durationMinutes: service.durationMinutes,
            price: service.price ?? '',
            currency: service.currency,
            bufferBeforeMinutes: service.bufferBeforeMinutes,
            bufferAfterMinutes: service.bufferAfterMinutes,
            requiresStaff: service.requiresStaff,
            requiresResource: service.requiresResource,
            active: service.active,
          }
        : DEFAULTS,
    );
  }, [open, service, reset]);

  const submit = handleSubmit(async (values) => {
    setSubmitError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      setSubmitError(errorMessage(error));
    }
  });

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{service ? 'Edit service' : 'New service'}</DialogTitle>
      <form onSubmit={submit} noValidate>
        <DialogContent dividers>
          <Grid container spacing={2.5}>
            <Grid item xs={12}>
              <TextField
                label="Name"
                fullWidth
                autoFocus
                error={Boolean(errors.name)}
                helperText={errors.name?.message}
                {...register('name')}
              />
            </Grid>
            <Grid item xs={12}>
              <TextField label="Description" fullWidth multiline minRows={2} {...register('description')} />
            </Grid>
            <Grid item xs={6}>
              <TextField
                label="Duration (minutes)"
                type="number"
                fullWidth
                error={Boolean(errors.durationMinutes)}
                helperText={errors.durationMinutes?.message}
                {...register('durationMinutes')}
              />
            </Grid>
            <Grid item xs={3}>
              <TextField
                label="Price"
                type="number"
                fullWidth
                error={Boolean(errors.price)}
                helperText={errors.price?.message}
                {...register('price')}
              />
            </Grid>
            <Grid item xs={3}>
              <TextField
                label="Currency"
                fullWidth
                error={Boolean(errors.currency)}
                helperText={errors.currency?.message}
                {...register('currency')}
              />
            </Grid>
            <Grid item xs={6}>
              <TextField
                label="Buffer before (min)"
                type="number"
                fullWidth
                helperText="Setup time held before the appointment"
                {...register('bufferBeforeMinutes')}
              />
            </Grid>
            <Grid item xs={6}>
              <TextField
                label="Buffer after (min)"
                type="number"
                fullWidth
                helperText="Clean-up time; blocks the next booking"
                {...register('bufferAfterMinutes')}
              />
            </Grid>
            <Grid item xs={12}>
              <Stack>
                <FormControlLabel
                  control={
                    <Switch
                      checked={watch('requiresStaff')}
                      onChange={(event) => setValue('requiresStaff', event.target.checked)}
                    />
                  }
                  label="Needs a staff member"
                />
                <FormControlLabel
                  control={
                    <Switch
                      checked={watch('requiresResource')}
                      onChange={(event) => setValue('requiresResource', event.target.checked)}
                    />
                  }
                  label="Needs a resource (room, machine, bay…)"
                />
                <FormControlLabel
                  control={
                    <Switch
                      checked={watch('active')}
                      onChange={(event) => setValue('active', event.target.checked)}
                    />
                  }
                  label="Bookable"
                />
              </Stack>
            </Grid>
            {submitError ? (
              <Grid item xs={12}>
                <Alert severity="error">{submitError}</Alert>
              </Grid>
            ) : null}
          </Grid>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={busy}>
            {service ? 'Save changes' : 'Create service'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
