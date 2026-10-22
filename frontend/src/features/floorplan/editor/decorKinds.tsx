import type { ReactNode } from 'react';
import DoorFrontOutlinedIcon from '@mui/icons-material/DoorFrontOutlined';
import LocalBarOutlinedIcon from '@mui/icons-material/LocalBarOutlined';
import SpaOutlinedIcon from '@mui/icons-material/SpaOutlined';
import SquareFootOutlinedIcon from '@mui/icons-material/SquareFootOutlined';
import ViewQuiltOutlinedIcon from '@mui/icons-material/ViewQuiltOutlined';
import type { DecorKind } from '@/types/api';

/** The room pieces an owner can draw: not bookable, they help guests find their way. */
export const DECOR_KINDS: { kind: DecorKind; label: string; icon: ReactNode; hint: string }[] = [
  { kind: 'ZONE', label: 'Area', icon: <ViewQuiltOutlinedIcon />, hint: 'A tinted part of the room — terrace, kitchen, lounge' },
  { kind: 'WALL', label: 'Wall', icon: <SquareFootOutlinedIcon />, hint: 'A wall or partition' },
  { kind: 'COUNTER', label: 'Bar', icon: <LocalBarOutlinedIcon />, hint: 'A bar or reception counter' },
  { kind: 'PLANT', label: 'Plant', icon: <SpaOutlinedIcon />, hint: 'A plant' },
  { kind: 'ENTRANCE', label: 'Entrance', icon: <DoorFrontOutlinedIcon />, hint: 'Where guests come in' },
];

export function decorKind(kind: DecorKind) {
  return DECOR_KINDS.find((option) => option.kind === kind)!;
}
