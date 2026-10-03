import { Suspense, useEffect, useState } from 'react';
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
import BarChartRoundedIcon from '@mui/icons-material/BarChartRounded';
import CategoryRoundedIcon from '@mui/icons-material/CategoryRounded';
import DesignServicesRoundedIcon from '@mui/icons-material/DesignServicesRounded';
import EventNoteRoundedIcon from '@mui/icons-material/EventNoteRounded';
import GroupsRoundedIcon from '@mui/icons-material/GroupsRounded';
import InsightsRoundedIcon from '@mui/icons-material/InsightsRounded';
import Inventory2RoundedIcon from '@mui/icons-material/Inventory2Rounded';
import LaunchRoundedIcon from '@mui/icons-material/LaunchRounded';
import LocalOfferRoundedIcon from '@mui/icons-material/LocalOfferRounded';
import LogoutRoundedIcon from '@mui/icons-material/LogoutRounded';
import MenuRoundedIcon from '@mui/icons-material/MenuRounded';
import PaymentsRoundedIcon from '@mui/icons-material/PaymentsRounded';
import PeopleAltRoundedIcon from '@mui/icons-material/PeopleAltRounded';
import SettingsRoundedIcon from '@mui/icons-material/SettingsRounded';
import { useAuth } from '@/features/auth/AuthContext';
import { useBusiness } from '@/features/business/api';
import { LoadingState } from '@/components/states';
import { prefetchAdminPages } from '@/routes/prefetch';

const DRAWER_WIDTH = 248;

interface NavItem {
  to: string;
  label: string;
  icon: JSX.Element;
  end?: boolean;
  ownerOnly?: boolean;
}

const NAV_SECTIONS: Array<{ label: string; items: NavItem[] }> = [
  {
    label: 'Overview',
    items: [
      { to: '/admin', label: 'Dashboard', icon: <BarChartRoundedIcon />, end: true, ownerOnly: true },
      { to: '/admin/reservations', label: 'Reservations', icon: <EventNoteRoundedIcon /> },
      { to: '/admin/customers', label: 'Customers', icon: <PeopleAltRoundedIcon /> },
    ],
  },
  {
    label: 'Catalogue',
    items: [
      { to: '/admin/services', label: 'Services', icon: <DesignServicesRoundedIcon />, ownerOnly: true },
      { to: '/admin/resources', label: 'Resources', icon: <Inventory2RoundedIcon />, ownerOnly: true },
      { to: '/admin/resource-types', label: 'Resource types', icon: <CategoryRoundedIcon />, ownerOnly: true },
      { to: '/admin/employees', label: 'Employees', icon: <GroupsRoundedIcon />, ownerOnly: true },
      { to: '/admin/discounts', label: 'Discounts', icon: <LocalOfferRoundedIcon />, ownerOnly: true },
    ],
  },
  {
    label: 'Insights',
    items: [
      { to: '/admin/revenue', label: 'Revenue', icon: <PaymentsRoundedIcon />, ownerOnly: true },
      { to: '/admin/analytics', label: 'Analytics', icon: <InsightsRoundedIcon />, ownerOnly: true },
      { to: '/admin/settings', label: 'Settings', icon: <SettingsRoundedIcon />, ownerOnly: true },
    ],
  },
];

/** Admin shell: permanent sidebar on desktop, a temporary drawer on small screens. */
export function AdminLayout() {
  const theme = useTheme();
  const isDesktop = useMediaQuery(theme.breakpoints.up('md'));
  const [mobileOpen, setMobileOpen] = useState(false);
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null);
  const { user, isOwner, logout } = useAuth();
  const { data: business } = useBusiness();
  const navigate = useNavigate();

  useEffect(() => {
    prefetchAdminPages();
  }, []);

  const sections = NAV_SECTIONS.map((section) => ({
    ...section,
    items: section.items.filter((item) => !item.ownerOnly || isOwner),
  })).filter((section) => section.items.length > 0);

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  const navigation = (
    <Box sx={{ width: DRAWER_WIDTH, display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Toolbar sx={{ px: 2.5 }}>
        <Stack spacing={0.25}>
          <Typography variant="subtitle1" fontWeight={700} noWrap>
            {business?.name ?? user?.activeBusiness?.businessName ?? 'Admin'}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {business ? `${business.timezone} · ${business.currency}` : ''}
          </Typography>
        </Stack>
      </Toolbar>
      <Divider />
      <Box sx={{ flex: 1, overflowY: 'auto', py: 1 }}>
        {sections.map((section) => (
          <List
            key={section.label}
            sx={{ px: 1.5, py: 1 }}
            subheader={
              <Typography variant="overline" color="text.secondary" sx={{ px: 1.5, lineHeight: 2.5 }}>
                {section.label}
              </Typography>
            }
          >
            {section.items.map((item) => (
              <ListItemButton
                key={item.to}
                component={NavLink}
                to={item.to}
                end={item.end}
                onClick={() => setMobileOpen(false)}
                sx={{
                  borderRadius: 2,
                  mb: 0.25,
                  py: 0.75,
                  '&.active': {
                    bgcolor: 'primary.main',
                    color: 'primary.contrastText',
                    '& .MuiListItemIcon-root': { color: 'inherit' },
                    '&:hover': { bgcolor: 'primary.dark' },
                  },
                }}
              >
                <ListItemIcon sx={{ minWidth: 36 }}>{item.icon}</ListItemIcon>
                <ListItemText primaryTypographyProps={{ fontSize: 14, fontWeight: 600 }} primary={item.label} />
              </ListItemButton>
            ))}
          </List>
        ))}
      </Box>
      <Divider />
      <Box sx={{ p: 2 }}>
        {business ? (
          <Button
            fullWidth
            size="small"
            variant="outlined"
            startIcon={<LaunchRoundedIcon />}
            href={`/b/${business.slug}`}
            target="_blank"
            rel="noopener"
          >
            Customer booking page
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

      <Box component="main" sx={{ flexGrow: 1, minWidth: 0, width: { md: `calc(100% - ${DRAWER_WIDTH}px)` } }}>
        <Toolbar />
        <Box sx={{ p: { xs: 2, md: 4 } }}>
          {/* Only the content area waits for a page; the sidebar and bar stay put. */}
          <Suspense fallback={<LoadingState />}>
            <Outlet />
          </Suspense>
        </Box>
      </Box>
    </Box>
  );
}
