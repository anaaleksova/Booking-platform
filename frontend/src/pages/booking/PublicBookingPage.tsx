import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardActionArea from '@mui/material/CardActionArea';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Container from '@mui/material/Container';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import Skeleton from '@mui/material/Skeleton';
import Stack from '@mui/material/Stack';
import Step from '@mui/material/Step';
import StepLabel from '@mui/material/StepLabel';
import Stepper from '@mui/material/Stepper';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import ArrowBackRoundedIcon from '@mui/icons-material/ArrowBackRounded';
import ChevronLeftRoundedIcon from '@mui/icons-material/ChevronLeftRounded';
import ChevronRightRoundedIcon from '@mui/icons-material/ChevronRightRounded';
import PlaceRoundedIcon from '@mui/icons-material/PlaceRounded';
import PhoneRoundedIcon from '@mui/icons-material/PhoneRounded';
import dayjs from 'dayjs';
import {
  useCreatePublicBooking,
  usePublicAvailability,
  usePublicBusiness,
  usePublicServices,
  usePublicStaff,
} from '@/features/publicBooking/usePublicBooking';
import { errorMessage } from '@/api/client';
import { formatDuration, formatMoney } from '@/utils/format';
import type { AvailableSlot, PublicService } from '@/types/api';

const STEPS = ['Service', 'With whom', 'Date & time', 'Your details'];

/**
 * The customer booking flow: service → staff → date → time → details.
 *
 * <p>Mobile-first, and no account required — most people book from a phone
 * after seeing a link, and any sign-up step loses them.
 */
