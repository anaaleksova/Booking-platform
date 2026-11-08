import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { mediaUrl } from '@/features/media/api';
import { Reveal } from '@/features/public/Reveal';
import { useSiteData } from '../render/SiteData';
import type { PublicTeamMember, TeamMemberRef, TeamSection } from '../types';
import { SectionBody, SectionHeading, textOr, useImageShape, type SectionProps } from './shared';

/** The picked people, in the picked order, with their live name and photo. Ones no longer employed drop out. */
function useMembers(section: TeamSection): Array<PublicTeamMember & TeamMemberRef> {
  const { team } = useSiteData();
  const byId = new Map(team.map((member) => [member.id, member]));
  return (section.content.members ?? []).flatMap((ref) => {
    const member = byId.get(ref.employeeId);
    return member ? [{ ...member, ...ref }] : [];
  });
}

function useHeading(section: TeamSection) {
  return { eyebrow: textOr(section.content.eyebrow, 'Our team'), title: textOr(section.content.title, 'Meet the team') };
}

/** A portrait card per person. */
export function TeamCards({ section }: SectionProps<TeamSection>) {
  const shape = useImageShape();
  const members = useMembers(section);
  const { eyebrow, title } = useHeading(section);
  if (members.length === 0) {
    return null;
  }
  return (
    <SectionBody>
      <SectionHeading section={section} eyebrow={eyebrow} title={title} />
      <Grid container spacing={3} justifyContent="center">
        {members.map((member, index) => (
          <Grid item xs={6} sm={4} md={3} key={member.id}>
            <Reveal delay={(index % 4) * 90}>
              <Box sx={{ aspectRatio: '3 / 4', ...shape(56), overflow: 'hidden', bgcolor: 'var(--site-primary)', display: 'grid', placeItems: 'center' }}>
                {member.imageUrl ? (
                  <Box component="img" src={mediaUrl(member.imageUrl)} alt={member.name} loading="lazy" sx={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }} />
                ) : (
                  <Typography sx={{ fontSize: 56, fontWeight: 800, color: 'var(--site-on-primary)' }}>{member.name.charAt(0)}</Typography>
                )}
              </Box>
              <Typography variant="h6" component="h3" fontWeight={800} sx={{ mt: 1.5 }}>
                {member.name}
              </Typography>
              {member.role ? (
                <Typography variant="body2" sx={{ color: 'var(--site-accent)', fontWeight: 600 }}>
                  {member.role}
                </Typography>
              ) : null}
              {member.bio ? (
                <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>
                  {member.bio}
                </Typography>
              ) : null}
            </Reveal>
          </Grid>
        ))}
      </Grid>
    </SectionBody>
  );
}

/** One row per person: photo, name, role and bio. */
export function TeamList({ section }: SectionProps<TeamSection>) {
  const members = useMembers(section);
  const { eyebrow, title } = useHeading(section);
  if (members.length === 0) {
    return null;
  }
  return (
    <SectionBody maxWidth="md">
      <SectionHeading section={section} eyebrow={eyebrow} title={title} />
      <Stack spacing={2}>
        {members.map((member, index) => (
          <Reveal key={member.id} delay={index * 80}>
            <Paper variant="outlined" sx={{ p: 2.5, borderRadius: 'var(--site-radius-card, 56px)' }}>
              <Stack direction="row" spacing={2.5} alignItems="center">
                <Avatar src={mediaUrl(member.imageUrl)} alt={member.name} sx={{ width: 72, height: 72, bgcolor: 'var(--site-primary)', fontSize: 28, fontWeight: 800 }}>
                  {member.name.charAt(0)}
                </Avatar>
                <Box>
                  <Typography variant="h6" component="h3" fontWeight={800}>
                    {member.name}
                  </Typography>
                  {member.role ? (
                    <Typography variant="body2" sx={{ color: 'var(--site-accent)', fontWeight: 600 }}>
                      {member.role}
                    </Typography>
                  ) : null}
                  {member.bio ? (
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      {member.bio}
                    </Typography>
                  ) : null}
                </Box>
              </Stack>
            </Paper>
          </Reveal>
        ))}
      </Stack>
    </SectionBody>
  );
}
