import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import InputAdornment from '@mui/material/InputAdornment';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import PeopleAltRoundedIcon from '@mui/icons-material/PeopleAltRounded';
import SearchRoundedIcon from '@mui/icons-material/SearchRounded';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState, ErrorState, TableLoadingRows } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import { useCreateCustomer, useCustomers } from '@/features/customers/useCustomers';
import { errorMessage } from '@/api/client';

export function CustomersPage() {
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [debounced, setDebounced] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [createOpen, setCreateOpen] = useState(false);

  // Debounced so typing a name does not fire a request per keystroke.
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebounced(search);
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);

  const { data, isLoading, isError, error, refetch } = useCustomers(debounced, page, size);

  return (
    <>
      <PageHeader
        title="Customers"
        description="Everyone who has booked with you."
        action={
          <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={() => setCreateOpen(true)}>
            Add customer
          </Button>
        }
      />

      <TextField
        fullWidth
        size="small"
        placeholder="Search by name, phone or email"
        value={search}
        onChange={(event) => setSearch(event.target.value)}
        sx={{ mb: 2.5, maxWidth: 420 }}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              <SearchRoundedIcon fontSize="small" />
            </InputAdornment>
          ),
        }}
      />

      {isError ? <ErrorState error={error} onRetry={() => void refetch()} /> : null}

      <Card>
        {isLoading ? (
          <TableLoadingRows />
        ) : (data?.content.length ?? 0) === 0 ? (
          <EmptyState
            title={debounced ? 'No customers match that search' : 'No customers yet'}
            description={
              debounced
                ? 'Try a different name, phone number or email.'
                : 'Customers are created automatically when someone books through your public page.'
            }
            icon={<PeopleAltRoundedIcon />}
          />
        ) : (
          <>
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Name</TableCell>
                    <TableCell>Phone</TableCell>
                    <TableCell>Email</TableCell>
                    <TableCell>Notes</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {data?.content.map((customer) => (
                    <TableRow
                      key={customer.id}
                      hover
                      sx={{ cursor: 'pointer' }}
                      onClick={() => navigate(`/dashboard/customers/${customer.id}`)}
                    >
                      <TableCell sx={{ fontWeight: 600 }}>{customer.name}</TableCell>
                      <TableCell>{customer.phone ?? '—'}</TableCell>
                      <TableCell>{customer.email ?? '—'}</TableCell>
                      <TableCell sx={{ maxWidth: 320 }} className="ellipsis">
                        {customer.notes ?? '—'}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
            <TablePagination
              component="div"
              count={data?.totalElements ?? 0}
              page={page}
              onPageChange={(_, value) => setPage(value)}
              rowsPerPage={size}
              onRowsPerPageChange={(event) => {
                setSize(Number(event.target.value));
                setPage(0);
              }}
              rowsPerPageOptions={[10, 25, 50]}
            />
          </>
        )}
      </Card>

      <CustomerDialog open={createOpen} onClose={() => setCreateOpen(false)} />
    </>
  );
}

function CustomerDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const toast = useToast();
  const createCustomer = useCreateCustomer();
  const [form, setForm] = useState({ name: '', phone: '', email: '', notes: '' });
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setForm({ name: '', phone: '', email: '', notes: '' });
      setSubmitError(null);
    }
  }, [open]);

  const submit = async () => {
    setSubmitError(null);
    try {
      await createCustomer.mutateAsync({
        name: form.name.trim(),
        phone: form.phone.trim() || undefined,
        email: form.email.trim() || undefined,
        notes: form.notes.trim() || undefined,
      });
      toast.success('Customer added.');
      onClose();
    } catch (error) {
      setSubmitError(errorMessage(error));
    }
  };

  const valid = form.name.trim().length > 0 && (form.phone.trim() || form.email.trim());

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>New customer</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2.5}>
          <TextField
            label="Name"
            required
            autoFocus
            fullWidth
            value={form.name}
            onChange={(event) => setForm({ ...form, name: event.target.value })}
          />
          <TextField
            label="Phone"
            fullWidth
            value={form.phone}
            onChange={(event) => setForm({ ...form, phone: event.target.value })}
            helperText="A phone number or an email is required"
          />
          <TextField
            label="Email"
            type="email"
            fullWidth
            value={form.email}
            onChange={(event) => setForm({ ...form, email: event.target.value })}
          />
          <TextField
            label="Notes"
            fullWidth
            multiline
            minRows={2}
            value={form.notes}
            onChange={(event) => setForm({ ...form, notes: event.target.value })}
          />
          {submitError ? <Alert severity="error">{submitError}</Alert> : null}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={() => void submit()} disabled={!valid || createCustomer.isPending}>
          Add customer
        </Button>
      </DialogActions>
    </Dialog>
  );
}
