import { useState } from 'react';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { ChartCard, ColumnChart, RankedBars, TrendChart } from '@/components/charts';
import { DateRangeFilter, presetRange, type RangePreset } from '@/components/DateRangeFilter';
import { KpiCard } from '@/components/KpiCard';
import { PageHeader } from '@/components/PageHeader';
import { LoadingState } from '@/components/states';
import {
  useCustomerStats,
  useReservationStats,
  useResourceStats,
  useRevenue,
  useServiceStats,
  type DateRange,
} from '@/features/analytics/api';
import { useBusiness } from '@/features/business/api';
import type { Granularity } from '@/types/api';
import {
  formatLocalDate,
  formatMoney,
  formatMoneyCompact,
  formatNumber,
  formatPercent,
  formatShortDate,
  pluralize,
} from '@/utils/format';

/** Everything computed from the database for the chosen range: revenue, volume, services, resources, customers. */
export function AnalyticsPage() {
  const [preset, setPreset] = useState<RangePreset>('30d');
  const [range, setRange] = useState<DateRange>(presetRange('30d'));
  const { data: business } = useBusiness();
  const currency = business?.currency ?? 'EUR';
  // Long ranges read better by week.
  const days = Math.round((new Date(range.to).getTime() - new Date(range.from).getTime()) / 86_400_000) + 1;
  const granularity: Granularity = days > 120 ? 'MONTH' : days > 45 ? 'WEEK' : 'DAY';

  const revenue = useRevenue(range, granularity);
  const reservations = useReservationStats(range);
  const services = useServiceStats(range);
  const resources = useResourceStats(range);
  const customers = useCustomerStats(range);

  return (
    <>
      <PageHeader title="Analytics" description="All figures are calculated live from your reservations." />
      <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
        <DateRangeFilter preset={preset} range={range} onChange={(nextPreset, nextRange) => { setPreset(nextPreset); setRange(nextRange); }} />
      </Paper>

      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid item xs={6} md={3}>
          <KpiCard label="Net revenue" value={revenue.data ? formatMoney(revenue.data.summary.netRevenue, currency) : '—'} loading={revenue.isLoading} />
        </Grid>
        <Grid item xs={6} md={3}>
          <KpiCard
            label="Reservations"
            value={reservations.data ? formatNumber(reservations.data.total) : '—'}
            note={reservations.data ? `${reservations.data.cancelled} cancelled` : undefined}
            loading={reservations.isLoading}
          />
        </Grid>
        <Grid item xs={6} md={3}>
          <KpiCard label="New customers" value={customers.data ? formatNumber(customers.data.newCustomers) : '—'} note="First reservation in this range" loading={customers.isLoading} />
        </Grid>
        <Grid item xs={6} md={3}>
          <KpiCard label="Returning customers" value={customers.data ? formatNumber(customers.data.returningCustomers) : '—'} note="Had reserved before" loading={customers.isLoading} />
        </Grid>
      </Grid>

      <Grid container spacing={2}>
        <Grid item xs={12} lg={6}>
          <ChartCard title="Revenue" subtitle={`Net, by ${granularity.toLowerCase()}`}>
            {revenue.data ? (
              <TrendChart
                data={revenue.data.series.map((point) => ({
                  label: granularity === 'MONTH' ? point.periodStart.slice(0, 7) : formatShortDate(point.periodStart),
                  detail: formatLocalDate(point.periodStart),
                  value: point.netRevenue,
                  extra: pluralize(point.reservations, 'reservation'),
                }))}
                valueLabel="Net revenue"
                formatValue={(value) => formatMoney(value, currency)}
                formatAxis={(value) => formatMoneyCompact(value, currency)}
              />
            ) : (
              <LoadingState />
            )}
          </ChartCard>
        </Grid>
        <Grid item xs={12} lg={6}>
          <ChartCard title="Reservations by day" subtitle="Excluding cancellations">
            {reservations.data ? (
              <ColumnChart
                data={reservations.data.series.map((point) => ({
                  label: formatShortDate(point.date),
                  detail: formatLocalDate(point.date),
                  value: point.total,
                  extra: point.participants ? pluralize(point.participants, 'guest') : undefined,
                }))}
                valueLabel="Reservations"
              />
            ) : (
              <LoadingState />
            )}
          </ChartCard>
        </Grid>

        <Grid item xs={12} md={6}>
          <ChartCard title="Most booked services" subtitle="Reservations, with revenue per service">
            {services.data ? (
              services.data.length ? (
                <RankedBars
                  items={services.data.map((service) => ({
                    key: service.serviceId,
                    label: service.serviceName,
                    sublabel: `${formatMoney(service.revenue, currency)} revenue · ${pluralize(service.participants, 'guest')}`,
                    value: service.reservations,
                    display: formatNumber(service.reservations),
                  }))}
                />
              ) : (
                <Typography variant="body2" color="text.secondary">No reservations in this range.</Typography>
              )
            ) : (
              <LoadingState />
            )}
          </ChartCard>
        </Grid>
        <Grid item xs={12} md={6}>
          <ChartCard title="Revenue per service">
            {services.data ? (
              <RankedBars
                items={[...services.data]
                  .sort((a, b) => b.revenue - a.revenue)
                  .map((service) => ({
                    key: service.serviceId,
                    label: service.serviceName,
                    value: service.revenue,
                    display: formatMoney(service.revenue, currency),
                  }))}
              />
            ) : (
              <LoadingState />
            )}
          </ChartCard>
        </Grid>

        <Grid item xs={12} lg={7}>
          <ChartCard title="Resource utilization" subtitle="Booked time as a share of each resource's available hours">
            {resources.data ? (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Resource</TableCell>
                    <TableCell align="right">Reservations</TableCell>
                    <TableCell align="right">Booked</TableCell>
                    <TableCell sx={{ width: '40%' }}>Utilization</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {resources.data.map((resource) => (
                    <TableRow key={resource.resourceId}>
                      <TableCell>
                        <Typography variant="body2" fontWeight={600}>{resource.resourceName}</Typography>
                        <Typography variant="caption" color="text.secondary">{resource.resourceTypeName}</Typography>
                      </TableCell>
                      <TableCell align="right">{resource.reservations}</TableCell>
                      <TableCell align="right">{Math.round(resource.bookedMinutes / 60)} h</TableCell>
                      <TableCell>
                        <RankedBars
                          max={1}
                          items={[{ key: resource.resourceId, label: '', value: resource.utilization, display: formatPercent(resource.utilization) }]}
                        />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            ) : (
              <LoadingState />
            )}
          </ChartCard>
        </Grid>
        <Grid item xs={12} lg={5}>
          <ChartCard title="Top customers" subtitle="By amount spent in this range">
            {customers.data ? (
              customers.data.topCustomers.length ? (
                <Stack spacing={1.25}>
                  {customers.data.topCustomers.map((customer, index) => (
                    <Stack key={customer.customerId} direction="row" justifyContent="space-between" alignItems="center">
                      <Typography variant="body2">
                        <Typography component="span" color="text.secondary" sx={{ mr: 1 }}>{index + 1}.</Typography>
                        {customer.name}
                      </Typography>
                      <Typography variant="body2" fontWeight={700}>
                        {formatMoney(customer.spent, currency)}
                        <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 1 }}>
                          {pluralize(customer.reservations, 'visit')}
                        </Typography>
                      </Typography>
                    </Stack>
                  ))}
                </Stack>
              ) : (
                <Typography variant="body2" color="text.secondary">No paying customers in this range.</Typography>
              )
            ) : (
              <LoadingState />
            )}
          </ChartCard>
        </Grid>
      </Grid>
    </>
  );
}
