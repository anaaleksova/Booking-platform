import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Container from '@mui/material/Container';
import Divider from '@mui/material/Divider';
import Grid from '@mui/material/Grid';
import LinearProgress from '@mui/material/LinearProgress';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import ArrowBackRoundedIcon from '@mui/icons-material/ArrowBackRounded';
import { errorCode, errorMessage } from '@/api/client';
import { ErrorState, LoadingState } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import { mediaUrl } from '@/features/media/api';
import { createPublicReservation, fetchQuote, usePublicBusiness, usePublicServices } from '@/features/public/api';
import { BusinessShell } from '@/features/public/BusinessShell';
import { queryKeys } from '@/features/queryKeys';
import { guessCountry } from '@/components/PhoneField';
import { useAuth } from '@/features/auth/AuthContext';
import { CustomerAuthPanel } from '@/features/public/wizard/CustomerAuthPanel';
import { DetailsStep, EMPTY_CUSTOMER, customerErrors, type CustomerForm } from '@/features/public/wizard/DetailsStep';
import { OptionsStep } from '@/features/public/wizard/OptionsStep';
import { ReviewStep, type ReviewLine } from '@/features/public/wizard/ReviewStep';
import { ServiceStep } from '@/features/public/wizard/ServiceStep';
import { SpotStep } from '@/features/public/wizard/SpotStep';
import { TimeStep } from '@/features/public/wizard/TimeStep';
import {
  listedRequirements,
  mappedRequirements,
  needsOptions,
  needsSpot,
  selectionsFrom,
  stepTitle,
  stepsFor,
  useWizardState,
  withoutMappedPicks,
  type StepKey,
} from '@/features/public/wizard/useWizardState';
import type { PriceQuote, PublicService } from '@/types/api';
import { formatDuration, formatLocalDate, formatMoney, toLocalDateTimeString } from '@/utils/format';

/**
 * The booking flow as separate views — one decision per screen:
 * service → guests & preferences (when the service has any) → date & time →
 * spot on the map (when the business drew one) → details → review & confirm.
 */
