import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import AppBar from '@mui/material/AppBar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Container from '@mui/material/Container';
import Grid from '@mui/material/Grid';
import InputAdornment from '@mui/material/InputAdornment';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Tab from '@mui/material/Tab';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Tabs from '@mui/material/Tabs';
import TextField from '@mui/material/TextField';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import AdminPanelSettingsRoundedIcon from '@mui/icons-material/AdminPanelSettingsRounded';
import LoginRoundedIcon from '@mui/icons-material/LoginRounded';
import LogoutRoundedIcon from '@mui/icons-material/LogoutRounded';
import SearchRoundedIcon from '@mui/icons-material/SearchRounded';
import StorefrontRoundedIcon from '@mui/icons-material/StorefrontRounded';
import { errorMessage } from '@/api/client';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { KpiCard } from '@/components/KpiCard';
import { EmptyState, ErrorState, TableLoadingRows } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import { useAuth } from '@/features/auth/AuthContext';
import {
  usePlatformAccounts,
  usePlatformActivity,
  usePlatformBusinesses,
  usePlatformOverview,
  usePlatformSession,
  useSetAccountActive,
  useSetBusinessActive,
} from '@/features/platform/api';
import type { PlatformAccount, PlatformAction, PlatformBusiness } from '@/types/api';
import { formatLocalDate, formatNumber } from '@/utils/format';
import dayjs from 'dayjs';

type TabKey = 'overview' | 'businesses' | 'accounts' | 'activity';
const TABS: Array<{ key: TabKey; label: string }> = [
  { key: 'overview', label: 'Overview' },
  { key: 'businesses', label: 'Businesses' },
  { key: 'accounts', label: 'Accounts' },
  { key: 'activity', label: 'Activity log' },
];

const ACTION_LABELS: Record<PlatformAction, string> = {
  ENTERED_BUSINESS: 'Opened the admin of',
  BUSINESS_DEACTIVATED: 'Took offline',
  BUSINESS_REACTIVATED: 'Brought back online',
  ACCOUNT_DEACTIVATED: 'Deactivated the account',
  ACCOUNT_REACTIVATED: 'Reactivated the account',
};

/**
 * The platform area: every business and account, for the people who run the
 * platform. Not tied to any business, so it has its own frame.
 */
export function PlatformPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { exit } = usePlatformSession();
  const [params, setParams] = useSearchParams();
  const tab = (TABS.find((item) => item.key === params.get('tab'))?.key ?? 'overview') as TabKey;
  const ownBusiness = (user?.businesses ?? [])[0];

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar position="sticky" color="inherit" elevation={0} sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Toolbar sx={{ gap: 1.5 }}>
          <AdminPanelSettingsRoundedIcon color="primary" />
          <Typography variant="subtitle1" fontWeight={800} sx={{ flex: 1 }} noWrap>
            Platform administration
          </Typography>
          {ownBusiness ? (
            <Button size="small" startIcon={<StorefrontRoundedIcon />} onClick={() => exit('/admin')}>
              {ownBusiness.businessName}
            </Button>
          ) : null}
          <Button
            size="small"
            color="inherit"
            startIcon={<LogoutRoundedIcon />}
            onClick={() => {
              logout();
              navigate('/login', { replace: true });
            }}
          >
            Sign out
          </Button>
        </Toolbar>
        <Container maxWidth="lg">
          <Tabs value={tab} onChange={(_, value: TabKey) => setParams({ tab: value })} variant="scrollable">
            {TABS.map((item) => (
              <Tab key={item.key} value={item.key} label={item.label} />
            ))}
          </Tabs>
        </Container>
      </AppBar>
      <Container maxWidth="lg" sx={{ py: { xs: 3, md: 4 } }}>
        {tab === 'overview' ? <OverviewTab /> : null}
        {tab === 'businesses' ? <BusinessesTab /> : null}
        {tab === 'accounts' ? <AccountsTab /> : null}
        {tab === 'activity' ? <ActivityTab /> : null}
      </Container>
    </Box>
  );
}

