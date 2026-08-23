import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import AppBar from '@mui/material/AppBar';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import Drawer from '@mui/material/Drawer';
import IconButton from '@mui/material/IconButton';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Toolbar from '@mui/material/Toolbar';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import AssessmentRoundedIcon from '@mui/icons-material/AssessmentRounded';
import BarChartRoundedIcon from '@mui/icons-material/BarChartRounded';
import CalendarMonthRoundedIcon from '@mui/icons-material/CalendarMonthRounded';
import EventNoteRoundedIcon from '@mui/icons-material/EventNoteRounded';
import DesignServicesRoundedIcon from '@mui/icons-material/DesignServicesRounded';
import GroupsRoundedIcon from '@mui/icons-material/GroupsRounded';
import Inventory2RoundedIcon from '@mui/icons-material/Inventory2Rounded';
import PeopleAltRoundedIcon from '@mui/icons-material/PeopleAltRounded';
import ScheduleRoundedIcon from '@mui/icons-material/ScheduleRounded';
import SettingsRoundedIcon from '@mui/icons-material/SettingsRounded';
import MenuRoundedIcon from '@mui/icons-material/MenuRounded';
import LaunchRoundedIcon from '@mui/icons-material/LaunchRounded';
import LogoutRoundedIcon from '@mui/icons-material/LogoutRounded';
import { useAuth } from '@/features/auth/AuthContext';
import { useBusiness } from '@/features/businesses/useBusiness';

const DRAWER_WIDTH = 248;

const NAV_ITEMS = [
  { to: '/dashboard', label: 'Dashboard', icon: <BarChartRoundedIcon />, end: true },
  { to: '/dashboard/calendar', label: 'Calendar', icon: <CalendarMonthRoundedIcon /> },
  { to: '/dashboard/bookings', label: 'Bookings', icon: <EventNoteRoundedIcon /> },
  { to: '/dashboard/services', label: 'Services', icon: <DesignServicesRoundedIcon />, ownerOnly: true },
  { to: '/dashboard/staff', label: 'Staff', icon: <GroupsRoundedIcon />, ownerOnly: true },
  { to: '/dashboard/resources', label: 'Resources', icon: <Inventory2RoundedIcon />, ownerOnly: true },
  { to: '/dashboard/customers', label: 'Customers', icon: <PeopleAltRoundedIcon /> },
  { to: '/dashboard/reports', label: 'Reports', icon: <AssessmentRoundedIcon />, ownerOnly: true },
  { to: '/dashboard/availability', label: 'Availability', icon: <ScheduleRoundedIcon /> },
  { to: '/dashboard/settings', label: 'Settings', icon: <SettingsRoundedIcon />, ownerOnly: true },
];

/** Sidebar shell: permanent on desktop, a temporary drawer on small screens. */
export function DashboardLayout() {
  const theme = useTheme();
  const isDesktop = useMediaQuery(theme.breakpoints.up('md'));
  const [mobileOpen, setMobileOpen] = useState(false);
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null);
  const { user, isOwner, logout } = useAuth();
  const { data: business } = useBusiness();
  const navigate = useNavigate();

  const visibleItems = NAV_ITEMS.filter((item) => !item.ownerOnly || isOwner);

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  const navigation = (
    <Box sx={{ width: DRAWER_WIDTH, display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Toolbar sx={{ px: 2.5 }}>
        <Stack spacing={0.25}>
          <Typography variant="subtitle1" fontWeight={700} noWrap>
            {business?.name ?? user?.activeBusiness?.businessName ?? 'Booking'}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {business?.timezone}
          </Typography>
        </Stack>
      </Toolbar>
      <Divider />
      <List sx={{ px: 1.5, py: 2, flex: 1 }}>
        {visibleItems.map((item) => (
          <ListItemButton
            key={item.to}
            component={NavLink}
            to={item.to}
            end={item.end}
            onClick={() => setMobileOpen(false)}
            sx={{
              borderRadius: 2,
              mb: 0.5,
              '&.active': {
                bgcolor: 'primary.main',
                color: 'primary.contrastText',
                '& .MuiListItemIcon-root': { color: 'inherit' },
                '&:hover': { bgcolor: 'primary.dark' },
              },
            }}
          >
            <ListItemIcon sx={{ minWidth: 38 }}>{item.icon}</ListItemIcon>
            <ListItemText primaryTypographyProps={{ fontSize: 14, fontWeight: 600 }} primary={item.label} />
          </ListItemButton>
        ))}
      </List>
      <Divider />
      <Box sx={{ p: 2 }}>
        {business ? (
          <Button
            fullWidth
            size="small"
            variant="outlined"
            startIcon={<LaunchRoundedIcon />}
            href={`/book/${business.slug}`}
            target="_blank"
            rel="noopener"
          >
            Public booking page
          </Button>
        ) : null}
      </Box>
    </Box>
  );

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar
        position="fixed"
        color="inherit"
        elevation={0}
        sx={{
          width: { md: `calc(100% - ${DRAWER_WIDTH}px)` },
          ml: { md: `${DRAWER_WIDTH}px` },
          borderBottom: 1,
          borderColor: 'divider',
          bgcolor: 'background.paper',
        }}
      >
        <Toolbar>
          <IconButton
            edge="start"
            onClick={() => setMobileOpen(true)}
            sx={{ mr: 2, display: { md: 'none' } }}
            aria-label="Open navigation"
          >
            <MenuRoundedIcon />
          </IconButton>
          <Box sx={{ flex: 1 }} />
          {user?.activeBusiness ? (
            <Chip
              size="small"
              label={user.activeBusiness.role === 'BUSINESS_OWNER' ? 'Owner' : 'Staff'}
              sx={{ mr: 2 }}
            />
          ) : null}
          <Tooltip title={user?.email ?? ''}>
            <IconButton onClick={(event) => setMenuAnchor(event.currentTarget)} aria-label="Account menu">
              <Avatar sx={{ width: 34, height: 34, bgcolor: 'primary.main', fontSize: 15 }}>
                {user?.fullName?.charAt(0)?.toUpperCase() ?? '?'}
              </Avatar>
            </IconButton>
          </Tooltip>
          <Menu anchorEl={menuAnchor} open={Boolean(menuAnchor)} onClose={() => setMenuAnchor(null)}>
            <MenuItem disabled>
              <Stack>
                <Typography variant="body2" fontWeight={600}>
                  {user?.fullName}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {user?.email}
                </Typography>
              </Stack>
            </MenuItem>
            <Divider />
            <MenuItem onClick={handleLogout}>
              <ListItemIcon>
                <LogoutRoundedIcon fontSize="small" />
              </ListItemIcon>
              Sign out
            </MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>

      <Box component="nav" sx={{ width: { md: DRAWER_WIDTH }, flexShrink: { md: 0 } }}>
        <Drawer
          variant={isDesktop ? 'permanent' : 'temporary'}
          open={isDesktop || mobileOpen}
          onClose={() => setMobileOpen(false)}
          ModalProps={{ keepMounted: true }}
          sx={{
            '& .MuiDrawer-paper': {
              width: DRAWER_WIDTH,
              boxSizing: 'border-box',
              borderRight: 1,
              borderColor: 'divider',
            },
          }}
        >
          {navigation}
        </Drawer>
      </Box>

      <Box component="main" sx={{ flexGrow: 1, width: { md: `calc(100% - ${DRAWER_WIDTH}px)` } }}>
        <Toolbar />
        <Box sx={{ p: { xs: 2, md: 4 } }}>
          <Outlet />
        </Box>
      </Box>
    </Box>
  );
}
