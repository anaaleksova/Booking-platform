import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import InputAdornment from '@mui/material/InputAdornment';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import PeopleAltRoundedIcon from '@mui/icons-material/PeopleAltRounded';
import SearchRoundedIcon from '@mui/icons-material/SearchRounded';
import { errorMessage } from '@/api/client';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState, ErrorState, TableLoadingRows } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import { useBusiness } from '@/features/business/api';
import { useCustomers, useSaveCustomer, type CustomerInput } from '@/features/customers/api';
import { formatDaysAgo, formatLocalDate, formatMoney } from '@/utils/format';

const PAGE_SIZES = [10, 25, 50];

export function CustomersPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const { data: business } = useBusiness();
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(PAGE_SIZES[0]);
  const [draft, setDraft] = useState<CustomerInput | null>(null);
  const { data, isLoading, isError, error, refetch } = useCustomers(query, page, pageSize);
  const save = useSaveCustomer();

  // Debounce the search box so typing does not fire a request per keystroke.
  useEffect(() => {
    const timer = setTimeout(() => {
      setQuery(search.trim());
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);

  const submit = async () => {
    if (!draft?.firstName.trim()) {
      return;
    }
    try {
      const created = await save.mutateAsync({ input: draft });
      toast.success('Customer added');
      setDraft(null);
      navigate(`/admin/customers/${created.id}`);
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  return (
    <>
      <PageHeader
        title="Customers"
        description="Returning customers are recognised by email or phone, so each person has one record."
        action={
          <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={() => setDraft({ firstName: '' })}>
            New customer
          </Button>
        }
      />
      <TextField
        fullWidth
        placeholder="Search by name, email or phone"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        InputProps={{ startAdornment: <InputAdornment position="start"><SearchRoundedIcon /></InputAdornment> }}
        sx={{ mb: 2, bgcolor: 'background.paper' }}
      />
      {isError ? <ErrorState error={error} onRetry={() => refetch()} /> : null}
      {isLoading ? (
        <Paper variant="outlined">
          <TableLoadingRows />
        </Paper>
      ) : data?.content.length === 0 ? (
        <EmptyState icon={<PeopleAltRoundedIcon />} title={query ? 'No matching customers' : 'No customers yet'} />
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>Email</TableCell>
                <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>Phone</TableCell>
                <TableCell align="center">Reservations</TableCell>
                <TableCell align="right">Total spent</TableCell>
                <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>Last visit</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {data?.content.map((customer) => (
                <TableRow key={customer.id} hover sx={{ cursor: 'pointer' }} onClick={() => navigate(`/admin/customers/${customer.id}`)}>
                  <TableCell>
                    <Typography variant="body2" fontWeight={600}>
                      {customer.fullName}
                    </Typography>
                  </TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{customer.email ?? '—'}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{customer.phone ?? '—'}</TableCell>
                  <TableCell align="center">{customer.reservationCount}</TableCell>
                  <TableCell align="right">{formatMoney(customer.totalSpent, business?.currency)}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>
                    <Typography variant="body2">
                      {customer.lastReservationAt ? formatLocalDate(customer.lastReservationAt) : '—'}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {customer.lastReservationAt ? formatDaysAgo(customer.lastReservationAt) : ''}
                    </Typography>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <TablePagination
            component="div"
            count={data?.totalElements ?? 0}
            page={page}
            rowsPerPage={pageSize}
            rowsPerPageOptions={PAGE_SIZES}
            onPageChange={(_, next) => setPage(next)}
            onRowsPerPageChange={(e) => {
              setPageSize(Number(e.target.value));
              setPage(0);
            }}
          />
        </TableContainer>
      )}

      <Dialog open={draft !== null} onClose={() => setDraft(null)} maxWidth="xs" fullWidth>
        <DialogTitle>New customer</DialogTitle>
        {draft ? (
          <DialogContent>
            <Stack spacing={2} sx={{ mt: 1 }}>
              <TextField autoFocus label="First name" value={draft.firstName} onChange={(e) => setDraft({ ...draft, firstName: e.target.value })} />
              <TextField label="Last name" value={draft.lastName ?? ''} onChange={(e) => setDraft({ ...draft, lastName: e.target.value })} />
              <TextField label="Email" value={draft.email ?? ''} onChange={(e) => setDraft({ ...draft, email: e.target.value })} />
              <TextField label="Phone" value={draft.phone ?? ''} onChange={(e) => setDraft({ ...draft, phone: e.target.value })} helperText="Email or phone is required" />
            </Stack>
          </DialogContent>
        ) : null}
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setDraft(null)}>Cancel</Button>
          <Button variant="contained" onClick={submit} disabled={!draft?.firstName.trim() || save.isPending}>
            Save
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