function OverviewTab() {
  const { data, isLoading, isError, error, refetch } = usePlatformOverview();
  if (isError) {
    return <ErrorState error={error} onRetry={() => refetch()} />;
  }
  const value = (n?: number) => (n === undefined ? '—' : formatNumber(n));
  return (
    <Grid container spacing={2}>
      <Grid item xs={12} sm={6} md={3}>
        <KpiCard
          label="Businesses"
          value={value(data?.businesses)}
          note={data ? `${formatNumber(data.activeBusinesses)} online` : undefined}
          loading={isLoading}
        />
      </Grid>
      <Grid item xs={12} sm={6} md={3}>
        <KpiCard label="New businesses" value={value(data?.newBusinesses)} note="Last 30 days" loading={isLoading} />
      </Grid>
      <Grid item xs={12} sm={6} md={3}>
        <KpiCard label="Accounts" value={value(data?.accounts)} note="Owners, staff and customers" loading={isLoading} />
      </Grid>
      <Grid item xs={12} sm={6} md={3}>
        <KpiCard label="Reservations" value={value(data?.reservationsThisMonth)} note="Made this month" loading={isLoading} />
      </Grid>
    </Grid>
  );
}

function SearchField({ value, onChange, placeholder }: { value: string; onChange: (value: string) => void; placeholder: string }) {
  return (
    <TextField
      size="small"
      value={value}
      onChange={(event) => onChange(event.target.value)}
      placeholder={placeholder}
      sx={{ mb: 2, width: { xs: '100%', sm: 360 } }}
      InputProps={{ startAdornment: <InputAdornment position="start"><SearchRoundedIcon fontSize="small" /></InputAdornment> }}
    />
  );
}

