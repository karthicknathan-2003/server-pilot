import * as React from "react";
import { cva } from "class-variance-authority";
import { cn } from "../../lib/utils";

const badgeVariants = cva(
  "inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium transition-colors focus:outline-none focus:ring-1 focus:ring-ring focus:ring-offset-2",
  {
    variants: {
      variant: {
        default: "border-blue-500/30 bg-blue-500/10 text-blue-400 border",
        secondary: "border-white/10 bg-white/10 text-white/70 border",
        destructive: "border-red-500/30 bg-red-500/10 text-red-400 border",
        success: "border-emerald-500/30 bg-emerald-500/10 text-emerald-400 border",
        warning: "border-amber-500/30 bg-amber-500/10 text-amber-400 border",
        outline: "border-white/20 text-white/60 border",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
);

function Badge({ className, variant, status, children, ...props }) {
  // If status is provided, auto-map status string ("online", "running", "offline", "stopped", etc.)
  let resolvedVariant = variant;
  let dotColor = null;

  if (status) {
    const s = String(status).toLowerCase();
    if (s === "online" || s === "running" || s === "active") {
      resolvedVariant = "success";
      dotColor = "bg-emerald-500";
    } else if (s === "offline" || s === "stopped" || s === "inactive" || s === "failed") {
      resolvedVariant = "destructive";
      dotColor = "bg-red-500";
    } else if (s === "building" || s === "deploying" || s === "starting") {
      resolvedVariant = "warning";
      dotColor = "bg-amber-500";
    } else {
      resolvedVariant = "secondary";
      dotColor = "bg-gray-400";
    }
  }

  return (
    <div className={cn(badgeVariants({ variant: resolvedVariant }), className)} {...props}>
      {dotColor && <span className={cn("h-1.5 w-1.5 rounded-full shrink-0 animate-pulse", dotColor)} />}
      <span>{children || status}</span>
    </div>
  );
}

export { Badge, badgeVariants };
