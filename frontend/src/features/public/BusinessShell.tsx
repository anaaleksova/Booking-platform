import { useEffect, useState, type ReactNode } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Container from '@mui/material/Container';
import CssBaseline from '@mui/material/CssBaseline';
import IconButton from '@mui/material/IconButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { alpha } from '@mui/material/styles';
import FacebookRoundedIcon from '@mui/icons-material/FacebookRounded';
import InstagramIcon from '@mui/icons-material/Instagram';
import LanguageRoundedIcon from '@mui/icons-material/LanguageRounded';
import LogoutRoundedIcon from '@mui/icons-material/LogoutRounded';
import PersonRoundedIcon from '@mui/icons-material/PersonRounded';
import PhoneRoundedIcon from '@mui/icons-material/PhoneRounded';
import { useAuth } from '@/features/auth/AuthContext';
import { mediaUrl } from '@/features/media/api';
import type { PublicBusiness } from '@/types/api';
import { prefetchCustomerPages } from '@/routes/prefetch';
import { SiteThemeScope } from '@/features/site/render/SiteThemeScope';
import { readableOn, withAlpha } from '@/features/site/theme';
import { usePublicServices } from './api';
import { brandSiteTheme } from './brandTheme';
import { bookLabel } from './freeBooking';

/**
 * The frame of every customer page of one business: its colour, its logo and
 * name, and nothing that leads to any other business.
 *
 * The business's published theme is set here, as an MUI theme and as
 * `--site-*` CSS custom properties on the root, so the homepage, the booking
 * flow and the reservation page are all drawn in it.
 *
 * @param transparentHeader lets the landing page's cover photo run under the bar
 * @param stickyHeader      keeps the bar on screen while scrolling (when it is not transparent)
 */
export function BusinessShell({
  business,
  children,
  transparentHeader = false,
  stickyHeader = true,
  hideBookButton = false,
  footer = true,
}: {
  business?: PublicBusiness;
  children: ReactNode;
  transparentHeader?: boolean;
  stickyHeader?: boolean;
  hideBookButton?: boolean;
  footer?: boolean;
}) {
  const siteTheme = brandSiteTheme(business);

  useEffect(() => {
    prefetchCustomerPages();
  }, []);
  const home = business ? `/b/${business.slug}` : '/';

  return (
    <SiteThemeScope theme={siteTheme} sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <CssBaseline />
      <Box
        component="header"
        sx={{
          position: transparentHeader ? 'absolute' : stickyHeader ? 'sticky' : 'relative',
          top: 0,
          left: 0,
          right: 0,
          zIndex: 10,
          color: transparentHeader ? '#fff' : 'text.primary',
          bgcolor: transparentHeader ? 'transparent' : (t) => alpha(t.palette.background.paper, 0.92),
          backdropFilter: transparentHeader ? undefined : 'blur(8px)',
          borderBottom: 1,
          borderColor: transparentHeader ? 'rgba(255,255,255,0.18)' : 'divider',
        }}
      >
        <Container maxWidth="lg">
          <Stack direction="row" alignItems="center" spacing={2} sx={{ py: 1.5 }}>
            <Stack
              component={RouterLink}
              to={home}
              direction="row"
              alignItems="center"
              spacing={1.25}
              sx={{ color: 'inherit', textDecoration: 'none', minWidth: 0, flex: 1 }}
            >
              <Avatar
                src={mediaUrl(business?.logoUrl)}
                sx={{ width: 38, height: 38, bgcolor: 'primary.main', fontWeight: 700, border: transparentHeader ? '1px solid rgba(255,255,255,0.5)' : 'none' }}
              >
                {business?.name.charAt(0)}
              </Avatar>
              <Typography variant="subtitle1" fontWeight={800} noWrap>
                {business?.name}
              </Typography>
            </Stack>
            {business?.phone ? (
              <Button
                href={`tel:${business.phone.replace(/\s/g, '')}`}
                color="inherit"
                startIcon={<PhoneRoundedIcon fontSize="small" />}
                sx={{ display: { xs: 'none', md: 'inline-flex' }, fontWeight: 600 }}
              >
                {business.phone}
              </Button>
            ) : null}
            <AccountButton />
            {business && !hideBookButton ? (
              <Button
                component={RouterLink}
                to={`/b/${business.slug}/book`}
                variant="contained"
                sx={{
                  borderRadius: 'var(--site-radius-button, 999px)',
                  px: 2.5,
                  // Over the hero photo an outline or text button is drawn in white, as the hero's own are.
                  ...(transparentHeader && siteTheme.components.buttonStyle !== 'FILLED'
                    ? {
                        color: '#fff',
                        boxShadow: siteTheme.components.buttonStyle === 'OUTLINE' ? 'inset 0 0 0 2px #fff' : 'none',
                        '&:hover': { bgcolor: 'rgba(255,255,255,0.12)' },
                      }
                    : {}),
                }}
              >
                Book now
              </Button>
            ) : null}
          </Stack>
        </Container>
      </Box>

      <Box component="main" sx={{ flex: 1 }}>
        {children}
      </Box>

      {footer && business ? <Footer business={business} /> : null}
    </SiteThemeScope>
  );
}

