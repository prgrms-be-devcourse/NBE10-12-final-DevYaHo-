"use client";

import { useCallback, useEffect, useState } from "react";
import { Plus, Tag, Save } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Banner } from "@/components/ui/Banner";
import { EmptyState } from "@/components/ui/EmptyState";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { CategoryFormModal } from "@/components/admin/CategoryFormModal";
import { CategoryTreeNode } from "@/components/admin/CategoryTreeNode";
import {
  adminCreateCategory,
  adminDeleteCategory,
  adminUpdateCategory,
  listCategories,
  adminReorderCategories,
} from "@/lib/api/category";
import { ApiError } from "@/lib/api/http";
import type { CategoryTreeResponse } from "@/lib/api/types";

// dnd-kit imports
import { DndContext, PointerSensor, useSensor, useSensors, DragEndEvent, closestCenter } from "@dnd-kit/core";
import { SortableContext, verticalListSortingStrategy, arrayMove } from "@dnd-kit/sortable";

export default function AdminCategoryPage() {
  const [tree, setTree] = useState<CategoryTreeResponse[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  // 드래그 앤 드롭 상태
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);
  const [savingOrder, setSavingOrder] = useState(false);

  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }));

  const [createModal, setCreateModal] = useState<{ open: boolean; parentId: number | null; nextSortOrder: number }>({
    open: false,
    parentId: null,
    nextSortOrder: 1,
  });

  const [editModal, setEditModal] = useState<{ open: boolean; id: number; name: string; sortOrder: number }>({
    open: false,
    id: 0,
    name: "",
    sortOrder: 0,
  });

  const [deleteDialog, setDeleteDialog] = useState<{ open: boolean; id: number; name: string }>({
    open: false,
    id: 0,
    name: "",
  });

  const loadTree = useCallback(async () => {
    setLoadError(null);
    try {
      const data = await listCategories();
      setTree(data);
      setHasUnsavedChanges(false);
    } catch {
      setLoadError("카테고리를 불러오는 중 오류가 발생했습니다.");
    }
  }, []);

  useEffect(() => {
    loadTree();
  }, [loadTree]);

  // 드래그 종료 처리
  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id || !tree) return;

    setTree((prevTree) => {
      if (!prevTree) return prevTree;

      const newTree = JSON.parse(JSON.stringify(prevTree)) as CategoryTreeResponse[];

      // 최상위 그룹에서 이동했는지 확인
      const topLevelActiveIndex = newTree.findIndex((n) => n.id === active.id);
      const topLevelOverIndex = newTree.findIndex((n) => n.id === over.id);

      if (topLevelActiveIndex !== -1 && topLevelOverIndex !== -1) {
        const moved = arrayMove(newTree, topLevelActiveIndex, topLevelOverIndex);
        moved.forEach((n, idx) => (n.sortOrder = idx + 1));
        setHasUnsavedChanges(true);
        return moved;
      }

      // 특정 부모의 자식 그룹 안에서 이동했는지 확인
      for (const parent of newTree) {
        const childActiveIndex = parent.children.findIndex((c) => c.id === active.id);
        const childOverIndex = parent.children.findIndex((c) => c.id === over.id);

        if (childActiveIndex !== -1 && childOverIndex !== -1) {
          parent.children = arrayMove(parent.children, childActiveIndex, childOverIndex);
          parent.children.forEach((c, idx) => (c.sortOrder = idx + 1));
          setHasUnsavedChanges(true);
          return newTree;
        }
      }

      return prevTree;
    });
  };

  // 변경된 전체 순서 저장
  const handleSaveOrder = async () => {
    if (!tree) return;
    setSavingOrder(true);
    setActionError(null);
    try {
      const payload: { id: number; sortOrder: number }[] = [];
      for (const root of tree) {
        payload.push({ id: root.id, sortOrder: root.sortOrder });
        for (const child of root.children) {
          payload.push({ id: child.id, sortOrder: child.sortOrder });
        }
      }
      await adminReorderCategories(payload);
      await loadTree();
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : "순서 저장 중 오류가 발생했습니다.");
    } finally {
      setSavingOrder(false);
    }
  };

  const handleCreate = async (categoryName: string, sortOrder: number) => {
    await adminCreateCategory({ parentId: createModal.parentId, categoryName, sortOrder });
    await loadTree();
  };

  const handleUpdate = async (categoryName: string, sortOrder: number) => {
    await adminUpdateCategory(editModal.id, { categoryName, sortOrder });
    await loadTree();
  };

  const handleDelete = async () => {
    setActionError(null);
    try {
      await adminDeleteCategory(deleteDialog.id);
      await loadTree();
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : "삭제 중 오류가 발생했습니다.");
    }
  };

  return (
    <div className="mx-auto max-w-5xl space-y-6 px-6 py-9">
      {/* 헤더 */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-xs font-bold tracking-wide text-wb-green">CATEGORY</p>
          <h1 className="mt-1 text-3xl font-bold">카테고리 관리</h1>
          <p className="mt-1 text-sm text-wb-secondary">최대 2뎁스(대분류 / 소분류)로 구성됩니다. 드래그 앤 드롭으로 노출 순서를 변경할 수 있습니다.</p>
        </div>
        <div className="flex items-center gap-2">
          {hasUnsavedChanges && (
            <Button variant="primary" onClick={handleSaveOrder} disabled={savingOrder}>
              <Save className="h-4 w-4" />
              {savingOrder ? "저장 중..." : "순서 저장"}
            </Button>
          )}
          <Button
            onClick={() => {
              const nextOrder = tree && tree.length > 0 ? Math.max(...tree.map((n) => n.sortOrder)) + 1 : 1;
              setCreateModal({ open: true, parentId: null, nextSortOrder: nextOrder });
            }}
          >
            <Plus className="h-4 w-4" />
            최상위 추가
          </Button>
        </div>
      </div>

      {actionError && <Banner tone="error">{actionError}</Banner>}

      {/* 트리 목록 */}
      {loadError ? (
        <Banner tone="error">{loadError}</Banner>
      ) : tree === null ? (
        <p className="text-sm text-wb-secondary">불러오는 중...</p>
      ) : tree.length === 0 ? (
        <EmptyState icon={Tag} title="카테고리 없음" message="등록된 카테고리가 없습니다." />
      ) : (
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
          <div className="rounded-xl border border-wb-line bg-wb-surface p-2">
            <SortableContext items={tree.map((t) => t.id)} strategy={verticalListSortingStrategy}>
              {tree.map((node) => (
                <CategoryTreeNode
                  key={node.id}
                  node={node}
                  depth={1}
                  onAddChild={(parentId) => {
                    const parent = tree.find((n) => n.id === parentId);
                    const nextOrder = parent && parent.children.length > 0 
                                      ? Math.max(...parent.children.map((c) => c.sortOrder)) + 1 
                                      : 1;
                    setCreateModal({ open: true, parentId, nextSortOrder: nextOrder });
                  }}
                  onEdit={(id, name, sortOrder) => setEditModal({ open: true, id, name, sortOrder })}
                  onDelete={(id, name) => setDeleteDialog({ open: true, id, name })}
                />
              ))}
            </SortableContext>
          </div>
        </DndContext>
      )}

      {/* 모달스 */}
      <CategoryFormModal
        open={createModal.open}
        onClose={() => setCreateModal({ open: false, parentId: null, nextSortOrder: 1 })}
        title={createModal.parentId ? "하위 카테고리 추가" : "최상위 카테고리 추가"}
        initialSortOrder={createModal.nextSortOrder}
        onSubmit={handleCreate}
      />

      <CategoryFormModal
        open={editModal.open}
        onClose={() => setEditModal({ open: false, id: 0, name: "", sortOrder: 0 })}
        title="카테고리 수정"
        initialName={editModal.name}
        initialSortOrder={editModal.sortOrder}
        onSubmit={handleUpdate}
      />

      <ConfirmDialog
        open={deleteDialog.open}
        onClose={() => setDeleteDialog({ open: false, id: 0, name: "" })}
        onConfirm={handleDelete}
        title="카테고리 삭제"
        message={`'${deleteDialog.name}' 카테고리를 삭제하시겠습니까?\n하위 카테고리나 등록된 상품이 있으면 삭제할 수 없습니다.`}
        confirmLabel="삭제"
        destructive
      />
    </div>
  );
}
