import Chip from '@mui/material/Chip';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import type { IntegrationEventDelivery } from '@/types/api';
import { DELIVERY_COLORS } from './integrationLabels';

export function DeliveriesCard({ events }: { events: IntegrationEventDelivery[] }) {
  return (
    <Paper variant="outlined" sx={{ p: 2.5 }}>
      <Typography variant="subtitle1" fontWeight={700}>
        Recent events
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        What the platform sent to n8n. Failed deliveries are retried with increasing delays.
      </Typography>
      {events.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          Nothing sent yet. Events appear once n8n is connected and a reservation changes.
        </Typography>
      ) : (
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Event</TableCell>
                <TableCell>Reservation</TableCell>
                <TableCell>Status</TableCell>
                <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>Detail</TableCell>
                <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>When</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {events.map((event) => (
                <TableRow key={event.id}>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: 13 }}>{event.eventType}</TableCell>
                  <TableCell>{event.reservationId ? `#${event.reservationId}` : '—'}</TableCell>
                  <TableCell>
                    <Chip size="small" variant="outlined" color={DELIVERY_COLORS[event.status]} label={event.status.toLowerCase()} />
                  </TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>
                    <Typography variant="caption" color="text.secondary">
                      {event.status === 'PENDING' && event.attempts === 0
                        ? `Due ${new Date(event.dueAt).toLocaleString()}`
                        : event.lastError ?? (event.responseStatus ? `HTTP ${event.responseStatus}` : '')}
                      {event.attempts > 1 ? ` · ${event.attempts} attempts` : ''}
                    </Typography>
                  </TableCell>
                  <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>
                    {new Date(event.deliveredAt ?? event.createdAt).toLocaleString()}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Paper>
  );
}
