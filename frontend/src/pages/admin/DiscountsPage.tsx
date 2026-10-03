import { useState } from 'react';
import Autocomplete from '@mui/material/Autocomplete';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import Grid from '@mui/material/Grid';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
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
import EditRoundedIcon from '@mui/icons-material/EditRounded';
import LocalOfferRoundedIcon from '@mui/icons-material/LocalOfferRounded';
import { errorMessage } from '@/api/client';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState, ErrorState, TableLoadingRows } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import { useBusiness } from '@/features/business/api';
import { useServices } from '@/features/catalog/api';
import { useDeleteDiscount, useDiscounts, useSaveDiscount } from '@/features/discounts/api';
import type { Discount, DiscountInput, DiscountState } from '@/types/api';
import { DISCOUNT_STATE_LABELS, formatLocalDate, formatMoney } from '@/utils/format';

const STATE_COLORS: Record<DiscountState, 'success' | 'default' | 'warning' | 'error' | 'info'> = {
  ACTIVE: 'success',
  SCHEDULED: 'info',
  EXPIRED: 'default',
  EXHAUSTED: 'warning',
  INACTIVE: 'default',
};

const EMPTY: DiscountInput = {
  name: '',
  code: '',
  type: 'PERCENTAGE',
  value: 10,
  startsOn: null,
  endsOn: null,
  active: true,
  maxUsage: null,
  minBookingValue: null,
  appliesToAllServices: true,
  serviceIds: [],
};