function BusinessesTab() {
  const toast = useToast();
  const { enter } = usePlatformSession();
  const [query, setQuery] = useState('');
  const { data, isLoading, isError, error, refetch } = usePlatformBusinesses(query.trim());
  const setActive = useSetBusinessActive();
  const [confirming, setConfirming] = useState<PlatformBusiness | null>(null);
  const [entering, setEntering] = useState<number | null>(null);

  const open = async (business: PlatformBusiness) => {
    setEntering(business.id);
    try {
      await enter(business.id);
    } catch (err) {
      toast.error(errorMessage(err));
      setEntering(null);
    }
  };

  const toggle = (business: PlatformBusiness) =>
    setActive
      .mutateAsync({ id: business.id, active: !business.active })
      .then(() => toast.success(business.active ? `${business.name} is offline` : `${business.name} is back online`))
      .catch((err) => toast.error(errorMessage(err)))
      .finally(() => setConfirming(null));

  return (
    <>
      <SearchField value={query} onChange={setQuery} placeholder="Search by name, page address or owner email" />
      {isError ? <ErrorState error={error} onRetry={() => refetch()} /> : null}
      {isLoading ? (
        <Paper variant="outlined">
          <TableLoadingRows />
        </Paper>
      ) : data?.length === 0 ? (
        <EmptyState icon={<StorefrontRoundedIcon />} title="No businesses found" />
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Business</TableCell>
                <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>Owner</TableCell>
                <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>Created</TableCell>
                <TableCell align="center" sx={{ display: { xs: 'none', sm: 'table-cell' } }}>
                  Bookings (30 days)
                </TableCell>
                <TableCell>Status</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {data?.map((business) => (
                <TableRow key={business.id} hover>
                  <TableCell>
                    <Typography variant="body2" fontWeight={700}>
                      {business.name}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      /b/{business.slug}
                    </Typography>
                  </TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>
                    {business.ownerEmails.join(', ') || '—'}
                  </TableCell>
                  <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>{formatLocalDate(business.createdAt)}</TableCell>
                  <TableCell align="center" sx={{ display: { xs: 'none', sm: 'table-cell' } }}>
                    {formatNumber(business.recentReservations)}
                  </TableCell>
                  <TableCell>
                    <Chip size="small" variant="outlined" color={business.active ? 'success' : 'default'} label={business.active ? 'Online' : 'Offline'} />
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    <Button
                      size="small"
                      startIcon={<LoginRoundedIcon />}
                      disabled={entering !== null}
                      onClick={() => open(business)}
                    >
                      {entering === business.id ? 'Opening…' : 'Open admin'}
                    </Button>
                    <Button size="small" color={business.active ? 'error' : 'primary'} onClick={() => setConfirming(business)}>
                      {business.active ? 'Take offline' : 'Bring online'}
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      <ConfirmDialog
        open={confirming !== null}
        title={confirming?.active ? `Take ${confirming.name} offline?` : `Bring ${confirming?.name ?? ''} back online?`}
        message={
          confirming?.active
            ? 'Its booking page disappears, its owner and staff cannot open the admin, and its chat integrations stop. Nothing is deleted; you can bring it back at any time.'
            : 'Its booking page, admin and integrations work again.'
        }
        confirmLabel={confirming?.active ? 'Take offline' : 'Bring online'}
        destructive={confirming?.active}
        busy={setActive.isPending}
        onConfirm={() => confirming && toggle(confirming)}
        onClose={() => setConfirming(null)}
      />
    </>
  );
}

function AccountsTab() {
  const toast = useToast();
  const [query, setQuery] = useState('');
  const { data, isLoading, isError, error, refetch } = usePlatformAccounts(query.trim());
  const setActive = useSetAccountActive();
  const [confirming, setConfirming] = useState<PlatformAccount | null>(null);

  const toggle = (account: PlatformAccount) =>
    setActive
      .mutateAsync({ id: account.id, active: !account.active })
      .then(() => toast.success(account.active ? `${account.email} can no longer sign in` : `${account.email} can sign in again`))
      .catch((err) => toast.error(errorMessage(err)))
      .finally(() => setConfirming(null));

  return (
    <>
      <SearchField value={query} onChange={setQuery} placeholder="Search by email or name" />
      {isError ? <ErrorState error={error} onRetry={() => refetch()} /> : null}
      {isLoading ? (
        <Paper variant="outlined">
          <TableLoadingRows />
        </Paper>
      ) : data?.length === 0 ? (
        <EmptyState title="No accounts found" />
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Account</TableCell>
                <TableCell>Roles</TableCell>
                <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>Created</TableCell>
                <TableCell>Status</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {data?.map((account) => (
                <TableRow key={account.id} hover>
                  <TableCell>
                    <Typography variant="body2" fontWeight={700}>
                      {account.fullName}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {account.email}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
                      {account.platformAdmin ? <Chip size="small" color="primary" label="Platform admin" /> : null}
                      {account.roles.map((role) => (
                        <Chip key={role} size="small" variant="outlined" label={role} />
                      ))}
                      {account.customerAt > 0 ? (
                        <Chip
                          size="small"
                          variant="outlined"
                          label={`Customer at ${account.customerAt} ${account.customerAt === 1 ? 'business' : 'businesses'}`}
                        />
                      ) : null}
                    </Stack>
                  </TableCell>
                  <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>{formatLocalDate(account.createdAt)}</TableCell>
                  <TableCell>
                    <Chip size="small" variant="outlined" color={account.active ? 'success' : 'default'} label={account.active ? 'Active' : 'Deactivated'} />
                  </TableCell>
                  <TableCell align="right">
                    {account.platformAdmin ? null : (
                      <Button size="small" color={account.active ? 'error' : 'primary'} onClick={() => setConfirming(account)}>
                        {account.active ? 'Deactivate' : 'Reactivate'}
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      <ConfirmDialog
        open={confirming !== null}
        title={confirming?.active ? `Deactivate ${confirming.email}?` : `Reactivate ${confirming?.email ?? ''}?`}
        message={
          confirming?.active
            ? 'They are signed out everywhere and cannot sign in. Their bookings and businesses are kept.'
            : 'They can sign in again with their password.'
        }
        confirmLabel={confirming?.active ? 'Deactivate' : 'Reactivate'}
        destructive={confirming?.active}
        busy={setActive.isPending}
        onConfirm={() => confirming && toggle(confirming)}
        onClose={() => setConfirming(null)}
      />
    </>
  );
}

function ActivityTab() {
  const { data, isLoading, isError, error, refetch } = usePlatformActivity();
  if (isError) {
    return <ErrorState error={error} onRetry={() => refetch()} />;
  }
  if (isLoading) {
    return (
      <Paper variant="outlined">
        <TableLoadingRows />
      </Paper>
    );
  }
  if (!data?.length) {
    return <EmptyState title="Nothing yet" description="Opening a business's admin and taking things offline are recorded here." />;
  }
  return (
    <TableContainer component={Paper} variant="outlined">
      <Table>
        <TableHead>
          <TableRow>
            <TableCell>When</TableCell>
            <TableCell>Who</TableCell>
            <TableCell>What</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {data.map((row) => (
            <TableRow key={row.id}>
              <TableCell sx={{ whiteSpace: 'nowrap' }}>{dayjs(row.createdAt).format('D MMM YYYY, HH:mm')}</TableCell>
              <TableCell>{row.actorEmail}</TableCell>
              <TableCell>
                {ACTION_LABELS[row.action] ?? row.action} <strong>{row.targetName}</strong>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
