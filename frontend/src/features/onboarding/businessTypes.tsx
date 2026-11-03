import type { ReactNode } from 'react';
import BrushRoundedIcon from '@mui/icons-material/BrushRounded';
import CategoryRoundedIcon from '@mui/icons-material/CategoryRounded';
import ContentCutRoundedIcon from '@mui/icons-material/ContentCutRounded';
import DeskRoundedIcon from '@mui/icons-material/DeskRounded';
import FitnessCenterRoundedIcon from '@mui/icons-material/FitnessCenterRounded';
import RestaurantRoundedIcon from '@mui/icons-material/RestaurantRounded';
import SpaRoundedIcon from '@mui/icons-material/SpaRounded';
import type { BusinessType } from '@/types/api';

export interface BusinessTypeInfo {
  type: BusinessType;
  label: string;
  /** What the business starts with, shown on the sign-up card. */
  hint: string;
  icon: ReactNode;
  /** The checklist step about what gets booked: "Your tables", "Your team" … */
  resourcesStep: { title: string; to: string };
}

const TEAM = { title: 'Add your team', to: '/admin/employees' };

/** In the order the sign-up page shows them; "Something else" last. */
export const BUSINESS_TYPES: BusinessTypeInfo[] = [
  {
    type: 'RESTAURANT',
    label: 'Restaurant / Bar / Café',
    hint: 'Table reservations, no menu needed',
    icon: <RestaurantRoundedIcon />,
    resourcesStep: { title: 'Set up your tables', to: '/admin/resources' },
  },
  {
    type: 'HAIR_SALON',
    label: 'Hair salon / Barber',
    hint: 'Haircuts and colouring, booked with you',
    icon: <ContentCutRoundedIcon />,
    resourcesStep: TEAM,
  },
  {
    type: 'NAILS_BEAUTY',
    label: 'Nails / Beauty',
    hint: 'Manicure, pedicure and gel nails',
    icon: <BrushRoundedIcon />,
    resourcesStep: TEAM,
  },
  {
    type: 'SPA',
    label: 'Spa / Massage',
    hint: 'Treatments with your team and a room',
    icon: <SpaRoundedIcon />,
    resourcesStep: { title: 'Add your team and rooms', to: '/admin/employees' },
  },
  {
    type: 'FITNESS',
    label: 'Fitness / Classes',
    hint: 'Group classes with limited places',
    icon: <FitnessCenterRoundedIcon />,
    resourcesStep: { title: 'Check your studio', to: '/admin/resources' },
  },
  {
    type: 'COWORKING',
    label: 'Co-working',
    hint: 'Desks and meeting rooms',
    icon: <DeskRoundedIcon />,
    resourcesStep: { title: 'Set up your desks and rooms', to: '/admin/resources' },
  },
  {
    type: 'OTHER',
    label: 'Something else',
    hint: 'Start empty and set it up yourself',
    icon: <CategoryRoundedIcon />,
    resourcesStep: { title: 'Add what customers book', to: '/admin/resource-types' },
  },
];

/**
 * Events (a band, a quiz night) are for restaurants and bars. A business from
 * before types existed keeps them; the server applies the same rule.
 */
export function offersEvents(type: BusinessType | null | undefined): boolean {
  return !type || type === 'RESTAURANT';
}

export function businessTypeInfo(type: BusinessType | null | undefined): BusinessTypeInfo {
  return BUSINESS_TYPES.find((info) => info.type === type) ?? BUSINESS_TYPES[BUSINESS_TYPES.length - 1];
}
