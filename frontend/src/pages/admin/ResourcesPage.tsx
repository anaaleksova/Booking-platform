import { useMemo, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Autocomplete from '@mui/material/Autocomplete';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Radio from '@mui/material/Radio';
import RadioGroup from '@mui/material/RadioGroup';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import Tab from '@mui/material/Tab';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Tabs from '@mui/material/Tabs';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import EditRoundedIcon from '@mui/icons-material/EditRounded';
import Inventory2RoundedIcon from '@mui/icons-material/Inventory2Rounded';
import { errorMessage } from '@/api/client';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { ImageUpload } from '@/components/ImageUpload';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState, ErrorState, TableLoadingRows } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import { WeeklyHoursEditor } from '@/components/WeeklyHoursEditor';
import {
  useDeleteResource,
  useReplaceResourceHours,
  useResources,
  useResourceTypes,
  useSaveResource,
  useServices,
  useSetResourceActive,
} from '@/features/catalog/api';
import type { DayInterval, Resource } from '@/types/api';
import { formatCapacity } from '@/utils/format';

interface Draft {
  id?: number;
  imageMediaId: string | null;
  imageUrl: string | null;
  resourceTypeId: number | '';
  name: string;
  description: string;
  capacity: string;
  shared: boolean;
  minPartySize: string;
  active: boolean;
  serviceIds: number[];
  hours: DayInterval[];
  ownHours: boolean;
}

