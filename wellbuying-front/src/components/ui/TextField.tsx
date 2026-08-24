"use client";

import { InputHTMLAttributes } from "react";

type TextFieldProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
};

export function TextField({ label, id, className = "", ...props }: TextFieldProps) {
  const inputId = id ?? label;
  return (
    <label htmlFor={inputId} className="flex flex-col gap-1.5">
      <span className="text-xs font-bold text-wb-ink">{label}</span>
      <input
        id={inputId}
        className={`h-11 rounded-lg border border-wb-line bg-wb-canvas px-3 text-sm text-wb-ink placeholder:text-wb-secondary focus:border-wb-green focus:outline-none focus:ring-1 focus:ring-wb-green disabled:opacity-60 ${className}`}
        {...props}
      />
    </label>
  );
}