export function BookingWizardPage() {
  const slug = useParams().slug!;
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();
  const business = usePublicBusiness(slug);
  const services = usePublicServices(slug);
  const { choices, step: requestedStep, go } = useWizardState();

  const [customer, setCustomer] = useState<CustomerForm>(EMPTY_CUSTOMER);
  const { user, initialising: authInitialising } = useAuth();

  // Booking needs an account, and the details follow whoever is signed in:
  // whenever the account changes (or signs out), start again from that
  // account's name and email, so nothing typed for another person carries over.
  // The customer can still edit the name afterwards.
  useEffect(() => {
    if (!user) {
      setCustomer(EMPTY_CUSTOMER);
      return;
    }
    const [first = '', ...rest] = user.fullName.trim().split(/\s+/);
    setCustomer({ ...EMPTY_CUSTOMER, email: user.email, firstName: first, lastName: rest.join(' ') });
  }, [user?.id, user?.email, user?.fullName]);
  const [showErrors, setShowErrors] = useState(false);
  const [codeInput, setCodeInput] = useState('');
  const [appliedCode, setAppliedCode] = useState('');
  const [codeError, setCodeError] = useState<string | null>(null);
  const [quote, setQuote] = useState<PriceQuote | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const service = services.data?.find((item) => item.id === choices.serviceId);
  const steps = stepsFor(service);
  const step: StepKey = useMemo(() => {
    if (!service) {
      return 'service';
    }
    if (requestedStep && steps.includes(requestedStep)) {
      // Never land past what has been chosen.
      if ((requestedStep === 'spot' || requestedStep === 'details' || requestedStep === 'review') && !(choices.date && choices.time)) {
        return 'time';
      }
      // Signed out (or the details were lost on a reload): back to the details step.
      if (requestedStep === 'review' && (!user || !customer.phone.trim())) {
        return 'details';
      }
      return requestedStep;
    }
    return needsOptions(service) ? 'options' : 'time';
  }, [service, requestedStep, steps, choices.date, choices.time, user, customer.phone]);
  const stepIndex = steps.indexOf(step);
  const selections = useMemo(() => selectionsFrom(service, choices.picks), [service, choices.picks]);
  // Map choices come after the time, so they never narrow the times offered.
  const timeSelections = useMemo(
    () => selectionsFrom(service, withoutMappedPicks(service, choices.picks)),
    [service, choices.picks],
  );
  const onSpotPicks = useCallback(
    (picks: Record<number, number[]>) => go('spot', { picks }, { replace: true }),
    [go],
  );

  // The price shown before confirming always comes from the server.
  useEffect(() => {
    if (!service || step !== 'review') {
      return;
    }
    let cancelled = false;
    fetchQuote(slug, service.id, choices.people, appliedCode)
      .then((result) => !cancelled && (setQuote(result), setCodeError(null)))
      .catch((error) => {
        if (!cancelled) {
          setCodeError(errorMessage(error));
          setAppliedCode('');
        }
      });
    return () => {
      cancelled = true;
    };
  }, [slug, service, choices.people, appliedCode, step]);

  const onDate = useCallback((date: string) => go('time', { date, time: null }, { replace: true }), [go]);

  if (business.isLoading || services.isLoading) {
    return <LoadingState />;
  }
  if (business.isError || !business.data) {
    return (
      <BusinessShell>
        <Container maxWidth="sm" sx={{ py: 8 }}>
          <ErrorState error={business.error} />
        </Container>
      </BusinessShell>
    );
  }
  const info = business.data;
  const back = () => (stepIndex > 0 ? go(steps[stepIndex - 1]) : navigate(`/b/${slug}`));

  const confirm = async () => {
    if (!service || !choices.date || !choices.time) {
      return;
    }
    setSubmitting(true);
    try {
      const reservation = await createPublicReservation(slug, {
        serviceId: service.id,
        participants: choices.people,
        startTime: toLocalDateTimeString(choices.date, choices.time),
        resourceSelections: selections,
        discountCode: appliedCode || undefined,
        customer: {
          firstName: customer.firstName.trim(),
          lastName: customer.lastName.trim(),
          email: customer.email.trim(),
          phone: customer.phone.trim(),
        },
        notes: customer.notes.trim() || undefined,
      });
      // The confirmation page then renders instantly from what the server just returned.
      queryClient.setQueryData(queryKeys.publicReservation(reservation.reference), reservation);
      navigate(`/booking/${reservation.reference}`, { state: { justBooked: true } });
    } catch (error) {
      toast.error(errorMessage(error));
      const code = errorCode(error);
      if (code === 'RESOURCE_NOT_AVAILABLE' || code === 'RESERVATION_TOO_SOON' || code === 'RESERVATION_IN_THE_PAST') {
        // Someone was faster: show what is still free.
        await queryClient.invalidateQueries({ queryKey: ['public', slug, 'availability'] });
        go('time', { time: null });
      }
    } finally {
      setSubmitting(false);
    }
  };

  const continueAction = (): { label: string; onClick: () => void; disabled?: boolean } | null => {
    switch (step) {
      case 'options':
        return { label: 'Continue', onClick: () => go('time', { time: null }) };
      case 'time':
        return { label: 'Continue', onClick: () => go(needsSpot(service) ? 'spot' : 'details'), disabled: !choices.time };
      case 'spot':
        return { label: 'Continue', onClick: () => go('details') };
      case 'details':
        if (!user) {
          return null;  // the sign-in panel has its own button
        }
        return {
          label: 'Continue',
          onClick: () => {
            setShowErrors(true);
            if (Object.values(customerErrors(customer)).every((message) => !message)) {
              go('review');
            }
          },
        };
      case 'review':
        return { label: submitting ? 'Confirming…' : 'Confirm reservation', onClick: confirm, disabled: submitting || !quote };
      default:
        return null;
    }
  };
  const action = continueAction();

  const namesOf = (requirements: typeof listed) =>
    requirements.flatMap((requirement) =>
      (choices.picks[requirement.requirementId] ?? []).map(
        (id) => requirement.resources.find((resource) => resource.id === id)?.name ?? '',
      ),
    );
  const listed = listedRequirements(service);
  const mapped = mappedRequirements(service);
  const pickedNames = namesOf(listed);
  const spotNames = namesOf(mapped);
  const spotLabel = mapped.length === 1 ? mapped[0].resourceTypeName : 'Seating';
  const estimate = service ? (service.pricePerParticipant ? service.price * choices.people : service.price) : 0;

  const reviewLines: ReviewLine[] = service
    ? [
        { label: 'Service', value: `${service.name} · ${formatDuration(service.durationMinutes)}`, step: 'service' },
        ...(needsOptions(service)
          ? [
              {
                label: 'Guests',
                value: `${choices.people} ${choices.people === 1 ? 'person' : 'people'}${pickedNames.length ? ` · ${pickedNames.join(', ')}` : ''}`,
                step: 'options' as StepKey,
              },
            ]
          : []),
        { label: 'When', value: choices.date && choices.time ? `${formatLocalDate(choices.date)} at ${choices.time}` : '—', step: 'time' },
        ...(mapped.length
          ? [{ label: spotLabel, value: spotNames.length ? spotNames.join(', ') : 'No preference', step: 'spot' as StepKey }]
          : []),
        { label: 'Name', value: `${customer.firstName} ${customer.lastName}`.trim(), step: 'details' },
        { label: 'Contact', value: [customer.email, customer.phone].filter(Boolean).join(' · '), step: 'details' },
      ]
    : [];

  return (
    <BusinessShell business={info} hideBookButton footer={false}>
      <Box sx={{ borderBottom: 1, borderColor: 'divider', bgcolor: 'background.paper' }}>
        <Container maxWidth="lg" sx={{ py: 2 }}>
          <Stack direction="row" alignItems="center" spacing={1.5}>
            <Button startIcon={<ArrowBackRoundedIcon />} onClick={back} color="inherit">
              Back
            </Button>
            <Box sx={{ flex: 1 }}>
              <Typography variant="caption" color="text.secondary">
                Step {stepIndex + 1} of {steps.length}
              </Typography>
              <Typography variant="h6" fontWeight={800} lineHeight={1.2}>
                {stepTitle(step, service)}
              </Typography>
            </Box>
          </Stack>
          <LinearProgress
            variant="determinate"
            value={((stepIndex + 1) / steps.length) * 100}
            sx={{ mt: 1.5, height: 4, borderRadius: 2 }}
            aria-label="Booking progress"
          />
        </Container>
      </Box>

      <Container maxWidth="lg" sx={{ py: { xs: 3, md: 5 }, pb: { xs: 14, md: 6 } }}>
        <Grid container spacing={4}>
          <Grid item xs={12} md={8}>
            <Paper variant="outlined" sx={{ p: { xs: 2, sm: 3 }, borderRadius: 4 }}>
              {step === 'service' ? (
                services.data?.length ? (
                  <ServiceStep
                    services={services.data}
                    selectedId={choices.serviceId}
                    onSelect={(selected: PublicService) =>
                      go(needsOptions(selected) ? 'options' : 'time', {
                        serviceId: selected.id,
                        people: 1,
                        picks: {},
                        date: null,
                        time: null,
                      })
                    }
                  />
                ) : (
                  <Typography color="text.secondary">No services are open for booking right now.</Typography>
                )
              ) : null}
              {step === 'options' && service ? (
                <OptionsStep
                  service={service}
                  people={choices.people}
                  picks={choices.picks}
                  onPeople={(people) => go('options', { people, time: null }, { replace: true })}
                  onPicks={(picks) => go('options', { picks, time: null }, { replace: true })}
                />
              ) : null}
              {step === 'time' && service ? (
                <TimeStep
                  slug={slug}
                  serviceId={service.id}
                  people={choices.people}
                  selections={timeSelections}
                  date={choices.date}
                  time={choices.time}
                  onDate={onDate}
                  onSelect={(date, slot) => {
                    const nextTime = slot.time.slice(0, 5);
                    const sameTime = date === choices.date && nextTime === choices.time;
                    go(needsSpot(service) ? 'spot' : 'details', {
                      date,
                      time: nextTime,
                      picks: sameTime ? choices.picks : withoutMappedPicks(service, choices.picks),
                    });
                  }}
                />
              ) : null}
              {step === 'spot' && service && choices.date && choices.time ? (
                <SpotStep
                  slug={slug}
                  service={service}
                  people={choices.people}
                  date={choices.date}
                  time={choices.time}
                  picks={choices.picks}
                  onPicks={onSpotPicks}
                  onSlotGone={() => go('time', { time: null })}
                />
              ) : null}
              {step === 'details' ? (
                authInitialising ? null : user ? (
                  <DetailsStep
                    value={customer}
                    onChange={setCustomer}
                    showErrors={showErrors}
                    defaultCountry={guessCountry(info?.phone)}
                  />
                ) : (
                  <CustomerAuthPanel />
                )
              ) : null}
              {step === 'review' ? (
                <ReviewStep
                  lines={reviewLines}
                  onEdit={(target) => go(target)}
                  codeInput={codeInput}
                  onCodeInput={(value) => {
                    setCodeInput(value);
                    setCodeError(null);
                  }}
                  onApplyCode={() => setAppliedCode(codeInput.trim())}
                  codeError={codeError}
                  quote={quote}
                />
              ) : null}

              {action ? (
                <Box sx={{ display: { xs: 'none', md: 'flex' }, justifyContent: 'flex-end', mt: 3 }}>
                  <Button variant="contained" size="large" onClick={action.onClick} disabled={action.disabled} sx={{ borderRadius: 999, px: 4 }}>
                    {action.label}
                  </Button>
                </Box>
              ) : null}
            </Paper>
          </Grid>

          <Grid item md={4} sx={{ display: { xs: 'none', md: 'block' } }}>
            <Paper variant="outlined" sx={{ p: 3, borderRadius: 4, position: 'sticky', top: 96 }}>
              <Typography variant="overline" color="text.secondary">
                Your booking
              </Typography>
              {service ? (
                <Stack spacing={1.5} sx={{ mt: 1 }}>
                  <Stack direction="row" spacing={1.5} alignItems="center">
                    <Avatar variant="rounded" src={mediaUrl(service.imageUrl)} sx={{ width: 52, height: 52, borderRadius: 2, bgcolor: 'primary.main' }}>
                      {service.name.charAt(0)}
                    </Avatar>
                    <div>
                      <Typography fontWeight={700}>{service.name}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        {formatDuration(service.durationMinutes)}
                      </Typography>
                    </div>
                  </Stack>
                  {needsOptions(service) ? (
                    <Typography variant="body2">
                      {choices.people} {choices.people === 1 ? 'person' : 'people'}
                      {pickedNames.length ? ` · ${pickedNames.join(', ')}` : ''}
                    </Typography>
                  ) : null}
                  {choices.date && choices.time ? (
                    <Typography variant="body2" fontWeight={600}>
                      {formatLocalDate(choices.date)} at {choices.time}
                    </Typography>
                  ) : null}
                  {spotNames.length ? <Typography variant="body2">{spotNames.join(', ')}</Typography> : null}
                  <Divider />
                  <Stack direction="row" justifyContent="space-between">
                    <Typography fontWeight={700}>Total</Typography>
                    <Typography fontWeight={800}>
                      {formatMoney(quote && step === 'review' ? quote.finalPrice : estimate, service.currency)}
                    </Typography>
                  </Stack>
                </Stack>
              ) : (
                <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                  Choose a service to begin.
                </Typography>
              )}
              <Divider sx={{ my: 2 }} />
              <Typography variant="body2" color="text.secondary">
                {info.address}
              </Typography>
              <Button component={RouterLink} to={`/b/${slug}`} size="small" sx={{ mt: 1, px: 0 }}>
                About {info.name}
              </Button>
            </Paper>
          </Grid>
        </Grid>
      </Container>

      {/* Mobile: the summary and the next action stay under the thumb. */}
      {action ? (
        <Paper
          elevation={8}
          sx={{
            display: { xs: 'flex', md: 'none' },
            position: 'fixed',
            left: 0,
            right: 0,
            bottom: 0,
            zIndex: 20,
            p: 2,
            gap: 2,
            alignItems: 'center',
            borderRadius: 0,
            borderTop: 1,
            borderColor: 'divider',
          }}
        >
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography variant="body2" noWrap fontWeight={700}>
              {service?.name}
            </Typography>
            <Typography variant="caption" color="text.secondary" noWrap display="block">
              {choices.date && choices.time ? `${formatLocalDate(choices.date)} · ${choices.time} · ` : ''}
              {service ? formatMoney(quote && step === 'review' ? quote.finalPrice : estimate, service.currency) : ''}
            </Typography>
          </Box>
          <Button variant="contained" size="large" onClick={action.onClick} disabled={action.disabled} sx={{ borderRadius: 999, px: 3, flexShrink: 0 }}>
            {action.label}
          </Button>
        </Paper>
      ) : null}
    </BusinessShell>
  );
}
