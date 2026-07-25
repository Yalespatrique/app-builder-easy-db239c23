import {
  useFocusable,
  type FocusableComponentLayout,
  type FocusDetails,
} from "@noriginmedia/norigin-spatial-navigation";
import { useEffect, type ReactNode } from "react";

type Props = {
  onEnterPress?: (props: object, details: FocusDetails) => void;
  onFocus?: (
    layout: FocusableComponentLayout,
    props: object,
    details: FocusDetails,
  ) => void;
  focusKey?: string;
  autoFocus?: boolean;
  className?: string;
  as?: "div" | "button";
  children: ReactNode | ((focused: boolean) => ReactNode);
};

/**
 * D-pad focusable wrapper. Renders a div (or button) with data-focused
 * so CSS (.aster-focusable[data-focused="true"]) applies the focus ring.
 */
export function Focusable({
  onEnterPress,
  onFocus,
  focusKey,
  autoFocus,
  className,
  as = "div",
  children,
}: Props) {
  const { ref, focused, focusSelf } = useFocusable({
    onEnterPress,
    onFocus,
    focusKey,
  });

  useEffect(() => {
    if (autoFocus) focusSelf();
  }, [autoFocus, focusSelf]);

  const cls = ["aster-focusable", className].filter(Boolean).join(" ");
  const content = typeof children === "function" ? children(focused) : children;

  if (as === "button") {
    return (
      <button
        ref={ref as React.RefObject<HTMLButtonElement>}
        data-focused={focused ? "true" : "false"}
        className={cls}
        type="button"
        onClick={() =>
          onEnterPress?.({}, { pressedKeys: {} } as FocusDetails)
        }
      >
        {content}
      </button>
    );
  }
  return (
    <div
      ref={ref as React.RefObject<HTMLDivElement>}
      data-focused={focused ? "true" : "false"}
      className={cls}
      onClick={() =>
        onEnterPress?.({}, { pressedKeys: {} } as FocusDetails)
      }
    >
      {content}
    </div>
  );
}
