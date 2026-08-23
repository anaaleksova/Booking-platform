import { useEffect, useMemo, useState } from 'react';
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
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import dayjs from 'dayjs';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { SlotPicker } from '@/features/availability/SlotPicker';
import { useCreateBooking } from '@/features/bookings/useBookings';
import { useCustomers } from '@/features/customers/useCustomers';
import { useServices } from '@/features/services/useServices';
import { useStaff } from '@/features/staff/useStaff';
import { useResources } from '@/features/resources/useResources';
import { useToast } from '@/components/ToastProvider';
import { errorMessage } from '@/api/client';
import { formatDuration, formatMoney } from '@/utils/format';
import type { AvailableSlot, Customer } from '@/types/api';

/**
 * Manual booking — the workflow most local businesses actually live on, since
 * their customers call, message or walk in.
 *
 * <p>It offers the same slots as the public page and is validated by the same
 * engine; only the lead-time rules are relaxed, so a business can still write
 * down the customer standing in front of them.
 */
export function BookingFormDialog({
  open,
  onClose,
  initialDate,
  initialStaffId,
}: {
  open: boolean;
  onClose: () => void;
  initialDate?: string;
  initialStaffId?: number | null;
}) {
  const toast = useToast();
  const createBooking = useCreateBooking();

  const { data: services = [] } = useServices(true);
  const { data: staff = [] } = useStaff(true);
  const { data: resources = [] } = useResources(true);

  const [serviceId, setServiceId] = useState<number | ''>('');
  const [staffId, setStaffId] = useState<number | ''>(initialStaffId ?? '');
  const [resourceId, setResourceId] = useState<number | ''>('');
  const [date, setDate] = useState(initialDate ?? dayjs().format('YYYY-MM-DD'));
  const [slot, setSlot] = useState<AvailableSlot | null>(null);

  const [customerTab, setCustomerTab] = useState<'existing' | 'new'>('existing');
  const [customer, setCustomer] = useState<Customer | null>(null);
  const [customerSearch, setCustomerSearch] = useState('');
  const [newCustomer, setNewCustomer] = useState({ name: '', phone: '', email: '' });

  const [customerNotes, setCustomerNotes] = useState('');
  const [internalNotes, setInternalNotes] = useState('');
  const [submitError, setSubmitError] = useState<string | null>(null);

  const { data: customerPage } = useCustomers(customerSearch, 0, 20);
  const selectedService = services.find((service) => service.id === serviceId);

  // Only staff and resources that can deliver the chosen service are offered.
  const eligibleStaff = useMemo(
    () =>
      serviceId === ''
        ? staff
        : staff.filter((member) => member.serviceIds.length === 0 || member.serviceIds.includes(serviceId)),
    [staff, serviceId],
  );
  const eligibleResources = useMemo(
    () =>
      serviceId === ''
        ? resources
        : resources.filter(
            (resource) => resource.serviceIds.length === 0 || resource.serviceIds.includes(serviceId),
          ),
    [resources, serviceId],
  );

  // The chosen time belongs to one exact combination; change any input and it
  // has to be picked again rather than silently re-used.
  useEffect(() => setSlot(null), [serviceId, staffId, resourceId, date]);

  useEffect(() => {
    if (open) {
      setSubmitError(null);
      setDate(initialDate ?? dayjs().format('YYYY-MM-DD'));
      setStaffId(initialStaffId ?? '');
    }
  }, [open, initialDate, initialStaffId]);

  const reset = () => {
    setServiceId('');
    setStaffId('');
    setResourceId('');
    setSlot(null);
    setCustomer(null);
    setNewCustomer({ name: '', phone: '', email: '' });
    setCustomerNotes('');
    setInternalNotes('');
    setSubmitError(null);
  };

  const canSubmit =
    serviceId !== '' &&
    slot !== null &&
    (customerTab === 'existing'
      ? customer !== null
      : newCustomer.name.trim().length > 0 &&
        (newCustomer.phone.trim().length > 0 || newCustomer.email.trim().length > 0));

  const handleSubmit = async () => {
    // Ordered so the narrowing checks come first: after this guard TypeScript
    // knows serviceId is a number and slot is present.
    if (serviceId === '' || !slot || !canSubmit) {
      return;
    }
    setSubmitError(null);
    try {
      await createBooking.mutateAsync({
        serviceId,
        staffId: staffId === '' ? null : staffId,
        resourceId: resourceId === '' ? null : resourceId,
        customerId: customerTab === 'existing' ? customer?.id : null,
        customer:
          customerTab === 'new'
            ? {
                name: newCustomer.name.trim(),
                phone: newCustomer.phone.trim() || undefined,
                email: newCustomer.email.trim() || undefined,
              }
            : undefined,
        // The slot's local time in the business timezone, which is what the
        // API expects and what the picker displayed.
        startDateTime: `${date}T${slot.time}`,
        customerNotes: customerNotes.trim() || undefined,
        internalNotes: internalNotes.trim() || undefined,
      });
      toast.success('Booking created.');
      reset();
      onClose();
    } catch (error) {
      setSubmitError(errorMessage(error, 'Could not create the booking.'));
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>New booking</DialogTitle>
      <DialogContent dividers>
        <Grid container spacing={3}>
          <Grid item xs={12} md={6}>
            <Stack spacing={2.5}>
              <TextField
                select
                label="Service"
                value={serviceId}
                onChange={(event) => setServiceId(Number(event.target.value))}
                fullWidth
                required
              >
                {services.map((service) => (
                  <MenuItem key={service.id} value={service.id}>
                    {service.name} · {formatDuration(service.durationMinutes)}
                    {service.price != null ? ` · ${formatMoney(service.price, service.currency)}` : ''}
                  </MenuItem>
                ))}
              </TextField>

              {selectedService?.requiresStaff !== false ? (
                <TextField
                  select
                  label="Staff"
                  value={staffId}
                  onChange={(event) =>
                    setStaffId(event.target.value === '' ? '' : Number(event.target.value))
                  }
                  fullWidth
                  helperText="Leave as “any available” to let the system assign someone"
                >
                  <MenuItem value="">Any available</MenuItem>
                  {eligibleStaff.map((member) => (
                    <MenuItem key={member.id} value={member.id}>
                      {member.name}
                    </MenuItem>
                  ))}
                </TextField>
              ) : null}

              {selectedService?.requiresResource ? (
                <TextField
                  select
                  label="Resource"
                  value={resourceId}
                  onChange={(event) =>
                    setResourceId(event.target.value === '' ? '' : Number(event.target.value))
                  }
                  fullWidth
                  helperText="Required for this service"
                >
                  <MenuItem value="">Any available</MenuItem>
                  {eligibleResources.map((resource) => (
                    <MenuItem key={resource.id} value={resource.id}>
                      {resource.name}
                    </MenuItem>
                  ))}
                </TextField>
              ) : null}

              <DatePicker
                label="Date"
                value={dayjs(date)}
                onChange={(value) => value && setDate(value.format('YYYY-MM-DD'))}
                slotProps={{ textField: { fullWidth: true, size: 'small' } }}
              />

              <div>
                <Typography variant="subtitle2" gutterBottom>
                  Available times
                </Typography>
                <SlotPicker
                  serviceId={serviceId === '' ? undefined : serviceId}
                  date={date}
                  staffId={staffId === '' ? null : staffId}
                  resourceId={resourceId === '' ? null : resourceId}
                  selected={slot}
                  onSelect={setSlot}
                />
              </div>
            </Stack>
          </Grid>

          <Grid item xs={12} md={6}>
            <Stack spacing={2.5}>
              <div>
                <Typography variant="subtitle2" gutterBottom>
                  Customer
                </Typography>
                <Tabs
                  value={customerTab}
                  onChange={(_, value) => setCustomerTab(value)}
                  sx={{ mb: 2, minHeight: 36 }}
                >
                  <Tab label="Existing" value="existing" sx={{ minHeight: 36 }} />
                  <Tab label="New" value="new" sx={{ minHeight: 36 }} />
                </Tabs>

                {customerTab === 'existing' ? (
                  <Autocomplete
                    options={customerPage?.content ?? []}
                    getOptionLabel={(option) =>
                      `${option.name}${option.phone ? ` · ${option.phone}` : ''}`
                    }
                    value={customer}
                    onChange={(_, value) => setCustomer(value)}
                    onInputChange={(_, value) => setCustomerSearch(value)}
                    isOptionEqualToValue={(option, value) => option.id === value.id}
                    renderInput={(params) => (
                      <TextField {...params} label="Search customers" placeholder="Name, phone or email" />
                    )}
                  />
                ) : (
                  <Stack spacing={2}>
                    <TextField
                      label="Name"
                      required
                      fullWidth
                      value={newCustomer.name}
                      onChange={(event) => setNewCustomer({ ...newCustomer, name: event.target.value })}
                    />
                    <TextField
                      label="Phone"
                      fullWidth
                      value={newCustomer.phone}
                      onChange={(event) => setNewCustomer({ ...newCustomer, phone: event.target.value })}
                      helperText="A phone number or an email is required"
                    />
                    <TextField
                      label="Email"
                      type="email"
                      fullWidth
                      value={newCustomer.email}
                      onChange={(event) => setNewCustomer({ ...newCustomer, email: event.target.value })}
                    />
                  </Stack>
                )}
              </div>

              <Divider />

              <TextField
                label="Customer notes"
                multiline
                minRows={2}
                fullWidth
                value={customerNotes}
                onChange={(event) => setCustomerNotes(event.target.value)}
              />
              <TextField
                label="Internal notes"
                multiline
                minRows={2}
                fullWidth
                value={internalNotes}
                onChange={(event) => setInternalNotes(event.target.value)}
                helperText="Only visible to your team"
              />

              {submitError ? <Alert severity="error">{submitError}</Alert> : null}
            </Stack>
          </Grid>
        </Grid>
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          onClick={() => void handleSubmit()}
          disabled={!canSubmit || createBooking.isPending}
        >
          {createBooking.isPending ? 'Creating…' : 'Create booking'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
