import * as React from "react";
import { cn } from "../../lib/utils";
import { Card } from "./card";

function Skeleton({ className, ...props }) {
    return (
        <div
            className={cn("animate-pulse rounded-lg bg-white/10", className)}
            {...props}
        />
    );
}

// CardSkeleton directly matches the layout & border of standard Card components
function CardSkeleton({ className }) {
    return (
        <Card className={cn("space-y-3", className)}>
            <div className="flex items-start justify-between">
                <div className="flex items-center gap-3">
                    <Skeleton className="h-10 w-10 rounded-xl shrink-0" />
                    <div className="space-y-1.5">
                        <Skeleton className="h-4 w-32" />
                        <Skeleton className="h-3 w-44" />
                    </div>
                </div>
                <Skeleton className="h-5 w-16 rounded-full" />
            </div>
            <div className="space-y-2 pt-2 border-t border-white/5">
                <Skeleton className="h-2 w-full" />
                <Skeleton className="h-2 w-3/4" />
            </div>
        </Card>
    );
}

// GaugeCardSkeleton directly matches the layout of metric & monitoring cards
function GaugeCardSkeleton() {
    return (
        <Card className="flex flex-col items-center justify-center p-5 gap-3">
            <Skeleton className="h-24 w-24 rounded-full shrink-0" />
            <Skeleton className="h-4 w-20" />
        </Card>
    );
}

// TableRowSkeleton directly matches file & service listing table rows
function TableRowSkeleton() {
    return (
        <div className="grid grid-cols-12 gap-2 px-4 py-3 items-center border-b border-white/5">
            <div className="col-span-6 flex items-center gap-3 min-w-0">
                <Skeleton className="h-4 w-4 rounded shrink-0" />
                <Skeleton className="h-4 w-2/3" />
            </div>
            <div className="col-span-2 hidden sm:block">
                <Skeleton className="h-3 w-16" />
            </div>
            <div className="col-span-2 hidden sm:block">
                <Skeleton className="h-3 w-20" />
            </div>
            <div className="col-span-2 flex justify-end">
                <Skeleton className="h-3 w-10" />
            </div>
        </div>
    );
}

export { Skeleton, CardSkeleton, GaugeCardSkeleton, TableRowSkeleton };
