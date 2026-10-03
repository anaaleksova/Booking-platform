import { useEffect, useMemo, useState } from 'react';
import dayjs from 'dayjs';
import Alert from '@mui/material/Alert';
import Autocomplete from '@mui/material/Autocomplete';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Divider from '@mui/material/Divider';
import Grid from '@mui/material/Grid';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import { errorMessage } from '@/api/client';
import { ResourcePicker, completeSelections } from '@/components/ResourcePicker';
import { LoadingState } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import { useServices } from '@/features/catalog/api';
import { useCustomers } from '@/features/customers/api';
import { fetchAdminQuote, useAdminAvailability, useCreateReservation } from '@/features/reservations/api';
import { SlotPicker } from '@/features/reservations/SlotPicker';
import type { Customer, PriceQuote, ResourceSelection, Slot } from '@/types/api';
import { formatMoney, toLocalDateTimeString } from '@/utils/format';

/**
 * A reservation taken by the business — over the phone, at the desk.
 * Lead-time rules do not apply here; opening hours and conflicts do, and the
 * admin may pick resources for any requirement.
 */
export function NewReservationDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'));
  const toast = useToast();
  const { data: services } = useServices();
  const create = useCreateReservation();

  const [customerMode, setCustomerMode] = useState<'existing' | 'new'>('existing');
  const [customerQuery, setCustomerQuery] = useState('');
  const [customer, setCustomer] = useState<Customer | null>(null);
  const [newCustomer, setNewCustomer] = useState({ firstName: '', lastName: '', email: '', phone: '' });
  const [serviceId, setServiceId] = useState<number | ''>('');
  const [participants, setParticipants] = useState(1);
  const [date, setDate] = useState(dayjs().format('YYYY-MM-DD'));
  const [slot, setSlot] = useState<Slot | null>(null);
  const [selections, setSelections] = useState<ResourceSelection[]>([]);
  const [discountCode, setDiscountCode] = useState('');
  const [quote, setQuote] = useState<PriceQuote | null>(null);
  const [quoteError, setQuoteError] = useState<string | null>(null);
  const [notes, setNotes] = useState('');

  const customers = useCustomers(customerQuery, 0, 10);
  const activeServices = useMemo(() => (services ?? []).filter((service) => service.active), [services]);
  const service = activeServices.find((item) => item.id === serviceId);
  const availability = useAdminAvailability(service?.id, date, participants);

  useEffect(() => {
    if (!open) {
      setCustomer(null);
      setNewCustomer({ firstName: '', lastName: '', email: '', phone: '' });
      setServiceId('');
      setParticipants(1);
      setSlot(null);
      setSelections([]);
      setDiscountCode('');
      setNotes('');
    }
  }, [open]);

  // The price is always the server's answer, including the discount check.
  useEffect(() => {
    if (!service) {
      setQuote(null);
      return;
    }
    let cancelled = false;
    const timer = setTimeout(() => {
      fetchAdminQuote(service.id, participants, discountCode.trim())
        .then((result) => !cancelled && (setQuote(result), setQuoteError(null)))
        .catch((error) => !cancelled && (setQuote(null), setQuoteError(errorMessage(error))));
    }, 300);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [service, participants, discountCode]);

  const customerReady =
    customerMode === 'existing'
      ? customer !== null
      : newCustomer.firstName.trim() !== '' && newCustomer.email.trim() !== '' && newCustomer.phone.trim() !== '';
  const canSubmit = service && slot && customerReady && !quoteError && !create.isPending;

  const submit = async () => {
    if (!service || !slot) {
      return;
    }
    try {
      await create.mutateAsync({
        serviceId: service.id,
        participants,
        startTime: toLocalDateTimeString(date, slot.time),
        resourceSelections: completeSelections(slot.requirements, selections),
        discountCode: discountCode.trim() || undefined,
        customerId: customerMode === 'existing' ? customer?.id : undefined,
        customer:
          customerMode === 'new'
            ? {
                firstName: newCustomer.firstName.trim(),
                lastName: newCustomer.lastName.trim() || undefined,
                email: newCustomer.email.trim(),
                phone: newCustomer.phone.trim(),
              }
            : undefined,
        internalNotes: notes.trim() || undefined,
      });
      toast.success('Reservation created');
      onClose();
    } catch (error) {
      toast.error(errorMessage(error));
    }
  };

  return (
    <Dialog open={open} onClose={create.isPending ? undefined : onClose} maxWidth="md" fullWidth fullScreen={fullScreen}>
      <DialogTitle>New reservation</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={3}>
          <section>
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1.5 }}>
              <Typography variant="subtitle1" fontWeight={700}>
                Customer
              </Typography>
              <ToggleButtonGroup
                size="small"
                exclusive
                value={customerMode}
                onChange={(_, value) => value && setCustomerMode(value)}
              >
                <ToggleButton value="existing">Existing</ToggleButton>
                <ToggleButton value="new">New</ToggleButton>
              </ToggleButtonGroup>
            </Stack>
            {customerMode === 'existing' ? (
              <Autocomplete
                options={customers.data?.content ?? []}
                value={customer}
                onChange={(_, value) => setCustomer(value)}
                onInputChange={(_, value) => setCustomerQuery(value)}
                getOptionLabel={(option) => option.fullName}
                isOptionEqualToValue={(a, b) => a.id === b.id}
                filterOptions={(options) => options}
                renderOption={(props, option) => (
                  <li {...props} key={option.id}>
                    <Stack>
                      <span>{option.fullName}</span>
                      <Typography variant="caption" color="text.secondary">
                        {[option.email, option.phone].filter(Boolean).join(' · ')}
                      </Typography>
                    </Stack>
                  </li>
                )}
                renderInput={(params) => <TextField {...params} label="Search by name, email or phone" />}
              />
            ) : (
              <Grid container spacing={2}>
                {(
                  [
                    ['firstName', 'First name'],
                    ['lastName', 'Last name'],
                    ['email', 'Email'],
                    ['phone', 'Phone'],
                  ] as const
                ).map(([key, label]) => (
                  <Grid item xs={12} sm={6} key={key}>
                    <TextField
                      fullWidth
                      label={label}
                      value={newCustomer[key]}
                      onChange={(event) => setNewCustomer({ ...newCustomer, [key]: event.target.value })}
                      required={key !== 'lastName'}
                    />
                  </Grid>
                ))}
              </Grid>
            )}
          </section>

          <Divider />

          <section>
            <Typography variant="subtitle1" fontWeight={700} sx={{ mb: 1.5 }}>
              Service and time
            </Typography>
            <Grid container spacing={2}>
              <Grid item xs={12} sm={6}>
                <TextField
                  select
                  fullWidth
                  label="Service"
                  value={serviceId}
                  onChange={(event) => {
                    setServiceId(Number(event.target.value));
                    setParticipants(1);
                    setSlot(null);
                    setSelections([]);
                  }}
                >
                  {activeServices.map((item) => (
                    <MenuItem key={item.id} value={item.id}>
                      {item.name}
                    </MenuItem>
                  ))}
                </TextField>
              </Grid>
              <Grid item xs={6} sm={3}>
                <TextField
                  type="number"
                  fullWidth
                  label="Participants"
                  value={participants}
                  disabled={!service}
                  inputProps={{ min: 1, max: service?.maxParticipants ?? 1 }}
                  onChange={(event) => {
                    const value = Math.max(1, Math.min(service?.maxParticipants ?? 1, Number(event.target.value) || 1));
                    setParticipants(value);
                    setSlot(null);
                    setSelections([]);
                  }}
                  helperText={service ? `Max ${service.maxParticipants}` : undefined}
                />
              </Grid>
              <Grid item xs={6} sm={3}>
                <TextField
                  type="date"
                  fullWidth
                  label="Date"
                  value={date}
                  InputLabelProps={{ shrink: true }}
                  onChange={(event) => {
                    setDate(event.target.value);
                    setSlot(null);
                    setSelections([]);
                  }}
                />
              </Grid>
            </Grid>
            {service ? (
              <Stack spacing={2} sx={{ mt: 2 }}>
                {availability.isLoading ? <LoadingState label="Checking availability…" /> : null}
                {availability.isError ? <Alert severity="warning">{errorMessage(availability.error)}</Alert> : null}
                <SlotPicker
                  slots={availability.data?.slots}
                  selected={slot ? slot.time.slice(0, 5) : null}
                  onSelect={(next) => {
                    setSlot(next);
                    setSelections([]);
                  }}
                />
                {slot && slot.requirements.length > 0 ? (
                  <>
                    <Typography variant="body2" color="text.secondary">
                      Optionally pick resources; anything left empty is assigned automatically.
                    </Typography>
                    <ResourcePicker requirements={slot.requirements} value={selections} onChange={setSelections} />
                  </>
                ) : null}
              </Stack>
            ) : null}
          </section>

          <Divider />

          <Grid container spacing={2}>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Discount code"
                value={discountCode}
                onChange={(event) => setDiscountCode(event.target.value.toUpperCase())}
                error={Boolean(quoteError) && discountCode !== ''}
                helperText={discountCode && quoteError ? quoteError : quote?.discountName ?? ' '}
              />
              <TextField
                fullWidth
                multiline
                minRows={2}
                label="Internal notes"
                value={notes}
                onChange={(event) => setNotes(event.target.value)}
                sx={{ mt: 1 }}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              {quote ? (
                <Stack spacing={0.5} sx={{ p: 2, bgcolor: 'background.default', borderRadius: 2 }}>
                  <Line label="Service" value={formatMoney(quote.originalPrice, quote.currency)} />
                  <Line label="Discount" value={`− ${formatMoney(quote.discountAmount, quote.currency)}`} />
                  <Divider />
                  <Line bold label="Total" value={formatMoney(quote.finalPrice, quote.currency)} />
                </Stack>
              ) : null}
            </Grid>
          </Grid>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose} disabled={create.isPending}>
          Cancel
        </Button>
        <Button variant="contained" onClick={submit} disabled={!canSubmit}>
          {create.isPending ? 'Saving…' : 'Create reservation'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function Line({ label, value, bold }: { label: string; value: string; bold?: boolean }) {
  return (
    <Stack direction="row" justifyContent="space-between">
      <Typography variant="body2" fontWeight={bold ? 700 : 400}>
        {label}
      </Typography>
      <Typography variant="body2" fontWeight={bold ? 700 : 400}>
        {value}
      </Typography>
    </Stack>
  );
}
