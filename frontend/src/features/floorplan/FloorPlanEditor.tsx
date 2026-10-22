import { useEffect, useRef, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import { errorMessage } from '@/api/client';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { LoadingState } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import { useFloorPlans, useResources, useSaveFloorPlans } from '@/features/catalog/api';
import type { FloorPlan, Resource, ResourceType } from '@/types/api';
import { DecorShape, FloorPlanScene, TableShape, decorDrawOrder } from './FloorPlanScene';
import { decorKind } from './editor/decorKinds';
import { FloorTabs, NoFloors, PieceControls, TableControls } from './editor/EditorControls';
import { MAX_FLOORS, floorNamesProblem, type FloorDraft } from './editor/editorModel';
import { FloorOverview } from './editor/FloorOverview';
import { useDragOnPlan } from './editor/useDragOnPlan';
import { useFloorDrafts } from './editor/useFloorDrafts';

/**
 * Draw the place once — one plan per floor or area: lay out the room (areas,
 * walls, bar, plants, entrance) and drag each table, desk or court to where it
 * really is. Customers see these drawings, with what is free at their time,
 * when they choose.
 */
export function FloorPlanEditor({ type, onClose }: { type: ResourceType | null; onClose: () => void }) {
  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down('md'));
  const plans = useFloorPlans(type?.id);
  const resources = useResources();

  return (
    <Dialog
      open={type !== null}
      onClose={onClose}
      maxWidth="xl"
      fullWidth
      fullScreen={fullScreen}
      // Wide screens: the dialog takes the window's height and the map scales into it,
      // so nothing but the side panel ever scrolls.
      PaperProps={{ sx: { height: { md: 'calc(100% - 64px)' } } }}
    >
      <DialogTitle>Floor plans · {type?.name}</DialogTitle>
      {type && plans.isSuccess && resources.isSuccess ? (
        <EditorBody
          key={type.id}
          type={type}
          plans={plans.data}
          resources={resources.data.filter((resource) => resource.resourceTypeId === type.id)}
          onClose={onClose}
        />
      ) : (
        <DialogContent>
          <LoadingState />
        </DialogContent>
      )}
    </Dialog>
  );
}

