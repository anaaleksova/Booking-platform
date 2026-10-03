import { useMemo } from 'react';
import { Link as RouterLink, Navigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import EventAvailableRoundedIcon from '@mui/icons-material/EventAvailableRounded';
import PaymentsRoundedIcon from '@mui/icons-material/PaymentsRounded';
import PeopleAltRoundedIcon from '@mui/icons-material/PeopleAltRounded';
import TodayRoundedIcon from '@mui/icons-material/TodayRounded';
import { PageHeader } from '@/components/PageHeader';
import { KpiCard } from '@/components/KpiCard';
import { ChartCard, ColumnChart, RankedBars, TrendChart } from '@/components/charts';
import { EmptyState, ErrorState, LoadingState } from '@/components/states';
import { StatusChip } from '@/components/StatusChip';
import { presetRange } from '@/components/DateRangeFilter';
import { useAuth } from '@/features/auth/AuthContext';
import {
  useDashboard,
  useReservationStats,
  useResourceStats,
  useRevenue,
  useServiceStats,
} from '@/features/analytics/api';
import {
  formatLocalDate,
  formatMoney,
  formatMoneyCompact,
  formatNumber,
  formatPercent,
  formatShortDate,
  formatWindow,
  pluralize,
} from '@/utils/format';

/** The admin landing page: today at a glance, the last 30 days in charts, what is coming next. */
export function DashboardPage() {
  const { isOwner } = useAuth();
  const last30 = useMemo(() => presetRange('30d'), []);
  const month = useMemo(() => presetRange('month'), []);

  const dashboard = useDashboard();
  const revenue = useRevenue(last30, 'DAY');
  const reservations = useReservationStats(last30);
  const services = useServiceStats(month);
  const resources = useResourceStats(last30);

  if (!isOwner) {
    // Staff have no access to business figures; their landing page is their schedule.
    return <Navigate to="/admin/reservations" replace />;
  }
  if (dashboard.isError) {
    return <ErrorState error={dashboard.error} onRetry={() => dashboard.refetch()} />;
  }

  const currency = dashboard.data?.currency ?? 'EUR';
  const data = dashboard.data;

  return (
    <>
      <PageHeader
        title="Dashboard"
        description="Revenue counts confirmed and completed reservations, after discounts."
        action={
          <Button component={RouterLink} to="/admin/reservations" variant="contained">
            View reservations
          </Button>
        }
      />

      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid item xs={12} sm={6} lg={3}>
          <KpiCard
            label="Today's revenue"
            value={data ? formatMoney(data.todayRevenue, currency) : '—'}
            icon={<PaymentsRoundedIcon />}
            loading={dashboard.isLoading}
          />
        </Grid>
        <Grid item xs={12} sm={6} lg={3}>
          <KpiCard
            label="Today's reservations"
            value={data ? formatNumber(data.todayReservations) : '—'}
            note="Excluding cancellations"
            icon={<TodayRoundedIcon />}
            loading={dashboard.isLoading}
          />
        </Grid>
        <Grid item xs={12} sm={6} lg={3}>
          <KpiCard
            label="This month's revenue"
            value={data ? formatMoney(data.monthRevenue, currency) : '—'}
            note={data ? pluralize(data.monthReservations, 'reservation') : undefined}
            icon={<EventAvailableRoundedIcon />}
            loading={dashboard.isLoading}
          />
        </Grid>
        <Grid item xs={12} sm={6} lg={3}>
          <KpiCard
            label="Total customers"
            value={data ? formatNumber(data.totalCustomers) : '—'}
            icon={<PeopleAltRoundedIcon />}
            loading={dashboard.isLoading}
          />
        </Grid>
      </Grid>

      <Grid container spacing={2}>
        <Grid item xs={12} lg={7}>
          <ChartCard title="Revenue" subtitle="Last 30 days, net of discounts">
            {revenue.data ? (
              <TrendChart
                data={revenue.data.series.map((point) => ({
                  label: formatShortDate(point.periodStart),
                  detail: formatLocalDate(point.periodStart),
                  value: point.netRevenue,
                  extra: pluralize(point.reservations, 'reservation'),
                }))}
                valueLabel="Revenue"
                formatValue={(value) => formatMoney(value, currency)}
                formatAxis={(value) => formatMoneyCompact(value, currency)}
              />
            ) : (
              <LoadingState />
            )}
          </ChartCard>
        </Grid>
        <Grid item xs={12} lg={5}>
          <ChartCard title="Reservations" subtitle="Per day, last 30 days, excluding cancellations">
            {reservations.data ? (
              <ColumnChart
                data={reservations.data.series.map((point) => ({
                  label: formatShortDate(point.date),
                  detail: formatLocalDate(point.date),
                  value: point.total,
                  extra: point.cancelled ? `${point.cancelled} cancelled` : undefined,
                }))}
                valueLabel="Reservations"
              />
            ) : (
              <LoadingState />
            )}
          </ChartCard>
        </Grid>

        <Grid item xs={12} md={6} lg={4}>
          <ChartCard title="Popular services" subtitle="Reservations this month">
            {services.data?.length ? (
              <RankedBars
                items={services.data.slice(0, 6).map((service) => ({
                  key: service.serviceId,
                  label: service.serviceName,
                  sublabel: formatMoney(service.revenue, currency),
                  value: service.reservations,
                  display: formatNumber(service.reservations),
                }))}
              />
            ) : services.isLoading ? (
              <LoadingState />
            ) : (
              <Typography variant="body2" color="text.secondary">
                No reservations this month yet.
              </Typography>
            )}
          </ChartCard>
        </Grid>
        <Grid item xs={12} md={6} lg={4}>
          <ChartCard title="Resource utilization" subtitle="Booked share of available time, last 30 days">
            {resources.data?.length ? (
              <RankedBars
                max={1}
                items={resources.data.slice(0, 6).map((resource) => ({
                  key: resource.resourceId,
                  label: resource.resourceName,
                  sublabel: resource.resourceTypeName ?? undefined,
                  value: resource.utilization,
                  display: formatPercent(resource.utilization),
                }))}
              />
            ) : resources.isLoading ? (
              <LoadingState />
            ) : (
              <Typography variant="body2" color="text.secondary">
                Add resources to see how busy they are.
              </Typography>
            )}
          </ChartCard>
        </Grid>
        <Grid item xs={12} lg={4}>
          <ChartCard
            title="Upcoming reservations"
            action={
              <Button component={RouterLink} to="/admin/reservations" size="small">
                All
              </Button>
            }
          >
            {data && data.upcoming.length === 0 ? (
              <EmptyState title="Nothing coming up" description="New reservations will appear here." />
            ) : (
              <Stack divider={<Divider flexItem />} spacing={1.25}>
                {data?.upcoming.map((reservation) => (
                  <Stack key={reservation.id} direction="row" spacing={1.5} alignItems="center">
                    <Box sx={{ width: 64, flexShrink: 0 }}>
                      <Typography variant="caption" color="text.secondary" display="block">
                        {formatShortDate(reservation.localStartTime)}
                      </Typography>
                      <Typography variant="body2" fontWeight={700}>
                        {formatWindow(reservation).split(' – ')[0]}
                      </Typography>
                    </Box>
                    <Box sx={{ flex: 1, minWidth: 0 }}>
                      <Typography variant="body2" fontWeight={600} noWrap>
                        {reservation.serviceName}
                      </Typography>
                      <Typography variant="caption" color="text.secondary" noWrap display="block">
                        {reservation.customerName} · {pluralize(reservation.numberOfParticipants, 'guest')}
                      </Typography>
                    </Box>
                    <StatusChip status={reservation.status} />
                  </Stack>
                ))}
              </Stack>
            )}
          </ChartCard>
        </Grid>
      </Grid>
    </>
  );
}
