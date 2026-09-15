import * as React from "react";
import { cva } from "class-variance-authority";
import { cn } from "../../lib/utils";

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-1.5 whitespace-nowrap rounded-lg text-xs font-medium transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-blue-500 disabled:pointer-events-none disabled:opacity-40 cursor-pointer select-none",
  {
    variants: {
      variant: {
        default: "bg-blue-600 text-white hover:bg-blue-700 active:bg-blue-800 shadow-sm",
        destructive: "bg-red-500/10 border border-red-500/20 text-red-400 hover:bg-red-500/20 active:bg-red-500/30",
        success: "bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 hover:bg-emerald-500/20 active:bg-emerald-500/30",
        outline: "border border-white/10 bg-white/5 text-white/70 hover:bg-white/10 hover:text-white active:bg-white/15",
        secondary: "bg-white/10 text-white hover:bg-white/15 active:bg-white/20",
        ghost: "hover:bg-white/5 text-white/60 hover:text-white",
        link: "text-blue-400 underline-offset-4 hover:underline p-0 h-auto",
      },
      size: {
        default: "px-3 py-2",
        sm: "px-2.5 py-1.5 text-xs",
        lg: "px-4 py-2.5 text-sm",
        icon: "h-8 w-8 p-0 flex items-center justify-center",
        iconSm: "h-6 w-6 p-0 flex items-center justify-center rounded",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
);

const Button = React.forwardRef(({ className, variant, size, ...props }, ref) => {
  return (
    <button
      className={cn(buttonVariants({ variant, size, className }))}
      ref={ref}
      {...props}
    />
  );
});
Button.displayName = "Button";

export { Button, buttonVariants };
