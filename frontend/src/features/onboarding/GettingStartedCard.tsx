import { Link as RouterLink } from 'react-router-dom';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import LinearProgress from '@mui/material/LinearProgress';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded';
import RadioButtonUncheckedRoundedIcon from '@mui/icons-material/RadioButtonUncheckedRounded';
import RocketLaunchRoundedIcon from '@mui/icons-material/RocketLaunchRounded';
import { errorMessage } from '@/api/client';
import { useToast } from '@/components/ToastProvider';
import { useDismissOnboarding, useMarkOnboardingStep, useOnboarding } from '@/features/onboarding/api';
import { businessTypeInfo } from '@/features/onboarding/businessTypes';
import type { Onboarding, OnboardingStepKey } from '@/types/api';

interface StepCopy {
  title: string;
  description: string;
  to?: string;
  /** Whether the owner can tick it with "Looks good"; the others follow their details. */
  confirmable: boolean;
}

/** What each step says, in the words of the owner's kind of business. */
function copyOf(key: OnboardingStepKey, onboarding: Onboarding): StepCopy {
  const info = businessTypeInfo(onboarding.businessType);
  switch (key) {
    case 'SERVICES':
      return onboarding.businessType === 'RESTAURANT'
        ? {
            title: 'Check your table reservation',
            description:
              'Customers book a table for free. Change how long a table is held, or add services you sell, such as a set menu.',
            to: '/admin/services',
            confirmable: true,
          }
        : onboarding.businessType === 'OTHER' || !onboarding.businessType
          ? {
              title: 'Add your services',
              description: 'What customers can book, how long it takes and what it costs.',
              to: '/admin/services',
              confirmable: true,
            }
          : {
              title: 'Check your services and prices',
              description: 'We added examples to start from. Rename them, change prices and durations, or add your own.',
              to: '/admin/services',
              confirmable: true,
            };
    case 'RESOURCES':
      return {
        title: info.resourcesStep.title,
        description:
          info.resourcesStep.to === '/admin/employees'
            ? 'You are on the team already. Add the people who work with you, and the services each one does.'
            : 'We added a few to start from. Rename them, change seats, or add and remove them.',
        to: info.resourcesStep.to,
        confirmable: true,
      };
    case 'HOURS':
      return {
        title: 'Check your opening hours',
        description: 'Customers can only book while you are open.',
        to: '/admin/settings?tab=hours',
        confirmable: true,
      };
    case 'CONTACT':
      return {
        title: 'Add your address and phone',
        description: 'Customers see them on your booking page, with a map of the address.',
        to: '/admin/settings?tab=business',
        confirmable: false,
      };
    case 'PHOTO':
      return {
        title: 'Add a logo or cover photo',
        description: 'It makes your booking page look like yours.',
        to: '/admin/settings?tab=booking-page',
        confirmable: false,
      };
    case 'SHARE':
      return {
        title: 'Share your booking link',
        description: 'Put it in your Instagram bio, on Google and on your website.',
        confirmable: false,
      };
  }
}

/**
 * The "Getting started" checklist at the top of a new business's dashboard.
 * Steps tick when the owner saves that part of the setup, says it looks good,
 * or fills in the details; the card goes away when all are done or hidden.
 */
export function GettingStartedCard() {
  const onboarding = useOnboarding();
  const markStep = useMarkOnboardingStep();
  const dismiss = useDismissOnboarding();
  const toast = useToast();

  const data = onboarding.data;
  if (!data || data.dismissed) {
    return null;
  }
  const doneCount = data.steps.filter((step) => step.done).length;
  if (doneCount === data.steps.length) {
    return null;
  }
  const pageUrl = `${window.location.origin}/b/${data.slug}`;

  const mark = (key: OnboardingStepKey) =>
    markStep.mutate(key, { onError: (error) => toast.error(errorMessage(error, 'Could not save that.')) });

  const copyLink = () =>
    navigator.clipboard
      ?.writeText(pageUrl)
      .then(() => {
        toast.success('Link copied');
        mark('SHARE');
      })
      .catch(() => toast.error('Copy the link manually'));

  return (
    <Paper variant="outlined" sx={{ p: { xs: 2, sm: 3 }, mb: 3 }} aria-label="Getting started">
      <Stack direction="row" spacing={1.5} alignItems="flex-start" sx={{ mb: 1.5 }}>
        <RocketLaunchRoundedIcon color="primary" sx={{ mt: 0.25 }} />
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography variant="subtitle1" fontWeight={700}>
            Getting started
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {data.businessType && data.businessType !== 'OTHER'
              ? 'Your booking page already takes bookings. Make it yours'
              : 'A few steps before customers can book'}
            : {doneCount} of {data.steps.length} done.
          </Typography>
        </Box>
        <Button size="small" color="inherit" onClick={() => dismiss.mutate(undefined)} disabled={dismiss.isPending}>
          Hide
        </Button>
      </Stack>
      <LinearProgress
        variant="determinate"
        value={(doneCount / data.steps.length) * 100}
        sx={{ mb: 2, height: 6, borderRadius: 3 }}
        aria-label={`${doneCount} of ${data.steps.length} done`}
      />

      <Stack spacing={1.5}>
        {data.steps.map((step) => {
          const copy = copyOf(step.key, data);
          return (
            <Stack
              key={step.key}
              direction={{ xs: 'column', sm: 'row' }}
              spacing={{ xs: 1, sm: 2 }}
              alignItems={{ xs: 'stretch', sm: 'center' }}
              sx={{ opacity: step.done ? 0.6 : 1 }}
              data-testid={`step-${step.key}`}
            >
              <Stack direction="row" spacing={1.5} alignItems="flex-start" sx={{ flex: 1, minWidth: 0 }}>
                {step.done ? (
                  <CheckCircleRoundedIcon color="success" fontSize="small" sx={{ mt: 0.25 }} aria-label="Done" />
                ) : (
                  <RadioButtonUncheckedRoundedIcon
                    color="disabled"
                    fontSize="small"
                    sx={{ mt: 0.25 }}
                    aria-label="To do"
                  />
                )}
                <Box sx={{ minWidth: 0 }}>
                  <Typography variant="body2" fontWeight={600}>
                    {copy.title}
                  </Typography>
                  {!step.done ? (
                    <Typography variant="body2" color="text.secondary">
                      {copy.description}
                    </Typography>
                  ) : null}
                </Box>
              </Stack>

              {!step.done ? (
                step.key === 'SHARE' ? (
                  <Stack direction="row" spacing={1} sx={{ pl: { xs: 3.5, sm: 0 } }}>
                    <TextField
                      size="small"
                      value={pageUrl}
                      InputProps={{ readOnly: true }}
                      onFocus={(event) => event.target.select()}
                      sx={{ minWidth: 0, width: { xs: '100%', sm: 240 } }}
                      inputProps={{ 'aria-label': 'Your booking link' }}
                    />
                    <Button variant="contained" size="small" onClick={copyLink}>
                      Copy
                    </Button>
                  </Stack>
                ) : (
                  <Stack direction="row" spacing={1} sx={{ pl: { xs: 3.5, sm: 0 }, flexShrink: 0 }}>
                    {copy.confirmable ? (
                      <Button size="small" onClick={() => mark(step.key)} disabled={markStep.isPending}>
                        Looks good
                      </Button>
                    ) : null}
                    {copy.to ? (
                      <Button size="small" variant="outlined" component={RouterLink} to={copy.to}>
                        Open
                      </Button>
                    ) : null}
                  </Stack>
                )
              ) : null}
            </Stack>
          );
        })}
      </Stack>
    </Paper>
  );
}
