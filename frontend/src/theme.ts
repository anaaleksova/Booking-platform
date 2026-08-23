import { createTheme } from '@mui/material/styles';

/**
 * One theme for both experiences — the dashboard and the public booking page —
 * so a business's customers see something coherent with the product.
 */
export const theme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: '#2f5bea', light: '#5b7ef0', dark: '#1e3fa8' },
    secondary: { main: '#0f9d76' },
    background: { default: '#f6f7fb', paper: '#ffffff' },
    success: { main: '#0f9d58' },
    warning: { main: '#e8a33d' },
    error: { main: '#d3453f' },
    divider: 'rgba(15, 23, 42, 0.10)',
    text: { primary: '#131a2b', secondary: '#5a6478' },
  },
  shape: { borderRadius: 12 },
  typography: {
    fontFamily: '"Inter", "Segoe UI", system-ui, -apple-system, sans-serif',
    h4: { fontWeight: 700, letterSpacing: '-0.02em' },
    h5: { fontWeight: 700, letterSpacing: '-0.01em' },
    h6: { fontWeight: 600 },
    button: { textTransform: 'none', fontWeight: 600 },
  },
  components: {
    MuiPaper: {
      styleOverrides: {
        // Flat surfaces with a hairline border read as more modern than
        // stacked shadows, and stay legible on the grey background.
        root: { backgroundImage: 'none' },
        outlined: { borderColor: 'rgba(15, 23, 42, 0.10)' },
      },
    },
    MuiCard: {
      defaultProps: { variant: 'outlined' },
    },
    MuiButton: {
      defaultProps: { disableElevation: true },
    },
    MuiTextField: {
      defaultProps: { size: 'small' },
    },
    MuiChip: {
      styleOverrides: { root: { fontWeight: 600 } },
    },
    MuiTableCell: {
      styleOverrides: {
        head: { fontWeight: 600, color: '#5a6478', backgroundColor: '#fafbfd' },
      },
    },
  },
});
