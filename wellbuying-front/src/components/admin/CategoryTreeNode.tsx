"use client";

import { useState } from "react";
import { ChevronDown, ChevronRight, GripVertical, Pencil, Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/Button";
import type { CategoryTreeResponse } from "@/lib/api/types";
import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { SortableContext, verticalListSortingStrategy } from "@dnd-kit/sortable";

type Props = {
  node: CategoryTreeResponse;
  depth: number;
  onAddChild: (parentId: number) => void;
  onEdit: (id: number, currentName: string, currentSortOrder: number) => void;
  onDelete: (id: number, name: string) => void;
};

export function CategoryTreeNode({ node, depth, onAddChild, onEdit, onDelete }: Props) {
  const [expanded, setExpanded] = useState(true);
  const hasChildren = node.children && node.children.length > 0;

  // dnd-kit sortable 훅
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: node.id });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  };

  return (
    <div ref={setNodeRef} style={style} className="select-none">
      <div
        className={`group flex items-center gap-2 rounded-lg px-2 py-2 hover:bg-wb-canvas ${
          depth === 1 ? "font-semibold" : "ml-6 font-normal text-wb-secondary"
        }`}
      >
        {/* 드래그 핸들 (hover 시 강조) */}
        <button
          {...attributes}
          {...listeners}
          className="flex h-5 w-5 cursor-grab items-center justify-center text-wb-secondary/50 hover:text-wb-primary focus:outline-none"
        >
          <GripVertical className="h-4 w-4" />
        </button>

        <button
          className="flex h-5 w-5 shrink-0 items-center justify-center text-wb-secondary"
          onClick={() => setExpanded((v) => !v)}
          disabled={!hasChildren}
        >
          {hasChildren ? (
            expanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />
          ) : (
            <span className="h-4 w-4" />
          )}
        </button>

        <span className="flex-1 text-sm">
          {node.categoryName} <span className="ml-1 text-xs font-normal text-wb-secondary opacity-50">({node.sortOrder})</span>
        </span>

        <div className="flex shrink-0 items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100 focus-within:opacity-100">
          {depth === 1 && (
            <Button variant="secondary" className="h-7 px-2 text-xs" onClick={() => onAddChild(node.id)}>
              <Plus className="h-3 w-3" />
              하위
            </Button>
          )}
          <Button
            variant="secondary"
            className="h-7 px-2 text-xs"
            onClick={() => onEdit(node.id, node.categoryName, node.sortOrder)}
          >
            <Pencil className="h-3 w-3" />
            수정
          </Button>
          <Button
            variant="secondary"
            className="h-7 px-2 text-xs text-red-500 hover:text-red-600"
            onClick={() => onDelete(node.id, node.categoryName)}
          >
            <Trash2 className="h-3 w-3" />
            삭제
          </Button>
        </div>
      </div>

      {/* 자식 요소 랜더링 (SortableContext로 감싸서 내부에서 드래그 가능하게 구성) */}
      {hasChildren && expanded && (
        <div className="ml-2 mt-1 flex flex-col gap-1 border-l border-wb-line/50 pl-2">
          <SortableContext items={node.children.map((c) => c.id)} strategy={verticalListSortingStrategy}>
            {node.children.map((child) => (
              <CategoryTreeNode
                key={child.id}
                node={child}
                depth={depth + 1}
                onAddChild={onAddChild}
                onEdit={onEdit}
                onDelete={onDelete}
              />
            ))}
          </SortableContext>
        </div>
      )}
    </div>
  );
}
