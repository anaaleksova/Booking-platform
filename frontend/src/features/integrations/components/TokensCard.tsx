import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import FormGroup from '@mui/material/FormGroup';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import { errorMessage } from '@/api/client';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { useToast } from '@/components/ToastProvider';
import { useCreateToken, useRevokeToken } from '@/features/integrations/api';
import type { IntegrationPermission, IntegrationToken } from '@/types/api';
import { formatDaysAgo } from '@/utils/format';
import { CopyField } from './IntegrationFields';
import { PERMISSION_LABELS } from './integrationLabels';

/**
 * API tokens: each works for this business only, with the permissions chosen.
 * A new token is shown once; revoking one stops it at once.
 */
export function TokensCard({
  tokens,
  availablePermissions,
}: {
  tokens: IntegrationToken[];
  availablePermissions: IntegrationPermission[];
}) {
  const toast = useToast();
  const [tokenDialog, setTokenDialog] = useState(false);
  const [newSecret, setNewSecret] = useState<string | null>(null);
  const [revoking, setRevoking] = useState<IntegrationToken | null>(null);
  const revoke = useRevokeToken();

  return (
    <>
      <Paper variant="outlined" sx={{ p: 2.5 }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" spacing={1} sx={{ mb: 2 }}>
          <div>
            <Typography variant="subtitle1" fontWeight={700}>
              API tokens
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Each token works for this business only, with the permissions you choose. It never opens the admin
              panel.
            </Typography>
          </div>
          <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={() => setTokenDialog(true)} sx={{ alignSelf: 'flex-start' }}>
            New token
          </Button>
        </Stack>
        {tokens.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            No tokens yet. Create one for n8n.
          </Typography>
        ) : (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Name</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>Token</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>Permissions</TableCell>
                  <TableCell>Last used</TableCell>
                  <TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {tokens.map((token) => (
                  <TableRow key={token.id}>
                    <TableCell>
                      <Typography variant="body2" fontWeight={600}>
                        {token.name}
                      </Typography>
                      {token.revokedAt ? <Chip size="small" label="Revoked" sx={{ mt: 0.5 }} /> : null}
                    </TableCell>
                    <TableCell sx={{ display: { xs: 'none', md: 'table-cell' }, fontFamily: 'monospace' }}>
                      {token.prefix}…
                    </TableCell>
                    <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>
                      {token.permissions.length === availablePermissions.length
                        ? 'All'
                        : token.permissions.map((permission) => PERMISSION_LABELS[permission]).join(', ')}
                    </TableCell>
                    <TableCell>{formatDaysAgo(token.lastUsedAt)}</TableCell>
                    <TableCell align="right">
                      {token.revokedAt ? null : (
                        <Button size="small" color="error" onClick={() => setRevoking(token)}>
                          Revoke
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>

      <NewTokenDialog
        open={tokenDialog}
        permissions={availablePermissions}
        onClose={() => setTokenDialog(false)}
        onCreated={(secret) => {
          setTokenDialog(false);
          setNewSecret(secret);
        }}
      />

      <Dialog open={newSecret !== null} onClose={() => setNewSecret(null)} maxWidth="sm" fullWidth>
        <DialogTitle>Copy your new token</DialogTitle>
        <DialogContent>
          <Alert severity="warning" sx={{ mb: 2 }}>
            This is the only time the token is shown. Store it in an n8n credential (Header Auth, name
            &quot;Authorization&quot;, value &quot;Bearer &lt;token&gt;&quot;).
          </Alert>
          {newSecret ? <CopyField label="Token" value={newSecret} /> : null}
        </DialogContent>
        <DialogActions>
          <Button variant="contained" onClick={() => setNewSecret(null)}>
            Done
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={revoking !== null}
        title="Revoke token?"
        message={`Anything using "${revoking?.name}" stops working immediately. This cannot be undone.`}
        confirmLabel="Revoke"
        busy={revoke.isPending}
        onClose={() => setRevoking(null)}
        onConfirm={async () => {
          if (!revoking) {
            return;
          }
          try {
            await revoke.mutateAsync(revoking.id);
            toast.success('Token revoked');
            setRevoking(null);
          } catch (err) {
            toast.error(errorMessage(err));
          }
        }}
      />
    </>
  );
}

function NewTokenDialog({
  open,
  permissions,
  onClose,
  onCreated,
}: {
  open: boolean;
  permissions: IntegrationPermission[];
  onClose: () => void;
  onCreated: (secret: string) => void;
}) {
  const toast = useToast();
  const create = useCreateToken();
  const [name, setName] = useState('n8n');
  const [granted, setGranted] = useState<IntegrationPermission[]>(permissions);

  useEffect(() => {
    if (open) {
      setName('n8n');
      setGranted(permissions);
    }
  }, [open, permissions]);

  const toggle = (permission: IntegrationPermission) =>
    setGranted((current) =>
      current.includes(permission) ? current.filter((item) => item !== permission) : [...current, permission],
    );

  const submit = async () => {
    try {
      const created = await create.mutateAsync({ name: name.trim(), permissions: granted });
      onCreated(created.secret);
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>New API token</DialogTitle>
      <DialogContent>
        <TextField autoFocus fullWidth label="Name" value={name} onChange={(event) => setName(event.target.value)} sx={{ mt: 1, mb: 2 }} />
        <Typography variant="subtitle2" sx={{ mb: 0.5 }}>
          Permissions
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          The booking workflows need all of them. Grant less for tools that only read.
        </Typography>
        <FormGroup>
          {permissions.map((permission) => (
            <FormControlLabel
              key={permission}
              control={<Checkbox size="small" checked={granted.includes(permission)} onChange={() => toggle(permission)} />}
              label={PERMISSION_LABELS[permission]}
            />
          ))}
        </FormGroup>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={!name.trim() || granted.length === 0 || create.isPending} onClick={submit}>
          Create token
        </Button>
      </DialogActions>
    </Dialog>
  );
}
