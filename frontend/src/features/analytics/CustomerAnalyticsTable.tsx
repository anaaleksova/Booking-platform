import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Card from '@mui/material/Card';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import PeopleAltRoundedIcon from '@mui/icons-material/PeopleAltRounded';
import { EmptyState, ErrorState, TableLoadingRows } from '@/components/states';
import { useCustomerAnalytics } from '@/features/analytics/useAnalytics';
import { formatDaysAgo, formatMoney } from '@/utils/format';
import type { AnalyticsRange } from '@/features/analytics/useAnalytics';

export function CustomerAnalyticsTable({
  range,
  currency,
}: {
  range: AnalyticsRange;
  currency?: string | null;
}) {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const { data, isLoading, isError, error, refetch } = useCustomerAnalytics(range, page);

  return (
    <Card>
      {isLoading ? <TableLoadingRows /> : null}
      {isError ? <ErrorState error={error} onRetry={() => void refetch()} /> : null}
      {!isLoading && !isError && (data?.content.length ?? 0) === 0 ? (
        <EmptyState
          title="No customer activity yet"
          description="Once appointments come in, customers appear here ranked by what they've spent."
          icon={<PeopleAltRoundedIcon />}
        />
      ) : null}

      {!isLoading && !isError && data && data.content.length > 0 ? (
        <>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Customer</TableCell>
                  <TableCell align="right">Total spent</TableCell>
                  <TableCell align="right">Appointments</TableCell>
                  <TableCell align="right">Avg. value</TableCell>
                  <TableCell>Last appointment</TableCell>
                  <TableCell>Favourite service</TableCell>
                  <TableCell align="right">Cancellations</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data.content.map((row) => (
                  <TableRow
                    key={row.customerId}
                    hover
                    sx={{ cursor: 'pointer' }}
                    onClick={() => navigate(`/dashboard/customers/${row.customerId}`)}
                  >
                    <TableCell sx={{ fontWeight: 600 }}>{row.customerName ?? '—'}</TableCell>
                    <TableCell align="right">{formatMoney(row.totalSpent, currency)}</TableCell>
                    <TableCell align="right">{row.appointmentCount}</TableCell>
                    <TableCell align="right">{formatMoney(row.averageAppointmentValue, currency)}</TableCell>
                    <TableCell>{formatDaysAgo(row.lastAppointmentAt)}</TableCell>
                    <TableCell>{row.favouriteServiceName ?? '—'}</TableCell>
                    <TableCell align="right">{row.cancellationCount}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination
            component="div"
            count={data.totalElements}
            page={page}
            onPageChange={(_, value) => setPage(value)}
            rowsPerPage={data.size}
            rowsPerPageOptions={[data.size]}
          />
        </>
      ) : null}
    </Card>
  );
}
