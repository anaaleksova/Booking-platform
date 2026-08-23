import { useState, type SyntheticEvent } from 'react';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Stack from '@mui/material/Stack';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import dayjs, { type Dayjs } from 'dayjs';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { PageHeader } from '@/components/PageHeader';
import { CustomerAnalyticsTable } from '@/features/analytics/CustomerAnalyticsTable';
import { OverviewPanel } from '@/features/analytics/OverviewPanel';
import { RevenueByServiceChart } from '@/features/analytics/RevenueByServiceChart';
import { useAnalyticsOverview } from '@/features/analytics/useAnalytics';

/**
 * Reports: how the business is performing.
 *
 * <p>Today/week/month/year revenue is always shown regardless of the date
 * range below — those four figures answer "how are we doing right now."
 * The range only scopes the appointment-volume stats, the service ranking
 * and the customer table, for looking further back.
 */
export function ReportsPage() {
  const [tab, setTab] = useState(0);
  const [from, setFrom] = useState<Dayjs | null>(dayjs().startOf('month'));
  const [to, setTo] = useState<Dayjs | null>(dayjs());

  const range = {
    from: from ? from.format('YYYY-MM-DD') : undefined,
    to: to ? to.format('YYYY-MM-DD') : undefined,
  };

  // The overview call is cheap and cached; reused here just for the currency label.
  const { data: overview } = useAnalyticsOverview(range);

  return (
    <>
      <PageHeader title="Reports" description="How the business is performing." />

      <Card sx={{ mb: 2.5 }}>
        <CardContent>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
            <DatePicker
              label="From"
              value={from}
              onChange={(value) => setFrom(value)}
              slotProps={{ textField: { size: 'small' } }}
            />
            <DatePicker
              label="To"
              value={to}
              onChange={(value) => setTo(value)}
              slotProps={{ textField: { size: 'small' } }}
            />
          </Stack>
        </CardContent>
      </Card>

      <Tabs
        value={tab}
        onChange={(_: SyntheticEvent, value: number) => setTab(value)}
        sx={{ mb: 2.5, borderBottom: 1, borderColor: 'divider' }}
      >
        <Tab label="Overview" />
        <Tab label="Revenue by service" />
        <Tab label="Customers" />
      </Tabs>

      {tab === 0 ? <OverviewPanel range={range} /> : null}
      {tab === 1 ? <RevenueByServiceChart range={range} /> : null}
      {tab === 2 ? <CustomerAnalyticsTable range={range} currency={overview?.currency} /> : null}
    </>
  );
}