/** Promo codes. Validity, limits and eligibility are all checked by the server at booking time. */
export function DiscountsPage() {
  const toast = useToast();
  const { data: business } = useBusiness();
  const { data: services } = useServices();
  const { data: discounts, isLoading, isError, error, refetch } = useDiscounts();
  const save = useSaveDiscount();
  const remove = useDeleteDiscount();
  const [draft, setDraft] = useState<(DiscountInput & { id?: number }) | null>(null);
  const [deleting, setDeleting] = useState<Discount | null>(null);
  const currency = business?.currency;

  const submit = async () => {
    if (!draft) {
      return;
    }
    const { id, ...input } = draft;
    try {
      await save.mutateAsync({ id, input: { ...input, code: input.code.trim().toUpperCase(), name: input.name.trim() } });
      toast.success(id ? 'Discount saved' : 'Discount created');
      setDraft(null);
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  const describeValue = (discount: Pick<Discount, 'type' | 'value'>) =>
    discount.type === 'PERCENTAGE' ? `${discount.value}% off` : `${formatMoney(discount.value, currency)} off`;

  return (
    <>
      <PageHeader
        title="Discounts"
        description="Promo codes customers enter while booking."
        action={
          <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={() => setDraft(EMPTY)}>
            New discount
          </Button>
        }
      />
      {isError ? <ErrorState error={error} onRetry={() => refetch()} /> : null}
      {isLoading ? (
        <Paper variant="outlined">
          <TableLoadingRows />
        </Paper>
      ) : discounts?.length === 0 ? (
        <EmptyState icon={<LocalOfferRoundedIcon />} title="No discounts yet" description="Create a code such as SUMMER20 for 20% off." />
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Code</TableCell>
                <TableCell>Discount</TableCell>
                <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>Valid</TableCell>
                <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>Applies to</TableCell>
                <TableCell align="center">Used</TableCell>
                <TableCell>State</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {discounts?.map((discount) => (
                <TableRow key={discount.id} hover>
                  <TableCell>
                    <Typography variant="body2" fontWeight={700} sx={{ fontFamily: 'monospace' }}>
                      {discount.code}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {discount.name}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    {describeValue(discount)}
                    {discount.minBookingValue ? (
                      <Typography variant="caption" color="text.secondary" display="block">
                        Min. {formatMoney(discount.minBookingValue, currency)}
                      </Typography>
                    ) : null}
                  </TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>
                    {discount.startsOn || discount.endsOn
                      ? `${discount.startsOn ? formatLocalDate(discount.startsOn) : '…'} – ${discount.endsOn ? formatLocalDate(discount.endsOn) : '…'}`
                      : 'Always'}
                  </TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>
                    {discount.appliesToAllServices
                      ? 'All services'
                      : discount.serviceIds.map((id) => services?.find((s) => s.id === id)?.name ?? id).join(', ')}
                  </TableCell>
                  <TableCell align="center">
                    {discount.usageCount}
                    {discount.maxUsage ? ` / ${discount.maxUsage}` : ''}
                  </TableCell>
                  <TableCell>
                    <Chip size="small" variant="outlined" color={STATE_COLORS[discount.state]} label={DISCOUNT_STATE_LABELS[discount.state]} />
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    <Tooltip title="Edit">
                      <IconButton
                        onClick={() => {
                          const { usageCount: _usage, state: _state, ...editable } = discount;
                          setDraft(editable);
                        }}
                      >
                        <EditRoundedIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Delete">
                      <IconButton onClick={() => setDeleting(discount)}>
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
        <DialogTitle>{draft?.id ? 'Edit discount' : 'New discount'}</DialogTitle>
        {draft ? (
          <DialogContent>
            <Grid container spacing={2} sx={{ mt: 0 }}>
              <Grid item xs={12} sm={6}>
                <TextField autoFocus fullWidth label="Name" placeholder="Summer promotion" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
              </Grid>
              <Grid item xs={12} sm={6}>
                <TextField
                  fullWidth
                  label="Promo code"
                  placeholder="SUMMER20"
                  value={draft.code}
                  onChange={(e) => setDraft({ ...draft, code: e.target.value.toUpperCase().replace(/[^A-Z0-9_-]/g, '') })}
                />
              </Grid>
              <Grid item xs={6}>
                <TextField select fullWidth label="Type" value={draft.type} onChange={(e) => setDraft({ ...draft, type: e.target.value as DiscountInput['type'] })}>
                  <MenuItem value="PERCENTAGE">Percentage</MenuItem>
                  <MenuItem value="FIXED_AMOUNT">Fixed amount</MenuItem>
                </TextField>
              </Grid>
              <Grid item xs={6}>
                <TextField
                  fullWidth
                  type="number"
                  label="Value"
                  value={draft.value}
                  onChange={(e) => setDraft({ ...draft, value: Number(e.target.value) })}
                  InputProps={{ endAdornment: <InputAdornment position="end">{draft.type === 'PERCENTAGE' ? '%' : currency}</InputAdornment> }}
                  inputProps={{ min: 0.01, max: draft.type === 'PERCENTAGE' ? 100 : undefined, step: '0.01' }}
                />
              </Grid>
              <Grid item xs={6}>
                <TextField fullWidth type="date" label="Start date" value={draft.startsOn ?? ''} InputLabelProps={{ shrink: true }} onChange={(e) => setDraft({ ...draft, startsOn: e.target.value || null })} />
              </Grid>
              <Grid item xs={6}>
                <TextField fullWidth type="date" label="End date" value={draft.endsOn ?? ''} InputLabelProps={{ shrink: true }} onChange={(e) => setDraft({ ...draft, endsOn: e.target.value || null })} />
              </Grid>
              <Grid item xs={6}>
                <TextField
                  fullWidth
                  type="number"
                  label="Maximum usage"
                  value={draft.maxUsage ?? ''}
                  onChange={(e) => setDraft({ ...draft, maxUsage: e.target.value ? Number(e.target.value) : null })}
                  helperText="Empty = unlimited"
                />
              </Grid>
              <Grid item xs={6}>
                <TextField
                  fullWidth
                  type="number"
                  label="Minimum booking value"
                  value={draft.minBookingValue ?? ''}
                  onChange={(e) => setDraft({ ...draft, minBookingValue: e.target.value ? Number(e.target.value) : null })}
                  InputProps={{ endAdornment: <InputAdornment position="end">{currency}</InputAdornment> }}
                />
              </Grid>
              <Grid item xs={12}>
                <FormControlLabel
                  control={<Switch checked={draft.appliesToAllServices} onChange={(e) => setDraft({ ...draft, appliesToAllServices: e.target.checked })} />}
                  label="Applies to all services"
                />
                {!draft.appliesToAllServices ? (
                  <Autocomplete
                    multiple
                    options={services ?? []}
                    getOptionLabel={(option) => option.name}
                    value={(services ?? []).filter((service) => draft.serviceIds.includes(service.id))}
                    onChange={(_, value) => setDraft({ ...draft, serviceIds: value.map((service) => service.id) })}
                    renderInput={(params) => <TextField {...params} label="Applicable services" />}
                    sx={{ mt: 1 }}
                  />
                ) : null}
              </Grid>
              <Grid item xs={12}>
                <FormControlLabel control={<Switch checked={draft.active} onChange={(e) => setDraft({ ...draft, active: e.target.checked })} />} label="Active" />
              </Grid>
            </Grid>
          </DialogContent>
        ) : null}
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setDraft(null)}>Cancel</Button>
          <Button variant="contained" onClick={submit} disabled={!draft?.name.trim() || !draft?.code.trim() || save.isPending}>
            Save
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={deleting !== null}
        title={`Delete ${deleting?.code ?? 'discount'}?`}
        message="Existing reservations keep their discounted price. Customers can no longer use the code."
        confirmLabel="Delete"
        busy={remove.isPending}
        onConfirm={() =>
          deleting &&
          remove
            .mutateAsync(deleting.id)
            .then(() => toast.success('Discount deleted'))
            .catch((err) => toast.error(errorMessage(err)))
            .finally(() => setDeleting(null))
        }
        onClose={() => setDeleting(null)}
      />
    </>
  );
}
