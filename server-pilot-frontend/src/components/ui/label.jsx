import * as React from "react";
import { cn } from "../../lib/utils";

const Label = React.forwardRef(({ className, ...props }, ref) => (
  <label
    ref={ref}
    className={cn(
      "text-xs font-medium text-white/50 leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70 select-none block mb-1.5",
      className
    )}
    {...props}
  />
));
Label.displayName = "Label";

export { Label };