/** Who is signed in, with sign-out. Nothing when signed out: sign-in happens while booking. */
function AccountButton() {
  const { user, logout } = useAuth();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  if (!user) {
    return null;
  }
  return (
    <>
      <IconButton color="inherit" onClick={(e) => setAnchor(e.currentTarget)} aria-label="Your account">
        <PersonRoundedIcon />
      </IconButton>
      <Menu anchorEl={anchor} open={Boolean(anchor)} onClose={() => setAnchor(null)} anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }} transformOrigin={{ vertical: 'top', horizontal: 'right' }}>
        <Box sx={{ px: 2, py: 1, maxWidth: 260 }}>
          <Typography variant="subtitle2" noWrap>
            {user.fullName}
          </Typography>
          <Typography variant="caption" color="text.secondary" noWrap display="block">
            {user.email}
          </Typography>
        </Box>
        <MenuItem
          onClick={() => {
            setAnchor(null);
            logout();
          }}
        >
          <ListItemIcon>
            <LogoutRoundedIcon fontSize="small" />
          </ListItemIcon>
          Sign out
        </MenuItem>
      </Menu>
    </>
  );
}

function Footer({ business }: { business: PublicBusiness }) {
  const services = usePublicServices(business.slug);
  // Text on the footer band at an opacity, white on a dark band and black on a light one.
  const onFooter = (alpha: number) => withAlpha(readableOn(brandSiteTheme(business).colors.secondary), alpha);
  const socials = [
    { href: business.instagramUrl, label: 'Instagram', icon: <InstagramIcon /> },
    { href: business.facebookUrl, label: 'Facebook', icon: <FacebookRoundedIcon /> },
    { href: business.websiteUrl, label: 'Website', icon: <LanguageRoundedIcon /> },
  ].filter((social) => social.href);

  return (
    <Box component="footer" sx={{ bgcolor: 'var(--site-secondary)', color: onFooter(0.75), py: 6, textAlign: 'center', borderTop: 1, borderColor: onFooter(0.08) }}>
      <Container maxWidth="sm">
        <Typography variant="h5" sx={{ color: 'var(--site-on-secondary)', fontWeight: 800 }}>
          {business.name}
        </Typography>
        <Typography variant="body2" sx={{ mt: 1 }}>
          {[business.address, business.phone].filter(Boolean).join(' · ')}
        </Typography>
        {socials.length ? (
          <Stack direction="row" justifyContent="center" spacing={1} sx={{ mt: 2 }}>
            {socials.map((social) => (
              <IconButton
                key={social.label}
                href={social.href!}
                target="_blank"
                rel="noopener noreferrer"
                aria-label={social.label}
                sx={{ color: onFooter(0.8) }}
              >
                {social.icon}
              </IconButton>
            ))}
          </Stack>
        ) : null}
        <Button
          component={RouterLink}
          to={`/b/${business.slug}/book`}
          variant="contained"
          size="large"
          sx={{ mt: 3, borderRadius: 'var(--site-radius-button, 999px)', px: 4 }}
        >
          {bookLabel(services.data)}
        </Button>
        <Typography variant="caption" display="block" sx={{ mt: 4, opacity: 0.5 }}>
          Online reservations
        </Typography>
      </Container>
    </Box>
  );
}