/** Concrete reservable items — Room 2, Table 5 — grouped by type. Employees live on their own page. */
export function ResourcesPage() {
  const toast = useToast();
  const { data: resources, isLoading, isError, error, refetch } = useResources();
  const { data: types } = useResourceTypes();
  const { data: services } = useServices();
  const save = useSaveResource();
  const saveHours = useReplaceResourceHours();
  const setActive = useSetResourceActive();
  const remove = useDeleteResource();

  const [tab, setTab] = useState<number | 'all'>('all');
  const [draft, setDraft] = useState<Draft | null>(null);
  const [deleting, setDeleting] = useState<Resource | null>(null);

  const thingTypes = useMemo(() => (types ?? []).filter((type) => !type.employeeType), [types]);
  const things = useMemo(() => (resources ?? []).filter((resource) => !resource.employeeManaged), [resources]);
  const visible = tab === 'all' ? things : things.filter((resource) => resource.resourceTypeId === tab);

  const open = (resource?: Resource) =>
    setDraft(
      resource
        ? {
            id: resource.id,
            imageMediaId: resource.imageMediaId ?? null,
            imageUrl: resource.imageUrl ?? null,
            resourceTypeId: resource.resourceTypeId,
            name: resource.name,
            description: resource.description ?? '',
            capacity: resource.capacity === null ? '' : String(resource.capacity),
            shared: resource.sharedSeating,
            minPartySize: resource.minPartySize === null ? '' : String(resource.minPartySize),
            active: resource.active,
            serviceIds: resource.serviceIds,
            hours: resource.hours,
            ownHours: resource.hours.length > 0,
          }
        : {
            imageMediaId: null,
            imageUrl: null,
            resourceTypeId: tab === 'all' ? thingTypes[0]?.id ?? '' : tab,
            name: '',
            description: '',
            capacity: '1',
            shared: false,
            minPartySize: '',
            active: true,
            serviceIds: [],
            hours: [],
            ownHours: false,
          },
    );

  const submit = async () => {
    if (!draft || draft.resourceTypeId === '' || !draft.name.trim()) {
      return;
    }
    try {
      const saved = await save.mutateAsync({
        id: draft.id,
        input: {
          resourceTypeId: draft.resourceTypeId,
          name: draft.name.trim(),
          description: draft.description.trim() || undefined,
          capacity: draft.capacity === '' ? null : Math.max(1, Number(draft.capacity)),
          sharedSeating: draft.capacity !== '' && draft.shared,
          minPartySize: draft.capacity === '' || draft.shared || draft.minPartySize === '' ? null : Math.max(1, Number(draft.minPartySize)),
          active: draft.active,
          imageMediaId: draft.imageMediaId,
          serviceIds: draft.serviceIds,
        },
      });
      await saveHours.mutateAsync({ id: saved.id, intervals: draft.ownHours ? draft.hours : [] });
      toast.success(draft.id ? 'Resource saved' : 'Resource created');
      setDraft(null);
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  return (
    <>
      <PageHeader
        title="Resources"
        description="The rooms, tables, machines and equipment that reservations hold."
        action={
          <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={() => open()} disabled={thingTypes.length === 0}>
            New resource
          </Button>
        }
      />

      {thingTypes.length === 0 && !isLoading ? (
        <Alert severity="info" sx={{ mb: 2 }}>
          Start by creating a resource type such as “Treatment Room” on the{' '}
          <RouterLink to="/admin/resource-types">Resource types</RouterLink> page.
        </Alert>
      ) : null}

      <Tabs value={tab} onChange={(_, value) => setTab(value)} variant="scrollable" sx={{ mb: 2 }}>
        <Tab value="all" label={`All (${things.length})`} />
        {thingTypes.map((type) => (
          <Tab key={type.id} value={type.id} label={`${type.name} (${type.resourceCount})`} />
        ))}
      </Tabs>

      {isError ? <ErrorState error={error} onRetry={() => refetch()} /> : null}
      {isLoading ? (
        <Paper variant="outlined">
          <TableLoadingRows />
        </Paper>
      ) : visible.length === 0 ? (
        <EmptyState icon={<Inventory2RoundedIcon />} title="No resources here yet" />
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Type</TableCell>
                <TableCell align="center">Capacity</TableCell>
                <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>Serves</TableCell>
                <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>Hours</TableCell>
                <TableCell align="center">Active</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {visible.map((resource) => (
                <TableRow key={resource.id} hover>
                  <TableCell>
                    <Typography variant="body2" fontWeight={600}>
                      {resource.name}
                    </Typography>
                    {resource.description ? (
                      <Typography variant="caption" color="text.secondary">
                        {resource.description}
                      </Typography>
                    ) : null}
                  </TableCell>
                  <TableCell>{resource.resourceTypeName}</TableCell>
                  <TableCell align="center">
                    {formatCapacity(resource.capacity)}
                    {resource.sharedSeating ? (
                      <Typography variant="caption" color="text.secondary" display="block">
                        Shared
                      </Typography>
                    ) : resource.minPartySize ? (
                      <Typography variant="caption" color="text.secondary" display="block">
                        Groups of {resource.minPartySize}+
                      </Typography>
                    ) : null}
                  </TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>
                    {resource.serviceIds.length === 0 ? (
                      <Typography variant="caption" color="text.secondary">
                        All services
                      </Typography>
                    ) : (
                      <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                        {resource.serviceIds.map((id) => (
                          <Chip key={id} size="small" variant="outlined" label={services?.find((s) => s.id === id)?.name ?? id} />
                        ))}
                      </Stack>
                    )}
                  </TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>
                    <Typography variant="caption" color="text.secondary">
                      {resource.hours.length ? 'Own schedule' : 'Opening hours'}
                    </Typography>
                  </TableCell>
                  <TableCell align="center">
                    <Switch
                      checked={resource.active}
                      inputProps={{ 'aria-label': `${resource.name} active` }}
                      onChange={(e) =>
                        setActive
                          .mutateAsync({ id: resource.id, active: e.target.checked })
                          .catch((err) => toast.error(errorMessage(err)))
                      }
                    />
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    <Tooltip title="Edit">
                      <IconButton onClick={() => open(resource)}>
                        <EditRoundedIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Delete">
                      <IconButton onClick={() => setDeleting(resource)}>
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

      <Dialog open={draft !== null} onClose={() => setDraft(null)} maxWidth="sm" fullWidth>
        <DialogTitle>{draft?.id ? 'Edit resource' : 'New resource'}</DialogTitle>
        {draft ? (
          <DialogContent>
            <Stack spacing={2} sx={{ mt: 1 }}>
              <TextField select label="Type" value={draft.resourceTypeId} onChange={(e) => setDraft({ ...draft, resourceTypeId: Number(e.target.value) })} fullWidth>
                {thingTypes.map((type) => (
                  <MenuItem key={type.id} value={type.id}>
                    {type.name}
                  </MenuItem>
                ))}
              </TextField>
              <TextField autoFocus label="Name" placeholder="e.g. Room 2" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} fullWidth />
              <TextField label="Description" value={draft.description} onChange={(e) => setDraft({ ...draft, description: e.target.value })} fullWidth />
              <ImageUpload
                label="Photo"
                hint="Shown to customers who choose this resource, e.g. a table by the window."
                url={draft.imageUrl}
                width={200}
                onChange={(media) => setDraft({ ...draft, imageMediaId: media?.id ?? null, imageUrl: media?.url ?? null })}
              />
              <TextField
                label="Capacity"
                type="number"
                value={draft.capacity}
                onChange={(e) => setDraft({ ...draft, capacity: e.target.value })}
                inputProps={{ min: 1 }}
                helperText="How many participants it serves at once, e.g. a table's seats. Leave empty for no limit."
                fullWidth
              />
              {draft.capacity !== '' && Number(draft.capacity) > 1 ? (
                <SeatingChoice draft={draft} onChange={setDraft} />
              ) : null}
              <Autocomplete
                multiple
                options={services ?? []}
                getOptionLabel={(option) => option.name}
                value={(services ?? []).filter((service) => draft.serviceIds.includes(service.id))}
                onChange={(_, value) => setDraft({ ...draft, serviceIds: value.map((service) => service.id) })}
                renderInput={(params) => (
                  <TextField {...params} label="Restrict to services" helperText="Empty = usable by every service that needs this type" />
                )}
              />
              <FormControlLabel control={<Switch checked={draft.active} onChange={(e) => setDraft({ ...draft, active: e.target.checked })} />} label="Active" />
              <FormControlLabel
                control={
                  <Switch
                    checked={draft.ownHours}
                    onChange={(e) => setDraft({ ...draft, ownHours: e.target.checked, hours: draft.hours.length ? draft.hours : [] })}
                  />
                }
                label="Own schedule (otherwise available whenever the business is open)"
              />
              {draft.ownHours ? <WeeklyHoursEditor value={draft.hours} onChange={(hours) => setDraft({ ...draft, hours })} /> : null}
            </Stack>
          </DialogContent>
        ) : null}
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setDraft(null)}>Cancel</Button>
          <Button variant="contained" onClick={submit} disabled={!draft?.name.trim() || draft?.resourceTypeId === '' || save.isPending}>
            Save
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={deleting !== null}
        title={`Delete ${deleting?.name ?? 'resource'}?`}
        message="A resource with reservations cannot be deleted — deactivate it instead to keep history intact."
        confirmLabel="Delete"
        busy={remove.isPending}
        onConfirm={() =>
          deleting &&
          remove
            .mutateAsync(deleting.id)
            .then(() => toast.success('Resource deleted'))
            .catch((err) => toast.error(errorMessage(err)))
            .finally(() => setDeleting(null))
        }
        onClose={() => setDeleting(null)}
      />
    </>
  );
}

/**
 * What happens when a group smaller than the capacity books: the whole
 * resource is theirs (optionally only from a minimum group size), or the free
 * seats stay bookable for other groups at the same time.
 */
function SeatingChoice({ draft, onChange }: { draft: Draft; onChange: (draft: Draft) => void }) {
  const capacity = Number(draft.capacity);
  return (
    <Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
      <Typography variant="subtitle2" fontWeight={700}>
        When a smaller group books
      </Typography>
      <RadioGroup value={draft.shared ? 'shared' : 'whole'} onChange={(e) => onChange({ ...draft, shared: e.target.value === 'shared' })}>
        <FormControlLabel
          value="whole"
          control={<Radio size="small" />}
          label={
            <Box sx={{ py: 0.5 }}>
              <Typography variant="body2" fontWeight={600}>
                Give them the whole {capacity}-seat resource
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Nobody else can book it at the same time.
              </Typography>
            </Box>
          }
        />
        <FormControlLabel
          value="shared"
          control={<Radio size="small" />}
          label={
            <Box sx={{ py: 0.5 }}>
              <Typography variant="body2" fontWeight={600}>
                Share it — the free seats stay bookable
              </Typography>
              <Typography variant="caption" color="text.secondary">
                A group of 2 at this {capacity}-seat table leaves {Math.max(0, capacity - 2)} seats for other guests at the same time.
              </Typography>
            </Box>
          }
        />
      </RadioGroup>
      {!draft.shared ? (
        <TextField
          label="Smallest group"
          type="number"
          size="small"
          value={draft.minPartySize}
          onChange={(e) => onChange({ ...draft, minPartySize: e.target.value })}
          inputProps={{ min: 1, max: capacity }}
          helperText={`Optional. Keep it for groups of at least this size, e.g. don't give the ${capacity}-seat table to 2 people.`}
          fullWidth
          sx={{ mt: 1.5 }}
        />
      ) : null}
    </Paper>
  );
}