export function PublicBookingPage() {
  const { slug = '' } = useParams();
  const navigate = useNavigate();

  const { data: business, isLoading, isError, error } = usePublicBusiness(slug);
  const { data: services = [] } = usePublicServices(slug);

  const [step, setStep] = useState(0);
  const [service, setService] = useState<PublicService | null>(null);
  const [staffId, setStaffId] = useState<number | null>(null);
  const [weekStart, setWeekStart] = useState(dayjs().startOf('day'));
  const [date, setDate] = useState<string | null>(null);
  const [slot, setSlot] = useState<AvailableSlot | null>(null);
  const [details, setDetails] = useState({ name: '', phone: '', email: '', notes: '' });
  const [submitError, setSubmitError] = useState<string | null>(null);

  const { data: staff = [] } = usePublicStaff(slug, service?.id ?? null);
  const createBooking = useCreatePublicBooking(slug);

  // A week at a time keeps the request small and the page fast on mobile.
  const { data: week = [], isFetching: loadingWeek } = usePublicAvailability(slug, {
    serviceId: service?.id ?? null,
    from: weekStart.format('YYYY-MM-DD'),
    to: weekStart.add(6, 'day').format('YYYY-MM-DD'),
    staffId,
  });

  const slotsForDate = useMemo(
    () => (date ? (week.find((day) => day.date === date)?.slots ?? []) : []),
    [week, date],
  );

  if (isLoading) {
    return (
      <Container maxWidth="sm" sx={{ py: 6 }}>
        <Skeleton variant="rounded" height={140} sx={{ mb: 3 }} />
        <Skeleton variant="rounded" height={280} />
      </Container>
    );
  }

  if (isError || !business) {
    return (
      <Container maxWidth="sm" sx={{ py: 8 }}>
        <Alert severity="error">
          {errorMessage(error, 'This booking page could not be found.')}
        </Alert>
      </Container>
    );
  }

  const canSubmit =
    service !== null &&
    slot !== null &&
    details.name.trim().length > 0 &&
    (details.phone.trim().length > 0 || details.email.trim().length > 0);

  const submit = async () => {
    if (!canSubmit || !service || !slot || !date) return;
    setSubmitError(null);
    try {
      const booking = await createBooking.mutateAsync({
        serviceId: service.id,
        staffId,
        startDateTime: `${date}T${slot.time}`,
        name: details.name.trim(),
        phone: details.phone.trim() || undefined,
        email: details.email.trim() || undefined,
        notes: details.notes.trim() || undefined,
      });
      navigate(`/booking/${booking.reference}`);
    } catch (bookingError) {
      // The slot may have gone while the form was being filled in — the
      // backend re-checks and says so, and the picker is refreshed.
      setSubmitError(errorMessage(bookingError, 'Could not complete the booking.'));
      setSlot(null);
      setStep(2);
    }
  };

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default', pb: 6 }}>
      <Box sx={{ bgcolor: 'primary.main', color: 'primary.contrastText', py: { xs: 4, sm: 5 } }}>
        <Container maxWidth="sm">
          <Stack direction="row" spacing={2} alignItems="center">
            {business.logoUrl ? (
              <Avatar src={business.logoUrl} sx={{ width: 56, height: 56 }} variant="rounded" />
            ) : null}
            <Stack>
              <Typography variant="h5">{business.name}</Typography>
              {business.description ? (
                <Typography variant="body2" sx={{ opacity: 0.9, mt: 0.5 }}>
                  {business.description}
                </Typography>
              ) : null}
            </Stack>
          </Stack>
          <Stack direction="row" flexWrap="wrap" gap={2} sx={{ mt: 2, opacity: 0.9 }}>
            {business.address ? (
              <Stack direction="row" spacing={0.5} alignItems="center">
                <PlaceRoundedIcon fontSize="small" />
                <Typography variant="body2">{business.address}</Typography>
              </Stack>
            ) : null}
            {business.phone ? (
              <Stack direction="row" spacing={0.5} alignItems="center">
                <PhoneRoundedIcon fontSize="small" />
                <Typography variant="body2">{business.phone}</Typography>
              </Stack>
            ) : null}
          </Stack>
        </Container>
      </Box>

      <Container maxWidth="sm" sx={{ mt: -3 }}>
        <Card>
          <CardContent sx={{ p: { xs: 2, sm: 3 } }}>
            <Stepper activeStep={step} alternativeLabel sx={{ mb: 3 }}>
              {STEPS.map((label) => (
                <Step key={label}>
                  <StepLabel>{label}</StepLabel>
                </Step>
              ))}
            </Stepper>

            {step > 0 ? (
              <Button
                size="small"
                startIcon={<ArrowBackRoundedIcon />}
                onClick={() => setStep(step - 1)}
                sx={{ mb: 2 }}
              >
                Back
              </Button>
            ) : null}

            {/* Step 1 — service */}
            {step === 0 ? (
              <Stack spacing={1.5}>
                {services.length === 0 ? (
                  <Alert severity="info">This business has no services available for booking yet.</Alert>
                ) : null}
                {services.map((option) => (
                  <Card key={option.id} variant="outlined">
                    <CardActionArea
                      onClick={() => {
                        setService(option);
                        setStaffId(null);
                        setDate(null);
                        setSlot(null);
                        setStep(option.requiresStaff ? 1 : 2);
                      }}
                    >
                      <CardContent>
                        <Stack direction="row" justifyContent="space-between" spacing={2}>
                          <Stack>
                            <Typography variant="subtitle1" fontWeight={700}>
                              {option.name}
                            </Typography>
                            {option.description ? (
                              <Typography variant="body2" color="text.secondary">
                                {option.description}
                              </Typography>
                            ) : null}
                            <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5 }}>
                              {formatDuration(option.durationMinutes)}
                            </Typography>
                          </Stack>
                          {option.price != null ? (
                            <Typography variant="subtitle1" fontWeight={700} whiteSpace="nowrap">
                              {formatMoney(option.price, option.currency)}
                            </Typography>
                          ) : null}
                        </Stack>
                      </CardContent>
                    </CardActionArea>
                  </Card>
                ))}
              </Stack>
            ) : null}

            {/* Step 2 — staff */}
            {step === 1 ? (
              <Stack spacing={1.5}>
                <Card variant="outlined">
                  <CardActionArea
                    onClick={() => {
                      setStaffId(null);
                      setStep(2);
                    }}
                  >
                    <CardContent>
                      <Typography variant="subtitle1" fontWeight={700}>
                        Any available
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Usually the most times to choose from.
                      </Typography>
                    </CardContent>
                  </CardActionArea>
                </Card>
                {staff.map((member) => (
                  <Card key={member.id} variant="outlined">
                    <CardActionArea
                      onClick={() => {
                        setStaffId(member.id);
                        setStep(2);
                      }}
                    >
                      <CardContent>
                        <Stack direction="row" spacing={2} alignItems="center">
                          <Avatar src={member.photoUrl ?? undefined}>{member.name.charAt(0)}</Avatar>
                          <Typography variant="subtitle1" fontWeight={600}>
                            {member.name}
                          </Typography>
                        </Stack>
                      </CardContent>
                    </CardActionArea>
                  </Card>
                ))}
              </Stack>
            ) : null}

            {/* Step 3 — date and time */}
            {step === 2 ? (
              <Stack spacing={2.5}>
                <Stack direction="row" alignItems="center" justifyContent="space-between">
                  <IconButton
                    onClick={() => setWeekStart(weekStart.subtract(7, 'day'))}
                    disabled={weekStart.isBefore(dayjs(), 'day')}
                    aria-label="Previous week"
                  >
                    <ChevronLeftRoundedIcon />
                  </IconButton>
                  <Typography variant="subtitle2">
                    {weekStart.format('D MMM')} – {weekStart.add(6, 'day').format('D MMM')}
                  </Typography>
                  <IconButton onClick={() => setWeekStart(weekStart.add(7, 'day'))} aria-label="Next week">
                    <ChevronRightRoundedIcon />
                  </IconButton>
                </Stack>

                <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 0.75 }}>
                  {Array.from({ length: 7 }, (_, index) => weekStart.add(index, 'day')).map((day) => {
                    const key = day.format('YYYY-MM-DD');
                    const dayData = week.find((entry) => entry.date === key);
                    const hasSlots = (dayData?.slots.length ?? 0) > 0;
                    const isSelected = date === key;
                    return (
                      <Button
                        key={key}
                        size="small"
                        variant={isSelected ? 'contained' : 'outlined'}
                        disabled={!hasSlots && !loadingWeek}
                        onClick={() => {
                          setDate(key);
                          setSlot(null);
                        }}
                        sx={{ flexDirection: 'column', py: 1, minWidth: 0 }}
                      >
                        <Typography variant="caption" component="span">
                          {day.format('dd')}
                        </Typography>
                        <Typography variant="body2" component="span" fontWeight={700}>
                          {day.format('D')}
                        </Typography>
                      </Button>
                    );
                  })}
                </Box>

                <Divider />

                {!date ? (
                  <Typography variant="body2" color="text.secondary">
                    Pick a day to see the available times.
                  </Typography>
                ) : slotsForDate.length === 0 ? (
                  <Alert severity="info">No times left on this day. Try another one.</Alert>
                ) : (
                  <Stack direction="row" flexWrap="wrap" gap={1}>
                    {slotsForDate.map((option) => (
                      <Chip
                        key={option.startDateTime}
                        label={option.time.slice(0, 5)}
                        clickable
                        color={slot?.startDateTime === option.startDateTime ? 'primary' : 'default'}
                        variant={slot?.startDateTime === option.startDateTime ? 'filled' : 'outlined'}
                        onClick={() => setSlot(option)}
                      />
                    ))}
                  </Stack>
                )}

                {submitError ? <Alert severity="warning">{submitError}</Alert> : null}

                <Button variant="contained" size="large" disabled={!slot} onClick={() => setStep(3)}>
                  Continue
                </Button>
              </Stack>
            ) : null}

            {/* Step 4 — details */}
            {step === 3 ? (
              <Stack spacing={2.5}>
                <SummaryBox
                  serviceName={service?.name ?? ''}
                  staffName={staff.find((member) => member.id === staffId)?.name ?? 'Any available'}
                  date={date ?? ''}
                  time={slot?.time.slice(0, 5) ?? ''}
                  duration={service?.durationMinutes ?? 0}
                  price={service?.price ?? null}
                  currency={service?.currency ?? null}
                  timezone={business.timezone}
                />

                <TextField
                  label="Your name"
                  required
                  fullWidth
                  value={details.name}
                  onChange={(event) => setDetails({ ...details, name: event.target.value })}
                />
                <TextField
                  label="Phone"
                  fullWidth
                  value={details.phone}
                  onChange={(event) => setDetails({ ...details, phone: event.target.value })}
                  helperText="A phone number or an email is required"
                />
                <TextField
                  label="Email"
                  type="email"
                  fullWidth
                  value={details.email}
                  onChange={(event) => setDetails({ ...details, email: event.target.value })}
                  helperText="We send your confirmation here"
                />
                <TextField
                  label="Anything we should know?"
                  fullWidth
                  multiline
                  minRows={2}
                  value={details.notes}
                  onChange={(event) => setDetails({ ...details, notes: event.target.value })}
                />

                {submitError ? <Alert severity="error">{submitError}</Alert> : null}

                <Button
                  variant="contained"
                  size="large"
                  disabled={!canSubmit || createBooking.isPending}
                  onClick={() => void submit()}
                >
                  {createBooking.isPending ? 'Confirming…' : 'Confirm booking'}
                </Button>
              </Stack>
            ) : null}
          </CardContent>
        </Card>

        <Typography variant="caption" color="text.secondary" display="block" textAlign="center" sx={{ mt: 3 }}>
          Times are shown in {business.timezone}.
        </Typography>
      </Container>
    </Box>
  );
}

function SummaryBox({
  serviceName,
  staffName,
  date,
  time,
  duration,
  price,
  currency,
  timezone,
}: {
  serviceName: string;
  staffName: string;
  date: string;
  time: string;
  duration: number;
  price: number | null;
  currency: string | null;
  timezone: string;
}) {
  return (
    <Card variant="outlined" sx={{ bgcolor: 'action.hover' }}>
      <CardContent>
        <Stack spacing={0.5}>
          <Typography variant="subtitle1" fontWeight={700}>
            {serviceName}
          </Typography>
          <Typography variant="body2">
            {dayjs(date).format('dddd, D MMMM YYYY')} at {time} ({timezone})
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {staffName} · {formatDuration(duration)}
            {price != null ? ` · ${formatMoney(price, currency)}` : ''}
          </Typography>
        </Stack>
      </CardContent>
    </Card>
  );
}
