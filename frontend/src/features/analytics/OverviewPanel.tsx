import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { ErrorState, LoadingState } from '@/components/states';
import { useAnalyticsOverview } from '@/features/analytics/useAnalytics';
import { formatMoney, formatPercent } from '@/utils/format';
import type { AnalyticsRange } from '@/features/analytics/useAnalytics';

export function OverviewPanel({ range }: { range: AnalyticsRange }) {
  const { data, isLoading, isError, error, refetch } = useAnalyticsOverview(range);

  if (isLoading) {
    return <LoadingState />;
  }
  if (isError) {
    return <ErrorState error={error} onRetry={() => void refetch()} />;
  }
  if (!data) {
    return null;
  }

  const revenueCountedAppointments = data.appointmentCount - data.cancelledCount;

  return (
    <>
      <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1.5 }}>
        Revenue
      </Typography>
      <Grid container spacing={2.5} sx={{ mb: 3 }}>
        <StatCard label="Today" value={formatMoney(data.revenueToday, data.currency)} />
        <StatCard label="This week" value={formatMoney(data.revenueThisWeek, data.currency)} />
        <StatCard label="This month" value={formatMoney(data.revenueThisMonth, data.currency)} />
        <StatCard label="This year" value={formatMoney(data.revenueThisYear, data.currency)} />
      </Grid>

      <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1.5 }}>
        In the selected period
      </Typography>
      <Grid container spacing={2.5} sx={{ mb: 3 }}>
        <StatCard label="Appointments" value={data.appointmentCount} />
        <StatCard
          label="Average booking value"
          value={formatMoney(data.averageBookingValue, data.currency)}
          helper={`over ${revenueCountedAppointments} revenue-generating bookings`}
        />
        <StatCard label="Cancellation rate" value={formatPercent(data.cancellationRate)} />
        <StatCard label="No-show rate" value={formatPercent(data.noShowRate)} />
      </Grid>

      <Card>
        <CardContent>
          <Typography variant="h6" sx={{ mb: 1.5 }}>
            Completed vs cancelled
          </Typography>
          <Divider sx={{ mb: 2 }} />
          <Stack direction="row" spacing={1.5} flexWrap="wrap">
            <Chip color="success" label={`${data.completedCount} completed`} />
            <Chip color="error" label={`${data.cancelledCount} cancelled`} />
            <Chip color="default" label={`${data.noShowCount} no-show`} />
          </Stack>
        </CardContent>
      </Card>
    </>
  );
}

function StatCard({ label, value, helper }: { label: string; value: string | number; helper?: string }) {
  return (
    <Grid item xs={6} md={3}>
      <Card sx={{ height: '100%' }}>
        <CardContent>
          <Typography variant="body2" color="text.secondary" gutterBottom>
            {label}
          </Typography>
          <Typography variant="h5">{value}</Typography>
          {helper ? (
            <Typography variant="caption" color="text.secondary">
              {helper}
            </Typography>
          ) : null}
        </CardContent>
      </Card>
    </Grid>
  );
}
