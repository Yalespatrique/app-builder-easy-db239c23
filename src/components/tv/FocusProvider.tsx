import { useEffect, type ReactNode } from "react";
import { init } from "@noriginmedia/norigin-spatial-navigation";

/**
 * Initializes the spatial-navigation engine once for the whole app.
 * Enables D-pad control on Android TV (arrow keys + Enter + Back).
 */
export function FocusProvider({ children }: { children: ReactNode }) {
  useEffect(() => {
    init({
      debug: false,
      visualDebug: false,
      throttle: 100,
    });
  }, []);
  return <>{children}</>;
}
