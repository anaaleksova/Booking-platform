import { useState } from 'react';
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
import CategoryRoundedIcon from '@mui/icons-material/CategoryRounded';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import EditRoundedIcon from '@mui/icons-material/EditRounded';
import MapRoundedIcon from '@mui/icons-material/MapRounded';
import { Link as RouterLink } from 'react-router-dom';
import { errorMessage } from '@/api/client';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import { useDeleteResourceType, useResourceTypes, useSaveResourceType } from '@/features/catalog/api';
import { FloorPlanEditor } from '@/features/floorplan/FloorPlanEditor';
import type { ResourceType } from '@/types/api';
import { pluralize } from '@/utils/format';

/** Business-defined kinds of resources — "Treatment Room", "Table", "Gaming Station". */
export function ResourceTypesPage() {
  const toast = useToast();
  const { data: types, isLoading, isError, error, refetch } = useResourceTypes();
  const save = useSaveResourceType();
  const remove = useDeleteResourceType();
  const [editing, setEditing] = useState<Partial<ResourceType> | null>(null);
  const [deleting, setDeleting] = useState<ResourceType | null>(null);
  const [mapping, setMapping] = useState<ResourceType | null>(null);

  const submit = async () => {
    if (!editing?.name?.trim()) {
      return;
    }
    try {
      await save.mutateAsync({
        id: editing.id,
        input: { name: editing.name.trim(), description: editing.description ?? undefined, active: editing.active ?? true },
      });
      toast.success(editing.id ? 'Resource type saved' : 'Resource type created');
      setEditing(null);
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  return (
    <>
      <PageHeader
        title="Resource types"
        description="Define the kinds of things your services need. No code change required — add “Treatment Room”, “Table” or “Court”. Optionally draw a floor plan so customers can pick their spot on a map."
        action={
          <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={() => setEditing({ active: true })}>
            New resource type
          </Button>
        }
      />
      {isError ? <ErrorState error={error} onRetry={() => refetch()} /> : null}
      {isLoading ? (
        <LoadingState />
      ) : types?.length === 0 ? (
        <EmptyState icon={<CategoryRoundedIcon />} title="No resource types yet" />
      ) : (
        <Grid container spacing={2}>
          {types?.map((type) => (
            <Grid item xs={12} sm={6} lg={4} key={type.id}>
              <Paper variant="outlined" sx={{ p: 2.5, height: '100%' }}>
                <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                  <div>
                    <Typography variant="subtitle1" fontWeight={700}>
                      {type.name}
                    </Typography>
                    <Stack direction="row" spacing={1} sx={{ mt: 0.5 }}>
                      {type.employeeType ? <Chip size="small" color="primary" variant="outlined" label="Employees" /> : null}
                      {!type.active ? <Chip size="small" label="Inactive" /> : null}
                    </Stack>
                  </div>
                  <Stack direction="row">
                    <Tooltip title="Edit">
                      <IconButton onClick={() => setEditing(type)}>
                        <EditRoundedIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    {!type.employeeType ? (
                      <Tooltip title="Delete">
                        <IconButton onClick={() => setDeleting(type)}>
                          <DeleteOutlineRoundedIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    ) : null}
                  </Stack>
                </Stack>
                {type.description ? (
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                    {type.description}
                  </Typography>
                ) : null}
                <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mt: 1.5 }}>
                  <Typography variant="body2">
                    <RouterLink to={type.employeeType ? '/admin/employees' : '/admin/resources'}>
                      {pluralize(type.resourceCount, type.employeeType ? 'employee' : 'resource')}
                    </RouterLink>
                  </Typography>
                  {!type.employeeType ? (
                    <Button size="small" startIcon={<MapRoundedIcon />} onClick={() => setMapping(type)}>
                      {type.floorPlanCount > 1
                        ? `Floor plans (${type.floorPlanCount})`
                        : type.floorPlanCount === 1
                          ? 'Edit floor plan'
                          : 'Add floor plan'}
                    </Button>
                  ) : null}
                </Stack>
              </Paper>
            </Grid>
          ))}
        </Grid>
      )}

      <Dialog open={editing !== null} onClose={() => setEditing(null)} maxWidth="xs" fullWidth>
        <DialogTitle>{editing?.id ? 'Edit resource type' : 'New resource type'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              autoFocus
              label="Name"
              placeholder="e.g. Treatment Room"
              value={editing?.name ?? ''}
              onChange={(e) => setEditing({ ...editing, name: e.target.value })}
              fullWidth
            />
            <TextField
              label="Description"
              value={editing?.description ?? ''}
              onChange={(e) => setEditing({ ...editing, description: e.target.value })}
              multiline
              minRows={2}
              fullWidth
            />
            {!editing?.employeeType ? (
              <FormControlLabel
                control={<Switch checked={editing?.active ?? true} onChange={(e) => setEditing({ ...editing, active: e.target.checked })} />}
                label="Active"
              />
            ) : null}
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setEditing(null)}>Cancel</Button>
          <Button variant="contained" onClick={submit} disabled={!editing?.name?.trim() || save.isPending}>
            Save
          </Button>
        </DialogActions>
      </Dialog>

      <FloorPlanEditor type={mapping} onClose={() => setMapping(null)} />

      <ConfirmDialog
        open={deleting !== null}
        title={`Delete ${deleting?.name ?? 'resource type'}?`}
        message="Only possible when no resources and no services use this type."
        confirmLabel="Delete"
        busy={remove.isPending}
        onConfirm={() =>
          deleting &&
          remove
            .mutateAsync(deleting.id)
            .then(() => toast.success('Resource type deleted'))
            .catch((err) => toast.error(errorMessage(err)))
            .finally(() => setDeleting(null))
        }
        onClose={() => setDeleting(null)}
      />
    </>
  );
}
