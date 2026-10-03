import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { Link as RouterLink } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 3 }}>
      <Stack spacing={2} alignItems="center" textAlign="center">
        <Typography variant="h4">Page not found</Typography>
        <Typography color="text.secondary">
          The page you were looking for does not exist or has moved.
        </Typography>
        <Button component={RouterLink} to="/" variant="contained">
          Go to the home page
        </Button>
      </Stack>
    </Box>
  );
}
