import { Link as RouterLink } from 'react-router-dom';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';

/**
 * The bare domain. Deliberately lists no businesses: customers arrive through
 * the link their business shares, and each booking page shows only its own
 * business.
 */
export function HomePage() {
  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'grid',
        placeItems: 'center',
        p: 3,
        background: 'linear-gradient(160deg, #eef2ff 0%, #f6f7fb 50%, #f2fbf7 100%)',
      }}
    >
      <Stack spacing={2} alignItems="center" textAlign="center" sx={{ maxWidth: 520 }}>
        <Typography variant="h3" fontWeight={800}>
          Reservations, made simple
        </Typography>
        <Typography color="text.secondary">
          Looking to book? Use the link your business shared with you — on their website, Instagram or Google profile.
        </Typography>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ pt: 2 }}>
          <Button component={RouterLink} to="/login" variant="contained" size="large">
            Business login
          </Button>
          <Button component={RouterLink} to="/register" variant="outlined" size="large">
            Create a business account
          </Button>
        </Stack>
      </Stack>
    </Box>
  );
}
