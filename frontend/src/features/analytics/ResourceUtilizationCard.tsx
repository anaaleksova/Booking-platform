import { useEffect, useMemo, useState } from 'react';
import MenuItem from '@mui/material/MenuItem';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { ChartCard, RankedBars } from '@/components/charts';
import { LoadingState } from '@/components/states';
import type { DateRange } from '@/features/analytics/api';
import type { ResourceUtilization } from '@/types/api';
import { formatPercent } from '@/utils/format';

const ALL_TYPES = 'ALL';

/**
 * Every resource with its share of booked time, busiest first. A business
 * with many tables or rooms narrows it to one kind and pages through it.
 */
export function ResourceUtilizationCard({ resources, range }: { resources?: ResourceUtilization[]; range: DateRange }) {
  const [type, setType] = useState<string>(ALL_TYPES);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);

  // The kinds present in the data, in name order: "Room", "Table", "Therapist"…
  const types = useMemo(() => {
    const byId = new Map<number, string>();
    (resources ?? []).forEach((r) => byId.set(r.resourceTypeId, r.resourceTypeName ?? 'Other'));
    return [...byId.entries()].sort((a, b) => a[1].localeCompare(b[1]));
  }, [resources]);

  // A new range or kind starts from the first page.
  useEffect(() => {
    setPage(0);
  }, [range, type]);

  // A kind that no longer appears in the data (e.g. after a range change) falls back to all.
  const selected = types.some(([id]) => String(id) === type) ? type : ALL_TYPES;
  const rows = (resources ?? []).filter((r) => selected === ALL_TYPES || String(r.resourceTypeId) === selected);
  const lastPage = Math.max(0, Math.ceil(rows.length / rowsPerPage) - 1);
  const shownPage = Math.min(page, lastPage);

  return (
    <ChartCard
      title="Resource utilization"
      subtitle="Booked time as a share of each resource's available hours"
      action={
        types.length > 1 ? (
          <TextField
            select
            size="small"
            label="Type"
            value={selected}
            onChange={(event) => setType(event.target.value)}
            sx={{ minWidth: 160 }}
          >
            <MenuItem value={ALL_TYPES}>All types</MenuItem>
            {types.map(([id, name]) => (
              <MenuItem key={id} value={String(id)}>
                {name}
              </MenuItem>
            ))}
          </TextField>
        ) : null
      }
    >
      {resources ? (
        rows.length ? (
          <>
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
                {rows.slice(shownPage * rowsPerPage, shownPage * rowsPerPage + rowsPerPage).map((resource) => (
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
            <TablePagination
              component="div"
              count={rows.length}
              page={shownPage}
              onPageChange={(_, next) => setPage(next)}
              rowsPerPage={rowsPerPage}
              rowsPerPageOptions={[5, 10, 25]}
              onRowsPerPageChange={(event) => {
                setRowsPerPage(Number(event.target.value));
                setPage(0);
              }}
            />
          </>
        ) : (
          <Typography variant="body2" color="text.secondary">No resources to show.</Typography>
        )
      ) : (
        <LoadingState />
      )}
    </ChartCard>
  );
}