function EditorBody({
  type,
  plans,
  resources,
  onClose,
}: {
  type: ResourceType;
  plans: FloorPlan[];
  resources: Resource[];
  onClose: () => void;
}) {
  const toast = useToast();
  const save = useSaveFloorPlans();
  const theme = useTheme();
  /** Map and panel side by side, fitted to the window; below this they stack and scroll. */
  const wide = useMediaQuery(theme.breakpoints.up('md'));
  const drafts = useFloorDrafts(plans, resources);
  const { floors, floor, aspectRatio, items, decor, byId, selection, setSelection, selectedTable, selectedPiece } = drafts;
  const [deletingFloor, setDeletingFloor] = useState<FloorDraft | null>(null);
  const [focusName, setFocusName] = useState(false);
  const svgRef = useRef<SVGSVGElement>(null);
  const nameRef = useRef<HTMLInputElement>(null);
  const dragProps = useDragOnPlan(svgRef, drafts);

  // A new floor's name is selected so it can be typed over straight away.
  useEffect(() => {
    if (focusName && nameRef.current) {
      nameRef.current.focus();
      nameRef.current.select();
      setFocusName(false);
    }
  }, [focusName, floor?.key]);

  const addFloor = () => {
    drafts.addFloor();
    setFocusName(true);
  };

  const deleteFloor = (target: FloorDraft) => {
    drafts.deleteFloor(target);
    setDeletingFloor(null);
  };

  const submit = async () => {
    const problem = floorNamesProblem(floors);
    if (problem) {
      if (problem.floorKey !== undefined) {
        drafts.switchFloor(problem.floorKey);
        setFocusName(true);
      }
      toast.error(problem.message);
      return;
    }
    try {
      await save.mutateAsync({
        typeId: type.id,
        plans: floors.map((draft) => ({
          id: draft.id,
          name: draft.name.trim(),
          aspectRatio: draft.aspectRatio,
          items: draft.items,
          decor: draft.decor.map(({ key: _key, ...piece }) => piece),
        })),
      });
      toast.success(floors.length ? 'Floor plans saved' : 'Floor plans removed');
      onClose();
    } catch (error) {
      toast.error(errorMessage(error));
    }
  };

  const typeName = type.name.toLowerCase();

  return (
    <>
      <DialogContent dividers sx={{ display: 'flex', flexDirection: 'column', overflow: wide ? 'hidden' : 'auto' }}>
        <FloorTabs
          floors={floors}
          activeKey={floor?.key}
          onSelect={drafts.switchFloor}
          onAdd={addFloor}
          canAdd={floors.length < MAX_FLOORS}
        />

        {floor ? (
          <Stack
            direction={{ xs: 'column', md: 'row' }}
            spacing={3}
            alignItems={wide ? 'stretch' : 'flex-start'}
            sx={{ mt: 2, ...(wide ? { flex: 1, minHeight: 0 } : {}) }}
          >
            <Box
              sx={{
                flex: 1,
                minWidth: 0,
                width: '100%',
                position: 'relative',
                ...(wide ? { display: 'flex', flexDirection: 'column', minHeight: 0 } : {}),
              }}
            >
              {items.length === 0 && decor.length === 0 ? (
                <Stack
                  alignItems="center"
                  justifyContent="center"
                  spacing={1}
                  sx={{ position: 'absolute', inset: 0, bottom: 28, zIndex: 1, pointerEvents: 'none', textAlign: 'center', px: 4 }}
                >
                  <Box sx={{ bgcolor: 'background.paper', borderRadius: 3, boxShadow: 2, px: 3, py: 2, maxWidth: 380 }}>
                    <Typography fontWeight={700}>“{floor.name.trim() || 'This floor'}” is empty</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {drafts.unplaced.length
                        ? `Use “Place” on the right to put ${typeName}s here`
                        : drafts.elsewhere.length
                          ? `Use “Move here” on the right to bring ${typeName}s from another floor`
                          : `Add ${typeName} resources first, then place them here`}
                      , and add walls, a bar or plants to draw the room.
                    </Typography>
                  </Box>
                </Stack>
              ) : null}
              <Box sx={wide ? { flex: 1, minHeight: 0 } : undefined}>
                <FloorPlanScene
                  ref={svgRef}
                  aspectRatio={aspectRatio}
                  minWidth={560}
                  fill={wide}
                  label={`Floor plan of ${floor.name}`}
                  onBackgroundPointerDown={() => setSelection(null)}
                >
                  {decorDrawOrder(decor).map((piece) => (
                    <DecorShape
                      key={piece.key}
                      decor={piece}
                      aspectRatio={aspectRatio}
                      selected={selection?.type === 'decor' && selection.key === piece.key}
                      groupProps={dragProps({ type: 'decor', key: piece.key }, piece, piece.label || decorKind(piece.kind).label)}
                    />
                  ))}
                  {items.map((item) => {
                    const resource = byId.get(item.resourceId);
                    const isSelected = selection?.type === 'table' && selection.id === item.resourceId;
                    return (
                      <TableShape
                        key={item.resourceId}
                        spot={item}
                        aspectRatio={aspectRatio}
                        name={resource?.name ?? '?'}
                        caption={resource?.capacity ? `${resource.capacity} seats` : undefined}
                        seats={resource?.capacity ?? null}
                        state={isSelected ? 'editSelected' : 'edit'}
                        groupProps={dragProps({ type: 'table', id: item.resourceId }, item, resource?.name ?? 'Table')}
                      />
                    );
                  })}
                </FloorPlanScene>
              </Box>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1, flexShrink: 0 }}>
                Drag anything to move it. Select it to change its shape, size or name; arrow keys nudge (Shift for bigger steps), Delete removes.
              </Typography>
            </Box>

            <Stack
              spacing={2.5}
              sx={{ width: { xs: '100%', md: 280, lg: 320 }, flexShrink: 0, ...(wide ? { overflowY: 'auto', minHeight: 0, pr: 0.5, pb: 1 } : {}) }}
            >
              {selectedTable ? (
                <TableControls
                  item={selectedTable}
                  resource={byId.get(selectedTable.resourceId)}
                  aspectRatio={aspectRatio}
                  onChange={(change) => drafts.updateTable(selectedTable.resourceId, change)}
                  onTurn={() => drafts.turn({ type: 'table', id: selectedTable.resourceId })}
                  onRemove={() => drafts.remove(selection)}
                  onDone={() => setSelection(null)}
                />
              ) : selectedPiece ? (
                <PieceControls
                  piece={selectedPiece}
                  onChange={(change) => drafts.updatePiece(selectedPiece.key, change)}
                  onTurn={() => drafts.turn({ type: 'decor', key: selectedPiece.key })}
                  onRemove={() => drafts.remove(selection)}
                  onDone={() => setSelection(null)}
                />
              ) : (
                <FloorOverview
                  type={type}
                  floor={floor}
                  drafts={drafts}
                  resourceCount={resources.length}
                  nameRef={nameRef}
                  onDeleteFloor={(target) =>
                    target.items.length || target.decor.length ? setDeletingFloor(target) : deleteFloor(target)
                  }
                />
              )}
            </Stack>
          </Stack>
        ) : (
          <NoFloors typeName={type.name} onAdd={addFloor} />
        )}

        {floors.length > 0 && floors.every((draft) => draft.items.length === 0) && plans.length > 0 ? (
          <Alert severity="info" sx={{ mt: 2, flexShrink: 0 }}>
            With nothing placed on any floor, customers choose from the list as before.
          </Alert>
        ) : null}
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={submit} disabled={save.isPending}>
          Save
        </Button>
      </DialogActions>

      <ConfirmDialog
        open={deletingFloor !== null}
        title={`Delete “${deletingFloor?.name.trim() || 'this floor'}”?`}
        message={
          deletingFloor?.items.length
            ? `Its ${deletingFloor.items.length} placed ${deletingFloor.items.length === 1 ? 'resource goes' : 'resources go'} back to “Not on any floor”. Nothing is deleted until you save.`
            : 'Its drawing is removed. Nothing is deleted until you save.'
        }
        confirmLabel="Delete floor"
        onConfirm={() => deletingFloor && deleteFloor(deletingFloor)}
        onClose={() => setDeletingFloor(null)}
      />
    </>
  );
}
