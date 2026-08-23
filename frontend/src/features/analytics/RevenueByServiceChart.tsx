import Card from '@mui/material/Card';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Box from '@mui/material/Box';
import { useTheme } from '@mui/material/styles';
import BarChartRoundedIcon from '@mui/icons-material/BarChartRounded';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { EmptyState, ErrorState, LoadingState } from '@/components/states';
import { useRevenueByService } from '@/features/analytics/useAnalytics';
import { formatMoney } from '@/utils/format';
import type { AnalyticsRange } from '@/features/analytics/useAnalytics';
import type { ServiceRevenue } from '@/types/api';

export function RevenueByServiceChart({ range }: { range: AnalyticsRange }) {
  const theme = useTheme();
  const { data, isLoading, isError, error, refetch } = useRevenueByService(range);

  if (isLoading) {
    return <LoadingState />;
  }
  if (isError) {
    return <ErrorState error={error} onRetry={() => void refetch()} />;
  }
  if (!data || data.length === 0) {
    return (
      <EmptyState
        title="No revenue yet"
        description="Once bookings come in, this shows which services make the most money."
        icon={<BarChartRoundedIcon />}
      />
    );
  }

  const currency = data[0]?.currency ?? undefined;
  const chartData = [...data].sort((a, b) => a.revenue - b.revenue);
  const chartHeight = Math.max(240, chartData.length * 48);

  return (
    <>
      <Card sx={{ mb: 2.5 }}>
        <Box sx={{ p: 2, height: chartHeight }}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={chartData} layout="vertical" margin={{ left: 8, right: 24 }}>
              <CartesianGrid strokeDasharray="3 3" horizontal={false} />
              <XAxis type="number" tickFormatter={(value: number) => formatMoney(value, currency)} />
              <YAxis type="category" dataKey="serviceName" width={140} tick={{ fontSize: 12 }} />
              <Tooltip formatter={(value: number) => formatMoney(value, currency)} />
              <Bar dataKey="revenue" fill={theme.palette.primary.main} radius={[0, 4, 4, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </Box>
      </Card>

      <Card>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Service</TableCell>
                <TableCell align="right">Appointments</TableCell>
                <TableCell align="right">Revenue</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {[...data]
                .sort((a, b) => b.revenue - a.revenue)
                .map((row: ServiceRevenue) => (
                  <TableRow key={row.serviceId} sx={{ opacity: row.active ? 1 : 0.6 }}>
                    <TableCell sx={{ fontWeight: 600 }}>
                      {row.serviceName}
                      {!row.active ? ' (inactive)' : ''}
                    </TableCell>
                    <TableCell align="right">{row.appointmentCount}</TableCell>
                    <TableCell align="right">{formatMoney(row.revenue, currency)}</TableCell>
                  </TableRow>
                ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>
    </>
  );
}
