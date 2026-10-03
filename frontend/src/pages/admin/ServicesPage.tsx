import { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import DesignServicesRoundedIcon from '@mui/icons-material/DesignServicesRounded';
import EditRoundedIcon from '@mui/icons-material/EditRounded';
import { errorMessage } from '@/api/client';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState, ErrorState, TableLoadingRows } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import { useBusiness } from '@/features/business/api';
import { useDeleteService, useServices, useSetServiceActive } from '@/features/catalog/api';
import type { Service } from '@/types/api';
import { formatDuration, formatMoney } from '@/utils/format';

export function ServicesPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const { data: business } = useBusiness();
  const { data: services, isLoading, isError, error, refetch } = useServices();
  const setActive = useSetServiceActive();
  const remove = useDeleteService();
  const [deleting, setDeleting] = useState<Service | null>(null);

  const confirmDelete = async () => {
    if (!deleting) {
      return;
    }
    try {
      const outcome = await remove.mutateAsync(deleting.id);
      outcome.deleted ? toast.success(outcome.message) : toast.notify(outcome.message, 'info');
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setDeleting(null);
    }
  };

  return (
    <>
      <PageHeader
        title="Services"
        description="What customers can book, and the resources each service needs."
        action={
          <Button component={RouterLink} to="/admin/services/new" variant="contained" startIcon={<AddRoundedIcon />}>
            New service
          </Button>
        }
      />

      {isError ? <ErrorState error={error} onRetry={() => refetch()} /> : null}
      {isLoading ? (
        <Paper variant="outlined">
          <TableLoadingRows />
        </Paper>
      ) : services && services.length === 0 ? (
        <EmptyState
          icon={<DesignServicesRoundedIcon />}
          title="No services yet"
          description="Create your first service and tell the platform which resources it needs."
          action={
            <Button component={RouterLink} to="/admin/services/new" variant="contained">
              Create a service
            </Button>
          }
        />
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Service</TableCell>
                <TableCell>Duration</TableCell>
                <TableCell>Price</TableCell>
                <TableCell align="center">Max guests</TableCell>
                <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>Needs</TableCell>
                <TableCell align="center">Active</TableCell>
                <TableCell align="right" />
              </TableRow>
            </TableHead>
            <TableBody>
              {services?.map((service) => (
                <TableRow key={service.id} hover>
                  <TableCell>
                    <Typography variant="body2" fontWeight={600}>
                      {service.name}
                    </Typography>
                    {service.allowCustomerResourceSelection ? (
                      <Typography variant="caption" color="text.secondary">
                        Customers can choose resources
                      </Typography>
                    ) : null}
                  </TableCell>
                  <TableCell>{formatDuration(service.durationMinutes)}</TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>
                    {formatMoney(service.price, business?.currency)}
                    {service.pricePerParticipant ? (
                      <Typography component="span" variant="caption" color="text.secondary">
                        {' '}
                        / person
                      </Typography>
                    ) : null}
                  </TableCell>
                  <TableCell align="center">{service.maxParticipants}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>
                    <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                      {service.requirements.length === 0 ? (
                        <Typography variant="caption" color="text.secondary">
                          No resources
                        </Typography>
                      ) : (
                        service.requirements.map((requirement) => (
                          <Chip
                            key={requirement.id}
                            size="small"
                            variant={requirement.required ? 'filled' : 'outlined'}
                            label={`${requirement.quantity} × ${requirement.resourceTypeName}${requirement.required ? '' : ' (optional)'}`}
                          />
                        ))
                      )}
                    </Stack>
                  </TableCell>
                  <TableCell align="center">
                    <Switch
                      checked={service.active}
                      inputProps={{ 'aria-label': `${service.name} active` }}
                      onChange={(event) =>
                        setActive
                          .mutateAsync({ id: service.id, active: event.target.checked })
                          .then(() => toast.success(event.target.checked ? 'Service activated' : 'Service deactivated'))
                          .catch((err) => toast.error(errorMessage(err)))
                      }
                    />
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    <Tooltip title="Edit">
                      <IconButton onClick={() => navigate(`/admin/services/${service.id}`)}>
                        <EditRoundedIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Delete">
                      <IconButton onClick={() => setDeleting(service)}>
                        <DeleteOutlineRoundedIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <ConfirmDialog
        open={deleting !== null}
        title={`Delete ${deleting?.name ?? 'service'}?`}
        message="A service with reservations is deactivated instead, so its history stays intact."
        confirmLabel="Delete"
        busy={remove.isPending}
        onConfirm={confirmDelete}
        onClose={() => setDeleting(null)}
      />
    </>
  );
}
