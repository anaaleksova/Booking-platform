import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
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
import Inventory2RoundedIcon from '@mui/icons-material/Inventory2Rounded';
import { PageHeader } from '@/components/PageHeader';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { EmptyState, ErrorState, LoadingState } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import { useServices } from '@/features/services/useServices';
import {
  useCreateResource,
  useDeleteResource,
  useResources,
  useUpdateResource,
} from '@/features/resources/useResources';
import { errorMessage } from '@/api/client';
import type { Resource } from '@/types/api';

/**
 * Resources are what make the platform generic: reformers, treatment rooms,
 * studios, service bays. A booking may need staff, a resource, both or neither.
 */
export function ResourcesPage() {
  const toast = useToast();
  const { data: resources = [], isLoading, isError, error, refetch } = useResources();
  const { data: services = [] } = useServices();
  const deleteResource = useDeleteResource();

  const [editing, setEditing] = useState<Resource | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<Resource | null>(null);

  const handleDelete = async () => {
    if (!pendingDelete) return;
    try {
      await deleteResource.mutateAsync(pendingDelete.id);
      toast.success('Resource deleted.');
    } catch (deleteError) {
      toast.error(errorMessage(deleteError));
    } finally {
      setPendingDelete(null);
    }
  };

  const openCreate = () => {
    setEditing(null);
    setDialogOpen(true);
  };

  return (
    <>
      <PageHeader
        title="Resources"
        description="Rooms, machines, chairs, bays — anything a booking occupies besides a person."
        action={
          <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={openCreate}>
            Add resource
          </Button>
        }
      />

      {isError ? <ErrorState error={error} onRetry={() => void refetch()} /> : null}
      {isLoading ? <LoadingState /> : null}

      {!isLoading && resources.length === 0 ? (
        <EmptyState
          title="No resources"
          description="Only add these if a booking occupies something physical. A hair salon usually needs none; a pilates studio needs one per reformer."
          icon={<Inventory2RoundedIcon />}
          action={
            <Button variant="contained" onClick={openCreate}>
              Add a resource
            </Button>
          }
        />
      ) : (
        <Grid container spacing={2.5}>
          {resources.map((resource) => (
            <Grid item xs={12} sm={6} md={4} key={resource.id}>
              <Card sx={{ height: '100%' }}>
                <CardContent>
                  <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                    <Stack sx={{ minWidth: 0 }}>
                      <Stack direction="row" spacing={1} alignItems="center">
                        <Typography variant="subtitle1" fontWeight={700} noWrap>
                          {resource.name}
                        </Typography>
                        {!resource.active ? <Chip size="small" label="Inactive" /> : null}
                      </Stack>
                      {resource.description ? (
                        <Typography variant="body2" color="text.secondary">
                          {resource.description}
                        </Typography>
                      ) : null}
                    </Stack>
                    <Stack direction="row">
                      <Tooltip title="Edit">
                        <IconButton
                          size="small"
                          onClick={() => {
                            setEditing(resource);
                            setDialogOpen(true);
                          }}
                        >
                          <EditRoundedIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Delete">
                        <IconButton size="small" onClick={() => setPendingDelete(resource)}>
                          <DeleteOutlineRoundedIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </Stack>
                  </Stack>

                  <Divider sx={{ my: 2 }} />

                  <Typography variant="caption" color="text.secondary" display="block" gutterBottom>
                    Hosts these services
                  </Typography>
                  <Stack direction="row" flexWrap="wrap" gap={0.5}>
                    {resource.serviceIds.length === 0 ? (
                      <Chip size="small" variant="outlined" label="Any service" />
                    ) : (
                      resource.serviceIds.map((id) => (
                        <Chip
                          key={id}
                          size="small"
                          variant="outlined"
                          label={services.find((service) => service.id === id)?.name ?? `#${id}`}
                        />
                      ))
                    )}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      )}

      <ResourceDialog
        open={dialogOpen}
        resource={editing}
        services={services.map((service) => ({ id: service.id, name: service.name }))}
        onClose={() => setDialogOpen(false)}
      />

      <ConfirmDialog
        open={pendingDelete !== null}
        title={`Delete ${pendingDelete?.name ?? ''}?`}
        message="Resources with existing bookings cannot be deleted — deactivate instead."
        confirmLabel="Delete"
        busy={deleteResource.isPending}
        onClose={() => setPendingDelete(null)}
        onConfirm={() => void handleDelete()}
      />
    </>
  );
}

function ResourceDialog({
  open,
  resource,
  services,
  onClose,
}: {
  open: boolean;
  resource: Resource | null;
  services: { id: number; name: string }[];
  onClose: () => void;
}) {
  const toast = useToast();
  const createResource = useCreateResource();
  const updateResource = useUpdateResource();

  const [form, setForm] = useState({ name: '', description: '', active: true });
  const [serviceIds, setServiceIds] = useState<number[]>([]);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setSubmitError(null);
    setForm({
      name: resource?.name ?? '',
      description: resource?.description ?? '',
      active: resource?.active ?? true,
    });
    setServiceIds(resource?.serviceIds ?? []);
  }, [open, resource]);

  const submit = async () => {
    setSubmitError(null);
    const input = {
      name: form.name.trim(),
      description: form.description.trim() || undefined,
      active: form.active,
      serviceIds,
    };
    try {
      if (resource) {
        await updateResource.mutateAsync({ id: resource.id, input });
        toast.success('Resource updated.');
      } else {
        await createResource.mutateAsync(input);
        toast.success('Resource added.');
      }
      onClose();
    } catch (error) {
      setSubmitError(errorMessage(error));
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{resource ? 'Edit resource' : 'New resource'}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2.5}>
          <TextField
            label="Name"
            required
            autoFocus
            fullWidth
            placeholder="Reformer 1, Room A, Bay 2…"
            value={form.name}
            onChange={(event) => setForm({ ...form, name: event.target.value })}
          />
          <TextField
            label="Description"
            fullWidth
            multiline
            minRows={2}
            value={form.description}
            onChange={(event) => setForm({ ...form, description: event.target.value })}
          />

          <Divider />

          <div>
            <Typography variant="subtitle2">Services</Typography>
            <Typography variant="caption" color="text.secondary">
              Select none to let this resource host any service.
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
          disabled={!form.name.trim() || createResource.isPending || updateResource.isPending}
        >
          {resource ? 'Save changes' : 'Add resource'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
