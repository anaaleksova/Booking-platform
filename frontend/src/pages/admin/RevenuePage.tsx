import { useEffect, useState } from 'react';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import { ChartCard, TrendChart } from '@/components/charts';
import { DateRangeFilter, presetRange, type RangePreset } from '@/components/DateRangeFilter';
import { KpiCard } from '@/components/KpiCard';
import { PageHeader } from '@/components/PageHeader';
import { ErrorState, LoadingState } from '@/components/states';
import { useRevenue, useRevenuePeriods, type DateRange } from '@/features/analytics/api';
import type { Granularity, RevenueSummary } from '@/types/api';
import { formatLocalDate, formatMoney, formatMoneyCompact, formatShortDate, pluralize } from '@/utils/format';

/**
 * Revenue, defined once and shown everywhere the same way:
 * the final price of CONFIRMED and COMPLETED reservations, by the day they
 * take place. Cancelled and pending reservations never count.
 */
export function RevenuePage() {
  const [preset, setPreset] = useState<RangePreset>('30d');
  const [range, setRange] = useState<DateRange>(presetRange('30d'));
  const [granularity, setGranularity] = useState<Granularity>('DAY');
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const periods = useRevenuePeriods();
  const report = useRevenue(range, granularity);
  const currency = report.data?.summary.currency ?? periods.data?.today.currency ?? 'EUR';

  const periodCard = (label: string, summary?: RevenueSummary) => (
    <KpiCard
      label={label}
      value={summary ? formatMoney(summary.netRevenue, summary.currency) : '—'}
      note={summary ? `${pluralize(summary.reservations, 'reservation')} · ${formatMoney(summary.discounts, summary.currency)} discounts` : undefined}
      loading={periods.isLoading}
    />
  );

  const summary = report.data?.summary;
  // Periods with no reservations add nothing to the table; the chart still shows them as zero.
  const rows = (report.data?.series ?? []).filter((point) => point.reservations > 0);

  // A new range or grouping starts the table from its first page.
  useEffect(() => {
    setPage(0);
  }, [range, granularity]);

  return (
    <>
      <PageHeader
        title="Revenue"
        description="Counts confirmed and completed reservations, by the day they take place. Cancelled and pending reservations are excluded."
      />
      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} md={4}>
          {periodCard('Today', periods.data?.today)}
        </Grid>
        <Grid item xs={12} md={4}>
          {periodCard('This week', periods.data?.thisWeek)}
        </Grid>
        <Grid item xs={12} md={4}>
          {periodCard('This month', periods.data?.thisMonth)}
        </Grid>
      </Grid>

      <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ md: 'center' }}>
          <DateRangeFilter preset={preset} range={range} onChange={(nextPreset, nextRange) => { setPreset(nextPreset); setRange(nextRange); }} />
          <ToggleButtonGroup size="small" exclusive value={granularity} onChange={(_, value) => value && setGranularity(value)}>
            <ToggleButton value="DAY">Daily</ToggleButton>
            <ToggleButton value="WEEK">Weekly</ToggleButton>
            <ToggleButton value="MONTH">Monthly</ToggleButton>
          </ToggleButtonGroup>
        </Stack>
      </Paper>

      {report.isError ? <ErrorState error={report.error} onRetry={() => report.refetch()} /> : null}

      <Grid container spacing={2} sx={{ mb: 2 }}>
        {[
          ['Gross revenue', summary ? formatMoney(summary.grossRevenue, currency) : '—', 'Before discounts'],
          ['Discounts', summary ? formatMoney(summary.discounts, currency) : '—', undefined],
          ['Net revenue', summary ? formatMoney(summary.netRevenue, currency) : '—', 'What was charged'],
          ['Reservations', summary ? String(summary.reservations) : '—', undefined],
          ['Average reservation', summary ? formatMoney(summary.averageReservationValue, currency) : '—', 'Net per reservation'],
        ].map(([label, value, note]) => (
          <Grid item xs={6} md key={label}>
            <KpiCard label={label!} value={value!} note={note} loading={report.isLoading} />
          </Grid>
        ))}
      </Grid>

      <ChartCard
        title="Net revenue"
        subtitle={`${formatLocalDate(range.from)} – ${formatLocalDate(range.to)}, ${granularity.toLowerCase()}`}
      >
        {report.data ? (
          <TrendChart
            height={300}
            data={report.data.series.map((point) => ({
              label: granularity === 'MONTH' ? point.periodStart.slice(0, 7) : formatShortDate(point.periodStart),
              detail: granularity === 'DAY' ? formatLocalDate(point.periodStart) : `${granularity === 'WEEK' ? 'Week of' : 'Month of'} ${formatLocalDate(point.periodStart)}`,
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

      {report.data ? (
        <TableContainer component={Paper} variant="outlined" sx={{ mt: 2 }}>
          <Typography variant="subtitle2" sx={{ px: 2, pt: 2 }}>
            Table view
          </Typography>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Period</TableCell>
                <TableCell align="right">Reservations</TableCell>
                <TableCell align="right">Gross</TableCell>
                <TableCell align="right">Discounts</TableCell>
                <TableCell align="right">Net</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows
                .slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage)
                .map((point) => (
                  <TableRow key={point.periodStart}>
                    <TableCell>{formatLocalDate(point.periodStart)}</TableCell>
                    <TableCell align="right">{point.reservations}</TableCell>
                    <TableCell align="right">{formatMoney(point.grossRevenue, currency)}</TableCell>
                    <TableCell align="right">{formatMoney(point.discounts, currency)}</TableCell>
                    <TableCell align="right">{formatMoney(point.netRevenue, currency)}</TableCell>
                  </TableRow>
                ))}
            </TableBody>
          </Table>
          <TablePagination
            component="div"
            count={rows.length}
            page={Math.min(page, Math.max(0, Math.ceil(rows.length / rowsPerPage) - 1))}
            onPageChange={(_, next) => setPage(next)}
            rowsPerPage={rowsPerPage}
            rowsPerPageOptions={[10, 25, 50]}
            onRowsPerPageChange={(event) => {
              setRowsPerPage(Number(event.target.value));
              setPage(0);
            }}
          />
        </TableContainer>
      ) : null}
    </>
  );
}
