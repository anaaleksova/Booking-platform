import Box from '@mui/material/Box';
import LinearProgress from '@mui/material/LinearProgress';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

/**
 * Chart building blocks.
 *
 * <p>Every chart in the admin panel plots a single series, so one validated
 * hue carries it (categorical slot 1; passes the lightness, chroma and 3:1
 * contrast checks on the light surface) and the card title names what is
 * plotted — no legend box needed. Marks stay thin, the grid is a solid
 * hairline, and all text uses text colours, never the series colour.
 */
export const CHART = {
  series: '#2a78d6',
  seriesWash: 'rgba(42, 120, 214, 0.10)',
  track: '#cde2fb',
  grid: '#e8e9ee',
  axisText: '#5a6478',
  text: '#131a2b',
};

const AXIS_TICK = { fill: CHART.axisText, fontSize: 12 };

export function ChartCard({
  title,
  subtitle,
  action,
  children,
}: {
  title: string;
  subtitle?: string;
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <Paper variant="outlined" sx={{ p: { xs: 2, md: 2.5 }, height: '100%' }}>
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={2} sx={{ mb: 2 }}>
        <Box>
          <Typography variant="subtitle1" fontWeight={700}>
            {title}
          </Typography>
          {subtitle ? (
            <Typography variant="body2" color="text.secondary">
              {subtitle}
            </Typography>
          ) : null}
        </Box>
        {action}
      </Stack>
      {children}
    </Paper>
  );
}

function TooltipBox({ title, lines }: { title: string; lines: Array<[string, string]> }) {
  return (
    <Paper variant="outlined" sx={{ px: 1.5, py: 1, boxShadow: '0 4px 16px rgba(15,23,42,0.08)' }}>
      <Typography variant="caption" color="text.secondary" display="block">
        {title}
      </Typography>
      {lines.map(([label, value]) => (
        <Stack key={label} direction="row" spacing={1.5} justifyContent="space-between" alignItems="center">
          <Stack direction="row" spacing={0.75} alignItems="center">
            <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: CHART.series }} />
            <Typography variant="body2">{label}</Typography>
          </Stack>
          <Typography variant="body2" fontWeight={700}>
            {value}
          </Typography>
        </Stack>
      ))}
    </Paper>
  );
}

export interface SeriesPoint {
  /** Axis label, already formatted. */
  label: string;
  /** Tooltip heading, already formatted. */
  detail: string;
  value: number;
  /** Optional second line in the tooltip, e.g. a reservation count. */
  extra?: string;
}

/** Change over time: a 2px line over a light wash, with a crosshair tooltip. */
export function TrendChart({
  data,
  valueLabel,
  formatValue,
  formatAxis,
  height = 240,
}: {
  data: SeriesPoint[];
  valueLabel: string;
  formatValue: (value: number) => string;
  formatAxis?: (value: number) => string;
  height?: number;
}) {
  return (
    <Box sx={{ width: '100%', height }}>
      <ResponsiveContainer>
        <AreaChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
          <CartesianGrid stroke={CHART.grid} vertical={false} />
          <XAxis
            dataKey="label"
            tick={AXIS_TICK}
            tickLine={false}
            axisLine={{ stroke: CHART.grid }}
            minTickGap={24}
          />
          <YAxis
            tick={AXIS_TICK}
            tickLine={false}
            axisLine={false}
            width={64}
            allowDecimals={false}
            tickFormatter={formatAxis ?? formatValue}
          />
          <Tooltip
            cursor={{ stroke: CHART.axisText, strokeWidth: 1 }}
            content={({ active, payload }) => {
              if (!active || !payload?.length) {
                return null;
              }
              const point = payload[0].payload as SeriesPoint;
              const lines: Array<[string, string]> = [[valueLabel, formatValue(point.value)]];
              return <TooltipBox title={point.extra ? `${point.detail} · ${point.extra}` : point.detail} lines={lines} />;
            }}
          />
          <Area
            type="monotone"
            dataKey="value"
            stroke={CHART.series}
            strokeWidth={2}
            fill={CHART.seriesWash}
            activeDot={{ r: 5, stroke: '#ffffff', strokeWidth: 2, fill: CHART.series }}
            dot={false}
            isAnimationActive={false}
          />
        </AreaChart>
      </ResponsiveContainer>
    </Box>
  );
}

/** Counts per period: thin columns with rounded data-ends, 2px apart at most densities. */
export function ColumnChart({
  data,
  valueLabel,
  formatValue = (value) => String(value),
  height = 240,
}: {
  data: SeriesPoint[];
  valueLabel: string;
  formatValue?: (value: number) => string;
  height?: number;
}) {
  return (
    <Box sx={{ width: '100%', height }}>
      <ResponsiveContainer>
        <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }} barCategoryGap={2}>
          <CartesianGrid stroke={CHART.grid} vertical={false} />
          <XAxis
            dataKey="label"
            tick={AXIS_TICK}
            tickLine={false}
            axisLine={{ stroke: CHART.grid }}
            minTickGap={24}
          />
          <YAxis tick={AXIS_TICK} tickLine={false} axisLine={false} width={40} allowDecimals={false} />
          <Tooltip
            cursor={{ fill: 'rgba(15,23,42,0.04)' }}
            content={({ active, payload }) => {
              if (!active || !payload?.length) {
                return null;
              }
              const point = payload[0].payload as SeriesPoint;
              return (
                <TooltipBox
                  title={point.extra ? `${point.detail} · ${point.extra}` : point.detail}
                  lines={[[valueLabel, formatValue(point.value)]]}
                />
              );
            }}
          />
          <Bar dataKey="value" fill={CHART.series} maxBarSize={24} radius={[4, 4, 0, 0]} isAnimationActive={false} />
        </BarChart>
      </ResponsiveContainer>
    </Box>
  );
}

export interface RankedItem {
  key: string | number;
  label: string;
  sublabel?: string;
  value: number;
  /** The value as shown at the bar's end. */
  display: string;
}

/**
 * A ranked list as horizontal bars — labels on the left, value at the tip.
 * Plain HTML rather than a charting library: it stays readable at any width
 * and every value is visible without hovering.
 */
export function RankedBars({ items, max }: { items: RankedItem[]; max?: number }) {
  const top = max ?? Math.max(1, ...items.map((item) => item.value));
  return (
    <Stack spacing={1.5} component="ol" sx={{ listStyle: 'none', p: 0, m: 0 }}>
      {items.map((item) => (
        <Box component="li" key={item.key}>
          <Stack direction="row" justifyContent="space-between" alignItems="baseline" spacing={2}>
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="body2" fontWeight={600} noWrap>
                {item.label}
              </Typography>
              {item.sublabel ? (
                <Typography variant="caption" color="text.secondary">
                  {item.sublabel}
                </Typography>
              ) : null}
            </Box>
            <Typography variant="body2" fontWeight={700} sx={{ fontVariantNumeric: 'tabular-nums' }}>
              {item.display}
            </Typography>
          </Stack>
          <LinearProgress
            variant="determinate"
            value={Math.min(100, (item.value / top) * 100)}
            aria-label={`${item.label}: ${item.display}`}
            sx={{
              mt: 0.75,
              height: 8,
              borderRadius: 4,
              bgcolor: CHART.track,
              '& .MuiLinearProgress-bar': { bgcolor: CHART.series, borderRadius: 4 },
            }}
          />
        </Box>
      ))}
    </Stack>
  );
}
