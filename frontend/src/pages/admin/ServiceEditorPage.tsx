import { useEffect, useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Autocomplete from '@mui/material/Autocomplete';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import FormControlLabel from '@mui/material/FormControlLabel';
import Grid from '@mui/material/Grid';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import ArrowBackRoundedIcon from '@mui/icons-material/ArrowBackRounded';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import { errorMessage } from '@/api/client';
import { ImageUpload } from '@/components/ImageUpload';
import { PageHeader } from '@/components/PageHeader';
import { ErrorState, LoadingState } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import { useBusiness } from '@/features/business/api';
import { useResourceTypes, useSaveService, useService, useServices } from '@/features/catalog/api';
import type { ServiceInput } from '@/types/api';

type RequirementRow = ServiceInput['requirements'][number];

const EMPTY: ServiceInput = {
  name: '',
  description: '',
  category: '',
  imageMediaId: null,
  durationMinutes: 60,
  price: 0,
  pricePerParticipant: false,
  maxParticipants: 1,
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  allowCustomerResourceSelection: false,
  active: true,
  requirements: [],
};

/**
 * Create or edit a service, including what it needs: rows of "quantity ×
 * resource type". The availability engine reads exactly these rows.
 */
export function ServiceEditorPage() {
  const { id } = useParams();
  const serviceId = id ? Number(id) : undefined;
  const navigate = useNavigate();
  const toast = useToast();
  const { data: business } = useBusiness();
  const { data: existing, isLoading, isError, error } = useService(serviceId);
  const { data: resourceTypes } = useResourceTypes();
  const { data: allServices } = useServices();
  const save = useSaveService();
  const [form, setForm] = useState<ServiceInput>(EMPTY);
  const [imageUrl, setImageUrl] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (existing) {
      setImageUrl(existing.imageUrl ?? null);
      setForm({
        name: existing.name,
        description: existing.description ?? '',
        category: existing.category ?? '',
        imageMediaId: existing.imageMediaId ?? null,
        durationMinutes: existing.durationMinutes,
        price: existing.price,
        pricePerParticipant: existing.pricePerParticipant,
        maxParticipants: existing.maxParticipants,
        bufferBeforeMinutes: existing.bufferBeforeMinutes,
        bufferAfterMinutes: existing.bufferAfterMinutes,
        allowCustomerResourceSelection: existing.allowCustomerResourceSelection,
        active: existing.active,
        requirements: existing.requirements.map(({ resourceTypeId, quantity, required, customerCanChoose }) => ({
          resourceTypeId,
          quantity,
          required,
          customerCanChoose,
        })),
      });
    }
  }, [existing]);

  if (serviceId !== undefined && isLoading) {
    return <LoadingState />;
  }
  if (isError) {
    return <ErrorState error={error} />;
  }

  const set = <K extends keyof ServiceInput>(key: K, value: ServiceInput[K]) => setForm({ ...form, [key]: value });
  const setRequirement = (index: number, patch: Partial<RequirementRow>) =>
    set(
      'requirements',
      form.requirements.map((row, position) => (position === index ? { ...row, ...patch } : row)),
    );
  const usedTypes = new Set(form.requirements.map((row) => row.resourceTypeId));
  const availableTypes = (resourceTypes ?? []).filter((type) => type.active);

  const validate = () => {
    const errors: Record<string, string> = {};
    if (!form.name.trim()) errors.name = 'Name is required';
    if (!(form.durationMinutes >= 5)) errors.durationMinutes = 'At least 5 minutes';
    if (!(form.price >= 0)) errors.price = 'Must not be negative';
    if (!(form.maxParticipants >= 1)) errors.maxParticipants = 'At least 1';
    if (form.requirements.some((row) => !row.resourceTypeId)) errors.requirements = 'Choose a resource type for each row';
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const submit = async () => {
    if (!validate()) {
      return;
    }
    try {
      await save.mutateAsync({ id: serviceId, input: { ...form, name: form.name.trim() } });
      toast.success(serviceId ? 'Service saved' : 'Service created');
      navigate('/admin/services');
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  return (
    <>
      <Button component={RouterLink} to="/admin/services" startIcon={<ArrowBackRoundedIcon />} sx={{ mb: 1 }}>
        Services
      </Button>
      <PageHeader
        title={serviceId ? `Edit ${existing?.name ?? 'service'}` : 'New service'}
        action={
          <Stack direction="row" spacing={1}>
            <Button component={RouterLink} to="/admin/services">
              Cancel
            </Button>
            <Button variant="contained" onClick={submit} disabled={save.isPending}>
              {save.isPending ? 'Saving…' : 'Save'}
            </Button>
          </Stack>
        }
      />

      <Grid container spacing={2}>
        <Grid item xs={12} lg={6}>
          <Paper variant="outlined" sx={{ p: 3 }}>
            <Typography variant="subtitle1" fontWeight={700} gutterBottom>
              Details
            </Typography>
            <Stack spacing={2}>
              <TextField label="Name" value={form.name} onChange={(e) => set('name', e.target.value)} error={Boolean(fieldErrors.name)} helperText={fieldErrors.name} fullWidth />
              <TextField label="Description" value={form.description} onChange={(e) => set('description', e.target.value)} multiline minRows={3} fullWidth />
              <Autocomplete
                freeSolo
                options={Array.from(new Set((allServices ?? []).map((service) => service.category).filter(Boolean) as string[]))}
                inputValue={form.category ?? ''}
                onInputChange={(_, value) => set('category', value)}
                renderInput={(params) => (
                  <TextField {...params} label="Category" helperText="Groups your price list, e.g. “Massages” or “Hair”" />
                )}
              />
              <ImageUpload
                label="Photo"
                hint="Shown on your booking page next to this service."
                url={imageUrl}
                width={240}
                onChange={(media) => {
                  set('imageMediaId', media?.id ?? null);
                  setImageUrl(media?.url ?? null);
                }}
              />
              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <TextField
                    label="Duration"
                    type="number"
                    value={form.durationMinutes}
                    onChange={(e) => set('durationMinutes', Number(e.target.value))}
                    InputProps={{ endAdornment: <InputAdornment position="end">min</InputAdornment> }}
                    error={Boolean(fieldErrors.durationMinutes)}
                    helperText={fieldErrors.durationMinutes}
                    fullWidth
                  />
                </Grid>
                <Grid item xs={6}>
                  <TextField
                    label="Price"
                    type="number"
                    value={form.price}
                    onChange={(e) => set('price', Number(e.target.value))}
                    InputProps={{ endAdornment: <InputAdornment position="end">{business?.currency}</InputAdornment> }}
                    inputProps={{ min: 0, step: '0.01' }}
                    error={Boolean(fieldErrors.price)}
                    helperText={fieldErrors.price}
                    fullWidth
                  />
                </Grid>
                <Grid item xs={6}>
                  <TextField
                    label="Maximum participants"
                    type="number"
                    value={form.maxParticipants}
                    onChange={(e) => set('maxParticipants', Number(e.target.value))}
                    inputProps={{ min: 1 }}
                    error={Boolean(fieldErrors.maxParticipants)}
                    helperText={fieldErrors.maxParticipants}
                    fullWidth
                  />
                </Grid>
                <Grid item xs={6}>
                  <TextField
                    select
                    label="Price applies"
                    value={form.pricePerParticipant ? 'person' : 'reservation'}
                    onChange={(e) => set('pricePerParticipant', e.target.value === 'person')}
                    fullWidth
                  >
                    <MenuItem value="reservation">Per reservation</MenuItem>
                    <MenuItem value="person">Per participant</MenuItem>
                  </TextField>
                </Grid>
                <Grid item xs={6}>
                  <TextField
                    label="Preparation before"
                    type="number"
                    value={form.bufferBeforeMinutes}
                    onChange={(e) => set('bufferBeforeMinutes', Number(e.target.value))}
                    InputProps={{ endAdornment: <InputAdornment position="end">min</InputAdornment> }}
                    fullWidth
                  />
                </Grid>
                <Grid item xs={6}>
                  <TextField
                    label="Clean-up after"
                    type="number"
                    value={form.bufferAfterMinutes}
                    onChange={(e) => set('bufferAfterMinutes', Number(e.target.value))}
                    InputProps={{ endAdornment: <InputAdornment position="end">min</InputAdornment> }}
                    fullWidth
                  />
                </Grid>
              </Grid>
              <FormControlLabel control={<Switch checked={form.active} onChange={(e) => set('active', e.target.checked)} />} label="Active — customers can book it" />
            </Stack>
          </Paper>
        </Grid>

        <Grid item xs={12} lg={6}>
          <Paper variant="outlined" sx={{ p: 3 }}>
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
              <Typography variant="subtitle1" fontWeight={700}>
                Resource requirements
              </Typography>
              <Button
                size="small"
                startIcon={<AddRoundedIcon />}
                disabled={availableTypes.every((type) => usedTypes.has(type.id))}
                onClick={() => {
                  const next = availableTypes.find((type) => !usedTypes.has(type.id));
                  if (next) {
                    set('requirements', [
                      ...form.requirements,
                      { resourceTypeId: next.id, quantity: 1, required: true, customerCanChoose: false },
                    ]);
                  }
                }}
              >
                Add requirement
              </Button>
            </Stack>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              Every reservation of this service holds these resources for its whole duration. The resources assigned
              to each requirement must together seat all participants.
            </Typography>
            {fieldErrors.requirements ? <Alert severity="error" sx={{ mb: 2 }}>{fieldErrors.requirements}</Alert> : null}
            {availableTypes.length === 0 ? (
              <Alert severity="info">
                Create resource types first on the <RouterLink to="/admin/resource-types">Resource types</RouterLink> page.
              </Alert>
            ) : null}

            <Stack spacing={1.5}>
              {form.requirements.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  No requirements: only opening hours limit when this service can be booked.
                </Typography>
              ) : null}
              {form.requirements.map((row, index) => (
                <Paper key={index} variant="outlined" sx={{ p: 1.5 }}>
                  <Stack direction="row" spacing={1.5} alignItems="center">
                    <TextField
                      label="Quantity"
                      type="number"
                      value={row.quantity}
                      onChange={(e) => setRequirement(index, { quantity: Math.max(1, Number(e.target.value)) })}
                      inputProps={{ min: 1, max: 50 }}
                      sx={{ width: 96, flexShrink: 0 }}
                    />
                    <TextField
                      select
                      label="Resource type"
                      value={row.resourceTypeId}
                      onChange={(e) => setRequirement(index, { resourceTypeId: Number(e.target.value) })}
                      sx={{ flex: 1, minWidth: 0 }}
                    >
                      {(resourceTypes ?? [])
                        .filter((type) => type.id === row.resourceTypeId || (type.active && !usedTypes.has(type.id)))
                        .map((type) => (
                          <MenuItem key={type.id} value={type.id}>
                            {type.name}
                          </MenuItem>
                        ))}
                    </TextField>
                    <IconButton
                      aria-label="Remove requirement"
                      onClick={() => set('requirements', form.requirements.filter((_, position) => position !== index))}
                    >
                      <DeleteOutlineRoundedIcon fontSize="small" />
                    </IconButton>
                  </Stack>
                  <Stack direction="row" spacing={2} useFlexGap flexWrap="wrap" sx={{ mt: 0.5 }}>
                    <FormControlLabel
                      control={
                        <Checkbox size="small" checked={row.required} onChange={(e) => setRequirement(index, { required: e.target.checked })} />
                      }
                      label={<Typography variant="body2">Required — no free one means no slot</Typography>}
                    />
                    <FormControlLabel
                      control={
                        <Checkbox
                          size="small"
                          checked={row.customerCanChoose}
                          onChange={(e) => setRequirement(index, { customerCanChoose: e.target.checked })}
                        />
                      }
                      label={<Typography variant="body2">Customer chooses</Typography>}
                    />
                  </Stack>
                </Paper>
              ))}
            </Stack>

            <FormControlLabel
              sx={{ mt: 2 }}
              control={
                <Switch
                  checked={form.allowCustomerResourceSelection}
                  onChange={(e) => set('allowCustomerResourceSelection', e.target.checked)}
                />
              }
              label="Allow customers to choose resources"
            />
            <Typography variant="caption" color="text.secondary" display="block">
              When off, every resource is assigned automatically — the best fit that is free — even for requirements
              marked "Customer chooses".
            </Typography>
          </Paper>
        </Grid>
      </Grid>
    </>
  );
}
